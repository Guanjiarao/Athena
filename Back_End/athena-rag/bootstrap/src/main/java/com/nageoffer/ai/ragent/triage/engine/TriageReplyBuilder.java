

package com.nageoffer.ai.ragent.triage.engine;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.nageoffer.ai.ragent.framework.convention.ChatMessage;
import com.nageoffer.ai.ragent.triage.controller.vo.TriageClarificationData;
import com.nageoffer.ai.ragent.triage.model.QuestionPlan;
import com.nageoffer.ai.ragent.triage.model.RiskLevel;
import com.nageoffer.ai.ragent.triage.model.SlotCode;
import com.nageoffer.ai.ragent.triage.model.Symptom;
import com.nageoffer.ai.ragent.triage.model.TriageContext;
import com.nageoffer.ai.ragent.triage.service.TriageModelGateway;
import com.nageoffer.ai.ragent.triage.worker.OptionGenerator;
import com.nageoffer.ai.ragent.triage.worker.QuestionPlanSupport;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
final class TriageReplyBuilder {

    private TriageReplyBuilder() {
    }

    static String buildClarificationReply(TriageContext context, OptionGenerator optionGenerator,
                                          QuestionPlanSupport questionPlanSupport, TriageModelGateway triageModelGateway) {
        log.info("[ReplyBuilder] 开始构建澄清回复, sessionId={}", context.getSessionId());

        List<String> prompts = new ArrayList<>();
        List<TriageClarificationData.QuestionOption> allOptions = new ArrayList<>();
        QuestionPlan questionPlan = context.getQuestionPlan();

        log.info("[ReplyBuilder] questionPlan 是否为空: {}", questionPlan == null);
        if (questionPlan != null) {
            log.info("[ReplyBuilder] questionPlan.nextSlotsToAsk: {}", questionPlan.getNextSlotsToAsk());
            log.info("[ReplyBuilder] questionPlan.priorityReason: {}", questionPlan.getPriorityReason());
        }

        // 限制单次追问数量：最多 2 个问题
        int maxQuestions = 2;

        if (questionPlan != null && questionPlan.getNextSlotsToAsk() != null && !questionPlan.getNextSlotsToAsk().isEmpty()) {
            log.info("[ReplyBuilder] 从 nextSlotsToAsk 生成问题, 槽位数量: {}", questionPlan.getNextSlotsToAsk().size());

            int count = 0;
            for (SlotCode slotCode : questionPlan.getNextSlotsToAsk()) {
                if (count >= maxQuestions) {
                    log.info("[ReplyBuilder] 已达到最大问题数量 {}, 停止添加", maxQuestions);
                    break;
                }

                String prompt = TriageReplyPromptSupport.promptForSlot(slotCode);
                log.info("[ReplyBuilder] 槽位 {} 的问题模板: {}", slotCode, prompt == null ? "null" : prompt);

                if (StrUtil.isNotBlank(prompt)) {
                    prompts.add(prompt);
                    count++;
                    log.info("[ReplyBuilder] 添加槽位 {} 的问题, 当前问题数: {}", slotCode, count);

                    // 为该槽位生成选项
                    if (optionGenerator != null) {
                        List<TriageClarificationData.QuestionOption> options = optionGenerator.generateOptionsForSlot(slotCode);
                        if (options != null && !options.isEmpty()) {
                            allOptions.addAll(options);
                            log.info("[ReplyBuilder] 为槽位 {} 生成了 {} 个选项", slotCode, options.size());
                        }
                    }
                }
            }
        }

        if (prompts.isEmpty()) {
            log.info("[ReplyBuilder] nextSlotsToAsk 未生成问题，尝试从 missingFields 生成");

            List<String> missingFields = context.getMissingFields() == null ? Collections.emptyList() : context.getMissingFields();
            log.info("[ReplyBuilder] missingFields: {}", missingFields);

            int count = 0;
            for (String field : missingFields) {
                if (count >= maxQuestions) {
                    log.info("[ReplyBuilder] 已达到最大问题数量 {}, 停止添加", maxQuestions);
                    break;
                }

                if (StrUtil.isBlank(field)) {
                    log.debug("[ReplyBuilder] 跳过空字段");
                    continue;
                }

                String prompt = TriageReplyPromptSupport.promptForField(field.trim());
                log.info("[ReplyBuilder] 字段 {} 的问题模板: {}", field, prompt == null ? "null" : prompt);

                if (StrUtil.isNotBlank(prompt)) {
                    prompts.add(prompt);
                    count++;
                    log.info("[ReplyBuilder] 添加字段 {} 的问题, 当前问题数: {}", field, count);

                    // 为该字段生成选项（通过字段名映射到槽位）
                    if (optionGenerator != null) {
                        SlotCode slotCode = TriageReplyPromptSupport.mapFieldToSlot(field.trim());
                        if (slotCode != null) {
                            List<TriageClarificationData.QuestionOption> options = optionGenerator.generateOptionsForSlot(slotCode);
                            if (options != null && !options.isEmpty()) {
                                allOptions.addAll(options);
                                log.info("[ReplyBuilder] 为字段 {} (槽位 {}) 生成了 {} 个选项", field, slotCode, options.size());
                            }
                        }
                    }
                }
            }
        }

        if (prompts.isEmpty()) {
            log.warn("[ReplyBuilder] 所有问题列表都为空，退化到通用话术, sessionId={}", context.getSessionId());
            log.warn("[ReplyBuilder] 退化原因分析: questionPlan={}, nextSlotsToAsk={}, missingFields={}",
                questionPlan == null ? "null" : "存在",
                questionPlan == null || questionPlan.getNextSlotsToAsk() == null ? "null" : questionPlan.getNextSlotsToAsk(),
                context.getMissingFields());

            // 新增：检测通用兜底问题的连续出现
            int consecutiveGenericCount = countConsecutiveGenericQuestions(context);
            // 关键修复：如果当前也要返回通用问题，计数+1（预判）
            consecutiveGenericCount++;
            log.warn("[ReplyBuilder] 检测到连续 {} 次通用兜底问题（含本次）", consecutiveGenericCount);

            // 如果连续出现2次或以上，触发LLM智能决策
            if (consecutiveGenericCount >= 2) {
                log.warn("[ReplyBuilder] 连续通用问题达到阈值，触发LLM智能兜底决策");

                // 调用LLM智能兜底机制
                QuestionPlan emergencyPlan = questionPlanSupport.selectEmergencySlotByLLM(context, triageModelGateway);

                if (emergencyPlan.getNextSlotsToAsk().isEmpty()) {
                    log.info("[ReplyBuilder] LLM 建议生成报告: {}", emergencyPlan.getPriorityReason());
                    // 返回特殊标记，让上层生成报告
                    context.setForceGenerateReport(true);
                    context.setForceGenerateReportReason(emergencyPlan.getPriorityReason());
                    return "##FORCE_GENERATE_REPORT##";
                }

                // LLM选择了一个槽位，尝试为该槽位生成问题
                SlotCode selectedSlot = emergencyPlan.getNextSlotsToAsk().get(0);
                log.info("[ReplyBuilder] LLM 选择槽位: {}, 理由: {}", selectedSlot, emergencyPlan.getPriorityReason());

                String prompt = TriageReplyPromptSupport.promptForSlot(selectedSlot);
                if (StrUtil.isNotBlank(prompt)) {
                    prompts.add(prompt);
                    log.info("[ReplyBuilder] 为LLM选择的槽位 {} 生成问题: {}", selectedSlot, prompt);

                    // 为该槽位生成选项
                    if (optionGenerator != null) {
                        List<TriageClarificationData.QuestionOption> options = optionGenerator.generateOptionsForSlot(selectedSlot);
                        if (options != null && !options.isEmpty()) {
                            allOptions.addAll(options);
                            log.info("[ReplyBuilder] 为槽位 {} 生成了 {} 个选项", selectedSlot, options.size());
                        }
                    }

                    context.setGeneratedOptions(allOptions);
                    String contextualIntro = buildContextualIntro(context);
                    String finalReply = contextualIntro + String.join("；", prompts) + "。";
                    log.info("[ReplyBuilder] LLM智能兜底后的最终回复: {}", finalReply);
                    return finalReply;
                }
            }

            // 即使没有明确的问题，也尝试为常见槽位生成选项作为兜底
            if (optionGenerator != null && allOptions.isEmpty()) {
                // 尝试为一些常见的槽位生成选项
                List<SlotCode> fallbackSlots = List.of(
                    // 时间维度
                    SlotCode.DURATION, SlotCode.ONSET_TIME,

                    // 疼痛维度
                    SlotCode.PAIN_SEVERITY, SlotCode.PAIN_CHARACTER, SlotCode.BODY_PART,

                    // 伴随症状
                    SlotCode.FEVER_PRESENCE, SlotCode.NAUSEA_PRESENCE, SlotCode.VOMITING_PRESENCE,
                    SlotCode.DIARRHEA_PRESENCE, SlotCode.COUGH_PRESENCE, SlotCode.DYSPNEA_PRESENCE,

                    // 病史
                    SlotCode.DIAGNOSIS_HISTORY, SlotCode.MEDICATION_HISTORY,

                    // 其他
                    SlotCode.ASSOCIATED_SYMPTOMS, SlotCode.AGE
                );
                for (SlotCode slot : fallbackSlots) {
                    List<TriageClarificationData.QuestionOption> options = optionGenerator.generateOptionsForSlot(slot);
                    if (options != null && !options.isEmpty()) {
                        allOptions.addAll(options);
                        log.info("[ReplyBuilder] 兜底：为槽位 {} 生成了 {} 个选项", slot, options.size());
                        break; // 只生成一组选项即可
                    }
                }
            }

            context.setGeneratedOptions(allOptions);
            log.info("[ReplyBuilder] 通用话术模式，总共生成了 {} 个选项", allOptions.size());
            return "为了继续判断，请再补充一些不适细节。";
        }

        log.info("[ReplyBuilder] 成功生成 {} 个问题", prompts.size());

        // 将生成的选项存储到 context 的临时字段中（用于后续响应构建）
        context.setGeneratedOptions(allOptions);
        log.info("[ReplyBuilder] 总共生成了 {} 个选项", allOptions.size());

        // 添加上下文引导：根据已知症状生成个性化引导语
        String contextualIntro = buildContextualIntro(context);
        String finalReply = contextualIntro + String.join("；", prompts) + "。";

        log.info("[ReplyBuilder] 最终回复: {}", finalReply);

        return finalReply;
    }

    private static String buildContextualIntro(TriageContext context) {
        // 根据已知症状生成个性化引导语
        if (context.getExtractedSymptoms() != null && !context.getExtractedSymptoms().isEmpty()) {
            log.debug("[ReplyBuilder] 使用症状引导语, 症状数量: {}", context.getExtractedSymptoms().size());
            return "为了更准确评估您的情况，需要了解：";
        }
        log.debug("[ReplyBuilder] 使用默认引导语");
        return "为了更准确判断，请再补充一下：";
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
        // 使用科室推荐引擎获取推荐科室
        DepartmentRecommender recommender = new DepartmentRecommender();
        DepartmentRecommender.DepartmentRecommendation recommendation = recommender.recommend(context);

        try {
            List<ChatMessage> messages = new ArrayList<>();
            String systemPrompt = """
                    你是医疗分诊系统中的报告生成助手。请用简洁中文输出适合手机端阅读的分诊摘要，不要给出明确诊断，不要输出 JSON。

                    报告必须包含以下部分：
                    1. 主诉提炼：用一句话总结患者的主要不适（如："患者主诉腹痛3天，伴恶心呕吐"）
                    2. 症状总结：列出所有收集到的症状信息（部位、性质、程度、持续时间、伴随症状等）
                    3. 风险分析：说明风险等级的判断依据和需要关注的危险信号
                    4. 建议科室：明确推荐就诊的科室（已提供：%s）
                    5. 行动建议：给出具体的就医建议（如：24小时内就诊、立即急诊、可观察等）和注意事项

                    报告语言要求：
                    - 专业但易懂，避免过度医学术语
                    - 简洁明了，重点突出
                    - 语气温和，避免引起恐慌
                    - 每个部分用明确的标题分隔

                    报告结构示例：
                    【主诉提炼】
                    患者主诉...

                    【症状总结】
                    - 主要症状：...
                    - 持续时间：...
                    - 伴随症状：...

                    【风险评估】
                    风险等级：...
                    判断依据：...

                    【建议科室】
                    建议就诊：%s

                    【行动建议】
                    建议您...
                    """.formatted(recommendation.getDepartment(), recommendation.getDepartment());

            messages.add(ChatMessage.system(systemPrompt));
            messages.add(ChatMessage.user(buildReportPrompt(context, recommendation)));
            String report = triageModelGateway.chatWithReportModel(messages, 0.2D, 0.3D, 900);
            if (StrUtil.isNotBlank(report)) {
                return report.trim();
            }
        } catch (Exception ignored) {
        }
        return buildFallbackReport(context, recommendation);
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

    private static String buildReportPrompt(TriageContext context, DepartmentRecommender.DepartmentRecommendation recommendation) {
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

        // 添加推荐科室信息
        builder.append("\n【重要】推荐就诊科室：").append(recommendation.getDepartment()).append("\n");
        builder.append("推荐理由：").append(recommendation.getReason()).append("\n");
        builder.append("\n请根据以上信息生成分诊报告，报告中必须明确包含建议就诊科室：").append(recommendation.getDepartment()).append("\n");
        builder.append("例如：\"建议尽快就诊").append(recommendation.getDepartment()).append("进行进一步检查。\"\n");

        builder.append("\n科室选择参考：\n");
        builder.append("- 消化系统症状（腹痛、腹泻、呕吐、恶心等）→ 消化内科\n");
        builder.append("- 呼吸系统症状（咳嗽、气喘、呼吸困难、胸闷等）→ 呼吸内科\n");
        builder.append("- 心血管症状（胸痛、心悸、心慌等）→ 心内科\n");
        builder.append("- 神经系统症状（头痛、头晕、眩晕、抽搐等）→ 神经内科\n");
        builder.append("- 骨骼肌肉症状（关节痛、骨痛、扭伤、骨折等）→ 骨科\n");
        builder.append("- 皮肤症状（皮疹、瘙痒、红肿等）→ 皮肤科\n");
        builder.append("- 眼部症状（视力下降、眼痛等）→ 眼科\n");
        builder.append("- 耳鼻喉症状（耳痛、鼻塞、咽痛等）→ 耳鼻喉科\n");
        builder.append("- 泌尿系统症状（尿频、尿痛、血尿等）→ 泌尿外科\n");
        builder.append("- 妇科症状（月经异常、阴道出血、孕期不适等）→ 妇产科\n");
        builder.append("- 儿童患者 → 儿科\n");
        builder.append("- 高风险/急危重症状 → 急诊科\n");
        builder.append("- 症状不明确或多系统症状 → 全科/内科\n");
        return builder.toString();
    }

    private static String buildFallbackReport(TriageContext context, DepartmentRecommender.DepartmentRecommendation recommendation) {
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
        RiskLevel riskLevel = context.getRiskAssessment();
        if (riskLevel != null) {
            builder.append("风险等级：").append(riskLevel.getLevel()).append("，依据：")
                    .append(StrUtil.blankToDefault(riskLevel.getEvidence(), "暂无")).append("\n");
        }
        builder.append("建议科室：").append(recommendation.getDepartment()).append("（").append(recommendation.getReason()).append("）\n");
        builder.append("行动建议：本结果仅用于分诊辅助，不能替代线下面诊，如症状持续或加重，请及时就医。");
        return builder.toString();
    }

    /**
     * 统计历史对话中连续出现通用兜底问题的次数
     */
    private static int countConsecutiveGenericQuestions(TriageContext context) {
        if (context.getSystemReplyHistory() == null || context.getSystemReplyHistory().isEmpty()) {
            log.info("[ReplyBuilder] systemReplyHistory 为空，返回计数 0");
            return 0;
        }

        String genericQuestion = "为了继续判断，请再补充一些不适细节";
        int count = 0;

        // 从最近的系统回复往前查找
        List<String> history = context.getSystemReplyHistory();
        log.info("[ReplyBuilder] 开始检测连续通用问题，systemReplyHistory 数量: {}", history.size());

        for (int i = history.size() - 1; i >= 0; i--) {
            String msg = history.get(i);
            if (msg != null && msg.contains(genericQuestion)) {
                count++;
                log.info("[ReplyBuilder] 检测到通用问题 [{}]: {}", i, msg);
            } else if (msg != null && !msg.trim().isEmpty()) {
                // 遇到非通用问题的非空消息，停止计数
                log.info("[ReplyBuilder] 遇到非通用问题 [{}]，停止计数: {}", i, msg);
                break;
            }
        }

        log.info("[ReplyBuilder] 连续通用问题计数结果: {}", count);
        return count;
    }
}
