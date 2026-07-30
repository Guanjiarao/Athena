

package com.nageoffer.ai.ragent.triage.response;

import com.nageoffer.ai.ragent.triage.model.TriageContext;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Response-package facade for department recommendation.
 */
public class DepartmentRecommender {

    private final com.nageoffer.ai.ragent.triage.engine.DepartmentRecommender delegate =
            new com.nageoffer.ai.ragent.triage.engine.DepartmentRecommender();

    @Data
    @AllArgsConstructor
    public static class DepartmentRecommendation {
        private String department;
        private String reason;
    }

    public DepartmentRecommendation recommend(TriageContext context) {
        com.nageoffer.ai.ragent.triage.engine.DepartmentRecommender.DepartmentRecommendation recommendation = delegate.recommend(context);
        return new DepartmentRecommendation(recommendation.getDepartment(), recommendation.getReason());
    }
}
