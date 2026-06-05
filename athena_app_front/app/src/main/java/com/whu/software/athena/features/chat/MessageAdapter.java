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
import com.whu.software.athena.config.ApiConfig;
import com.whu.software.athena.core.ArticleReference;
import com.whu.software.athena.core.Message;
import com.whu.software.athena.features.privacy.RLHFDialogHelper;
import com.whu.software.athena.utils.TokenManager;
import com.whu.software.athena.utils.UnsafeOkHttpClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.List;

import io.noties.markwon.Markwon;
import io.noties.markwon.image.glide.GlideImagesPlugin;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MessageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final String TAG = "MessageAdapter";
    private static final String WELCOME_READY_TEXT =
            "\u51C6\u5907\u597D\u4E86\uff0c\u968F\u65F6\u5F00\u59CB";
    private static final String CARD_BUTTON_TEXT =
            "\u67e5\u770b\u8be6\u60c5";
    private static final String MSG_ARTICLE_DETAIL_FAILED =
            "\u83b7\u53d6\u6587\u7ae0\u8be6\u60c5\u5931\u8d25\uff0c\u8bf7\u68c0\u67e5\u7f51\u7edc";
    private static final String MSG_TOKEN_EXPIRED =
            "\u8eab\u4efd\u9a8c\u8bc1\u5df2\u8fc7\u671f\uff0c\u8bf7\u91cd\u65b0\u767b\u5f55";
    private static final MediaType JSON_MEDIA_TYPE =
            MediaType.get("application/json; charset=utf-8");

    static final int TYPE_USER = 0;
    static final int TYPE_ASSISTANT = 1;
    static final int TYPE_ASSISTANT_CARD = 2;
    static final int TYPE_WELCOME = 3;
    static final int TYPE_SYSTEM_NOTICE = 4;

    public static final String ROLE_WELCOME = "welcome";
    public static final String ROLE_SYSTEM_NOTICE = "system_notice";

    private static final int MAX_TYPING_DURATION_MS = 5000;
    private static final int MS_PER_CHAR = 30;

    private final List<Message> messages;
    private final Markwon markwon;
    private final Runnable scrollToBottom;
    private final OkHttpClient okHttpClient;
    private final Handler mainHandler;

    public MessageAdapter(Context context, List<Message> messages) {
        this(context, messages, null);
    }

    public MessageAdapter(Context context, List<Message> messages, Runnable scrollToBottom) {
        this.messages = messages;
        this.scrollToBottom = scrollToBottom;
        this.okHttpClient = UnsafeOkHttpClient.getUnsafeOkHttpClient();
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

        if (ROLE_WELCOME.equals(role)) {
            return TYPE_WELCOME;
        }
        if (ROLE_SYSTEM_NOTICE.equals(role)) {
            return TYPE_SYSTEM_NOTICE;
        }
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
            case TYPE_WELCOME:
                return new WelcomeViewHolder(inflater.inflate(R.layout.item_chat_welcome, parent, false));
            case TYPE_SYSTEM_NOTICE:
                return new SystemNoticeViewHolder(inflater.inflate(R.layout.item_system_notice, parent, false));
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
        if (viewType == TYPE_WELCOME) {
            bindWelcome((WelcomeViewHolder) rawHolder, message);
            return;
        }
        if (viewType == TYPE_SYSTEM_NOTICE) {
            ((SystemNoticeViewHolder) rawHolder).tvNotice.setText(message.getContent());
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

    private void bindWelcome(@NonNull WelcomeViewHolder holder, Message message) {
        String content = message.getContent() == null ? "" : message.getContent();
        String[] parts = content.split("\\|", 2);

        String drawableName = parts.length > 0 ? parts[0] : "tree";
        String name = parts.length > 1 ? parts[1] : "Athena AI";

        Context context = holder.itemView.getContext();
        int resId = context.getResources().getIdentifier(
                drawableName, "drawable", context.getPackageName());
        if (resId != 0) {
            holder.ivAvatar.setImageResource(resId);
        }

        holder.tvName.setText(name);
        holder.tvDesc.setText(WELCOME_READY_TEXT);
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
        Log.d(TAG, "[ScienceAI] article card clicked reference=" + reference);
        fetchArticleBasicAndOpen(context, reference);
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

    private void fetchArticleBasicAndOpen(@NonNull Context context, @NonNull ArticleReference reference) {
        String token = TokenManager.getToken(context);
        if (TextUtils.isEmpty(token)) {
            Log.e(TAG, "[ScienceAI] article detail fetch blocked because token is empty"
                    + " reference=" + reference);
            showToastOnMain(context, MSG_TOKEN_EXPIRED);
            return;
        }

        try {
            JSONObject requestJson = new JSONObject();
            JSONArray noteIdList = new JSONArray();
            noteIdList.put(reference.getNoteId());
            requestJson.put("noteIdList", noteIdList);

            Request request = new Request.Builder()
                    .url(ApiConfig.API_NOTE_BASIC_LIST_BY_NOTE_IDS)
                    .addHeader("Authorization", "Bearer " + token)
                    .post(RequestBody.create(requestJson.toString(), JSON_MEDIA_TYPE))
                    .build();

            Log.d(TAG, "[ScienceAI] fetch article basic start"
                    + " noteId=" + reference.getNoteId()
                    + " requestBody=" + requestJson);

            okHttpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    Log.e(TAG, "[ScienceAI] fetch article basic failed"
                            + " reference=" + reference
                            + " error=" + e.getMessage(), e);
                    showToastOnMain(context, MSG_ARTICLE_DETAIL_FAILED);
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    String body = response.body() != null ? response.body().string() : "";
                    Log.d(TAG, "[ScienceAI] fetch article basic response"
                            + " httpCode=" + response.code()
                            + " reference=" + reference
                            + " body=" + body);
                    try (Response ignored = response) {
                        if (!response.isSuccessful()) {
                            Log.e(TAG, "[ScienceAI] fetch article basic http failed"
                                    + " reference=" + reference
                                    + " httpCode=" + response.code());
                            showToastOnMain(context, MSG_ARTICLE_DETAIL_FAILED);
                            return;
                        }

                        ArticleDetailPayload payload = buildPayloadFromBasic(body, reference);
                        requestArticleDetail(context, token, reference, payload);
                    } catch (Exception e) {
                        Log.e(TAG, "[ScienceAI] fetch article basic parse failed"
                                + " reference=" + reference
                                + " error=" + e.getMessage()
                                + " rawBody=" + body, e);
                        showToastOnMain(context, MSG_ARTICLE_DETAIL_FAILED);
                    }
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "[ScienceAI] fetch article basic build request failed"
                    + " reference=" + reference
                    + " error=" + e.getMessage(), e);
            showToastOnMain(context, MSG_ARTICLE_DETAIL_FAILED);
        }
    }

    private void requestArticleDetail(@NonNull Context context,
                                      @NonNull String token,
                                      @NonNull ArticleReference reference,
                                      @NonNull ArticleDetailPayload payload) {
        int requestType = payload.type > 0 ? payload.type : 1;
        String requestBlogId = !TextUtils.isEmpty(payload.blogId)
                ? payload.blogId
                : String.valueOf(reference.getNoteId());

        try {
            HttpUrl url = HttpUrl.parse(ApiConfig.API_BLOG_DETAIL).newBuilder()
                    .addQueryParameter("blog_id", requestBlogId)
                    .addQueryParameter("type", String.valueOf(requestType))
                    .build();

            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer " + token)
                    .get()
                    .build();

            Log.d(TAG, "[ScienceAI] fetch article detail start"
                    + " noteId=" + reference.getNoteId()
                    + " blogId=" + requestBlogId
                    + " type=" + requestType
                    + " title=" + payload.title);

            okHttpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    Log.e(TAG, "[ScienceAI] fetch article detail failed"
                            + " noteId=" + reference.getNoteId()
                            + " blogId=" + requestBlogId
                            + " type=" + requestType
                            + " error=" + e.getMessage(), e);
                    showToastOnMain(context, MSG_ARTICLE_DETAIL_FAILED);
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    String body = response.body() != null ? response.body().string() : "";
                    Log.d(TAG, "[ScienceAI] fetch article detail response"
                            + " httpCode=" + response.code()
                            + " noteId=" + reference.getNoteId()
                            + " blogId=" + requestBlogId
                            + " type=" + requestType
                            + " body=" + body);
                    try (Response ignored = response) {
                        if (!response.isSuccessful()) {
                            Log.e(TAG, "[ScienceAI] fetch article detail http failed"
                                    + " blogId=" + requestBlogId
                                    + " type=" + requestType
                                    + " httpCode=" + response.code());
                            showToastOnMain(context, MSG_ARTICLE_DETAIL_FAILED);
                            return;
                        }

                        ArticleDetailPayload detailPayload = fillPayloadFromDetail(body, payload, reference);
                        openArticleDetailActivity(context, detailPayload);
                    } catch (Exception e) {
                        Log.e(TAG, "[ScienceAI] fetch article detail parse failed"
                                + " blogId=" + requestBlogId
                                + " type=" + requestType
                                + " error=" + e.getMessage()
                                + " rawBody=" + body, e);
                        showToastOnMain(context, MSG_ARTICLE_DETAIL_FAILED);
                    }
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "[ScienceAI] fetch article detail build request failed"
                    + " reference=" + reference
                    + " error=" + e.getMessage(), e);
            showToastOnMain(context, MSG_ARTICLE_DETAIL_FAILED);
        }
    }

    @NonNull
    private ArticleDetailPayload buildPayloadFromBasic(@NonNull String body,
                                                       @NonNull ArticleReference reference) throws Exception {
        JSONObject root = new JSONObject(body);
        if (!isBusinessSuccess(root)) {
            throw new Exception(root.optString("message", "basic request failed"));
        }

        JSONObject basic = extractFirstDataObject(root.opt("data"));
        ArticleDetailPayload payload = new ArticleDetailPayload();
        payload.noteId = reference.getNoteId();
        payload.blogId = firstNonEmpty(
                optString(basic, "blogId"),
                optString(basic, "noteId"),
                optString(basic, "id"),
                optString(basic, "blog_id"),
                reference.getBlogId(),
                String.valueOf(reference.getNoteId())
        );
        payload.type = firstPositiveInt(basic,
                "type", "articleType", "article_type", "blogType");
        if (payload.type <= 0) {
            payload.type = reference.getArticleType();
        }
        if (payload.type <= 0) {
            payload.type = 1;
        }
        payload.title = firstNonEmpty(optString(basic, "title"), getReferenceTitle(reference));
        payload.authorName = parseAuthorName(basic);
        payload.coverUrl = firstNonEmpty(
                optString(basic, "coverUrl"),
                optString(basic, "image_url")
        );

        Log.d(TAG, "[ScienceAI] article basic parsed payload=" + payload);
        return payload;
    }

    @NonNull
    private ArticleDetailPayload fillPayloadFromDetail(@NonNull String body,
                                                       @NonNull ArticleDetailPayload basePayload,
                                                       @NonNull ArticleReference reference) throws Exception {
        JSONObject root = new JSONObject(body);
        if (!isBusinessSuccess(root)) {
            throw new Exception(root.optString("message", "detail request failed"));
        }

        JSONObject detail = extractObject(root.opt("data"));
        if (detail == null) {
            throw new Exception("detail data is empty");
        }

        ArticleDetailPayload payload = new ArticleDetailPayload(basePayload);
        payload.blogId = firstNonEmpty(
                optString(detail, "blogId"),
                optString(detail, "noteId"),
                optString(detail, "id"),
                optString(detail, "blog_id"),
                payload.blogId,
                String.valueOf(reference.getNoteId())
        );
        payload.type = firstPositiveInt(detail, "type", "articleType", "article_type", "blogType");
        if (payload.type <= 0) {
            payload.type = basePayload.type > 0 ? basePayload.type : 1;
        }
        payload.title = firstNonEmpty(optString(detail, "title"), payload.title, getReferenceTitle(reference));
        payload.authorName = firstNonEmpty(parseAuthorName(detail), payload.authorName);
        payload.coverUrl = firstNonEmpty(
                optString(detail, "coverUrl"),
                optString(detail, "image_url"),
                payload.coverUrl
        );
        payload.contentHtml = optString(detail, "content");
        if (TextUtils.isEmpty(payload.contentHtml)) {
            throw new Exception("detail content is empty");
        }

        Log.d(TAG, "[ScienceAI] article detail parsed payload=" + payload);
        return payload;
    }

    private void openArticleDetailActivity(@NonNull Context context,
                                           @NonNull ArticleDetailPayload payload) {
        mainHandler.post(() -> {
            try {
                Intent intent = new Intent(context, ArticleDetailActivity.class);
                intent.putExtra("noteId", (int) payload.noteId);
                intent.putExtra("blog_id", payload.blogId);
                intent.putExtra("title", payload.title);
                intent.putExtra("type", payload.type);
                intent.putExtra("article_type", payload.type);
                intent.putExtra("article_content_html", payload.contentHtml);
                intent.putExtra("article_author_name", payload.authorName);
                intent.putExtra("article_cover_url", payload.coverUrl);
                if (!(context instanceof Activity)) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                }

                Log.d(TAG, "[ScienceAI] start ArticleDetailActivity after network"
                        + " payload=" + payload);
                context.startActivity(intent);
            } catch (Exception e) {
                Log.e(TAG, "[ScienceAI] start ArticleDetailActivity failed"
                        + " payload=" + payload
                        + " error=" + e.getMessage(), e);
                Toast.makeText(context, MSG_ARTICLE_DETAIL_FAILED, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showToastOnMain(@NonNull Context context, @NonNull String message) {
        mainHandler.post(() -> Toast.makeText(context, message, Toast.LENGTH_SHORT).show());
    }

    private boolean isBusinessSuccess(@NonNull JSONObject root) {
        if (!root.has("code")) {
            return true;
        }
        String code = String.valueOf(root.opt("code"));
        return "200".equals(code) || "0".equals(code);
    }

    @NonNull
    private JSONObject extractFirstDataObject(Object rawData) throws Exception {
        JSONObject single = extractObject(rawData);
        if (single != null) {
            return single;
        }

        JSONArray array = extractArray(rawData);
        if (array != null && array.length() > 0) {
            JSONObject item = array.optJSONObject(0);
            if (item != null) {
                return item;
            }
        }
        return new JSONObject();
    }

    private JSONObject extractObject(Object rawData) throws Exception {
        if (rawData instanceof JSONObject) {
            return (JSONObject) rawData;
        }
        if (rawData instanceof String) {
            String text = ((String) rawData).trim();
            if (text.startsWith("{")) {
                return new JSONObject(text);
            }
        }
        return null;
    }

    private JSONArray extractArray(Object rawData) throws Exception {
        if (rawData instanceof JSONArray) {
            return (JSONArray) rawData;
        }
        if (rawData instanceof String) {
            String text = ((String) rawData).trim();
            if (text.startsWith("[")) {
                return new JSONArray(text);
            }
        }
        return null;
    }

    private int firstPositiveInt(JSONObject object, String... keys) {
        if (object == null || keys == null) {
            return 0;
        }
        for (String key : keys) {
            int value = parseInt(object.opt(key));
            if (value > 0) {
                return value;
            }
        }
        return 0;
    }

    private int parseInt(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String) {
            try {
                String text = ((String) value).trim();
                if (!TextUtils.isEmpty(text)) {
                    return Integer.parseInt(text);
                }
            } catch (Exception ignored) {
            }
        }
        return 0;
    }

    @NonNull
    private String parseAuthorName(JSONObject object) {
        if (object == null) {
            return "";
        }
        JSONObject userDTO = object.optJSONObject("userDTO");
        return firstNonEmpty(
                optString(userDTO, "nickName"),
                optString(object, "nickName"),
                optString(object, "userName"),
                optString(object, "authorName")
        );
    }

    @NonNull
    private String optString(JSONObject object, String key) {
        if (object == null || TextUtils.isEmpty(key) || !object.has(key)) {
            return "";
        }
        Object value = object.opt(key);
        return value == null ? "" : String.valueOf(value);
    }

    @NonNull
    private String firstNonEmpty(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (!TextUtils.isEmpty(value) && !"null".equalsIgnoreCase(value.trim())) {
                return value.trim();
            }
        }
        return "";
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

    private static class ArticleDetailPayload {
        long noteId;
        String blogId = "";
        String title = "";
        String authorName = "";
        String coverUrl = "";
        String contentHtml = "";
        int type = 1;

        ArticleDetailPayload() {
        }

        ArticleDetailPayload(@NonNull ArticleDetailPayload other) {
            this.noteId = other.noteId;
            this.blogId = other.blogId;
            this.title = other.title;
            this.authorName = other.authorName;
            this.coverUrl = other.coverUrl;
            this.contentHtml = other.contentHtml;
            this.type = other.type;
        }

        @NonNull
        @Override
        public String toString() {
            return "ArticleDetailPayload{"
                    + "noteId=" + noteId
                    + ", blogId='" + blogId + '\''
                    + ", title='" + title + '\''
                    + ", authorName='" + authorName + '\''
                    + ", coverUrl='" + coverUrl + '\''
                    + ", contentLength=" + (contentHtml == null ? 0 : contentHtml.length())
                    + ", type=" + type
                    + '}';
        }
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

    static class WelcomeViewHolder extends RecyclerView.ViewHolder {
        ImageView ivAvatar;
        TextView tvName;
        TextView tvDesc;

        WelcomeViewHolder(View itemView) {
            super(itemView);
            ivAvatar = itemView.findViewById(R.id.iv_welcome_avatar);
            tvName = itemView.findViewById(R.id.tv_welcome_name);
            tvDesc = itemView.findViewById(R.id.tv_welcome_desc);
        }
    }

    static class SystemNoticeViewHolder extends RecyclerView.ViewHolder {
        TextView tvNotice;

        SystemNoticeViewHolder(View itemView) {
            super(itemView);
            tvNotice = (TextView) itemView;
        }
    }
}
