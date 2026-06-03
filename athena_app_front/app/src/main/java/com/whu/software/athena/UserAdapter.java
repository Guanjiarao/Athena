package com.whu.software.athena;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.whu.software.athena.entity.UserEntity;

import java.util.ArrayList;
import java.util.List;

public class UserAdapter extends RecyclerView.Adapter<UserAdapter.UserViewHolder> {

    private final List<UserEntity> users = new ArrayList<>();
    private OnUserClickListener clickListener;

    public interface OnUserClickListener {
        void onUserClick(UserEntity user);
    }

    public void setOnUserClickListener(OnUserClickListener listener) {
        this.clickListener = listener;
    }

    public void setData(List<UserEntity> data) {
        users.clear();
        if (data != null) {
            users.addAll(data);
        }
        notifyDataSetChanged();
    }

    public void addData(List<UserEntity> data) {
        if (data != null) {
            users.addAll(data);
            notifyDataSetChanged();
        }
    }

    @NonNull
    @Override
    public UserViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_user, parent, false);
        return new UserViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull UserViewHolder holder, int position) {
        holder.bind(users.get(position), clickListener);
    }

    @Override
    public int getItemCount() {
        return users.size();
    }

    static class UserViewHolder extends RecyclerView.ViewHolder {

        private final ImageView ivAvatar;
        private final TextView tvNickname;
        private final TextView tvUserId;
        private final ImageView ivPriorityBadge;

        UserViewHolder(@NonNull View itemView) {
            super(itemView);
            ivAvatar = itemView.findViewById(R.id.iv_user_avatar);
            tvNickname = itemView.findViewById(R.id.tv_user_nickname);
            tvUserId = itemView.findViewById(R.id.tv_user_id);
            ivPriorityBadge = itemView.findViewById(R.id.iv_priority_badge);
        }

        void bind(UserEntity user, OnUserClickListener listener) {
            tvNickname.setText(user.getNickName());
            tvUserId.setText("ID: " + user.getId());

            if (!TextUtils.isEmpty(user.getIcon())) {
                Glide.with(itemView.getContext())
                        .load(user.getIcon())
                        .circleCrop()
                        .placeholder(R.drawable.circle_background)
                        .into(ivAvatar);
            } else {
                ivAvatar.setImageResource(R.drawable.circle_background);
            }

            if (user.isPriority()) {
                ivPriorityBadge.setVisibility(View.VISIBLE);
            } else {
                ivPriorityBadge.setVisibility(View.GONE);
            }

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onUserClick(user);
                }
            });
        }
    }
}
