

package com.nageoffer.ai.ragent.triage.battle;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 三路 battle 批量运行响应。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BattleRunResponse {

    private Integer caseCount;

    private Integer baselineCount;

    private Long elapsedMillis;

    private String reportPath;

    private String reportUrl;

    private List<BaselineSummary> summaries = new ArrayList<>();

    private List<CaseBattleResult> cases = new ArrayList<>();

    public static BattleRunResponse of(Integer caseCount,
                                       Integer baselineCount,
                                       Long elapsedMillis,
                                       List<BaselineSummary> summaries,
                                       List<CaseBattleResult> cases) {
        BattleRunResponse response = new BattleRunResponse();
        response.setCaseCount(caseCount);
        response.setBaselineCount(baselineCount);
        response.setElapsedMillis(elapsedMillis);
        response.setSummaries(summaries == null ? new ArrayList<>() : summaries);
        response.setCases(cases == null ? new ArrayList<>() : cases);
        return response;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CaseBattleResult {
        private String caseId;
        private String diseaseName;
        private String riskLabel;
        private String systemCategory;
        private String userInput;
        private List<BaselineResult> results = new ArrayList<>();

        public static CaseBattleResult of(BattleCaseLoader.BattleCase battleCase, List<BaselineResult> results) {
            CaseBattleResult caseBattleResult = new CaseBattleResult();
            caseBattleResult.setCaseId(battleCase.getCaseId());
            caseBattleResult.setDiseaseName(battleCase.getDiseaseName());
            caseBattleResult.setRiskLabel(battleCase.getRiskLabel());
            caseBattleResult.setSystemCategory(battleCase.getSystemCategory());
            caseBattleResult.setUserInput(battleCase.getUserInput());
            caseBattleResult.setResults(results == null ? new ArrayList<>() : results);
            return caseBattleResult;
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BaselineResult {
        private String baseline;
        private String action;
        private String message;
        private Integer riskLevel;
        private Long elapsedMillis;
        private Object data;
        private String conversationLog;
        private BattleScore score;
        private String error;

        public static BaselineResult success(String baseline,
                                             TriageResponseSnapshot snapshot,
                                             Long elapsedMillis,
                                             String conversationLog) {
            BaselineResult result = new BaselineResult();
            result.setBaseline(baseline);
            result.setAction(snapshot.getAction());
            result.setMessage(snapshot.getMessage());
            result.setRiskLevel(snapshot.getRiskLevel());
            result.setElapsedMillis(elapsedMillis);
            result.setData(snapshot.getData());
            result.setConversationLog(conversationLog);
            return result;
        }

        public static BaselineResult error(String baseline, Long elapsedMillis, String error) {
            BaselineResult result = new BaselineResult();
            result.setBaseline(baseline);
            result.setElapsedMillis(elapsedMillis);
            result.setError(error);
            return result;
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BaselineSummary {
        private String baseline;
        private Integer successCount;
        private Integer errorCount;
        private Double averageOutcomeScore;
        private Double averageProcessScore;
        private Double averageWeightedScore;
        private Double averageElapsedMillis;

        public static BaselineSummary of(String baseline,
                                         Integer successCount,
                                         Integer errorCount,
                                         Double averageOutcomeScore,
                                         Double averageProcessScore,
                                         Double averageWeightedScore,
                                         Double averageElapsedMillis) {
            BaselineSummary summary = new BaselineSummary();
            summary.setBaseline(baseline);
            summary.setSuccessCount(successCount);
            summary.setErrorCount(errorCount);
            summary.setAverageOutcomeScore(averageOutcomeScore);
            summary.setAverageProcessScore(averageProcessScore);
            summary.setAverageWeightedScore(averageWeightedScore);
            summary.setAverageElapsedMillis(averageElapsedMillis);
            return summary;
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TriageResponseSnapshot {
        private String action;
        private String message;
        private Integer riskLevel;
        private Object data;
    }
}
