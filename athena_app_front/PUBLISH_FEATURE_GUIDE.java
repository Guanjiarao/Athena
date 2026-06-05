/**
 * ========================================
 * Athena 发布动态功能完整指南
 * ========================================
 */

// ==================== 1. UploadUtil 使用示例 ====================

// 示例 1：上传单张图片
UploadUtil.uploadSingleImage("/sdcard/photo.jpg", new UploadUtil.SingleUploadCallback() {
    @Override
    public void onSuccess(String imageUrl) {
        // 已在主线程，可直接更新 UI
        Log.d(TAG, "图片上传成功: " + imageUrl);
        // 例如：更新头像
        // userAvatar.setImageUrl(imageUrl);
    }

    @Override
    public void onFailure(String errorMsg) {
        Toast.makeText(context, "上传失败: " + errorMsg, Toast.LENGTH_SHORT).show();
    }
});

// 示例 2：批量上传多张图片（发帖场景）
List<String> imagePaths = Arrays.asList(
    "/sdcard/photo1.jpg",
    "/sdcard/photo2.jpg",
    "/sdcard/photo3.jpg"
);

UploadUtil.uploadMultipleImages(imagePaths, new UploadUtil.MultipleUploadCallback() {
    @Override
    public void onSuccess(List<String> imageUrls) {
        // 所有图片上传成功
        Log.d(TAG, "全部上传成功，共 " + imageUrls.size() + " 张");
        // 可以提交文章了
        submitPost(title, content, imageUrls);
    }

    @Override
    public void onPartialSuccess(List<String> successUrls, int failureCount) {
        // 部分图片上传成功
        Toast.makeText(context, 
            "部分图片上传失败（" + failureCount + "张），是否继续？", 
            Toast.LENGTH_LONG).show();
    }

    @Override
    public void onFailure(String errorMsg) {
        // 全部上传失败
        Toast.makeText(context, "上传失败: " + errorMsg, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onProgress(int current, int total) {
        // 上传进度回调
        Log.d(TAG, "上传进度: " + current + "/" + total);
        progressDialog.setMessage("正在上传图片 (" + current + "/" + total + ")...");
    }
});

// 示例 3：AI 聊天传图场景
File imageFile = new File("/sdcard/ai_chat_image.jpg");
UploadUtil.uploadSingleImage(imageFile, new UploadUtil.SingleUploadCallback() {
    @Override
    public void onSuccess(String imageUrl) {
        // 将图片 URL 发送给 AI
        sendMessageToAI("请分析这张图片", imageUrl);
    }

    @Override
    public void onFailure(String errorMsg) {
        Toast.makeText(context, "图片上传失败", Toast.LENGTH_SHORT).show();
    }
});

// ==================== 2. PublishActivity 功能流程 ====================

/**
 * 用户操作流程：
 * 
 * 1. 点击广场页面左上角的 "+" 按钮
 *    -> SquareFragment 跳转到 PublishActivity
 * 
 * 2. 输入标题和正文
 *    -> EditText 输入内容
 * 
 * 3. 选择图片（最多 9 张）
 *    -> 点击 "+" 按钮
 *    -> 使用 ActivityResultContracts.PickMultipleVisualMedia()
 *    -> 系统相册选择器（支持多选）
 *    -> 自动转换 Uri 到真实文件路径
 * 
 * 4. 删除不需要的图片
 *    -> 点击图片右上角的 "×" 按钮
 * 
 * 5. 点击右上角 "发布" 按钮
 *    -> 验证标题和正文是否为空
 *    -> 显示 Loading 对话框
 *    -> 批量上传图片到阿里云 OSS
 *    -> 获取所有图片的 URL
 *    -> 组装 JSON 数据
 *    -> 提交到后端（目前为 Mock）
 *    -> 发布成功，返回广场页面
 */

// ==================== 3. 后端接口对接说明 ====================

/**
 * 当后端的"发布文章"接口定稿后，修改 PublishActivity.java 中的
 * submitPostToServer() 方法：
 */

private void submitPostToServer(String title, String content, List<String> photoUrls) {
    showLoading("正在发布...");

    // 组装 JSON 数据
    Map<String, Object> postData = new HashMap<>();
    postData.put("title", title);
    postData.put("content", content);
    postData.put("photo", photoUrls);
    // 根据后端要求添加其他字段，例如：
    // postData.put("user_id", getCurrentUserId());
    // postData.put("blog_type", "image");

    Gson gson = new Gson();
    String jsonData = gson.toJson(postData);

    new Thread(() -> {
        try {
            // 真实的 OkHttp 请求
            OkHttpClient client = new OkHttpClient();
            RequestBody requestBody = RequestBody.create(
                    MediaType.parse("application/json; charset=utf-8"),
                    jsonData
            );
            Request request = new Request.Builder()
                    .url(ApiConfig.BASE_URL + "com.whu.software.athena/blog/publish") // 替换为真实接口
                    .post(requestBody)
                    .build();

            Response response = client.newCall(request).execute();
            if (response.isSuccessful() && response.body() != null) {
                String responseStr = response.body().string();
                Log.d(TAG, "发布成功: " + responseStr);

                runOnUiThread(() -> {
                    dismissLoading();
                    Toast.makeText(this, "发布成功！", Toast.LENGTH_SHORT).show();
                    finish();
                });
            } else {
                throw new IOException("服务器返回错误: " + response.code());
            }

        } catch (Exception e) {
            Log.e(TAG, "发布失败: " + e.getMessage());
            runOnUiThread(() -> {
                dismissLoading();
                Toast.makeText(this, "发布失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            });
        }
    }).start();
}

// ==================== 4. 权限说明 ====================

/**
 * AndroidManifest.xml 中已添加的权限：
 * 
 * <uses-permission android:name="android.permission.INTERNET" />
 * <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"/>
 * <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"/>
 * <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
 * 
 * 注意：Android 13+ (API 33+) 需要使用新的权限模型：
 * <uses-permission android:name="android.permission.READ_MEDIA_IMAGES"/>
 * 
 * 但由于使用了 ActivityResultContracts.PickVisualMedia，
 * 系统会自动处理权限请求，无需手动申请。
 */

// ==================== 5. 配置说明 ====================

/**
 * 在 ApiConfig.java 中配置上传接口地址：
 * 
 * public static final String BASE_URL = "http://your-domain.com/";
 * public static final String API_FILE_UPLOAD = BASE_URL + "file/upload";
 * 
 * 确保后端接口：
 * - URL: POST /file/upload
 * - Content-Type: multipart/form-data
 * - 参数名: file
 * - 返回格式: {"url": "https://oss.aliyuncs.com/xxx.jpg"}
 */

// ==================== 6. 测试步骤 ====================

/**
 * 1. 启动 App，进入广场页面
 * 2. 点击左上角 "+" 按钮
 * 3. 输入标题："测试发布"
 * 4. 输入正文："这是一条测试动态"
 * 5. 点击图片区域的 "+" 按钮
 * 6. 从相册选择 3 张图片
 * 7. 确认图片显示正确
 * 8. 点击右上角 "发布" 按钮
 * 9. 观察 Loading 对话框显示上传进度
 * 10. 查看 Logcat 输出的上传日志
 * 11. 发布成功后自动返回广场页面
 */
