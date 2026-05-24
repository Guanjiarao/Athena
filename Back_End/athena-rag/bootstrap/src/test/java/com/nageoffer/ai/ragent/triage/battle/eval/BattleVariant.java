

package com.nageoffer.ai.ragent.triage.battle.eval;

/**
 * 三版本 battle 评测对象。
 */
public enum BattleVariant {

    MULTI_AGENT("multi-agent", "现有多 Agent 架构"),
    PURE_PROMPT("pure-prompt", "纯 Prompt 基线"),
    PURE_SKILL("pure-skill", "纯 Skill 基线");

    private final String code;
    private final String displayName;

    BattleVariant(String code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }

    public String getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }
}
