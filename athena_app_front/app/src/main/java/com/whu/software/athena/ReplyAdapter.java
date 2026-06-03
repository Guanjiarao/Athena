package com.whu.software.athena;

import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.whu.software.athena.entity.CommentBean;

import java.util.ArrayList;
import java.util.List;

public class ReplyAdapter extends RecyclerView.Adapter<ReplyAdapter.ReplyViewHolder> {

    private final List<CommentBean> replies = new ArrayList<>();

    public void setData(List<CommentBean> data) {
        replies.clear();
        if (data != null) {
            replies.addAll(data);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ReplyViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_reply, parent, false);
        return new ReplyViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ReplyViewHolder holder, int position) {
        holder.bind(replies.get(position));
    }

    @Override
    public int getItemCount() {
        return replies.size();
    }

    static class ReplyViewHolder extends RecyclerView.ViewHolder {

        private final ImageView ivAvatar;
        private final TextView tvNickname;
        private final TextView tvTime;
        private final TextView tvContent;

        ReplyViewHolder(@NonNull View itemView) {
            super(itemView);
            ivAvatar   = itemView.findViewById(R.id.iv_reply_avatar);
            tvNickname = itemView.findViewById(R.id.tv_reply_nickname);
            tvTime     = itemView.findViewById(R.id.tv_reply_time);
            tvContent  = itemView.findViewById(R.id.tv_reply_content);
        }

        void bind(CommentBean reply) {
            CommentBean.UserDTO user = reply.getUserDTO();

            // 昵称
            tvNickname.setText(user != null ? user.getNickName() : "");

            // 时间
            String time = reply.getCreateTime();
            if (time != null && time.length() > 16) {
                time = time.substring(0, 16).replace("T", " ");
            }
            tvTime.setText(time != null ? time : "");

            // 头像
            if (user != null && !TextUtils.isEmpty(user.getIcon())) {
                Glide.with(ivAvatar.getContext())
                        .load(user.getIcon())
                        .circleCrop()
                        .placeholder(R.drawable.circle_background)
                        .into(ivAvatar);
            } else {
                ivAvatar.setImageResource(android.R.drawable.ic_menu_myplaces);
            }

            // 内容：如果有 replyUserName，前缀显示"回复 @某人: "（粉色高亮）
            String replyTo = reply.getReplyUserName();
            String body = reply.getContent() != null ? reply.getContent() : "";
            if (!TextUtils.isEmpty(replyTo)) {
                String prefix = "回复 @" + replyTo + ": ";
                SpannableString span = new SpannableString(prefix + body);
                span.setSpan(
                        new ForegroundColorSpan(0xFFFF7BAC),
                        0, prefix.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                tvContent.setText(span);
            } else {
                tvContent.setText(body);
            }
        }
    }
}