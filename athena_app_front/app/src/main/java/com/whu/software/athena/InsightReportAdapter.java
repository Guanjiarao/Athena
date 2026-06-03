package com.whu.software.athena;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.flexbox.FlexboxLayout;
import com.whu.software.athena.config.ApiConfig;
import com.whu.software.athena.utils.InsightReportEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class InsightReportAdapter extends RecyclerView.Adapter<InsightReportAdapter.ReportViewHolder> {

    private final List<InsightReportEntity.ReadingSuggestion> items = new ArrayList<>();

    public void submitList(@NonNull List<InsightReportEntity.ReadingSuggestion> data) {
        items.clear();
        items.addAll(data);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ReportViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_insight_report, parent, false);
        return new ReportViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ReportViewHolder holder, int position) {
        InsightReportEntity.ReadingSuggestion item = items.get(position);
        Context context = holder.itemView.getContext();

        String coverUrl = ApiConfig.MOCK_COVER_URL;
        Glide.with(context)
                .load(coverUrl)
                .placeholder(R.drawable.bg_category_cover)
                .error(R.drawable.bg_category_cover)
                .centerCrop()
                .into(holder.ivCover);

        holder.tvTitle.setText(safeText(item.title, "专属推荐内容"));
        holder.tvReason.setText("推荐理由：" + safeText(item.reason, "为你补充频道科普内容"));
        holder.tvScore.setText(String.format(Locale.CHINA, "匹配度 %.1f", item.score));

        holder.flexTopics.removeAllViews();
        if (item.topics != null && !item.topics.isEmpty()) {
            holder.flexTopics.setVisibility(View.VISIBLE);
            for (int i = 0; i < item.topics.size(); i++) {
                String topic = safeText(item.topics.get(i), "");
                if (TextUtils.isEmpty(topic)) {
                    continue;
                }
                holder.flexTopics.addView(createTopicChip(context, topic, i));
            }
        } else {
            holder.flexTopics.setVisibility(View.GONE);
        }

        holder.itemView.setOnClickListener(v -> {
            if (item.noteId <= 0L) {
                return;
            }
            Intent intent;
            if (isVideoType(item.type)) {
                intent = new Intent(v.getContext(), VideoDetailActivity.class);
                intent.putExtra("blog_id", String.valueOf(item.noteId));
                intent.putExtra("title", safeText(item.title, "专属推荐内容"));
                intent.putExtra("content_type", normalizeVideoType(item.type));
            } else {
                intent = new Intent(v.getContext(), ArticleDetailActivity.class);
                intent.putExtra("blog_id", String.valueOf(item.noteId));
                intent.putExtra("noteId", (int) Math.min(Integer.MAX_VALUE, item.noteId));
                intent.putExtra("title", safeText(item.title, "专属推荐内容"));
                intent.putExtra("type", item.type);
                intent.putExtra("article_type", item.type);
            }
            v.getContext().startActivity(intent);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @NonNull
    private TextView createTopicChip(@NonNull Context context, @NonNull String text, int index) {
        TextView chip = new TextView(context);
        chip.setText(text);
        chip.setTextColor(Color.parseColor("#7E4F42"));
        chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        chip.setSingleLine(true);
        chip.setEllipsize(TextUtils.TruncateAt.END);
        chip.setIncludeFontPadding(false);
        chip.setPadding(dp(context, 10), dp(context, 5), dp(context, 10), dp(context, 5));

        GradientDrawable drawable = new GradientDrawable();
        drawable.setCornerRadius(dp(context, 14));
        drawable.setColor(topicPalette(index));
        drawable.setStroke(dp(context, 1), Color.parseColor("#25B9896E"));
        chip.setBackground(drawable);

        FlexboxLayout.LayoutParams params = new FlexboxLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.rightMargin = dp(context, 8);
        params.bottomMargin = dp(context, 8);
        chip.setLayoutParams(params);
        return chip;
    }

    private int topicPalette(int index) {
        int[] palette = new int[] {
                Color.parseColor("#FDEBED"),
                Color.parseColor("#FFF2E6"),
                Color.parseColor("#EAF5F1"),
                Color.parseColor("#F3ECFF"),
                Color.parseColor("#FFF8D8")
        };
        return palette[index % palette.length];
    }

    private boolean isVideoType(int type) {
        return type == 0 || type == 2;
    }

    private int normalizeVideoType(int type) {
        return type == 2 ? 2 : 0;
    }

    private int dp(@NonNull Context context, int value) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                context.getResources().getDisplayMetrics()
        ));
    }

    @NonNull
    private String safeText(@Nullable String value, @NonNull String fallback) {
        if (value == null) {
            return fallback;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? fallback : trimmed;
    }

    static class ReportViewHolder extends RecyclerView.ViewHolder {
        final ImageView ivCover;
        final TextView tvTitle;
        final FlexboxLayout flexTopics;
        final TextView tvReason;
        final TextView tvScore;

        ReportViewHolder(@NonNull View itemView) {
            super(itemView);
            ivCover = itemView.findViewById(R.id.iv_report_cover);
            tvTitle = itemView.findViewById(R.id.tv_report_title);
            flexTopics = itemView.findViewById(R.id.flex_report_topics);
            tvReason = itemView.findViewById(R.id.tv_report_reason);
            tvScore = itemView.findViewById(R.id.tv_report_score);
        }
    }
}
