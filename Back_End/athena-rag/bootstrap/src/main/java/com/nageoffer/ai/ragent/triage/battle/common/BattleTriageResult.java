

package com.nageoffer.ai.ragent.triage.battle.common;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * battle 基线链路统一 LLM 输出结构。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BattleTriageResult {

    /** ASK_CLARIFICATION / GENERATE_REPORT / WARN */
    private String action;

    private String message;

    private Integer riskLevel;

    private String recommendedDepartment;

    private String departmentReason;

    private String report;

    @Builder.Default
    private List<BattleQuestion> questions = new ArrayList<>();

    @Builder.Default
    private List<String> extractedSymptoms = new ArrayList<>();

    @Builder.Default
    private List<String> missingFields = new ArrayList<>();

    @Builder.Default
    private List<String> evidence = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BattleQuestion {
        private String slot;
        private String question;
        private String inputType;
        private Boolean required;
        private Boolean multiple;

        @Builder.Default
        private List<BattleOption> options = new ArrayList<>();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BattleOption {
        private String label;
        private String value;
    }
}
