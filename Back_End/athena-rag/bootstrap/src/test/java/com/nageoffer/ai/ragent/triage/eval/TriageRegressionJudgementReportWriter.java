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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TriageRegressionJudgementReportWriter {

    private static final List<String> ORDER = List.of("Understanding", "Reducer", "Planner", "History", "RiskDecision", "Final behavior", "Compatibility");
    private final ObjectMapper objectMapper;

    public TriageRegressionJudgementReportWriter() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    public void writeJsonReport(String suiteName, List<TriageRegressionJudgement> results, Path outputPath) {
        try {
            Files.createDirectories(outputPath.getParent());
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(outputPath.toFile(), buildReport(suiteName, results));
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to write triage regression judgement json report: " + outputPath, ex);
        }
    }

    public void writeMarkdownReport(String suiteName, List<TriageRegressionJudgement> results, Path outputPath) {
        try {
            Files.createDirectories(outputPath.getParent());
            Files.writeString(outputPath, buildMarkdown(suiteName, results));
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to write triage regression judgement markdown report: " + outputPath, ex);
        }
    }

    private TriageRegressionJudgementReport buildReport(String suiteName, List<TriageRegressionJudgement> results) {
        int total = results == null ? 0 : results.size();
        int passed = 0;
        if (results != null) for (TriageRegressionJudgement result : results) if (result != null && result.isPassed()) passed++;
        return TriageRegressionJudgementReport.builder().suiteName(suiteName).total(total).passed(passed).failed(total - passed).results(results).build();
    }

    private String buildMarkdown(String suiteName, List<TriageRegressionJudgement> results) {
        TriageRegressionJudgementReport report = buildReport(suiteName, results);
        StringBuilder builder = new StringBuilder();
        builder.append("# Triage Regression Judgement Report\n\n");
        builder.append("- Suite: ").append(suiteName).append("\n");
        builder.append("- Total: ").append(report.getTotal()).append("\n");
        builder.append("- Passed: ").append(report.getPassed()).append("\n");
        builder.append("- Failed: ").append(report.getFailed()).append("\n\n");
        builder.append("## Results\n\n");
        if (results == null || results.isEmpty()) return builder.append("No results.\n").toString();
        for (TriageRegressionJudgement result : results) {
            builder.append("### ").append(result.getCaseId()).append(" - ").append(result.isPassed() ? "PASS" : "FAIL").append("\n\n");
            builder.append("- Category: ").append(result.getCategory()).append("\n");
            builder.append("- Priority: ").append(result.getPriority()).append("\n");
            builder.append("- Passed Checks: ").append(result.getPassedChecks() == null ? 0 : result.getPassedChecks().size()).append("\n");
            builder.append("- Failed Checks: ").append(result.getFailedChecks() == null ? 0 : result.getFailedChecks().size()).append("\n");
            builder.append("- Reducer Complaint Truth: ").append(nullToEmpty(result.getReducerComplaintTruth())).append("\n");
            builder.append("- History Final Primary Complaint: ").append(nullToEmpty(result.getHistoryFinalPrimaryComplaint())).append("\n");
            builder.append("- History Reducer Complaint Truth: ").append(nullToEmpty(result.getHistoryReducerComplaintTruth())).append("\n");
            builder.append("- Complaint Truth Synchronized: ").append(result.getComplaintTruthSynchronized() == null ? "" : result.getComplaintTruthSynchronized()).append("\n\n");
            appendLayerSection(builder, "Passed checks by layer", group(result.getPassedChecks()));
            appendFailureSections(builder, group(result.getFailedChecks()));
            if (result.getObservedResult() != null && result.getObservedResult().getNormalizedResult() != null) {
                builder.append("\n#### Observed Summary\n\n");
                builder.append("- Action: ").append(nullToEmpty(result.getObservedResult().getNormalizedResult().getNextAction())).append("\n");
                builder.append("- Risk Level: ").append(nullToEmpty(result.getObservedResult().getNormalizedResult().getRiskLevel())).append("\n");
                builder.append("- Final Reply: ").append(nullToEmpty(result.getObservedResult().getNormalizedResult().getFinalReply())).append("\n\n");
            }
        }
        return builder.toString();
    }

    private Map<String, List<String>> group(List<String> checks) {
        Map<String, List<String>> grouped = new LinkedHashMap<>();
        for (String layer : ORDER) grouped.put(layer, new ArrayList<>());
        if (checks == null) return grouped;
        for (String check : checks) {
            String layer = extractLayer(check);
            grouped.computeIfAbsent(layer, k -> new ArrayList<>()).add(stripLayer(check, layer));
        }
        return grouped;
    }

    private void appendLayerSection(StringBuilder builder, String title, Map<String, List<String>> grouped) {
        builder.append("#### ").append(title).append("\n\n");
        boolean hasAny = false;
        for (String layer : ORDER) {
            List<String> values = grouped.get(layer);
            if (values != null && !values.isEmpty()) {
                hasAny = true;
                builder.append("- ").append(layer).append("\n");
                for (String value : values) builder.append("  - ").append(value).append("\n");
            }
        }
        if (!hasAny) builder.append("- None\n");
        builder.append("\n");
    }

    private void appendFailureSections(StringBuilder builder, Map<String, List<String>> grouped) {
        appendNamedFailures(builder, "Understanding failures", grouped.get("Understanding"));
        appendNamedFailures(builder, "Reducer failures", grouped.get("Reducer"));
        appendNamedFailures(builder, "Planner failures", grouped.get("Planner"));
        appendNamedFailures(builder, "History failures", grouped.get("History"));
        appendNamedFailures(builder, "RiskDecision failures", grouped.get("RiskDecision"));
        List<String> finalFailures = new ArrayList<>();
        if (grouped.get("Final behavior") != null) finalFailures.addAll(grouped.get("Final behavior"));
        if (grouped.get("Compatibility") != null) finalFailures.addAll(grouped.get("Compatibility"));
        appendNamedFailures(builder, "Final behavior failures", finalFailures);
    }

    private void appendNamedFailures(StringBuilder builder, String title, List<String> values) {
        builder.append("#### ").append(title).append("\n\n");
        if (values == null || values.isEmpty()) builder.append("- None\n\n");
        else {
            for (String value : values) builder.append("- ").append(value).append("\n");
            builder.append("\n");
        }
    }

    private String extractLayer(String value) {
        if (value != null && value.startsWith("[")) {
            int end = value.indexOf(']');
            if (end > 1) return value.substring(1, end);
        }
        return "Final behavior";
    }

    private String stripLayer(String value, String layer) {
        String prefix = "[" + layer + "] ";
        return value != null && value.startsWith(prefix) ? value.substring(prefix.length()) : nullToEmpty(value);
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
