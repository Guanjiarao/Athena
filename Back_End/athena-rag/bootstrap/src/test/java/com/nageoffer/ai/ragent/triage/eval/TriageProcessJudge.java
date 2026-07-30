

package com.nageoffer.ai.ragent.triage.eval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nageoffer.ai.ragent.framework.convention.ChatMessage;
import com.nageoffer.ai.ragent.framework.convention.ChatRequest;
import com.nageoffer.ai.ragent.infra.chat.LLMService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 过程评分 Judge
 * 对分诊过程的 5 个维度进行打分
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TriageProcessJudge {

    private final LLMService llmService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 对过程维度打分
     */
    public TriageEvalScore judgeProcess(
            TriageEvalCase evalCase,
            String conversationLog,
            int actualTurns
    ) {
        try {
            // 将标准对话转换为字符串格式
            String standardDialogueStr = formatStandardDialogue(evalCase.getStandardDialogue());

            // 1. 幻觉/记忆一致性（40分）
            Integer memoryScore = scoreMemoryConsistency(conversationLog);

            // 2. 信息完整度（20分）
            Integer completenessScore = scoreInformationCompleteness(
                    conversationLog,
                    standardDialogueStr
            );

            // 3. 对话轮次合理性（15分）
            Integer turnsScore = scoreConversationTurns(
                    actualTurns,
                    evalCase.getDiseaseName()
            );

            // 4. 逻辑连贯性（15分）
            Integer logicScore = scoreLogicCoherence(conversationLog);

            // 5. 选项推送率（10分）
            Integer optionScore = scoreOptionQuality(conversationLog);

            return TriageEvalScore.builder()
                    .memoryConsistencyScore(memoryScore)
                    .informationCompletenessScore(completenessScore)
                    .conversationTurnsScore(turnsScore)
                    .logicCoherenceScore(logicScore)
                    .optionQualityScore(optionScore)
                    .build();

        } catch (Exception ex) {
            log.error("过程评分失败: caseId={}", evalCase.getCaseId(), ex);
            return TriageEvalScore.builder()
                    .memoryConsistencyScore(0)
                    .informationCompletenessScore(0)
                    .conversationTurnsScore(0)
                    .logicCoherenceScore(0)
                    .optionQualityScore(0)
                    .build();
        }
    }

    /**
     * 将标准对话列表格式化为字符串
     */
    private String formatStandardDialogue(List<TriageEvalCase.DialogueTurn> standardDialogue) {
        if (standardDialogue == null || standardDialogue.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        for (TriageEvalCase.DialogueTurn turn : standardDialogue) {
            if ("system".equals(turn.getRole())) {
                sb.append("系统：").append(turn.getContent()).append("\n");
            } else if ("user".equals(turn.getRole())) {
                sb.append("用户：").append(turn.getContent()).append("\n");
            }
        }
        return sb.toString();
    }

    /**
     * 维度1：幻觉/记忆一致性（40分）
     * 评估系统是否记得用户已回答的信息，是否重复提问
     */
    private Integer scoreMemoryConsistency(String conversationLog) {
        String prompt = """
                你是一个医疗问诊质量评估专家。请评估以下对话的【幻觉/记忆一致性】。

                【评分标准】
                - 35-40分：无幻觉，全程无重复提问，每轮追问都基于新信息推进
                - 21-34分：轻微幻觉，有1次重复提问，或在尾声冗余确认已说过的信息
                - 8-20分：中等幻觉，2-3次重复提问，明显不记得用户已回答内容
                - 0-7分：严重幻觉，多次重复提问（≥4次），或关键信息丢失导致回到已问过的问题

                【典型扣分场景】
                - 用户回答"37.5°C"后，又在一轮后问"有没有发烧？" → 扣分
                - 用户说"肚脐周围疼"，后面又问"疼在哪个位置" → 扣分
                - 用户说"有高血压"，后面又问"以前有高血压吗" → 扣分

                【对话记录】
                %s

                请仔细检查对话中是否存在重复提问的情况。

                请返回 JSON 格式：
                {
                  "score": 分数（整数，0-40），
                  "reason": "评分理由，说明发现了几次重复提问，具体是什么"
                }
                """.formatted(conversationLog);

        return callLLMJudge(prompt, 40);
    }

    /**
     * 维度2：信息完整度（20分）
     * 评估问诊是否覆盖了对分诊决策有决定意义的全部关键信息
     */
    private Integer scoreInformationCompleteness(String conversationLog, String standardDialogue) {
        String prompt = """
                你是一个医疗问诊质量评估专家。请评估以下对话的【信息完整度】。

                【评分标准】
                - 17-20分：完整，覆盖全部关键鉴别诊断维度，不遗漏必问信息
                - 11-16分：基本完整，覆盖大部分维度，缺失1个非核心信息
                - 6-10分：部分缺失，缺少1-2个对分诊较关键的信息
                - 0-5分：严重缺失，缺少核心鉴别信息，可能导致分诊结论偏差

                【评分方法】
                对照标准对话中询问的信息维度，检查实际对话是否都覆盖了。
                常见必问维度包括：病程、性质、位置、程度、诱因、伴随症状、既往史等。

                【标准对话】（参考）
                %s

                【实际对话】
                %s

                请对比标准对话和实际对话，判断实际对话是否收集了足够的关键信息。

                请返回 JSON 格式：
                {
                  "score": 分数（整数，0-20），
                  "reason": "评分理由，说明缺失了哪些关键信息（如有）"
                }
                """.formatted(standardDialogue, conversationLog);

        return callLLMJudge(prompt, 20);
    }

    /**
     * 维度3：对话轮次合理性（15分）
     * 评估追问轮次是否适中
     */
    private Integer scoreConversationTurns(int actualTurns, String diseaseName) {
        // 判断是否为急症
        boolean isEmergency = isEmergencyCase(diseaseName);

        String prompt = """
                你是一个医疗问诊质量评估专家。请评估以下对话的【对话轮次合理性】。

                【评分标准】
                - 13-15分：合理，7-8轮追问，信息量刚好支撑分诊决策
                - 9-12分：可接受，5-6轮或9-10轮，信息略有不足或略有冗余
                - 5-8分：偏少/偏多，≤4轮（漏信息）或≥11轮（冗余）
                - 0-4分：严重不合理，≤2轮基本没问或≥15轮拖沓

                【基准参考】
                - 常见症状（腹泻、头痛、咳嗽）：6-8轮
                - 复杂症状（胸痛、腹痛鉴别）：7-10轮
                - 紧急情况（心梗、脑卒中）：3-5轮即触发告警（轮次少不扣分，因合理）
                - 极简单情况（体检异常咨询）：4-6轮

                【特殊规则】
                如果是急症（如心梗、脑卒中、严重外伤等），轮次少不扣分，因为快速识别风险是合理的。

                【实际情况】
                - 疾病名称：%s
                - 是否急症：%s
                - 实际轮次：%d轮

                请根据疾病类型和实际轮次，判断轮次是否合理。

                请返回 JSON 格式：
                {
                  "score": 分数（整数，0-15），
                  "reason": "评分理由，说明轮次是否合理"
                }
                """.formatted(diseaseName, isEmergency ? "是" : "否", actualTurns);

        return callLLMJudge(prompt, 15);
    }

    /**
     * 维度4：逻辑连贯性（15分）
     * 评估追问顺序是否符合从主诉→鉴别诊断的临床推理逻辑
     */
    private Integer scoreLogicCoherence(String conversationLog) {
        String prompt = """
                你是一个医疗问诊质量评估专家。请评估以下对话的【逻辑连贯性】。

                【评分标准】
                - 13-15分：优秀，逻辑递进清晰，先问"是什么"→"什么感觉"→"多久了"→"伴随什么"→"为什么"，完全符合临床思维
                - 9-12分：良好，基本符合逻辑顺序，偶有1-2处顺序可优化
                - 5-8分：一般，逻辑顺序有较明显问题，或跳跃感强
                - 0-4分：差，逻辑混乱，问题之间无关联，或关键鉴别问题放在了最后

                【推荐顺序】
                主诉 → 部位 → 时间/病程 → 性质 → 程度 → 诱因 → 伴随症状 → 危险因素 → 既往史

                【特别说明】
                在紧急场景中，把关键鉴别问题前置不算逻辑混乱，而是合理的临床优先级。
                例如：用户说"胸口疼，喘不上气"，应该优先问"疼多久了"+"往哪放射"+"出冷汗吗"，
                而不是先问"几岁"+"吃药了吗"。

                【对话记录】
                %s

                请评估对话的逻辑顺序是否合理，问题之间是否有清晰的递进关系。

                请返回 JSON 格式：
                {
                  "score": 分数（整数，0-15），
                  "reason": "评分理由，说明逻辑顺序是否合理"
                }
                """.formatted(conversationLog);

        return callLLMJudge(prompt, 15);
    }

    /**
     * 维度5：选项推送率（10分）
     * 评估每轮追问是否都推送了合理的候选选项，且包含"其他"兜底
     */
    private Integer scoreOptionQuality(String conversationLog) {
        String prompt = """
                你是一个医疗问诊质量评估专家。请评估以下对话的【选项推送率】。

                【评分标准】
                - 9-10分：优秀，每轮都推送选项，选项覆盖主要答案分支且措辞合理，均含"其他"兜底
                - 6-8分：良好，绝大多数轮次推送选项，偶有1-2轮漏推，选项基本合理
                - 3-5分：一般，部分轮次未推送选项，或选项设计有缺陷（选项不全/不符）
                - 0-2分：差，很少推送选项，或完全没有选项推送机制

                【选项设计扣分项】
                - 选项中没有"其他"兜底选项 → 每缺一轮扣1分
                - 选项与问题不匹配 → 每次扣2分
                - 选项遗漏常见分支 → 每次扣1分
                - 所有选项都不符合用户情况（用户只能选"其他"）→ 该轮选项设计不合理扣2分
                - 连续3轮以上没有选项推送 → 额外扣3分

                【对话记录】
                %s

                请检查每一轮对话是否推送了选项，选项设计是否合理。
                对话记录中，选项通常以"选项：[...]"的格式出现。

                请返回 JSON 格式：
                {
                  "score": 分数（整数，0-10），
                  "reason": "评分理由，说明选项推送情况和存在的问题"
                }
                """.formatted(conversationLog);

        return callLLMJudge(prompt, 10);
    }

    /**
     * 调用 LLM Judge 并解析分数
     */
    private Integer callLLMJudge(String prompt, int maxScore) {
        try {
            ChatRequest request = ChatRequest.builder()
                    .messages(List.of(ChatMessage.user(prompt)))
                    .temperature(0.1)
                    .topP(0.3)
                    .maxTokens(500)
                    .build();

            String response = llmService.chat(request);
            return parseJsonScore(response, maxScore);

        } catch (Exception ex) {
            log.error("LLM Judge 调用失败", ex);
            return 0;
        }
    }

    /**
     * 从 JSON 响应中解析分数
     */
    private Integer parseJsonScore(String response, int maxScore) {
        try {
            // 尝试提取 JSON 部分
            String jsonStr = extractJson(response);
            JsonNode jsonNode = objectMapper.readTree(jsonStr);

            int score = jsonNode.get("score").asInt();
            String reason = jsonNode.get("reason").asText();

            log.debug("LLM Judge 评分: score={}, reason={}", score, reason);

            // 确保分数在合理范围内
            return Math.max(0, Math.min(score, maxScore));

        } catch (Exception ex) {
            log.warn("解析 JSON 分数失败，尝试提取数字: response={}", response, ex);
            // 降级：尝试直接提取数字
            return extractNumberScore(response, maxScore);
        }
    }

    /**
     * 从响应中提取 JSON 字符串
     */
    private String extractJson(String response) {
        // 如果响应包含 ```json 代码块，提取其中的内容
        if (response.contains("```json")) {
            int start = response.indexOf("```json") + 7;
            int end = response.indexOf("```", start);
            if (end > start) {
                return response.substring(start, end).trim();
            }
        }

        // 如果响应包含 { 和 }，提取 JSON 对象
        int start = response.indexOf('{');
        int end = response.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return response.substring(start, end + 1);
        }

        return response;
    }

    /**
     * 降级方案：直接从文本中提取数字
     */
    private Integer extractNumberScore(String response, int maxScore) {
        try {
            // 尝试匹配 "score": 数字 或 分数：数字
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(?:score|分数)[\":\\s]+(\\d+)");
            java.util.regex.Matcher matcher = pattern.matcher(response);
            if (matcher.find()) {
                int score = Integer.parseInt(matcher.group(1));
                return Math.max(0, Math.min(score, maxScore));
            }

            // 如果找不到，返回 0
            log.warn("无法从响应中提取分数，返回0分: {}", response);
            return 0;

        } catch (Exception ex) {
            log.error("提取数字分数失败", ex);
            return 0;
        }
    }

    /**
     * 判断是否为急症
     */
    private boolean isEmergencyCase(String diseaseName) {
        if (diseaseName == null) {
            return false;
        }

        String lowerCase = diseaseName.toLowerCase();
        return lowerCase.contains("心梗") ||
                lowerCase.contains("脑卒中") ||
                lowerCase.contains("中风") ||
                lowerCase.contains("休克") ||
                lowerCase.contains("大出血") ||
                lowerCase.contains("窒息") ||
                lowerCase.contains("急性") && (lowerCase.contains("心肌梗死") || lowerCase.contains("脑梗")) ||
                lowerCase.contains("严重外伤") ||
                lowerCase.contains("骨折") && lowerCase.contains("开放性");
    }
}
