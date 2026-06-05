package com.whu.software.athena.utils;

import android.content.Context;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.whu.software.athena.R;

import java.util.ArrayList;
import java.util.List;

/**
 * 健康记录列表「心情 / 好习惯」副区域：矢量图标 + Alpha 状态联动。
 */
public final class RecordActionExtraBinder {

    public static final float ALPHA_DIM = 0.3f;
    public static final float ALPHA_SELECTED = 1.0f;
    private static final float MOOD_SCALE_SELECTED = 1.2f;
    private static final int ANIM_MS = 160;

    public static final int[] HABIT_ICON_RES = {
            R.drawable.ic_habit_run,
            R.drawable.ic_habit_water,
            R.drawable.ic_habit_food,
            R.drawable.ic_habit_sleep,
            R.drawable.ic_habit_spa
    };

    private RecordActionExtraBinder() {}

    /**
     * 绑定 XML 中 {@link R.id#iv_mood_0} … {@link R.id#iv_mood_4} 的单选交互。
     */
    public static void bindMoodRow(View itemRoot) {
        int[] ids = {
                R.id.iv_mood_0, R.id.iv_mood_1, R.id.iv_mood_2,
                R.id.iv_mood_3, R.id.iv_mood_4
        };
        List<ImageView> icons = new ArrayList<>(5);
        for (int id : ids) {
            View v = itemRoot.findViewById(id);
            if (v instanceof ImageView) {
                ImageView iv = (ImageView) v;
                iv.setAlpha(ALPHA_DIM);
                iv.setScaleX(1f);
                iv.setScaleY(1f);
                icons.add(iv);
            }
        }
        if (icons.isEmpty()) {
            return;
        }
        for (ImageView iv : icons) {
            iv.setOnClickListener(v -> {
                for (ImageView o : icons) {
                    o.animate().alpha(ALPHA_DIM).scaleX(1f).scaleY(1f).setDuration(ANIM_MS).start();
                }
                iv.animate()
                        .alpha(ALPHA_SELECTED)
                        .scaleX(MOOD_SCALE_SELECTED)
                        .scaleY(MOOD_SCALE_SELECTED)
                        .setDuration(ANIM_MS)
                        .start();
            });
        }
    }

    /**
     * 好习惯：动态添加矢量 {@link ImageView}，多选 Toggle Alpha。
     */
    public static void bindHabitRow(LinearLayout extra, Context context) {
        extra.removeAllViews();
        float d = context.getResources().getDisplayMetrics().density;
        int size = (int) (28 * d);
        int margin = (int) (4 * d);
        List<ImageView> icons = new ArrayList<>();
        for (int res : HABIT_ICON_RES) {
            ImageView iv = new ImageView(context);
            iv.setImageResource(res);
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            iv.setContentDescription("好习惯");
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.setMarginStart(margin);
            iv.setLayoutParams(lp);
            iv.setAlpha(ALPHA_DIM);
            extra.addView(iv);
            icons.add(iv);
        }
        for (ImageView iv : icons) {
            iv.setOnClickListener(v -> {
                float a = iv.getAlpha();
                boolean on = Math.abs(a - ALPHA_SELECTED) < 0.05f;
                float target = on ? ALPHA_DIM : ALPHA_SELECTED;
                iv.animate().alpha(target).setDuration(ANIM_MS).start();
            });
        }
    }
}
