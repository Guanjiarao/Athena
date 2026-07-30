

package com.nageoffer.ai.ragent.triage.response;

import com.nageoffer.ai.ragent.triage.controller.vo.TriageAnalyzeResponse;
import com.nageoffer.ai.ragent.triage.controller.vo.TriageWarningData;
import com.nageoffer.ai.ragent.triage.model.TriageAction;
import com.nageoffer.ai.ragent.triage.model.TriageContext;
import org.springframework.stereotype.Component;

@Component
public class WarningResponseAssembler {

    public TriageAnalyzeResponse assemble(TriageContext context) {
        DepartmentRecommender recommender = new DepartmentRecommender();
        DepartmentRecommender.DepartmentRecommendation recommendation = recommender.recommend(context);

        if (context.getRiskAssessment() != null && context.getRiskAssessment().getLevel() >= 3) {
            recommendation = new DepartmentRecommender.DepartmentRecommendation(
                    "急诊科",
                    "存在高风险症状，建议立即前往急诊科就诊"
            );
        }

        TriageWarningData data = TriageWarningData.builder()
                .sessionId(context.getSessionId())
                .riskAssessment(context.getRiskAssessment())
                .extractedSymptoms(context.getExtractedSymptoms())
                .warningText(context.getFinalReply())
                .emergencyGuidance("如症状持续加重，或出现呼吸困难、意识变化、明显出血等情况，请立即前往急诊。")
                .recommendedDepartment(recommendation.getDepartment())
                .departmentReason(recommendation.getReason())
                .build();
        return TriageAnalyzeResponse.builder()
                .action(TriageAction.TRIGGER_WARNING.name())
                .data(data)
                .message(context.getFinalReply())
                .riskLevel(context.getRiskAssessment() == null ? 0 : context.getRiskAssessment().getLevel())
                .build();
    }
}
