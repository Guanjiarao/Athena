package com.whu.software.athena.utils;

import android.content.Context;
import android.graphics.Color;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TimePicker;
import android.widget.Toast;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.whu.software.athena.R;

/**
 * 爱爱记录底部弹窗工具类。
 *
 * <p>复用于经期（PeriodFragment）、备孕（PregnancyPrepFragment）、
 * 怀孕（PregnancyFragment）三个模块的"爱爱"列表项。
 */
public final class SexRecordDialogHelper {

    /** 用户确认后回调：措施文案 + 时间文案（如 13:25） */
    public interface OnRecordSavedListener {
        void onSaved(String measure, String time);
    }

    /** 当前选中的措施在图标列表中的下标，-1 = 未选 */
    private static int selectedMeasureIndex = -1;

    private SexRecordDialogHelper() {}

    private static final int[] ICON_IDS = {
            R.id.ic_measure_none,
            R.id.ic_measure_condom,
            R.id.ic_measure_external,
            R.id.ic_measure_no_ejac,
            R.id.ic_measure_emergency,
            R.id.ic_measure_short_pill,
            R.id.ic_measure_long_pill,
            R.id.ic_measure_iud,
            R.id.ic_measure_other,
    };

    private static final String[] MEASURE_NAMES = {
            "无措施", "避孕套", "体外排精", "未射精", "紧急避孕药",
            "短效避孕药", "长效避孕药", "节育环", "其他措施"
    };

    /**
     * 弹出爱爱记录弹窗（无保存回调，仅 Toast）。
     */
    public static void show(Context context) {
        show(context, null);
    }

    /**
     * 弹出爱爱记录弹窗。
     *
     * @param listener 确定后回调；为 null 时行为与旧版一致（仅 Toast）
     */
    public static void show(Context context, OnRecordSavedListener listener) {
        BottomSheetDialog dialog = new BottomSheetDialog(context);

        View contentView = LayoutInflater.from(context)
                .inflate(R.layout.dialog_bottom_sex_record, null);
        dialog.setContentView(contentView);

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(
                    new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        }

        selectedMeasureIndex = -1;

        ImageView[] icons = new ImageView[ICON_IDS.length];
        for (int i = 0; i < ICON_IDS.length; i++) {
            icons[i] = contentView.findViewById(ICON_IDS[i]);
            final int idx = i;
            icons[i].setOnClickListener(v -> {
                if (selectedMeasureIndex >= 0 && selectedMeasureIndex < icons.length) {
                    icons[selectedMeasureIndex].setBackgroundResource(R.drawable.bg_measure_icon);
                }
                selectedMeasureIndex = idx;
                icons[idx].setBackgroundResource(R.drawable.bg_measure_icon_selected);
            });
        }

        TimePicker timePicker = contentView.findViewById(R.id.time_picker);
        timePicker.setIs24HourView(true);

        contentView.findViewById(R.id.btn_cancel).setOnClickListener(v -> dialog.dismiss());

        contentView.findViewById(R.id.btn_confirm).setOnClickListener(v -> {
            if (selectedMeasureIndex < 0 || selectedMeasureIndex >= MEASURE_NAMES.length) {
                Toast.makeText(context, "请选择措施", Toast.LENGTH_SHORT).show();
                return;
            }
            String measure = MEASURE_NAMES[selectedMeasureIndex];

            int hour   = timePicker.getHour();
            int minute = timePicker.getMinute();
            String timeStr = String.format(java.util.Locale.US, "%02d:%02d", hour, minute);
            if (TextUtils.isEmpty(timeStr)) {
                Toast.makeText(context, "请选择时间", Toast.LENGTH_SHORT).show();
                return;
            }

            if (listener != null) {
                listener.onSaved(measure, timeStr);
            } else {
                Toast.makeText(context,
                        "已记录：" + measure + "  " + timeStr,
                        Toast.LENGTH_SHORT).show();
            }
            dialog.dismiss();
        });

        dialog.show();
    }
}
