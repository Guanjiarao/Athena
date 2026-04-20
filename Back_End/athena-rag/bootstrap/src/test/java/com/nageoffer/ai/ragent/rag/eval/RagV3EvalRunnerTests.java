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

package com.nageoffer.ai.ragent.rag.eval;

import cn.hutool.core.collection.CollUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

/**
 * RAG V3 评测运行器（最小骨架）
 */
@Slf4j
@SpringBootTest
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class RagV3EvalRunnerTests {

    private final RagV3CaseLoader caseLoader;
    private final RagV3Invoker invoker;
    private final RagV3RuleChecker ruleChecker;
    private final RagV3ReportWriter reportWriter;

    @Test
    public void runSmokeCases() {
        RagV3EvalReport report = runSuite("athena-rag-smoke", caseLoader.loadSmokeCases());
        Assertions.assertEquals(0, report.getFailed(), "存在失败用例，请查看日志中的评测报告");
    }

    @Test
    public void runBadCases() {
        RagV3EvalReport report = runSuite("athena-rag-bad-cases", caseLoader.loadBadCases());
        Assertions.assertEquals(0, report.getFailed(), "存在失败用例，请查看日志中的评测报告");
    }

    private RagV3EvalReport runSuite(String suiteName, List<RagV3EvalCase> cases) {
        Assertions.assertFalse(CollUtil.isEmpty(cases), suiteName + " 评测集不能为空");

        List<RagV3EvalResult> results = cases.stream()
                .map(this::executeCase)
                .toList();

        RagV3EvalReport report = buildReport(suiteName, results);
        logReport(report);
        reportWriter.writeReport(report);
        return report;
    }

    private RagV3EvalResult executeCase(RagV3EvalCase evalCase) {
        if (evalCase.hasHistory()) {
            return ruleChecker.skipped(evalCase, "history_not_supported_by_runner");
        }
        return ruleChecker.check(evalCase, invoker.invoke(evalCase));
    }

    private RagV3EvalReport buildReport(String suiteName, List<RagV3EvalResult> results) {
        int passed = 0;
        int warnings = 0;
        int failed = 0;
        int skipped = 0;

        for (RagV3EvalResult result : results) {
            switch (result.getStatus()) {
                case "pass" -> passed++;
                case "warning" -> warnings++;
                case "fail" -> failed++;
                case "skipped" -> skipped++;
                default -> warnings++;
            }
        }

        return RagV3EvalReport.builder()
                .suiteName(suiteName)
                .total(results.size())
                .passed(passed)
                .warnings(warnings)
                .failed(failed)
                .skipped(skipped)
                .results(results)
                .build();
    }

    private void logReport(RagV3EvalReport report) {
        log.info("\n===== RAG V3 EVAL REPORT =====\n"
                        + "suite={} total={} pass={} warning={} fail={} skipped={}\n"
                        + "===============================",
                report.getSuiteName(),
                report.getTotal(),
                report.getPassed(),
                report.getWarnings(),
                report.getFailed(),
                report.getSkipped());

        for (RagV3EvalResult result : report.getResults()) {
            log.info("[{}] {} -> status={}, findings={}, references={}",
                    result.getCaseId(),
                    result.getQuestion(),
                    result.getStatus(),
                    result.getFindings(),
                    result.getReferenceTitles());
        }
    }
}
