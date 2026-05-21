

package com.nageoffer.ai.ragent.triage.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nageoffer.ai.ragent.framework.convention.ChatMessage;
import com.nageoffer.ai.ragent.triage.controller.request.TriageAnalyzeRequest;
import com.nageoffer.ai.ragent.triage.controller.vo.TriageAnalyzeResponse;
import com.nageoffer.ai.ragent.triage.controller.vo.TriageClarificationData;
import com.nageoffer.ai.ragent.triage.model.SlotCode;
import com.nageoffer.ai.ragent.triage.service.TriageModelGateway;
import com.nageoffer.ai.ragent.triage.service.TriageOrchestratorService;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 分诊系统调用器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TriageInvoker {

    private final TriageOrchestratorService triageOrchestratorService;
    private final TriageModelGateway triageModelGateway;

    /**
     * 调用分诊系统，模拟多轮对话
     */
    public TriageEvalResult invoke(TriageEvalCase evalCase) {
        return invoke(evalCase, Integer.MAX_VALUE).toEvalResult();
    }

    /**
     * 调用分诊系统，模拟多轮对话（限制轮次）
     *
     * @param evalCase 测试用例
     * @param maxTurns 最大轮次（包括初始轮）
     * @return 调用结果
     */
    public InvokeResult invoke(TriageEvalCase evalCase, int maxTurns) {
        String sessionId = UUID.randomUUID().toString();
        StringBuilder conversationLog = new StringBuilder();
        String finalReport = null;

        try {
            // 第一轮：发送用户初始输入
            TriageAnalyzeRequest firstRequest = TriageAnalyzeRequest.builder()
                    .sessionId(sessionId)
                    .userInput(evalCase.getUserInput())
                    .build();

            TriageAnalyzeResponse firstResponse = triageOrchestratorService.analyze(firstRequest);
            conversationLog.append("【第1轮】\n");
            conversationLog.append("用户: ").append(evalCase.getUserInput()).append("\n");
            conversationLog.append("系统: ").append(firstResponse.getMessage()).append("\n");
            conversationLog.append("Action: ").append(firstResponse.getAction()).append("\n");
            appendOptions(conversationLog, firstResponse);
            conversationLog.append("\n");

            // 如果第一轮就返回了报告或触发警告，直接结束
            if ("GENERATE_REPORT".equals(firstResponse.getAction()) || "SHOW_REPORT".equals(firstResponse.getAction()) || "TRIGGER_WARNING".equals(firstResponse.getAction())) {
                finalReport = firstResponse.getData() != null ? firstResponse.getData().toString() : null;
                conversationLog.append("【分诊报告】\n");
                conversationLog.append(finalReport).append("\n");
                return InvokeResult.builder()
                        .conversationLog(conversationLog.toString())
                        .finalReport(finalReport)
                        .build();
            }

            // 后续轮次：使用智能匹配
            Map<SlotCode, String> answerMap = buildAnswerMap(evalCase);
            Set<SlotCode> usedSlots = new HashSet<>();  // 记录已使用的槽位，避免重复

            int turnIndex = 1;
            int maxIterations = 20;  // 防止死循环
            int iteration = 0;
            int consecutiveGenericAnswers = 0;  // 连续通用回答次数
            TriageAnalyzeResponse lastResponse = firstResponse;

            while (iteration < maxIterations) {
                if (turnIndex >= maxTurns) {
                    conversationLog.append("【达到最大轮次限制: ").append(maxTurns).append("】\n");
                    break;
                }

                iteration++;
                turnIndex++;

                // 1. 提取系统问的是什么槽位
                SlotCode askedSlot = extractSlotFromQuestion(lastResponse.getMessage());
                String userAnswer = null;
                boolean isGenericAnswer = false;

                // 2. 先尝试关键词匹配
                if (askedSlot != null && answerMap.containsKey(askedSlot) && !usedSlots.contains(askedSlot)) {
                    userAnswer = answerMap.get(askedSlot);
                    usedSlots.add(askedSlot);
                    log.info("关键词匹配成功: {} -> {}", askedSlot, userAnswer);
                }

                // 3. 如果关键词匹配失败，使用 LLM 兜底
                if (userAnswer == null) {
                    userAnswer = findAnswerByLLM(lastResponse.getMessage(), evalCase);
                    if (userAnswer != null) {
                        log.info("LLM 兜底匹配成功");
                    }
                }

                // 4. 如果还是找不到，使用通用回答继续对话
                if (userAnswer == null) {
                    userAnswer = generateGenericAnswer(lastResponse.getMessage(), askedSlot);
                    isGenericAnswer = true;
                    consecutiveGenericAnswers++;
                    log.warn("无法找到匹配的回答，使用通用回答: {} (连续第{}次)", userAnswer, consecutiveGenericAnswers);

                    // 如果连续3次使用通用回答，强制结束对话
                    if (consecutiveGenericAnswers >= 3) {
                        log.warn("连续3次使用通用回答，强制结束对话");
                        conversationLog.append("【连续使用通用回答，对话结束】\n");
                        break;
                    }
                } else {
                    consecutiveGenericAnswers = 0;  // 重置计数器
                }

                // 5. 发送用户回答
                TriageAnalyzeRequest request = TriageAnalyzeRequest.builder()
                        .sessionId(sessionId)
                        .userInput(userAnswer)
                        .build();

                TriageAnalyzeResponse response = triageOrchestratorService.analyze(request);
                conversationLog.append("【第").append(turnIndex).append("轮】\n");
                conversationLog.append("用户: ").append(userAnswer).append("\n");
                conversationLog.append("系统: ").append(response.getMessage()).append("\n");
                conversationLog.append("Action: ").append(response.getAction()).append("\n");
                appendOptions(conversationLog, response);
                conversationLog.append("\n");

                // 6. 检查是否结束
                if ("GENERATE_REPORT".equals(response.getAction()) ||
                        "SHOW_REPORT".equals(response.getAction()) ||
                        "TRIGGER_WARNING".equals(response.getAction())) {
                    finalReport = response.getData() != null ? response.getData().toString() : null;
                    conversationLog.append("【分诊报告】\n");
                    conversationLog.append(finalReport).append("\n");
                    break;
                }

                lastResponse = response;
            }

            return InvokeResult.builder()
                    .conversationLog(conversationLog.toString())
                    .finalReport(finalReport)
                    .build();

        } catch (Exception ex) {
            log.error("调用分诊系统失败: caseId={}", evalCase.getCaseId(), ex);
            return InvokeResult.builder()
                    .conversationLog(conversationLog.toString())
                    .finalReport(null)
                    .error(ex.getMessage())
                    .build();
        }
    }

    /**
     * 提取并追加选项信息到对话记录
     */
    private void appendOptions(StringBuilder conversationLog, TriageAnalyzeResponse response) {
        try {
            if (response.getData() != null && "ASK_CLARIFICATION".equals(response.getAction())) {
                ObjectMapper mapper = new ObjectMapper();
                TriageClarificationData data = mapper.convertValue(response.getData(), TriageClarificationData.class);

                if (data.getOptions() != null && !data.getOptions().isEmpty()) {
                    conversationLog.append("选项：[");
                    for (int i = 0; i < data.getOptions().size(); i++) {
                        TriageClarificationData.QuestionOption option = data.getOptions().get(i);
                        if (i > 0) {
                            conversationLog.append(", ");
                        }
                        conversationLog.append(option.getLabel());
                    }
                    conversationLog.append("]\n");
                }
            }
        } catch (Exception ex) {
            log.debug("提取选项信息失败", ex);
        }
    }

    /**
     * 从系统问题中提取槽位（关键词匹配）
     */
    private SlotCode extractSlotFromQuestion(String question) {
        if (question == null || question.isBlank()) {
            return null;
        }

        String q = question.toLowerCase();

        // 主诉
        if (q.contains("主要不适") || q.contains("哪里不舒服")) {
            return SlotCode.PRIMARY_SYMPTOM;
        }

        // 时间相关
        if (q.contains("什么时候开始") || q.contains("多久了") || q.contains("持续时间")) {
            return SlotCode.DURATION;
        }
        if (q.contains("什么时候发作") || q.contains("发作时间")) {
            return SlotCode.ONSET_TIME;
        }

        // 部位
        if (q.contains("哪里疼") || q.contains("什么部位") || q.contains("疼痛位置")) {
            return SlotCode.BODY_PART;
        }

        // 疼痛特征
        if (q.contains("疼痛程度") || q.contains("多疼") || q.contains("严重吗")) {
            return SlotCode.PAIN_SEVERITY;
        }
        if (q.contains("什么样的疼") || q.contains("疼痛性质") || q.contains("刺痛") || q.contains("胀痛")) {
            return SlotCode.PAIN_CHARACTER;
        }

        // 腹泻相关
        if (q.contains("拉了几次") || q.contains("腹泻频率") || q.contains("一天几次")) {
            return SlotCode.DIARRHEA_FREQUENCY;
        }
        if (q.contains("大便") || q.contains("性状") || q.contains("水样") || q.contains("稀便")) {
            return SlotCode.STOOL_CHARACTER;
        }

        // 伴随症状
        if (q.contains("发热") || q.contains("发烧") || q.contains("体温")) {
            return SlotCode.FEVER_PRESENCE;
        }
        if (q.contains("恶心")) {
            return SlotCode.NAUSEA_PRESENCE;
        }
        if (q.contains("呕吐")) {
            return SlotCode.VOMITING_PRESENCE;
        }
        if (q.contains("呼吸困难") || q.contains("喘")) {
            return SlotCode.DYSPNEA_PRESENCE;
        }
        if (q.contains("出血")) {
            return SlotCode.BLEEDING_PRESENCE;
        }

        // 病史
        if (q.contains("吃了什么") || q.contains("饮食") || q.contains("食物")) {
            return SlotCode.FOOD_HISTORY;
        }
        if (q.contains("既往病史") || q.contains("以前得过")) {
            return SlotCode.DIAGNOSIS_HISTORY;
        }
        if (q.contains("用药") || q.contains("吃药")) {
            return SlotCode.MEDICATION_HISTORY;
        }

        // 其他
        if (q.contains("伴随症状") || q.contains("其他不适")) {
            return SlotCode.ASSOCIATED_SYMPTOMS;
        }
        if (q.contains("年龄") || q.contains("多大")) {
            return SlotCode.AGE;
        }

        // 咳嗽相关
        if (q.contains("咳嗽")) {
            return SlotCode.COUGH_PRESENCE;
        }
        if (q.contains("痰") || q.contains("咳痰")) {
            return SlotCode.SPUTUM_CHARACTER;
        }

        // 鼻部症状
        if (q.contains("鼻涕") || q.contains("流鼻涕")) {
            return SlotCode.NASAL_DISCHARGE_COLOR;
        }

        // 咽喉症状
        if (q.contains("咽痛") || q.contains("喉咙痛")) {
            return SlotCode.THROAT_PAIN;
        }

        // 全身症状
        if (q.contains("全身酸痛") || q.contains("肌肉酸痛")) {
            return SlotCode.BODY_ACHE;
        }

        return null;
    }

    /**
     * 从测试用例的标准对话中建立问答映射
     */
    private Map<SlotCode, String> buildAnswerMap(TriageEvalCase evalCase) {
        Map<SlotCode, String> answerMap = new HashMap<>();
        List<TriageEvalCase.DialogueTurn> dialogue = evalCase.getStandardDialogue();

        if (dialogue == null || dialogue.isEmpty()) {
            return answerMap;
        }

        for (int i = 0; i < dialogue.size() - 1; i++) {
            TriageEvalCase.DialogueTurn systemTurn = dialogue.get(i);
            TriageEvalCase.DialogueTurn userTurn = dialogue.get(i + 1);

            if ("system".equals(systemTurn.getRole()) && "user".equals(userTurn.getRole())) {
                SlotCode slot = extractSlotFromQuestion(systemTurn.getContent());
                if (slot != null) {
                    answerMap.put(slot, userTurn.getContent());
                    log.debug("映射槽位 {} -> 回答: ", slot, userTurn.getContent());
                }
            }
        }

        log.info("构建问答映射完成，共 {} 个槽位", answerMap.size());
        return answerMap;
    }

    /**
     * 使用 LLM 进行语义匹配（当关键词匹配失败时使用）
     */
    private String findAnswerByLLM(String systemQuestion, TriageEvalCase evalCase) {
        try {
            StringBuilder dialogueText = new StringBuilder();
            for (TriageEvalCase.DialogueTurn turn : evalCase.getStandardDialogue()) {
                dialogueText.append(turn.getRole()).append(": ").append(turn.getContent()).append("\n");
            }

            String prompt = String.format("""
                系统当前的问题是：%s

                测试用例的标准对话如下：
                %s

                请从标准对话中找出最匹配系统问题的用户回答。
                要求：
                1. 只返回用户回答的原文，不要添加任何解释
                2. 如果找不到匹配的回答，返回"未找到"
                3. 优先匹配语义相关的回答
                """, systemQuestion, dialogueText.toString());

            // 调用 LLM
            ChatMessage message = ChatMessage.user(prompt);

            String answer = triageModelGateway.chatWithTextModel(List.of(message), 0.3, 0.8, 500);

            if (answer != null && !answer.contains("未找到")) {
                log.info("LLM 匹配成功: {} -> {}", systemQuestion, answer);
                return answer.trim();
            }
        } catch (Exception ex) {
            log.warn("LLM 匹配失败", ex);
        }

        return null;
    }

    /**
     * 生成通用回答（当找不到匹配答案时使用）
     */
    private String generateGenericAnswer(String systemQuestion, SlotCode askedSlot) {
        if (systemQuestion == null) {
            return "不清楚";
        }

        String q = systemQuestion.toLowerCase();

        // 是非问题：优先回答"没有"
        if (q.contains("有没有") || q.contains("是否") || q.contains("有吗")) {
            return "没有";
        }

        // 时间问题
        if (q.contains("什么时候") || q.contains("多久")) {
            return "不太记得了";
        }

        // 程度问题
        if (q.contains("程度") || q.contains("严重") || q.contains("厉害")) {
            return "一般";
        }

        // 频率问题
        if (q.contains("几次") || q.contains("频率")) {
            return "不太清楚";
        }

        // 性状/颜色问题
        if (q.contains("什么样") || q.contains("颜色") || q.contains("性状")) {
            return "说不清楚";
        }

        // 默认回答
        return "不太清楚";
    }

    /**
     * 调用结果
     */
    @Data
    @Builder
    public static class InvokeResult {
        private String conversationLog;
        private String finalReport;
        private String error;

        public TriageEvalResult toEvalResult() {
            // 从 conversationLog 中提取 caseId 等信息（简化处理）
            return TriageEvalResult.builder()
                    .actualResponse(conversationLog)
                    .status(error == null ? "success" : "error")
                    .errorMessage(error)
                    .build();
        }
    }
}
