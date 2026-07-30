

package com.nageoffer.ai.ragent.triage.response;

import com.nageoffer.ai.ragent.triage.model.SlotCode;

/**
 * Response-package facade for clarification prompt templates.
 */
public final class TriageReplyPromptSupport {

    private TriageReplyPromptSupport() {
    }

    public static String promptForSlot(SlotCode slotCode) {
        if (slotCode == null) {
            return "请补充一下这个信息。";
        }
        return switch (slotCode) {
            case PRIMARY_SYMPTOM -> "您现在最主要的不舒服是什么？";
            case SYMPTOM -> "您还有哪些不舒服的症状？";
            case DURATION -> "这种不适持续多久了？";
            case ONSET_TIME -> "这种不适大概是什么时候开始的？";
            case BODY_PART, PAIN_LOCATION -> "具体是哪个部位不舒服？";
            case PAIN_CHARACTER -> "这种疼痛更像是刺痛、胀痛、绞痛，还是其他感觉？";
            case PAIN_SEVERITY -> "如果用 0 到 10 分表示严重程度，现在大概是几分？";
            case AGGRAVATING_FACTORS -> "什么情况下会加重？";
            case RELIEVING_FACTORS -> "有没有什么方式能缓解？";
            case FEVER_PRESENCE -> "有没有发热？";
            case TEMPERATURE, FEVER_TEMPERATURE -> "最高体温大概是多少？";
            case NAUSEA_PRESENCE -> "有没有恶心？";
            case VOMITING_PRESENCE -> "有没有呕吐？";
            case DYSPNEA_PRESENCE -> "有没有呼吸困难、气短或喘不上气？";
            case BLEEDING_PRESENCE -> "有没有出血或异常分泌物？";
            case PREGNANCY_STATUS -> "目前是否处于妊娠期或可能怀孕？";
            case SEIZURE_PRESENCE -> "有没有抽搐、意识不清或类似发作？";
            case DIARRHEA_PRESENCE -> "有没有腹泻？";
            case STOOL_CHARACTER -> "大便性状有没有变化，比如水样、黏液、黑便或带血？";
            case DIAGNOSIS_HISTORY -> "之前有没有相关诊断或类似病史？";
            case ASSOCIATED_SYMPTOMS -> "除了刚才说的症状，还有没有其他伴随不适？";
            case AGE -> "患者年龄是多少？";
            case COUGH_PRESENCE -> "有没有咳嗽？";
            case SPUTUM_CHARACTER -> "痰液是什么颜色和性状？";
            case ALLERGY_HISTORY -> "有没有药物或食物过敏史？";
            case DIARRHEA_FREQUENCY -> "一天大概腹泻几次？";
            case FOOD_HISTORY -> "最近有没有吃不洁食物、生冷食物或特殊饮食？";
            case PAIN_TIMING, ONSET_TIMING -> "这种不适通常在什么时间或什么情况下出现？";
            case ACID_REFLUX -> "有没有反酸、烧心？";
            case WEIGHT_CHANGE -> "最近体重有没有明显变化？";
            case STOOL_COLOR -> "大便颜色有没有变黑、变红或其他异常？";
            case EXAM_HISTORY -> "近期有没有做过相关检查？";
            case PAIN_MIGRATION -> "疼痛位置有没有转移？";
            case REBOUND_TENDERNESS -> "按压后松手时疼痛会不会明显加重？";
            case APPETITE -> "食欲有没有明显变化？";
            case CHEST_TIGHTNESS -> "有没有胸闷？";
            case DIET_HABITS -> "平时饮食习惯有什么特点？";
            case NASAL_DISCHARGE_COLOR -> "鼻涕是什么颜色？";
            case THROAT_PAIN -> "有没有咽痛？";
            case BODY_ACHE -> "有没有全身酸痛？";
            case CONTACT_HISTORY -> "近期有没有接触类似症状的人或相关暴露史？";
            case THROAT_APPEARANCE -> "咽喉有没有红肿、化脓或白点？";
            case SWALLOWING_PAIN -> "吞咽时会不会疼？";
            case NECK_SWELLING -> "颈部有没有肿胀或包块？";
            case RECURRENCE_HISTORY -> "以前是否反复出现过类似情况？";
            default -> "请补充一下" + slotCode.name() + "相关信息。";
        };
    }

    public static String promptForField(String field) {
        SlotCode slotCode = mapFieldToSlot(field);
        if (slotCode != null) {
            return promptForSlot(slotCode);
        }
        if (field == null || field.isBlank()) {
            return "请补充一下这个信息。";
        }
        return "请补充一下" + field + "。";
    }

    public static SlotCode mapFieldToSlot(String field) {
        if (field == null || field.isBlank()) {
            return null;
        }
        return switch (field.trim()) {
            case "主诉症状", "主要症状" -> SlotCode.PRIMARY_SYMPTOM;
            case "症状" -> SlotCode.SYMPTOM;
            case "持续时间" -> SlotCode.DURATION;
            case "发病时间" -> SlotCode.ONSET_TIME;
            case "疼痛部位", "部位" -> SlotCode.BODY_PART;
            case "疼痛性质" -> SlotCode.PAIN_CHARACTER;
            case "疼痛程度" -> SlotCode.PAIN_SEVERITY;
            case "加重因素" -> SlotCode.AGGRAVATING_FACTORS;
            case "缓解因素" -> SlotCode.RELIEVING_FACTORS;
            case "是否伴随发热" -> SlotCode.FEVER_PRESENCE;
            case "体温" -> SlotCode.TEMPERATURE;
            case "是否伴随恶心" -> SlotCode.NAUSEA_PRESENCE;
            case "是否伴随呕吐" -> SlotCode.VOMITING_PRESENCE;
            case "是否伴随呼吸困难" -> SlotCode.DYSPNEA_PRESENCE;
            case "是否伴随出血" -> SlotCode.BLEEDING_PRESENCE;
            case "是否妊娠" -> SlotCode.PREGNANCY_STATUS;
            case "是否存在抽搐" -> SlotCode.SEIZURE_PRESENCE;
            case "是否伴随腹泻" -> SlotCode.DIARRHEA_PRESENCE;
            case "大便性状" -> SlotCode.STOOL_CHARACTER;
            case "诊断史" -> SlotCode.DIAGNOSIS_HISTORY;
            case "伴随症状" -> SlotCode.ASSOCIATED_SYMPTOMS;
            case "年龄" -> SlotCode.AGE;
            case "是否咳嗽" -> SlotCode.COUGH_PRESENCE;
            case "痰液性状" -> SlotCode.SPUTUM_CHARACTER;
            case "过敏史" -> SlotCode.ALLERGY_HISTORY;
            default -> null;
        };
    }
}
