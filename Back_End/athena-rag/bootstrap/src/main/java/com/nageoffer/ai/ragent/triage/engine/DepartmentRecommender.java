

package com.nageoffer.ai.ragent.triage.engine;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.nageoffer.ai.ragent.triage.model.RiskLevel;
import com.nageoffer.ai.ragent.triage.model.SlotCode;
import com.nageoffer.ai.ragent.triage.model.SlotState;
import com.nageoffer.ai.ragent.triage.model.SlotValue;
import com.nageoffer.ai.ragent.triage.model.Symptom;
import com.nageoffer.ai.ragent.triage.model.TriageContext;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 科室推荐引擎 - 基于症状和风险等级推荐就诊科室
 */
public class DepartmentRecommender {

    @Data
    @AllArgsConstructor
    public static class DepartmentRecommendation {
        private String department;
        private String reason;
    }

    /**
     * 推荐就诊科室
     */
    public DepartmentRecommendation recommend(TriageContext context) {
        if (context == null) {
            return new DepartmentRecommendation("全科/内科", "信息不足，建议全科初步评估");
        }

        // 获取风险等级
        int riskLevel = 0;
        List<String> riskHints = new ArrayList<>();
        if (context.getRiskAssessment() != null) {
            riskLevel = context.getRiskAssessment().getLevel();
            if (context.getRiskAssessment().getRiskHints() != null) {
                riskHints = context.getRiskAssessment().getRiskHints();
            }
        }

        // 高风险或危急症状 -> 急诊科
        if (riskLevel >= 3 || hasEmergencySignals(riskHints)) {
            return new DepartmentRecommendation("急诊科", "存在高风险或危急症状，建议急诊就诊");
        }

        // 提取主要症状
        List<String> symptoms = extractSymptomNames(context);
        String primarySymptom = symptoms.isEmpty() ? "" : symptoms.get(0).toLowerCase();

        // 获取槽位信息
        SlotState slotState = context.getSlotState();
        String ageGroup = getSlotValue(slotState, "AGE_GROUP");
        String gender = getSlotValue(slotState, "GENDER");

        // 儿童患者 -> 儿科
        if ("儿童".equals(ageGroup) || "婴幼儿".equals(ageGroup)) {
            return new DepartmentRecommendation("儿科", "患者为儿童，建议儿科就诊");
        }

        // 基于症状推荐科室
        DepartmentRecommendation recommendation = recommendBySymptoms(symptoms, primarySymptom, gender, riskLevel);
        if (recommendation != null) {
            return recommendation;
        }

        // 默认推荐
        if (riskLevel >= 2) {
            return new DepartmentRecommendation("内科", "症状需要进一步评估，建议内科就诊");
        }

        return new DepartmentRecommendation("全科/内科", "症状不明确，建议全科初步评估");
    }

    private boolean hasEmergencySignals(List<String> riskHints) {
        if (CollUtil.isEmpty(riskHints)) {
            return false;
        }
        // 危急信号
        return riskHints.contains("SEIZURE")
            || riskHints.contains("DYSPNEA")
            || riskHints.contains("CHEST_PAIN_WITH_DYSPNEA")
            || riskHints.contains("SEVERE_BLEEDING")
            || riskHints.contains("ALTERED_CONSCIOUSNESS");
    }

    private List<String> extractSymptomNames(TriageContext context) {
        List<String> names = new ArrayList<>();
        if (CollUtil.isNotEmpty(context.getExtractedSymptoms())) {
            for (Symptom symptom : context.getExtractedSymptoms()) {
                if (StrUtil.isNotBlank(symptom.getName())) {
                    names.add(symptom.getName().trim());
                }
            }
        }
        return names;
    }

    private String getSlotValue(SlotState slotState, String slotCodeStr) {
        if (slotState == null || slotState.getSlots() == null) {
            return null;
        }
        try {
            SlotCode slotCode = SlotCode.valueOf(slotCodeStr);
            SlotValue slotValue = slotState.get(slotCode);
            return slotValue != null ? slotValue.getValue() : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private DepartmentRecommendation recommendBySymptoms(List<String> symptoms, String primarySymptom, String gender, int riskLevel) {
        if (symptoms.isEmpty()) {
            return null;
        }

        // 消化系统症状
        if (containsAny(symptoms, "腹痛", "腹泻", "呕吐", "恶心", "便血", "黑便", "腹胀", "消化不良")) {
            if (riskLevel >= 2) {
                return new DepartmentRecommendation("消化内科", "消化系统症状明显，建议消化内科就诊");
            }
            return new DepartmentRecommendation("消化内科", "消化系统症状，建议消化内科评估");
        }

        // 呼吸系统症状
        if (containsAny(symptoms, "咳嗽", "气喘", "呼吸困难", "胸闷", "咳痰", "喘息", "气短")) {
            if (containsAny(symptoms, "呼吸困难", "气短") && riskLevel >= 2) {
                return new DepartmentRecommendation("急诊科", "呼吸困难症状，建议急诊评估");
            }
            return new DepartmentRecommendation("呼吸内科", "呼吸系统症状，建议呼吸内科就诊");
        }

        // 心血管症状
        if (containsAny(symptoms, "胸痛", "心悸", "心慌", "胸闷")) {
            if (containsAny(symptoms, "胸痛") && riskLevel >= 2) {
                return new DepartmentRecommendation("急诊科", "胸痛症状需紧急评估");
            }
            return new DepartmentRecommendation("心内科", "心血管症状，建议心内科就诊");
        }

        // 神经系统症状
        if (containsAny(symptoms, "头痛", "头晕", "眩晕", "抽搐", "意识障碍", "肢体无力", "麻木")) {
            if (containsAny(symptoms, "抽搐", "意识障碍") || riskLevel >= 3) {
                return new DepartmentRecommendation("急诊科", "神经系统危急症状，建议急诊就诊");
            }
            return new DepartmentRecommendation("神经内科", "神经系统症状，建议神经内科就诊");
        }

        // 骨科/运动系统症状
        if (containsAny(symptoms, "关节痛", "骨痛", "扭伤", "骨折", "腰痛", "颈痛", "肩痛", "膝痛")) {
            return new DepartmentRecommendation("骨科", "骨骼肌肉症状，建议骨科就诊");
        }

        // 皮肤症状
        if (containsAny(symptoms, "皮疹", "瘙痒", "红肿", "皮肤病变", "荨麻疹", "湿疹")) {
            return new DepartmentRecommendation("皮肤科", "皮肤症状，建议皮肤科就诊");
        }

        // 眼科症状
        if (containsAny(symptoms, "视力下降", "眼痛", "眼红", "眼部不适", "视物模糊", "眼干")) {
            return new DepartmentRecommendation("眼科", "眼部症状，建议眼科就诊");
        }

        // 耳鼻喉症状
        if (containsAny(symptoms, "耳痛", "鼻塞", "咽痛", "喉咙痛", "耳鸣", "听力下降", "流鼻涕")) {
            return new DepartmentRecommendation("耳鼻喉科", "耳鼻喉症状，建议耳鼻喉科就诊");
        }

        // 泌尿系统症状
        if (containsAny(symptoms, "尿频", "尿痛", "血尿", "排尿困难", "尿急", "尿失禁")) {
            return new DepartmentRecommendation("泌尿外科", "泌尿系统症状，建议泌尿外科就诊");
        }

        // 妇科症状
        if ("女".equals(gender) && containsAny(symptoms, "月经异常", "阴道出血", "下腹痛", "白带异常", "孕期不适")) {
            if (containsAny(symptoms, "阴道出血", "孕期不适") && riskLevel >= 2) {
                return new DepartmentRecommendation("妇产科", "妇科症状需及时评估，建议妇产科就诊");
            }
            return new DepartmentRecommendation("妇产科", "妇科症状，建议妇产科就诊");
        }

        // 发热症状
        if (containsAny(symptoms, "发热", "发烧", "高热")) {
            if (riskLevel >= 2) {
                return new DepartmentRecommendation("急诊科", "发热伴其他症状，建议急诊评估");
            }
            return new DepartmentRecommendation("内科", "发热症状，建议内科就诊");
        }

        return null;
    }

    private boolean containsAny(List<String> symptoms, String... keywords) {
        for (String symptom : symptoms) {
            String lower = symptom.toLowerCase();
            for (String keyword : keywords) {
                if (lower.contains(keyword.toLowerCase())) {
                    return true;
                }
            }
        }
        return false;
    }
}
