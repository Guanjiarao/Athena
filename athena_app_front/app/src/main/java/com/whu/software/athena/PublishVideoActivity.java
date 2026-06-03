package com.whu.software.athena;

import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.drawable.Drawable;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.whu.software.athena.config.ApiConfig;
import com.whu.software.athena.utils.TokenManager;
import com.whu.software.athena.utils.UserDao;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * 发布视频页面
 */
public class PublishVideoActivity extends AppCompatActivity {

    private static final String TAG = "PublishVideoActivity";

    private ImageView btnBack;
    private TextView  btnPublish;
    private TextView  tabPublishImage;
    private TextView  tabPublishVideo;
    private EditText  etTitle;
    private EditText  etContent;

    private Uri       selectedVideoUri;
    private VideoView videoPreview;
    private View      layoutVideoPlaceholder;

    private ProgressDialog progressDialog;
    private OkHttpClient   okHttpClient;

    // 系统文件选择器，MIME type = video/*
    private final ActivityResultLauncher<String> pickVideoLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                Log.d(TAG, "pickVideoLauncher 回调, uri = " + uri);
                if (uri != null) {
                    selectedVideoUri = uri;
                    Log.d(TAG, "选中视频 Uri: " + selectedVideoUri);

                    // 隐藏占位布局（+ 号 + 文字）
                    layoutVideoPlaceholder.setVisibility(View.GONE);
                    Log.d(TAG, "隐藏占位布局 layoutVideoPlaceholder");

                    // 显示并播放视频
                    videoPreview.setVisibility(View.VISIBLE);
                    Log.d(TAG, "显示 videoPreview");
                    videoPreview.setVideoURI(uri);
                    videoPreview.setOnPreparedListener(mp -> {
                        mp.setLooping(true);
                        Log.d(TAG, "VideoView onPrepared, 开始循环播放");
                        videoPreview.start();
                    });
                } else {
                    Log.w(TAG, "未选择任何视频，uri 为 null");
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Log.d(TAG, "onCreate()");

        // 状态栏纯白 + 深色图标
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Window window = getWindow();
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            window.setStatusBarColor(Color.WHITE);
            window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }

        setContentView(R.layout.activity_publish_video);

        okHttpClient = new OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(120, TimeUnit.SECONDS)
                .build();
        Log.d(TAG, "OkHttpClient 初始化完成");

        initViews();
        setupListeners();
    }

    private void initViews() {
        btnBack                = findViewById(R.id.btn_back);
        btnPublish             = findViewById(R.id.btn_publish);
        tabPublishImage        = findViewById(R.id.tab_publish_image);
        tabPublishVideo        = findViewById(R.id.tab_publish_video);
        etTitle                = findViewById(R.id.et_title);
        etContent              = findViewById(R.id.et_content);
        videoPreview           = findViewById(R.id.video_preview);
        layoutVideoPlaceholder = findViewById(R.id.layout_video_placeholder);
        Log.d(TAG, "initViews 完成: "
                + "btnBack=" + btnBack
                + ", btnPublish=" + btnPublish
                + ", videoPreview=" + videoPreview
                + ", placeholder=" + layoutVideoPlaceholder);
    }

    private void setupListeners() {
        Log.d(TAG, "setupListeners()");
        btnBack.setOnClickListener(v -> finish());

        btnPublish.setOnClickListener(v -> {
            Log.d(TAG, "点击发布按钮");
            handlePublish();
        });

        // 点击"发布图文" Tab → 无缝切回图文发布页
        tabPublishImage.setOnClickListener(v -> {
            startActivity(new Intent(this, PublishActivity.class));
            finish();
        });

        // 点击视频占位区 → 拉起系统文件选择器
        findViewById(R.id.card_video_picker).setOnClickListener(v -> {
            Log.d(TAG, "点击视频占位区, 启动系统视频选择器");
            pickVideoLauncher.launch("video/*");
        });
    }

    // -----------------------------------------------------------------------
    // 发布主流程
    // -----------------------------------------------------------------------

    private void handlePublish() {
        Log.d(TAG, "handlePublish() 开始");

        // 1. 前置校验
        if (selectedVideoUri == null) {
            Log.w(TAG, "handlePublish: selectedVideoUri 为空");
            Toast.makeText(this, "请先添加视频", Toast.LENGTH_SHORT).show();
            return;
        }
        String title = etTitle.getText().toString().trim();
        if (TextUtils.isEmpty(title)) {
            Log.w(TAG, "handlePublish: 标题为空");
            Toast.makeText(this, "标题不能为空", Toast.LENGTH_SHORT).show();
            etTitle.requestFocus();
            return;
        }
        String content = etContent.getText().toString().trim();
        Log.d(TAG, "handlePublish 参数: title=" + title + ", content.length=" + content.length());

        // 2. 弹出 Loading
        showLoading("视频发布中，请耐心等待...");
        Log.d(TAG, "显示 Loading: 视频发布中，请耐心等待...");

        // 3. 在子线程执行：Uri → File → 上传视频 → 发帖
        new Thread(() -> {
            try {
                // 3-1. 提取视频第一帧作为封面
                Log.d(TAG, "步骤 3-1: 提取视频第一帧");
                Bitmap coverBitmap = extractVideoFrame(selectedVideoUri);
                if (coverBitmap == null) {
                    Log.e(TAG, "提取视频帧失败");
                    throw new IOException("提取视频封面失败");
                }
                Log.d(TAG, "提取视频帧成功, width=" + coverBitmap.getWidth() + ", height=" + coverBitmap.getHeight());

                // 3-2. 保存封面图片到缓存
                Log.d(TAG, "步骤 3-2: 保存封面图片");
                File coverFile = saveBitmapToFile(coverBitmap);
                if (coverFile == null || !coverFile.exists()) {
                    Log.e(TAG, "保存封面图片失败");
                    throw new IOException("保存封面图片失败");
                }

                // 3-3. Uri 转物理 File（ContentResolver 复制到 cache）
                Log.d(TAG, "步骤 3-3: Uri → File, uri=" + selectedVideoUri);
                File videoFile = copyUriToCache(selectedVideoUri);
                if (videoFile == null || !videoFile.exists()) {
                    Log.e(TAG, "copyUriToCache 返回空或文件不存在");
                    throw new IOException("视频文件读取失败");
                }
                Log.d(TAG, "缓存视频文件路径: " + videoFile.getAbsolutePath()
                        + ", size=" + videoFile.length());

                // 3-4. 上传视频文件，拿到 videoUrl
                Log.d(TAG, "步骤 3-4: 开始上传视频文件");
                String videoUrl = uploadVideoSync(videoFile);
                Log.d(TAG, "步骤 3-4: 上传视频完成, videoUrl=" + videoUrl);

                // 3-5. 上传封面图片，拿到 coverUrl
                Log.d(TAG, "步骤 3-5: 开始上传封面图片");
                String coverUrl = uploadImageSync(coverFile);
                Log.d(TAG, "步骤 3-5: 上传封面完成, coverUrl=" + coverUrl);

                // 3-6. 发布帖子
                Log.d(TAG, "步骤 3-6: 调用 submitPostSync 发布帖子");
                submitPostSync(title, content, videoUrl, coverUrl);

            } catch (Exception e) {
                Log.e(TAG, "发布失败: " + e.getMessage(), e);
                runOnUiThread(() -> {
                    dismissLoading();
                    Toast.makeText(this, "发布失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    // -----------------------------------------------------------------------
    // 步骤 3-1：Uri → 缓存 File
    // -----------------------------------------------------------------------

    private File copyUriToCache(Uri uri) {
        try {
            Log.d(TAG, "copyUriToCache 开始, uri=" + uri);
            ContentResolver resolver = getContentResolver();
            InputStream inputStream = resolver.openInputStream(uri);
            if (inputStream == null) {
                Log.e(TAG, "copyUriToCache: inputStream 为 null");
                return null;
            }

            File cacheFile = new File(getCacheDir(), "temp_upload_video.mp4");
            Log.d(TAG, "copyUriToCache: 目标缓存文件 = " + cacheFile.getAbsolutePath());
            FileOutputStream outputStream = new FileOutputStream(cacheFile);
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            inputStream.close();
            outputStream.close();
            Log.d(TAG, "copyUriToCache 完成, size=" + cacheFile.length());
            return cacheFile;
        } catch (IOException e) {
            Log.e(TAG, "复制视频到缓存失败: " + e.getMessage());
            return null;
        }
    }

    // -----------------------------------------------------------------------
    // 步骤 3-2：同步上传视频 → 返回 videoUrl
    // -----------------------------------------------------------------------

    private String uploadVideoSync(File videoFile) throws Exception {
        Log.d(TAG, "uploadVideoSync 开始, file=" + videoFile.getAbsolutePath()
                + ", size=" + videoFile.length());
        RequestBody fileBody = RequestBody.create(
                MediaType.parse("video/*"), videoFile);
        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", videoFile.getName(), fileBody)
                .build();

        String token = TokenManager.getToken(this);
        Log.d(TAG, "uploadVideoSync 使用 token 长度=" + (token != null ? token.length() : 0));
        Request request = new Request.Builder()
                .url(ApiConfig.API_FILE_UPLOAD)
                .addHeader("Authorization", "Bearer " + token)
                .post(requestBody)
                .build();

        Response response = okHttpClient.newCall(request).execute();
        Log.d(TAG, "uploadVideoSync HTTP 状态码 = " + response.code());
        if (response.body() == null) throw new IOException("上传视频：响应体为空");

        String responseStr = response.body().string();
        Log.d(TAG, "上传视频响应: " + responseStr);

        JSONObject json = new JSONObject(responseStr);
        int code = json.optInt("code", -1);
        Log.d(TAG, "uploadVideoSync 解析 code=" + code);
        if (code != 200) {
            throw new IOException("上传视频失败: " + json.optString("message", "未知错误"));
        }
        String videoUrl = json.optString("data", "");
        if (TextUtils.isEmpty(videoUrl)) {
            throw new IOException("上传视频：未获取到 videoUrl");
        }
        Log.d(TAG, "uploadVideoSync 成功, videoUrl=" + videoUrl);
        return videoUrl;
    }

    // -----------------------------------------------------------------------
    // 步骤 3-3：同步发帖
    // -----------------------------------------------------------------------

    private void submitPostSync(String title, String content, String videoUrl, String coverUrl) throws Exception {
        Log.d(TAG, "submitPostSync 开始, title=" + title
                + ", content.length=" + (content != null ? content.length() : 0)
                + ", videoUrl=" + videoUrl
                + ", coverUrl=" + coverUrl);
        // 获取 userId（与 PublishActivity 完全一致）
        String userId = "";
        UserDao userDao = new UserDao(this);
        try {
            userDao.open();
            String[] loginUser = userDao.getCurrentLoginUser();
            if (loginUser != null && loginUser.length > 3) {
                userId = loginUser[3]; // index 3 = user_id
            }
        } catch (Exception e) {
            Log.e(TAG, "获取用户ID失败: " + e.getMessage());
        } finally {
            userDao.close();
        }
        Log.d(TAG, "submitPostSync 获取到 userId=" + userId);
        final String finalUserId = userId;

        JSONObject jsonBody = new JSONObject();
        jsonBody.put("title", title);
        jsonBody.put("content", content);
        jsonBody.put("userId", finalUserId);
        jsonBody.put("topicId", 0);
        jsonBody.put("topicName", "");
        jsonBody.put("type", 2);          // 2 = 视频类型
        jsonBody.put("coverUrl", coverUrl);
        jsonBody.put("videoUrl", videoUrl);
        jsonBody.put("visible", 1);
        jsonBody.put("imgUrls", new JSONArray());
        Log.d(TAG, "submitPostSync JSON 请求体: " + jsonBody.toString());

        String token = TokenManager.getToken(this);
        Log.d(TAG, "submitPostSync 使用 token 长度=" + (token != null ? token.length() : 0));
        RequestBody requestBody = RequestBody.create(
            MediaType.parse("application/json; charset=utf-8"),
            jsonBody.toString());
        Request request = new Request.Builder()
                .url(ApiConfig.API_BLOG_SUBMIT)
                .addHeader("Authorization", "Bearer " + token)
                .post(requestBody)
                .build();

        Response response = okHttpClient.newCall(request).execute();
        Log.d(TAG, "submitPostSync HTTP 状态码 = " + response.code());

        // 刷新 Token
        String newToken = response.header("Authorization");
        if (newToken != null && !newToken.isEmpty()) {
            TokenManager.updateToken(this, finalUserId, newToken);
            Log.d(TAG, "submitPostSync 更新本地 token");
        }

        if (response.isSuccessful() && response.body() != null) {
            String responseStr = response.body().string();
            Log.d(TAG, "发帖成功，响应：" + responseStr);
            runOnUiThread(() -> {
                dismissLoading();
                Toast.makeText(this, "发布成功！", Toast.LENGTH_LONG).show();
                finish();
            });
        } else {
            throw new IOException("发帖失败，状态码：" + response.code());
        }
    }

    // -----------------------------------------------------------------------
    // 视频帧截取工具方法
    // -----------------------------------------------------------------------

    /**
     * 从视频中提取第一帧作为封面
     */
    private Bitmap extractVideoFrame(Uri videoUri) {
        MediaMetadataRetriever retriever = null;
        try {
            retriever = new MediaMetadataRetriever();
            retriever.setDataSource(this, videoUri);
            // 获取视频第一帧
            return retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
        } catch (Exception e) {
            Log.e(TAG, "提取视频帧失败: " + e.getMessage());
            return null;
        } finally {
            if (retriever != null) {
                try {
                    retriever.release();
                } catch (Exception e) {
                    Log.e(TAG, "释放 MediaMetadataRetriever 失败: " + e.getMessage());
                }
            }
        }
    }

    /**
     * 将 Bitmap 保存为文件
     */
    private File saveBitmapToFile(Bitmap bitmap) throws IOException {
        File cacheFile = new File(getCacheDir(), "video_cover.jpg");
        FileOutputStream outputStream = new FileOutputStream(cacheFile);
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream);
        outputStream.close();
        Log.d(TAG, "保存封面图片到: " + cacheFile.getAbsolutePath() + ", size=" + cacheFile.length());
        return cacheFile;
    }

    /**
     * 上传图片文件
     */
    private String uploadImageSync(File imageFile) throws Exception {
        Log.d(TAG, "uploadImageSync 开始, file=" + imageFile.getAbsolutePath()
                + ", size=" + imageFile.length());
        RequestBody fileBody = RequestBody.create(
                MediaType.parse("image/*"), imageFile);
        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", imageFile.getName(), fileBody)
                .build();

        String token = TokenManager.getToken(this);
        Log.d(TAG, "uploadImageSync 使用 token 长度=" + (token != null ? token.length() : 0));
        Request request = new Request.Builder()
                .url(ApiConfig.API_FILE_UPLOAD)
                .addHeader("Authorization", "Bearer " + token)
                .post(requestBody)
                .build();

        Response response = okHttpClient.newCall(request).execute();
        Log.d(TAG, "uploadImageSync HTTP 状态码 = " + response.code());
        if (response.body() == null) throw new IOException("上传图片：响应体为空");

        String responseStr = response.body().string();
        Log.d(TAG, "上传图片响应: " + responseStr);

        JSONObject json = new JSONObject(responseStr);
        int code = json.optInt("code", -1);
        Log.d(TAG, "uploadImageSync 解析 code=" + code);
        if (code != 200) {
            throw new IOException("上传图片失败: " + json.optString("message", "未知错误"));
        }
        String imageUrl = json.optString("data", "");
        if (TextUtils.isEmpty(imageUrl)) {
            throw new IOException("上传图片：未获取到 imageUrl");
        }
        Log.d(TAG, "uploadImageSync 成功, imageUrl=" + imageUrl);
        return imageUrl;
    }

    // -----------------------------------------------------------------------
    // Loading 工具方法
    // -----------------------------------------------------------------------

    private void showLoading(String message) {
        if (progressDialog == null) {
            progressDialog = new ProgressDialog(this);
            progressDialog.setCancelable(false);
        }
        progressDialog.setMessage(message);
        if (!progressDialog.isShowing()) {
            progressDialog.show();
        }
    }

    private void dismissLoading() {
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        dismissLoading();
    }
}
