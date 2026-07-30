

package com.nageoffer.ai.ragent.triage.engine;

import com.nageoffer.ai.ragent.triage.model.QuestionPlan;
import com.nageoffer.ai.ragent.triage.model.SlotCode;
import com.nageoffer.ai.ragent.triage.model.TriageAction;
import com.nageoffer.ai.ragent.triage.model.TriageContext;
import com.nageoffer.ai.ragent.triage.service.TriageModelGateway;
import com.nageoffer.ai.ragent.triage.question.QuestionOptionProvider;
import com.nageoffer.ai.ragent.triage.question.QuestionPlanningSupport;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

@Slf4j
final class TriageStageExecutor {

    private TriageStageExecutor() {
    }

    static String executeValidation(TriageContext context,
                                    QuestionOptionProvider optionGenerator,
                                    QuestionPlanningSupport questionPlanSupport,
                                    TriageModelGateway triageModelGateway) {
        QuestionPlan questionPlanAfterPlanner = context.getQuestionPlan();
        log.info("[StageExecutor] 使用 Supervisor/ContextReducer 已写入的问题计划: nextSlotsToAsk={}, pendingSlots={}, priorityReason={}, policyReason={}, askCount={}, followUpMode={}",
                questionPlanAfterPlanner == null ? "null" : questionPlanAfterPlanner.getNextSlotsToAsk(),
                questionPlanAfterPlanner == null ? "null" : questionPlanAfterPlanner.getPendingSlots(),
                questionPlanAfterPlanner == null ? "null" : questionPlanAfterPlanner.getPriorityReason(),
                questionPlanAfterPlanner == null ? "null" : questionPlanAfterPlanner.getPolicyReason(),
                questionPlanAfterPlanner == null ? "null" : questionPlanAfterPlanner.getAskCount(),
                questionPlanAfterPlanner == null ? "null" : questionPlanAfterPlanner.getFollowUpMode());
        syncMissingFieldsFromQuestionPlan(context);
        log.info("[StageExecutor] syncMissingFields 后: missingFields={}, pendingSlots={}, hasMissingFields={}",
                context.getMissingFields(), context.getPendingSlots(), context.hasMissingFields());
        QuestionPlan questionPlan = context.getQuestionPlan();
        if (questionPlan != null && questionPlan.getNextSlotsToAsk() != null) {
            context.setLastAskedSlots(new ArrayList<>(questionPlan.getNextSlotsToAsk()));
            log.info("[StageExecutor] 更新 lastAskedSlots: {}", context.getLastAskedSlots());
        }
        if (context.hasMissingFields()) {
            log.info("[StageExecutor] 进入澄清分支: missingFields={}, pendingSlots={}, questionPlan.nextSlotsToAsk={}, finalReplyBeforeBuild={}",
                    context.getMissingFields(), context.getPendingSlots(),
                    questionPlan == null ? "null" : questionPlan.getNextSlotsToAsk(),
                    context.getFinalReply());
            context.setNextAction(TriageAction.ASK_CLARIFICATION);
            String clarificationReply = TriageReplyBuilder.buildClarificationReply(context, optionGenerator, questionPlanSupport, triageModelGateway);
            log.info("[StageExecutor] buildClarificationReply 返回: {}", clarificationReply);
            context.setFinalReply(clarificationReply);
            log.info("[StageExecutor] finalReply 已写入 context: {}", context.getFinalReply());

            // 检查是否需要强制生成报告（LLM智能兜底决策）
            if (Boolean.TRUE.equals(context.getForceGenerateReport())) {
                log.info("[StageExecutor] LLM智能兜底决策：强制生成报告, 理由: {}", context.getForceGenerateReportReason());
                // 不在这里生成报告，让状态机处理
                return "LLM emergency fallback triggered: " + context.getForceGenerateReportReason();
            }

            return buildPendingSlotRationale(context);
        }
        log.info("[StageExecutor] 未进入澄清分支: missingFields={}, pendingSlots={}, questionPlan.nextSlotsToAsk={}",
                context.getMissingFields(), context.getPendingSlots(), questionPlan == null ? "null" : questionPlan.getNextSlotsToAsk());
        return "All mandatory slot requirements are complete.";
    }

    private static void syncMissingFieldsFromQuestionPlan(TriageContext context) {
        QuestionPlan questionPlan = context.getQuestionPlan();
        if (questionPlan == null || questionPlan.getPendingSlots() == null || questionPlan.getPendingSlots().isEmpty()) {
            context.setMissingFields(new ArrayList<>());
            return;
        }
        List<String> missingFields = new ArrayList<>();
        for (SlotCode slotCode : questionPlan.getPendingSlots()) {
            String fieldName = mapSlotToMissingField(slotCode);
            if (fieldName != null) {
                missingFields.add(fieldName);
            }
        }
        context.setMissingFields(missingFields);
    }

    private static String buildPendingSlotRationale(TriageContext context) {
        List<SlotCode> pendingSlots = context.getPendingSlots() == null ? List.of() : context.getPendingSlots();
        QuestionPlan questionPlan = context.getQuestionPlan();
        if (!pendingSlots.isEmpty()) {
            String priorityReason = questionPlan == null ? null : questionPlan.getPriorityReason();
            String slotSummary = pendingSlots.stream()
                    .map(TriageStageExecutor::mapSlotToMissingField)
                    .filter(each -> each != null && !each.isBlank())
                    .reduce((left, right) -> left + ", " + right)
                    .orElse("关键槽位");
            if (priorityReason != null && !priorityReason.isBlank()) {
                return "Pending slots: " + slotSummary + ". " + priorityReason;
            }
            return "Pending slots: " + slotSummary + ".";
        }
        List<String> missingFields = context.getMissingFields() == null ? List.of() : context.getMissingFields();
        if (!missingFields.isEmpty()) {
            return "Compatibility fallback missing fields: " + String.join(", ", missingFields);
        }
        return "Additional clarification is required before risk assessment.";
    }

    private static String mapSlotToMissingField(SlotCode slotCode) {
        if (slotCode == null) {
            return null;
        }
        return switch (slotCode) {
            case PRIMARY_SYMPTOM -> "主诉症状";
            case SYMPTOM -> "症状";
            case DURATION -> "持续时间";
            case ONSET_TIME -> "发病时间";
            case BODY_PART -> "疼痛部位";
            case PAIN_CHARACTER -> "疼痛性质";
            case PAIN_SEVERITY -> "疼痛程度";
            case AGGRAVATING_FACTORS -> "加重因素";
            case RELIEVING_FACTORS -> "缓解因素";
            case FEVER_PRESENCE -> "是否伴随发热";
            case TEMPERATURE -> "体温";
            case NAUSEA_PRESENCE -> "是否伴随恶心";
            case VOMITING_PRESENCE -> "是否伴随呕吐";
            case DYSPNEA_PRESENCE -> "是否伴随呼吸困难";
            case BLEEDING_PRESENCE -> "是否伴随出血";
            case PREGNANCY_STATUS -> "是否妊娠";
            case SEIZURE_PRESENCE -> "是否存在抽搐";
            case DIARRHEA_PRESENCE -> "是否伴随腹泻";
            case STOOL_CHARACTER -> "大便性状";
            case DIAGNOSIS_HISTORY -> "诊断史";
            case ASSOCIATED_SYMPTOMS -> "伴随症状";
            case FEVER_TEMPERATURE -> "体温";
            case AGE -> "年龄";
            case COUGH_PRESENCE -> "是否咳嗽";
            case SPUTUM_CHARACTER -> "痰液性状";
            case ALLERGY_HISTORY -> "过敏史";

            // 消化系统相关槽位
            case DIARRHEA_FREQUENCY -> "腹泻次数";
            case FOOD_HISTORY -> "饮食史";
            case PAIN_TIMING -> "疼痛时机";
            case ACID_REFLUX -> "是否反酸";
            case WEIGHT_CHANGE -> "体重变化";
            case STOOL_COLOR -> "大便颜色";
            case EXAM_HISTORY -> "检查史";
            case PAIN_MIGRATION -> "疼痛转移";
            case PAIN_LOCATION -> "疼痛位置";
            case REBOUND_TENDERNESS -> "反跳痛";
            case APPETITE -> "食欲";
            case ONSET_TIMING -> "发作时机";
            case CHEST_TIGHTNESS -> "是否胸闷";
            case DIET_HABITS -> "饮食习惯";

            // 呼吸系统相关槽位
            case NASAL_DISCHARGE_COLOR -> "鼻涕颜色";
            case THROAT_PAIN -> "咽痛";
            case BODY_ACHE -> "全身酸痛";
            case CONTACT_HISTORY -> "接触史";
            case THROAT_APPEARANCE -> "咽喉外观";
            case SWALLOWING_PAIN -> "吞咽痛";
            case NECK_SWELLING -> "颈部肿胀";
            case RECURRENCE_HISTORY -> "复发史";
            case COUGH_CHARACTER -> "咳嗽性质";
            case SPUTUM_COLOR -> "痰液颜色";
            case SMOKING_HISTORY -> "吸烟史";
            case NIGHT_COUGH -> "夜间咳嗽";
            case SEASONALITY -> "季节性";
            case NASAL_SYMPTOMS -> "鼻部症状";
            case EYE_SYMPTOMS -> "眼部症状";
            case TRIGGER_FACTORS -> "诱发因素";
            case MEDICATION_HISTORY -> "用药史";
        };
    }
}
