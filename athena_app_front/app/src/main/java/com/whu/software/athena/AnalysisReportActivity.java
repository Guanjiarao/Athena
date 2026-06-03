package com.whu.software.athena;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.flexbox.FlexboxLayout;
import com.whu.software.athena.utils.InsightApiService;
import com.whu.software.athena.utils.InsightReportEntity;

import java.io.InterruptedIOException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;

public class AnalysisReportActivity extends AppCompatActivity {

    private static final String TAG = "InsightNet";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private View loadingOverlay;
    private TextView tvLoadingText;
    private TextView tvEmptyState;
    private RecyclerView rvReport;
    private InsightReportAdapter adapter;

    private TextView tvSummary;
    private TextView tvSummarySource;
    private FlexboxLayout layoutHealthFocuses;
    private FlexboxLayout layoutRiskTags;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupStatusBar();
        setContentView(R.layout.activity_analysis_report);
        initViews();
        showLoading();
        loadReport();
    }

    private void setupStatusBar() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return;
        }
        Window window = getWindow();
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setStatusBarColor(Color.parseColor("#FFFDFB"));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            window.getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            );
        }
    }

    private void initViews() {
        findViewById(R.id.iv_back).setOnClickListener(v -> finish());

        loadingOverlay = findViewById(R.id.layout_loading);
        tvLoadingText = findViewById(R.id.tv_loading_text);
        tvEmptyState = findViewById(R.id.tv_empty_state);
        rvReport = findViewById(R.id.rv_insight_report);

        tvSummary = findViewById(R.id.tv_report_summary);
        tvSummarySource = findViewById(R.id.tv_report_summary_source);
        layoutHealthFocuses = findViewById(R.id.layout_health_focuses);
        layoutRiskTags = findViewById(R.id.layout_risk_tags);

        rvReport.setLayoutManager(new LinearLayoutManager(this));
        rvReport.setNestedScrollingEnabled(false);
        rvReport.setHasFixedSize(false);
    }

    private void showLoading() {
        loadingOverlay.setVisibility(View.VISIBLE);
        tvEmptyState.setVisibility(View.GONE);
        tvLoadingText.setText("AI 正在为您生成专属健康分析报告，可能需要约半分钟，请耐心等待...");
    }

    private void hideLoading() {
        loadingOverlay.setVisibility(View.GONE);
    }

    private void showEmptyState() {
        tvEmptyState.setVisibility(View.VISIBLE);
    }

    private void hideEmptyState() {
        tvEmptyState.setVisibility(View.GONE);
    }

    private void loadReport() {
        new Thread(() -> {
            InsightReportEntity entity = null;
            String errorMessage = null;
            boolean timeout = false;
            try {
                entity = InsightApiService.getReportSync(this);
            } catch (Exception e) {
                Log.e(TAG, "请求失败", e);
                timeout = isTimeoutException(e);
                if (timeout) {
                    MAIN.post(() -> Toast.makeText(
                            AnalysisReportActivity.this,
                            "AI分析耗时较长，请稍后再试",
                            Toast.LENGTH_SHORT
                    ).show());
                } else {
                    String message = e.getMessage();
                    errorMessage = (message == null || message.trim().isEmpty())
                            ? e.getClass().getSimpleName()
                            : message;
                }
            }

            final InsightReportEntity finalEntity = entity;
            final String finalErrorMessage = errorMessage;
            final boolean finalTimeout = timeout;
            MAIN.post(() -> {
                hideLoading();
                if (finalEntity == null || finalEntity.data == null) {
                    showEmptyState();
                }

                if (!finalTimeout && finalErrorMessage != null) {
                    Toast.makeText(
                            AnalysisReportActivity.this,
                            "获取分析报告失败：" + finalErrorMessage,
                            Toast.LENGTH_SHORT
                    ).show();
                }

                if (finalEntity != null && finalEntity.data != null) {
                    bindHeader(finalEntity.data);
                    bindSuggestions(finalEntity.data.readingSuggestions);
                } else {
                    bindHeader(null);
                    bindSuggestions(new ArrayList<>());
                }
            });
        }).start();
    }

    private void bindHeader(@Nullable InsightReportEntity.ReportData data) {
        String summary = data != null ? safeText(data.summary, "当前暂无足够数据生成完整报告") : "当前暂无足够数据生成完整报告";
        tvSummary.setText(summary);
        tvSummarySource.setText("数据来源：" + safeText(data != null ? data.summarySource : null, "RULE"));

        layoutHealthFocuses.removeAllViews();
        layoutRiskTags.removeAllViews();

        if (data != null) {
            addTags(layoutHealthFocuses, data.healthFocuses, false);
            addTags(layoutRiskTags, data.riskTags, true);
        }
    }

    private void bindSuggestions(@NonNull List<InsightReportEntity.ReadingSuggestion> suggestions) {
        if (adapter == null) {
            adapter = new InsightReportAdapter();
            rvReport.setAdapter(adapter);
        }
        adapter.submitList(suggestions);
        if (suggestions.isEmpty()) {
            showEmptyState();
        } else {
            hideEmptyState();
        }
    }

    private void addTags(@NonNull FlexboxLayout container,
                         @NonNull List<String> tags,
                         boolean riskStyle) {
        for (String tag : tags) {
            String text = safeText(tag, null);
            if (text == null) {
                continue;
            }
            container.addView(createTagView(text, riskStyle));
        }
    }

    @NonNull
    private TextView createTagView(@NonNull String text, boolean riskStyle) {
        TextView chip = new TextView(this);
        chip.setText(text);
        chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        chip.setSingleLine(true);
        chip.setEllipsize(android.text.TextUtils.TruncateAt.END);
        chip.setIncludeFontPadding(false);
        chip.setPadding(dp(10), dp(6), dp(10), dp(6));

        GradientDrawable drawable = new GradientDrawable();
        drawable.setCornerRadius(dp(999));
        if (riskStyle) {
            drawable.setColor(Color.parseColor("#FFF0F0"));
            drawable.setStroke(dp(1), Color.parseColor("#33D96A6A"));
            chip.setTextColor(Color.parseColor("#B84C4C"));
        } else {
            drawable.setColor(Color.parseColor("#EEF6FF"));
            drawable.setStroke(dp(1), Color.parseColor("#336A9FD8"));
            chip.setTextColor(Color.parseColor("#4A7196"));
        }
        chip.setBackground(drawable);

        FlexboxLayout.LayoutParams params = new FlexboxLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.rightMargin = dp(8);
        params.bottomMargin = dp(8);
        chip.setLayoutParams(params);
        return chip;
    }

    private int dp(int value) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                getResources().getDisplayMetrics()
        ));
    }

    @Nullable
    private String safeText(@Nullable String value, @Nullable String fallback) {
        if (value == null) {
            return fallback;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? fallback : trimmed;
    }

    private boolean isTimeoutException(@NonNull Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof SocketTimeoutException || current instanceof InterruptedIOException) {
                return true;
            }
            String message = current.getMessage();
            if (message != null && message.toLowerCase().contains("timeout")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
