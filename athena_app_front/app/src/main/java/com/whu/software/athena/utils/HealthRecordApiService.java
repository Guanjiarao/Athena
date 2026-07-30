package com.whu.software.athena.utils;

import android.content.Context;

import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.whu.software.athena.config.ApiConfig;
import com.whu.software.athena.entity.ApiResponse;
import com.whu.software.athena.entity.HealthRecordEntity;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Health record API wrapper aligned with the current backend CRUD contract.
 */
public final class HealthRecordApiService {

    private static final Gson GSON = new Gson();
    private static final MediaType JSON_MEDIA_TYPE =
            MediaType.parse("application/json; charset=utf-8");

    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .connectTimeout(ApiConfig.CONNECT_TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(ApiConfig.READ_TIMEOUT, TimeUnit.SECONDS)
            .writeTimeout(ApiConfig.WRITE_TIMEOUT, TimeUnit.SECONDS)
            .build();

    private HealthRecordApiService() {
    }

    public interface Callback<T> {
        void onSuccess(T data);

        void onError(String message);
    }

    public static void getDailyDetail(@NonNull Context context,
                                      @NonNull String date,
                                      @NonNull Callback<ApiResponse<List<HealthRecordEntity>>> callback) {
        HttpUrl url = HttpUrl.parse(ApiConfig.API_RECORD_DETAIL).newBuilder()
                .addQueryParameter("date", date)
                .build();
        Request request = baseBuilder(context).url(url).get().build();
        Type responseType = new TypeToken<ApiResponse<List<HealthRecordEntity>>>() {
        }.getType();
        execute(request, responseType, new Callback<ApiResponse<List<HealthRecordEntity>>>() {
            @Override
            public void onSuccess(ApiResponse<List<HealthRecordEntity>> data) {
                normalizeDailyDetailResponse(data);
                callback.onSuccess(data);
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    public static void createRecord(@NonNull Context context,
                                    @NonNull HealthRecordEntity entity,
                                    @NonNull Callback<ApiResponse<Object>> callback) {
        createRecord(context, toCreateBody(entity), callback);
    }

    public static void createRecord(@NonNull Context context,
                                    @NonNull Map<String, Object> bodyMap,
                                    @NonNull Callback<ApiResponse<Object>> callback) {
        RequestBody body = RequestBody.create(GSON.toJson(bodyMap), JSON_MEDIA_TYPE);
        Request request = baseBuilder(context)
                .url(ApiConfig.API_RECORD_SAVE)
                .post(body)
                .build();
        execute(request, new TypeToken<ApiResponse<Object>>() {
        }.getType(), callback);
    }

    public static void updateRecord(@NonNull Context context,
                                    @NonNull HealthRecordEntity entity,
                                    @NonNull Callback<ApiResponse<Object>> callback) {
        updateRecord(context, entity.getId(), toUpdateBody(entity), callback);
    }

    public static void updateRecord(@NonNull Context context,
                                    long id,
                                    @NonNull Map<String, Object> bodyMap,
                                    @NonNull Callback<ApiResponse<Object>> callback) {
        RequestBody body = RequestBody.create(GSON.toJson(bodyMap), JSON_MEDIA_TYPE);
        Request request = baseBuilder(context)
                .url(ApiConfig.API_RECORD_UPDATE + "/" + id)
                .put(body)
                .build();
        execute(request, new TypeToken<ApiResponse<Object>>() {
        }.getType(), callback);
    }

    public static void deleteRecord(@NonNull Context context,
                                    @NonNull HealthRecordEntity entity,
                                    @NonNull Callback<ApiResponse<Object>> callback) {
        deleteRecord(context, entity.getId(), callback);
    }

    public static void deleteRecord(@NonNull Context context,
                                    long id,
                                    @NonNull Callback<ApiResponse<Object>> callback) {
        Request request = baseBuilder(context)
                .url(ApiConfig.API_RECORD_DELETE + "/" + id)
                .delete()
                .build();
        execute(request, new TypeToken<ApiResponse<Object>>() {
        }.getType(), callback);
    }

    private static Map<String, Object> toCreateBody(@NonNull HealthRecordEntity entity) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("recordDate", entity.getRecordDate());
        body.put("modeType", HealthRecordModeMapper.toApiModeType(entity.getModeType()));
        body.put("recordItemId", entity.getRecordItemId());
        body.put("recordValue", entity.getRecordValue());
        return body;
    }

    private static Map<String, Object> toUpdateBody(@NonNull HealthRecordEntity entity) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("modeType", HealthRecordModeMapper.toApiModeType(entity.getModeType()));
        body.put("recordValue", entity.getRecordValue());
        return body;
    }

    private static void normalizeDailyDetailResponse(ApiResponse<List<HealthRecordEntity>> response) {
        if (response == null || response.getData() == null) {
            return;
        }
        for (HealthRecordEntity record : response.getData()) {
            if (record != null) {
                record.setModeType(HealthRecordModeMapper.toUiModeType(record.getModeType()));
            }
        }
    }

    private static Request.Builder baseBuilder(@NonNull Context context) {
        String token = TokenManager.getToken(context);
        return new Request.Builder()
                .addHeader("Authorization", "Bearer " + (token == null ? "" : token))
                .addHeader("Content-Type", "application/json");
    }

    private static <T> void execute(@NonNull Request request,
                                    @NonNull Type type,
                                    @NonNull Callback<T> callback) {
        new Thread(() -> {
            try (Response response = CLIENT.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    callback.onError("HTTP " + response.code());
                    return;
                }
                String json = response.body().string();
                T result = GSON.fromJson(json, type);
                callback.onSuccess(result);
            } catch (IOException e) {
                callback.onError(e.getMessage() == null ? "network error" : e.getMessage());
            } catch (Exception e) {
                callback.onError(e.getMessage() == null ? "parse error" : e.getMessage());
            }
        }).start();
    }
}
