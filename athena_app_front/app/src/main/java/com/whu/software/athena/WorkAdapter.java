package com.whu.software.athena;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class WorkAdapter extends RecyclerView.Adapter<WorkAdapter.WorkViewHolder> {

    private List<WorkItem> workList = new ArrayList<>();
    private OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(WorkItem work);
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    public void setWorkList(List<WorkItem> workList) {
        this.workList = workList;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public WorkViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_work, parent, false);
        return new WorkViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull WorkViewHolder holder, int position) {
        WorkItem work = workList.get(position);
        holder.bind(work);
    }

    @Override
    public int getItemCount() {
        return workList.size();
    }

    class WorkViewHolder extends RecyclerView.ViewHolder {
        private ImageView ivWorkThumbnail;
        private TextView tvWorkTitle;

        public WorkViewHolder(@NonNull View itemView) {
            super(itemView);
            ivWorkThumbnail = itemView.findViewById(R.id.iv_work_thumbnail);
            tvWorkTitle = itemView.findViewById(R.id.tv_work_title);

            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && listener != null) {
                    listener.onItemClick(workList.get(position));
                }
            });
        }

        public void bind(WorkItem work) {
            tvWorkTitle.setText(work.getTitle());
            // TODO: 使用图片加载库（如 Glide 或 Picasso）加载缩略图
            // Glide.with(itemView.getContext())
            //     .load(work.getThumbnailUrl())
            //     .into(ivWorkThumbnail);
        }
    }

    // 作品数据模型
    public static class WorkItem {
        private String id;
        private String title;
        private String thumbnailUrl;

        public WorkItem(String id, String title, String thumbnailUrl) {
            this.id = id;
            this.title = title;
            this.thumbnailUrl = thumbnailUrl;
        }

        public String getId() {
            return id;
        }

        public String getTitle() {
            return title;
        }

        public String getThumbnailUrl() {
            return thumbnailUrl;
        }
    }
}

