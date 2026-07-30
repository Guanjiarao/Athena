

package com.nageoffer.ai.ragent.triage.model;

/**
 * 就医助手编排器的下一步动作枚举。
 *
 * <p>该枚举是前后端之间最核心的控制信号之一：
 * 前端只需要关心当前动作是什么，而不需要知道后端内部到底调用了几个 Worker、
 * 使用了什么 Prompt、是否做过规则校验。</p>
 */
public enum TriageAction {

    /**
     * 信息还不够，必须先追问。
     */
    ASK_CLARIFICATION,

    /**
     * 发现高风险信号，必须优先阻断并给出就医警示。
     */
    TRIGGER_WARNING,

    /**
     * 信息已齐备且风险可控，可以进入病历摘要 / 分诊报告生成阶段。
     */
    GENERATE_REPORT
}
