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

package com.nageoffer.ai.ragent.triage.engine;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.nageoffer.ai.ragent.framework.convention.ChatMessage;
import com.nageoffer.ai.ragent.triage.model.QuestionPlan;
import com.nageoffer.ai.ragent.triage.model.RiskLevel;
import com.nageoffer.ai.ragent.triage.model.SlotCode;
import com.nageoffer.ai.ragent.triage.model.Symptom;
import com.nageoffer.ai.ragent.triage.model.TriageContext;
import com.nageoffer.ai.ragent.triage.service.TriageModelGateway;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class TriageReplyBuilder {

    private TriageReplyBuilder() {
    }

    static String buildClarificationReply(TriageContext context) {
        List<String> prompts = new ArrayList<>();
        QuestionPlan questionPlan = context.getQuestionPlan();
        if (questionPlan != null && questionPlan.getNextSlotsToAsk() != null && !questionPlan.getNextSlotsToAsk().isEmpty()) {
            for (SlotCode slotCode : questionPlan.getNextSlotsToAsk()) {
                String prompt = TriageReplyPromptSupport.promptForSlot(slotCode);
                if (StrUtil.isNotBlank(prompt)) {
                    prompts.add(prompt);
                }
            }
        }
        if (prompts.isEmpty()) {
            List<String> missingFields = context.getMissingFields() == null ? Collections.emptyList() : context.getMissingFields();
            for (String field : missingFields) {
                if (StrUtil.isBlank(field)) {
                    continue;
                }
                String prompt = TriageReplyPromptSupport.promptForField(field.trim());
                if (StrUtil.isNotBlank(prompt)) {
                    prompts.add(prompt);
                }
            }
        }
        return prompts.isEmpty() ? "为了继续判断，请再补充一些不适细节。" : "为了更准确判断，请再补充一下：" + String.join("；", prompts) + "。";
    }

    static String buildWarningReply(TriageContext context) {
        RiskLevel riskLevel = context.getRiskAssessment();
        String evidence = riskLevel == null ? "" : sanitizeSentence(StrUtil.blankToDefault(riskLevel.getEvidence(), ""));
        List<String> riskHints = riskLevel == null || riskLevel.getRiskHints() == null ? List.of() : riskLevel.getRiskHints();
        StringBuilder builder = new StringBuilder("根据当前症状描述，存在较高风险，建议尽快前往线下医院就诊。");
        if (StrUtil.isNotBlank(evidence)) {
            builder.append("重点依据：").append(evidence).append(" ");
        }
        String specificHint = buildSpecificWarningHint(riskHints);
        if (StrUtil.isNotBlank(specificHint)) {
            builder.append(specificHint).append(" ");
        }
        builder.append("如果症状持续加重，或出现呼吸困难、意识变化、明显出血等情况，请及时前往急诊。");
        return builder.toString().trim();
    }

    static String generatePreTriageReport(TriageContext context, TriageModelGateway triageModelGateway) {
        try {
            List<ChatMessage> messages = new ArrayList<>();
            messages.add(ChatMessage.system("你是医疗分诊系统中的报告生成助手。请用简洁中文输出适合手机端阅读的分诊摘要，不要给出明确诊断，不要输出 JSON。"));
            messages.add(ChatMessage.user(buildReportPrompt(context)));
            String report = triageModelGateway.chatWithReportModel(messages, 0.2D, 0.3D, 900);
            if (StrUtil.isNotBlank(report)) {
                return report.trim();
            }
        } catch (Exception ignored) {
        }
        return buildFallbackReport(context);
    }

    private static String buildSpecificWarningHint(List<String> riskHints) {
        if (riskHints == null || riskHints.isEmpty()) {
            return "";
        }
        if (riskHints.contains("SEIZURE")) {
            return "已出现抽搐/惊厥等高危红旗表现。";
        }
        if (riskHints.contains("PREGNANCY_BLEEDING")) {
            return "妊娠相关出血需要尽快线下评估。";
        }
        if (riskHints.contains("BLEEDING")) {
            return "明显出血提示存在急危重风险。";
        }
        if (riskHints.contains("DYSPNEA") || riskHints.contains("CHEST_PAIN_WITH_DYSPNEA")) {
            return "呼吸困难相关表现提示需尽快急诊评估。";
        }
        return "";
    }

    private static String sanitizeSentence(String text) {
        if (StrUtil.isBlank(text)) {
            return "";
        }
        return text.trim().replace("。。", "。");
    }

    private static String buildReportPrompt(TriageContext context) {
        StringBuilder builder = new StringBuilder();
        builder.append("会话ID：").append(context.getSessionId()).append("\n");
        builder.append("用户描述：").append(StrUtil.blankToDefault(context.getUserInput(), "暂无")).append("\n");
        builder.append("结构化症状：\n");
        if (CollUtil.isEmpty(context.getExtractedSymptoms())) {
            builder.append("- 暂无可用结构化症状\n");
        } else {
            for (Symptom symptom : context.getExtractedSymptoms()) {
                builder.append("- 症状：").append(StrUtil.blankToDefault(symptom.getName(), "未知症状")).append("\n");
                if (StrUtil.isNotBlank(symptom.getBodyPart())) builder.append("  部位：").append(symptom.getBodyPart()).append("\n");
                if (StrUtil.isNotBlank(symptom.getDuration())) builder.append("  持续时间：").append(symptom.getDuration()).append("\n");
                if (StrUtil.isNotBlank(symptom.getSeverity())) builder.append("  程度：").append(symptom.getSeverity()).append("\n");
                if (CollUtil.isNotEmpty(symptom.getCharacteristics())) builder.append("  特征：").append(String.join("、", symptom.getCharacteristics())).append("\n");
                if (CollUtil.isNotEmpty(symptom.getAccompanyingSymptoms())) builder.append("  伴随症状：").append(String.join("、", symptom.getAccompanyingSymptoms())).append("\n");
            }
        }
        RiskLevel riskLevel = context.getRiskAssessment();
        if (riskLevel != null) {
            builder.append("风险等级：").append(riskLevel.getLevel()).append("\n");
            builder.append("风险分数：").append(riskLevel.getScore()).append("\n");
            builder.append("依据：").append(StrUtil.blankToDefault(riskLevel.getEvidence(), "暂无")).append("\n");
            builder.append("解释：").append(StrUtil.blankToDefault(riskLevel.getRationale(), "暂无")).append("\n");
        }
        return builder.toString();
    }

    private static String buildFallbackReport(TriageContext context) {
        StringBuilder builder = new StringBuilder();
        builder.append("【分诊摘要】\n");
        builder.append("主要不适：").append(StrUtil.blankToDefault(context.getUserInput(), "暂无")).append("\n");
        builder.append("症状概览：");
        if (CollUtil.isEmpty(context.getExtractedSymptoms())) {
            builder.append("当前可用信息有限，仍需继续补充描述。\n");
        } else {
            List<String> lines = new ArrayList<>();
            for (Symptom symptom : context.getExtractedSymptoms()) {
                StringBuilder line = new StringBuilder(StrUtil.blankToDefault(symptom.getName(), "症状"));
                if (StrUtil.isNotBlank(symptom.getBodyPart())) line.append("（").append(symptom.getBodyPart()).append("）");
                if (StrUtil.isNotBlank(symptom.getDuration())) line.append("，持续").append(symptom.getDuration());
                if (StrUtil.isNotBlank(symptom.getSeverity())) line.append("，程度").append(symptom.getSeverity());
                lines.add(line.toString());
            }
            builder.append(String.join("；", lines)).append("。\n");
        }
        if (context.getRiskAssessment() != null) {
            builder.append("风险等级：").append(context.getRiskAssessment().getLevel()).append("，依据：")
                    .append(StrUtil.blankToDefault(context.getRiskAssessment().getEvidence(), "暂无")).append("\n");
        }
        builder.append("建议：本结果仅用于分诊辅助，不能替代线下面诊，如症状持续或加重，请及时就医。");
        return builder.toString();
    }
}
