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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class TriageEvalReportWriter {

    private final ObjectMapper objectMapper;

    public TriageEvalReportWriter() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    public void writeJsonReport(String suiteName, List<TriageEvalObservedCaseResult> results, Path outputPath) {
        try {
            Files.createDirectories(outputPath.getParent());
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(outputPath.toFile(),
                    TriageEvalSuiteReport.builder()
                            .suiteName(suiteName)
                            .total(results == null ? 0 : results.size())
                            .results(results)
                            .build());
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to write triage eval json report: " + outputPath, ex);
        }
    }

    public void writeMarkdownReport(String suiteName, List<TriageEvalObservedCaseResult> results, Path outputPath) {
        try {
            Files.createDirectories(outputPath.getParent());
            Files.writeString(outputPath, buildMarkdown(suiteName, results));
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to write triage eval markdown report: " + outputPath, ex);
        }
    }

    private String buildMarkdown(String suiteName, List<TriageEvalObservedCaseResult> results) {
        StringBuilder b = new StringBuilder();
        b.append("# Triage Eval Observed Report\n\n")
                .append("- Suite: ").append(suiteName).append("\n")
                .append("- Total: ").append(results == null ? 0 : results.size()).append("\n")
                .append("- ASK_CLARIFICATION: ").append(countByAction(results, "ASK_CLARIFICATION")).append("\n")
                .append("- TRIGGER_WARNING: ").append(countByAction(results, "TRIGGER_WARNING")).append("\n")
                .append("- GENERATE_REPORT: ").append(countByAction(results, "GENERATE_REPORT")).append("\n\n")
                .append("## Results\n\n");
        if (results == null || results.isEmpty()) {
            return b.append("No results.\n").toString();
        }
        for (TriageEvalObservedCaseResult r : results) {
            appendResult(b, r);
        }
        return b.toString();
    }

    private void appendResult(StringBuilder b, TriageEvalObservedCaseResult r) {
        b.append("### ").append(r.getCaseId()).append(" - ").append(r.getStatus()).append("\n\n")
                .append("- Question: ").append(empty(r.getQuestion())).append("\n")
                .append("- Category: ").append(empty(r.getCategory())).append("\n")
                .append("- Priority: ").append(empty(r.getPriority())).append("\n");
        TriageEvalNormalizer.NormalizedEvalResult n = r.getNormalizedResult();
        if (n == null) {
            b.append("\n");
            return;
        }
        b.append("- Action: ").append(empty(n.getNextAction())).append("\n")
                .append("- Risk Level: ").append(empty(n.getRiskLevel())).append("\n")
                .append("- Risk Score: ").append(n.getRiskScore() == null ? "" : n.getRiskScore()).append("\n")
                .append("- Pending Slots: ").append(list(n.getPendingSlots())).append("\n")
                .append("- Last Asked Slots: ").append(list(n.getLastAskedSlots())).append("\n")
                .append("- Facts Count: ").append(n.getFacts() == null ? 0 : n.getFacts().size()).append("\n");

        sec(b, "Turn Understanding");
        appendUnderstanding(b, n.getUnderstanding());

        sec(b, "State Reducer");
        appendReducer(b, n.getReducer());

        sec(b, "Planner");
        appendPlanner(b, n.getPlanner());

        sec(b, "Risk Decision");
        appendRiskDecision(b, n.getRiskDecision());

        sec(b, "Slot Values");
        appendSlotValues(b, n.getSlotValues());

        sec(b, "Question Plan");
        appendQuestionPlan(b, n.getQuestionPlan());

        sec(b, "History / Multi-turn Summary");
        appendHistory(b, r.getQuestion(), n.getHistory(), n.getUnderstanding());

        sec(b, "Facts");
        appendFacts(b, n.getFacts());

        sec(b, "Final Reply");
        b.append(empty(n.getFinalReply())).append("\n\n");
    }

    private void appendUnderstanding(StringBuilder b, TriageEvalNormalizer.UnderstandingSnapshot s) {
        if (s == null) {
            b.append("- None\n");
            return;
        }
        line(b, "Intent", empty(s.getIntent()));
        line(b, "Primary Complaint", empty(s.getPrimaryComplaint()));
        line(b, "Answered Slots", list(s.getAnsweredSlots()));
        line(b, "Risk Signals", list(s.getRiskSignals()));
        line(b, "Corrections", list(s.getCorrections()));
    }

    private void appendReducer(StringBuilder b, TriageEvalNormalizer.ReducerSnapshot s) {
        if (s == null) {
            b.append("- None\n");
            return;
        }
        line(b, "Complaint Truth", empty(s.getComplaintTruth()));
        line(b, "Reduced Slot Values", String.valueOf(s.getReducedSlotValues() == null ? Map.of() : s.getReducedSlotValues()));
        line(b, "Answered Slots", list(s.getAnsweredSlots()));
        line(b, "Pending Candidates", list(s.getPendingCandidates()));
        line(b, "Accumulated Risk Signals", list(s.getAccumulatedRiskSignals()));
        line(b, "Correction Count", s.getCorrectionCount() == null ? "" : String.valueOf(s.getCorrectionCount()));
    }

    private void appendPlanner(StringBuilder b, TriageEvalNormalizer.PlannerSnapshot s) {
        if (s == null) {
            b.append("- None\n");
            return;
        }
        line(b, "Candidate Gaps", list(s.getCandidateGaps()));
        line(b, "Selected Gaps", list(s.getSelectedGaps()));
        line(b, "Suppressed Gaps", list(s.getSuppressedGaps()));
        line(b, "Askability Decisions", list(s.getAskabilityDecisions()));
    }

    private void appendRiskDecision(StringBuilder b, TriageEvalNormalizer.RiskDecisionSnapshot s) {
        if (s == null) {
            b.append("- None\n");
            return;
        }
        line(b, "Decision Type", empty(s.getDecisionType()));
        line(b, "Should Interrupt", s.getShouldInterrupt() == null ? "" : String.valueOf(s.getShouldInterrupt()));
        line(b, "Needs More Info", s.getNeedsMoreInfo() == null ? "" : String.valueOf(s.getNeedsMoreInfo()));
        line(b, "Confirmed Risk Gaps", list(s.getConfirmedRiskGaps()));
        line(b, "Suspected Risk Gaps", list(s.getSuspectedRiskGaps()));
        line(b, "Unresolved Risk Gaps", list(s.getUnresolvedRiskGaps()));
    }

    private void appendHistory(StringBuilder b,
                               String question,
                               TriageEvalNormalizer.HistorySnapshot h,
                               TriageEvalNormalizer.UnderstandingSnapshot u) {
        line(b, "History Snapshot", h == null && u == null ? "None" : "Available");
        line(b, "Turns Count", String.valueOf(turns(question)));
        line(b, "Latest Turn Intent", u == null ? "" : empty(u.getIntent()));
        line(b, "Final Primary Complaint", h == null ? "" : empty(h.getFinalPrimaryComplaint()));
        line(b, "Reducer Complaint Truth", h == null ? "" : empty(h.getReducerComplaintTruth()));
        line(b, "Complaint Truth Synchronized", h == null || h.getComplaintTruthSynchronized() == null ? "" : String.valueOf(h.getComplaintTruthSynchronized()));
        line(b, "Turn Understanding History Count", h == null || h.getTurnUnderstandingHistoryCount() == null ? "" : String.valueOf(h.getTurnUnderstandingHistoryCount()));
        line(b, "State Reducer History Count", h == null || h.getStateReducerHistoryCount() == null ? "" : String.valueOf(h.getStateReducerHistoryCount()));
        line(b, "Risk Decision History Count", h == null || h.getRiskDecisionHistoryCount() == null ? "" : String.valueOf(h.getRiskDecisionHistoryCount()));
        line(b, "Question Timeline (derived, display-only)", timeline(question));
        line(b, "Timeline Note", "Derived from observed question text only; not the source of truth for turn history.");
    }

    private void appendSlotValues(StringBuilder b, Map<String, TriageEvalNormalizer.SlotSnapshot> m) {
        if (m == null || m.isEmpty()) {
            b.append("- None\n");
            return;
        }
        for (Map.Entry<String, TriageEvalNormalizer.SlotSnapshot> e : m.entrySet()) {
            TriageEvalNormalizer.SlotSnapshot s = e.getValue();
            b.append("- ").append(e.getKey())
                    .append(": value=").append(s == null ? "" : empty(s.getValue()))
                    .append(", status=").append(s == null ? "" : empty(s.getStatus()))
                    .append(", evidence=").append(s == null ? "" : empty(s.getEvidence()))
                    .append("\n");
        }
    }

    private void appendQuestionPlan(StringBuilder b, TriageEvalNormalizer.QuestionPlanSnapshot q) {
        if (q == null) {
            b.append("- None\n");
            return;
        }
        b.append("- Next Slots To Ask: ").append(list(q.getNextSlotsToAsk())).append("\n")
                .append("- Pending Slots: ").append(list(q.getPendingSlots())).append("\n")
                .append("- Ask Count: ").append(q.getAskCount() == null ? "" : q.getAskCount()).append("\n")
                .append("- Follow Up Mode: ").append(q.getFollowUpMode() == null ? "" : q.getFollowUpMode()).append("\n")
                .append("- Priority Reason: ").append(empty(q.getPriorityReason())).append("\n");
    }

    private void appendFacts(StringBuilder b, List<?> facts) {
        if (facts == null || facts.isEmpty()) {
            b.append("- None\n");
            return;
        }
        for (Object fact : facts) {
            b.append("- ").append(String.valueOf(fact)).append("\n");
        }
    }

    private int countByAction(List<TriageEvalObservedCaseResult> rs, String action) {
        if (rs == null || rs.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (TriageEvalObservedCaseResult r : rs) {
            if (r != null && r.getNormalizedResult() != null && action.equals(r.getNormalizedResult().getNextAction())) {
                count++;
            }
        }
        return count;
    }

    private void sec(StringBuilder b, String title) {
        b.append("\n#### ").append(title).append("\n\n");
    }

    private void line(StringBuilder b, String key, String value) {
        b.append("- ").append(key).append(": ").append(value).append("\n");
    }

    private int turns(String q) {
        return q == null || q.isBlank() ? 0 : parts(q).length;
    }

    private String timeline(String q) {
        String[] ps = parts(q);
        if (ps.length == 0) {
            return "[]";
        }
        StringBuilder b = new StringBuilder("[");
        for (int i = 0; i < ps.length; i++) {
            if (i > 0) {
                b.append("; ");
            }
            b.append("T").append(i + 1).append("=").append(ps[i]);
        }
        return b.append("]").toString();
    }

    private String[] parts(String q) {
        if (q == null || q.isBlank()) {
            return new String[0];
        }
        return q.split("\\s*\\|\\s*");
    }

    private String list(List<String> items) {
        return items == null || items.isEmpty() ? "[]" : items.toString();
    }

    private String empty(String value) {
        return value == null ? "" : value;
    }
}
