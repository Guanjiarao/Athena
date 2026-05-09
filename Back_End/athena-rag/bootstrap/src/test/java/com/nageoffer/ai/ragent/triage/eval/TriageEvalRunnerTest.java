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

import com.nageoffer.ai.ragent.triage.model.Fact;
import com.nageoffer.ai.ragent.triage.model.FactPolarity;
import com.nageoffer.ai.ragent.triage.model.FactType;
import com.nageoffer.ai.ragent.triage.model.QuestionPlan;
import com.nageoffer.ai.ragent.triage.model.RiskLevel;
import com.nageoffer.ai.ragent.triage.model.SlotCode;
import com.nageoffer.ai.ragent.triage.model.SlotState;
import com.nageoffer.ai.ragent.triage.model.SlotStatus;
import com.nageoffer.ai.ragent.triage.model.SlotValue;
import com.nageoffer.ai.ragent.triage.model.TriageAction;
import com.nageoffer.ai.ragent.triage.model.TriageContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TriageEvalRunnerTest {

    private static final Path SMOKE_CASES_PATH = Path.of("..", "resources", "eval", "triage", "triage-smoke-cases.json");

    @TempDir
    Path tempDir;

    @Test
    void shouldLoadSmokeCasesFromJson() {
        TriageEvalRunner runner = new TriageEvalRunner();

        List<TriageEvalCase> cases = runner.loadCases(SMOKE_CASES_PATH);

        assertFalse(cases.isEmpty());
        assertEquals("TRIAGE-NEG-001", cases.get(0).getId());
        assertEquals("negation", cases.get(0).getCategory());
    }

    @Test
    void shouldNormalizeExecutorResultForSmokeCases() {
        TriageEvalRunner runner = new TriageEvalRunner();

        List<TriageEvalNormalizer.NormalizedEvalResult> results = runner.runCases(SMOKE_CASES_PATH, this::fakeExecute);

        assertFalse(results.isEmpty());
        TriageEvalNormalizer.NormalizedEvalResult first = results.get(0);
        assertNotNull(first.getCaseId());
        assertNotNull(first.getSlotValues());
        assertNotNull(first.getPendingSlots());
    }

    @Test
    void shouldRunObservedModeAndWriteReports() throws Exception {
        TriageEvalRunner runner = new TriageEvalRunner();
        TriageEvalReportWriter reportWriter = new TriageEvalReportWriter();

        List<TriageEvalObservedCaseResult> results = runner.runObservedCases(SMOKE_CASES_PATH, this::fakeExecute);
        Path jsonReport = tempDir.resolve("triage-observed-report.json");
        Path markdownReport = tempDir.resolve("triage-observed-report.md");

        reportWriter.writeJsonReport("triage-smoke", results, jsonReport);
        reportWriter.writeMarkdownReport("triage-smoke", results, markdownReport);

        assertFalse(results.isEmpty());
        assertEquals("observed", results.get(0).getStatus());
        assertTrue(Files.exists(jsonReport));
        assertTrue(Files.exists(markdownReport));
        assertTrue(Files.readString(markdownReport).contains("Triage Eval Observed Report"));
    }

    @Test
    void shouldExecuteRealStateMachineWithStubbedDependencies() {
        TriageEvalRunner runner = new TriageEvalRunner();
        TriageEvalRealExecutor executor = new TriageEvalRealExecutor(
                TriageEvalRealExecutor.heuristicLlmStub(),
                TriageEvalRealExecutor.stubGateway()
        );

        TriageEvalObservedCaseResult result = runner.runObservedCase(buildRealExecutionCase(), executor::execute);

        assertEquals("observed", result.getStatus());
        assertNotNull(result.getNormalizedResult());
        assertNotNull(result.getNormalizedResult().getNextAction());
        assertNotNull(result.getNormalizedResult().getFinalReply());
    }

    @Test
    void shouldConsumeCaseContextAndPerTurnContextInRealExecutor() {
        TriageEvalRunner runner = new TriageEvalRunner();
        TriageEvalRealExecutor executor = new TriageEvalRealExecutor(
                TriageEvalRealExecutor.heuristicLlmStub(),
                TriageEvalRealExecutor.stubGateway()
        );

        TriageEvalObservedCaseResult result = runner.runObservedCase(buildContextDrivenCase(), executor::execute);

        assertEquals("observed", result.getStatus());
        assertEquals(List.of("DURATION", "BODY_PART"), result.getNormalizedResult().getLastAskedSlots());
        assertTrue(result.getNormalizedResult().getSlotValues().containsKey("PRIMARY_SYMPTOM"));
        assertEquals("腹痛", result.getNormalizedResult().getSlotValues().get("PRIMARY_SYMPTOM").getValue());
        assertTrue(result.getNormalizedResult().getPendingSlots().contains("DURATION"));
    }

    @Test
    void shouldGenerateSmokeObservedReportIntoFixedOutputsDirectory() throws Exception {
        TriageSmokeObservedReportGenerator generator = new TriageSmokeObservedReportGenerator();

        List<TriageEvalObservedCaseResult> results = generator.generate();

        assertFalse(results.isEmpty());
        assertTrue(Files.exists(TriageSmokeObservedReportGenerator.JSON_REPORT_PATH));
        assertTrue(Files.exists(TriageSmokeObservedReportGenerator.MARKDOWN_REPORT_PATH));
        assertTrue(Files.readString(TriageSmokeObservedReportGenerator.MARKDOWN_REPORT_PATH).contains("TRIAGE-NEG-001"));
    }

    @Test
    void shouldGenerateRegressionObservedReportIntoFixedOutputsDirectory() throws Exception {
        TriageRegressionObservedReportGenerator generator = new TriageRegressionObservedReportGenerator();

        List<TriageEvalObservedCaseResult> results = generator.generate();

        assertFalse(results.isEmpty());
        assertTrue(Files.exists(TriageRegressionObservedReportGenerator.JSON_REPORT_PATH));
        assertTrue(Files.exists(TriageRegressionObservedReportGenerator.MARKDOWN_REPORT_PATH));
        assertTrue(Files.readString(TriageRegressionObservedReportGenerator.MARKDOWN_REPORT_PATH).contains("TRIAGE-RISK-001"));
    }

    @Test
    void shouldGenerateRegressionJudgementReportIntoFixedOutputsDirectory() throws Exception {
        TriageRegressionJudgementReportGenerator generator = new TriageRegressionJudgementReportGenerator();

        List<TriageRegressionJudgement> results = generator.generate();

        assertFalse(results.isEmpty());
        assertTrue(Files.exists(TriageRegressionJudgementReportGenerator.JSON_REPORT_PATH));
        assertTrue(Files.exists(TriageRegressionJudgementReportGenerator.MARKDOWN_REPORT_PATH));
        assertTrue(Files.readString(TriageRegressionJudgementReportGenerator.MARKDOWN_REPORT_PATH).contains("Triage Regression Judgement Report"));
    }

    @Test
    void shouldGenerateRegressionExtendedObservedReportIntoFixedOutputsDirectory() throws Exception {
        TriageRegressionExtendedObservedReportGenerator generator = new TriageRegressionExtendedObservedReportGenerator();

        List<TriageEvalObservedCaseResult> results = generator.generate();

        assertFalse(results.isEmpty());
        assertTrue(Files.exists(TriageRegressionExtendedObservedReportGenerator.JSON_REPORT_PATH));
        assertTrue(Files.exists(TriageRegressionExtendedObservedReportGenerator.MARKDOWN_REPORT_PATH));
        assertTrue(Files.readString(TriageRegressionExtendedObservedReportGenerator.MARKDOWN_REPORT_PATH).contains("TRIAGE-EXT-001"));
    }

    @Test
    void shouldGenerateRegressionExtendedJudgementReportIntoFixedOutputsDirectory() throws Exception {
        TriageRegressionExtendedJudgementReportGenerator generator = new TriageRegressionExtendedJudgementReportGenerator();

        List<TriageRegressionJudgement> results = generator.generate();

        assertFalse(results.isEmpty());
        assertTrue(Files.exists(TriageRegressionExtendedJudgementReportGenerator.JSON_REPORT_PATH));
        assertTrue(Files.exists(TriageRegressionExtendedJudgementReportGenerator.MARKDOWN_REPORT_PATH));
        String json = Files.readString(TriageRegressionExtendedJudgementReportGenerator.JSON_REPORT_PATH);
        String markdown = Files.readString(TriageRegressionExtendedJudgementReportGenerator.MARKDOWN_REPORT_PATH);
        assertTrue(markdown.contains("Triage Regression Judgement Report"));
        assertTrue(markdown.contains("TRIAGE-EXT-001"));
        assertTrue(json.contains("\"reducerComplaintTruth\""));
        assertTrue(json.contains("\"historyFinalPrimaryComplaint\""));
        assertTrue(json.contains("\"historyReducerComplaintTruth\""));
        assertTrue(json.contains("\"complaintTruthSynchronized\""));
        assertTrue(markdown.contains("Reducer Complaint Truth:"));
        assertTrue(markdown.contains("History Final Primary Complaint:"));
        assertTrue(markdown.contains("History Reducer Complaint Truth:"));
        assertTrue(markdown.contains("Complaint Truth Synchronized:"));
    }

    private TriageEvalCase buildRealExecutionCase() {
        return TriageEvalCase.builder()
                .id("TRIAGE-REAL-001")
                .category("smoke")
                .priority("P1")
                .turns(List.of(TriageEvalCase.Turn.builder()
                        .role("user")
                        .text("肚子疼一天了，右下腹，有点恶心，没发热")
                        .build()))
                .build();
    }

    private TriageEvalCase buildContextDrivenCase() {
        return TriageEvalCase.builder()
                .id("TRIAGE-CONTEXT-001")
                .category("multi_turn_slot_filling")
                .priority("P0")
                .turns(List.of(
                        TriageEvalCase.Turn.builder().role("user").text("肚子疼").build(),
                        TriageEvalCase.Turn.builder().role("user").text("没发热").build()
                ))
                .context(TriageEvalCase.EvalContext.builder()
                        .lastAskedSlots(List.of("BODY_PART"))
                        .pendingSlots(List.of("BODY_PART", "DURATION"))
                        .slotState(Map.of(
                                "PRIMARY_SYMPTOM", TriageEvalCase.SlotSeed.builder().value("腹痛").status("FILLED").build()
                        ))
                        .perTurnContext(List.of(
                                Map.of(),
                                Map.of(
                                        "lastAskedSlots", List.of("FEVER_PRESENCE"),
                                        "pendingSlots", List.of("FEVER_PRESENCE", "DURATION")
                                )
                        ))
                        .build())
                .build();
    }

    private TriageContext fakeExecute(TriageEvalCase testCase) {
        TriageContext context = new TriageContext();
        context.ensureCollections();
        context.setFinalReply("stub reply for " + testCase.getId());
        context.setNextAction(TriageAction.ASK_CLARIFICATION);
        context.setQuestionPlan(QuestionPlan.builder()
                .nextSlotsToAsk(List.of(SlotCode.DURATION))
                .pendingSlots(List.of(SlotCode.DURATION))
                .priorityReason("stub next slot")
                .askCount(1)
                .followUpMode(Boolean.TRUE)
                .build());
        context.setRiskAssessment(RiskLevel.builder()
                .level(2)
                .score(40D)
                .evidence("stub evidence")
                .rationale("stub rationale")
                .build());
        context.getPendingSlots().add(SlotCode.DURATION);
        context.getLastAskedSlots().add(SlotCode.FEVER_PRESENCE);
        context.setSlotState(buildSlotState());
        context.appendFacts(List.of(Fact.builder()
                .type(FactType.SLOT_EVIDENCE)
                .slot(SlotCode.FEVER_PRESENCE)
                .canonicalValue("NO")
                .polarity(FactPolarity.NEGATIVE)
                .evidence("没发热")
                .sourceTurnIndex(1)
                .sourceText("没发热")
                .build()));
        return context;
    }

    private SlotState buildSlotState() {
        SlotState slotState = SlotState.empty();
        slotState.put(SlotValue.builder()
                .slot(SlotCode.PRIMARY_SYMPTOM)
                .value("腹痛")
                .status(SlotStatus.FILLED)
                .evidence("肚子疼")
                .updatedAt(Instant.now())
                .build());
        slotState.put(SlotValue.builder()
                .slot(SlotCode.FEVER_PRESENCE)
                .value("NO")
                .status(SlotStatus.FILLED)
                .evidence("没发热")
                .updatedAt(Instant.now())
                .build());
        return slotState;
    }
}
