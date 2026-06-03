package com.whu.software.athena.core;

import android.content.Context;
import android.util.Log;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class LLMClient {
    private static final String TAG = "LLMClient";
    private final OkHttpClient client;
    private Call currentCall;

    public interface LLMCallback {
        void onSuccess(String response);
        void onError(String error);
    }

    public LLMClient() {
        client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    public void getCompletion(Context context,
                              List<Message> messages,
                              boolean jsonMode,
                              LLMCallback callback) {
        getCompletion(messages, jsonMode, callback);
    }

    public void getCompletion(List<Message> messages,
                              boolean jsonMode,
                              LLMCallback callback) {
        JsonObject jsonBody = new JsonObject();
        jsonBody.addProperty("model", Config.MODEL);

        JsonArray messagesArray = new JsonArray();
        for (Message msg : messages) {
            if (msg == null) {
                continue;
            }
            JsonObject msgObj = new JsonObject();
            msgObj.addProperty("role", msg.getRole());
            msgObj.addProperty("content", msg.getContent());
            messagesArray.add(msgObj);
        }
        jsonBody.add("messages", messagesArray);

        if (jsonMode) {
            JsonObject format = new JsonObject();
            format.addProperty("type", "json_object");
            jsonBody.add("response_format", format);
        }

        RequestBody body = RequestBody.create(
                jsonBody.toString(),
                MediaType.get("application/json; charset=utf-8")
        );

        Request request = new Request.Builder()
                .url(Config.BASE_URL)
                .addHeader("Authorization", "Bearer " + Config.API_KEY)
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build();

        currentCall = client.newCall(request);
        currentCall.enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                clearCurrentCall(call);
                Log.e(TAG, "Request failed", e);
                callback.onError(e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                clearCurrentCall(call);
                try (Response responseToClose = response) {
                    if (!responseToClose.isSuccessful()) {
                        String errorBody = responseToClose.body() != null
                                ? responseToClose.body().string()
                                : "Unknown error";
                        Log.e(TAG, "Unsuccessful response: " + errorBody);
                        callback.onError("Error: " + responseToClose.code() + " " + errorBody);
                        return;
                    }

                    try {
                        String responseBody = responseToClose.body() != null
                                ? responseToClose.body().string()
                                : "";
                        String content = JsonParser.parseString(responseBody)
                                .getAsJsonObject()
                                .getAsJsonArray("choices")
                                .get(0).getAsJsonObject()
                                .getAsJsonObject("message")
                                .get("content").getAsString();
                        callback.onSuccess(content);
                    } catch (Exception e) {
                        Log.e(TAG, "Parsing failed", e);
                        callback.onError("Parsing Error: " + e.getMessage());
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

    private synchronized void clearCurrentCall(Call call) {
        if (currentCall == call) {
            currentCall = null;
        }
    }
}
