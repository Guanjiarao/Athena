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

import com.nageoffer.ai.ragent.triage.model.TriageContext;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;

public class TriageEvalRunner {

    private final TriageEvalCaseLoader caseLoader;
    private final TriageEvalNormalizer normalizer;

    public TriageEvalRunner() {
        this.caseLoader = new TriageEvalCaseLoader();
        this.normalizer = new TriageEvalNormalizer();
    }

    public List<TriageEvalCase> loadCases(Path caseFilePath) {
        return caseLoader.loadFromPath(caseFilePath);
    }

    public List<TriageEvalNormalizer.NormalizedEvalResult> runCases(Path caseFilePath,
                                                                    Function<TriageEvalCase, TriageContext> executor) {
        return loadCases(caseFilePath).stream()
                .map(testCase -> runCase(testCase, executor))
                .toList();
    }

    public List<TriageEvalObservedCaseResult> runObservedCases(Path caseFilePath,
                                                               Function<TriageEvalCase, TriageContext> executor) {
        return loadCases(caseFilePath).stream()
                .map(testCase -> runObservedCase(testCase, executor))
                .toList();
    }

    public TriageEvalNormalizer.NormalizedEvalResult runCase(TriageEvalCase testCase,
                                                             Function<TriageEvalCase, TriageContext> executor) {
        if (testCase == null) {
            throw new IllegalArgumentException("testCase must not be null");
        }
        if (executor == null) {
            throw new IllegalArgumentException("executor must not be null");
        }
        TriageContext context = executor.apply(testCase);
        return normalizer.normalize(testCase.getId(), context);
    }

    public TriageEvalObservedCaseResult runObservedCase(TriageEvalCase testCase,
                                                        Function<TriageEvalCase, TriageContext> executor) {
        TriageEvalNormalizer.NormalizedEvalResult normalizedResult = runCase(testCase, executor);
        return TriageEvalObservedCaseResult.builder()
                .caseId(testCase.getId())
                .category(testCase.getCategory())
                .priority(testCase.getPriority())
                .question(resolveQuestion(testCase))
                .status("observed")
                .normalizedResult(normalizedResult)
                .build();
    }

    private String resolveQuestion(TriageEvalCase testCase) {
        if (testCase == null || testCase.getTurns() == null || testCase.getTurns().isEmpty()) {
            return "";
        }
        return testCase.getTurns().stream()
                .map(TriageEvalCase.Turn::getText)
                .filter(each -> each != null && !each.isBlank())
                .reduce((first, second) -> first + " | " + second)
                .orElse("");
    }
}
