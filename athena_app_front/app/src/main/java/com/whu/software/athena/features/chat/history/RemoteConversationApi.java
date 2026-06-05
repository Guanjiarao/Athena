package com.whu.software.athena.features.chat.history;

import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.whu.software.athena.config.ApiConfig;
import com.whu.software.athena.core.Message;
import com.whu.software.athena.utils.TokenManager;
import com.whu.software.athena.utils.UnsafeOkHttpClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class RemoteConversationApi {

    private static final MediaType JSON_MEDIA_TYPE =
            MediaType.get("application/json; charset=utf-8");

    public interface DataCallback<T> {
        void onSuccess(T data);

        void onError(String error);
    }

    public interface ActionCallback {
        void onSuccess();

        void onError(String error);
    }

    private final OkHttpClient client = UnsafeOkHttpClient.getUnsafeOkHttpClient();

    public void getConversationList(@NonNull Context context,
                                    @NonNull DataCallback<List<ConversationSummary>> callback) {
        Request request = authorizedBuilder(context, ApiConfig.API_CONVERSATIONS)
                .get()
                .build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                callback.onError(e.getMessage());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try (Response ignored = response) {
                    String body = response.body() != null ? response.body().string() : "";
                    if (!response.isSuccessful()) {
                        callback.onError(extractError(body, response.code()));
                        return;
                    }
                    callback.onSuccess(parseConversationSummaries(body));
                } catch (Exception exception) {
                    callback.onError(exception.getMessage());
                }
            }
        });
    }

    public void getConversationMessages(@NonNull Context context,
                                        @NonNull String conversationId,
                                        @NonNull DataCallback<List<Message>> callback) {
        Request request = authorizedBuilder(
                context,
                ApiConfig.API_CONVERSATIONS + "/" + conversationId + "/messages"
        ).get().build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                callback.onError(e.getMessage());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try (Response ignored = response) {
                    String body = response.body() != null ? response.body().string() : "";
                    if (!response.isSuccessful()) {
                        callback.onError(extractError(body, response.code()));
                        return;
                    }
                    callback.onSuccess(parseConversationMessages(body));
                } catch (Exception exception) {
                    callback.onError(exception.getMessage());
                }
            }
        });
    }

    public void renameConversation(@NonNull Context context,
                                   @NonNull String conversationId,
                                   @NonNull String title,
                                   @NonNull ActionCallback callback) {
        JSONObject bodyJson = new JSONObject();
        try {
            bodyJson.put("title", title);
        } catch (Exception ignored) {
        }
        RequestBody body = RequestBody.create(bodyJson.toString(), JSON_MEDIA_TYPE);
        Request request = authorizedBuilder(context, ApiConfig.API_CONVERSATIONS + "/" + conversationId)
                .put(body)
                .build();
        executeAction(request, callback);
    }

    public void deleteConversation(@NonNull Context context,
                                   @NonNull String conversationId,
                                   @NonNull ActionCallback callback) {
        Request request = authorizedBuilder(context, ApiConfig.API_CONVERSATIONS + "/" + conversationId)
                .delete()
                .build();
        executeAction(request, callback);
    }

    private void executeAction(@NonNull Request request, @NonNull ActionCallback callback) {
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                callback.onError(e.getMessage());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try (Response ignored = response) {
                    String body = response.body() != null ? response.body().string() : "";
                    if (!response.isSuccessful()) {
                        callback.onError(extractError(body, response.code()));
                        return;
                    }

                    if (!TextUtils.isEmpty(body)) {
                        JSONObject root = new JSONObject(body);
                        String code = root.optString("code", "0");
                        if (!TextUtils.isEmpty(code) && !"0".equals(code)) {
                            callback.onError(root.optString("message", "request failed"));
                            return;
                        }
                    }
                    callback.onSuccess();
                } catch (Exception exception) {
                    callback.onError(exception.getMessage());
                }
            }
        });
    }

    @NonNull
    private Request.Builder authorizedBuilder(@NonNull Context context, @NonNull String url) {
        Request.Builder builder = new Request.Builder().url(url);
        String token = TokenManager.getToken(context);
        if (!TextUtils.isEmpty(token)) {
            builder.addHeader("Authorization", "Bearer " + token);
        }
        return builder;
    }

    @NonNull
    private List<ConversationSummary> parseConversationSummaries(@NonNull String body) throws Exception {
        JSONObject root = new JSONObject(body);
        ensureSuccess(root);
        JSONArray data = root.optJSONArray("data");
        List<ConversationSummary> result = new ArrayList<>();
        if (data == null) {
            return result;
        }
        for (int i = 0; i < data.length(); i++) {
            JSONObject item = data.optJSONObject(i);
            if (item == null) {
                continue;
            }
            String conversationId = item.optString("conversationId");
            if (TextUtils.isEmpty(conversationId)) {
                continue;
            }
            String title = item.optString("title");
            String lastTimeRaw = item.optString("lastTime");
            long sortTimeMillis = ConversationTimeFormatter.parseServerTimeToMillis(lastTimeRaw);
            result.add(new ConversationSummary(
                    conversationId,
                    TextUtils.isEmpty(title) ? "\u65B0\u5BF9\u8BDD" : title,
                    sortTimeMillis,
                    ConversationTimeFormatter.formatServerTime(lastTimeRaw)
            ));
        }
        result.sort((left, right) ->
                Long.compare(right.getSortTimeMillis(), left.getSortTimeMillis()));
        return result;
    }

    @NonNull
    private List<Message> parseConversationMessages(@NonNull String body) throws Exception {
        JSONObject root = new JSONObject(body);
        ensureSuccess(root);
        JSONArray data = root.optJSONArray("data");
        List<Message> result = new ArrayList<>();
        if (data == null) {
            return result;
        }
        for (int i = 0; i < data.length(); i++) {
            JSONObject item = data.optJSONObject(i);
            if (item == null) {
                continue;
            }
            String role = item.optString("role");
            String content = item.optString("content");
            if (TextUtils.isEmpty(role) || TextUtils.isEmpty(content)) {
                continue;
            }
            result.add(new Message(role, content));
        }
        return result;
    }

    private void ensureSuccess(@NonNull JSONObject root) throws Exception {
        String code = root.optString("code", "0");
        if (!TextUtils.isEmpty(code) && !"0".equals(code)) {
            throw new Exception(root.optString("message", "request failed"));
        }
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
