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

package com.nageoffer.ai.ragent.triage.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.nageoffer.ai.ragent.triage.engine.TriageState;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared context for the whole triage session lifecycle.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TriageContext {

    private String sessionId;
    private String userInput;
    private String latestUserTurn;
    private String conversationSummary;
    private String finalPrimaryComplaint;

    @Default
    private List<String> conversationHistory = new ArrayList<>();

    @Default
    private List<Symptom> extractedSymptoms = new ArrayList<>();

    @Default
    private List<String> missingFields = new ArrayList<>();

    @Default
    private List<Fact> factHistory = new ArrayList<>();

    @Default
    private SlotState slotState = SlotState.empty();

    @Default
    private List<SlotCode> lastAskedSlots = new ArrayList<>();

    @Default
    private List<SlotCode> answeredSlots = new ArrayList<>();

    @Default
    private List<SlotCode> pendingSlots = new ArrayList<>();

    @Default
    private List<QuestionGap> candidateQuestionGaps = new ArrayList<>();

    @Default
    private List<QuestionGap> selectedQuestionGaps = new ArrayList<>();

    @Default
    private List<QuestionGap> suppressedQuestionGaps = new ArrayList<>();

    @Default
    private List<AskabilityDecision> askabilityDecisions = new ArrayList<>();

    private TurnUnderstanding latestTurnUnderstanding;

    @Default
    private List<TurnUnderstanding> turnUnderstandingHistory = new ArrayList<>();

    private StateReducerResult latestStateReducerResult;

    @Default
    private List<StateReducerResult> stateReducerHistory = new ArrayList<>();

    @Default
    private List<RiskSignalUnderstanding> riskSignalState = new ArrayList<>();

    @Default
    private List<CorrectionUnderstanding> correctionHistory = new ArrayList<>();

    private QuestionPlan questionPlan;
    private RiskLevel riskAssessment;
    private RiskDecision riskDecision;

    @Default
    private List<RiskDecision> riskDecisionHistory = new ArrayList<>();

    private TriageAction nextAction;
    private String finalReply;
    private TriageState currentState;

    @Default
    private List<String> stateLog = new ArrayList<>();

    @Default
    private List<AuditLog> auditTrail = new ArrayList<>();

    public void ensureCollections() {
        if (conversationHistory == null) {
            conversationHistory = new ArrayList<>();
        }
        if (extractedSymptoms == null) {
            extractedSymptoms = new ArrayList<>();
        }
        if (missingFields == null) {
            missingFields = new ArrayList<>();
        }
        if (factHistory == null) {
            factHistory = new ArrayList<>();
        }
        if (slotState == null) {
            slotState = SlotState.empty();
        }
        if (lastAskedSlots == null) {
            lastAskedSlots = new ArrayList<>();
        }
        if (answeredSlots == null) {
            answeredSlots = new ArrayList<>();
        }
        if (pendingSlots == null) {
            pendingSlots = new ArrayList<>();
        }
        if (candidateQuestionGaps == null) {
            candidateQuestionGaps = new ArrayList<>();
        }
        if (selectedQuestionGaps == null) {
            selectedQuestionGaps = new ArrayList<>();
        }
        if (suppressedQuestionGaps == null) {
            suppressedQuestionGaps = new ArrayList<>();
        }
        if (askabilityDecisions == null) {
            askabilityDecisions = new ArrayList<>();
        }
        if (turnUnderstandingHistory == null) {
            turnUnderstandingHistory = new ArrayList<>();
        }
        if (stateReducerHistory == null) {
            stateReducerHistory = new ArrayList<>();
        }
        if (riskSignalState == null) {
            riskSignalState = new ArrayList<>();
        }
        if (riskDecisionHistory == null) {
            riskDecisionHistory = new ArrayList<>();
        }
        if (correctionHistory == null) {
            correctionHistory = new ArrayList<>();
        }
        if (stateLog == null) {
            stateLog = new ArrayList<>();
        }
        if (auditTrail == null) {
            auditTrail = new ArrayList<>();
        }
    }

    public boolean hasMissingFields() {
        return (pendingSlots != null && !pendingSlots.isEmpty())
                || (missingFields != null && !missingFields.isEmpty());
    }

    public void appendConversation(String turnText) {
        ensureCollections();
        if (turnText == null || turnText.isBlank()) {
            return;
        }
        conversationHistory.add(turnText.trim());
    }

    public void appendFacts(List<Fact> facts) {
        ensureCollections();
        if (facts == null || facts.isEmpty()) {
            return;
        }
        factHistory.addAll(facts.stream().filter(each -> each != null).toList());
    }

    public void appendTurnUnderstanding(TurnUnderstanding turnUnderstanding) {
        ensureCollections();
        if (turnUnderstanding == null) {
            return;
        }
        latestTurnUnderstanding = turnUnderstanding;
        turnUnderstandingHistory.add(turnUnderstanding);
    }

    public void appendStateReducerResult(StateReducerResult reducerResult) {
        ensureCollections();
        if (reducerResult == null) {
            return;
        }
        latestStateReducerResult = reducerResult;
        stateReducerHistory.add(reducerResult);
        finalPrimaryComplaint = reducerResult.getComplaintTruth() == null
                ? null
                : reducerResult.getComplaintTruth().getValue();
        if (reducerResult.getAccumulatedRiskSignals() != null) {
            riskSignalState = new ArrayList<>(reducerResult.getAccumulatedRiskSignals());
        }
        if (reducerResult.getCorrectionLog() != null) {
            correctionHistory = new ArrayList<>(reducerResult.getCorrectionLog());
        }
    }

    public void appendRiskDecision(RiskDecision decision) {
        ensureCollections();
        if (decision == null) {
            return;
        }
        riskDecision = decision;
        riskDecisionHistory.add(decision);
    }

    public void resetTurnState() {
        latestTurnUnderstanding = null;
        questionPlan = null;
        riskAssessment = null;
        riskDecision = null;
        nextAction = null;
        finalReply = null;
        extractedSymptoms = new ArrayList<>();
        missingFields = new ArrayList<>();
        lastAskedSlots = new ArrayList<>();
        answeredSlots = new ArrayList<>();
        pendingSlots = new ArrayList<>();
        candidateQuestionGaps = new ArrayList<>();
        selectedQuestionGaps = new ArrayList<>();
        suppressedQuestionGaps = new ArrayList<>();
        askabilityDecisions = new ArrayList<>();
    }

    public List<String> evictOldestTurnsByCharBudget(int targetRecentWindowChars) {
        ensureCollections();
        List<String> evicted = new ArrayList<>();
        while (conversationHistory.size() > 1 && recentConversationChars() > targetRecentWindowChars) {
            evicted.add(conversationHistory.remove(0));
        }
        return evicted;
    }

    public int recentConversationChars() {
        ensureCollections();
        if (conversationHistory.isEmpty()) {
            return 0;
        }
        return String.join("\n", conversationHistory).length();
    }

    public int totalTranscriptChars(boolean includeSummary) {
        return buildConversationTranscript(includeSummary).length();
    }

    public void appendState(String state) {
        ensureCollections();
        if (state == null || state.isBlank()) {
            return;
        }
        stateLog.add(state.trim());
    }

    public void appendAudit(AuditLog auditLog) {
        ensureCollections();
        if (auditLog == null) {
            return;
        }
        auditTrail.add(auditLog);
    }

    public String buildConversationTranscript(boolean includeSummary) {
        ensureCollections();
        StringBuilder builder = new StringBuilder();
        if (includeSummary && conversationSummary != null && !conversationSummary.isBlank()) {
            builder.append("[summary]\n").append(conversationSummary.trim()).append("\n\n");
        }
        if (conversationHistory != null && !conversationHistory.isEmpty()) {
            builder.append(String.join("\n", conversationHistory));
        }
        return builder.toString().trim();
    }
}
