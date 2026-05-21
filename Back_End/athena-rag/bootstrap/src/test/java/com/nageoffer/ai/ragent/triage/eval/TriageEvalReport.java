

package com.nageoffer.ai.ragent.triage.eval;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 分诊评测报告
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TriageEvalReport {

    /**
     * 测试套件名称
     */
    private String suiteName;

    /**
     * 总用例数
     */
    private Integer total;

    /**
     * 通过数（总分>=60）
     */
    private Integer passed;

    /**
     * 失败数（总分<60）
     */
    private Integer failed;

    /**
     * 错误数（执行异常）
     */
    private Integer errors;

    /**
     * 平均分
     */
    private Double averageScore;

    /**
     * 最高分
     */
    private Integer maxScore;

    /**
     * 最低分
     */
    private Integer minScore;

    /**
     * 各维度平均分
     */
    private TriageEvalScore averageScores;

    /**
     * 详细结果列表
     */
    private List<TriageEvalResult> results;
}
