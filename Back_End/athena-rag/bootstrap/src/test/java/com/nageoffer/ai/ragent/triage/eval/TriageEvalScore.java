

package com.nageoffer.ai.ragent.triage.eval;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 分诊评测得分（过程评分5个维度 + 结果评分6个维度）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TriageEvalScore {

    // ==================== 过程评分（满分100） ====================

    /**
     * 幻觉/记忆一致性得分（满分40）
     */
    private Integer memoryConsistencyScore;

    /**
     * 信息完整度得分（满分20）
     */
    private Integer informationCompletenessScore;

    /**
     * 对话轮次合理性得分（满分15）
     */
    private Integer conversationTurnsScore;

    /**
     * 逻辑连贯性得分（满分15）
     */
    private Integer logicCoherenceScore;

    /**
     * 选项推送率得分（满分10）
     */
    private Integer optionQualityScore;

    // ==================== 结果评分（满分100） ====================

    /**
     * 风险等级得分（满分20）
     */
    private Integer riskLevelScore;

    /**
     * 建议科室得分（满分15）
     */
    private Integer departmentScore;

    /**
     * 主诉提炼得分（满分15）
     */
    private Integer chiefComplaintScore;

    /**
     * 症状提取得分（满分20）
     */
    private Integer symptomsScore;

    /**
     * 风险分析得分（满分15）
     */
    private Integer riskAnalysisScore;

    /**
     * 行动建议得分（满分15）
     */
    private Integer actionAdviceScore;
}
