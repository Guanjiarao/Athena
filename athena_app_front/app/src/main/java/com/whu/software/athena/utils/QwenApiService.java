package com.whu.software.athena.utils;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.whu.software.athena.config.ApiConfig;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class QwenApiService {
    private static final String TAG = "QwenApiService";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final String API_QWEN_VL = ApiConfig.API_QWEN_VL;
    private static final String QWEN_API_KEY = ApiConfig.QWEN_API_KEY;
    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();

    public interface OnVisionAnalyzeListener {
        void onSuccess(String report);
        void onFailure(String errorMsg);
    }

    private static final String MEDICAL_REPORT_PROMPT =
            "你是一位专业的医学大语言模型专家。请分析这张用户提供的（且已经过匿名化隐私处理的）"
                    + "医疗化验单或健康报告单图片，识别核心指标，给出通俗易懂的专业解读和行动建议。"
                    + "排版要分段清晰，适合手机阅读。如发现图片模糊或信息不足，请如实说明。";

    private static final String GENERAL_MEDICAL_PROMPT =
            "请解析这张已脱敏的医疗/健康图片，提取关键信息，并给出通俗专业的解读。"
                    + "排版分段清晰，适合手机阅读。如图片模糊或信息不足，请如实说明。";

    public static void analyzeMedicalReport(String anonymizedImageUrl,
                                            OnVisionAnalyzeListener listener) {
        new Thread(() -> {
            try {
                JSONObject root = new JSONObject();
                root.put("model", "qwen-vl-max");

                JSONArray messages = new JSONArray();
                JSONObject message = new JSONObject();
                message.put("role", "user");

                JSONArray contentArray = new JSONArray();

                JSONObject imageObj = new JSONObject();
                imageObj.put("type", "image_url");
                JSONObject imageUrlObj = new JSONObject();
                imageUrlObj.put("url", anonymizedImageUrl);
                imageObj.put("image_url", imageUrlObj);

                JSONObject textObj = new JSONObject();
                textObj.put("type", "text");
                textObj.put("text", MEDICAL_REPORT_PROMPT);

                contentArray.put(imageObj);
                contentArray.put(textObj);
                message.put("content", contentArray);
                messages.put(message);
                root.put("messages", messages);

                RequestBody body = RequestBody.create(
                        root.toString(), MediaType.parse("application/json; charset=utf-8"));
                Request request = new Request.Builder()
                        .url(API_QWEN_VL)
                        .addHeader("Authorization", "Bearer " + QWEN_API_KEY)
                        .addHeader("Content-Type", "application/json")
                        .post(body)
                        .build();

                try (Response response = CLIENT.newCall(request).execute()) {
                    String responseBody = response.body() != null ? response.body().string() : "";

                    if (response.isSuccessful()) {
                        JSONObject resJson = new JSONObject(responseBody);
                        JSONArray choices = resJson.optJSONArray("choices");
                        if (choices != null && choices.length() > 0) {
                            String report = choices.getJSONObject(0)
                                    .optJSONObject("message")
                                    .optString("content");
                            MAIN.post(() -> listener.onSuccess(report));
                        } else {
                            MAIN.post(() -> listener.onFailure("解析报告失败：数据格式异常"));
                        }
                    } else {
                        Log.e(TAG, "医疗报告分析失败: " + responseBody);
                        MAIN.post(() -> listener.onFailure("服务器返回错误：" + response.code()));
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "医疗报告分析异常", e);
                MAIN.post(() -> listener.onFailure("网络异常或请求超时：" + e.getMessage()));
            }
        }).start();
    }

    public static void analyzeGeneralMedicalImage(String imageUrl,
                                                  OnVisionAnalyzeListener listener) {
        new Thread(() -> {
            try {
                JSONObject root = new JSONObject();
                root.put("model", "qwen-vl-max");

                JSONArray messages = new JSONArray();
                JSONObject message = new JSONObject();
                message.put("role", "user");

                JSONArray contentArray = new JSONArray();

                JSONObject imageObj = new JSONObject();
                imageObj.put("type", "image_url");
                JSONObject imageUrlObj = new JSONObject();
                imageUrlObj.put("url", imageUrl);
                imageObj.put("image_url", imageUrlObj);

                JSONObject textObj = new JSONObject();
                textObj.put("type", "text");
                textObj.put("text", GENERAL_MEDICAL_PROMPT);

                contentArray.put(imageObj);
                contentArray.put(textObj);
                message.put("content", contentArray);
                messages.put(message);
                root.put("messages", messages);

                RequestBody body = RequestBody.create(
                        root.toString(), MediaType.parse("application/json; charset=utf-8"));
                Request request = new Request.Builder()
                        .url(API_QWEN_VL)
                        .addHeader("Authorization", "Bearer " + QWEN_API_KEY)
                        .addHeader("Content-Type", "application/json")
                        .post(body)
                        .build();

                try (Response response = CLIENT.newCall(request).execute()) {
                    String responseBody = response.body() != null ? response.body().string() : "";

                    if (response.isSuccessful()) {
                        JSONObject resJson = new JSONObject(responseBody);
                        JSONArray choices = resJson.optJSONArray("choices");
                        if (choices != null && choices.length() > 0) {
                            String report = choices.getJSONObject(0)
                                    .optJSONObject("message")
                                    .optString("content");
                            MAIN.post(() -> listener.onSuccess(report));
                        } else {
                            Log.e(TAG, "analyzeGeneralMedicalImage invalid choices: " + responseBody);
                            MAIN.post(() -> listener.onFailure("解析报告失败：数据格式异常"));
                        }
                    } else {
                        Log.e(TAG, "analyzeGeneralMedicalImage failed HTTP "
                                + response.code() + ": " + responseBody);
                        MAIN.post(() -> listener.onFailure("服务器返回错误：" + response.code()));
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "analyzeGeneralMedicalImage exception", e);
                MAIN.post(() -> listener.onFailure("网络异常或请求超时：" + e.getMessage()));
            }
        }).start();
    }

    public static void analyzeOvulationStrip(String imageUrl,
                                             OnVisionAnalyzeListener listener) {
        new Thread(() -> {
            try {
                JSONObject root = new JSONObject();
                root.put("model", "qwen3-vl-plus");

                JSONArray messages = new JSONArray();
                JSONObject message = new JSONObject();
                message.put("role", "user");

                JSONArray contentArray = new JSONArray();

                JSONObject imageObj = new JSONObject();
                imageObj.put("type", "image_url");
                JSONObject imageUrlObj = new JSONObject();
                imageUrlObj.put("url", imageUrl);
                imageObj.put("image_url", imageUrlObj);

                JSONObject textObj = new JSONObject();
                textObj.put("type", "text");
                textObj.put("text",
                        "作为专业的妇科AI助手，请仔细分析这张排卵试纸图片。识别对照线（C线）和测试线（T线）的显色清晰度与颜色深浅对比。"
                                + "判断LH促黄体生成素水平（如：阴性、弱阳性、阳性、强阳性或无效），"
                                + "并给出专业、温暖、积极的备孕指导建议。排版要分段清晰，适合手机阅读。");

                contentArray.put(imageObj);
                contentArray.put(textObj);
                message.put("content", contentArray);
                messages.put(message);
                root.put("messages", messages);

                RequestBody body = RequestBody.create(
                        root.toString(), MediaType.parse("application/json; charset=utf-8"));
                Request request = new Request.Builder()
                        .url(ApiConfig.API_QWEN_VL)
                        .addHeader("Authorization", "Bearer " + ApiConfig.QWEN_API_KEY)
                        .addHeader("Content-Type", "application/json")
                        .post(body)
                        .build();

                try (Response response = CLIENT.newCall(request).execute()) {
                    String responseBody = response.body() != null ? response.body().string() : "";

                    if (response.isSuccessful()) {
                        JSONObject resJson = new JSONObject(responseBody);
                        JSONArray choices = resJson.optJSONArray("choices");
                        if (choices != null && choices.length() > 0) {
                            String report = choices.getJSONObject(0)
                                    .optJSONObject("message")
                                    .optString("content");
                            MAIN.post(() -> listener.onSuccess(report));
                        } else {
                            MAIN.post(() -> listener.onFailure("解析报告失败：数据格式异常"));
                        }
                    } else {
                        Log.e(TAG, "request failed: " + responseBody);
                        MAIN.post(() -> listener.onFailure("服务器返回错误：" + response.code()));
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "analysis exception", e);
                MAIN.post(() -> listener.onFailure("网络异常或请求超时：" + e.getMessage()));
            }
        }).start();
    }
}
