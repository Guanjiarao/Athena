package com.whu.software.athena;

import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.content.FileProvider; 

import com.whu.software.athena.utils.UploadUtil;
import com.whu.software.athena.utils.QwenApiService;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class OvulationScanActivity extends AppCompatActivity {

    private ImageView    ivPreview;
    private LinearLayout layoutPlaceholder;
    private Button       btnStartAi;
    private CardView     cardImagePicker;
    private CardView     cvAnalysisReport;
    private TextView     tvReportContent;
    private FrameLayout  pbLoading;

    // 🌟 新增：用于保存高清拍照后的临时安全 Uri
    private Uri currentPhotoUri;

    // ── 相册选图 Launcher ─────────────────────────────────────────────────
    private final ActivityResultLauncher<String> pickImageLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.GetContent(),
                    uri -> { if (uri != null) onImagePicked(uri); });

    // 🌟 核心升级：废弃 TakePicturePreview（模糊图），启用 TakePicture（高清原图）！
    private final ActivityResultLauncher<Uri> takePictureLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.TakePicture(),
                    success -> {
                        if (success && currentPhotoUri != null) {
                            onImagePicked(currentPhotoUri); // 拍照成功，直接用 Uri 渲染高清大图！
                        } else {
                            Toast.makeText(this, "拍照取消或失败", Toast.LENGTH_SHORT).show();
                        }
                    });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 沉浸式状态栏
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            android.view.Window window = getWindow();
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(android.graphics.Color.parseColor("#4FA0FF"));
        }

        setContentView(R.layout.activity_ovulation_scan);
        bindViews();
        setupListeners();
    }

    private void bindViews() {
        ivPreview         = findViewById(R.id.iv_preview);
        layoutPlaceholder = findViewById(R.id.layout_placeholder);
        btnStartAi        = findViewById(R.id.btn_start_ai);
        cardImagePicker   = findViewById(R.id.card_image_picker);
        cvAnalysisReport  = findViewById(R.id.cv_analysis_report);
        tvReportContent   = findViewById(R.id.tv_report_content);
        pbLoading         = findViewById(R.id.pb_loading);
    }

    private void setupListeners() {
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        // 相册选图
        findViewById(R.id.btn_pick_from_gallery).setOnClickListener(v -> pickImageLauncher.launch("image/*"));

        // 🌟 核心升级：点击拍照按钮，生成安全 Uri 并唤起系统相机
        findViewById(R.id.btn_scan_from_camera).setOnClickListener(v -> {
            currentPhotoUri = createImageUri();
            if (currentPhotoUri != null) {
                takePictureLauncher.launch(currentPhotoUri);
            }
        });

        btnStartAi.setOnClickListener(v -> onStartAiAnalysis());
    }

    
    private Uri createImageUri() {
        // 在应用的沙盒缓存目录下创建一个临时文件
        File imageFile = new File(getCacheDir(), "camera_photo_" + System.currentTimeMillis() + ".jpg");
        // 生成安全的 Content Uri 授权给系统相机写入
        return FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", imageFile);
    }

    private void onImagePicked(Uri uri) {
        layoutPlaceholder.setVisibility(View.GONE);
        ivPreview.setVisibility(View.VISIBLE);
        ivPreview.setImageURI(uri);
        enableAnalysisButton();
    }

    private void enableAnalysisButton() {
        btnStartAi.setEnabled(true);
        btnStartAi.setAlpha(1.0f);
    }

    private void lockUiForAnalysis() {
        pbLoading.setVisibility(View.VISIBLE);
        btnStartAi.setEnabled(false);
        btnStartAi.setAlpha(0.5f);
    }

    private void unlockUi() {
        pbLoading.setVisibility(View.GONE);
        enableAnalysisButton();
    }

    private void onStartAiAnalysis() {
        Bitmap previewBitmap = null;
        if (ivPreview.getDrawable() instanceof BitmapDrawable) {
            previewBitmap = ((BitmapDrawable) ivPreview.getDrawable()).getBitmap();
        }

        if (previewBitmap == null) {
            Toast.makeText(this, "请先选择一张试纸图片", Toast.LENGTH_SHORT).show();
            return;
        }

        lockUiForAnalysis();
        final Bitmap bmp = previewBitmap;

        new Thread(() -> {
            // 端侧脱敏处理
            Bitmap safeBitmap = com.whu.software.athena.utils.PrivacyUtil.applyPrivacyMosaic(bmp);

            // 让用户亲眼看到端侧协同的脱敏效果
            runOnUiThread(() -> {
                ivPreview.setImageBitmap(safeBitmap);
                Toast.makeText(OvulationScanActivity.this, "端侧隐私脱敏完成，已遮蔽环境信息", Toast.LENGTH_SHORT).show();
            });

            File tempFile = bitmapToTempFile(safeBitmap);

            if (tempFile == null) {
                runOnUiThread(() -> {
                    unlockUi();
                    Toast.makeText(this, "图片处理失败，请重新选图", Toast.LENGTH_SHORT).show();
                });
                return;
            }

            UploadUtil.uploadFile(OvulationScanActivity.this, tempFile, new UploadUtil.UploadCallback() {
                @Override
                public void onSuccess(String imageUrl) {
                    QwenApiService.analyzeOvulationStrip(
                            imageUrl,
                            new QwenApiService.OnVisionAnalyzeListener() {
                                @Override
                                public void onSuccess(String report) {
                                    String cleanReport = report.replace("**", "");
                                    runOnUiThread(() -> showReport(cleanReport));
                                    tempFile.delete();
                                }

                                @Override
                                public void onFailure(String errorMsg) {
                                    runOnUiThread(() -> {
                                        showReport("AI 分析失败，请检查网络后重试。\n\n错误详情：" + errorMsg);
                                    });
                                    tempFile.delete();
                                }
                            });
                }

                @Override
                public void onFailure(String errorMessage) {
                    tempFile.delete();
                    runOnUiThread(() -> {
                        unlockUi();
                        Toast.makeText(OvulationScanActivity.this, "图片上传失败：" + errorMessage, Toast.LENGTH_LONG).show();
                    });
                }
            });
        }).start();
    }

    @Nullable
    private File bitmapToTempFile(Bitmap bitmap) {
        File tempFile = new File(getCacheDir(), "ovulation_scan_temp.jpg");
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(tempFile);
            Bitmap scaled = scaleBitmap(bitmap, 1024);
            scaled.compress(Bitmap.CompressFormat.JPEG, 80, fos);
            fos.flush();
            if (scaled != bitmap) scaled.recycle();
            return tempFile;
        } catch (IOException e) {
            return null;
        } finally {
            if (fos != null) {
                try { fos.close(); } catch (IOException ignored) {}
            }
        }
    }

    private static Bitmap scaleBitmap(Bitmap src, int maxSize) {
        int w = src.getWidth(), h = src.getHeight();
        if (w <= maxSize && h <= maxSize) return src;
        float ratio = (float) maxSize / Math.max(w, h);
        return Bitmap.createScaledBitmap(src, Math.round(w * ratio), Math.round(h * ratio), true);
    }

    private void showReport(String text) {
        unlockUi();
        tvReportContent.setText(text);
        cvAnalysisReport.setVisibility(View.VISIBLE);
    }
}