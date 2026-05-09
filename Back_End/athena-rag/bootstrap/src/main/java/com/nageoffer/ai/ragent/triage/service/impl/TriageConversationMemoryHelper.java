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

package com.nageoffer.ai.ragent.triage.service.impl;

import cn.hutool.core.util.StrUtil;
import com.nageoffer.ai.ragent.framework.convention.ChatMessage;
import com.nageoffer.ai.ragent.triage.model.QuestionPlan;
import com.nageoffer.ai.ragent.triage.model.SlotCode;
import com.nageoffer.ai.ragent.triage.model.SlotStatus;
import com.nageoffer.ai.ragent.triage.model.SlotValue;
import com.nageoffer.ai.ragent.triage.model.Symptom;
import com.nageoffer.ai.ragent.triage.model.TriageContext;
import com.nageoffer.ai.ragent.triage.service.TriageModelGateway;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
class TriageConversationMemoryHelper {

    String summarizeConversation(TriageContext context,
                                 List<String> evictedTurns,
                                 int summaryMaxChars,
                                 TriageModelGateway triageModelGateway) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system("你是医疗分诊会话的记忆压缩助手。请把更早的对话压缩成简洁中文摘要，只保留已确认的槽位信息、主诉、部位、持续时间、伴随症状、风险线索和仍待补充的信息。不要输出诊断，不要输出 JSON。"));
        messages.add(ChatMessage.user(buildSummaryPrompt(context, evictedTurns, summaryMaxChars)));
        try {
            String summary = triageModelGateway.summarizeConversationMemory(messages, 400);
            if (StrUtil.isNotBlank(summary)) {
                return truncate(summary.trim(), summaryMaxChars);
            }
        } catch (Exception ignored) {
        }
        return buildHeuristicSummary(context, evictedTurns, summaryMaxChars);
    }

    String buildSummaryPrompt(TriageContext context, List<String> evictedTurns, int summaryMaxChars) {
        StringBuilder builder = new StringBuilder();
        if (StrUtil.isNotBlank(context.getConversationSummary())) {
            builder.append("已有摘要:\n").append(context.getConversationSummary()).append("\n\n");
        }
        builder.append("需压缩的旧对话:\n").append(String.join("\n", evictedTurns)).append("\n\n");
        appendIfPresent(builder, buildComplaintSummaryText(context));
        appendIfPresent(builder, buildSlotSummaryText(context));
        appendIfPresent(builder, buildPendingSummaryText(context));
        appendIfPresent(builder, buildSymptomSummaryText(context));
        builder.append("请输出不超过").append(summaryMaxChars).append("字的摘要。");
        return builder.toString();
    }

    String buildHeuristicSummary(TriageContext context, List<String> evictedTurns, int summaryMaxChars) {
        List<String> parts = new ArrayList<>();
        addIfPresent(parts, StrUtil.isBlank(context.getConversationSummary()) ? null : context.getConversationSummary().trim());
        addIfPresent(parts, buildComplaintSummaryText(context));
        addIfPresent(parts, buildSlotSummaryText(context));
        addIfPresent(parts, buildPendingSummaryText(context));
        addIfPresent(parts, buildSymptomSummaryText(context));
        if (!evictedTurns.isEmpty()) {
            parts.add("早期原话：" + String.join("；", evictedTurns));
        }
        return truncate(String.join("。", parts), summaryMaxChars);
    }

    private void appendIfPresent(StringBuilder builder, String value) {
        if (StrUtil.isNotBlank(value)) {
            builder.append(value).append("\n");
        }
    }

    private void addIfPresent(List<String> values, String value) {
        if (StrUtil.isNotBlank(value)) {
            values.add(value);
        }
    }

    private String buildComplaintSummaryText(TriageContext context) {
        if (context == null || StrUtil.isBlank(context.getFinalPrimaryComplaint())) {
            return null;
        }
        return "最终主诉：" + context.getFinalPrimaryComplaint().trim();
    }

    private String buildSlotSummaryText(TriageContext context) {
        if (context.getSlotState() == null || context.getSlotState().getSlots() == null || context.getSlotState().getSlots().isEmpty()) {
            return null;
        }
        List<String> slotLines = new ArrayList<>();
        for (SlotCode slotCode : SlotCode.values()) {
            SlotValue slotValue = context.getSlotState().get(slotCode);
            if (slotValue == null || slotValue.getStatus() != SlotStatus.FILLED || StrUtil.isBlank(slotValue.getValue())) {
                continue;
            }
            if (slotCode == SlotCode.PRIMARY_SYMPTOM) {
                continue;
            }
            slotLines.add(slotCode.name() + "=" + slotValue.getValue());
        }
        return slotLines.isEmpty() ? null : "已确认槽位：" + String.join("；", slotLines);
    }

    private String buildPendingSummaryText(TriageContext context) {
        List<String> parts = new ArrayList<>();
        if (context.getPendingSlots() != null && !context.getPendingSlots().isEmpty()) {
            parts.add("待补槽位：" + context.getPendingSlots());
        }
        QuestionPlan questionPlan = context.getQuestionPlan();
        if (questionPlan != null && questionPlan.getNextSlotsToAsk() != null && !questionPlan.getNextSlotsToAsk().isEmpty()) {
            parts.add("下一轮追问：" + questionPlan.getNextSlotsToAsk());
        }
        if (parts.isEmpty() && context.getMissingFields() != null && !context.getMissingFields().isEmpty()) {
            parts.add("待补充字段：" + String.join("、", context.getMissingFields()));
        }
        return parts.isEmpty() ? null : String.join("；", parts);
    }

    private String buildSymptomSummaryText(TriageContext context) {
        if (context.getExtractedSymptoms() == null || context.getExtractedSymptoms().isEmpty()) {
            return null;
        }
        List<String> symptomLines = new ArrayList<>();
        for (Symptom symptom : context.getExtractedSymptoms()) {
            if (symptom == null || StrUtil.isBlank(symptom.getName())) {
                continue;
            }
            StringBuilder line = new StringBuilder(symptom.getName().trim());
            if (StrUtil.isNotBlank(symptom.getBodyPart())) {
                line.append("(").append(symptom.getBodyPart().trim()).append(")");
            }
            if (StrUtil.isNotBlank(symptom.getDuration())) {
                line.append(" 持续").append(symptom.getDuration().trim());
            }
            symptomLines.add(line.toString());
        }
        return symptomLines.isEmpty() ? null : "兼容症状：" + String.join("；", symptomLines);
    }

    private String truncate(String text, int maxChars) {
        if (StrUtil.isBlank(text) || text.length() <= maxChars) {
            return StrUtil.blankToDefault(text, "");
        }
        return text.substring(0, maxChars);
    }
}
