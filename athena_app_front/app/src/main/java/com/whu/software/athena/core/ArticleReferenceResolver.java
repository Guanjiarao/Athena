package com.whu.software.athena.core;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.whu.software.athena.config.ApiConfig;
import com.whu.software.athena.utils.TokenManager;
import com.whu.software.athena.utils.UnsafeOkHttpClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ArticleReferenceResolver {

    private static final String TAG = "ArticleReferenceResolver";
    private static final MediaType JSON_MEDIA_TYPE =
            MediaType.get("application/json; charset=utf-8");
    private static final String MSG_TOKEN_EXPIRED =
            "\u8eab\u4efd\u9a8c\u8bc1\u5df2\u8fc7\u671f\uff0c\u8bf7\u91cd\u65b0\u767b\u5f55";

    public interface ResolverCallback {
        void onSuccess(List<ArticleReference> references);

        void onError(String error);
    }

    private final OkHttpClient client = UnsafeOkHttpClient.getUnsafeOkHttpClient();
    private Call currentCall;

    public synchronized void resolve(@NonNull Context context,
                                     @Nullable List<ArticleReference> rawReferences,
                                     @NonNull ResolverCallback callback) {
        List<ArticleReference> safeReferences = rawReferences == null
                ? new ArrayList<>()
                : new ArrayList<>(rawReferences);
        List<Long> noteIds = collectNoteIds(safeReferences);
        if (noteIds.isEmpty()) {
            callback.onSuccess(new ArrayList<>());
            return;
        }

        String token = TokenManager.getToken(context);
        if (TextUtils.isEmpty(token)) {
            callback.onError(MSG_TOKEN_EXPIRED);
            return;
        }

        JSONObject requestJson = new JSONObject();
        JSONArray idArray = new JSONArray();
        for (Long noteId : noteIds) {
            idArray.put(noteId);
        }
        try {
            requestJson.put("noteIdList", idArray);
        } catch (Exception e) {
            callback.onError(e.getMessage());
            return;
        }

        Log.d(TAG, "[ScienceAI] resolve start"
                + " noteIds=" + noteIds
                + " rawReferences=" + safeReferences
                + " requestBody=" + requestJson);

        Request request = new Request.Builder()
                .url(ApiConfig.API_NOTE_BASIC_LIST_BY_NOTE_IDS)
                .addHeader("Authorization", "Bearer " + token)
                .post(RequestBody.create(requestJson.toString(), JSON_MEDIA_TYPE))
                .build();

        Call call = client.newCall(request);
        currentCall = call;
        call.enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                clearCurrentCall(call);
                if (!call.isCanceled()) {
                    Log.e(TAG, "note basic request failed", e);
                    callback.onError(e.getMessage());
                }
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                clearCurrentCall(call);
                String body = response.body() != null ? response.body().string() : "";
                Log.d(TAG, "note basic response: " + body);
                try (Response ignored = response) {
                    if (!response.isSuccessful()) {
                        callback.onError(extractError(body, response.code()));
                        return;
                    }
                    callback.onSuccess(parseResolvedReferences(body, safeReferences));
                } catch (Exception e) {
                    Log.e(TAG, "note basic parse failed", e);
                    callback.onError(e.getMessage());
                }
            }
        });
    }

    public synchronized void cancel() {
        if (currentCall != null) {
            currentCall.cancel();
            currentCall = null;
        }
    }

    private synchronized void clearCurrentCall(@NonNull Call call) {
        if (currentCall == call) {
            currentCall = null;
        }
    }

    @NonNull
    private List<ArticleReference> parseResolvedReferences(@NonNull String body,
                                                          @NonNull List<ArticleReference> rawReferences)
            throws Exception {
        JSONObject root = new JSONObject(body);
        if (!isSuccess(root)) {
            throw new Exception(root.optString("message", "request failed"));
        }

        JSONArray dataArray = extractDataArray(root.opt("data"));
        Map<Long, JSONObject> basicByNoteId = new LinkedHashMap<>();
        if (dataArray != null) {
            for (int i = 0; i < dataArray.length(); i++) {
                JSONObject item = dataArray.optJSONObject(i);
                if (item == null) {
                    continue;
                }
                long noteId = firstPositiveLong(item,
                        "noteId", "blogId", "id", "blog_id", "note_id", "noteid");
                if (noteId > 0) {
                    basicByNoteId.put(noteId, item);
                }
            }
        }

        List<ArticleReference> result = new ArrayList<>();
        for (ArticleReference rawReference : rawReferences) {
            long rawNoteId = rawReference == null ? 0L : rawReference.getNoteId();
            JSONObject basic = basicByNoteId.get(rawNoteId);
            if (basic == null) {
                Log.w(TAG, "[ScienceAI] note basic missing for noteId=" + rawNoteId
                        + " rawReference=" + rawReference);
            } else {
                Log.d(TAG, "[ScienceAI] note basic matched for noteId=" + rawNoteId
                        + " basic=" + basic);
            }
            result.add(toResolvedReference(rawReference, basic));
        }
        Log.d(TAG, "[ScienceAI] resolve result references=" + result);
        return result;
    }

    @NonNull
    private ArticleReference toResolvedReference(@Nullable ArticleReference rawReference,
                                                @Nullable JSONObject basic) {
        long rawNoteId = rawReference == null ? 0L : rawReference.getNoteId();
        String rawBlogId = rawReference == null ? "" : rawReference.getBlogId();
        String rawTitle = rawReference == null ? "" : rawReference.getTitle();
        String rawSnippet = rawReference == null ? "" : rawReference.getSnippet();
        int rawType = rawReference == null ? 0 : rawReference.getArticleType();

        long noteId = basic == null
                ? rawNoteId
                : firstPositiveLong(basic, "noteId", "blogId", "id", "blog_id", "note_id", "noteid");
        if (noteId <= 0) {
            noteId = rawNoteId;
        }

        String blogId = basic == null
                ? rawBlogId
                : firstNonEmpty(
                        optString(basic, "blogId"),
                        optString(basic, "noteId"),
                        optString(basic, "id"),
                        optString(basic, "blog_id"),
                        rawBlogId
                );
        if (TextUtils.isEmpty(blogId) && noteId > 0) {
            blogId = String.valueOf(noteId);
        }

        int articleType = basic == null
                ? rawType
                : firstPositiveInt(basic, "type", "articleType", "article_type", "blogType");
        if (articleType <= 0) {
            articleType = rawType;
        }

        String title = basic == null
                ? rawTitle
                : firstNonEmpty(optString(basic, "title"), rawTitle);
        if (TextUtils.isEmpty(title)) {
            title = noteId > 0
                    ? "\u76f8\u5173\u79d1\u666e\u6587\u7ae0 #" + noteId
                    : "\u76f8\u5173\u79d1\u666e\u6587\u7ae0";
        }

        String snippet = basic == null
                ? rawSnippet
                : firstNonEmpty(
                        optString(basic, "snippet"),
                        optString(basic, "summary"),
                        optString(basic, "description"),
                        buildSnippet(optString(basic, "content")),
                        rawSnippet
                );
        return new ArticleReference(noteId, blogId, title, snippet, articleType);
    }

    @NonNull
    private List<Long> collectNoteIds(@NonNull List<ArticleReference> references) {
        LinkedHashMap<Long, Boolean> uniqueIds = new LinkedHashMap<>();
        for (ArticleReference reference : references) {
            if (reference != null && reference.getNoteId() > 0) {
                uniqueIds.put(reference.getNoteId(), true);
            }
        }
        return new ArrayList<>(uniqueIds.keySet());
    }

    private boolean isSuccess(@NonNull JSONObject root) {
        if (!root.has("code")) {
            return true;
        }
        String code = String.valueOf(root.opt("code"));
        return "200".equals(code) || "0".equals(code);
    }

    @Nullable
    private JSONArray extractDataArray(@Nullable Object rawData) {
        try {
            if (rawData instanceof JSONArray) {
                return (JSONArray) rawData;
            }
            if (rawData instanceof JSONObject) {
                JSONObject object = (JSONObject) rawData;
                JSONArray records = object.optJSONArray("records");
                if (records != null) {
                    return records;
                }
                JSONArray list = object.optJSONArray("list");
                if (list != null) {
                    return list;
                }
                JSONArray rows = object.optJSONArray("rows");
                if (rows != null) {
                    return rows;
                }
                JSONArray content = object.optJSONArray("content");
                if (content != null) {
                    return content;
                }
                JSONArray single = new JSONArray();
                single.put(object);
                return single;
            }
            if (rawData instanceof String) {
                String text = ((String) rawData).trim();
                if (TextUtils.isEmpty(text) || "null".equalsIgnoreCase(text)) {
                    return null;
                }
                if (text.startsWith("[")) {
                    return new JSONArray(text);
                }
                if (text.startsWith("{")) {
                    return extractDataArray(new JSONObject(text));
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "extract data array failed", e);
        }
        return null;
    }

    private long firstPositiveLong(@NonNull JSONObject object, @NonNull String... keys) {
        for (String key : keys) {
            long value = parseLong(object.opt(key));
            if (value > 0) {
                return value;
            }
        }
        return 0L;
    }

    private int firstPositiveInt(@NonNull JSONObject object, @NonNull String... keys) {
        for (String key : keys) {
            long value = parseLong(object.opt(key));
            if (value > 0 && value <= Integer.MAX_VALUE) {
                return (int) value;
            }
        }
        return 0;
    }

    private long parseLong(@Nullable Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value instanceof String) {
            try {
                String text = ((String) value).trim();
                if (!TextUtils.isEmpty(text)) {
                    return Long.parseLong(text);
                }
            } catch (Exception ignored) {
            }
        }
        return 0L;
    }

    @NonNull
    private String firstNonEmpty(@Nullable String... values) {
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

    @NonNull
    private String optString(@Nullable JSONObject object, @NonNull String key) {
        if (object == null || !object.has(key)) {
            return "";
        }
        Object value = object.opt(key);
        if (value == null) {
            return "";
        }
        return String.valueOf(value);
    }

    @NonNull
    private String buildSnippet(@Nullable String rawContent) {
        if (TextUtils.isEmpty(rawContent)) {
            return "";
        }
        String clean = rawContent
                .replaceAll("<[^>]+>", " ")
                .replace("&nbsp;", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (clean.length() > 80) {
            return clean.substring(0, 80) + "...";
        }
        return clean;
    }

    @NonNull
    private String extractError(@NonNull String body, int statusCode) {
        try {
            JSONObject root = new JSONObject(body);
            String message = root.optString("message");
            if (!TextUtils.isEmpty(message)) {
                return message;
            }
        } catch (Exception ignored) {
        }
        return "HTTP " + statusCode;
    }
}
