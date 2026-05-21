

package com.nageoffer.ai.ragent.triage.controller.vo;



import io.swagger.v3.oas.annotations.media.Schema;
import com.nageoffer.ai.ragent.triage.model.RiskLevel;
import com.nageoffer.ai.ragent.triage.model.Symptom;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 病历报告动作对应的数据载荷。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "病历报告动作对应的数据载荷。")
public class TriageReportData {

@Schema(description = "sessionId")
    private String sessionId;

@Schema(description = "report")
    private String report;

@Schema(description = "riskAssessment")
    private RiskLevel riskAssessment;

    @Builder.Default
    private List<Symptom> extractedSymptoms = new ArrayList<>();

    @Schema(description = "推荐就诊科室")
    private String recommendedDepartment;

    @Schema(description = "科室推荐理由")
    private String departmentReason;

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("【分诊报告】\n");

        if (riskAssessment != null) {
            sb.append("风险等级: ").append(getRiskLevelText(riskAssessment.getLevel())).append("\n");
            sb.append("风险评分: ").append(riskAssessment.getScore()).append("\n");
            if (riskAssessment.getEvidence() != null) {
                sb.append("风险依据: ").append(riskAssessment.getEvidence()).append("\n");
            }
        }

        if (recommendedDepartment != null) {
            sb.append("建议科室: ").append(recommendedDepartment).append("\n");
        }

        if (departmentReason != null) {
            sb.append("科室推荐理由: ").append(departmentReason).append("\n");
        }

        if (extractedSymptoms != null && !extractedSymptoms.isEmpty()) {
            sb.append("提取的症状: ");
            for (int i = 0; i < extractedSymptoms.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(extractedSymptoms.get(i).getName());
            }
            sb.append("\n");
        }

        if (report != null) {
            sb.append("\n完整报告:\n").append(report).append("\n");
        }

        return sb.toString();
    }

    private String getRiskLevelText(Integer level) {
        if (level == null) return "未知";
        return switch (level) {
            case 1 -> "低风险";
            case 2 -> "中风险";
            case 3 -> "高风险";
            case 4 -> "紧急";
            default -> "未知";
        };
    }
}
