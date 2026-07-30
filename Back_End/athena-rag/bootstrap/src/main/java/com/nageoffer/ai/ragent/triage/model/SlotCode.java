

package com.nageoffer.ai.ragent.triage.model;

/**
 * Supported triage slot codes for the refactored dual-agent flow.
 */
public enum SlotCode {

    PRIMARY_SYMPTOM,

    SYMPTOM,

    DURATION,

    ONSET_TIME,

    BODY_PART,

    PAIN_CHARACTER,

    PAIN_SEVERITY,

    AGGRAVATING_FACTORS,

    RELIEVING_FACTORS,

    FEVER_PRESENCE,

    TEMPERATURE,

    NAUSEA_PRESENCE,

    VOMITING_PRESENCE,

    DYSPNEA_PRESENCE,

    BLEEDING_PRESENCE,

    PREGNANCY_STATUS,

    SEIZURE_PRESENCE,

    DIARRHEA_PRESENCE,

    STOOL_CHARACTER,

    DIAGNOSIS_HISTORY,

    ASSOCIATED_SYMPTOMS,

    AGE,

    COUGH_PRESENCE,

    SPUTUM_CHARACTER,

    ALLERGY_HISTORY,

    FEVER_TEMPERATURE,

    // 消化系统相关槽位
    DIARRHEA_FREQUENCY,      // 腹泻次数
    FOOD_HISTORY,            // 饮食史
    PAIN_TIMING,             // 疼痛时机（饭前/饭后）
    ACID_REFLUX,             // 反酸
    WEIGHT_CHANGE,           // 体重变化
    STOOL_COLOR,             // 大便颜色
    EXAM_HISTORY,            // 检查史
    PAIN_MIGRATION,          // 疼痛转移
    PAIN_LOCATION,           // 疼痛位置
    REBOUND_TENDERNESS,      // 反跳痛
    APPETITE,                // 食欲
    ONSET_TIMING,            // 发作时机
    CHEST_TIGHTNESS,         // 胸闷
    DIET_HABITS,             // 饮食习惯

    // 呼吸系统相关槽位
    NASAL_DISCHARGE_COLOR,   // 鼻涕颜色
    THROAT_PAIN,             // 咽痛
    BODY_ACHE,               // 全身酸痛
    CONTACT_HISTORY,         // 接触史
    THROAT_APPEARANCE,       // 咽喉外观
    SWALLOWING_PAIN,         // 吞咽痛
    NECK_SWELLING,           // 颈部肿胀
    RECURRENCE_HISTORY,      // 复发史
    COUGH_CHARACTER,         // 咳嗽性质（干咳/有痰）
    SPUTUM_COLOR,            // 痰液颜色
    SMOKING_HISTORY,         // 吸烟史
    NIGHT_COUGH,             // 夜间咳嗽
    SEASONALITY,             // 季节性
    NASAL_SYMPTOMS,          // 鼻部症状
    EYE_SYMPTOMS,            // 眼部症状
    TRIGGER_FACTORS,         // 诱发因素
    MEDICATION_HISTORY       // 用药史
}
