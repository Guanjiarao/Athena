package com.whu.software.athena;

import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.whu.software.athena.entity.TimelineEntity;

import java.util.ArrayList;
import java.util.List;


public class TimelineAdapter extends RecyclerView.Adapter<TimelineAdapter.TimelineViewHolder> {

    private List<TimelineEntity> items = new ArrayList<>();

    public void setItems(List<TimelineEntity> items) {
        this.items = items;
        notifyDataSetChanged();
    }

    public List<TimelineEntity> getCurrentItems() {
        return items;
    }

    @NonNull
    @Override
    public TimelineViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_timeline_left, parent, false);
        return new TimelineViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TimelineViewHolder holder, int position) {
        TimelineEntity item = items.get(position);

        holder.tvTitle.setText(item.getTitle());
        holder.tvTitle.setVisibility(View.VISIBLE);

        holder.tvDesc.setText(item.getDescription());
        holder.tvDesc.setVisibility(View.VISIBLE);
        
        String timeLabel = item.getTimeLabel();
        if (timeLabel != null && !timeLabel.trim().isEmpty()) {
            holder.tvTime.setText(timeLabel);
            holder.tvTime.setVisibility(View.VISIBLE); // 强制显示
        } else {
            holder.tvTime.setText("");
            holder.tvTime.setVisibility(View.GONE);    // 强制隐藏，防止复用残留
        }

        // 优先使用本地 drawable，否则使用网络 URL
        if (item.hasLocalDrawable()) {
            Glide.with(holder.itemView.getContext())
                    .load(item.getDrawableResId())
                    .centerCrop()
                    .placeholder(android.R.color.darker_gray)
                    .into(holder.ivThumb);
        } else {
            Glide.with(holder.itemView.getContext())
                    .load(item.getImageUrl())
                    .centerCrop()
                    .placeholder(android.R.color.darker_gray)
                    .into(holder.ivThumb);
        }

        // 透明度与缩放由外部 ScrollListener 统一控制，此处重置为默认值
        holder.itemView.setAlpha(1.0f);
        holder.itemView.setScaleX(1.0f);
        holder.itemView.setScaleY(1.0f);

        // 标题文案会随科普页改版变化，点击只按年龄前缀路由到现有详情页。
        View clickTarget = holder.cardTimeline != null ? holder.cardTimeline : holder.itemView;
        clickTarget.setOnClickListener(v -> {
            String title = item.getTitle() == null ? "" : item.getTitle();
            if (title.startsWith("0~12")) {
                Intent intent = new Intent(v.getContext(), Age0To12RoleActivity.class);
                v.getContext().startActivity(intent);
            } else if (title.startsWith("12~22")) {
                Intent intent = new Intent(v.getContext(), Age12To22Activity.class);
                v.getContext().startActivity(intent);
            } else if (title.startsWith("22~55")) {
                Intent intent = new Intent(v.getContext(), MatureStageActivity.class);
                v.getContext().startActivity(intent);
            } else if (title.startsWith("55")) {
                Intent intent = new Intent(v.getContext(), Age55PlusActivity.class);
                v.getContext().startActivity(intent);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class TimelineViewHolder extends RecyclerView.ViewHolder {
        final View      cardTimeline;
        final ImageView ivThumb;
        final TextView  tvTitle;
        final TextView  tvDesc;
        final TextView  tvTime;

        TimelineViewHolder(@NonNull View itemView) {
            super(itemView);
            cardTimeline = itemView.findViewById(R.id.card_timeline);
            ivThumb      = itemView.findViewById(R.id.iv_timeline_thumb);
            tvTitle      = itemView.findViewById(R.id.tv_timeline_title);
            tvDesc       = itemView.findViewById(R.id.tv_timeline_desc);
            tvTime       = itemView.findViewById(R.id.tv_timeline_time);
        }
    }
}
