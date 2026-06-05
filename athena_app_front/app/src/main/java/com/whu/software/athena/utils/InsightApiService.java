package com.whu.software.athena.utils;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.whu.software.athena.config.ApiConfig;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public final class InsightApiService {

    private static final String TAG = "InsightNet";

    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .connectTimeout(120, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .build();

    private static final MediaType JSON_MEDIA_TYPE =
            MediaType.parse("application/json; charset=utf-8");

    private InsightApiService() {
    }

    @Nullable
    public static InsightReportEntity getReportSync(@NonNull Context context) throws Exception {
        String token = TokenManager.getToken(context);

        HttpUrl baseUrl = HttpUrl.parse(ApiConfig.API_INSIGHT_REPORT);
        if (baseUrl == null) {
            throw new IOException("Invalid report url");
        }
        HttpUrl url = baseUrl.newBuilder()
                .addQueryParameter("pageNum", "1")
                .addQueryParameter("pageSize", "10")
                .build();

        Log.d(TAG, "开始请求: " + url + " Token: " + token);

        Request request = baseBuilder(token).url(url).get().build();
        try (Response response = CLIENT.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new IOException("HTTP " + response.code());
            }
            JSONObject root = new JSONObject(response.body().string());
            if (root.optInt("code", -1) != 200) {
                throw new IOException(root.optString("message", "request failed"));
            }
            return parseInsightReportEntity(root);
        }
    }

    @Nullable
    public static InsightData getInsightSync(@NonNull Context context) throws Exception {
        JSONObject data = getDataObject(context, ApiConfig.API_INSIGHT_CONTENT);
        if (data == null) {
            return null;
        }
        InsightData insight = new InsightData();
        insight.userId = optLong(data, "userId");
        insight.generatedAt = emptyToNull(data.optString("generatedAt", null));
        insight.healthFocuses = parseMaybeJsonArrayString(data.optString("healthFocusJson", null));
        insight.contentFocuses = parseMaybeJsonArrayString(data.optString("contentFocusJson", null));
        insight.riskTags = parseMaybeJsonArrayString(data.optString("riskTagsJson", null));
        insight.recommendationReasons = parseMaybeJsonArrayString(
                data.optString("recommendationReasonsJson", null)
        );
        return insight;
    }

    public static void refreshFeatureSync(@NonNull Context context) throws Exception {
        postEmptyJson(context, ApiConfig.API_INSIGHT_FEATURE_REFRESH);
    }

    public static void refreshInsightSync(@NonNull Context context) throws Exception {
        postEmptyJson(context, ApiConfig.API_INSIGHT_CONTENT_REFRESH);
    }

    @Nullable
    private static InsightReportEntity parseInsightReportEntity(@NonNull JSONObject root) {
        InsightReportEntity entity = new InsightReportEntity();
        entity.code = root.optInt("code", -1);
        entity.message = emptyToNull(root.optString("message", null));

        JSONObject dataObject = root.optJSONObject("data");
        if (dataObject != null) {
            entity.data = parseReportData(dataObject);
        }
        return entity;
    }

    @NonNull
    private static InsightReportEntity.ReportData parseReportData(@NonNull JSONObject data) {
        InsightReportEntity.ReportData report = new InsightReportEntity.ReportData();
        report.summary = emptyToNull(data.optString("summary", null));
        report.summarySource = emptyToNull(data.optString("summarySource", null));
        report.healthFocuses = parseStringList(data.optJSONArray("healthFocuses"));
        report.contentFocuses = parseStringList(data.optJSONArray("contentFocuses"));
        report.riskTags = parseStringList(data.optJSONArray("riskTags"));
        report.recommendTopics = parseStringList(data.optJSONArray("recommendTopics"));

        JSONArray suggestions = data.optJSONArray("readingSuggestions");
        if (suggestions != null) {
            for (int i = 0; i < suggestions.length(); i++) {
                JSONObject itemObject = suggestions.optJSONObject(i);
                if (itemObject != null) {
                    report.readingSuggestions.add(parseReadingSuggestion(itemObject));
                }
            }
        }
        return report;
    }

    @NonNull
    private static InsightReportEntity.ReadingSuggestion parseReadingSuggestion(@NonNull JSONObject object) {
        InsightReportEntity.ReadingSuggestion item = new InsightReportEntity.ReadingSuggestion();
        item.noteId = optLong(object, "noteId");
        item.type = optInt(object, "type");
        item.title = emptyToNull(object.optString("title", null));
        item.topics = parseStringList(object.optJSONArray("topics"));
        item.reason = emptyToNull(object.optString("reason", null));
        item.score = optDouble(object, "score");
        return item;
    }

    @Nullable
    private static JSONObject getDataObject(@NonNull Context context, @NonNull String url) throws Exception {
        Request request = baseBuilder(TokenManager.getToken(context)).url(url).get().build();
        try (Response response = CLIENT.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new IOException("HTTP " + response.code());
            }
            JSONObject root = new JSONObject(response.body().string());
            if (root.optInt("code", -1) != 200) {
                throw new IOException(root.optString("message", "request failed"));
            }
            Object data = root.opt("data");
            if (data instanceof JSONObject) {
                return (JSONObject) data;
            }
            return null;
        }
    }

    private static void postEmptyJson(@NonNull Context context, @NonNull String url) throws Exception {
        RequestBody body = RequestBody.create("{}", JSON_MEDIA_TYPE);
        Request request = baseBuilder(TokenManager.getToken(context)).url(url).post(body).build();
        try (Response response = CLIENT.newCall(request).execute()) {
            String raw = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                throw new IOException("HTTP " + response.code());
            }
            JSONObject root = new JSONObject(raw);
            if (root.optInt("code", -1) != 200) {
                throw new IOException(root.optString("message", "request failed"));
            }
        }
    }

    private static Request.Builder baseBuilder(@Nullable String token) {
        return new Request.Builder()
                .addHeader("Authorization", "Bearer " + (token == null ? "" : token))
                .addHeader("Content-Type", "application/json");
    }

    @NonNull
    private static List<String> parseStringList(@Nullable JSONArray array) {
        List<String> values = new ArrayList<>();
        if (array == null) {
            return values;
        }
        for (int i = 0; i < array.length(); i++) {
            Object value = array.opt(i);
            if (value instanceof JSONObject) {
                JSONObject object = (JSONObject) value;
                String text = firstNonEmpty(
                        object.optString("name", null),
                        object.optString("title", null),
                        object.optString("text", null),
                        object.optString("value", null)
                );
                if (!TextUtils.isEmpty(text)) {
                    values.add(text);
                }
            } else {
                String text = emptyToNull(array.optString(i, null));
                if (text != null) {
                    values.add(text);
                }
            }
        }
        return values;
    }

    @NonNull
    private static List<String> parseMaybeJsonArrayString(@Nullable String text) {
        if (TextUtils.isEmpty(text)) {
            return new ArrayList<>();
        }
        try {
            return parseStringList(new JSONArray(text));
        } catch (Exception ignored) {
            List<String> fallback = new ArrayList<>();
            String trimmed = emptyToNull(text);
            if (trimmed != null) {
                fallback.add(trimmed);
            }
            return fallback;
        }
    }

    private static int optInt(@NonNull JSONObject object, @NonNull String key) {
        if (!object.has(key) || object.isNull(key)) {
            return 0;
        }
        try {
            Object value = object.opt(key);
            if (value instanceof Number) {
                return ((Number) value).intValue();
            }
            String text = value == null ? "" : String.valueOf(value).trim();
            return TextUtils.isEmpty(text) ? 0 : Integer.parseInt(text);
        } catch (Exception ignored) {
            return 0;
        }
    }

    private static long optLong(@NonNull JSONObject object, @NonNull String key) {
        if (!object.has(key) || object.isNull(key)) {
            return 0L;
        }
        try {
            Object value = object.opt(key);
            if (value instanceof Number) {
                return ((Number) value).longValue();
            }
            String text = value == null ? "" : String.valueOf(value).trim();
            return TextUtils.isEmpty(text) ? 0L : Long.parseLong(text);
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private static double optDouble(@NonNull JSONObject object, @NonNull String key) {
        if (!object.has(key) || object.isNull(key)) {
            return 0d;
        }
        try {
            Object value = object.opt(key);
            if (value instanceof Number) {
                return ((Number) value).doubleValue();
            }
            String text = value == null ? "" : String.valueOf(value).trim();
            return TextUtils.isEmpty(text) ? 0d : Double.parseDouble(text);
        } catch (Exception ignored) {
            return 0d;
        }
    }

    @Nullable
    private static String emptyToNull(@Nullable String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    @NonNull
    private static String firstNonEmpty(@Nullable String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            String cleaned = emptyToNull(value);
            if (cleaned != null) {
                return cleaned;
            }
        }
        return "";
    }

    public static final class InsightData {
        @Nullable public Long userId;
        @NonNull public List<String> healthFocuses = new ArrayList<>();
        @NonNull public List<String> contentFocuses = new ArrayList<>();
        @NonNull public List<String> riskTags = new ArrayList<>();
        @NonNull public List<String> recommendationReasons = new ArrayList<>();
        @Nullable public String generatedAt;
    }
}
