

package com.nageoffer.ai.ragent.triage.eval;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 分诊评测标准（6个维度）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TriageEvalCriteria {

    /**
     * 风险等级标准答案（20分）
     */
    private String riskLevel;

    /**
     * 建议科室标准答案（15分）
     */
    private String department;

    /**
     * 主诉提炼标准答案（15分）
     */
    private String chiefComplaint;

    /**
     * 症状提取标准答案（20分）
     */
    private String symptoms;

    /**
     * 风险分析标准答案（15分）
     */
    private String riskAnalysis;

    /**
     * 行动建议标准答案（15分）
     */
    private String actionAdvice;
}
