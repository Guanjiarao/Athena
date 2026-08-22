package com.whu.software.athena.features.privacy;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * RLHFMemory — In-Context RLHF 对齐记忆读取工具。
 *
 * 从 SharedPreferences 读取用户历史矫正语料，构建注入给 LLM 的 System 级对齐指令。
 * 写入端由 {@link RLHFDialogHelper} 负责。
 */
public final class RLHFMemory {

    /** 每条矫正语料在注入 Prompt 中的前缀编号格式。 */
    private static final String BULLET = "• ";

    private RLHFMemory() {}

    /**
     * 读取最近 N 条矫正语料，构建完整的 System 级对齐注入 Prompt。
     *
     * 若本地无任何矫正记录，返回 null（调用方应跳过注入）。
     *
     * @param context 任意 Context
     * @return 对齐注入 Prompt 字符串，或 null（无记录时）
     */
    @Nullable
    public static String buildAlignmentPrompt(@NonNull Context context) {
        SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences(ShapleyMathEngine.PREFS_NAME, Context.MODE_PRIVATE);

        String raw = prefs.getString(RLHFDialogHelper.KEY_RLHF_MEMORY, "");
        if (raw == null || raw.trim().isEmpty()) return null;

        String[] items = raw.split("\\|");
        if (items.length == 0) return null;

        StringBuilder rules = new StringBuilder();
        for (String item : items) {
            String trimmed = item.trim();
            if (!trimmed.isEmpty()) {
                rules.append(BULLET).append(trimmed).append("\n");
            }
        }
        if (rules.length() == 0) return null;

        return "[系统级价值观对齐指令：在生成回答时，请你严格遵循以下人类反馈的价值观矫正规则，" +
                "摒弃原有的刻板印象：\n" +
                rules +
                "必须用上述平权视角来回答接下来的问题！]";
    }

    /** 检查是否存在任何矫正记录（用于 UI 判断是否显示对齐徽章）。 */
    public static boolean hasMemory(@NonNull Context context) {
        SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences(ShapleyMathEngine.PREFS_NAME, Context.MODE_PRIVATE);
        String raw = prefs.getString(RLHFDialogHelper.KEY_RLHF_MEMORY, "");
        return raw != null && !raw.trim().isEmpty();
    }
}
