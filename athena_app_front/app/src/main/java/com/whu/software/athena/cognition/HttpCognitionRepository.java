package com.whu.software.athena.cognition;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.whu.software.athena.cognition.CognitionModels.*;
import com.whu.software.athena.config.ApiConfig;
import com.whu.software.athena.utils.TokenManager;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Collections;
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

/** HTTP implementation of the deployed Cognition Contract V1. */
public final class HttpCognitionRepository implements CognitionRepository {
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private final Context context;
    private final Gson gson = new Gson();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(ApiConfig.CONNECT_TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(ApiConfig.READ_TIMEOUT, TimeUnit.SECONDS)
            .writeTimeout(ApiConfig.WRITE_TIMEOUT, TimeUnit.SECONDS)
            .build();

    public HttpCognitionRepository(Context context) {
        this.context = context.getApplicationContext();
    }

    @Override public void createClue(ClueCreateRequest value, Callback<ClueCreateResult> callback) {
        post("/clues", value, ClueCreateResult.class, callback);
    }

    @Override public void deleteClue(String clueId, Callback<String> callback) {
        Request request = baseBuilder().url(url("/clues/" + clueId))
                .header("Idempotency-Key", UUID.randomUUID().toString()).delete().build();
        execute(request, String.class, callback);
    }

    @Override public void getInbox(Callback<Inbox> callback) {
        get("/inbox", Inbox.class, callback);
    }

    @Override public void listClues(ClueListView view, int page, int pageSize, Callback<Page<Clue>> callback) {
        HttpUrl target = url("/clues").newBuilder()
                .addQueryParameter("view", view.name())
                .addQueryParameter("page", String.valueOf(page))
                .addQueryParameter("pageSize", String.valueOf(pageSize)).build();
        getPage(target, new TypeToken<List<Clue>>() {}.getType(), callback);
    }

    @Override public void createDigestTask(List<String> clueIds, Callback<DigestTask> callback) {
        DigestTaskRequest value = new DigestTaskRequest();
        value.clueIds = clueIds == null ? Collections.emptyList() : clueIds;
        post("/digest-tasks", value, DigestTask.class, callback);
    }

    @Override public void getDigestTask(String taskId, Callback<DigestTask> callback) {
        get("/digest-tasks/" + taskId, DigestTask.class, callback);
    }

    @Override public void retryDigestTask(String taskId, Callback<DigestTask> callback) {
        post("/digest-tasks/" + taskId + "/retry", Collections.emptyMap(), DigestTask.class, callback);
    }

    @Override public void getDigest(String digestId, Callback<Digest> callback) {
        get("/digests/" + digestId, Digest.class, callback);
    }

    @Override public void listReadyDigests(int page, int pageSize, Callback<Page<Digest>> callback) {
        HttpUrl target = url("/digests").newBuilder()
                .addQueryParameter("status", DigestStatus.READY.name())
                .addQueryParameter("page", String.valueOf(page))
                .addQueryParameter("pageSize", String.valueOf(pageSize)).build();
        getPage(target, new TypeToken<List<Digest>>() {}.getType(), callback);
    }

    @Override public void decideDigest(String digestId, DigestDecision decision, String reason,
                                       int clientVersion, Callback<DigestDecisionResult> callback) {
        Map<String, Object> body = new HashMap<>();
        body.put("decision", decision.name());
        body.put("clientVersion", clientVersion);
        if (reason != null && !reason.trim().isEmpty()) body.put("reason", reason.trim());
        post("/digests/" + digestId + "/decision", body, DigestDecisionResult.class, callback);
    }

    @Override public void listTopics(int page, int pageSize, Callback<Page<Topic>> callback) {
        HttpUrl target = url("/topics").newBuilder()
                .addQueryParameter("page", String.valueOf(page))
                .addQueryParameter("pageSize", String.valueOf(pageSize)).build();
        getPage(target, new TypeToken<List<Topic>>() {}.getType(), callback);
    }

    @Override public void getTopic(String topicId, Callback<TopicDetail> callback) {
        get("/topics/" + topicId, TopicDetail.class, callback);
    }

    @Override public void submitFeedback(String actionId, String topicId, ActionFeedbackResult result,
                                         String note, String occurredAt, Callback<FeedbackResult> callback) {
        Map<String, Object> body = new HashMap<>();
        body.put("topicId", topicId);
        body.put("result", result.name());
        if (note != null && !note.trim().isEmpty()) body.put("note", note.trim());
        if (occurredAt != null && !occurredAt.trim().isEmpty()) body.put("occurredAt", occurredAt);
        post("/actions/" + actionId + "/feedback", body, FeedbackResult.class, callback);
    }

    @Override public void getHome(Callback<Home> callback) {
        get("/home", Home.class, callback);
    }

    private <T> void get(String path, Type type, Callback<T> callback) {
        execute(baseBuilder().url(url(path)).get().build(), type, callback);
    }

    private <T> void post(String path, Object value, Type type, Callback<T> callback) {
        RequestBody body = RequestBody.create(gson.toJson(value), JSON);
        Request request = baseBuilder().url(url(path))
                .header("Idempotency-Key", UUID.randomUUID().toString()).post(body).build();
        execute(request, type, callback);
    }

    private <T> void getPage(HttpUrl target, Type listType, Callback<Page<T>> callback) {
        Request request = baseBuilder().url(target).get().build();
        client.newCall(request).enqueue(new okhttp3.Callback() {
            @Override public void onFailure(okhttp3.Call call, IOException e) {
                deliverError(callback, "网络连接失败，请稍后重试");
            }
            @Override public void onResponse(okhttp3.Call call, Response response) {
                try (Response ignored = response) {
                    JsonObject envelope = parseEnvelope(response, callback);
                    if (envelope == null) return;
                    Page<T> result = new Page<>();
                    JsonElement data = envelope.get("data");
                    if (data != null && data.isJsonArray()) result.data = gson.fromJson(data, listType);
                    JsonElement total = envelope.get("total");
                    result.total = total == null || total.isJsonNull() ? result.data.size() : total.getAsLong();
                    main.post(() -> callback.onSuccess(result));
                } catch (Exception e) {
                    deliverError(callback, "服务端数据格式暂时无法读取");
                }
            }
        });
    }

    private <T> void execute(Request request, Type type, Callback<T> callback) {
        client.newCall(request).enqueue(new okhttp3.Callback() {
            @Override public void onFailure(okhttp3.Call call, IOException e) {
                deliverError(callback, "网络连接失败，请稍后重试");
            }
            @Override public void onResponse(okhttp3.Call call, Response response) {
                try (Response ignored = response) {
                    JsonObject envelope = parseEnvelope(response, callback);
                    if (envelope == null) return;
                    JsonElement data = envelope.get("data");
                    T value = data == null || data.isJsonNull() ? null : gson.fromJson(data, type);
                    main.post(() -> callback.onSuccess(value));
                } catch (Exception e) {
                    deliverError(callback, "服务端数据格式暂时无法读取");
                }
            }
        });
    }

    private <T> JsonObject parseEnvelope(Response response, Callback<T> callback) throws IOException {
        if (response.body() == null) {
            deliverError(callback, "服务端未返回数据");
            return null;
        }
        String json = response.body().string();
        JsonObject envelope;
        try {
            envelope = gson.fromJson(json, JsonObject.class);
        } catch (Exception e) {
            deliverError(callback, response.isSuccessful() ? "服务端数据格式暂时无法读取" : "请求失败（" + response.code() + "）");
            return null;
        }
        int code = envelope != null && envelope.has("code") ? envelope.get("code").getAsInt() : response.code();
        if (!response.isSuccessful() || code != 200) {
            String errorCode = null;
            String serverMessage = envelope != null && envelope.has("message")
                    && !envelope.get("message").isJsonNull() ? envelope.get("message").getAsString() : null;
            if (envelope != null && envelope.has("data") && envelope.get("data").isJsonObject()) {
                JsonObject data = envelope.getAsJsonObject("data");
                if (data.has("errorCode") && !data.get("errorCode").isJsonNull()) errorCode = data.get("errorCode").getAsString();
            }
            deliverError(callback, CognitionErrorMessages.toUserMessage(
                    response.code(), code, errorCode, serverMessage));
            return null;
        }
        return envelope;
    }

    private Request.Builder baseBuilder() {
        String token = TokenManager.getToken(context);
        Request.Builder builder = new Request.Builder().header("Accept", "application/json");
        if (token != null && !token.trim().isEmpty()) builder.header("Authorization", "Bearer " + token);
        return builder;
    }

    private HttpUrl url(String path) {
        HttpUrl value = HttpUrl.parse(ApiConfig.API_COGNITION_BASE + path);
        if (value == null) throw new IllegalStateException("Invalid cognition URL");
        return value;
    }

    private <T> void deliverError(Callback<T> callback, String message) {
        main.post(() -> callback.onError(message));
    }
}
