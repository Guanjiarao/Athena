package com.whu.software.athena.features.privacy;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;

import com.whu.software.athena.R;
import com.whu.software.athena.utils.QwenApiService;
import com.whu.software.athena.utils.UploadUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * PrivacyFragment — 端云协同全链路：
 *   相册选图 → LocalAnonymizeView 手绘 → ML Kit 离线 OCR 本地脱敏 → 匿名图上传 → Qwen 医疗分析
 */
public class PrivacyFragment extends Fragment {

    private static final int PICK_IMAGE_REQUEST = 1;

    private CardView         cardPrivacyImage;
    private View             privacyPlaceholder;
    private View             ocrLoadingOverlay;
    private LocalAnonymizeView localAnonymizeView;
    private Button           btnBlur, btnPixel;
    private Button           btnLocalAi;
    private Button           btnUploadAnalysis;
    private TextView         tvUploadGuardHint;
    private SeekBar          seekBarIntensity;
    private TextView         textSeekPercent;
    private CardView         cardAnalysisResult;
    private TextView         textAnalysisResult;

    private Dialog                federatedLoadingDialog;
    private TextView              textFederatedLog;
    private ScrollView            scrollFederatedLog;
    private boolean federatedDialogShowing;

    private Bitmap           originalBitmap;
    private final ExecutorService bgExecutor   = Executors.newSingleThreadExecutor();
    private final Handler         mainHandler  = new Handler(Looper.getMainLooper());

    /** 仅当离线 OCR 脱敏成功（检测到敏感区并已涂黑）后为 true，才允许云端上传按钮。 */
    private boolean          offlineRedactionSucceeded;
    private boolean          uiLocked;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_privacy, container, false);
        bindViews(view);
//        view.findViewById(R.id.btn_privacy_back).setOnClickListener(v ->
//                requireActivity().getOnBackPressedDispatcher().onBackPressed());
        setupSeekBar();
        setupToolButtons();
        setupAutoAnonymizeButton();
        setupSaveButton();
        cardPrivacyImage.setOnClickListener(v -> openFileChooser());
        return view;
    }

    @Override
    public void onDestroyView() {
        dismissFederatedLoadingDialog();
        super.onDestroyView();
    }

    private void bindViews(View root) {
        cardPrivacyImage    = root.findViewById(R.id.cardPrivacyImage);
        privacyPlaceholder  = root.findViewById(R.id.privacyPlaceholder);
        ocrLoadingOverlay   = root.findViewById(R.id.ocrLoadingOverlay);
        localAnonymizeView  = root.findViewById(R.id.localAnonymizeView);
        btnBlur             = root.findViewById(R.id.btnBlur);
        btnPixel            = root.findViewById(R.id.btnPixel);
        btnLocalAi          = root.findViewById(R.id.btn_local_ai);
        btnUploadAnalysis   = root.findViewById(R.id.btn_upload_analysis);
        tvUploadGuardHint   = root.findViewById(R.id.tv_upload_guard_hint);
        seekBarIntensity    = root.findViewById(R.id.seekBarIntensity);
        textSeekPercent     = root.findViewById(R.id.textSeekPercent);
        cardAnalysisResult  = root.findViewById(R.id.cardAnalysisResult);
        textAnalysisResult  = root.findViewById(R.id.textAnalysisResult);

        offlineRedactionSucceeded = false;
        btnUploadAnalysis.setEnabled(false);
        refreshUploadGuardHint();

        setToolSelected(true);
    }

    private void refreshUploadGuardHint() {
        if (tvUploadGuardHint == null) return;
        boolean show = originalBitmap != null && !offlineRedactionSucceeded;
        tvUploadGuardHint.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void refreshUploadButtonEnabled() {
        if (btnUploadAnalysis == null) return;
        btnUploadAnalysis.setEnabled(offlineRedactionSucceeded && originalBitmap != null && !uiLocked);
    }

    private void setupSeekBar() {
        updateSeekLabel();
        seekBarIntensity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                updateSeekLabel();
                if (fromUser && originalBitmap != null) {
                    localAnonymizeView.setProcessIntensity(intensityFromProgress(progress));
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    private void setupToolButtons() {
        btnBlur.setOnClickListener(v -> {
            setToolSelected(true);
            localAnonymizeView.setMode(LocalAnonymizeView.MODE_MANUAL_BLUR);
        });
        btnPixel.setOnClickListener(v -> {
            setToolSelected(false);
            localAnonymizeView.setMode(LocalAnonymizeView.MODE_MANUAL_MOSAIC);
        });
    }

    private void setToolSelected(boolean blurSelected) {
        btnBlur.setSelected(blurSelected);
        btnPixel.setSelected(!blurSelected);
        btnBlur.setTypeface(null,  blurSelected ? Typeface.BOLD : Typeface.NORMAL);
        btnPixel.setTypeface(null, !blurSelected ? Typeface.BOLD : Typeface.NORMAL);
    }

    private void setupAutoAnonymizeButton() {
        btnLocalAi.setOnClickListener(v -> {
            if (originalBitmap == null) return;
            setUiLocked(true);
            ocrLoadingOverlay.setVisibility(View.VISIBLE);

            MLKitAnonymizeService.detectPIIInMedicalReport(
                    originalBitmap,
                    new MLKitAnonymizeService.OnPIIDetectedListener() {
                        @Override
                        public void onPIIDetected(@NonNull List<android.graphics.Rect> regions,
                                                  @NonNull List<String> texts) {
                            ocrLoadingOverlay.setVisibility(View.GONE);
                            setUiLocked(false);

                            if (regions.isEmpty()) {
                                offlineRedactionSucceeded = false;
                                refreshUploadGuardHint();
                                refreshUploadButtonEnabled();
                                toast(getString(R.string.privacy_ocr_no_pii));
                            } else {
                                localAnonymizeView.autoAnonymizeRegions(regions);
                                offlineRedactionSucceeded = true;
                                refreshUploadGuardHint();
                                refreshUploadButtonEnabled();
                                toast(getString(R.string.privacy_ocr_done, regions.size()));
                            }
                        }

                        @Override
                        public void onError(@NonNull Exception e) {
                            ocrLoadingOverlay.setVisibility(View.GONE);
                            setUiLocked(false);
                            offlineRedactionSucceeded = false;
                            refreshUploadGuardHint();
                            refreshUploadButtonEnabled();
                            toast(getString(R.string.privacy_ocr_error, e.getMessage()));
                        }
                    });
        });
    }

    private void setupSaveButton() {
        btnUploadAnalysis.setOnClickListener(v -> {
            Bitmap finalBitmap = localAnonymizeView.getFinalAnonymizedBitmap();
            if (finalBitmap == null) {
                toast("请先选择图片并完成脱敏处理");
                return;
            }

            setUiLocked(true);
            showFederatedLoadingDialog();

            bgExecutor.execute(() -> {
                appendLog(getString(R.string.privacy_federated_log_1));

                final int SAMPLE_SIDE = 64;
                double[] pixels = PrivacyMathEngine.extractPixelFeatures(finalBitmap, SAMPLE_SIDE);

                appendLog(getString(R.string.privacy_federated_log_2));
                double[] rapFeatures = PrivacyMathEngine.applyRandomProjection(pixels, 256);

                String haCoreHash = PrivacyMathEngine.extractLSHSignature(rapFeatures);
                double ratio = PrivacyMathEngine.oneRatio(haCoreHash);

                String hashPreview = haCoreHash.substring(0, Math.min(32, haCoreHash.length()));
                appendLog(String.format(
                        "端侧特征签名: %s…\n  (激活率 %.1f%% · 压缩比 64:1)",
                        hashPreview, ratio * 100));

                appendLog(getString(R.string.privacy_federated_log_4));
                double[] noisedFeatures = PrivacyMathEngine.injectLaplaceNoise(rapFeatures, 0.5);
                String finalHash = PrivacyMathEngine.extractLSHSignature(noisedFeatures);
                appendLog(String.format("梯度混淆完成 · 扰动后签名前缀: %s",
                        finalHash.substring(0, Math.min(32, finalHash.length()))));

                appendLog(getString(R.string.privacy_federated_log_5));
                File jpegFile = saveBitmapToJpeg(finalBitmap);
                if (jpegFile == null) {
                    mainHandler.post(() -> {
                        dismissFederatedLoadingDialog();
                        setUiLocked(false);
                        toast("图片保存失败，请重试");
                    });
                    return;
                }

                appendLog(getString(R.string.privacy_federated_log_6));
                final String capturedHash = haCoreHash;
                mainHandler.post(() ->
                    UploadUtil.uploadFile(requireContext(), jpegFile,
                            new UploadUtil.UploadCallback() {
                                @Override
                                public void onSuccess(String imageUrl) {
                                    callQwenForMedicalReport(imageUrl, capturedHash);
                                }

                                @Override
                                public void onFailure(String errorMessage) {
                                    dismissFederatedLoadingDialog();
                                    setUiLocked(false);
                                    toast("上传失败：" + errorMessage);
                                }
                            }));
            });
        });
    }

    private void appendLog(String message) {
        mainHandler.post(() -> {
            if (!federatedDialogShowing || textFederatedLog == null || !isAdded()) return;
            textFederatedLog.append("> " + message + "\n");
            if (scrollFederatedLog != null) {
                scrollFederatedLog.post(() -> scrollFederatedLog.fullScroll(View.FOCUS_DOWN));
            }
        });
    }

    private void showFederatedLoadingDialog() {
        if (!isAdded()) return;
        dismissFederatedLoadingDialog();

        federatedDialogShowing = true;

        Dialog dialog = new Dialog(requireContext(), R.style.Theme_PrivacyFederatedDialog);
        View content = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_privacy_federated_loading, null, false);
        textFederatedLog = content.findViewById(R.id.textFederatedLog);
        scrollFederatedLog = content.findViewById(R.id.scrollFederatedLog);
        textFederatedLog.setText("");

        dialog.setContentView(content);
        dialog.setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);
        dialog.show();

        federatedLoadingDialog = dialog;
    }

    private void dismissFederatedLoadingDialog() {
        federatedDialogShowing = false;
        textFederatedLog = null;
        scrollFederatedLog = null;
        if (federatedLoadingDialog != null) {
            try {
                if (federatedLoadingDialog.isShowing()) {
                    federatedLoadingDialog.dismiss();
                }
            } catch (Exception ignored) {
            }
            federatedLoadingDialog = null;
        }
    }

    private void callQwenForMedicalReport(String anonymizedImageUrl, String haCoreHash) {
        QwenApiService.analyzeGeneralMedicalImage(anonymizedImageUrl,
                new QwenApiService.OnVisionAnalyzeListener() {
                    @Override
                    public void onSuccess(String report) {
                        dismissFederatedLoadingDialog();
                        setUiLocked(false);
                        showAnalysisResult(report);

                        bgExecutor.execute(() -> {
                            double shapley = ShapleyMathEngine.computeMonteCarloShapley(
                                    haCoreHash, 100, 500);
                            long points = Math.round(shapley * 1000);

                            SharedPreferences prefs = requireContext().getSharedPreferences(
                                    ShapleyMathEngine.PREFS_NAME, Context.MODE_PRIVATE);
                            SharedPreferences.Editor editor = prefs.edit();

                            double prevShapley = Double.longBitsToDouble(
                                    prefs.getLong(ShapleyMathEngine.KEY_SHAPLEY_SUM,
                                            Double.doubleToLongBits(0.0)));
                            long prevPoints = prefs.getLong(ShapleyMathEngine.KEY_POINTS_SUM, 0L);

                            editor.putLong(ShapleyMathEngine.KEY_SHAPLEY_SUM,
                                    Double.doubleToLongBits(prevShapley + shapley));
                            editor.putLong(ShapleyMathEngine.KEY_POINTS_SUM, prevPoints + points);
                            editor.apply();

                            mainHandler.post(() ->
                                    Toast.makeText(getContext(),
                                            "🎉 端侧 AI 贡献计算完毕，已存入资产中枢",
                                            Toast.LENGTH_SHORT).show());
                        });
                    }

                    @Override
                    public void onFailure(String errorMsg) {
                        dismissFederatedLoadingDialog();
                        setUiLocked(false);
                        toast("AI 分析失败，请重试");
                        Log.w("PrivacyFragment", "Qwen 分析失败: " + errorMsg);
                    }
                });
    }

    private void showAnalysisResult(String report) {
        cardAnalysisResult.setVisibility(View.VISIBLE);
        textAnalysisResult.setText(report);
        cardAnalysisResult.requestFocus();

        View feedbackBar = cardAnalysisResult.findViewById(R.id.layout_feedback);
        if (feedbackBar != null) {
            feedbackBar.findViewById(R.id.btn_rlhf_negative).setOnClickListener(v ->
                    RLHFDialogHelper.showBiasCorrectionDialog(requireContext(), null));
        }
    }

    private void openFileChooser() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        startActivityForResult(intent, PICK_IMAGE_REQUEST);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_IMAGE_REQUEST
                || resultCode != Activity.RESULT_OK
                || data == null || data.getData() == null) return;

        Uri uri = data.getData();
        try {
            InputStream is = requireActivity().getContentResolver().openInputStream(uri);
            originalBitmap = BitmapFactory.decodeStream(is);
        } catch (Exception e) {
            toast("图片加载失败");
            return;
        }

        offlineRedactionSucceeded = false;
        privacyPlaceholder.setVisibility(View.GONE);
        cardAnalysisResult.setVisibility(View.GONE);
        refreshUploadGuardHint();
        refreshUploadButtonEnabled();

        btnLocalAi.setEnabled(false);

        localAnonymizeView.setMode(LocalAnonymizeView.MODE_MANUAL_BLUR);
        localAnonymizeView.setProcessIntensity(intensityFromProgress(seekBarIntensity.getProgress()));
        localAnonymizeView.setStrokeWidth(dpToPx(60));
        localAnonymizeView.setOnEffectReadyListener(() -> {
            if (!uiLocked) {
                btnLocalAi.setEnabled(true);
            }
            refreshUploadButtonEnabled();
        });
        localAnonymizeView.setImageSource(originalBitmap);
    }

    private void setUiLocked(boolean locked) {
        uiLocked = locked;
        btnLocalAi.setEnabled(!locked && originalBitmap != null);
        btnBlur.setEnabled(!locked);
        btnPixel.setEnabled(!locked);
        seekBarIntensity.setEnabled(!locked);
        refreshUploadButtonEnabled();
    }

    private void updateSeekLabel() {
        int max = seekBarIntensity.getMax();
        int p   = seekBarIntensity.getProgress();
        int pct = max > 0 ? Math.round(100f * p / max) : 0;
        textSeekPercent.setText(getString(R.string.privacy_seek_percent_format, pct));
    }

    private static int intensityFromProgress(int progress) {
        return Math.max(1, progress);
    }

    @Nullable
    private File saveBitmapToJpeg(Bitmap bitmap) {
        try {
            File dir  = requireActivity().getExternalFilesDir(null);
            File file = new File(dir, "anonymized_" + System.currentTimeMillis() + ".jpg");
            FileOutputStream fos = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos);
            fos.flush();
            fos.close();
            return file;
        } catch (Exception e) {
            return null;
        }
    }

    private float dpToPx(float dp) {
        return dp * getResources().getDisplayMetrics().density;
    }

    private void toast(String msg) {
        Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();
    }
}
