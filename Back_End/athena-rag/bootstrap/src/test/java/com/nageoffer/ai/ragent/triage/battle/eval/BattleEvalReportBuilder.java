

package com.nageoffer.ai.ragent.triage.battle.eval;

import com.nageoffer.ai.ragent.triage.eval.TriageEvalReport;
import com.nageoffer.ai.ragent.triage.eval.TriageEvalResult;
import com.nageoffer.ai.ragent.triage.eval.TriageEvalScore;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 复用原 eval 报告模型，单独为 battle 构建报告统计。
 */
@Component
public class BattleEvalReportBuilder {

    public TriageEvalReport buildReport(String suiteName, List<TriageEvalResult> results) {
        int passed = 0;
        int failed = 0;
        int errors = 0;
        int totalScore = 0;
        int maxScore = 0;
        int minScore = results.isEmpty() ? 0 : 100;

        int sumRiskLevel = 0;
        int sumDepartment = 0;
        int sumChiefComplaint = 0;
        int sumSymptoms = 0;
        int sumRiskAnalysis = 0;
        int sumActionAdvice = 0;
        int sumMemoryConsistency = 0;
        int sumInformationCompleteness = 0;
        int sumConversationTurns = 0;
        int sumLogicCoherence = 0;
        int sumOptionQuality = 0;

        for (TriageEvalResult result : results) {
            switch (result.getStatus()) {
                case "pass" -> passed++;
                case "fail" -> failed++;
                case "error" -> errors++;
                default -> failed++;
            }

            int score = result.getTotalScore() == null ? 0 : result.getTotalScore();
            totalScore += score;
            maxScore = Math.max(maxScore, score);
            minScore = Math.min(minScore, score);

            if (result.getScores() != null) {
                TriageEvalScore scores = result.getScores();
                sumRiskLevel += value(scores.getRiskLevelScore());
                sumDepartment += value(scores.getDepartmentScore());
                sumChiefComplaint += value(scores.getChiefComplaintScore());
                sumSymptoms += value(scores.getSymptomsScore());
                sumRiskAnalysis += value(scores.getRiskAnalysisScore());
                sumActionAdvice += value(scores.getActionAdviceScore());
                sumMemoryConsistency += value(scores.getMemoryConsistencyScore());
                sumInformationCompleteness += value(scores.getInformationCompletenessScore());
                sumConversationTurns += value(scores.getConversationTurnsScore());
                sumLogicCoherence += value(scores.getLogicCoherenceScore());
                sumOptionQuality += value(scores.getOptionQualityScore());
            }
        }

        TriageEvalScore averageScores = TriageEvalScore.builder()
                .riskLevelScore(sumRiskLevel)
                .departmentScore(sumDepartment)
                .chiefComplaintScore(sumChiefComplaint)
                .symptomsScore(sumSymptoms)
                .riskAnalysisScore(sumRiskAnalysis)
                .actionAdviceScore(sumActionAdvice)
                .memoryConsistencyScore(sumMemoryConsistency)
                .informationCompletenessScore(sumInformationCompleteness)
                .conversationTurnsScore(sumConversationTurns)
                .logicCoherenceScore(sumLogicCoherence)
                .optionQualityScore(sumOptionQuality)
                .build();

        return TriageEvalReport.builder()
                .suiteName(suiteName)
                .total(results.size())
                .passed(passed)
                .failed(failed)
                .errors(errors)
                .averageScore(results.isEmpty() ? 0.0 : totalScore / (double) results.size())
                .maxScore(maxScore)
                .minScore(minScore)
                .averageScores(averageScores)
                .results(results)
                .build();
    }

    private int value(Integer value) {
        return value == null ? 0 : value;
    }
}
