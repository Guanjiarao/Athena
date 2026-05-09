/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.nageoffer.ai.ragent.triage.eval;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TriageEvalCase {

    private String id;

    private String category;

    private String priority;

    @Builder.Default
    private List<Turn> turns = new ArrayList<>();

    private EvalContext context;

    private Expected expected;

    @Builder.Default
    private List<String> forbidden = new ArrayList<>();

    private Boolean strictCompatibilityFacts;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Turn {

        private String role;

        private String text;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class EvalContext {

        @Builder.Default
        private List<String> lastAskedSlots = new ArrayList<>();

        @Builder.Default
        private List<String> pendingSlots = new ArrayList<>();

        @Builder.Default
        private Map<String, SlotSeed> slotState = new LinkedHashMap<>();

        @Builder.Default
        private List<Map<String, Object>> perTurnContext = new ArrayList<>();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class SlotSeed {

        private String value;

        private String status;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Expected {

        @Builder.Default
        private Map<String, String> slotValues = new LinkedHashMap<>();

        @Builder.Default
        private Map<String, String> finalSlotValues = new LinkedHashMap<>();

        @Builder.Default
        private Map<String, String> slotStatuses = new LinkedHashMap<>();

        @Builder.Default
        private List<String> answeredSlotsContains = new ArrayList<>();

        @Builder.Default
        private List<String> pendingSlotsNotContains = new ArrayList<>();

        private String nextAction;

        private String riskLevelAtLeast;

        private QuestionPlanExpectation questionPlan;

        private Boolean mustAcknowledgeInsufficient;

        /**
         * Legacy compatibility assertions retained for v1 cases.
         * Prefer structured v2 assertions under understanding/reducer/planner/riskDecision/history.
         */
        @Builder.Default
        private Map<String, String> factModifiers = new LinkedHashMap<>();

        /**
         * Compatibility-only hints retained to avoid breaking legacy cases.
         */
        @Builder.Default
        private List<FactPolarityHint> factPolarityHints = new ArrayList<>();

        /**
         * Reply-level textual observation retained as a fallback assertion.
         */
        @Builder.Default
        private List<String> riskHintsContains = new ArrayList<>();

        private UnderstandingExpectation understanding;

        private ReducerExpectation reducer;

        private PlannerExpectation planner;

        private RiskDecisionExpectation riskDecision;

        private HistoryExpectation history;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class FactPolarityHint {

        private String evidence;

        private String polarity;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class QuestionPlanExpectation {

        @Builder.Default
        private List<String> mustAskAnyOf = new ArrayList<>();

        @Builder.Default
        private List<String> shouldAskAnyOf = new ArrayList<>();

        @Builder.Default
        private List<String> mustNotAsk = new ArrayList<>();

        private Integer maxAskCount;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class UnderstandingExpectation {

        private String intent;

        private String primaryComplaint;

        @Builder.Default
        private List<String> answeredSlotsContains = new ArrayList<>();

        @Builder.Default
        private List<String> riskSignalsContains = new ArrayList<>();

        @Builder.Default
        private List<String> correctionsContains = new ArrayList<>();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ReducerExpectation {

        @Builder.Default
        private Map<String, String> reducedSlotValues = new LinkedHashMap<>();

        @Builder.Default
        private List<String> answeredSlotsContains = new ArrayList<>();

        @Builder.Default
        private List<String> pendingSlotsNotContains = new ArrayList<>();

        @Builder.Default
        private List<String> riskSignalsContains = new ArrayList<>();

        private Integer correctionCountAtLeast;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class PlannerExpectation {

        @Builder.Default
        private List<String> candidateGapsContains = new ArrayList<>();

        @Builder.Default
        private List<String> selectedGapsContains = new ArrayList<>();

        @Builder.Default
        private List<String> suppressedGapsContains = new ArrayList<>();

        @Builder.Default
        private List<String> askabilityDecisionsContains = new ArrayList<>();

        @Builder.Default
        private List<String> mustNotSelectGaps = new ArrayList<>();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class RiskDecisionExpectation {

        private String decisionType;

        private Boolean shouldInterrupt;

        private Boolean needsMoreInfo;

        @Builder.Default
        private List<String> confirmedRiskGapsContains = new ArrayList<>();

        @Builder.Default
        private List<String> suspectedRiskGapsContains = new ArrayList<>();

        @Builder.Default
        private List<String> unresolvedRiskGapsContains = new ArrayList<>();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class HistoryExpectation {

        private Integer turnUnderstandingCountAtLeast;

        private Integer stateReducerHistoryCountAtLeast;

        private Integer riskDecisionHistoryCountAtLeast;

        private String finalPrimaryComplaint;

        @JsonAlias("finalPrimarySymptomMustPersist")
        private String finalPrimarySymptomMustPersist;
    }
}
