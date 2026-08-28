package com.whu.software.athena.features.chat;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.whu.software.athena.ArticleDetailActivity;
import com.whu.software.athena.R;
import com.whu.software.athena.SolutionDetailActivity;
import com.whu.software.athena.core.ArticleReference;
import com.whu.software.athena.core.Message;
import com.whu.software.athena.features.privacy.RLHFDialogHelper;

import org.json.JSONObject;

import java.util.List;

import io.noties.markwon.Markwon;
import io.noties.markwon.image.glide.GlideImagesPlugin;

public class MessageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final String TAG = "MessageAdapter";
    public static final String ROLE_WELCOME = "welcome";
    public static final String ROLE_SYSTEM_NOTICE = "system_notice";
    private static final String CARD_BUTTON_TEXT =
            "\u67e5\u770b\u8be6\u60c5";

    static final int TYPE_USER = 0;
    static final int TYPE_ASSISTANT = 1;
    static final int TYPE_ASSISTANT_CARD = 2;

    private static final int MAX_TYPING_DURATION_MS = 5000;
    private static final int MS_PER_CHAR = 30;

    private final List<Message> messages;
    private final Markwon markwon;
    private final Runnable scrollToBottom;
    private final Handler mainHandler;

    public MessageAdapter(Context context, List<Message> messages) {
        this(context, messages, null);
    }

    public MessageAdapter(Context context, List<Message> messages, Runnable scrollToBottom) {
        this.messages = messages;
        this.scrollToBottom = scrollToBottom;
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.markwon = Markwon.builder(context)
                .usePlugin(GlideImagesPlugin.create(context))
                .build();
    }

    public void stopAllTyping() {
        for (Message msg : messages) {
            if (msg.isTyping()) {
                msg.setTyping(false);
            }
        }
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        Message message = messages.get(position);
        String role = message.getRole();

        if ("user".equals(role)) {
            return TYPE_USER;
        }

        String content = message.getContent();
        if (content != null
                && content.trim().startsWith("{")
                && content.contains("\"ui_type\"")
                && content.contains("\"product_card\"")) {
            return TYPE_ASSISTANT_CARD;
        }
        return TYPE_ASSISTANT;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        switch (viewType) {
            case TYPE_ASSISTANT_CARD:
                return new CardViewHolder(inflater.inflate(R.layout.item_chat_ai_card, parent, false));
            default:
                return new ViewHolder(inflater.inflate(R.layout.item_message, parent, false));
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder rawHolder, int position) {
        Message message = messages.get(position);
        int viewType = getItemViewType(position);

        if (viewType == TYPE_ASSISTANT_CARD) {
            bindCard((CardViewHolder) rawHolder, message);
            return;
        }
        ViewHolder holder = (ViewHolder) rawHolder;
        resetTypingAnimator(holder);
        bindReferences(holder, null);

        if (viewType == TYPE_USER) {
            holder.layoutBot.setVisibility(View.GONE);
            holder.layoutUser.setVisibility(View.VISIBLE);
            holder.tvUserContent.setText(message.getContent());
            return;
        }

        holder.layoutUser.setVisibility(View.GONE);
        holder.layoutBot.setVisibility(View.VISIBLE);

        final String fullContent = message.getContent() == null ? "" : message.getContent();
        if (!message.isTyping()) {
            markwon.setMarkdown(holder.tvBotContent, fullContent);
            bindReferences(holder, message);
            showFeedback(holder);
            return;
        }

        if ("...".equals(fullContent)) {
            markwon.setMarkdown(holder.tvBotContent, "...");
            hideFeedback(holder);
            return;
        }

        int charCount = fullContent.length();
        long duration = Math.min((long) charCount * MS_PER_CHAR, MAX_TYPING_DURATION_MS);
        markwon.setMarkdown(holder.tvBotContent, "");
        hideFeedback(holder);

        ValueAnimator animator = ValueAnimator.ofInt(0, charCount);
        animator.setDuration(duration);
        animator.addUpdateListener(animation -> {
            int shown = (int) animation.getAnimatedValue();
            markwon.setMarkdown(holder.tvBotContent, fullContent.substring(0, shown));
            if (scrollToBottom != null) {
                scrollToBottom.run();
            }
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                message.setTyping(false);
                markwon.setMarkdown(holder.tvBotContent, fullContent);
                bindReferences(holder, message);
                showFeedback(holder);
            }
        });
        animator.start();
        holder.typeAnimator = animator;
    }

    private void bindCard(@NonNull CardViewHolder holder, Message message) {
        try {
            JSONObject json = new JSONObject(message.getContent());

            String title = json.optString("title", "");
            String description = json.optString("description", "");
            String imageUrl = json.optString("imageUrl", "");
            String buttonText = json.optString("buttonText", CARD_BUTTON_TEXT);

            holder.itemView.setVisibility(View.VISIBLE);
            holder.tvCardTitle.setText(title);
            holder.tvCardDesc.setText(description);
            holder.btnCardAction.setText(buttonText);
            holder.btnCardAction.setOnClickListener(v -> {
                Intent intent = new Intent(v.getContext(), SolutionDetailActivity.class);
                intent.putExtra("ITEM_TITLE", title);
                v.getContext().startActivity(intent);
            });

            if (!imageUrl.isEmpty()) {
                holder.ivCardImage.setVisibility(View.VISIBLE);
                Glide.with(holder.itemView.getContext())
                        .load(imageUrl)
                        .centerCrop()
                        .placeholder(android.R.color.darker_gray)
                        .into(holder.ivCardImage);
            } else {
                holder.ivCardImage.setVisibility(View.GONE);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse product card JSON", e);
            holder.itemView.setVisibility(View.GONE);
        }
    }

    private void bindReferences(@NonNull ViewHolder holder, Message message) {
        holder.llReferencesContainer.removeAllViews();
        if (message == null || message.getReferences() == null || message.getReferences().isEmpty()) {
            holder.llReferencesContainer.setVisibility(View.GONE);
            return;
        }

        holder.llReferencesContainer.setVisibility(View.VISIBLE);
        LayoutInflater inflater = LayoutInflater.from(holder.itemView.getContext());
        for (ArticleReference reference : message.getReferences()) {
            View cardView = inflater.inflate(
                    R.layout.item_article_reference_card,
                    holder.llReferencesContainer,
                    false
            );

            TextView tvTitle = cardView.findViewById(R.id.tv_reference_title);
            TextView tvSnippet = cardView.findViewById(R.id.tv_reference_snippet);

            tvTitle.setText(getReferenceTitle(reference));
            if (TextUtils.isEmpty(reference.getSnippet())) {
                tvSnippet.setVisibility(View.GONE);
            } else {
                tvSnippet.setVisibility(View.VISIBLE);
                tvSnippet.setText(reference.getSnippet());
            }

            cardView.setOnClickListener(v -> openArticleDetail(v.getContext(), reference));
            holder.llReferencesContainer.addView(cardView);
        }
    }

    private void openArticleDetail(Context context, ArticleReference reference) {
        if (reference == null || reference.getNoteId() <= 0) {
            Log.w(TAG, "[ScienceAI] article card click blocked reference=" + reference);
            showToastOnMain(context, "\u6587\u7ae0\u4fe1\u606f\u4e0d\u5b8c\u6574\uff0c\u6682\u65f6\u65e0\u6cd5\u6253\u5f00");
            return;
        }
        launchArticleDetail(context, reference);
    }

    @NonNull
    private String getReferenceTitle(ArticleReference reference) {
        if (reference == null) {
            return "";
        }
        if (!TextUtils.isEmpty(reference.getTitle())) {
            return reference.getTitle();
        }
        if (reference.getNoteId() > 0) {
            return "\u76f8\u5173\u79d1\u666e\u6587\u7ae0 #" + reference.getNoteId();
        }
        return "\u76f8\u5173\u79d1\u666e\u6587\u7ae0";
    }

    private void launchArticleDetail(@NonNull Context context, @NonNull ArticleReference reference) {
        long noteId = reference.getNoteId();
        String blogId = reference.getBlogId();
        if (TextUtils.isEmpty(blogId) && noteId > 0) {
            blogId = String.valueOf(noteId);
        }
        int articleType = reference.getArticleType() > 0 ? reference.getArticleType() : 1;

        try {
            Intent intent = new Intent(context, ArticleDetailActivity.class);
            intent.putExtra("noteId", safeInt(noteId));
            intent.putExtra("id", String.valueOf(noteId));
            intent.putExtra("blog_id", blogId);
            intent.putExtra("blogId", blogId);
            intent.putExtra("title", getReferenceTitle(reference));
            intent.putExtra("type", articleType);
            intent.putExtra("article_type", articleType);
            if (!(context instanceof Activity)) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            }
            Log.d(TAG, "[ScienceAI] start ArticleDetailActivity directly"
                    + " noteId=" + noteId
                    + " blogId=" + blogId
                    + " type=" + articleType
                    + " reference=" + reference);
            context.startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "[ScienceAI] start ArticleDetailActivity failed"
                    + " reference=" + reference
                    + " error=" + e.getMessage(), e);
            showToastOnMain(context, "打开文章失败");
        }
    }

    private int safeInt(long value) {
        if (value > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        if (value < Integer.MIN_VALUE) {
            return Integer.MIN_VALUE;
        }
        return (int) value;
    }

    private void showToastOnMain(@NonNull Context context, @NonNull String message) {
        mainHandler.post(() -> Toast.makeText(context, message, Toast.LENGTH_SHORT).show());
    }

    private void showFeedback(@NonNull ViewHolder holder) {
        if (holder.layoutFeedback == null) {
            return;
        }
        holder.layoutFeedback.setVisibility(View.VISIBLE);
        holder.layoutFeedback.findViewById(R.id.btn_rlhf_negative)
                .setOnClickListener(v -> RLHFDialogHelper.showBiasCorrectionDialog(
                        v.getContext(), null));
    }

    private void hideFeedback(@NonNull ViewHolder holder) {
        if (holder.layoutFeedback != null) {
            holder.layoutFeedback.setVisibility(View.GONE);
        }
    }

    private void resetTypingAnimator(@NonNull ViewHolder holder) {
        if (holder.typeAnimator != null) {
            holder.typeAnimator.cancel();
            holder.typeAnimator = null;
        }
    }

    @Override
    public void onViewRecycled(@NonNull RecyclerView.ViewHolder rawHolder) {
        super.onViewRecycled(rawHolder);
        if (rawHolder instanceof ViewHolder) {
            resetTypingAnimator((ViewHolder) rawHolder);
        }
    }

    @Override
    public int getItemCount() {
        return messages == null ? 0 : messages.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        LinearLayout layoutBot;
        LinearLayout layoutUser;
        LinearLayout llReferencesContainer;
        TextView tvBotContent;
        TextView tvUserContent;
        View layoutFeedback;
        ValueAnimator typeAnimator;

        ViewHolder(View itemView) {
            super(itemView);
            layoutBot = itemView.findViewById(R.id.layoutBot);
            layoutUser = itemView.findViewById(R.id.layoutUser);
            llReferencesContainer = itemView.findViewById(R.id.ll_references_container);
            tvBotContent = itemView.findViewById(R.id.tvBotContent);
            tvUserContent = itemView.findViewById(R.id.tvUserContent);
            layoutFeedback = itemView.findViewById(R.id.layout_feedback);
        }
    }

    static class CardViewHolder extends RecyclerView.ViewHolder {
        ImageView ivCardImage;
        TextView tvCardTitle;
        TextView tvCardDesc;
        Button btnCardAction;

        CardViewHolder(View itemView) {
            super(itemView);
            ivCardImage = itemView.findViewById(R.id.ivCardImage);
            tvCardTitle = itemView.findViewById(R.id.tvCardTitle);
            tvCardDesc = itemView.findViewById(R.id.tvCardDesc);
            btnCardAction = itemView.findViewById(R.id.btnCardAction);
        }
    }

}
