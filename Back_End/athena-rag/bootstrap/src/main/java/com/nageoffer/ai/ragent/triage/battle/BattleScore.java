

package com.nageoffer.ai.ragent.triage.battle;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * battle LLM Judge 双轨评分。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BattleScore {

    private Integer outcomeScore;
    private Integer processScore;
    private Double weightedScore;

    private Integer riskLevelScore;
    private Integer departmentScore;
    private Integer chiefComplaintScore;
    private Integer symptomsScore;
    private Integer riskAnalysisScore;
    private Integer actionAdviceScore;

    private Integer memoryConsistencyScore;
    private Integer informationCompletenessScore;
    private Integer conversationTurnsScore;
    private Integer logicCoherenceScore;
    private Integer optionQualityScore;

    private String reason;
}
