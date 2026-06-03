package com.whu.software.athena.net;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.annotation.NonNull;
import org.json.JSONObject;
import java.io.IOException;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Headers;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import com.whu.software.athena.config.ApiConfig;
import com.whu.software.athena.utils.TokenManager;


public class FollowRequestManager {
    // ???????
    private static FollowRequestManager instance;
    private final OkHttpClient okHttpClient;
    private final Context mContext;
    // ????????ApiConfig??????????

    // ?????????
    private FollowRequestManager(Context context) {
        this.mContext = context.getApplicationContext();
        this.okHttpClient = new OkHttpClient.Builder()
                .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .writeTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .build();
    }

    // ???????
    public static FollowRequestManager getInstance(Context context) {
        if (instance == null) {
            synchronized (FollowRequestManager.class) {
                if (instance == null) {
                    instance = new FollowRequestManager(context);
                }
            }
        }
        return instance;
    }

    /**
     * ?????????????????????????

     */
    public void requestBlogDetail(String tempBlogId, final BlogDetailCallback callback) {
        // 1. ???????????????????
        MediaType JSON = MediaType.parse("application/json; charset=utf-8");
        JSONObject requestJson = new JSONObject();
        try {
            requestJson.put("tempBlogId", tempBlogId);
        } catch (Exception e) {
            e.printStackTrace();
            if (callback != null) {
                callback.onFailure("???????????");
            }
            return;
        }

        // 2. ???????????header????????????
        Request request = new Request.Builder()
                .url(ApiConfig.API_BLOG_DETAIL)
                .addHeader("Authorization", "Bearer " + getToken()) // ????????????token
                .addHeader("deviceId", getDeviceId()) // ???????????????ID
                .addHeader("appVersion", getAppVersion()) // ????????????????
                .post(RequestBody.create(JSON, requestJson.toString()))
                .build();

        // 3. ????????????????
        okHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (callback != null) {
                        callback.onFailure("????????????" + e.getMessage());
                    }
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (!response.isSuccessful()) {
                        if (callback != null) {
                            callback.onFailure("????????" + response.code());
                        }
                        return;
                    }

                    try {
                        // 4. ?????????????????????????
                        String responseStr = response.body().string();
                        JSONObject responseJson = new JSONObject(responseStr);
                        int code = responseJson.getInt("code");
                        if (code == 200) {
                            // ????????????????????blogId??userId
                            String blogId = responseJson.getJSONObject("data").getString("blogId");
                            String userId = responseJson.getJSONObject("data").getString("userId");

                            // ???????????????????
                            if (callback != null) {
                                callback.onSuccess(blogId, userId, responseJson.getJSONObject("data"));
                            }

                            // 5. ?????????????????????????blogId??
                            requestBlogLikeStatus(blogId, new LikeStatusCallback() {
                                @Override
                                public void onSuccess(boolean isLiked) {
                                    if (callback != null) {
                                        callback.onLikeStatusLoaded(isLiked);
                                    }
                                }

                                @Override
                                public void onFailure(String errorMsg) {
                                    if (callback != null) {
                                        callback.onLikeStatusLoadFailed(errorMsg);
                                    }
                                }
                            });

                            // 6. ????????????????????????userId??
                            requestUserFollowStatus(userId, new FollowStatusCallback() {
                                @Override
                                public void onSuccess(boolean isFollowed) {
                                    if (callback != null) {
                                        callback.onFollowStatusLoaded(isFollowed);
                                    }
                                }

                                @Override
                                public void onFailure(String errorMsg) {
                                    if (callback != null) {
                                        callback.onFollowStatusLoadFailed(errorMsg);
                                    }
                                }
                            });
                        } else {
                            if (callback != null) {
                                callback.onFailure("???????" + responseJson.getString("msg"));
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        if (callback != null) {
                            callback.onFailure("????????" + e.getMessage());
                        }
                    }
                });
            }
        });
    }

    /**
     * ??????????????????????GET????
     * @param blogId ????ID??????????????
     * @param callback ?????????
     */
    public void requestBlogLikeStatus(String blogId, final LikeStatusCallback callback) {
        // 1. ????????URL???????????
        String url = ApiConfig.API_BLOG_LIKE_STATUS + "?blogId=" + blogId;

        // 2. ????????header??????????????
        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + getToken()) // ????????????header
                .addHeader("deviceId", getDeviceId())
                .addHeader("appVersion", getAppVersion())
                .get() // ???GET????
                .build();

        // 3. ????????
        okHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (callback != null) {
                        callback.onFailure("??????????????" + e.getMessage());
                    }
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (!response.isSuccessful()) {
                        if (callback != null) {
                            callback.onFailure("??????????????" + response.code());
                        }
                        return;
                    }

                    try {
                        String responseStr = response.body().string();
                        JSONObject responseJson = new JSONObject(responseStr);
                        int code = responseJson.getInt("code");
                        if (code == 200) {
                            // ???????????true=??????false=???????
                            boolean isLiked = responseJson.getBoolean("data");
                            Log.d("FollowRequestManager", "[点赞状态] ✅ 点赞状态已返回，状态: " + isLiked);
                            if (callback != null) {
                                callback.onSuccess(isLiked);
                            }
                        } else {
                            if (callback != null) {
                                callback.onFailure("?????????????" + responseJson.getString("msg"));
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        if (callback != null) {
                            callback.onFailure("??????????????" + e.getMessage());
                        }
                    }
                });
            }
        });
    }

    /**
     * ?????????????????????GET????
     * @param userId ????ID??????????????
     * @param callback ????????
     */
    public void requestUserFollowStatus(String userId, final FollowStatusCallback callback) {
        // 1. ????????URL???????????
        String url = ApiConfig.API_USER_FOLLOW_STATUS + "?followUserId=" + userId;

        // 2. ????????header????????????????
        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + getToken()) // ?????????header
                .addHeader("deviceId", getDeviceId())
                .addHeader("appVersion", getAppVersion())
                .get() // ???GET????
                .build();

        // 3. ????????
        okHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (callback != null) {
                        callback.onFailure("?????????????" + e.getMessage());
                    }
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (!response.isSuccessful()) {
                        if (callback != null) {
                            callback.onFailure("?????????????" + response.code());
                        }
                        return;
                    }

                    try {
                        String responseStr = response.body().string();
                        JSONObject responseJson = new JSONObject(responseStr);
                        int code = responseJson.getInt("code");
                        if (code == 200) {
                            // ???????????true=??????false=???????
                            boolean isFollowed = responseJson.getBoolean("data");
                            if (callback != null) {
                                callback.onSuccess(isFollowed);
                            }
                        } else {
                            if (callback != null) {
                                callback.onFailure("????????????" + responseJson.getString("msg"));
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        if (callback != null) {
                            callback.onFailure("?????????????" + e.getMessage());
                        }
                    }
                });
            }
        });
    }
    // FollowRequestManager.java ???????????????
// ???????/???????????

    /**
     * ???/?????????
     * @param followUserId ????ID
     * @param isFollow true=?????false=??????
     * @param callback ??????????
     */
    public void toggleUserFollow(String followUserId, boolean isFollow, final FollowToggleCallback callback) {
        // 1. ????????URL???????????
        String url = isFollow ? ApiConfig.API_USER_FOLLOW : ApiConfig.API_USER_UNFOLLOW;
        url += "?followUserId=" + followUserId;

        // 2. ????????????????????????Header??
        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + getToken())
                .addHeader("deviceId", getDeviceId())
                .addHeader("appVersion", getAppVersion())
                .post(RequestBody.create(null, new byte[0])) // ??????POST??????
                .build();

        // 3. ????????
        okHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (callback != null) {
                        callback.onFailure("???????????" + e.getMessage());
                    }
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (!response.isSuccessful()) {
                        if (callback != null) {
                            callback.onFailure("???????????" + response.code());
                        }
                        return;
                    }

                    try {
                        String responseStr = response.body().string();
                        JSONObject responseJson = new JSONObject(responseStr);
                        int code = responseJson.getInt("code");
                        if (code == 200) {
                            // ????????????????????
                            if (callback != null) {
                                callback.onSuccess(isFollow);
                            }
                        } else {
                            if (callback != null) {
                                callback.onFailure("??????????" + responseJson.getString("msg"));
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        // ???????????????????????????????????????????
                        if (callback != null) {
                            callback.onSuccess(isFollow);
                        }
                    }
                });
            }
        });
    }

    // ???????????????????? FollowRequestManager ????????????
    public interface FollowToggleCallback {
        // isFollow???????????????true=????????false=???????????
        void onSuccess(boolean isFollow);
        void onFailure(String errorMsg);
    }

    // ---------------- ?????????????????????header????????? ----------------
    /**
     * ???????????token???????????????
     */
    private String getToken() {
        return TokenManager.getToken(mContext);
    }

    /**
     * ??????ID???????????????
     */
    private String getDeviceId() {
        // ?????????????????????????????????ID
        return android.os.Build.SERIAL;
    }

    /**
     * ???APP???????????????????
     */
    private String getAppVersion() {
        try {
            return mContext.getPackageManager()
                    .getPackageInfo(mContext.getPackageName(), 0)
                    .versionName;
        } catch (Exception e) {
            return "1.0.0";
        }
    }


    /**
     * ????/?????????
     * @param blogId ????ID
     * @param isLike true=?????false=???????
     * @param callback ??????????
     */
    public void toggleBlogLike(String blogId, boolean isLike, final LikeToggleCallback callback) {
        // 1. ????????URL???????????
        String url = ApiConfig.API_BLOG_LIKE_TOGGLE + "?blogId=" + blogId;

        // 2. ????????????????????????Header??
        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + getToken())
                .addHeader("deviceId", getDeviceId())
                .addHeader("appVersion", getAppVersion())
                .post(RequestBody.create(null, new byte[0])) // ??????POST??????
                .build();

        // 3. ????????
        okHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (callback != null) {
                        callback.onFailure("????????????" + e.getMessage());
                    }
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (!response.isSuccessful()) {
                        if (callback != null) {
                            callback.onFailure("????????????" + response.code());
                        }
                        return;
                    }

                    try {
                        String responseStr = response.body().string();
                        JSONObject responseJson = new JSONObject(responseStr);
                        int code = responseJson.getInt("code");
                        if (code == 200) {
                            // ??????????????????????????????
                            int newLikeCount = 0;
                            try {
                                // ?????data?????likeCount
                                if (responseJson.has("data")) {
                                    JSONObject data = responseJson.getJSONObject("data");
                                    if (data.has("likeCount")) {
                                        newLikeCount = data.getInt("likeCount");
                                    }
                                }
                            } catch (Exception e) {
                                // ??????????????????0
                                e.printStackTrace();
                            }
                            if (callback != null) {
                                callback.onSuccess(isLike, newLikeCount);
                            }
                        } else {
                            if (callback != null) {
                                callback.onFailure("???????????" + responseJson.getString("msg"));
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        // ???????????????????????????????????????????
                        if (callback != null) {
                            callback.onSuccess(isLike, 0);
                        }
                    }
                });
            }
        });
    }

    /**
     * ???????????? GET????
     * @param blogId ????ID???????????????
     * @param callback ??????
     */
    public void requestBlogCollectStatus(String blogId, final CollectStatusCallback callback) {
        // 1. ????????URL????????
        String url = ApiConfig.API_BLOG_COLLECT_STATUS + "?blogId=" + blogId;

        // 2. ??????????????GET????
        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + getToken()) // Я??token??header??
                .addHeader("deviceId", getDeviceId())
                .addHeader("appVersion", getAppVersion())
                .get() // ???GET????
                .build();

        // 3. ???????
        okHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (callback != null) {
                        callback.onFailure("???????????:" + e.getMessage());
                    }
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (!response.isSuccessful()) {
                        if (callback != null) {
                            callback.onFailure("???????????:" + response.code());
                        }
                        return;
                    }

                    try {
                        String responseStr = response.body().string();
                        JSONObject responseJson = new JSONObject(responseStr);
                        int code = responseJson.getInt("code");
                        if (code == 200) {
                            // ????????????true=????? false=δ???
                            boolean isCollected = responseJson.getBoolean("data");
                            Log.d("FollowRequestManager", "[收藏状态] ✅ 收藏状态已返回，状态: " + isCollected);
                            if (callback != null) {
                                callback.onSuccess(isCollected);
                            }
                        } else {
                            if (callback != null) {
                                callback.onFailure("???????????:" + responseJson.getString("msg"));
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        if (callback != null) {
                            callback.onFailure("???????????:" + e.getMessage());
                        }
                    }
                });
            }
        });
    }

    /**
     * ???????/??????
     * @param blogId ????ID
     * @param isCollect true=??? false=??????
     * @param callback ??????
     */
    public void toggleBlogCollect(String blogId, boolean isCollect, final CollectToggleCallback callback) {
        // 1. ????????URL????????
        String url = ApiConfig.API_BLOG_COLLECT_TOGGLE + "?blogId=" + blogId;

        // 2. ??????????????????Header?
        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + getToken())
                .addHeader("deviceId", getDeviceId())
                .addHeader("appVersion", getAppVersion())
                .post(RequestBody.create(null, new byte[0])) // ?????????POST????
                .build();

        // 3. ???????
        okHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (callback != null) {
                        callback.onFailure("???????????" + e.getMessage());
                    }
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (!response.isSuccessful()) {
                        if (callback != null) {
                            callback.onFailure("???????????" + response.code());
                        }
                        return;
                    }

                    try {
                        String responseStr = response.body().string();
                        JSONObject responseJson = new JSONObject(responseStr);
                        int code = responseJson.getInt("code");
                        if (code == 200) {
                            // ????????????????????????
                            int newCollectCount = 0;
                            try {
                                // ?????data?л??collectCount
                                if (responseJson.has("data")) {
                                    JSONObject data = responseJson.getJSONObject("data");
                                    if (data.has("collectCount")) {
                                        newCollectCount = data.getInt("collectCount");
                                    }
                                }
                            } catch (Exception e) {
                                // ?????????????0
                                e.printStackTrace();
                            }
                            if (callback != null) {
                                callback.onSuccess(isCollect, newCollectCount);
                            }
                        } else {
                            if (callback != null) {
                                callback.onFailure("???????:" + responseJson.getString("msg"));
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        // ??????????????????????????????????
                        if (callback != null) {
                            callback.onSuccess(isCollect, 0);
                        }
                    }
                });
            }
        });
    }

    /**
     * 获取关注数量 GET请求
     * @param userId 用户ID，可选，当为null时查询当前用户
     * @param callback 回调接口
     */
    public void requestFollowCount(String userId, final FollowCountCallback callback) {
        // 1. 构建请求URL并拼接参数
        String url = ApiConfig.API_USER_FOLLOW_COUNT;
        if (userId != null && !userId.isEmpty()) {
            url += "?userId=" + userId;
        }

        // 2. 构建请求头并执行GET请求
        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + getToken()) // 携带token在header中
                .addHeader("deviceId", getDeviceId())
                .addHeader("appVersion", getAppVersion())
                .get() // 使用GET请求
                .build();

        // 3. 执行请求
        okHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (callback != null) {
                        callback.onFailure("网络请求失败:" + e.getMessage());
                    }
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (!response.isSuccessful()) {
                        if (callback != null) {
                            callback.onFailure("网络请求失败:" + response.code());
                        }
                        return;
                    }

                    try {
                        String responseStr = response.body().string();
                        JSONObject responseJson = new JSONObject(responseStr);
                        int code = responseJson.getInt("code");
                        if (code == 200) {
                            // 解析响应数据，获取关注数量
                            int count = responseJson.getInt("data");
                            if (callback != null) {
                                callback.onSuccess(count);
                            }
                        } else {
                            if (callback != null) {
                                callback.onFailure("获取关注数量失败:" + responseJson.getString("msg"));
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        if (callback != null) {
                            callback.onFailure("获取关注数量失败:" + e.getMessage());
                        }
                    }
                });
            }
        });
    }

    /**
     * 获取粉丝数量 GET请求
     * @param userId 用户ID，可选，当为null时查询当前用户
     * @param callback 回调接口
     */
    public void requestFanCount(String userId, final FanCountCallback callback) {
        // 1. 构建请求URL并拼接参数
        String url = ApiConfig.API_USER_FAN_COUNT;
        if (userId != null && !userId.isEmpty()) {
            url += "?userId=" + userId;
        }

        // 2. 构建请求头并执行GET请求
        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + getToken()) // 携带token在header中
                .addHeader("deviceId", getDeviceId())
                .addHeader("appVersion", getAppVersion())
                .get() // 使用GET请求
                .build();

        // 3. 执行请求
        okHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (callback != null) {
                        callback.onFailure("网络请求失败:" + e.getMessage());
                    }
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (!response.isSuccessful()) {
                        if (callback != null) {
                            callback.onFailure("网络请求失败:" + response.code());
                        }
                        return;
                    }

                    try {
                        String responseStr = response.body().string();
                        JSONObject responseJson = new JSONObject(responseStr);
                        int code = responseJson.getInt("code");
                        if (code == 200) {
                            // 解析响应数据，获取粉丝数量
                            int count = responseJson.getInt("data");
                            if (callback != null) {
                                callback.onSuccess(count);
                            }
                        } else {
                            if (callback != null) {
                                callback.onFailure("获取粉丝数量失败:" + responseJson.getString("msg"));
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        if (callback != null) {
                            callback.onFailure("获取粉丝数量失败:" + e.getMessage());
                        }
                    }
                });
            }
        });
    }

    /**
     * 获取用户详情 GET请求
     * @param userId 用户ID，可选，当为null时查询当前用户
     * @param callback 回调接口
     */
    public void requestUserInfo(String userId, final UserInfoCallback callback) {
        // 1. 构建请求URL并拼接参数
        String url = ApiConfig.API_USER_GET_INFO;
        if (userId != null && !userId.isEmpty()) {
            url += "?userId=" + userId;
        }

        // 2. 构建请求头并执行GET请求
        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + getToken()) // 携带token在header中
                .addHeader("deviceId", getDeviceId())
                .addHeader("appVersion", getAppVersion())
                .get() // 使用GET请求
                .build();

        // 3. 执行请求
        okHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (callback != null) {
                        callback.onFailure("网络请求失败:" + e.getMessage());
                    }
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (!response.isSuccessful()) {
                        if (callback != null) {
                            callback.onFailure("网络请求失败:" + response.code());
                        }
                        return;
                    }

                    try {
                        String responseStr = response.body().string();
                        JSONObject responseJson = new JSONObject(responseStr);
                        int code = responseJson.getInt("code");
                        if (code == 200) {
                            // 解析响应数据，获取用户信息
                            JSONObject userInfo = responseJson.getJSONObject("data");
                            if (callback != null) {
                                callback.onSuccess(userInfo);
                            }
                        } else {
                            if (callback != null) {
                                callback.onFailure("获取用户信息失败:" + responseJson.getString("msg"));
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        if (callback != null) {
                            callback.onFailure("获取用户信息失败:" + e.getMessage());
                        }
                    }
                });
            }
        });
    }

    // ???????????????????? FollowRequestManager ????????????
    public interface LikeToggleCallback {
        // isLike???????????????true=????????false=?????????????newLikeCount???????????
        void onSuccess(boolean isLike, int newLikeCount);
        void onFailure(String errorMsg);
    }

    // ---------------- ????????? ----------------
    /**
     * ????????????????????/???????
     */
    public interface BlogDetailCallback {
        // ????????????????blogId??userId??
        void onSuccess(String blogId, String userId, JSONObject data);
        // ???????????
        void onFailure(String errorMsg);
        // ????????????
        void onLikeStatusLoaded(boolean isLiked);
        // ?????????????
        void onLikeStatusLoadFailed(String errorMsg);
        // ???????????
        void onFollowStatusLoaded(boolean isFollowed);
        // ????????????
        void onFollowStatusLoadFailed(String errorMsg);
    }

    /**
     * ?????????
     */
    public interface LikeStatusCallback {
        void onSuccess(boolean isLiked);
        void onFailure(String errorMsg);
    }

    /**
     * ????????
     */
    public interface FollowStatusCallback {
        void onSuccess(boolean isFollowed);
        void onFailure(String errorMsg);
    }

    /**
     * ????????
     */
    public interface CollectStatusCallback {
        void onSuccess(boolean isCollected);
        void onFailure(String errorMsg);
    }

    /**
     * 收藏操作回调
     */
    public interface CollectToggleCallback {
        void onSuccess(boolean isCollect, int newCollectCount);
        void onFailure(String errorMsg);
    }

    /**
     * 关注数量回调
     */
    public interface FollowCountCallback {
        void onSuccess(int count);
        void onFailure(String errorMsg);
    }

    /**
     * 粉丝数量回调
     */
    public interface FanCountCallback {
        void onSuccess(int count);
        void onFailure(String errorMsg);
    }

    /**
     * 用户信息回调
     */
    public interface UserInfoCallback {
        void onSuccess(JSONObject userInfo);
        void onFailure(String errorMsg);
    }
}