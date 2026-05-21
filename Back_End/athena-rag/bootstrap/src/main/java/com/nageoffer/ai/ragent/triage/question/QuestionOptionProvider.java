

package com.nageoffer.ai.ragent.triage.question;

import com.nageoffer.ai.ragent.triage.controller.vo.TriageClarificationData;
import com.nageoffer.ai.ragent.triage.model.SlotCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 选项生成器，为不同槽位生成合适的选项列表。
 */
@Slf4j
@Component
public class QuestionOptionProvider {

    /**
     * 为指定槽位生成选项列表
     *
     * @param slot 槽位代码
     * @return 选项列表
     */
    public List<TriageClarificationData.QuestionOption> generateOptionsForSlot(SlotCode slot) {
        if (slot == null) {
            log.warn("[QuestionOptionProvider] 槽位为空，返回空选项列表");
            return List.of();
        }

        log.debug("[QuestionOptionProvider] 为槽位 {} 生成选项", slot);

        return switch (slot) {
            case PAIN_SEVERITY -> createOptions(
                    option("轻微", "mild", slot),
                    option("中等", "moderate", slot),
                    option("严重", "severe", slot),
                    option("其他", "other", slot)
            );

            case FEVER_PRESENCE -> createOptions(
                    option("有发热", "yes", slot),
                    option("没有发热", "no", slot),
                    option("不确定", "uncertain", slot),
                    option("其他", "other", slot)
            );

            case DURATION -> createOptions(
                    option("几小时内", "hours", slot),
                    option("1-2天", "1-2days", slot),
                    option("3-7天", "3-7days", slot),
                    option("超过一周", "over_week", slot),
                    option("其他", "other", slot)
            );

            case NAUSEA_PRESENCE, VOMITING_PRESENCE, DYSPNEA_PRESENCE,
                 BLEEDING_PRESENCE, SEIZURE_PRESENCE, DIARRHEA_PRESENCE,
                 COUGH_PRESENCE -> createOptions(
                    option("有", "yes", slot),
                    option("没有", "no", slot),
                    option("不确定", "uncertain", slot),
                    option("其他", "other", slot)
            );

            case PREGNANCY_STATUS -> createOptions(
                    option("已怀孕", "pregnant", slot),
                    option("未怀孕", "not_pregnant", slot),
                    option("不确定", "uncertain", slot),
                    option("其他", "other", slot)
            );

            case BODY_PART -> createOptions(
                    option("头部", "head", slot),
                    option("胸部", "chest", slot),
                    option("腹部", "abdomen", slot),
                    option("四肢", "limbs", slot),
                    option("其他", "other", slot)
            );

            case PAIN_CHARACTER -> createOptions(
                    option("刺痛", "sharp", slot),
                    option("钝痛", "dull", slot),
                    option("绞痛", "cramping", slot),
                    option("胀痛", "distending", slot),
                    option("其他", "other", slot)
            );

            case STOOL_CHARACTER -> createOptions(
                    option("水样便", "watery", slot),
                    option("稀便", "loose", slot),
                    option("血便", "bloody", slot),
                    option("黑便", "black", slot),
                    option("其他", "other", slot)
            );

            case SPUTUM_CHARACTER -> createOptions(
                    option("白色", "white", slot),
                    option("黄色", "yellow", slot),
                    option("绿色", "green", slot),
                    option("带血", "bloody", slot),
                    option("其他", "other", slot)
            );

            case TEMPERATURE -> createOptions(
                    option("37-38℃", "low_fever", slot),
                    option("38-39℃", "moderate_fever", slot),
                    option("39℃以上", "high_fever", slot),
                    option("不确定", "uncertain", slot),
                    option("其他", "other", slot)
            );

            case FEVER_TEMPERATURE -> createOptions(
                    option("37-38℃", "low_fever", slot),
                    option("38-39℃", "moderate_fever", slot),
                    option("39℃以上", "high_fever", slot),
                    option("不确定", "uncertain", slot),
                    option("其他", "other", slot)
            );

            case ONSET_TIME -> createOptions(
                    option("突然发作", "sudden", slot),
                    option("逐渐加重", "gradual", slot),
                    option("间歇发作", "intermittent", slot),
                    option("其他", "other", slot)
            );

            case AGE -> createOptions(
                    option("0-3岁", "infant", slot),
                    option("4-12岁", "child", slot),
                    option("13-18岁", "adolescent", slot),
                    option("19-60岁", "adult", slot),
                    option("60岁以上", "elderly", slot),
                    option("其他", "other", slot)
            );

            case ASSOCIATED_SYMPTOMS -> createOptions(
                    option("麻木或无力", "numbness_weakness", slot),
                    option("放射到其他部位", "radiating", slot),
                    option("发热或寒战", "fever_chills", slot),
                    option("没有其他不适", "none", slot),
                    option("其他", "other", slot)
            );

            case AGGRAVATING_FACTORS -> createOptions(
                    option("活动后加重", "activity", slot),
                    option("按压加重", "pressing", slot),
                    option("夜间加重", "night", slot),
                    option("不明显", "none", slot),
                    option("其他", "other", slot)
            );

            case RELIEVING_FACTORS -> createOptions(
                    option("休息后缓解", "rest", slot),
                    option("热敷/冷敷缓解", "compress", slot),
                    option("用药后缓解", "medication", slot),
                    option("没有缓解", "none", slot),
                    option("其他", "other", slot)
            );

            case DIAGNOSIS_HISTORY -> createOptions(
                    option("有相关病史", "yes", slot),
                    option("没有相关病史", "no", slot),
                    option("以前类似发作", "similar_before", slot),
                    option("不确定", "uncertain", slot),
                    option("其他", "other", slot)
            );

            // 对于开放式问题，提供通用兜底选项
            case PRIMARY_SYMPTOM, SYMPTOM, ALLERGY_HISTORY -> createOptions(
                    option("其他", "other", slot)
            );

            default -> {
                log.debug("[OptionGenerator] 槽位 {} 使用默认兜底选项", slot);
                yield createOptions(option("其他", "other", slot));
            }
        };
    }

    /**
     * 创建选项对象
     */
    private TriageClarificationData.QuestionOption option(String label, String value, SlotCode targetSlot) {
        return TriageClarificationData.QuestionOption.builder()
                .label(label)
                .value(value)
                .targetSlot(targetSlot)
                .build();
    }

    /**
     * 创建选项列表
     */
    private List<TriageClarificationData.QuestionOption> createOptions(TriageClarificationData.QuestionOption... options) {
        return new ArrayList<>(List.of(options));
    }
}
