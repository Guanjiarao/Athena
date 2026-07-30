

package com.nageoffer.ai.ragent.triage.eval;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 分诊评测结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TriageEvalResult {

    /**
     * 用例ID
     */
    private String caseId;

    /**
     * 疾病名称
     */
    private String diseaseName;

    /**
     * 用户输入
     */
    private String userInput;

    /**
     * 系统实际返回的完整响应
     */
    private String actualResponse;

    /**
     * 提取的风险等级
     */
    private String actualRiskLevel;

    /**
     * 提取的建议科室
     */
    private String actualDepartment;

    /**
     * 提取的主诉提炼
     */
    private String actualChiefComplaint;

    /**
     * 提取的症状提取
     */
    private String actualSymptoms;

    /**
     * 提取的风险分析
     */
    private String actualRiskAnalysis;

    /**
     * 提取的行动建议
     */
    private String actualActionAdvice;

    /**
     * 实际对话轮次
     */
    private Integer actualTurns;

    /**
     * 是否触发红旗
     */
    private Boolean isRedFlag;

    /**
     * 各维度得分
     */
    private TriageEvalScore scores;

    /**
     * 总分
     */
    private Integer totalScore;

    /**
     * 过程评分总分（满分100，权重70%）
     * 由5个过程维度加权计算：幻觉/记忆一致性30 + 信息完整度20 + 对话轮次合理性15 + 逻辑连贯性15 + 选项推送率20
     */
    private Integer processScore;

    /**
     * 结果评分总分（满分100，权重30%）
     * 由6个结果维度加权计算：风险等级20 + 建议科室15 + 主诉提炼15 + 症状提取20 + 风险分析15 + 行动建议15
     */
    private Integer outcomeScore;

    /**
     * 加权总分（满分100）
     * 计算公式：processScore * 0.7 + outcomeScore * 0.3
     */
    private Double weightedScore;

    /**
     * 评测状态：pass/fail/error
     */
    private String status;

    /**
     * 错误信息（如果有）
     */
    private String errorMessage;
}
