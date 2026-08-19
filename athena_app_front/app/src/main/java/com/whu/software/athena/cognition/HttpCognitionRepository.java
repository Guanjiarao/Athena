package com.whu.software.athena.cognition;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.whu.software.athena.cognition.CognitionModels.*;
import com.whu.software.athena.config.ApiConfig;
import com.whu.software.athena.entity.ApiResponse;
import com.whu.software.athena.utils.TokenManager;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/** Contract V1 HTTP implementation. Model keys and health decisions never run in Android. */
public final class HttpCognitionRepository implements CognitionRepository {

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final String KEY_PREFS = "athena_cognition_pending_mutations";

    private final Context context;
    private final Gson gson = new Gson();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final SharedPreferences pendingKeys;
    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(ApiConfig.CONNECT_TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(ApiConfig.READ_TIMEOUT, TimeUnit.SECONDS)
            .writeTimeout(ApiConfig.WRITE_TIMEOUT, TimeUnit.SECONDS)
            .build();

    public HttpCognitionRepository(Context context) {
        this.context = context.getApplicationContext();
        this.pendingKeys = this.context.getSharedPreferences(KEY_PREFS, Context.MODE_PRIVATE);
    }

    @Override
    public void createClue(ClueCreateRequest request, Callback<Clue> callback) {
        post("/clues", "CREATE_CLUE", request, new TypeToken<ApiResponse<Clue>>() {}.getType(), callback);
    }

    @Override
    public void listClues(ClueSection section, Callback<List<Clue>> callback) {
        HttpUrl url = url("/clues").newBuilder().addQueryParameter("section", section.name()).build();
        getPage(url, new TypeToken<ApiResponse<CursorPage<Clue>>>() {}.getType(), callback);
    }

    @Override
    public void createDigestTask(List<Long> clueIds, Callback<DigestTask> callback) {
        Map<String, Object> body = new HashMap<>();
        body.put("clueIds", clueIds);
        post("/digest-tasks", "CREATE_DIGEST_TASK", body,
                new TypeToken<ApiResponse<DigestTask>>() {}.getType(), callback);
    }

    @Override
    public void retryDigestTask(long taskId, Callback<DigestTask> callback) {
        post("/digest-tasks/" + taskId + "/retry", "RETRY_DIGEST_TASK_" + taskId,
                new HashMap<>(), new TypeToken<ApiResponse<DigestTask>>() {}.getType(), callback);
    }

    @Override
    public void getDigest(long digestId, Callback<Digest> callback) {
        get(url("/digests/" + digestId), new TypeToken<ApiResponse<Digest>>() {}.getType(), callback);
    }

    @Override
    public void listPendingDigests(Callback<List<Digest>> callback) {
        HttpUrl url = url("/digests").newBuilder()
                .addQueryParameter("status", DigestStatus.PENDING_CONFIRMATION.name()).build();
        getPage(url, new TypeToken<ApiResponse<CursorPage<Digest>>>() {}.getType(), callback);
    }

    @Override
    public void decideDigest(long digestId, DigestDecision decision, String reasonCode,
                             Callback<DigestDecisionResult> callback) {
        Map<String, Object> body = new HashMap<>();
        body.put("decision", decision.name());
        if (reasonCode != null) body.put("reasonCode", reasonCode);
        post("/digests/" + digestId + "/decisions", "DECIDE_DIGEST_" + digestId, body,
                new TypeToken<ApiResponse<DigestDecisionResult>>() {}.getType(), callback);
    }

    @Override
    public void listTopics(Callback<List<Topic>> callback) {
        getPage(url("/topics"), new TypeToken<ApiResponse<CursorPage<Topic>>>() {}.getType(), callback);
    }

    @Override
    public void getTopic(long topicId, Callback<Topic> callback) {
        get(url("/topics/" + topicId), new TypeToken<ApiResponse<Topic>>() {}.getType(), callback);
    }

    @Override
    public void updateTopicProgress(long topicId, TopicProgress progress, Callback<Topic> callback) {
        Map<String, Object> body = new HashMap<>();
        body.put("progress", progress.name());
        mutate("PATCH", "/topics/" + topicId + "/progress", "UPDATE_TOPIC_PROGRESS_" + topicId,
                body, new TypeToken<ApiResponse<Topic>>() {}.getType(), callback);
    }

    @Override
    public void submitFeedback(long actionId, FeedbackAccuracy accuracy, boolean completed, String note,
                               Callback<Feedback> callback) {
        Map<String, Object> body = new HashMap<>();
        body.put("accuracy", accuracy.name());
        body.put("completed", completed);
        if (note != null) body.put("note", note);
        post("/actions/" + actionId + "/feedback", "SUBMIT_ACTION_FEEDBACK_" + actionId,
                body, new TypeToken<ApiResponse<Feedback>>() {}.getType(), callback);
    }

    @Override
    public void getHome(Callback<Home> callback) {
        get(url("/home"), new TypeToken<ApiResponse<Home>>() {}.getType(), callback);
    }

    private <T> void post(String path, String operation, Object body, Type type, Callback<T> callback) {
        mutate("POST", path, operation, body, type, callback);
    }

    private <T> void mutate(String method, String path, String operation, Object value, Type type, Callback<T> callback) {
        String bodyJson = gson.toJson(value);
        String keySlot = operation + ":" + sha256(bodyJson);
        String key = pendingKeys.getString(keySlot, null);
        if (key == null) {
            key = UUID.randomUUID().toString();
            pendingKeys.edit().putString(keySlot, key).apply();
        }
        RequestBody body = RequestBody.create(bodyJson, JSON);
        Request request = baseBuilder().url(url(path)).header("Idempotency-Key", key).method(method, body).build();
        execute(request, type, keySlot, callback);
    }

    private <T> void get(HttpUrl url, Type type, Callback<T> callback) {
        execute(baseBuilder().url(url).get().build(), type, null, callback);
    }

    private <T> void getPage(HttpUrl url, Type type, Callback<List<T>> callback) {
        execute(baseBuilder().url(url).get().build(), type, null, new Callback<CursorPage<T>>() {
            @Override public void onSuccess(CursorPage<T> value) {
                callback.onSuccess(value == null ? java.util.Collections.emptyList() : value.items);
            }
            @Override public void onError(String safeMessage) { callback.onError(safeMessage); }
        });
    }

    @SuppressWarnings("unchecked")
    private <T> void execute(Request request, Type type, String keySlot, Callback<T> callback) {
        client.newCall(request).enqueue(new okhttp3.Callback() {
            @Override public void onFailure(okhttp3.Call call, IOException e) {
                deliverError(callback, "网络连接失败，请稍后重试");
            }

            @Override public void onResponse(okhttp3.Call call, Response response) {
                try (Response closeable = response) {
                    if (response.body() == null) {
                        deliverError(callback, "服务器未返回数据");
                        return;
                    }
                    String json = response.body().string();
                    ApiResponse<T> envelope = gson.fromJson(json, type);
                    if (!response.isSuccessful() || envelope == null || envelope.getCode() != 200) {
                        String message = envelope == null || envelope.getMessage() == null
                                ? "请求失败（" + response.code() + "）" : envelope.getMessage();
                        deliverError(callback, message);
                        return;
                    }
                    if (keySlot != null) pendingKeys.edit().remove(keySlot).apply();
                    main.post(() -> callback.onSuccess(envelope.getData()));
                } catch (Exception ignored) {
                    deliverError(callback, "服务器数据格式暂时无法读取");
                }
            }
        });
    }

    private Request.Builder baseBuilder() {
        String token = TokenManager.getToken(context);
        return new Request.Builder()
                .header("Authorization", "Bearer " + (token == null ? "" : token))
                .header("Content-Type", "application/json");
    }

    private HttpUrl url(String path) {
        HttpUrl value = HttpUrl.parse(ApiConfig.API_COGNITION_BASE + path);
        if (value == null) throw new IllegalStateException("Invalid cognition URL");
        return value;
    }

    private <T> void deliverError(Callback<T> callback, String message) {
        main.post(() -> callback.onError(message));
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder();
            for (byte item : digest) out.append(String.format("%02x", item));
            return out.toString();
        } catch (Exception ignored) {
            return Integer.toHexString(value.hashCode());
        }
    }
}
