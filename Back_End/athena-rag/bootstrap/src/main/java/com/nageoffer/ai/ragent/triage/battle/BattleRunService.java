

package com.nageoffer.ai.ragent.triage.battle;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nageoffer.ai.ragent.triage.battle.pureprompt.PurePromptBattleService;
import com.nageoffer.ai.ragent.triage.battle.pureskill.PureSkillBattleService;
import com.nageoffer.ai.ragent.triage.controller.request.TriageAnalyzeRequest;
import com.nageoffer.ai.ragent.triage.controller.vo.TriageAnalyzeResponse;
import com.nageoffer.ai.ragent.triage.service.TriageOrchestratorService;
import com.nageoffer.ai.ragent.triage.session.TriageSessionProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * 一键运行三路分诊 battle。
 */
@Service
@RequiredArgsConstructor
public class BattleRunService {

    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 50;
    private static final List<String> BASELINES = List.of("official", "pure-prompt", "pure-skill");

    private final BattleCaseLoader battleCaseLoader;
    private final TriageOrchestratorService triageOrchestratorService;
    private final PurePromptBattleService purePromptBattleService;
    private final PureSkillBattleService pureSkillBattleService;
    private final BattleJudgeService battleJudgeService;
    private final BattleHtmlReportWriter battleHtmlReportWriter;
    private final ObjectMapper objectMapper;
    private final TriageSessionProperties triageSessionProperties;

    public BattleRunResponse run(BattleRunRequest request) {
        long startNanos = System.nanoTime();
        List<BattleCaseLoader.BattleCase> cases = selectCases(request);
        int maxTurns = resolveMaxTurns(request == null ? null : request.getMaxTurns());
        boolean judgeEnabled = request == null || request.getJudgeEnabled() == null || request.getJudgeEnabled();
        ExecutorService executor = Executors.newFixedThreadPool(Math.min(cases.size() * BASELINES.size(), 12));
        try {
            List<CompletableFuture<BattleRunResponse.CaseBattleResult>> futures = cases.stream()
                    .map(battleCase -> CompletableFuture.supplyAsync(() -> runCase(battleCase, executor, maxTurns, judgeEnabled), executor))
                    .toList();
            List<BattleRunResponse.CaseBattleResult> results = futures.stream()
                    .map(CompletableFuture::join)
                    .sorted(Comparator.comparing(BattleRunResponse.CaseBattleResult::getCaseId))
                    .toList();
            BattleRunResponse response = BattleRunResponse.of(
                    results.size(),
                    BASELINES.size(),
                    elapsedMillis(startNanos),
                    buildSummaries(results),
                    results
            );
            String reportPath = battleHtmlReportWriter.write(response);
            response.setReportPath(reportPath);
            response.setReportUrl(reportPath == null ? null : "file:///" + reportPath.replace('\\', '/'));
            return response;
        } finally {
            executor.shutdown();
        }
    }

    private BattleRunResponse.CaseBattleResult runCase(BattleCaseLoader.BattleCase battleCase,
                                                        ExecutorService executor,
                                                        int maxTurns,
                                                        boolean judgeEnabled) {
        List<CompletableFuture<BattleRunResponse.BaselineResult>> futures = BASELINES.stream()
                .map(baseline -> CompletableFuture.supplyAsync(() -> runBaseline(battleCase, baseline, maxTurns), executor))
                .toList();
        List<BattleRunResponse.BaselineResult> results = futures.stream()
                .map(CompletableFuture::join)
                .toList();
        if (judgeEnabled) {
            results.forEach(result -> result.setScore(battleJudgeService.judge(battleCase, result)));
        }
        return BattleRunResponse.CaseBattleResult.of(battleCase, results);
    }

    private BattleRunResponse.BaselineResult runBaseline(BattleCaseLoader.BattleCase battleCase, String baseline, int maxTurns) {
        long startNanos = System.nanoTime();
        String sessionId = "battle-" + baseline + "-" + battleCase.getCaseId() + "-" + UUID.randomUUID();
        StringBuilder conversationLog = new StringBuilder();
        TriageAnalyzeResponse lastResponse = null;
        try {
            String userInput = battleCase.getUserInput();
            for (int turn = 1; turn <= maxTurns; turn++) {
                TriageAnalyzeRequest request = TriageAnalyzeRequest.builder()
                        .sessionId(sessionId)
                        .userInput(userInput)
                        .build();
                lastResponse = invokeBaseline(baseline, request);
                appendTurn(conversationLog, turn, userInput, lastResponse);
                if (isTerminal(lastResponse)) {
                    break;
                }
                userInput = firstOptionOrDefault(lastResponse);
            }
            BattleRunResponse.TriageResponseSnapshot snapshot = new BattleRunResponse.TriageResponseSnapshot(
                    lastResponse == null ? null : lastResponse.getAction(),
                    lastResponse == null ? null : lastResponse.getMessage(),
                    lastResponse == null ? null : lastResponse.getRiskLevel(),
                    lastResponse == null ? null : lastResponse.getData()
            );
            return BattleRunResponse.BaselineResult.success(baseline, snapshot, elapsedMillis(startNanos), conversationLog.toString());
        } catch (Exception ex) {
            BattleRunResponse.BaselineResult errorResult = BattleRunResponse.BaselineResult.error(baseline, elapsedMillis(startNanos), ex.getMessage());
            errorResult.setConversationLog(conversationLog.toString());
            return errorResult;
        }
    }

    private TriageAnalyzeResponse invokeBaseline(String baseline, TriageAnalyzeRequest request) {
        return switch (baseline) {
            case "official" -> triageOrchestratorService.analyze(request);
            case "pure-prompt" -> purePromptBattleService.analyze(request);
            case "pure-skill" -> pureSkillBattleService.analyze(request);
            default -> throw new IllegalArgumentException("Unsupported baseline: " + baseline);
        };
    }

    private List<BattleCaseLoader.BattleCase> selectCases(BattleRunRequest request) {
        List<BattleCaseLoader.BattleCase> allCases = battleCaseLoader.loadAllCases();
        int limit = resolveLimit(request == null ? null : request.getLimit());
        if (request == null || request.getCaseIds() == null || request.getCaseIds().isEmpty()) {
            return allCases.stream().limit(limit).toList();
        }
        Set<String> caseIds = request.getCaseIds().stream()
                .filter(caseId -> caseId != null && !caseId.isBlank())
                .map(String::trim)
                .collect(Collectors.toSet());
        List<BattleCaseLoader.BattleCase> selected = new ArrayList<>();
        for (BattleCaseLoader.BattleCase battleCase : allCases) {
            if (caseIds.contains(battleCase.getCaseId())) {
                selected.add(battleCase);
            }
            if (selected.size() >= limit) {
                break;
            }
        }
        return selected;
    }

    private void appendTurn(StringBuilder conversationLog, int turn, String userInput, TriageAnalyzeResponse response) {
        conversationLog.append("【第").append(turn).append("轮】\n");
        conversationLog.append("用户: ").append(userInput).append("\n");
        conversationLog.append("系统: ").append(response == null ? "" : response.getMessage()).append("\n");
        conversationLog.append("Action: ").append(response == null ? "" : response.getAction()).append("\n");
        conversationLog.append("Data: ").append(response == null ? "" : response.getData()).append("\n\n");
    }

    private boolean isTerminal(TriageAnalyzeResponse response) {
        if (response == null || response.getAction() == null) {
            return true;
        }
        return "GENERATE_REPORT".equals(response.getAction())
                || "SHOW_REPORT".equals(response.getAction())
                || "WARN".equals(response.getAction())
                || "TRIGGER_WARNING".equals(response.getAction());
    }

    @SuppressWarnings("unchecked")
    private String firstOptionOrDefault(TriageAnalyzeResponse response) {
        Object data = response == null ? null : response.getData();
        if (data == null) {
            return "其他";
        }
        java.util.Map<?, ?> map = objectMapper.convertValue(data, java.util.Map.class);
        Object questions = map.get("questions");
        if (questions instanceof List<?> questionList && !questionList.isEmpty()) {
            Object firstQuestion = questionList.get(0);
            java.util.Map<?, ?> questionMap = objectMapper.convertValue(firstQuestion, java.util.Map.class);
            String option = firstOptionText(questionMap.get("options"));
            if (option != null) {
                return option;
            }
            Object question = questionMap.get("question");
            if (question != null) {
                return "其他";
            }
        }
        String option = firstOptionText(map.get("options"));
        if (option != null) {
            return option;
        }
        return "其他";
    }

    private String firstOptionText(Object options) {
        if (!(options instanceof List<?> optionList) || optionList.isEmpty()) {
            return null;
        }
        Object firstOption = optionList.get(0);
        if (firstOption instanceof java.util.Map<?, ?> optionMap) {
            Object label = optionMap.get("label");
            if (label != null && !label.toString().isBlank()) {
                return label.toString();
            }
            Object value = optionMap.get("value");
            if (value != null && !value.toString().isBlank()) {
                return value.toString();
            }
        }
        return firstOption == null ? null : firstOption.toString();
    }

    private List<BattleRunResponse.BaselineSummary> buildSummaries(List<BattleRunResponse.CaseBattleResult> results) {
        return BASELINES.stream().map(baseline -> {
            List<BattleRunResponse.BaselineResult> baselineResults = results.stream()
                    .flatMap(caseResult -> caseResult.getResults().stream())
                    .filter(result -> baseline.equals(result.getBaseline()))
                    .toList();
            long errorCount = baselineResults.stream().filter(result -> result.getError() != null && !result.getError().isBlank()).count();
            return BattleRunResponse.BaselineSummary.of(
                    baseline,
                    (int) (baselineResults.size() - errorCount),
                    (int) errorCount,
                    avg(baselineResults.stream().map(result -> result.getScore() == null ? null : result.getScore().getOutcomeScore()).toList()),
                    avg(baselineResults.stream().map(result -> result.getScore() == null ? null : result.getScore().getProcessScore()).toList()),
                    avgDouble(baselineResults.stream().map(result -> result.getScore() == null ? null : result.getScore().getWeightedScore()).toList()),
                    avgLong(baselineResults.stream().map(BattleRunResponse.BaselineResult::getElapsedMillis).toList())
            );
        }).toList();
    }

    private Double avg(List<Integer> values) {
        return values.stream().filter(value -> value != null).mapToInt(Integer::intValue).average().orElse(0.0D);
    }

    private Double avgDouble(List<Double> values) {
        return values.stream().filter(value -> value != null).mapToDouble(Double::doubleValue).average().orElse(0.0D);
    }

    private Double avgLong(List<Long> values) {
        return values.stream().filter(value -> value != null).mapToLong(Long::longValue).average().orElse(0.0D);
    }

    private int resolveLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private int resolveMaxTurns(Integer maxTurns) {
        int configuredMaxTurns = triageSessionProperties.getMaxTotalTurns() == null
                ? 8
                : triageSessionProperties.getMaxTotalTurns();
        if (maxTurns == null || maxTurns <= 0) {
            return configuredMaxTurns;
        }
        return Math.min(maxTurns, configuredMaxTurns);
    }

    private long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }
}
