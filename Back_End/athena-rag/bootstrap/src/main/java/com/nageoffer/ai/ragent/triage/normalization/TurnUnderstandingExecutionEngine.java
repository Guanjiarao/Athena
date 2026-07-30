

package com.nageoffer.ai.ragent.triage.normalization;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nageoffer.ai.ragent.infra.chat.LLMService;
import com.nageoffer.ai.ragent.triage.model.*;
import com.nageoffer.ai.ragent.triage.worker.AbstractStructuredTriageWorker;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class TurnUnderstandingExecutionEngine extends AbstractStructuredTriageWorker {
    private final TurnUnderstandingExecutionShell turnUnderstandingExecutionShell;

    public TurnUnderstandingExecutionEngine(LLMService llmService,
                                            ObjectMapper objectMapper,
                                            TurnUnderstandingExecutionShell turnUnderstandingExecutionShell,
                                            com.nageoffer.ai.ragent.triage.config.TriageAiProperties triageAiProperties) {
        super(llmService, objectMapper, triageAiProperties);
        this.turnUnderstandingExecutionShell = turnUnderstandingExecutionShell;
    }

    public TriageContext execute(TriageContext context) {
        String latestTurn = context == null ? null : StrUtil.blankToDefault(context.getLatestUserTurn(), "").trim();
        return turnUnderstandingExecutionShell.execute(
                context,
                latestTurn,
                () -> {
                    String raw = invokeLlm(resolveModelId("turnUnderstandingModel", "deepseek-v4-flash"), buildSystemPrompt(), buildUserPrompt(context), 0.1D, 0.2D);
                    TurnUnderstanding result = readObjectSafely(raw, TurnUnderstanding.class, null, "回合语义理解");

                    // 添加调试日志
                    log.info("=== TurnUnderstanding Debug ===");
                    log.info("用户输入: {}", context.getLatestUserTurn());
                    log.info("LLM 原始输出: {}", raw);
                    if (result != null && result.getAnsweredSlots() != null) {
                        log.info("解析后的 answeredSlots 数量: {}", result.getAnsweredSlots().size());
                        for (var slot : result.getAnsweredSlots()) {
                            log.info("  - 槽位: {}, 值: {}, 置信度: {}",
                                slot.getSlot(), slot.getNormalizedValue(), slot.getConfidence());
                        }
                    } else {
                        log.info("解析后的 answeredSlots: null 或空");
                    }
                    log.info("===============================");

                    // 规则兜底：扩展触发条件
                    if (result != null) {
                        boolean needRuleFallback = false;

                        // 情况1：LLM 未识别任何槽位
                        if (result.getAnsweredSlots() == null || result.getAnsweredSlots().isEmpty()) {
                            needRuleFallback = true;
                            log.info("[TurnUnderstanding] LLM未识别任何槽位，触发规则兜底");
                        }

                        // 情况2：上一轮有询问槽位，但 LLM 未识别为回答
                        if (context.getLastAskedSlots() != null && !context.getLastAskedSlots().isEmpty()) {
                            boolean answeredLastAsked = result.getAnsweredSlots() != null && result.getAnsweredSlots().stream()
                                .anyMatch(slot -> context.getLastAskedSlots().contains(slot.getSlot())
                                               && Boolean.TRUE.equals(slot.getAnswersPreviousQuestion()));
                            String userInput = context.getLatestUserTurn();
                            if (!answeredLastAsked && userInput != null && userInput.trim().length() < 15) {
                                needRuleFallback = true;
                                log.info("[TurnUnderstanding] 上一轮有询问槽位但未被识别，且用户输入是简短回答，触发规则兜底");
                            }
                        }

                        if (needRuleFallback) {
                            List<AnsweredSlotUnderstanding> ruleBasedSlots = extractSlotsByRules(
                                context.getLatestUserTurn(),
                                context.getLastAskedSlots()
                            );
                            if (!ruleBasedSlots.isEmpty()) {
                                log.info("[TurnUnderstanding] 规则兜底提取到 {} 个槽位", ruleBasedSlots.size());
                                if (result.getAnsweredSlots() == null || result.getAnsweredSlots().isEmpty()) {
                                    result.setAnsweredSlots(ruleBasedSlots);
                                } else {
                                    // 合并LLM识别的槽位和规则提取的槽位
                                    List<AnsweredSlotUnderstanding> merged = new ArrayList<>(result.getAnsweredSlots());
                                    merged.addAll(ruleBasedSlots);
                                    result.setAnsweredSlots(merged);
                                }
                                result.setIntent(TurnIntent.ANSWER_FOLLOW_UP);
                            }
                        }
                    }

                    return result;
                });
    }

    private String buildSystemPrompt() { return TurnUnderstandingPromptTemplates.systemPrompt(); }
    private String buildUserPrompt(TriageContext context) { return TurnUnderstandingPromptTemplates.userPrompt(StrUtil.blankToDefault(context.getSessionId(), "UNKNOWN"), StrUtil.blankToDefault(context.getLatestUserTurn(), ""), StrUtil.blankToDefault(context.buildConversationTranscript(true), "无"), toJsonSafely(context.getLastAskedSlots()), toJsonSafely(context.getPendingSlots()), toJsonSafely(context.getSlotState())); }

    /**
     * 规则兜底：当 LLM 无法提取槽位时，使用规则提取
     */
    private List<AnsweredSlotUnderstanding> extractSlotsByRules(String userInput, List<SlotCode> lastAskedSlots) {
        List<AnsweredSlotUnderstanding> slots = new ArrayList<>();
        if (StrUtil.isBlank(userInput)) {
            return slots;
        }

        String input = userInput.trim();

        // 1. 时间相关（ONSET_TIME, DURATION）
        slots.addAll(extractTimeSlots(input, lastAskedSlots));

        // 2. 温度相关（TEMPERATURE, FEVER_PRESENCE）
        slots.addAll(extractTemperatureSlots(input, lastAskedSlots));

        // 3. 症状描述（SYMPTOM）
        slots.addAll(extractSymptomSlots(input, lastAskedSlots));

        // 4. 性状描述（STOOL_CHARACTER, PAIN_CHARACTER）
        slots.addAll(extractCharacterSlots(input, lastAskedSlots));

        // 5. 是否回答（FEVER_PRESENCE, NAUSEA_PRESENCE 等）
        slots.addAll(extractPresenceSlots(input, lastAskedSlots));

        return slots;
    }

    /**
     * 提取时间相关槽位
     */
    private List<AnsweredSlotUnderstanding> extractTimeSlots(String input, List<SlotCode> lastAskedSlots) {
        List<AnsweredSlotUnderstanding> slots = new ArrayList<>();

        // 匹配模式：数字+时间单位
        Pattern digitPattern = Pattern.compile("(\\d+)\\s*([天日小时分钟时]|个?小时)");
        Matcher digitMatcher = digitPattern.matcher(input);

        // 匹配模式：中文数字+天
        Pattern chinesePattern = Pattern.compile("([一二三四五六七八九十两]+)\\s*([天日])");
        Matcher chineseMatcher = chinesePattern.matcher(input);

        // 匹配模式：昨天、今天、前天
        Pattern relativePattern = Pattern.compile("(昨天|今天|前天|刚才|刚刚)");
        Matcher relativeMatcher = relativePattern.matcher(input);

        boolean foundTime = false;

        if (digitMatcher.find()) {
            String value = digitMatcher.group(1);
            String unit = digitMatcher.group(2);
            String normalized = value + normalizeTimeUnit(unit);

            SlotCode targetSlot = determineTimeSlot(lastAskedSlots, normalized);
            if (targetSlot != null) {
                slots.add(createSlot(targetSlot, input, normalized, AssertionStatus.PRESENT, 0.75, true));
                foundTime = true;
            }
        }

        if (!foundTime && chineseMatcher.find()) {
            String chineseNum = chineseMatcher.group(1);
            int value = parseChineseNumber(chineseNum);
            String normalized = value + "天";

            SlotCode targetSlot = determineTimeSlot(lastAskedSlots, normalized);
            if (targetSlot != null) {
                slots.add(createSlot(targetSlot, input, normalized, AssertionStatus.PRESENT, 0.75, true));
                foundTime = true;
            }
        }

        if (!foundTime && relativeMatcher.find()) {
            String relative = relativeMatcher.group(1);
            SlotCode targetSlot = determineTimeSlot(lastAskedSlots, relative);
            if (targetSlot != null) {
                slots.add(createSlot(targetSlot, input, relative, AssertionStatus.PRESENT, 0.75, true));
            }
        }

        return slots;
    }

    /**
     * 提取温度相关槽位
     */
    private List<AnsweredSlotUnderstanding> extractTemperatureSlots(String input, List<SlotCode> lastAskedSlots) {
        List<AnsweredSlotUnderstanding> slots = new ArrayList<>();

        // 匹配模式：数字+温度单位
        Pattern pattern = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*[°℃度]");
        Matcher matcher = pattern.matcher(input);

        if (matcher.find()) {
            String tempValue = matcher.group(1);
            double temp = Double.parseDouble(tempValue);
            String normalized = tempValue + "°C";

            // 添加 TEMPERATURE 槽位
            if (lastAskedSlots == null || lastAskedSlots.contains(SlotCode.TEMPERATURE) || lastAskedSlots.contains(SlotCode.FEVER_TEMPERATURE)) {
                SlotCode tempSlot = (lastAskedSlots != null && lastAskedSlots.contains(SlotCode.FEVER_TEMPERATURE))
                    ? SlotCode.FEVER_TEMPERATURE : SlotCode.TEMPERATURE;
                slots.add(createSlot(tempSlot, input, normalized, AssertionStatus.PRESENT, 0.8, true));

                // 如果温度 >= 37.3，自动添加 FEVER_PRESENCE
                if (temp >= 37.3) {
                    slots.add(createSlot(SlotCode.FEVER_PRESENCE, input, "是", AssertionStatus.PRESENT, 0.8, false));
                }
            }
        }

        return slots;
    }

    /**
     * 提取症状描述槽位
     */
    private List<AnsweredSlotUnderstanding> extractSymptomSlots(String input, List<SlotCode> lastAskedSlots) {
        List<AnsweredSlotUnderstanding> slots = new ArrayList<>();

        // 危险症状关键词（优先级最高，即使没有询问也提取）
        String[] criticalSymptoms = {
            "黑便", "柏油样便", "油亮", "血便", "便血", "呕血", "咯血",
            "胸痛", "呼吸困难", "意识不清", "抽搐", "惊厥"
        };

        // 检查是否包含危险症状
        for (String critical : criticalSymptoms) {
            if (input.contains(critical)) {
                // 危险症状直接提取为 PRIMARY_SYMPTOM，无需等待询问
                String normalizedValue = input;
                if (critical.equals("油亮") && input.contains("便")) {
                    normalizedValue = "黑便（柏油样）";
                } else if (critical.equals("黑便") || critical.equals("柏油样便")) {
                    normalizedValue = "黑便";
                }
                slots.add(createSlot(SlotCode.PRIMARY_SYMPTOM, input, normalizedValue, AssertionStatus.PRESENT, 0.85, true));
                return slots; // 找到危险症状后立即返回
            }
        }

        // 普通症状关键词
        String[] symptomKeywords = {
            "鼻涕", "咳嗽", "痰", "疼", "痛", "烧", "热", "晕", "吐", "泻",
            "胀", "闷", "喘", "血", "肿", "痒", "麻", "酸", "乏", "困"
        };

        for (String keyword : symptomKeywords) {
            if (input.contains(keyword)) {
                // 只有在询问症状相关槽位时才提取
                if (lastAskedSlots != null && (
                    lastAskedSlots.contains(SlotCode.SYMPTOM) ||
                    lastAskedSlots.contains(SlotCode.PRIMARY_SYMPTOM) ||
                    lastAskedSlots.contains(SlotCode.ASSOCIATED_SYMPTOMS)
                )) {
                    SlotCode targetSlot = lastAskedSlots.contains(SlotCode.PRIMARY_SYMPTOM)
                        ? SlotCode.PRIMARY_SYMPTOM : SlotCode.SYMPTOM;
                    slots.add(createSlot(targetSlot, input, input, AssertionStatus.PRESENT, 0.7, true));
                    break;
                }
            }
        }

        return slots;
    }

    /**
     * 提取性状描述槽位
     */
    private List<AnsweredSlotUnderstanding> extractCharacterSlots(String input, List<SlotCode> lastAskedSlots) {
        List<AnsweredSlotUnderstanding> slots = new ArrayList<>();

        // 大便性状
        String[] stoolCharacters = {"水样", "糊状", "稀", "干", "硬", "软", "成形"};
        for (String character : stoolCharacters) {
            if (input.contains(character)) {
                if (lastAskedSlots != null && lastAskedSlots.contains(SlotCode.STOOL_CHARACTER)) {
                    String normalized = character.contains("便") ? input : character + "便";
                    slots.add(createSlot(SlotCode.STOOL_CHARACTER, input, normalized, AssertionStatus.PRESENT, 0.75, true));
                    break;
                }
            }
        }

        // 疼痛性状
        String[] painCharacters = {"胀痛", "刺痛", "钝痛", "绞痛", "隐痛", "酸痛", "剧痛"};
        for (String character : painCharacters) {
            if (input.contains(character)) {
                if (lastAskedSlots != null && lastAskedSlots.contains(SlotCode.PAIN_CHARACTER)) {
                    slots.add(createSlot(SlotCode.PAIN_CHARACTER, input, character, AssertionStatus.PRESENT, 0.75, true));
                    break;
                }
            }
        }

        return slots;
    }

    /**
     * 提取是否类槽位
     */
    private List<AnsweredSlotUnderstanding> extractPresenceSlots(String input, List<SlotCode> lastAskedSlots) {
        List<AnsweredSlotUnderstanding> slots = new ArrayList<>();

        if (lastAskedSlots == null || lastAskedSlots.isEmpty()) {
            return slots;
        }

        // 肯定回答
        boolean isPositive = input.matches(".*[有是嗯对啊].*") && !input.contains("没") && !input.contains("不");
        // 否定回答
        boolean isNegative = input.matches(".*(没有|不是|没|不|无).*");

        if (!isPositive && !isNegative) {
            return slots;
        }

        // 只处理 PRESENCE 类槽位
        for (SlotCode slot : lastAskedSlots) {
            if (slot.name().endsWith("_PRESENCE")) {
                String value = isPositive ? "是" : "否";
                AssertionStatus assertion = isPositive ? AssertionStatus.PRESENT : AssertionStatus.ABSENT;
                slots.add(createSlot(slot, input, value, assertion, 0.75, true));
            }
        }

        return slots;
    }

    /**
     * 创建槽位对象
     */
    private AnsweredSlotUnderstanding createSlot(SlotCode slot, String rawValue, String normalizedValue,
                                                   AssertionStatus assertion, double confidence, boolean answersPrevious) {
        return AnsweredSlotUnderstanding.builder()
            .slot(slot)
            .rawValue(rawValue)
            .normalizedValue(normalizedValue)
            .assertion(assertion)
            .confidence(confidence)
            .answersPreviousQuestion(answersPrevious)
            .evidence("规则提取")
            .build();
    }

    /**
     * 判断时间槽位类型
     */
    private SlotCode determineTimeSlot(List<SlotCode> lastAskedSlots, String value) {
        if (lastAskedSlots == null || lastAskedSlots.isEmpty()) {
            // 默认：包含"前"或相对时间 -> ONSET_TIME，否则 -> DURATION
            if (value.contains("前") || value.matches("(昨天|今天|前天|刚才|刚刚)")) {
                return SlotCode.ONSET_TIME;
            } else {
                return SlotCode.DURATION;
            }
        }

        // 根据上一轮询问的槽位决定
        if (lastAskedSlots.contains(SlotCode.ONSET_TIME)) {
            return SlotCode.ONSET_TIME;
        } else if (lastAskedSlots.contains(SlotCode.DURATION)) {
            return SlotCode.DURATION;
        }

        return null;
    }

    /**
     * 规范化时间单位
     */
    private String normalizeTimeUnit(String unit) {
        if (unit.contains("天") || unit.contains("日")) {
            return "天";
        } else if (unit.contains("小时") || unit.contains("时")) {
            return "小时";
        } else if (unit.contains("分钟")) {
            return "分钟";
        }
        return unit;
    }

    /**
     * 解析中文数字
     */
    private int parseChineseNumber(String chinese) {
        if (chinese.contains("两")) return 2;
        if (chinese.contains("一")) return 1;
        if (chinese.contains("二")) return 2;
        if (chinese.contains("三")) return 3;
        if (chinese.contains("四")) return 4;
        if (chinese.contains("五")) return 5;
        if (chinese.contains("六")) return 6;
        if (chinese.contains("七")) return 7;
        if (chinese.contains("八")) return 8;
        if (chinese.contains("九")) return 9;
        if (chinese.contains("十")) {
            if (chinese.length() == 1) return 10;
            if (chinese.startsWith("十")) return 10 + parseChineseNumber(chinese.substring(1));
            return parseChineseNumber(chinese.substring(0, 1)) * 10;
        }
        return 1;
    }
}
