package com.whu.software.athena;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

/** Applies the article-reading bottom-menu visual language to priority record rows. */
final class RecordActionReadingStyle {
    private static final int ARTICLE_ICON_PURPLE = Color.rgb(105, 82, 200);
    private static final int ARTICLE_TEXT = Color.rgb(47, 43, 50);

    private RecordActionReadingStyle() {}

    static void apply(@NonNull View row) {
        Context context = row.getContext();
        row.setBackgroundResource(R.drawable.bg_article_action_glass);
        row.setElevation(dp(context, 2));

        ViewGroup.LayoutParams rawParams = row.getLayoutParams();
        if (rawParams instanceof LinearLayout.LayoutParams) {
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) rawParams;
            params.setMargins(dp(context, 8), dp(context, 4), dp(context, 8), dp(context, 4));
            row.setLayoutParams(params);
        }

        ImageView icon = row.findViewById(R.id.action_icon);
        if (icon != null) {
            icon.setBackground(null);
            icon.setPadding(dp(context, 6), dp(context, 6), dp(context, 6), dp(context, 6));
            icon.setColorFilter(ARTICLE_ICON_PURPLE, PorterDuff.Mode.SRC_IN);
        }

        Typeface readingTypeface = Typeface.create("sans-serif-medium", Typeface.NORMAL);
        applyTypeface(row, readingTypeface);

        TextView title = row.findViewById(R.id.action_title);
        if (title != null) {
            title.setTextColor(ARTICLE_TEXT);
            title.setTextSize(13f);
            title.setTypeface(readingTypeface);
        }

        View divider = row.findViewById(R.id.action_divider);
        if (divider != null) divider.setVisibility(View.GONE);
    }

    private static void applyTypeface(@NonNull View view, @NonNull Typeface typeface) {
        if (view instanceof TextView) {
            ((TextView) view).setTypeface(typeface);
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                applyTypeface(group.getChildAt(i), typeface);
            }
        }
    }

    private static int dp(@NonNull Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
