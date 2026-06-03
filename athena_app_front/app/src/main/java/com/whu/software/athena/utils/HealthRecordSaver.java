package com.whu.software.athena.utils;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import com.google.gson.Gson;
import com.whu.software.athena.config.ApiConfig;
import com.whu.software.athena.entity.HealthRecordEntity;

import org.json.JSONObject;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public final class HealthRecordSaver {

    private static final String TAG = "HealthRecordSaver";
    private static final String NET_TAG = "AthenaNet";
    private static final int RECORD_ITEM_PERIOD = 1;
    private static final String VALUE_YES = "\u662f";
    private static final String VALUE_NO = "\u5426";

    private static final OkHttpClient HTTP_CLIENT = new OkHttpClient.Builder()
            .connectTimeout(ApiConfig.CONNECT_TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(ApiConfig.READ_TIMEOUT, TimeUnit.SECONDS)
            .writeTimeout(ApiConfig.WRITE_TIMEOUT, TimeUnit.SECONDS)
            .build();

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Gson GSON = new Gson();

    private HealthRecordSaver() {
    }

    public interface OnRecordSavedListener {
        void onSaved();
    }

    public interface OnMenstruationPostedListener {
        void onResult(boolean success, @androidx.annotation.Nullable String message);
    }

    public static void savePeriodYes(Context ctx,
                                     int userId,
                                     String recordDate,
                                     int modeType,
                                     OnRecordSavedListener listener) {
        HealthRecordEntity entity = new HealthRecordEntity(
                userId, recordDate, modeType, RECORD_ITEM_PERIOD, VALUE_YES
        );
        save(ctx, entity, listener);
    }

    public static void savePeriodNo(Context ctx,
                                    int userId,
                                    String recordDate,
                                    int modeType,
                                    OnRecordSavedListener listener) {
        HealthRecordEntity entity = new HealthRecordEntity(
                userId, recordDate, modeType, RECORD_ITEM_PERIOD, VALUE_NO
        );
        save(ctx, entity, listener);
    }

    public static void saveGenericRecord(Context ctx,
                                         int userId,
                                         String recordDate,
                                         int modeType,
                                         int itemId,
                                         String value,
                                         OnRecordSavedListener listener) {
        HealthRecordEntity entity = new HealthRecordEntity(
                userId, recordDate, modeType, itemId, value
        );
        save(ctx, entity, listener);
    }

    /**
     * Dedicated POST /record save entry used by generic input sheets and quick record rows.
     */
    public static void postRecordSave(Context ctx,
                                      String date,
                                      int recordItemId,
                                      String recordValue,
                                      int modeType,
                                      OnRecordSavedListener listener) {
        HealthRecordEntity entity = new HealthRecordEntity(
                0, date, modeType, recordItemId, recordValue
        );
        save(ctx, entity, listener);
    }

    public static void save(Context ctx,
                            HealthRecordEntity entity,
                            OnRecordSavedListener listener) {
        new Thread(() -> {
            try {
                String token = TokenManager.getToken(ctx);
                if (token == null || token.trim().isEmpty()) {
                    MAIN.post(() -> Toast.makeText(
                            ctx,
                            "\u8bf7\u5148\u767b\u5f55\u540e\u518d\u8bd5",
                            Toast.LENGTH_SHORT
                    ).show());
                    return;
                }
                String json = GSON.toJson(buildCreatePayload(entity));

                RequestBody body = RequestBody.create(
                        json, MediaType.parse("application/json; charset=utf-8")
                );

                Request request = new Request.Builder()
                        .url(ApiConfig.API_RECORD_SAVE)
                        .addHeader("Authorization", "Bearer " + token)
                        .post(body)
                        .build();

                Log.d(NET_TAG, "\u5f00\u59cb\u8bf7\u6c42: /record Body: " + json);

                try (Response response = HTTP_CLIENT.newCall(request).execute()) {
                    String raw = response.body() != null ? response.body().string() : "";
                    Log.d(NET_TAG, "\u8bf7\u6c42\u7ed3\u679c: " + response.code() + " Body: " + raw);

                    if (response.isSuccessful()) {
                        int code = parseCode(raw);
                        if (code == 200) {
                            MAIN.post(() -> {
                                HealthSyncManager.markCycleMutation(ctx, entity.getRecordDate());
                                if (listener != null) {
                                    listener.onSaved();
                                }
                            });
                        } else {
                            String errorMessage = parseServerMessage(raw);
                            MAIN.post(() -> Toast.makeText(
                                    ctx,
                                    errorMessage.isEmpty()
                                            ? "\u4fdd\u5b58\u5931\u8d25\uff08code=" + code + "\uff09"
                                            : "\u4fdd\u5b58\u5931\u8d25\uff1a" + errorMessage,
                                    Toast.LENGTH_SHORT
                            ).show());
                        }
                    } else {
                        String errorMessage = parseServerMessage(raw);
                        MAIN.post(() -> Toast.makeText(
                                ctx,
                                errorMessage.isEmpty()
                                        ? "\u670d\u52a1\u5668\u5fd9\u7895\uff08HTTP " + response.code() + "\uff09\uff0c\u8bf7\u7a0d\u540e\u518d\u8bd5"
                                        : "\u4fdd\u5b58\u5931\u8d25\uff1a" + errorMessage,
                                Toast.LENGTH_SHORT
                        ).show());
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, "save record failed", e);
                MAIN.post(() -> Toast.makeText(
                        ctx,
                        "\u7f51\u7edc\u8fde\u63a5\u5931\u8d25\uff0c\u8bf7\u7a0d\u540e\u518d\u8bd5",
                        Toast.LENGTH_SHORT
                ).show());
            }
        }).start();
    }

    public static void postMenstruationStart(Context ctx, String startDate) {
        postMenstruationStart(ctx, startDate, null);
    }

    public static void postMenstruationStart(Context ctx,
                                             String startDate,
                                             OnMenstruationPostedListener listener) {
        postJson(ctx, ApiConfig.API_MENSTRUATION_START,
                "{\"startDate\":\"" + startDate + "\"}",
                "postMenstruationStart",
                listener);
    }

    public static void postMenstruationEnd(Context ctx, String endDate) {
        postMenstruationEnd(ctx, endDate, null);
    }

    public static void postMenstruationEnd(Context ctx,
                                           String endDate,
                                           OnMenstruationPostedListener listener) {
        postJson(ctx, ApiConfig.API_MENSTRUATION_END,
                "{\"endDate\":\"" + endDate + "\"}",
                "postMenstruationEnd",
                listener);
    }

    private static void postJson(Context ctx,
                                 String url,
                                 String jsonBody,
                                 String logTag,
                                 OnMenstruationPostedListener listener) {
        new Thread(() -> {
            try {
                String token = TokenManager.getToken(ctx);
                RequestBody body = RequestBody.create(
                        jsonBody, MediaType.parse("application/json; charset=utf-8")
                );
                Request request = new Request.Builder()
                        .url(url)
                        .addHeader("Authorization", "Bearer " + token)
                        .post(body)
                        .build();
                Log.d(NET_TAG, "\u5f00\u59cb\u8bf7\u6c42: " + request.url() + " Body: " + jsonBody);
                Log.d(TAG, logTag + " request queued url=" + url
                        + ", body=" + jsonBody
                        + ", thread=" + Thread.currentThread().getName());

                try (Response response = HTTP_CLIENT.newCall(request).execute()) {
                    String raw = response.body() != null ? response.body().string() : "";
                    Log.d(NET_TAG, "\u8bf7\u6c42\u7ed3\u679c: " + response.code() + " Body: " + raw);
                    boolean success = response.isSuccessful() && parseCode(raw) == 200;
                    Log.d(TAG, logTag + " response summary http=" + response.code()
                            + ", success=" + success
                            + ", raw=" + raw);
                    if (!success) {
                        Log.w(TAG, logTag + " failed, http=" + response.code() + ", body=" + raw);
                    }
                    if (listener != null) {
                        MAIN.post(() -> {
                            Log.d(TAG, logTag + " callback dispatch success=" + success
                                    + ", body=" + raw);
                            if (success) {
                                HealthSyncManager.markCycleMutation(
                                        ctx,
                                        logTag.equals("postMenstruationStart") ? startDateFromBody(jsonBody) : startDateFromBody(jsonBody)
                                );
                            }
                            listener.onResult(success, parseServerMessage(raw));
                        });
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, logTag + " exception", e);
                if (listener != null) {
                    MAIN.post(() -> listener.onResult(false, e.getMessage()));
                }
            }
        }).start();
    }

    private static int parseCode(String json) {
        try {
            return new JSONObject(json).optInt("code", -1);
        } catch (Exception e) {
            return -1;
        }
    }

    private static String parseServerMessage(String json) {
        if (json == null || json.trim().isEmpty()) {
            return "";
        }
        try {
            JSONObject root = new JSONObject(json);
            String message = root.optString("message", "");
            if (message != null && !message.trim().isEmpty()) {
                return message.trim();
            }
            String error = root.optString("error", "");
            return error != null ? error.trim() : "";
        } catch (Exception e) {
            return "";
        }
    }

    private static Map<String, Object> buildCreatePayload(HealthRecordEntity entity) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("recordDate", entity.getRecordDate());
        payload.put("modeType", entity.getModeType());
        payload.put("recordItemId", entity.getRecordItemId());
        payload.put("recordValue", entity.getRecordValue());
        return payload;
    }

    private static String startDateFromBody(String jsonBody) {
        try {
            JSONObject root = new JSONObject(jsonBody);
            String startDate = root.optString("startDate", "");
            if (startDate != null && !startDate.trim().isEmpty()) {
                return startDate.trim();
            }
            String endDate = root.optString("endDate", "");
            return endDate == null ? "" : endDate.trim();
        } catch (Exception ignored) {
            return "";
        }
    }
}
