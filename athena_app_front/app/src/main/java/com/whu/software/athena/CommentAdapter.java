package com.whu.software.athena;

import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.whu.software.athena.entity.CommentBean;

import java.util.ArrayList;
import java.util.List;

public class CommentAdapter extends RecyclerView.Adapter<CommentAdapter.CommentViewHolder> {

    private static final String TAG = "CommentAdapter";

    private static final int VIEW_TYPE_COMMENT = 0;
    private static final int VIEW_TYPE_EXPAND  = 1;

    /**
     * 展平后的单行数据：
     * - 一行可以是主评论 / 子评论（type=COMMENT）
     * - 也可以是“展开另外 X 条回复 >”占位行（type=EXPAND）
     */
    public static class RowItem {
        private final int type;
        private final CommentBean comment;   // 当 type=COMMENT 时有效
        private final boolean isReply;       // 当前行是否为子评论
        private final long parentCommentId;  // 所属主评论 ID（子评论和展开行需要）
        private final int remainingReply;    // 展开行剩余回复数量

        private RowItem(int type, CommentBean comment,
                        boolean isReply, long parentCommentId, int remainingReply) {
            this.type = type;
            this.comment = comment;
            this.isReply = isReply;
            this.parentCommentId = parentCommentId;
            this.remainingReply = remainingReply;
        }

        public static RowItem comment(CommentBean comment, boolean isReply, long parentId) {
            return new RowItem(VIEW_TYPE_COMMENT, comment, isReply, parentId, 0);
        }

        public static RowItem expand(long parentId, int remaining) {
            return new RowItem(VIEW_TYPE_EXPAND, null, true, parentId, remaining);
        }

        public int getType() { return type; }
        public CommentBean getComment() { return comment; }
        public boolean isReply() { return isReply; }
        public long getParentCommentId() { return parentCommentId; }
        public int getRemainingReply() { return remainingReply; }
    }

    public interface OnExpandClickListener {
        void onExpandClick(long commentId);
    }

    /** 点击评论条目时回调，用于触发回复输入框（传完整对象，供 Activity 提取所有回复字段） */
    public interface OnCommentClickListener {
        void onCommentClick(CommentBean clickedComment);
    }

    private final List<RowItem> items = new ArrayList<>();
    private OnExpandClickListener expandClickListener;
    private OnCommentClickListener commentClickListener;

    public void setOnExpandClickListener(OnExpandClickListener listener) {
        this.expandClickListener = listener;
    }

    public void setOnCommentClickListener(OnCommentClickListener listener) {
        this.commentClickListener = listener;
    }

    public void setData(List<RowItem> data) {
        Log.d(TAG, "setData(flatten): count=" + (data != null ? data.size() : 0));
        items.clear();
        if (data != null) {
            items.addAll(data);
        }
        notifyDataSetChanged();
    }

    /** 供 Activity 持有同一份数据引用，进行局部插入后配合 notifyItemInserted 使用（展平后的 RowItem 列表） */
    public List<RowItem> getItems() {
        return items;
    }

    /**
     * 兼容旧代码：返回当前列表中的所有评论实体（按当前顺序，仅包含评论行，不含“展开更多”行）。
     * 注意：这是一个新的 List 副本，索引与 adapter 内部 items 基本一致，但不包含展开行。
     */
    public List<CommentBean> getComments() {
        List<CommentBean> result = new ArrayList<>();
        for (RowItem item : items) {
            if (item.getType() == VIEW_TYPE_COMMENT && item.getComment() != null) {
                result.add(item.getComment());
            }
        }
        return result;
    }

    /** 在指定位置插入一行并刷新（新展平模型专用） */
    public void insertAt(int index, RowItem item) {
        Log.d(TAG, "insertAt: index=" + index
                + ", type=" + item.getType()
                + ", parentId=" + item.getParentCommentId());
        items.add(index, item);
        notifyItemInserted(index);
    }

    /**
     * 兼容旧接口：直接传入 CommentBean 的插入方法。
     * 会根据 replyCommentId 判断是否为子评论，并构造对应的 RowItem。
     */
    public void insertAt(int index, CommentBean comment) {
        if (comment == null) return;
        boolean isReply = comment.getReplyCommentId() != 0;
        long parentId = isReply ? comment.getReplyCommentId() : comment.getCommentId();
        RowItem rowItem = RowItem.comment(comment, isReply, parentId);
        insertAt(index, rowItem);
    }

    @NonNull
    @Override
    public CommentViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_EXPAND) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_comment_expand, parent, false);
            return new CommentViewHolder(view);
        } else {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_comment, parent, false);
            return new CommentViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull CommentViewHolder holder, int position) {
        holder.bind(items.get(position), expandClickListener, commentClickListener);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @Override
    public int getItemViewType(int position) {
        return items.get(position).getType();
    }

    static class CommentViewHolder extends RecyclerView.ViewHolder {

        private final ImageView ivAvatar;
        private final TextView tvNickname;
        private final TextView tvTime;
        private final TextView tvContent;
        private final TextView tvExpandMore;

        CommentViewHolder(@NonNull View itemView) {
            super(itemView);
            ivAvatar    = itemView.findViewById(R.id.iv_comment_avatar);
            tvNickname  = itemView.findViewById(R.id.tv_comment_nickname);
            tvTime      = itemView.findViewById(R.id.tv_comment_time);
            tvContent   = itemView.findViewById(R.id.tv_comment_content);
            tvExpandMore = itemView.findViewById(R.id.tv_expand_more);
        }

        void bind(RowItem rowItem,
                  OnExpandClickListener listener,
                  OnCommentClickListener commentClickListener) {
            if (rowItem.getType() == VIEW_TYPE_EXPAND) {
                // 展开行：只显示“展开另外 X 条回复 >”
                if (tvExpandMore != null) {
                    tvExpandMore.setVisibility(View.VISIBLE);
                    tvExpandMore.setText("展开另外 " + rowItem.getRemainingReply() + " 条回复 >");
                    tvExpandMore.setOnClickListener(v -> {
                        Log.d(TAG, "expandMore clicked, parentId=" + rowItem.getParentCommentId()
                                + ", remaining=" + rowItem.getRemainingReply());
                        if (listener != null) {
                            listener.onExpandClick(rowItem.getParentCommentId());
                        }
                    });
                }

                // 隐藏评论自身内容区域（此布局只有一个 TextView）
                if (ivAvatar != null) ivAvatar.setVisibility(View.GONE);
                if (tvNickname != null) tvNickname.setVisibility(View.GONE);
                if (tvTime != null) tvTime.setVisibility(View.GONE);
                if (tvContent != null) tvContent.setVisibility(View.GONE);
                return;
            }

            CommentBean comment = rowItem.getComment();
            CommentBean.UserDTO user = comment.getUserDTO();
            String nickName = user != null ? user.getNickName() : "";
            tvNickname.setText(nickName);

            Log.d(TAG, "bind pos=" + getAdapterPosition()
                    + " | commentId=" + comment.getCommentId()
                    + " | replyCommentId=" + comment.getReplyCommentId()
                    + " | parentId=" + comment.getParentId()
                    + " | nick=" + nickName
                    + " | replyTotal=" + comment.getReplyTotal()
                    + " | content=\"" + comment.getContent() + "\"");

            if (tvExpandMore != null) {
                tvExpandMore.setVisibility(View.GONE);
                tvExpandMore.setOnClickListener(null);
            }

            // 点击昵称 / 评论内容区域 → 触发回复输入（传整个 comment 对象）
            tvContent.setOnClickListener(v -> {
                Log.d(TAG, "onCommentClick(content) commentId=" + comment.getCommentId()
                        + " nick=" + nickName);
                if (commentClickListener != null) {
                    commentClickListener.onCommentClick(comment);
                }
            });
            tvNickname.setOnClickListener(v -> {
                Log.d(TAG, "onCommentClick(nickname) commentId=" + comment.getCommentId()
                        + " nick=" + nickName);
                if (commentClickListener != null) {
                    commentClickListener.onCommentClick(comment);
                }
            });

            String time = comment.getCreateTime();
            if (time != null && time.length() > 16) {
                time = time.substring(0, 16).replace("T", " ");
            }
            tvTime.setText(time != null ? time : "");

            tvContent.setText(comment.getContent());

            if (user != null && !TextUtils.isEmpty(user.getIcon())) {
                Glide.with(ivAvatar.getContext())
                        .load(user.getIcon())
                        .circleCrop()
                        .placeholder(R.drawable.circle_background)
                        .into(ivAvatar);
            } else {
                ivAvatar.setImageResource(android.R.drawable.ic_menu_myplaces);
            }

            // ── 子评论缩进（展平：通过 leftMargin 区分层级，背景保持白色）─────────
            ViewGroup.MarginLayoutParams lp =
                    (ViewGroup.MarginLayoutParams) itemView.getLayoutParams();
            if (lp == null) {
                lp = new ViewGroup.MarginLayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
            }
            int indentPx = (int) (itemView.getResources().getDisplayMetrics().density * 40); // 约 40dp
            if (rowItem.isReply()) {
                lp.leftMargin = indentPx;
            } else {
                lp.leftMargin = 0;
            }
            itemView.setLayoutParams(lp);
            itemView.setBackgroundColor(0xFFFFFFFF);
        }
    }
}