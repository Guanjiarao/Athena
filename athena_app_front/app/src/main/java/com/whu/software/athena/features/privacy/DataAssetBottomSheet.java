package com.whu.software.athena.features.privacy;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.whu.software.athena.R;

import java.util.Locale;

/**
 * DataAssetBottomSheet — 数据资产展示底部弹窗。
 *
 * 从 SharedPreferences 读取：
 *   - 累计 Shapley 贡献值（shapley_cumulative）
 *   - Athena AI 联邦健康积分（athena_points_cumulative）
 *   - 偏见矫正次数（bias_correction_count）
 * 并以高定感 UI 呈现。
 */
public class DataAssetBottomSheet extends BottomSheetDialogFragment {

    public static DataAssetBottomSheet newInstance() {
        return new DataAssetBottomSheet();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_data_asset, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        SharedPreferences prefs = requireContext()
                .getSharedPreferences(ShapleyMathEngine.PREFS_NAME, Context.MODE_PRIVATE);

        double shapleySum  = Double.longBitsToDouble(
                prefs.getLong(ShapleyMathEngine.KEY_SHAPLEY_SUM, Double.doubleToLongBits(0.0)));
        long   pointsSum   = prefs.getLong(ShapleyMathEngine.KEY_POINTS_SUM, 0L);
        long   biasCount   = prefs.getLong(RLHFDialogHelper.KEY_BIAS_COUNT, 0L);

        view.<TextView>findViewById(R.id.tv_shapley_value)
                .setText(String.format(Locale.US, "%.4f", shapleySum));
        view.<TextView>findViewById(R.id.tv_athena_points)
                .setText(String.valueOf(pointsSum));

        // 平权荣誉横幅：仅在有至少一次矫正记录时显示
        CardView honorBanner = view.findViewById(R.id.card_honor_banner);
        if (biasCount > 0) {
            honorBanner.setVisibility(View.VISIBLE);
            String honorText = String.format(Locale.CHINESE,
                    "平权贡献：您已累计参与 %d 次 AI 偏见矫正，为构建『无偏见女性大模型』贡献了珍贵的对齐语料！",
                    biasCount);
            view.<TextView>findViewById(R.id.tv_honor_text).setText(honorText);
        }
    }
}
