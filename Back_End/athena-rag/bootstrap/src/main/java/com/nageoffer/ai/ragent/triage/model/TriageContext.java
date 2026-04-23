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

    /**
     * The transcript used by workers in the current turn.
     */
    private String userInput;

    /**
     * Rolling summary of earlier turns.
     */
    private String conversationSummary;

    /**
     * All recent user turns kept in the active window.
     */
    @Default
    private List<String> conversationHistory = new ArrayList<>();

    @Default
    private List<Symptom> extractedSymptoms = new ArrayList<>();

    @Default
    private List<String> missingFields = new ArrayList<>();

    private RiskLevel riskAssessment;

    private TriageAction nextAction;

    private String finalReply;

    /**
     * The latest state reached by the finite-state machine.
     */
    private TriageState currentState;

    /**
     * Explicit orchestration state trace for debugging and audit.
     */
    @Default
    private List<String> stateLog = new ArrayList<>();

    /**
     * Immutable transition history used for audit persistence.
     */
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
        if (stateLog == null) {
            stateLog = new ArrayList<>();
        }
        if (auditTrail == null) {
            auditTrail = new ArrayList<>();
        }
    }

    public boolean hasMissingFields() {
        return missingFields != null && !missingFields.isEmpty();
    }

    public void appendConversation(String turnText) {
        ensureCollections();
        if (turnText == null || turnText.isBlank()) {
            return;
        }
        conversationHistory.add(turnText.trim());
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

    public String buildConversationTranscript() {
        return buildConversationTranscript(false);
    }

    public String buildConversationTranscript(boolean includeSummary) {
        ensureCollections();
        List<String> sections = new ArrayList<>();
        if (includeSummary && conversationSummary != null && !conversationSummary.isBlank()) {
            sections.add("【历史摘要】\n" + conversationSummary.trim());
        }
        if (!conversationHistory.isEmpty()) {
            sections.add("【最近对话】\n" + String.join("\n", conversationHistory));
        }
        if (!sections.isEmpty()) {
            return String.join("\n\n", sections);
        }
        return userInput == null ? "" : userInput;
    }

    /**
     * Clear turn-level derived fields before a new orchestration round starts.
     */
    public void resetTurnState() {
        ensureCollections();
        missingFields.clear();
        riskAssessment = null;
        nextAction = null;
        finalReply = null;
        currentState = null;
    }
}
