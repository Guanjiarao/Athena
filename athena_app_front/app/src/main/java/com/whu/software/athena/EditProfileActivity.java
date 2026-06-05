package com.whu.software.athena;

import android.Manifest;
import android.app.DatePickerDialog;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.IOException;
import java.util.Calendar;
import java.util.Locale;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.app.ProgressDialog;
import android.content.ContentResolver;

import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CircleCrop;
import com.whu.software.athena.config.ApiConfig;
import com.whu.software.athena.utils.TokenManager;
import com.whu.software.athena.utils.UnsafeOkHttpClient;
import android.os.Environment;
import org.json.JSONObject;
import android.provider.MediaStore;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class EditProfileActivity extends AppCompatActivity {

    private static final String TAG = "EditProfileActivity";
    private static final int REQUEST_CAMERA = 100;
    private static final int REQUEST_GALLERY = 101;
    private static final int REQUEST_CAMERA_PERMISSION = 200;
    // UI 控件
    private ImageView ivBack;
    private TextView tvSave;
    private FrameLayout avatarContainer;
    private ImageView ivAvatar;
    private EditText etNickname;
    private EditText tvBirthday;
    private TextView tvAge;

    private OkHttpClient okHttpClient;
    private Uri cameraUri; // 用于保存拍照临时生成的 Uri
    private Uri photoUri;
    // ──────────────────────────────────────────────────────────
    // 现代 API：注册权限与图片获取的 Launcher (拒绝繁琐的 onActivityResult)
    // ──────────────────────────────────────────────────────────

    // 1. 申请相机权限
    private final ActivityResultLauncher<String> requestCameraPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) launchCamera();
                else Toast.makeText(this, "需要相机权限才能拍照哦", Toast.LENGTH_SHORT).show();
            });

    // 2. 从相册选择图片
    private final ActivityResultLauncher<String> pickImageLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) handleImageSelected(uri);
            });

    // 3. 拍照获取图片
    private final ActivityResultLauncher<Uri> takePictureLauncher =
            registerForActivityResult(new ActivityResultContracts.TakePicture(), success -> {
                if (success && cameraUri != null) handleImageSelected(cameraUri);
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_profile);

        okHttpClient = UnsafeOkHttpClient.getUnsafeOkHttpClient();
        initView();
        setupListeners();
        fetchUserInfo(); // 进页面立刻拉取资料
    }

    private void initView() {
        ivBack = findViewById(R.id.iv_back);
        tvSave = findViewById(R.id.tv_save);
        avatarContainer = findViewById(R.id.avatar_container);
        ivAvatar = findViewById(R.id.iv_avatar);
        etNickname = findViewById(R.id.et_nickname);
        tvBirthday = findViewById(R.id.et_birthday);
        tvAge = findViewById(R.id.tv_age);
    }

    private void setupListeners() {
        ivBack.setOnClickListener(v -> finish());
        tvSave.setOnClickListener(v -> saveUserProfile());

        // 点击头像区域，弹出选择框
        avatarContainer.setOnClickListener(v -> showAvatarDialog());
        tvBirthday.setOnClickListener(v -> showDatePicker());
        View labelBirthday = findViewById(R.id.label_birthday);
        if (labelBirthday != null) {
            labelBirthday.setOnClickListener(v -> showDatePicker());
        }
    }

    private void showDatePicker() {
        Calendar cal = Calendar.getInstance();
        int year = cal.get(Calendar.YEAR);
        int month = cal.get(Calendar.MONTH);
        int day = cal.get(Calendar.DAY_OF_MONTH);

        String current = tvBirthday.getText() != null ? tvBirthday.getText().toString().trim() : "";
        if (!current.isEmpty()) {
            try {
                String[] parts = current.split("[\\-/]");
                if (parts.length >= 3) {
                    year = Integer.parseInt(parts[0].trim());
                    month = Integer.parseInt(parts[1].trim()) - 1;
                    day = Integer.parseInt(parts[2].trim().split(" ")[0]);
                }
            } catch (Exception e) {
                Log.w(TAG, "解析已有生日失败，使用今天: " + e.getMessage());
            }
        }

        DatePickerDialog dialog = new DatePickerDialog(
                this,
                R.style.CustomDatePickerBlue,
                (view, y, m, d) -> {
                    String formatted = String.format(Locale.getDefault(), "%04d-%02d-%02d", y, m + 1, d);
                    tvBirthday.setText(formatted);
                    updateAgeDisplay(y, m + 1, d);
                },
                year, month, day
        );
        dialog.show();
    }

    /** 根据出生年月日更新年龄展示 */
    private void updateAgeDisplay(int birthYear, int birthMonth1to12, int birthDay) {
        Calendar birth = Calendar.getInstance();
        birth.set(birthYear, birthMonth1to12 - 1, birthDay);
        Calendar now = Calendar.getInstance();
        int age = now.get(Calendar.YEAR) - birth.get(Calendar.YEAR);
        if (now.get(Calendar.DAY_OF_YEAR) < birth.get(Calendar.DAY_OF_YEAR)) {
            age--;
        }
        if (age >= 0 && age < 150) {
            tvAge.setText(String.valueOf(age));
        } else {
            tvAge.setText("");
        }
    }

    // ──────────────────────────────────────────────────────────
    // 头像选择与处理逻辑
    // ──────────────────────────────────────────────────────────

    private void showAvatarDialog() {
        String[] options = {"从相册选择", "拍一张"};
        new AlertDialog.Builder(this)
                .setTitle("更换头像")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        // 启动相册 (GetContent 契约在现代 Android 中无需手动申请存储权限)
                        pickImageLauncher.launch("image/*");
                    } else {
                        // 检查并启动相机
                        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                                == PackageManager.PERMISSION_GRANTED) {
                            launchCamera();
                        } else {
                            requestCameraPermission.launch(Manifest.permission.CAMERA);
                        }
                    }
                }).show();
    }

    private void launchCamera() {
        try {
            // 在应用的专属缓存目录下创建一个临时文件用于装载相片
            File photoFile = new File(getCacheDir(), "avatar_temp_" + System.currentTimeMillis() + ".jpg");
            // 利用 FileProvider 转换为安全的 Uri，这里的 authorities 必须与 AndroidManifest 中一致
            cameraUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", photoFile);
            takePictureLauncher.launch(cameraUri);
        } catch (Exception e) {
            Log.e(TAG, "启动相机失败: " + e.getMessage());
            Toast.makeText(this, "无法启动相机", Toast.LENGTH_SHORT).show();
        }
    }

    private void handleImageSelected(Uri uri) {
        // 1. 【极致体验】：立刻在本地用 Glide 圆形预览，用户不用等上传就能看到效果
        Glide.with(this).load(uri).transform(new CircleCrop()).into(ivAvatar);

        // 2. 开启子线程，将 Uri 转为实体 File，然后上传
        new Thread(() -> {
            File imageFile = uriToFile(uri);
            if (imageFile != null) {
                uploadToOssAndSave(imageFile);
            } else {
                runOnUiThread(() -> Toast.makeText(this, "图片处理失败", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    // 将 Uri 复制为真实的 File 供 OkHttp 上传
    private File uriToFile(Uri uri) {
        try {
            ContentResolver resolver = getContentResolver();
            InputStream inputStream = resolver.openInputStream(uri);
            File tempFile = new File(getCacheDir(), "upload_temp.jpg");
            FileOutputStream outputStream = new FileOutputStream(tempFile);
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            outputStream.close();
            inputStream.close();
            return tempFile;
        } catch (Exception e) {
            Log.e(TAG, "Uri转File失败: " + e.getMessage());
            return null;
        }
    }

    // ──────────────────────────────────────────────────────────
    // 网络请求（上传OSS -> 存数据库）
    // ──────────────────────────────────────────────────────────

    private void uploadToOssAndSave(File imageFile) {
        String token = TokenManager.getToken(this);
        if (token.isEmpty()) {
            runOnUiThread(() -> Toast.makeText(this, "登录已失效", Toast.LENGTH_SHORT).show());
            return;
        }

        runOnUiThread(() -> Toast.makeText(this, "正在上传图片...", Toast.LENGTH_SHORT).show());

        // 1. 构造 MultipartBody (对应 Apifox 里的 form-data 上传)
        RequestBody fileBody = RequestBody.create(MediaType.parse("image/jpeg"), imageFile);
        MultipartBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", imageFile.getName(), fileBody) // 这里的 "file" 就是你 Apifox 截图里的参数名
                .build();

        // 2. 构造请求 (POST /file/upload)
        Request request = new Request.Builder()
                .url(ApiConfig.API_FILE_UPLOAD) // 确保 ApiConfig 里定义了这个常量
                .addHeader("Authorization", "Bearer " + token)
                .post(requestBody)
                .build();

        // 3. 发送请求给 OSS
        okHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "OSS上传失败", e);
                runOnUiThread(() -> Toast.makeText(EditProfileActivity.this, "图片上传失败，请检查网络", Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful() || response.body() == null) {
                    runOnUiThread(() -> Toast.makeText(EditProfileActivity.this, "服务器拒绝了上传请求", Toast.LENGTH_SHORT).show());
                    return;
                }

                String bodyStr = response.body().string();
                Log.d(TAG, "OSS上传响应: " + bodyStr);
                try {
                    JSONObject root = new JSONObject(bodyStr);
                    if (root.optInt("code", -1) == 200) {
                        // 从响应的 data 字段中提取真实的图片网络地址
                        String realImageUrl = root.optString("data", "");
                        if (!realImageUrl.isEmpty()) {
                            Log.d(TAG, "成功拿到 OSS 图片链接: " + realImageUrl);
                            // 拿到链接后，走第二步：调用 updateIcon 接口存入数据库
                            updateAvatarUrlOnServer(realImageUrl);
                        } else {
                            runOnUiThread(() -> Toast.makeText(EditProfileActivity.this, "未能获取图片URL", Toast.LENGTH_SHORT).show());
                        }
                    } else {
                        String msg = root.optString("message", "上传失败");
                        runOnUiThread(() -> Toast.makeText(EditProfileActivity.this, msg, Toast.LENGTH_SHORT).show());
                    }
                } catch (Exception e) {
                    Log.e(TAG, "解析OSS响应异常", e);
                    runOnUiThread(() -> Toast.makeText(EditProfileActivity.this, "图片上传解析失败", Toast.LENGTH_SHORT).show());
                }
            }
        });
    }

    // 第二步：将 OSS 链接发给后端保存 (你提供的 PUT /user/updateIcon 接口)
    private void updateAvatarUrlOnServer(String imageUrl) {
        String token = TokenManager.getToken(this);
        if (token.isEmpty()) return;

        try {
            // 参数直接拼在 URL 上，因为后端没有要求特殊的 Body 格式
            okhttp3.HttpUrl parsedUrl = okhttp3.HttpUrl.parse(ApiConfig.API_USER_UPDATE_ICON); // 确保 ApiConfig 有这个常量
            String finalUrl = parsedUrl.newBuilder()
                    .addQueryParameter("icon", imageUrl) // 参数名 "icon"
                    .build().toString();

            // 构造空表单，完美应付 SpringBoot 的傲娇拦截
            RequestBody emptyBody = new FormBody.Builder().build();

            Request request = new Request.Builder()
                    .url(finalUrl)
                    .addHeader("Authorization", "Bearer " + token)
                    .put(emptyBody) // PUT 请求
                    .build();

            okHttpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    runOnUiThread(() -> Toast.makeText(EditProfileActivity.this, "头像保存失败", Toast.LENGTH_SHORT).show());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    String body = response.body() != null ? response.body().string() : "";
                    Log.d(TAG, "更新头像响应: " + body);
                    runOnUiThread(() -> {
                        try {
                            JSONObject root = new JSONObject(body);
                            if (root.optInt("code", -1) == 200) {
                                Toast.makeText(EditProfileActivity.this, "头像修改成功", Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(EditProfileActivity.this, root.optString("message", "失败"), Toast.LENGTH_SHORT).show();
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    });
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ──────────────────────────────────────────────────────────
    // 纯净版的拉取和保存（告别 userId）
    // ──────────────────────────────────────────────────────────

    private void fetchUserInfo() {
        String token = TokenManager.getToken(this);
        if (token.isEmpty()) return;

        // 干净的 URL，坚决不拼 ?userId=
        Request request = new Request.Builder()
                .url(ApiConfig.API_USER_GET_INFO)
                .addHeader("Authorization", "Bearer " + token)
                .get()
                .build();

        okHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {}

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        String body = response.body().string();
                        JSONObject data = new JSONObject(body).optJSONObject("data");
                        if (data != null) {
                            String nickname = data.optString("nickName", data.optString("name"));
                            String birthday = data.optString("birthday");
                            String avatarUrl = data.optString("icon", data.optString("avatar"));

                            runOnUiThread(() -> {
                                if (nickname != null && !nickname.isEmpty()) etNickname.setText(nickname);
                                if (birthday != null && !birthday.isEmpty()) {
                                    tvBirthday.setText(birthday);
                                    try {
                                        String[] p = birthday.trim().split("[\\-/]");
                                        if (p.length >= 3) {
                                            updateAgeDisplay(
                                                    Integer.parseInt(p[0].trim()),
                                                    Integer.parseInt(p[1].trim()),
                                                    Integer.parseInt(p[2].trim().split(" ")[0]));
                                        }
                                    } catch (Exception ignored) { }
                                }
                                if (avatarUrl != null && !avatarUrl.isEmpty() && !isDestroyed()) {
                                    Glide.with(EditProfileActivity.this).load(avatarUrl).transform(new CircleCrop()).into(ivAvatar);
                                }
                            });
                        }
                    } catch (Exception e) { e.printStackTrace(); }
                }
            }
        });
    }

    private void saveUserProfile() {
        String token = TokenManager.getToken(this);
        if (token.isEmpty()) {
            Toast.makeText(this, "登录已失效，请重新登录", Toast.LENGTH_SHORT).show();
            return;
        }

        final String nickname = etNickname.getText() != null ? etNickname.getText().toString().trim() : "";
        final String birthday = tvBirthday.getText() != null ? tvBirthday.getText().toString().trim() : "";

        ProgressDialog loading = new ProgressDialog(this);
        loading.setMessage("保存中...");
        loading.setCancelable(false);
        loading.show();

        new Thread(() -> {
            StringBuilder errorLog = new StringBuilder();

            // 1. 更新昵称 (复用你原来成功的代码结构)
            if (!nickname.isEmpty()) {
                try {
                    String encoded = URLEncoder.encode(nickname, "UTF-8");
                    Request req = new Request.Builder()
                            .url(ApiConfig.API_USER_UPDATE_NICKNAME + "?nickName=" + encoded)
                            .addHeader("Authorization", "Bearer " + token)
                            .put(new FormBody.Builder().build())
                            .build();
                    try (Response resp = okHttpClient.newCall(req).execute()) {
                        String body = resp.body() != null ? resp.body().string() : "";
                        JSONObject root = new JSONObject(body);
                        if (root.optInt("code", -1) != 200) errorLog.append("昵称失败 ");
                    }
                } catch (Exception e) { errorLog.append("昵称异常 "); }
            }

            // 2. 更新生日 (一模一样的结构)
            if (!birthday.isEmpty()) {
                try {
                    String encoded = URLEncoder.encode(birthday, "UTF-8");
                    Request req = new Request.Builder()
                            .url(ApiConfig.API_USER_UPDATE_BIRTHDAY + "?birthday=" + encoded)
                            .addHeader("Authorization", "Bearer " + token)
                            .put(new FormBody.Builder().build())
                            .build();
                    try (Response resp = okHttpClient.newCall(req).execute()) {
                        String body = resp.body() != null ? resp.body().string() : "";
                        JSONObject root = new JSONObject(body);
                        if (root.optInt("code", -1) != 200) errorLog.append("生日失败 ");
                    }
                } catch (Exception e) { errorLog.append("生日异常 "); }
            }

            runOnUiThread(() -> {
                loading.dismiss();
                if (errorLog.length() == 0) {
                    Toast.makeText(this, "保存成功", Toast.LENGTH_SHORT).show();
                    new Handler(Looper.getMainLooper()).postDelayed(this::finish, 500);
                } else {
                    Toast.makeText(this, "部分失败: " + errorLog.toString(), Toast.LENGTH_SHORT).show();
                }
            });
        }).start();
    }
}