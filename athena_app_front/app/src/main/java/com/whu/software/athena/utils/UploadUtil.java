package com.whu.software.athena.utils;

import android.content.Context; // 🌟 引入 Context
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.whu.software.athena.config.ApiConfig;

import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class UploadUtil {
    private static final String TAG = "UploadUtil";

    // 使用 UnsafeOkHttpClient 跳过 HTTPS 证书验证
    private static final OkHttpClient client = UnsafeOkHttpClient.getUnsafeOkHttpClient();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    // ================== 接口定义 ==================
    public interface UploadCallback {
        void onSuccess(String imageUrl);
        void onFailure(String errorMessage);
    }

    public interface MultipleUploadCallback {
        void onAllSuccess(List<String> imageUrls);
        void onFailure(String errorMessage);
    }

    // ================== 核心方法 ==================
    /**
     * 上传单个文件到服务器
     * @param context  上下文（用于获取 Token）
     * @param file     要上传的文件
     * @param callback 上传结果回调
     */
    // 🌟 核心修复：增加了 Context 参数
    public static void uploadFile(Context context, File file, UploadCallback callback) {
        if (file == null || !file.exists()) {
            Log.e(TAG, "文件不存在: " + (file != null ? file.getAbsolutePath() : "null"));
            mainHandler.post(() -> callback.onFailure("文件不存在"));
            return;
        }

        Log.d(TAG, "开始上传文件: " + file.getName() + ", 大小: " + file.length() + " bytes");
        Log.d(TAG, "上传接口: " + ApiConfig.API_FILE_UPLOAD);

        RequestBody fileBody = RequestBody.create(MediaType.parse("image/*"), file);
        MultipartBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", file.getName(), fileBody)
                .build();

        // 🌟 核心修复：获取本地存储的 Token
        String token = "";
        if (context != null) {
            token = TokenManager.getToken(context);
        }

        // 🌟 核心修复：在请求头里带上 Token 鉴权！
        Request request = new Request.Builder()
                .url(ApiConfig.API_FILE_UPLOAD)
                .addHeader("Authorization", "Bearer " + token)
                .post(requestBody)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "上传失败: " + file.getName() + ", 错误: " + e.getMessage(), e);
                mainHandler.post(() -> callback.onFailure("网络请求失败: " + e.getMessage()));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String responseStr = "";
                try {
                    if (response.body() != null) {
                        responseStr = response.body().string();
                    }

                    Log.d(TAG, "上传响应 [" + file.getName() + "] HTTP " + response.code());
                    Log.d(TAG, "响应内容: " + responseStr);

                    if (response.isSuccessful()) {
                        // 🌟 防御性解析：防止后端乱返回非 JSON 格式导致崩溃
                        JSONObject jsonObject = new JSONObject(responseStr);
                        int code = jsonObject.optInt("code", -1);
                        String message = jsonObject.optString("message", "");
                        String imageUrl = jsonObject.optString("data", "");

                        Log.d(TAG, "解析结果 - code: " + code + ", message: " + message + ", data: " + imageUrl);

                        if (code == 200 && !imageUrl.isEmpty()) {
                            Log.d(TAG, "上传成功: " + file.getName() + " -> " + imageUrl);
                            mainHandler.post(() -> callback.onSuccess(imageUrl));
                        } else {
                            String errorMsg = "上传失败: " + (message.isEmpty() ? "未获取到图片URL" : message);
                            Log.e(TAG, errorMsg);
                            mainHandler.post(() -> callback.onFailure(errorMsg));
                        }
                    } else {
                        String errorMsg = "服务器异常 HTTP " + response.code();
                        Log.e(TAG, errorMsg + ", 响应: " + responseStr);
                        mainHandler.post(() -> callback.onFailure(errorMsg));
                    }
                } catch (Exception e) {
                    // 即使后端返回脏数据，也不会崩溃，而是抛给回调处理
                    Log.e(TAG, "解析响应失败: " + e.getMessage() + ", 原始响应: " + responseStr, e);
                    mainHandler.post(() -> callback.onFailure("返回数据格式异常"));
                }
            }
        });
    }

    /**
     * 批量上传多张图片
     * @param context    上下文（用于获取 Token）
     * @param imagePaths 图片文件路径列表
     * @param callback   批量上传结果回调
     */
    // 🌟 核心修复：批量上传也同步增加了 Context 参数
    public static void uploadMultipleImages(Context context, List<String> imagePaths, MultipleUploadCallback callback) {
        if (imagePaths == null || imagePaths.isEmpty()) {
            Log.d(TAG, "图片列表为空，直接返回成功");
            mainHandler.post(() -> callback.onAllSuccess(new ArrayList<>()));
            return;
        }

        int totalCount = imagePaths.size();
        Log.d(TAG, "开始批量上传 " + totalCount + " 张图片");

        AtomicInteger completedCount = new AtomicInteger(0);
        AtomicInteger failedCount = new AtomicInteger(0);
        String[] urlsArray = new String[totalCount];

        for (int i = 0; i < totalCount; i++) {
            final int index = i;
            File file = new File(imagePaths.get(i));

            Log.d(TAG, "准备上传第 " + (index + 1) + "/" + totalCount + " 张: " + file.getName());

            // 🌟 核心修复：传入 context
            uploadFile(context, file, new UploadCallback() {
                @Override
                public void onSuccess(String imageUrl) {
                    urlsArray[index] = imageUrl;
                    Log.d(TAG, "第 " + (index + 1) + "/" + totalCount + " 张上传成功");
                    checkComplete();
                }

                @Override
                public void onFailure(String errorMessage) {
                    failedCount.incrementAndGet();
                    Log.e(TAG, "第 " + (index + 1) + "/" + totalCount + " 张上传失败: " + errorMessage);
                    checkComplete();
                }

                private void checkComplete() {
                    int finished = completedCount.incrementAndGet();
                    Log.d(TAG, "上传进度: " + finished + "/" + totalCount +
                            " (成功: " + (finished - failedCount.get()) + ", 失败: " + failedCount.get() + ")");

                    if (finished == totalCount) {
                        mainHandler.post(() -> {
                            if (failedCount.get() > 0) {
                                String errorMsg = failedCount.get() + " 张图片上传失败，请重试";
                                Log.e(TAG, "批量上传完成，但有失败: " + errorMsg);
                                callback.onFailure(errorMsg);
                            } else {
                                Log.d(TAG, "批量上传全部成功！");
                                callback.onAllSuccess(Arrays.asList(urlsArray));
                            }
                        });
                    }
                }
            });
        }
    }
}