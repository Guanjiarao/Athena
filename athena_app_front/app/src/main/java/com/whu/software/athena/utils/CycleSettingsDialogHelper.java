package com.whu.software.athena.utils;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Switch;
import android.widget.TextView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.whu.software.athena.R;

/**
 * Bottom sheet for editing cycle settings.
 */
public final class CycleSettingsDialogHelper {

    private CycleSettingsDialogHelper() {
    }

    public interface OnCycleSettingsSavedListener {
        void onSaved(int periodLengthDays, int cycleLengthDays, boolean irregular);
    }

    private static final int MIN_PERIOD_DAYS = 2;
    private static final int MAX_PERIOD_DAYS = 14;
    private static final int MIN_CYCLE_DAYS = 20;
    private static final int MAX_CYCLE_DAYS = 45;

    public static void show(Context context, OnCycleSettingsSavedListener listener) {
        BottomSheetDialog dialog = new BottomSheetDialog(context);
        View contentView = LayoutInflater.from(context)
                .inflate(R.layout.dialog_bottom_cycle_settings, null);
        dialog.setContentView(contentView);

        TextView btnCancel = contentView.findViewById(R.id.btn_cancel);
        TextView btnSave = contentView.findViewById(R.id.btn_save);

        TextView tvPeriodValue = contentView.findViewById(R.id.tv_period_value);
        TextView btnPeriodMinus = contentView.findViewById(R.id.btn_period_minus);
        TextView btnPeriodPlus = contentView.findViewById(R.id.btn_period_plus);

        View rowCycleLength = contentView.findViewById(R.id.row_cycle_length);
        TextView tvCycleValue = contentView.findViewById(R.id.tv_cycle_value);
        TextView btnCycleMinus = contentView.findViewById(R.id.btn_cycle_minus);
        TextView btnCyclePlus = contentView.findViewById(R.id.btn_cycle_plus);

        Switch switchIrregular = contentView.findViewById(R.id.switch_irregular);

        final int[] periodDays = {CycleDataManager.getPeriodDays(context)};
        final int[] cycleDays = {CycleDataManager.getCycleDays(context)};
        final boolean[] irregular = {CycleDataManager.isIrregular(context)};

        tvPeriodValue.setText(periodDays[0] + " 天");
        tvCycleValue.setText(cycleDays[0] + " 天");
        switchIrregular.setChecked(irregular[0]);
        applyIrregularUi(rowCycleLength, btnCycleMinus, btnCyclePlus, irregular[0]);

        btnPeriodMinus.setOnClickListener(v -> {
            if (periodDays[0] > MIN_PERIOD_DAYS) {
                periodDays[0]--;
                tvPeriodValue.setText(periodDays[0] + " 天");
            }
        });
        btnPeriodPlus.setOnClickListener(v -> {
            if (periodDays[0] < MAX_PERIOD_DAYS) {
                periodDays[0]++;
                tvPeriodValue.setText(periodDays[0] + " 天");
            }
        });

        btnCycleMinus.setOnClickListener(v -> {
            if (cycleDays[0] > MIN_CYCLE_DAYS) {
                cycleDays[0]--;
                tvCycleValue.setText(cycleDays[0] + " 天");
            }
        });
        btnCyclePlus.setOnClickListener(v -> {
            if (cycleDays[0] < MAX_CYCLE_DAYS) {
                cycleDays[0]++;
                tvCycleValue.setText(cycleDays[0] + " 天");
            }
        });

        switchIrregular.setOnCheckedChangeListener((buttonView, isChecked) -> {
            irregular[0] = isChecked;
            applyIrregularUi(rowCycleLength, btnCycleMinus, btnCyclePlus, isChecked);
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnSave.setOnClickListener(v -> {
            CycleDataManager.saveSettings(context, periodDays[0], cycleDays[0], irregular[0]);
            HealthSyncManager.markInsightDirty(context);
            if (listener != null) {
                listener.onSaved(periodDays[0], cycleDays[0], irregular[0]);
            }
            dialog.dismiss();
        });

        dialog.show();
    }

    private static void applyIrregularUi(View rowCycleLength,
                                         TextView btnCycleMinus,
                                         TextView btnCyclePlus,
                                         boolean irregular) {
        rowCycleLength.setAlpha(irregular ? 0.4f : 1.0f);
        btnCycleMinus.setEnabled(!irregular);
        btnCyclePlus.setEnabled(!irregular);
    }
}
