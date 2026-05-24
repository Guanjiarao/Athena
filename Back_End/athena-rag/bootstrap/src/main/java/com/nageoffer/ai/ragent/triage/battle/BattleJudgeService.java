

package com.nageoffer.ai.ragent.triage.battle;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nageoffer.ai.ragent.framework.convention.ChatMessage;
import com.nageoffer.ai.ragent.triage.service.TriageModelGateway;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * battle 专用 LLM Judge，按预分诊双轨评分细则输出结构化分数。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BattleJudgeService {

    private final TriageModelGateway triageModelGateway;
    private final ObjectMapper objectMapper;

    public BattleScore judge(BattleCaseLoader.BattleCase battleCase, BattleRunResponse.BaselineResult result) {
        if (result.getError() != null && !result.getError().isBlank()) {
            return BattleScore.builder()
                    .outcomeScore(0)
                    .processScore(0)
                    .weightedScore(0.0D)
                    .reason("运行失败，无法评分：" + result.getError())
                    .build();
        }
        String raw = triageModelGateway.chatWithTextModel(
                List.of(ChatMessage.system(systemPrompt()), ChatMessage.user(userPrompt(battleCase, result))),
                0.1,
                0.3,
                1200
        );
        try {
            String json = extractJson(raw);
            BattleScore score = objectMapper.readValue(json, BattleScore.class);
            score.setOutcomeScore(safe(score.getOutcomeScore()));
            score.setProcessScore(safe(score.getProcessScore()));
            if (score.getWeightedScore() == null) {
                score.setWeightedScore(score.getProcessScore() * 0.7D + score.getOutcomeScore() * 0.3D);
            }
            return score;
        } catch (Exception ex) {
            log.warn("battle LLM Judge 解析失败: caseId={}, baseline={}, raw={}", battleCase.getCaseId(), result.getBaseline(), raw, ex);
            return BattleScore.builder()
                    .outcomeScore(0)
                    .processScore(0)
                    .weightedScore(0.0D)
                    .reason("评分解析失败：" + ex.getMessage())
                    .build();
        }
    }

    private String systemPrompt() {
        return """
                你是医疗预分诊系统评测专家。你必须严格按双轨评分细则打分，并只输出 JSON。

                结果评分 100 分：
                - riskLevelScore 风险等级 20 分
                - departmentScore 建议科室 15 分
                - chiefComplaintScore 主诉提炼 15 分
                - symptomsScore 症状提取 20 分
                - riskAnalysisScore 风险分析 15 分
                - actionAdviceScore 行动建议 15 分

                过程评分 100 分：
                - memoryConsistencyScore 幻觉/记忆一致性 30 分
                - informationCompletenessScore 信息完整度 20 分
                - conversationTurnsScore 对话轮次合理性 15 分
                - logicCoherenceScore 逻辑连贯性 15 分
                - optionQualityScore 选项推送率 20 分

                weightedScore = processScore * 0.7 + outcomeScore * 0.3。
                不要因为表达格式不同而机械扣分，应根据语义判断。不要输出 Markdown。
                """;
    }

    private String userPrompt(BattleCaseLoader.BattleCase battleCase, BattleRunResponse.BaselineResult result) {
        BattleCaseLoader.BattleCriteria criteria = battleCase.getCriteria() == null
                ? BattleCaseLoader.BattleCriteria.builder().build()
                : battleCase.getCriteria();
        return """
                请对下面一个 baseline 的预分诊表现评分。

                【用例】
                caseId：%s
                疾病/场景：%s
                风险标签：%s
                系统分类：%s
                初始输入：%s

                【标准答案/结果评分参考】
                风险等级：%s
                建议科室：%s
                主诉提炼：%s
                症状提取：%s
                风险分析：%s
                行动建议：%s

                【标准对话/过程评分参考】
                %s

                【实际对话和输出】
                baseline：%s
                action：%s
                riskLevel：%s
                message：%s
                data：%s
                conversationLog：
                %s

                请返回且只返回如下 JSON：
                {
                  "riskLevelScore": 0,
                  "departmentScore": 0,
                  "chiefComplaintScore": 0,
                  "symptomsScore": 0,
                  "riskAnalysisScore": 0,
                  "actionAdviceScore": 0,
                  "memoryConsistencyScore": 0,
                  "informationCompletenessScore": 0,
                  "conversationTurnsScore": 0,
                  "logicCoherenceScore": 0,
                  "optionQualityScore": 0,
                  "outcomeScore": 0,
                  "processScore": 0,
                  "weightedScore": 0.0,
                  "reason": "简要说明主要扣分原因"
                }
                """.formatted(
                battleCase.getCaseId(),
                battleCase.getDiseaseName(),
                battleCase.getRiskLabel(),
                battleCase.getSystemCategory(),
                battleCase.getUserInput(),
                criteria.getRiskLevel(),
                criteria.getDepartment(),
                criteria.getChiefComplaint(),
                criteria.getSymptoms(),
                criteria.getRiskAnalysis(),
                criteria.getActionAdvice(),
                battleCase.getStandardDialogue(),
                result.getBaseline(),
                result.getAction(),
                result.getRiskLevel(),
                result.getMessage(),
                result.getData(),
                result.getConversationLog()
        );
    }

    private String extractJson(String raw) throws com.fasterxml.jackson.core.JsonProcessingException {
        if (raw == null || raw.isBlank()) {
            return "{}";
        }
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start >= 0 && end > start) {
            String json = raw.substring(start, end + 1);
            JsonNode ignored = objectMapper.readTree(json);
            return json;
        }
        return raw;
    }

    private int safe(Integer value) {
        return value == null ? 0 : value;
    }
}
