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

public class TriageRegressionExtendedObservedReportGenerator {

    public static final String SUITE_NAME = "triage-regression-extended";
    public static final Path REGRESSION_CASES_PATH = Path.of("..", "resources", "eval", "triage", "triage-regression-cases-extended.json");
    public static final Path JSON_REPORT_PATH = Path.of("..", "resources", "eval", "outputs", "triage-regression-extended-report.json");
    public static final Path MARKDOWN_REPORT_PATH = Path.of("..", "resources", "eval", "outputs", "triage-regression-extended-report.md");

    private final TriageEvalRunner runner;
    private final TriageEvalReportWriter reportWriter;
    private final TriageEvalRealExecutor executor;

    public TriageRegressionExtendedObservedReportGenerator() {
        this.runner = new TriageEvalRunner();
        this.reportWriter = new TriageEvalReportWriter();
        this.executor = new TriageEvalRealExecutor(
                TriageEvalRealExecutor.heuristicLlmStub(),
                TriageEvalRealExecutor.stubGateway()
        );
    }

    public List<TriageEvalObservedCaseResult> generate() {
        List<TriageEvalObservedCaseResult> results = runner.runObservedCases(REGRESSION_CASES_PATH, executor::execute);
        reportWriter.writeJsonReport(SUITE_NAME, results, JSON_REPORT_PATH);
        reportWriter.writeMarkdownReport(SUITE_NAME, results, MARKDOWN_REPORT_PATH);
        return results;
    }
}
