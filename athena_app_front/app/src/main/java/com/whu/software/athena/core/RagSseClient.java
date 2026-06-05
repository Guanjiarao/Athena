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
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okio.BufferedSource;

public class RagSseClient {

    private static final String TAG = "RagSseClient";
    private static final String DEFAULT_BASE_URL = ApiConfig.BASE_URL;
    private static final String DEFAULT_RAG_RELATIVE_PATH = "rag/rag/v3/chat";
    private static final String CONTEXT_PATH_SEGMENT = "athena";
    private static final String ERROR_BUILD_BODY =
            "\u8bf7\u6c42\u4f53\u6784\u5efa\u5931\u8d25: ";
    private static final String ERROR_REQUEST_FAILED =
            "SSE \u8bf7\u6c42\u5931\u8d25: ";
    private static final String ERROR_RESPONSE_FAILED =
            "SSE \u54cd\u5e94\u5f02\u5e38: ";
    private static final String ERROR_RESPONSE_EMPTY =
            "SSE \u54cd\u5e94\u4f53\u4e3a\u7a7a";
    private static final String ERROR_PARSE_FAILED =
            "SSE \u89e3\u6790\u5931\u8d25: ";

    public interface Listener {
        default void onOpen() {
        }

        default void onMeta(String conversationId, String taskId, String title) {
        }

        void onDelta(String delta);

        void onFinish(String messageId, String title, List<ArticleReference> references);

        void onError(String error);

        default void onClosed() {
        }
    }

    private final OkHttpClient client;
    private final String endpointUrl;
    private Call currentCall;

    public RagSseClient() {
        this(DEFAULT_BASE_URL);
    }

    public RagSseClient(@NonNull String baseUrl) {
        this.endpointUrl = buildEndpointUrl(baseUrl, DEFAULT_RAG_RELATIVE_PATH);
        this.client = UnsafeOkHttpClient.getUnsafeOkHttpClient()
                .newBuilder()
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .build();
    }

    public synchronized void streamQuestion(@NonNull Context context,
                                            @NonNull String question,
                                            @NonNull Listener listener) {
        streamQuestion(context, question, null, false, listener);
    }

    public synchronized void streamQuestion(@NonNull Context context,
                                            @NonNull String question,
                                            @Nullable String conversationId,
                                            boolean deepThinking,
                                            @NonNull Listener listener) {
        cancel();

        HttpUrl requestUrl = HttpUrl.parse(endpointUrl);
        if (requestUrl == null) {
            listener.onError(ERROR_BUILD_BODY + "RAG URL invalid: " + endpointUrl);
            return;
        }

        HttpUrl.Builder urlBuilder = requestUrl.newBuilder()
                .addQueryParameter("question", question)
                .addQueryParameter("deepThinking", String.valueOf(deepThinking));
        if (!TextUtils.isEmpty(conversationId)) {
            urlBuilder.addQueryParameter("conversationId", conversationId);
        }
        requestUrl = urlBuilder.build();

        Request.Builder requestBuilder = new Request.Builder()
                .url(requestUrl)
                .addHeader("Accept", "text/event-stream")
                .addHeader("Cache-Control", "no-cache")
                .get();

        String token = TokenManager.getToken(context);
        if (!TextUtils.isEmpty(token)) {
            requestBuilder.addHeader("Authorization", "Bearer " + token);
        }

        Request request = requestBuilder.build();
        Log.d(TAG, "stream url = " + request.url());
        currentCall = client.newCall(request);
        currentCall.enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                clearCurrentCall(call);
                if (call.isCanceled()) {
                    return;
                }
                Log.e(TAG, "SSE request failed", e);
                listener.onError(ERROR_REQUEST_FAILED + e.getMessage());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                clearCurrentCall(call);
                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "";
                    Log.e(TAG, "SSE response failed: code=" + response.code() + " body=" + errorBody);
                    listener.onError(ERROR_RESPONSE_FAILED + response.code());
                    return;
                }

                if (response.body() == null) {
                    listener.onError(ERROR_RESPONSE_EMPTY);
                    return;
                }

                listener.onOpen();
                try (Response ignored = response) {
                    parseEventStream(response.body().source(), listener);
                    listener.onClosed();
                } catch (Exception e) {
                    if (!call.isCanceled()) {
                        Log.e(TAG, "SSE parse failed", e);
                        listener.onError(ERROR_PARSE_FAILED + e.getMessage());
                    }
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
    private String buildEndpointUrl(@NonNull String baseUrl, @NonNull String relativePath) {
        HttpUrl parsedBaseUrl = HttpUrl.parse(ensureTrailingSlash(baseUrl.trim()));
        if (parsedBaseUrl == null) {
            return baseUrl;
        }

        List<String> baseSegments = normalizeBasePathSegments(parsedBaseUrl.pathSegments());
        List<String> relativeSegments = splitPathSegments(relativePath);
        if (endsWithSegments(baseSegments, relativeSegments)) {
            return rebuildUrl(parsedBaseUrl, baseSegments).toString();
        }

        HttpUrl.Builder builder = rebuildUrl(parsedBaseUrl, baseSegments).newBuilder();
        for (String segment : relativeSegments) {
            builder.addPathSegment(segment);
        }
        return builder.build().toString();
    }

    @NonNull
    private HttpUrl rebuildUrl(@NonNull HttpUrl baseUrl, @NonNull List<String> pathSegments) {
        HttpUrl.Builder builder = baseUrl.newBuilder();
        builder.encodedPath("/");
        for (String segment : pathSegments) {
            builder.addPathSegment(segment);
        }
        return builder.build();
    }

    @NonNull
    private List<String> normalizeBasePathSegments(@NonNull List<String> rawSegments) {
        List<String> normalized = new ArrayList<>();
        for (String segment : rawSegments) {
            if (TextUtils.isEmpty(segment)) {
                continue;
            }
            if (CONTEXT_PATH_SEGMENT.equals(segment)
                    && !normalized.isEmpty()
                    && CONTEXT_PATH_SEGMENT.equals(normalized.get(normalized.size() - 1))) {
                continue;
            }
            normalized.add(segment);
        }
        return normalized;
    }

    @NonNull
    private List<String> splitPathSegments(@NonNull String path) {
        List<String> segments = new ArrayList<>();
        String[] parts = path.split("/");
        for (String part : parts) {
            if (!TextUtils.isEmpty(part)) {
                segments.add(part);
            }
        }
        return segments;
    }

    private boolean endsWithSegments(@NonNull List<String> source, @NonNull List<String> target) {
        if (target.isEmpty() || source.size() < target.size()) {
            return false;
        }
        int offset = source.size() - target.size();
        for (int i = 0; i < target.size(); i++) {
            if (!target.get(i).equals(source.get(offset + i))) {
                return false;
            }
        }
        return true;
    }

    @NonNull
    private String ensureTrailingSlash(@NonNull String url) {
        return url.endsWith("/") ? url : url + "/";
    }

    private void parseEventStream(@NonNull BufferedSource source,
                                  @NonNull Listener listener) throws IOException {
        String currentEvent = null;
        StringBuilder dataBuilder = new StringBuilder();

        while (!source.exhausted()) {
            String line = source.readUtf8Line();
            if (line == null) {
                break;
            }

            if (line.isEmpty()) {
                dispatchEvent(currentEvent, dataBuilder.toString(), listener);
                currentEvent = null;
                dataBuilder.setLength(0);
                continue;
            }

            if (line.startsWith("event:")) {
                currentEvent = line.substring("event:".length()).trim();
                continue;
            }

            if (line.startsWith("data:")) {
                if (dataBuilder.length() > 0) {
                    dataBuilder.append('\n');
                }
                dataBuilder.append(line.substring("data:".length()).trim());
            }
        }

        if (dataBuilder.length() > 0) {
            dispatchEvent(currentEvent, dataBuilder.toString(), listener);
        }
    }

    private void dispatchEvent(String eventName,
                               String rawData,
                               Listener listener) {
        String data = rawData == null ? "" : rawData.trim();
        if (data.isEmpty() || "[DONE]".equals(data)) {
            return;
        }

        String resolvedEvent = eventName;
        JSONObject payload = parseJsonSafely(data);
        if (TextUtils.isEmpty(resolvedEvent) && payload != null) {
            resolvedEvent = payload.optString("event");
            if (TextUtils.isEmpty(resolvedEvent)) {
                resolvedEvent = payload.optString("type");
            }
        }

        if ("meta".equalsIgnoreCase(resolvedEvent)) {
            dispatchMeta(payload, listener);
            return;
        }

        if (isFinishPayload(resolvedEvent, payload)) {
            dispatchFinish(payload, listener);
            return;
        }

        if ("error".equalsIgnoreCase(resolvedEvent)) {
            listener.onError(extractErrorMessage(payload, data));
            return;
        }

        String delta = extractDeltaText(payload, data);
        if (!TextUtils.isEmpty(delta)) {
            listener.onDelta(delta);
        }
    }

    private boolean isFinishPayload(String eventName, JSONObject payload) {
        JSONObject resolvedPayload = resolvePayloadObject(payload);
        if ("finish".equalsIgnoreCase(eventName)) {
            return true;
        }
        if (resolvedPayload == null) {
            return false;
        }
        return resolvedPayload.has("messageId")
                || resolvedPayload.has("references")
                || resolvedPayload.has("noteIds")
                || resolvedPayload.has("noteIdList")
                || resolvedPayload.has("note_id_list");
    }

    private void dispatchMeta(JSONObject payload, Listener listener) {
        JSONObject resolvedPayload = resolvePayloadObject(payload);
        if (resolvedPayload == null) {
            listener.onMeta("", "", "");
            return;
        }
        listener.onMeta(
                resolvedPayload.optString("conversationId"),
                resolvedPayload.optString("taskId"),
                resolvedPayload.optString("title")
        );
    }

    private void dispatchFinish(JSONObject payload, Listener listener) {
        JSONObject resolvedPayload = resolvePayloadObject(payload);
        if (resolvedPayload == null) {
            Log.w(TAG, "[ScienceAI] finish payload is empty");
            listener.onFinish("", "", new ArrayList<>());
            return;
        }

        List<ArticleReference> references = new ArrayList<>();
        JSONArray referencesArray = resolvedPayload.optJSONArray("references");
        if (referencesArray != null) {
            for (int i = 0; i < referencesArray.length(); i++) {
                appendReference(references, referencesArray.opt(i));
            }
        }
        appendNoteIdsValue(references, resolvedPayload.opt("noteIds"));
        appendNoteIdsValue(references, resolvedPayload.opt("noteIdList"));
        appendNoteIdsValue(references, resolvedPayload.opt("note_id_list"));
        appendSingleNoteId(references, resolvedPayload);

        Log.d(TAG, "[ScienceAI] finish payload parsed"
                + " messageId=" + resolvedPayload.optString("messageId")
                + " title=" + resolvedPayload.optString("title")
                + " references=" + references
                + " rawPayload=" + resolvedPayload);

        listener.onFinish(
                resolvedPayload.optString("messageId"),
                resolvedPayload.optString("title"),
                references
        );
    }

    private void appendReference(@NonNull List<ArticleReference> references, Object rawItem) {
        if (rawItem instanceof JSONObject) {
            JSONObject item = (JSONObject) rawItem;
            long noteId = firstPositiveLong(item,
                    "noteId", "note_id", "noteid", "blogId", "blog_id", "id");
            if (noteId <= 0 || containsNoteId(references, noteId)) {
                return;
            }
            int articleType = firstPositiveInt(item, "type", "articleType", "article_type", "blogType");
            references.add(new ArticleReference(
                    noteId,
                    noteId > 0 ? String.valueOf(noteId) : "",
                    item.optString("title"),
                    item.optString("snippet"),
                    articleType
            ));
            return;
        }

        long noteId = parseLong(rawItem);
        if (noteId > 0 && !containsNoteId(references, noteId)) {
            references.add(new ArticleReference(noteId, "", ""));
        }
    }

    private void appendNoteIdsValue(@NonNull List<ArticleReference> references, Object rawValue) {
        if (rawValue instanceof JSONArray) {
            appendNoteIdArray(references, (JSONArray) rawValue);
            return;
        }
        if (rawValue instanceof String) {
            String text = ((String) rawValue).trim();
            if (TextUtils.isEmpty(text) || "null".equalsIgnoreCase(text)) {
                return;
            }
            if (text.startsWith("[")) {
                try {
                    appendNoteIdArray(references, new JSONArray(text));
                    return;
                } catch (Exception ignored) {
                }
            }
            String[] parts = text.split(",");
            for (String part : parts) {
                long noteId = parseLong(part);
                if (noteId > 0 && !containsNoteId(references, noteId)) {
                    references.add(new ArticleReference(noteId, "", ""));
                }
            }
            return;
        }
        long noteId = parseLong(rawValue);
        if (noteId > 0 && !containsNoteId(references, noteId)) {
            references.add(new ArticleReference(noteId, "", ""));
        }
    }

    private void appendNoteIdArray(@NonNull List<ArticleReference> references, @NonNull JSONArray noteIdArray) {
        for (int i = 0; i < noteIdArray.length(); i++) {
            long noteId = parseLong(noteIdArray.opt(i));
            if (noteId > 0 && !containsNoteId(references, noteId)) {
                references.add(new ArticleReference(noteId, "", ""));
            }
        }
    }

    private void appendSingleNoteId(@NonNull List<ArticleReference> references, @NonNull JSONObject payload) {
        long noteId = firstPositiveLong(payload, "noteId", "note_id", "noteid", "blogId", "blog_id", "id");
        if (noteId > 0 && !containsNoteId(references, noteId)) {
            references.add(new ArticleReference(noteId, "", ""));
        }
    }

    private boolean containsNoteId(@NonNull List<ArticleReference> references, long noteId) {
        for (ArticleReference reference : references) {
            if (reference != null && reference.getNoteId() == noteId) {
                return true;
            }
        }
        return false;
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

    private long parseLong(Object value) {
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

    private String extractDeltaText(JSONObject payload, String fallback) {
        JSONObject resolvedPayload = resolvePayloadObject(payload);
        if (resolvedPayload == null) {
            return fallback;
        }

        String[] keys = {"delta", "content", "text", "answer", "message", "chunk"};
        for (String key : keys) {
            String value = resolvedPayload.optString(key);
            if (!TextUtils.isEmpty(value)) {
                return value;
            }
        }
        return "";
    }

    private String extractErrorMessage(JSONObject payload, String fallback) {
        JSONObject resolvedPayload = resolvePayloadObject(payload);
        if (resolvedPayload == null) {
            return fallback;
        }
        String message = resolvedPayload.optString("message");
        if (!TextUtils.isEmpty(message)) {
            return message;
        }
        return fallback;
    }

    private JSONObject resolvePayloadObject(JSONObject payload) {
        if (payload == null) {
            return null;
        }
        JSONObject nested = payload.optJSONObject("data");
        if (nested != null) {
            return nested;
        }
        String dataText = payload.optString("data");
        if (!TextUtils.isEmpty(dataText) && dataText.trim().startsWith("{")) {
            try {
                return new JSONObject(dataText);
            } catch (Exception ignored) {
            }
        }
        return payload;
    }

    private JSONObject parseJsonSafely(String data) {
        try {
            return new JSONObject(data);
        } catch (Exception ignored) {
            return null;
        }
    }
}
