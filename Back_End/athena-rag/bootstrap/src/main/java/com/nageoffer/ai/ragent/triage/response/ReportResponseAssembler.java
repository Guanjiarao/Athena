

package com.nageoffer.ai.ragent.triage.response;

import com.nageoffer.ai.ragent.triage.controller.vo.TriageAnalyzeResponse;
import com.nageoffer.ai.ragent.triage.controller.vo.TriageReportData;
import com.nageoffer.ai.ragent.triage.model.TriageAction;
import com.nageoffer.ai.ragent.triage.model.TriageContext;
import org.springframework.stereotype.Component;

@Component
public class ReportResponseAssembler {

    public TriageAnalyzeResponse assemble(TriageContext context) {
        DepartmentRecommender recommender = new DepartmentRecommender();
        DepartmentRecommender.DepartmentRecommendation recommendation = recommender.recommend(context);

        TriageReportData data = TriageReportData.builder()
                .sessionId(context.getSessionId())
                .report(context.getFinalReply())
                .riskAssessment(context.getRiskAssessment())
                .extractedSymptoms(context.getExtractedSymptoms())
                .recommendedDepartment(recommendation.getDepartment())
                .departmentReason(recommendation.getReason())
                .build();
        return TriageAnalyzeResponse.builder()
                .action(TriageAction.GENERATE_REPORT.name())
                .data(data)
                .message(context.getFinalReply())
                .riskLevel(context.getRiskAssessment() == null ? 0 : context.getRiskAssessment().getLevel())
                .build();
    }
}
