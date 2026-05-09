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

import java.nio.file.Path;
import java.util.List;

public class TriageRegressionExtendedJudgementReportGenerator {

    public static final String SUITE_NAME = "triage-regression-extended-judged";
    public static final Path REGRESSION_CASES_PATH = Path.of("..", "resources", "eval", "triage", "triage-regression-cases-extended.json");
    public static final Path JSON_REPORT_PATH = Path.of("..", "resources", "eval", "outputs", "triage-regression-extended-judged-report.json");
    public static final Path MARKDOWN_REPORT_PATH = Path.of("..", "resources", "eval", "outputs", "triage-regression-extended-judged-report.md");

    private final TriageEvalCaseLoader caseLoader;
    private final TriageEvalRunner runner;
    private final TriageEvalRealExecutor executor;
    private final TriageRegressionJudge judge;
    private final TriageRegressionJudgementReportWriter reportWriter;

    public TriageRegressionExtendedJudgementReportGenerator() {
        this.caseLoader = new TriageEvalCaseLoader();
        this.runner = new TriageEvalRunner();
        this.executor = new TriageEvalRealExecutor(
                TriageEvalRealExecutor.heuristicLlmStub(),
                TriageEvalRealExecutor.stubGateway()
        );
        this.judge = new TriageRegressionJudge();
        this.reportWriter = new TriageRegressionJudgementReportWriter();
    }

    public List<TriageRegressionJudgement> generate() {
        List<TriageEvalCase> cases = caseLoader.loadFromPath(REGRESSION_CASES_PATH);
        List<TriageEvalObservedCaseResult> observedResults = runner.runObservedCases(REGRESSION_CASES_PATH, executor::execute);
        List<TriageRegressionJudgement> judgements = judge.judge(cases, observedResults);
        reportWriter.writeJsonReport(SUITE_NAME, judgements, JSON_REPORT_PATH);
        reportWriter.writeMarkdownReport(SUITE_NAME, judgements, MARKDOWN_REPORT_PATH);
        return judgements;
    }
}
