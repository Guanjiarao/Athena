package com.whu.software.athena;

import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.whu.software.athena.config.ApiConfig;
import com.whu.software.athena.utils.TokenManager;
import com.whu.software.athena.utils.UploadUtil;
import com.whu.software.athena.utils.UserDao;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * 发布动态页面
 * 支持多图选择、批量上传、发布文章
 */
public class PublishActivity extends AppCompatActivity {

    private static final String TAG = "PublishActivity";
    private static final int MAX_IMAGE_COUNT = 9;
    // 接口地址统一由 ApiConfig 管理
    private static final String API_FILE_UPLOAD = ApiConfig.API_FILE_UPLOAD;
    private static final String API_BLOG_SUBMIT = ApiConfig.API_BLOG_SUBMIT;

    private ImageView btnBack;
    private TextView btnPublish;
    private TextView tabPublishImage;
    private TextView tabPublishVideo;
    private EditText etTitle;
    private EditText etContent;
    private EditText etTopic;
    private RecyclerView rvImages;

    private ImageAdapter imageAdapter;
    private List<String> selectedImagePaths = new ArrayList<>();
    private ProgressDialog progressDialog;

    // 现代化的图片选择器
    private ActivityResultLauncher<PickVisualMediaRequest> pickMultipleMedia;

    // OkHttp 客户端（全局单例）
    private OkHttpClient okHttpClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 状态栏纯白 + 深色图标，消除紫色默认主题
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Window window = getWindow();
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            window.setStatusBarColor(android.graphics.Color.WHITE);
            window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }

        setContentView(R.layout.activity_publish);

        // 初始化 OkHttp 客户端（设置超时时间）
        okHttpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();

        initViews();
        initImagePicker();
        setupListeners();
    }

    /**
     * 初始化视图
     */
    private void initViews() {
        btnBack        = findViewById(R.id.btn_back);
        btnPublish     = findViewById(R.id.btn_publish);
        tabPublishImage = findViewById(R.id.tab_publish_image);
        tabPublishVideo = findViewById(R.id.tab_publish_video);
        etTitle        = findViewById(R.id.et_title);
        etContent      = findViewById(R.id.et_content);
        etTopic        = findViewById(R.id.et_topic);
        rvImages       = findViewById(R.id.rv_images);

        // 设置图片网格布局（3列）
        GridLayoutManager layoutManager = new GridLayoutManager(this, 3);
        rvImages.setLayoutManager(layoutManager);

        imageAdapter = new ImageAdapter();
        rvImages.setAdapter(imageAdapter);
    }

    /**
     * 初始化现代化的图片选择器
     */
    private void initImagePicker() {
        pickMultipleMedia = registerForActivityResult(
                new ActivityResultContracts.PickMultipleVisualMedia(MAX_IMAGE_COUNT),
                uris -> {
                    if (uris != null && !uris.isEmpty()) {
                        Log.d(TAG, "选择了 " + uris.size() + " 张图片");
                        handleSelectedImages(uris);
                    }
                }
        );
    }

    /**
     * 设置监听器
     */
    private void setupListeners() {
        btnBack.setOnClickListener(v -> finish());
        btnPublish.setOnClickListener(v -> handlePublish());

        // "发布视频" Tab 点击 → 无缝切换到视频发布页
        tabPublishVideo.setOnClickListener(v -> {
            Intent intent = new Intent(this, PublishVideoActivity.class);
            startActivity(intent);
            finish();
        });
    }

    /**
     * 处理选择的图片
     */
    private void handleSelectedImages(List<Uri> uris) {
        showLoading("正在处理图片...");

        new Thread(() -> {
            List<String> newPaths = new ArrayList<>();

            for (Uri uri : uris) {
                String path = getFilePathFromUri(uri);
                if (path != null) {
                    newPaths.add(path);
                }
            }

            runOnUiThread(() -> {
                dismissLoading();
                if (!newPaths.isEmpty()) {
                    selectedImagePaths.addAll(newPaths);
                    imageAdapter.notifyDataSetChanged();
                    Toast.makeText(this, "已添加 " + newPaths.size() + " 张图片", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "图片处理失败", Toast.LENGTH_SHORT).show();
                }
            });
        }).start();
    }

    /**
     * 从 Uri 获取真实文件路径
     */
    private String getFilePathFromUri(Uri uri) {
        try {
            // 方案1：尝试从 MediaStore 获取路径
            String path = getRealPathFromURI(uri);
            if (path != null && new File(path).exists()) {
                return path;
            }

            // 方案2：复制到缓存目录
            return copyUriToCache(uri);
        } catch (Exception e) {
            Log.e(TAG, "获取文件路径失败: " + e.getMessage());
            return null;
        }
    }

    /**
     * 从 MediaStore 获取真实路径
     */
    private String getRealPathFromURI(Uri uri) {
        String[] projection = {MediaStore.Images.Media.DATA};
        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(uri, projection, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
                return cursor.getString(columnIndex);
            }
        } catch (Exception e) {
            Log.e(TAG, "从 MediaStore 获取路径失败: " + e.getMessage());
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return null;
    }

    /**
     * 复制 Uri 内容到缓存目录
     */
    private String copyUriToCache(Uri uri) {
        try {
            ContentResolver resolver = getContentResolver();
            InputStream inputStream = resolver.openInputStream(uri);
            if (inputStream == null) return null;

            // 获取文件名
            String fileName = getFileName(uri);
            if (fileName == null) {
                fileName = "image_" + System.currentTimeMillis() + ".jpg";
            }

            // 创建缓存文件
            File cacheDir = new File(getCacheDir(), "images");
            if (!cacheDir.exists()) {
                cacheDir.mkdirs();
            }
            File cacheFile = new File(cacheDir, fileName);

            // 复制文件
            FileOutputStream outputStream = new FileOutputStream(cacheFile);
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }

            inputStream.close();
            outputStream.close();

            return cacheFile.getAbsolutePath();
        } catch (IOException e) {
            Log.e(TAG, "复制文件到缓存失败: " + e.getMessage());
            return null;
        }
    }

    /**
     * 获取文件名
     */
    private String getFileName(Uri uri) {
        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(uri, null, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (nameIndex != -1) {
                    return cursor.getString(nameIndex);
                }
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return null;
    }

    /**
     * 处理发布
     */
    private void handlePublish() {
        String title = etTitle.getText().toString().trim();
        String content = etContent.getText().toString().trim();

        // 验证输入
        if (TextUtils.isEmpty(title)) {
            Toast.makeText(this, "请输入标题", Toast.LENGTH_SHORT).show();
            etTitle.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(content)) {
            Toast.makeText(this, "请输入正文内容", Toast.LENGTH_SHORT).show();
            etContent.requestFocus();
            return;
        }

        // 如果有图片，先上传图片
        if (!selectedImagePaths.isEmpty()) {
            uploadImagesAndPublish(title, content);
        } else {
            // 没有图片，直接发布
            submitPostToServer(title, content, new ArrayList<>());
        }
    }

    /**
     * 上传图片并发布
     */
    private void uploadImagesAndPublish(String title, String content) {
        showLoading("正在上传图片，请稍候...");

        // 🌟 核心修复：在这里把 PublishActivity.this 传进去，满足新版接口的要求！
        UploadUtil.uploadMultipleImages(PublishActivity.this, selectedImagePaths, new UploadUtil.MultipleUploadCallback() {
            @Override
            public void onAllSuccess(java.util.List<String> imageUrls) {
                dismissLoading();
                Log.d(TAG, "所有图片上传成功，共 " + imageUrls.size() + " 张");
                // 拿到所有阿里云的图片 URL 后，打包提交给服务器
                submitPostToServer(title, content, imageUrls);
            }

            @Override
            public void onFailure(String errorMsg) {
                dismissLoading();
                Toast.makeText(PublishActivity.this, "图片上传失败: " + errorMsg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * 提交文章到服务器（修复 Lambda 变量不可变问题）
     * 接口地址：blog/submit
     * 请求方式：POST
     * 提交格式：Multipart/form-data
     */
    private void submitPostToServer(String title, String content, List<String> photoUrls) {
        // 读取话题输入（UI线程安全：调用方均在主线程触发）
        String topicName = (etTopic != null)
                ? etTopic.getText().toString().trim()
                : "";
        showLoading("正在发布...");

        // 1. 初始化基础变量（默认值，后续仅赋值一次，成为实质最终）
        String userId = "";
        UserDao userDao = new UserDao(this);
        try {
            userDao.open();
            String[] loginUser = userDao.getCurrentLoginUser();
            // 仅在获取到数据时赋值一次，后续不再修改
            if (loginUser != null && loginUser.length > 3) {
                userId = loginUser[3]; // index 3 = user_id
            }
        } catch (Exception e) {
            Log.e(TAG, "获取用户ID失败: " + e.getMessage());
        } finally {
            userDao.close();
        }

        // 2. 处理封面和图片列表（一次性赋值，成为实质最终）
        String coverUrl = "";
        List<String> imgUrls = new ArrayList<>();
        // 仅执行一次赋值逻辑，后续不再修改 coverUrl 和 imgUrls 引用
        if (photoUrls != null && !photoUrls.isEmpty()) {
            coverUrl = photoUrls.get(0);
            if (photoUrls.size() > 1) {
                imgUrls = photoUrls.subList(1, photoUrls.size());
            }
        }

        // 3. 定义临时不可变变量（兜底方案，确保 Lambda 中安全使用）
        final String finalUserId = userId;
        final String finalCoverUrl = coverUrl;
        final List<String> finalImgUrls = imgUrls;
        final String finalTopicName = topicName;

        // 4. 开启子线程执行网络请求（Lambda 中使用 final 临时变量）
        new Thread(() -> {
            try {
                // 构造 JSON 请求体：多图字段名必须为 imgUrls（与后端 / Apifox 一致）
                JSONArray imgUrlsArray = new JSONArray();
                for (String url : finalImgUrls) {
                    imgUrlsArray.put(url);
                }

                JSONObject jsonBody = new JSONObject();
                jsonBody.put("title", title);
                jsonBody.put("content", content);
                if (finalUserId != null && !finalUserId.trim().isEmpty()) {
                    try {
                        jsonBody.put("userId", Long.parseLong(finalUserId.trim()));
                    } catch (NumberFormatException e) {
                        jsonBody.put("userId", finalUserId);
                    }
                } else {
                    jsonBody.put("userId", JSONObject.NULL);
                }
                jsonBody.put("topicId", 0);
                jsonBody.put("topicName", finalTopicName);
                jsonBody.put("isTop", false);
                jsonBody.put("type", 1);
                jsonBody.put("coverUrl", finalCoverUrl);
                jsonBody.put("imgUrls", imgUrlsArray);
                jsonBody.put("videoUrl", "");
                jsonBody.put("visible", 1);

                // 获取本地Token并添加到请求头
                String token = TokenManager.getToken(PublishActivity.this);
                RequestBody requestBody = RequestBody.create(
                        jsonBody.toString(),
                        MediaType.parse("application/json; charset=utf-8")
                );
                Request request = new Request.Builder()
                        .url(API_BLOG_SUBMIT)
                        .addHeader("Authorization", "Bearer " + token)
                        .post(requestBody)
                        .build();

                Response response = okHttpClient.newCall(request).execute();

                // 检查响应头中的Token并更新本地
                String newToken = response.header("Authorization");
                if (newToken != null && !newToken.isEmpty()) {
                    TokenManager.updateToken(PublishActivity.this, finalUserId, newToken);
                }

                if (response.isSuccessful() && response.body() != null) {
                    String responseBody = response.body().string();
                    Log.d(TAG, "文章发布成功，响应：" + responseBody);
                    runOnUiThread(() -> {
                        dismissLoading();
                        Toast.makeText(PublishActivity.this, "发布成功！", Toast.LENGTH_LONG).show();
                        finish();
                    });
                } else {
                    String errorMsg = "发布失败，状态码：" + response.code();
                    Log.e(TAG, errorMsg);
                    runOnUiThread(() -> {
                        dismissLoading();
                        Toast.makeText(PublishActivity.this, errorMsg, Toast.LENGTH_SHORT).show();
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "发布异常: " + e.getMessage(), e);
                runOnUiThread(() -> {
                    dismissLoading();
                    Toast.makeText(PublishActivity.this, "发布失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    /**
     * 显示加载对话框
     */
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

    /**
     * 更新加载对话框消息
     */
    private void updateLoadingMessage(String message) {
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.setMessage(message);
        }
    }

    /**
     * 关闭加载对话框
     */
    private void dismissLoading() {
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
    }

    /**
     * 图片适配器
     */
    private class ImageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        private static final int VIEW_TYPE_IMAGE = 0;
        private static final int VIEW_TYPE_ADD = 1;

        @Override
        public int getItemViewType(int position) {
            if (position < selectedImagePaths.size()) {
                return VIEW_TYPE_IMAGE;
            } else {
                return VIEW_TYPE_ADD;
            }
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == VIEW_TYPE_IMAGE) {
                View view = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_publish_image, parent, false);
                return new ImageViewHolder(view);
            } else {
                View view = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_add_image, parent, false);
                return new AddViewHolder(view);
            }
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (holder instanceof ImageViewHolder) {
                ImageViewHolder imageHolder = (ImageViewHolder) holder;
                String imagePath = selectedImagePaths.get(position);

                // 使用 Glide 加载图片
                Glide.with(PublishActivity.this)
                        .load(new File(imagePath))
                        .centerCrop()
                        .into(imageHolder.ivImage);

                // 删除按钮
                imageHolder.ivDelete.setOnClickListener(v -> {
                    int pos = holder.getAdapterPosition();
                    if (pos != RecyclerView.NO_POSITION) {
                        selectedImagePaths.remove(pos);
                        notifyDataSetChanged();
                        Toast.makeText(PublishActivity.this, "已删除", Toast.LENGTH_SHORT).show();
                    }
                });

            } else if (holder instanceof AddViewHolder) {
                // 点击添加图片
                holder.itemView.setOnClickListener(v -> {
                    if (selectedImagePaths.size() >= MAX_IMAGE_COUNT) {
                        Toast.makeText(PublishActivity.this,
                                "最多只能选择 " + MAX_IMAGE_COUNT + " 张图片",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // 启动图片选择器
                    int remainingCount = MAX_IMAGE_COUNT - selectedImagePaths.size();
                    pickMultipleMedia.launch(new PickVisualMediaRequest.Builder()
                            .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                            .build());
                });
            }
        }

        @Override
        public int getItemCount() {
            // 如果未达到最大数量，显示添加按钮
            return selectedImagePaths.size() < MAX_IMAGE_COUNT ?
                    selectedImagePaths.size() + 1 : selectedImagePaths.size();
        }

        class ImageViewHolder extends RecyclerView.ViewHolder {
            ImageView ivImage;
            ImageView ivDelete;

            ImageViewHolder(@NonNull View itemView) {
                super(itemView);
                ivImage = itemView.findViewById(R.id.iv_image);
                ivDelete = itemView.findViewById(R.id.iv_delete);
            }
        }

        class AddViewHolder extends RecyclerView.ViewHolder {
            AddViewHolder(@NonNull View itemView) {
                super(itemView);
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        dismissLoading();
    }
}