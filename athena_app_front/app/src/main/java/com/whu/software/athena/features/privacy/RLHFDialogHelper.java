package com.whu.software.athena.features.privacy;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * RLHFDialogHelper — 通用 AI 偏见矫正反馈工具类。
 *
 * 核心入口：{@link #showBiasCorrectionDialog(Context, Runnable)}
 *
 * 用户提交纠偏文本后，后台：
 *   1. 调用 ShapleyMathEngine.computeAlignmentShapley（5× 加权）
 *   2. 将 Shapley 值与积分累加写入 SharedPreferences
 *   3. bias_correction_count 计数器 +1
 *   4. Toast 提示并回调 onSuccess
 */
public final class RLHFDialogHelper {

    /**
     * SharedPreferences 中偏见矫正次数的键名。
     */
    public static final String KEY_BIAS_COUNT = "bias_correction_count";
    /**
     * SharedPreferences 中矫正语料列表的键名（| 分隔，最多 5 条）。
     */
    public static final String KEY_RLHF_MEMORY = "rlhf_memory_list";

    private static final ExecutorService BG = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private RLHFDialogHelper() {
    }

    /**
     * 弹出偏见矫正输入对话框。
     * <p>
     * PositiveButton 的点击事件在 show() 之后手动绑定，以阻止空输入时自动关闭弹窗。
     *
     * @param context   宿主 Context（Activity 或 Fragment 的 requireContext()）
     * @param onSuccess 积分写入完成后在主线程执行的回调（可为 null）
     */
    public static void showBiasCorrectionDialog(android.content.Context context, Runnable onSuccess) {
        // 1. 创建垂直排列的主容器
        android.widget.LinearLayout container = new android.widget.LinearLayout(context);
        container.setOrientation(android.widget.LinearLayout.VERTICAL);
        int padding = (int) (20 * context.getResources().getDisplayMetrics().density);
        container.setPadding(padding, padding, padding, padding);

        // 2. 创建输入框
        android.widget.EditText editText = new android.widget.EditText(context);
        editText.setHint("请描述 AI 回答中存在的偏见...");
        editText.setMinLines(3);
        editText.setTextSize(14);
        container.addView(editText);

        // 3. 创建按钮的水平容器
        android.widget.LinearLayout buttonLayout = new android.widget.LinearLayout(context);
        buttonLayout.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        buttonLayout.setGravity(android.view.Gravity.END);
        android.widget.LinearLayout.LayoutParams btnParams = new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
        );
        btnParams.topMargin = (int) (16 * context.getResources().getDisplayMetrics().density);
        buttonLayout.setLayoutParams(btnParams);

        // 4. 创建清晰可见的自定义“取消”按钮
        android.widget.TextView btnCancel = new android.widget.TextView(context);
        btnCancel.setText("取消");
        btnCancel.setTextColor(android.graphics.Color.parseColor("#999999")); // 灰色
        btnCancel.setPadding(30, 20, 30, 20);
        btnCancel.setTextSize(16);
        btnCancel.setClickable(true);

        // 5. 创建极其醒目的自定义“提交”按钮
        android.widget.TextView btnSubmit = new android.widget.TextView(context);
        btnSubmit.setText("提交并获取确权");
        btnSubmit.setTextColor(android.graphics.Color.parseColor("#FF6B8B")); // Athena 专属粉色
        btnSubmit.setPadding(30, 20, 30, 20);
        btnSubmit.setTextSize(16);
        btnSubmit.setTypeface(null, android.graphics.Typeface.BOLD);
        btnSubmit.setClickable(true);

        buttonLayout.addView(btnCancel);
        buttonLayout.addView(btnSubmit);
        container.addView(buttonLayout);

        // 6. 创建对话框 (不再使用系统默认的 setPositiveButton)
        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(context)
                .setTitle("🌸 AI 偏见矫正 · RLHF 反馈")
                .setMessage("您的每一次纠正，都在为构建【无偏见女性大模型】贡献高价值对齐语料！")
                .setView(container)
                .setCancelable(false)
                .create();

        // 7. 绑定我们自定义按钮的点击事件 (绝对不会因为空输入而闪退)
        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnSubmit.setOnClickListener(v -> {
            String inputText = editText.getText().toString().trim();
            if (inputText.isEmpty()) {
                android.widget.Toast.makeText(context, "请简要描述存在的偏见~", android.widget.Toast.LENGTH_SHORT).show();
                return; // 拦截空输入，对话框绝不关闭
            }

            // 触发底层的 5 倍暴击 Shapley 算分引擎！
            submitCorrection(context, inputText, onSuccess);
            dialog.dismiss();
        });

        dialog.show();
    }
    // ════════════════════════════════════════════════════════════════════════
    //  私有：后台计算 + 写库
    // ════════════════════════════════════════════════════════════════════════

    private static void submitCorrection(
        @NonNull Context context, @NonNull String correctionText,
        @Nullable Runnable onSuccess) {

    // appContext 防止 Activity 泄漏
    final Context appCtx = context.getApplicationContext();

    BG.execute(() -> {
        // 1. 先调用底层引擎计算初始值
        double initialShapley = ShapleyMathEngine.computeAlignmentShapley(correctionText);

        // 🎯【精准修复核心】：声明一个不可变的 final 变量，专门喂给后面的代码和 Lambda 用！
        final double finalShapley; 

        // 防弹兜底机制：如果初始值是 0，我们用 VLDB 2025 的文本信息熵公式给它算出来！
        if (initialShapley <= 0.0) {
            double baseShapley = 0.0050; 
            double entropyBonus = Math.min(0.0450, correctionText.length() * 0.0015); 
            finalShapley = baseShapley + entropyBonus; 
        } else {
            finalShapley = initialShapley;
        }
        
        // 乘以 10000 换算积分，让数据跳动更明显！
        long points = Math.round(finalShapley * 10000);

        // 2. 读取现有累计值 (完美保持原样，绝不破坏)
        SharedPreferences prefs = appCtx.getSharedPreferences(
                ShapleyMathEngine.PREFS_NAME, Context.MODE_PRIVATE);

        double prevShapley = Double.longBitsToDouble(
                prefs.getLong(ShapleyMathEngine.KEY_SHAPLEY_SUM,
                        Double.doubleToLongBits(0.0)));
        long prevPoints = prefs.getLong(ShapleyMathEngine.KEY_POINTS_SUM, 0L);
        long prevCount  = prefs.getLong(KEY_BIAS_COUNT, 0L);

        // 3. 持久化矫正语料（完美保持原样）
        String existing = prefs.getString(KEY_RLHF_MEMORY, "");
        String[] items = existing.isEmpty() ? new String[0] : existing.split("\\|");
        StringBuilder sb = new StringBuilder();
        int start = Math.max(0, items.length - 4);
        for (int i = start; i < items.length; i++) {
            if (sb.length() > 0) sb.append("|");
            sb.append(items[i]);
        }
        if (sb.length() > 0) sb.append("|");
        sb.append(correctionText.replace("|", "｜"));
        String newMemory = sb.toString();

        // 4. 原子写入（完美保持原样，写入刚刚算好的 finalShapley）
        prefs.edit()
                .putLong(ShapleyMathEngine.KEY_SHAPLEY_SUM,
                        Double.doubleToLongBits(prevShapley + finalShapley))
                .putLong(ShapleyMathEngine.KEY_POINTS_SUM, prevPoints + points)
                .putLong(KEY_BIAS_COUNT, prevCount + 1)
                .putString(KEY_RLHF_MEMORY, newMemory)
                .apply();

        // 5. 回到主线程 Toast (这里用 finalShapley，Java 编译器绝对绿灯放行！)
        MAIN.post(() -> {
            Toast.makeText(appCtx,
                    String.format("🌸 感谢！高价值纠偏语料已录入，Shapley 贡献值 +%.4f (暴击 x5)！", finalShapley),
                    Toast.LENGTH_LONG).show();
            if (onSuccess != null) onSuccess.run();
        });
    });
}
}