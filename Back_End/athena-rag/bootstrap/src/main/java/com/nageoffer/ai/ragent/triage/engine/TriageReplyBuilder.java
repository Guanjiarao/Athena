

package com.nageoffer.ai.ragent.triage.engine;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.nageoffer.ai.ragent.framework.convention.ChatMessage;
import com.nageoffer.ai.ragent.triage.controller.vo.TriageClarificationData;
import com.nageoffer.ai.ragent.triage.model.QuestionPlan;
import com.nageoffer.ai.ragent.triage.model.RiskLevel;
import com.nageoffer.ai.ragent.triage.model.SlotCode;
import com.nageoffer.ai.ragent.triage.model.Symptom;
import com.nageoffer.ai.ragent.triage.model.TriageContext;
import com.nageoffer.ai.ragent.triage.service.TriageModelGateway;
import com.nageoffer.ai.ragent.triage.question.QuestionOptionProvider;
import com.nageoffer.ai.ragent.triage.question.QuestionPlanningSupport;
import com.nageoffer.ai.ragent.triage.response.TriageReplyPromptSupport;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
public final class TriageReplyBuilder {

    private TriageReplyBuilder() {
    }

    public static String buildClarificationReply(TriageContext context, QuestionOptionProvider optionGenerator,
                                                 QuestionPlanningSupport questionPlanSupport, TriageModelGateway triageModelGateway) {
        log.info("[ReplyBuilder] 开始构建澄清回复, sessionId={}, nextAction={}, missingFields={}, pendingSlots={}, generatedOptions={}",
                context.getSessionId(),
                context.getNextAction(),
                context.getMissingFields(),
                context.getPendingSlots(),
                context.getGeneratedOptions() == null ? "null" : context.getGeneratedOptions().size());

        List<String> prompts = new ArrayList<>();
        List<TriageClarificationData.QuestionOption> allOptions = new ArrayList<>();
        QuestionPlan questionPlan = context.getQuestionPlan();

        log.info("[ReplyBuilder] questionPlan 是否为空: {}", questionPlan == null);
        if (questionPlan != null) {
            log.info("[ReplyBuilder] questionPlan.nextSlotsToAsk: {}", questionPlan.getNextSlotsToAsk());
            log.info("[ReplyBuilder] questionPlan.pendingSlots: {}", questionPlan.getPendingSlots());
            log.info("[ReplyBuilder] questionPlan.priorityReason: {}", questionPlan.getPriorityReason());
            log.info("[ReplyBuilder] questionPlan.policyReason: {}", questionPlan.getPolicyReason());
        }

        // 限制单次追问数量：最多 2 个问题
        int maxQuestions = 2;

        if (questionPlan != null && questionPlan.getNextSlotsToAsk() != null && !questionPlan.getNextSlotsToAsk().isEmpty()) {
            log.info("[ReplyBuilder] 从 nextSlotsToAsk 生成问题, 槽位数量: {}", questionPlan.getNextSlotsToAsk().size());

            int count = 0;
            for (SlotCode slotCode : questionPlan.getNextSlotsToAsk()) {
                if (count >= maxQuestions) {
                    log.info("[ReplyBuilder] 已达到最大问题数量 {}, 停止添加", maxQuestions);
                    break;
                }

                String prompt = TriageReplyPromptSupport.promptForSlot(slotCode);
                log.info("[ReplyBuilder] 槽位 {} 的问题模板: {}", slotCode, prompt == null ? "null" : prompt);

                if (StrUtil.isNotBlank(prompt)) {
                    prompts.add(prompt);
                    count++;
                    log.info("[ReplyBuilder] 添加槽位 {} 的问题, 当前问题数: {}", slotCode, count);

                    // 为该槽位生成选项
                    if (optionGenerator != null) {
                        List<TriageClarificationData.QuestionOption> options = optionGenerator.generateOptionsForSlot(slotCode);
                        if (options != null && !options.isEmpty()) {
                            allOptions.addAll(options);
                            log.info("[ReplyBuilder] 为槽位 {} 生成了 {} 个选项", slotCode, options.size());
                        }
                    }
                }
            }
        }

        if (prompts.isEmpty()) {
            log.info("[ReplyBuilder] nextSlotsToAsk 未生成问题，尝试从 missingFields 生成");

            List<String> missingFields = context.getMissingFields() == null ? Collections.emptyList() : context.getMissingFields();
            log.info("[ReplyBuilder] missingFields: {}", missingFields);

            int count = 0;
            for (String field : missingFields) {
                if (count >= maxQuestions) {
                    log.info("[ReplyBuilder] 已达到最大问题数量 {}, 停止添加", maxQuestions);
                    break;
                }

                if (StrUtil.isBlank(field)) {
                    log.debug("[ReplyBuilder] 跳过空字段");
                    continue;
                }

                String prompt = TriageReplyPromptSupport.promptForField(field.trim());
                log.info("[ReplyBuilder] 字段 {} 的问题模板: {}", field, prompt == null ? "null" : prompt);

                if (StrUtil.isNotBlank(prompt)) {
                    prompts.add(prompt);
                    count++;
                    log.info("[ReplyBuilder] 添加字段 {} 的问题, 当前问题数: {}", field, count);

                    // 为该字段生成选项（通过字段名映射到槽位）
                    if (optionGenerator != null) {
                        SlotCode slotCode = TriageReplyPromptSupport.mapFieldToSlot(field.trim());
                        if (slotCode != null) {
                            List<TriageClarificationData.QuestionOption> options = optionGenerator.generateOptionsForSlot(slotCode);
                            if (options != null && !options.isEmpty()) {
                                allOptions.addAll(options);
                                log.info("[ReplyBuilder] 为字段 {} (槽位 {}) 生成了 {} 个选项", field, slotCode, options.size());
                            }
                        }
                    }
                }
            }
        }

        if (prompts.isEmpty()) {
            log.warn("[ReplyBuilder] 所有问题列表都为空，启动 LLM 兜底生成问题, sessionId={}", context.getSessionId());
            log.warn("[ReplyBuilder] 退化原因分析: questionPlan={}, nextSlotsToAsk={}, missingFields={}",
                questionPlan == null ? "null" : "存在",
                questionPlan == null || questionPlan.getNextSlotsToAsk() == null ? "null" : questionPlan.getNextSlotsToAsk(),
                context.getMissingFields());

            // 使用 LLM 生成具体问题
            String llmGeneratedQuestion = generateQuestionByLLM(context, triageModelGateway);
            if (StrUtil.isNotBlank(llmGeneratedQuestion)) {
                log.info("[ReplyBuilder] LLM 生成的问题: {}", llmGeneratedQuestion);

                // 尝试为常见槽位生成选项作为兜底
                if (optionGenerator != null && allOptions.isEmpty()) {
                    List<SlotCode> fallbackSlots = List.of(
                        SlotCode.DURATION, SlotCode.ONSET_TIME,
                        SlotCode.PAIN_SEVERITY, SlotCode.PAIN_CHARACTER, SlotCode.BODY_PART,
                        SlotCode.FEVER_PRESENCE, SlotCode.NAUSEA_PRESENCE, SlotCode.VOMITING_PRESENCE,
                        SlotCode.DIARRHEA_PRESENCE, SlotCode.COUGH_PRESENCE, SlotCode.DYSPNEA_PRESENCE,
                        SlotCode.DIAGNOSIS_HISTORY, SlotCode.MEDICATION_HISTORY,
                        SlotCode.ASSOCIATED_SYMPTOMS, SlotCode.AGE
                    );
                    for (SlotCode slot : fallbackSlots) {
                        List<TriageClarificationData.QuestionOption> options = optionGenerator.generateOptionsForSlot(slot);
                        if (options != null && !options.isEmpty()) {
                            allOptions.addAll(options);
                            log.info("[ReplyBuilder] 兜底：为槽位 {} 生成了  个选项", slot, options.size());
                            break;
                        }
                    }
                }

                context.setGeneratedOptions(allOptions);
                return llmGeneratedQuestion;
            }

            // 如果 LLM 也失败了，才使用通用话术
            log.warn("[ReplyBuilder] LLM 生成问题失败，使用通用话术");
            context.setGeneratedOptions(allOptions);
            return "为了继续判断，请再补充一些不适细节。";
        }

        log.info("[ReplyBuilder] 成功生成 {} 个问题", prompts.size());

        // 只保留当前轮要问槽位的选项，避免把 DB/Redis 命中的整组规则选项泄露到响应中。
        Set<SlotCode> currentSlots = new HashSet<>(collectCurrentQuestionSlots(context));
        if (context.getGeneratedOptions() != null && !context.getGeneratedOptions().isEmpty()) {
            context.getGeneratedOptions().stream()
                    .filter(option -> option != null && currentSlots.contains(option.getTargetSlot()))
                    .forEach(allOptions::add);
        }
        List<TriageClarificationData.QuestionOption> currentRoundOptions = deduplicateOptions(allOptions).stream()
                .filter(option -> option != null && currentSlots.contains(option.getTargetSlot()))
                .toList();
        context.setGeneratedOptions(currentRoundOptions);
        log.info("[ReplyBuilder] 当前轮生成了 {} 个选项", context.getGeneratedOptions().size());

        // 添加上下文引导：根据已知症状生成个性化引导语
        String contextualIntro = buildContextualIntro(context);
        String finalReply = contextualIntro + String.join("；", prompts) + "。";

        log.info("[ReplyBuilder] 最终回复: {}", finalReply);

        return finalReply;
    }

    public static List<TriageClarificationData.ClarificationQuestion> buildClarificationQuestions(TriageContext context,
                                                                                                  QuestionOptionProvider optionGenerator) {
        if (context == null) {
            return List.of();
        }
        List<SlotCode> slots = collectCurrentQuestionSlots(context);
        if (slots.isEmpty()) {
            log.info("[ReplyBuilder] 当前轮无 pending/nextSlots，questions 为空");
            return List.of();
        }
        List<TriageClarificationData.QuestionOption> availableOptions = context.getGeneratedOptions() == null
                ? List.of()
                : context.getGeneratedOptions();
        List<TriageClarificationData.ClarificationQuestion> questions = new ArrayList<>();
        for (SlotCode slot : slots) {
            String questionText = TriageReplyPromptSupport.promptForSlot(slot);
            List<TriageClarificationData.QuestionOption> slotOptions = availableOptions.stream()
                    .filter(option -> option != null && option.getTargetSlot() == slot)
                    .toList();
            if (slotOptions.isEmpty() && optionGenerator != null) {
                slotOptions = optionGenerator.generateOptionsForSlot(slot);
            }
            questions.add(TriageClarificationData.ClarificationQuestion.builder()
                    .slot(slot)
                    .question(questionText)
                    .inputType(resolveInputType(slot, slotOptions))
                    .required(Boolean.TRUE)
                    .multiple(isMultiChoiceSlot(slot))
                    .options(slotOptions)
                    .build());
        }
        log.info("[ReplyBuilder] 构建结构化 questions, count={}, slots={}", questions.size(), slots);
        return questions;
    }

    private static List<SlotCode> collectCurrentQuestionSlots(TriageContext context) {
        List<SlotCode> rawSlots = new ArrayList<>();
        if (context.getQuestionPlan() != null && context.getQuestionPlan().getNextSlotsToAsk() != null) {
            rawSlots.addAll(context.getQuestionPlan().getNextSlotsToAsk());
        }
        if (rawSlots.isEmpty() && context.getPendingSlots() != null) {
            rawSlots.addAll(context.getPendingSlots());
        }
        if (rawSlots.isEmpty() && context.getMissingFields() != null) {
            for (String field : context.getMissingFields()) {
                SlotCode slot = TriageReplyPromptSupport.mapFieldToSlot(field == null ? null : field.trim());
                if (slot != null) {
                    rawSlots.add(slot);
                }
            }
        }
        List<SlotCode> result = new ArrayList<>();
        Set<SlotCode> seen = new HashSet<>();
        for (SlotCode slot : rawSlots) {
            if (slot != null && seen.add(slot)) {
                result.add(slot);
            }
            if (result.size() >= 2) {
                break;
            }
        }
        return result;
    }

    private static String resolveInputType(SlotCode slot, List<TriageClarificationData.QuestionOption> options) {
        if (isMultiChoiceSlot(slot)) {
            return "MULTI_CHOICE";
        }
        if (options == null || options.isEmpty() || (options.size() == 1 && "other".equals(options.get(0).getValue()))) {
            return "TEXT";
        }
        return "SINGLE_CHOICE";
    }

    private static boolean isMultiChoiceSlot(SlotCode slot) {
        return slot == SlotCode.ASSOCIATED_SYMPTOMS;
    }

    private static String buildContextualIntro(TriageContext context) {
        // 根据已知症状生成个性化引导语
        if (context.getExtractedSymptoms() != null && !context.getExtractedSymptoms().isEmpty()) {
            log.debug("[ReplyBuilder] 使用症状引导语, 症状数量: {}", context.getExtractedSymptoms().size());
            return "为了更准确评估您的情况，需要了解：";
        }
        log.debug("[ReplyBuilder] 使用默认引导语");
        return "为了更准确判断，请再补充一下：";
    }

    private static List<TriageClarificationData.QuestionOption> deduplicateOptions(List<TriageClarificationData.QuestionOption> options) {
        if (options == null || options.isEmpty()) {
            return List.of();
        }
        List<TriageClarificationData.QuestionOption> result = new ArrayList<>();
        for (TriageClarificationData.QuestionOption option : options) {
            if (option == null || option.getTargetSlot() == null || StrUtil.isBlank(option.getValue())) {
                continue;
            }
            boolean exists = result.stream().anyMatch(existing -> existing.getTargetSlot() == option.getTargetSlot()
                    && StrUtil.equals(existing.getValue(), option.getValue()));
            if (!exists) {
                result.add(option);
            }
        }
        return result;
    }

    public static String buildWarningReply(TriageContext context) {
        RiskLevel riskLevel = context.getRiskAssessment();
        String evidence = riskLevel == null ? "" : sanitizeSentence(StrUtil.blankToDefault(riskLevel.getEvidence(), ""));
        List<String> riskHints = riskLevel == null || riskLevel.getRiskHints() == null ? List.of() : riskLevel.getRiskHints();
        StringBuilder builder = new StringBuilder("根据当前症状描述，存在较高风险，建议尽快前往线下医院就诊。");
        if (StrUtil.isNotBlank(evidence)) {
            builder.append("重点依据：").append(evidence).append(" ");
        }
        String specificHint = buildSpecificWarningHint(riskHints);
        if (StrUtil.isNotBlank(specificHint)) {
            builder.append(specificHint).append(" ");
        }
        builder.append("如果症状持续加重，或出现呼吸困难、意识变化、明显出血等情况，请及时前往急诊。");
        return builder.toString().trim();
    }

    public static String generatePreTriageReport(TriageContext context, TriageModelGateway triageModelGateway) {
        // 使用科室推荐引擎获取推荐科室
        DepartmentRecommender recommender = new DepartmentRecommender();
        DepartmentRecommender.DepartmentRecommendation recommendation = recommender.recommend(context);

        try {
            List<ChatMessage> messages = new ArrayList<>();
            String systemPrompt = """
                    你是医疗分诊系统中的报告生成助手。请用简洁中文输出适合手机端阅读的分诊摘要，不要给出明确诊断，不要输出 JSON。

                    报告必须包含以下部分：
                    1. 主诉提炼：用一句话总结患者的主要不适（如："患者主诉腹痛3天，伴恶心呕吐"）
                    2. 症状总结：列出所有收集到的症状信息（部位、性质、程度、持续时间、伴随症状等）
                    3. 风险分析：说明风险等级的判断依据和需要关注的危险信号
                    4. 建议科室：明确推荐就诊的科室（已提供：%s）
                    5. 行动建议：给出具体的就医建议（如：24小时内就诊、立即急诊、可观察等）和注意事项

                    报告语言要求：
                    - 专业但易懂，避免过度医学术语
                    - 简洁明了，重点突出
                    - 语气温和，避免引起恐慌
                    - 每个部分用明确的标题分隔

                    报告结构示例：
                    【主诉提炼】
                    患者主诉...

                    【症状总结】
                    - 主要症状：...
                    - 持续时间：...
                    - 伴随症状：...

                    【风险评估】
                    风险等级：...
                    判断依据：...

                    【建议科室】
                    建议就诊：%s

                    【行动建议】
                    建议您...
                    """.formatted(recommendation.getDepartment(), recommendation.getDepartment());

            messages.add(ChatMessage.system(systemPrompt));
            messages.add(ChatMessage.user(buildReportPrompt(context, recommendation)));
            String report = triageModelGateway.chatWithReportModel(messages, 0.2D, 0.3D, 900);
            if (StrUtil.isNotBlank(report)) {
                return report.trim();
            }
        } catch (Exception ignored) {
        }
        return buildFallbackReport(context, recommendation);
    }

    private static String buildSpecificWarningHint(List<String> riskHints) {
        if (riskHints == null || riskHints.isEmpty()) {
            return "";
        }
        if (riskHints.contains("SEIZURE")) {
            return "已出现抽搐/惊厥等高危红旗表现。";
        }
        if (riskHints.contains("PREGNANCY_BLEEDING")) {
            return "妊娠相关出血需要尽快线下评估。";
        }
        if (riskHints.contains("BLEEDING")) {
            return "明显出血提示存在急危重风险。";
        }
        if (riskHints.contains("DYSPNEA") || riskHints.contains("CHEST_PAIN_WITH_DYSPNEA")) {
            return "呼吸困难相关表现提示需尽快急诊评估。";
        }
        return "";
    }

    private static String sanitizeSentence(String text) {
        if (StrUtil.isBlank(text)) {
            return "";
        }
        return text.trim().replace("。。", "。");
    }

    private static String buildReportPrompt(TriageContext context, DepartmentRecommender.DepartmentRecommendation recommendation) {
        StringBuilder builder = new StringBuilder();
        builder.append("会话ID：").append(context.getSessionId()).append("\n");
        builder.append("用户描述：").append(StrUtil.blankToDefault(context.getUserInput(), "暂无")).append("\n");
        builder.append("结构化症状：\n");
        if (CollUtil.isEmpty(context.getExtractedSymptoms())) {
            builder.append("- 暂无可用结构化症状\n");
        } else {
            for (Symptom symptom : context.getExtractedSymptoms()) {
                builder.append("- 症状：").append(StrUtil.blankToDefault(symptom.getName(), "未知症状")).append("\n");
                if (StrUtil.isNotBlank(symptom.getBodyPart())) builder.append("  部位：").append(symptom.getBodyPart()).append("\n");
                if (StrUtil.isNotBlank(symptom.getDuration())) builder.append("  持续时间：").append(symptom.getDuration()).append("\n");
                if (StrUtil.isNotBlank(symptom.getSeverity())) builder.append("  程度：").append(symptom.getSeverity()).append("\n");
                if (CollUtil.isNotEmpty(symptom.getCharacteristics())) builder.append("  特征：").append(String.join("、", symptom.getCharacteristics())).append("\n");
                if (CollUtil.isNotEmpty(symptom.getAccompanyingSymptoms())) builder.append("  伴随症状：").append(String.join("、", symptom.getAccompanyingSymptoms())).append("\n");
            }
        }
        RiskLevel riskLevel = context.getRiskAssessment();
        if (riskLevel != null) {
            builder.append("风险等级：").append(riskLevel.getLevel()).append("\n");
            builder.append("风险分数：").append(riskLevel.getScore()).append("\n");
            builder.append("依据：").append(StrUtil.blankToDefault(riskLevel.getEvidence(), "暂无")).append("\n");
            builder.append("解释：").append(StrUtil.blankToDefault(riskLevel.getRationale(), "暂无")).append("\n");
        }

        // 添加推荐科室信息
        builder.append("\n【重要】推荐就诊科室：").append(recommendation.getDepartment()).append("\n");
        builder.append("推荐理由：").append(recommendation.getReason()).append("\n");
        builder.append("\n请根据以上信息生成分诊报告，报告中必须明确包含建议就诊科室：").append(recommendation.getDepartment()).append("\n");
        builder.append("例如：\"建议尽快就诊").append(recommendation.getDepartment()).append("进行进一步检查。\"\n");

        builder.append("\n科室选择参考：\n");
        builder.append("- 消化系统症状（腹痛、腹泻、呕吐、恶心等）→ 消化内科\n");
        builder.append("- 呼吸系统症状（咳嗽、气喘、呼吸困难、胸闷等）→ 呼吸内科\n");
        builder.append("- 心血管症状（胸痛、心悸、心慌等）→ 心内科\n");
        builder.append("- 神经系统症状（头痛、头晕、眩晕、抽搐等）→ 神经内科\n");
        builder.append("- 骨骼肌肉症状（关节痛、骨痛、扭伤、骨折等）→ 骨科\n");
        builder.append("- 皮肤症状（皮疹、瘙痒、红肿等）→ 皮肤科\n");
        builder.append("- 眼部症状（视力下降、眼痛等）→ 眼科\n");
        builder.append("- 耳鼻喉症状（耳痛、鼻塞、咽痛等）→ 耳鼻喉科\n");
        builder.append("- 泌尿系统症状（尿频、尿痛、血尿等）→ 泌尿外科\n");
        builder.append("- 妇科症状（月经异常、阴道出血、孕期不适等）→ 妇产科\n");
        builder.append("- 儿童患者 → 儿科\n");
        builder.append("- 高风险/急危重症状 → 急诊科\n");
        builder.append("- 症状不明确或多系统症状 → 全科/内科\n");
        return builder.toString();
    }

    private static String buildFallbackReport(TriageContext context, DepartmentRecommender.DepartmentRecommendation recommendation) {
        StringBuilder builder = new StringBuilder();
        builder.append("【分诊摘要】\n");
        builder.append("主要不适：").append(StrUtil.blankToDefault(context.getUserInput(), "暂无")).append("\n");
        builder.append("症状概览：");
        if (CollUtil.isEmpty(context.getExtractedSymptoms())) {
            builder.append("当前可用信息有限，仍需继续补充描述。\n");
        } else {
            List<String> lines = new ArrayList<>();
            for (Symptom symptom : context.getExtractedSymptoms()) {
                StringBuilder line = new StringBuilder(StrUtil.blankToDefault(symptom.getName(), "症状"));
                if (StrUtil.isNotBlank(symptom.getBodyPart())) line.append("（").append(symptom.getBodyPart()).append("）");
                if (StrUtil.isNotBlank(symptom.getDuration())) line.append("，持续").append(symptom.getDuration());
                if (StrUtil.isNotBlank(symptom.getSeverity())) line.append("，程度").append(symptom.getSeverity());
                lines.add(line.toString());
            }
            builder.append(String.join("；", lines)).append("。\n");
        }
        RiskLevel riskLevel = context.getRiskAssessment();
        if (riskLevel != null) {
            builder.append("风险等级：").append(riskLevel.getLevel()).append("，依据：")
                    .append(StrUtil.blankToDefault(riskLevel.getEvidence(), "暂无")).append("\n");
        }
        builder.append("建议科室：").append(recommendation.getDepartment()).append("（").append(recommendation.getReason()).append("）\n");
        builder.append("行动建议：本结果仅用于分诊辅助，不能替代线下面诊，如症状持续或加重，请及时就医。");
        return builder.toString();
    }

    /**
     * 使用 LLM 生成具体问题（当没有匹配的问题模板时）
     */
    private static String generateQuestionByLLM(TriageContext context, TriageModelGateway triageModelGateway) {
        try {
            List<ChatMessage> messages = new ArrayList<>();
            String systemPrompt = """
                    你是医疗分诊系统的问题生成助手。当前对话中缺少关键信息，你需要生成一个具体、有针对性的问题来收集信息。

                    要求：
                    1. 问题必须具体、明确，不能是"请再补充一些细节"这样的通用话术
                    2. 根据已收集的症状信息，询问最关键的缺失信息
                    3. 优先询问：持续时间、疼痛程度、伴随症状、发作时间等
                    4. 问题要简洁，一次只问一个方面
                    5. 使用口语化、易懂的表达方式

                    示例：
                    - 如果已知腹痛，但不知道持续时间 → "这种腹痛是从什么时候开始的呢？"
                    - 如果已知咳嗽，但不知道是否有痰 → "咳嗽的时候有痰吗？"
                    - 如果已知发热，但不知道温度 → "量过体温吗？大概多少度？"

                    只返回问题本身，不要添加任何解释或前缀。
                    """;

            messages.add(ChatMessage.system(systemPrompt));
            messages.add(ChatMessage.user(buildLLMQuestionPrompt(context)));

            String question = triageModelGateway.chatWithTextModel(messages, 0.3D, 0.5D, 100);
            if (StrUtil.isNotBlank(question)) {
                return question.trim();
            }
        } catch (Exception ex) {
            log.error("[ReplyBuilder] LLM 生成问题失败", ex);
        }
        return null;
    }

    /**
     * 构建 LLM 问题生成的 prompt
     */
    private static String buildLLMQuestionPrompt(TriageContext context) {
        StringBuilder builder = new StringBuilder();
        builder.append("会话ID：").append(context.getSessionId()).append("\n");
        builder.append("用户初始输入：").append(StrUtil.blankToDefault(context.getUserInput(), "暂无")).append("\n");

        builder.append("\n已收集的症状信息：\n");
        if (CollUtil.isEmpty(context.getExtractedSymptoms())) {
            builder.append("- 暂无结构化症状\n");
        } else {
            for (Symptom symptom : context.getExtractedSymptoms()) {
                builder.append("- 症状：").append(StrUtil.blankToDefault(symptom.getName(), "未知")).append("\n");
                if (StrUtil.isNotBlank(symptom.getBodyPart())) builder.append("  部位：").append(symptom.getBodyPart()).append("\n");
                if (StrUtil.isNotBlank(symptom.getDuration())) builder.append("  持续时间：").append(symptom.getDuration()).append("\n");
                if (StrUtil.isNotBlank(symptom.getSeverity())) builder.append("  程度：").append(symptom.getSeverity()).append("\n");
            }
        }

        builder.append("\n槽位状态：\n");
        if (context.getSlotState() == null || context.getSlotState().getSlots() == null || context.getSlotState().getSlots().isEmpty()) {
            builder.append("- 暂无槽位信息\n");
        } else {
            context.getSlotState().getSlots().forEach((slot, value) -> {
                if (value != null && value.getValue() != null && !value.getValue().isBlank()) {
                    builder.append("- ").append(slot).append(": ").append(value.getValue()).append("\n");
                }
            });
        }

        builder.append("\n对话历史（最近3轮）：\n");
        builder.append(context.buildConversationTranscript(true));

        builder.append("\n请根据以上信息，生成一个具体的问题来收集最关键的缺失信息。");
        return builder.toString();
    }
}
