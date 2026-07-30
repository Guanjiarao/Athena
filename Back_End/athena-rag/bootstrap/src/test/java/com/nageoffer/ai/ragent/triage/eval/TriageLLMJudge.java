

package com.nageoffer.ai.ragent.triage.eval;

import com.nageoffer.ai.ragent.framework.convention.ChatMessage;
import com.nageoffer.ai.ragent.infra.chat.LLMService;
import com.nageoffer.ai.ragent.framework.convention.ChatRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LLM Judge 打分器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TriageLLMJudge {

    private final LLMService llmService;
    private final TriageProcessJudge processJudge;

    private static final Pattern SCORE_PATTERN = Pattern.compile("(\\d+)分");

    /**
     * 使用LLM对分诊结果进行打分（结果评分 + 过程评分）
     */
    public TriageEvalResult judge(TriageEvalCase evalCase, TriageEvalResult result) {
        if ("error".equals(result.getStatus())) {
            result.setTotalScore(0);
            result.setOutcomeScore(0);
            result.setProcessScore(0);
            result.setWeightedScore(0.0);
            return result;
        }

        try {
            // 解析实际对话轮次
            int actualTurns = extractActualTurns(result.getActualResponse());
            result.setActualTurns(actualTurns);

            // 检测是否触发红旗
            boolean isRedFlag = result.getActualResponse().contains("Action: TRIGGER_WARNING");
            result.setIsRedFlag(isRedFlag);

            // 1. 结果评分（6个维度，共100分）
            TriageEvalScore outcomeScores = judgeOutcome(evalCase, result);

            // 2. 过程评分（5个维度，共100分）
            TriageEvalScore processScores = processJudge.judgeProcess(
                evalCase,
                result.getActualResponse(),
                actualTurns
            );

            // 3. 合并评分
            TriageEvalScore mergedScores = mergeScores(outcomeScores, processScores);

            // 4. 计算总分
            int outcomeTotal = calculateOutcomeTotal(outcomeScores);
            int processTotal = calculateProcessTotal(processScores);
            double weightedTotal = processTotal * 0.7 + outcomeTotal * 0.3;

            log.info("评分完成 - caseId={}, 结果分={}, 过程分={}, 加权总分={}",
                evalCase.getCaseId(), outcomeTotal, processTotal, weightedTotal);

            // 5. 设置结果
            result.setScores(mergedScores);
            result.setOutcomeScore(outcomeTotal);
            result.setProcessScore(processTotal);
            result.setWeightedScore(weightedTotal);
            result.setTotalScore((int) Math.round(weightedTotal));

            // 6. 判断通过标准：加权总分 >= 60
            result.setStatus(weightedTotal >= 60 ? "pass" : "fail");

            return result;

        } catch (Exception ex) {
            log.error("LLM Judge 打分失败: caseId={}", evalCase.getCaseId(), ex);
            result.setStatus("error");
            result.setErrorMessage("打分失败: " + ex.getMessage());
            result.setTotalScore(0);
            result.setOutcomeScore(0);
            result.setProcessScore(0);
            result.setWeightedScore(0.0);
            return result;
        }
    }

    /**
     * 结果评分（6个维度）
     */
    private TriageEvalScore judgeOutcome(TriageEvalCase evalCase, TriageEvalResult result) {
        // 对6个维度分别打分，直接传入完整对话记录
        Integer riskLevelScore = scoreDimension("风险等级",
                evalCase.getCriteria().getRiskLevel(),
                result.getActualResponse(), 20);

        Integer departmentScore = scoreDimension("建议科室",
                evalCase.getCriteria().getDepartment(),
                result.getActualResponse(), 15);

        Integer chiefComplaintScore = scoreDimension("主诉提炼",
                evalCase.getCriteria().getChiefComplaint(),
                result.getActualResponse(), 15);

        Integer symptomsScore = scoreDimension("症状提取",
                evalCase.getCriteria().getSymptoms(),
                result.getActualResponse(), 20);

        Integer riskAnalysisScore = scoreDimension("风险分析",
                evalCase.getCriteria().getRiskAnalysis(),
                result.getActualResponse(), 15);

        Integer actionAdviceScore = scoreDimension("行动建议",
                evalCase.getCriteria().getActionAdvice(),
                result.getActualResponse(), 15);

        return TriageEvalScore.builder()
                .riskLevelScore(riskLevelScore)
                .departmentScore(departmentScore)
                .chiefComplaintScore(chiefComplaintScore)
                .symptomsScore(symptomsScore)
                .riskAnalysisScore(riskAnalysisScore)
                .actionAdviceScore(actionAdviceScore)
                .build();
    }

    /**
     * 合并结果评分和过程评分
     */
    private TriageEvalScore mergeScores(TriageEvalScore outcomeScores, TriageEvalScore processScores) {
        return TriageEvalScore.builder()
                // 结果评分字段
                .riskLevelScore(outcomeScores.getRiskLevelScore())
                .departmentScore(outcomeScores.getDepartmentScore())
                .chiefComplaintScore(outcomeScores.getChiefComplaintScore())
                .symptomsScore(outcomeScores.getSymptomsScore())
                .riskAnalysisScore(outcomeScores.getRiskAnalysisScore())
                .actionAdviceScore(outcomeScores.getActionAdviceScore())
                // 过程评分字段
                .memoryConsistencyScore(processScores.getMemoryConsistencyScore())
                .informationCompletenessScore(processScores.getInformationCompletenessScore())
                .conversationTurnsScore(processScores.getConversationTurnsScore())
                .logicCoherenceScore(processScores.getLogicCoherenceScore())
                .optionQualityScore(processScores.getOptionQualityScore())
                .build();
    }

    /**
     * 计算结果评分总分（6个维度，满分100）
     */
    private int calculateOutcomeTotal(TriageEvalScore scores) {
        return scores.getRiskLevelScore() +
               scores.getDepartmentScore() +
               scores.getChiefComplaintScore() +
               scores.getSymptomsScore() +
               scores.getRiskAnalysisScore() +
               scores.getActionAdviceScore();
    }

    /**
     * 计算过程评分总分（5个维度，满分100）
     */
    private int calculateProcessTotal(TriageEvalScore scores) {
        return scores.getMemoryConsistencyScore() +
               scores.getInformationCompletenessScore() +
               scores.getConversationTurnsScore() +
               scores.getLogicCoherenceScore() +
               scores.getOptionQualityScore();
    }

    /**
     * 对单个维度打分
     */
    private Integer scoreDimension(String dimensionName, String expected, String actual, int maxScore) {
        String prompt = buildScoringPrompt(dimensionName, expected, actual, maxScore);

        ChatRequest request = ChatRequest.builder()
                .messages(List.of(ChatMessage.user(prompt)))
                .temperature(0.1)
                .topP(0.3)
                .maxTokens(200)
                .build();

        String response = llmService.chat(request);
        return parseScore(response, maxScore);
    }

    /**
     * 构建打分提示词
     */
    private String buildScoringPrompt(String dimensionName, String expected, String actual, int maxScore) {
        return String.format("""
                你是一个医疗预分诊系统的评测专家。请对以下维度进行打分：

                【评分维度】：%s（满分%d分）

                【标准答案】：
                %s

                【系统实际输出】（完整对话记录）：
                %s

                【评分标准】：
                - 完全符合：%d分（100%%）
                - 比较符合：%d分（80%%）
                - 符合：%d分（60%%）
                - 比较不符合：%d分（40%%）
                - 非常不符合：%d分（20%%）
                - 完全不符合：0分

                请仔细阅读系统的完整对话记录，从中提取与"%s"相关的信息，然后与标准答案进行对比。
                系统的输出可能是自然语言描述，不一定有明确的标记，你需要理解其语义。

                只需要输出分数，格式为：XX分
                """,
                dimensionName, maxScore,
                expected,
                actual,
                maxScore,
                (int)(maxScore * 0.8),
                (int)(maxScore * 0.6),
                (int)(maxScore * 0.4),
                (int)(maxScore * 0.2),
                dimensionName);
    }

    /**
     * 从LLM响应中解析分数
     */
    private Integer parseScore(String response, int maxScore) {
        Matcher matcher = SCORE_PATTERN.matcher(response);
        if (matcher.find()) {
            int score = Integer.parseInt(matcher.group(1));
            return Math.min(score, maxScore);
        }
        log.warn("无法解析分数，返回0分: {}", response);
        return 0;
    }

    /**
     * 从对话记录中提取实际轮次
     */
    private int extractActualTurns(String actualResponse) {
        Pattern turnPattern = Pattern.compile("【第(\\d+)轮】");
        Matcher matcher = turnPattern.matcher(actualResponse);
        int maxTurn = 0;

        while (matcher.find()) {
            int turn = Integer.parseInt(matcher.group(1));
            maxTurn = Math.max(maxTurn, turn);
        }

        return maxTurn;
    }
}
