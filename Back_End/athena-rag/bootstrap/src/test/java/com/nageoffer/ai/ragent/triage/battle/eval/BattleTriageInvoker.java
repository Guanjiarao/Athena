

package com.nageoffer.ai.ragent.triage.battle.eval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nageoffer.ai.ragent.triage.controller.request.TriageAnalyzeRequest;
import com.nageoffer.ai.ragent.triage.controller.vo.TriageAnalyzeResponse;
import com.nageoffer.ai.ragent.triage.eval.TriageEvalCase;
import com.nageoffer.ai.ragent.triage.eval.TriageEvalResult;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 三版本 battle 调用器：固定十用例、多轮追问时每轮选择第一个选项。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BattleTriageInvoker {

    private static final int DEFAULT_MAX_TURNS = 10;
    private static final int MAX_ITERATIONS = 20;

    private final BattleTriageClient battleTriageClient;
    private final FirstOptionAnswerStrategy answerStrategy;
    private final ObjectMapper objectMapper;

    public TriageEvalResult invoke(BattleVariant variant, TriageEvalCase evalCase) {
        return invoke(variant, evalCase, DEFAULT_MAX_TURNS).toEvalResult(evalCase);
    }

    public InvokeResult invoke(BattleVariant variant, TriageEvalCase evalCase, int maxTurns) {
        String sessionId = variant.getCode() + "-" + UUID.randomUUID();
        StringBuilder conversationLog = new StringBuilder();
        String finalReport = null;
        TriageAnalyzeResponse lastResponse = null;

        try {
            String userInput = evalCase.getUserInput();
            int turnIndex = 1;
            int iteration = 0;

            while (iteration < MAX_ITERATIONS && turnIndex <= maxTurns) {
                TriageAnalyzeRequest request = TriageAnalyzeRequest.builder()
                        .sessionId(sessionId)
                        .userInput(userInput)
                        .build();

                TriageAnalyzeResponse response = battleTriageClient.analyze(variant, request);
                lastResponse = response;
                appendTurn(conversationLog, turnIndex, userInput, response);

                if (isTerminalAction(response.getAction())) {
                    finalReport = response.getData() != null ? response.getData().toString() : response.getMessage();
                    conversationLog.append("【分诊报告】\n").append(finalReport).append("\n");
                    break;
                }

                if (!"ASK_CLARIFICATION".equals(response.getAction())) {
                    conversationLog.append("【未知动作，对话结束】\n");
                    break;
                }

                if (turnIndex >= maxTurns) {
                    conversationLog.append("【达到最大轮次限制: ").append(maxTurns).append("】\n");
                    break;
                }

                userInput = answerStrategy.answer(response);
                turnIndex++;
                iteration++;
            }

            return InvokeResult.builder()
                    .variant(variant)
                    .conversationLog(conversationLog.toString())
                    .finalReport(finalReport)
                    .lastResponse(lastResponse)
                    .build();
        } catch (Exception ex) {
            log.error("battle 调用失败: variant={}, caseId={}", variant.getCode(), evalCase.getCaseId(), ex);
            return InvokeResult.builder()
                    .variant(variant)
                    .conversationLog(conversationLog.toString())
                    .finalReport(finalReport)
                    .lastResponse(lastResponse)
                    .error(ex.getMessage())
                    .build();
        }
    }

    private void appendTurn(StringBuilder conversationLog,
                            int turnIndex,
                            String userInput,
                            TriageAnalyzeResponse response) {
        conversationLog.append("【第").append(turnIndex).append("轮】\n");
        conversationLog.append("用户: ").append(userInput).append("\n");
        conversationLog.append("系统: ").append(response.getMessage()).append("\n");
        conversationLog.append("Action: ").append(response.getAction()).append("\n");
        appendOptions(conversationLog, response);
        appendRawData(conversationLog, response);
        conversationLog.append("\n");
    }

    private void appendOptions(StringBuilder conversationLog, TriageAnalyzeResponse response) {
        try {
            JsonNode root = objectMapper.valueToTree(response.getData());
            boolean appended = appendQuestionOptions(conversationLog, root.path("questions"));
            if (!appended) {
                appended = appendOptionsArray(conversationLog, root.path("questionPlan").path("options"));
            }
            if (!appended) {
                appendOptionsArray(conversationLog, root.path("options"));
            }
        } catch (Exception ex) {
            log.debug("battle 提取选项信息失败", ex);
        }
    }

    private boolean appendQuestionOptions(StringBuilder conversationLog, JsonNode questions) {
        if (!questions.isArray()) {
            return false;
        }
        boolean appended = false;
        for (JsonNode question : questions) {
            JsonNode options = question.path("options");
            if (appendOptionsArray(conversationLog, options)) {
                appended = true;
            }
        }
        return appended;
    }

    private boolean appendOptionsArray(StringBuilder conversationLog, JsonNode options) {
        if (!options.isArray() || options.isEmpty()) {
            return false;
        }
        conversationLog.append("选项：[");
        for (int i = 0; i < options.size(); i++) {
            if (i > 0) {
                conversationLog.append(", ");
            }
            JsonNode option = options.get(i);
            String label = option.path("label").asText(option.path("value").asText(""));
            conversationLog.append(label);
        }
        conversationLog.append("]\n");
        return true;
    }

    private void appendRawData(StringBuilder conversationLog, TriageAnalyzeResponse response) {
        try {
            if (response.getData() != null) {
                conversationLog.append("Data: ").append(objectMapper.writeValueAsString(response.getData())).append("\n");
            }
        } catch (Exception ex) {
            conversationLog.append("Data: ").append(response.getData()).append("\n");
        }
    }

    private boolean isTerminalAction(String action) {
        return "GENERATE_REPORT".equals(action)
                || "SHOW_REPORT".equals(action)
                || "TRIGGER_WARNING".equals(action)
                || "WARN".equals(action);
    }

    @Data
    @Builder
    public static class InvokeResult {
        private BattleVariant variant;
        private String conversationLog;
        private String finalReport;
        private TriageAnalyzeResponse lastResponse;
        private String error;

        public TriageEvalResult toEvalResult(TriageEvalCase evalCase) {
            return TriageEvalResult.builder()
                    .caseId(evalCase.getCaseId())
                    .diseaseName("[" + variant.getDisplayName() + "] " + evalCase.getDiseaseName())
                    .userInput(evalCase.getUserInput())
                    .actualResponse(conversationLog)
                    .status(error == null ? "success" : "error")
                    .errorMessage(error)
                    .totalScore(0)
                    .build();
        }
    }
}
