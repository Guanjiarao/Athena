package com.whu.software.athena;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.whu.software.athena.cognition.CognitionModels.*;
import com.whu.software.athena.cognition.CognitionRepository;
import com.whu.software.athena.cognition.CognitionRepositoryProvider;
import java.util.List;

/** User-facing hub for personal clues, questions, drafts, topics, actions and controls. */
public class MyCognitionActivity extends AppCompatActivity {
    private CognitionRepository repository;
    private TextView summary;
    private LinearLayout topicList;
    private TextView resetDemo;
    private Button modeButton;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my_cognition);
        repository = CognitionRepositoryProvider.get(this);
        summary = findViewById(R.id.tv_cognition_summary);
        topicList = findViewById(R.id.layout_my_topics);
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_my_clues).setOnClickListener(v -> startActivity(new Intent(this, BodyCluesActivity.class)));
        findViewById(R.id.btn_my_drafts).setOnClickListener(v -> openDraft());
        findViewById(R.id.btn_reminders).setOnClickListener(v -> startActivity(new Intent(this, ReminderPreferencesActivity.class)));
        findViewById(R.id.btn_privacy).setOnClickListener(v -> startActivity(new Intent(this, DataPrivacyActivity.class)));
        resetDemo = findViewById(R.id.btn_reset_demo);
        modeButton = findViewById(R.id.btn_cognition_mode);
        resetDemo.setOnClickListener(v -> confirmReset());
        modeButton.setOnClickListener(v -> confirmModeChange());
        updateModeControls();
    }

    @Override protected void onResume() { super.onResume(); refresh(); }

    private void refresh() {
        repository.getHome(new CognitionRepository.Callback<Home>() {
            @Override public void onSuccess(Home home) {
                summary.setText("待确认草稿 " + home.pendingDigestCount + " 份 · 失败任务 " + home.failedTaskCount
                        + " 个\n" + (home.nextAction == null ? "当前没有待完成行动" : "下一步：" + home.nextAction.title));
            }
            @Override public void onError(String message) { summary.setText(message); }
        });
        repository.listTopics(1, 20, new CognitionRepository.Callback<Page<Topic>>() {
            @Override public void onSuccess(Page<Topic> topics) { renderTopics(topics.data); }
            @Override public void onError(String message) { Toast.makeText(MyCognitionActivity.this, message, Toast.LENGTH_SHORT).show(); }
        });
    }

    private void renderTopics(List<Topic> topics) {
        topicList.removeAllViews();
        if (topics.isEmpty()) {
            TextView empty = text("还没有正式认知主题。\n你可以先查看身体线索，把一组线索整理成待确认草稿。", 15);
            empty.setTextColor(Color.rgb(105, 111, 108));
            topicList.addView(empty);
            return;
        }
        for (Topic topic : topics) {
            TextView card = text(topic.title + "\n" + safe(topic.stageUnderstanding)
                    + "\n证据 " + topic.evidenceCount + " 条 · 查看完整详情 →", 15);
            card.setBackgroundColor(Color.WHITE);
            card.setPadding(dp(16), dp(16), dp(16), dp(16));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
            params.bottomMargin = dp(10);
            card.setLayoutParams(params);
            card.setOnClickListener(v -> startActivity(new Intent(this, CognitionTopicActivity.class)
                    .putExtra(CognitionTopicActivity.EXTRA_TOPIC_ID, topic.id)));
            topicList.addView(card);
        }
    }

    private void openDraft() {
        repository.listReadyDigests(1, 20, new CognitionRepository.Callback<Page<Digest>>() {
            @Override public void onSuccess(Page<Digest> drafts) {
                if (drafts.data.isEmpty()) startActivity(new Intent(MyCognitionActivity.this, BodyCluesActivity.class));
                else startActivity(new Intent(MyCognitionActivity.this, CognitionDigestActivity.class)
                        .putExtra(CognitionDigestActivity.EXTRA_DIGEST_ID, drafts.data.get(0).id));
            }
            @Override public void onError(String message) { Toast.makeText(MyCognitionActivity.this, message, Toast.LENGTH_SHORT).show(); }
        });
    }

    private void confirmReset() {
        new AlertDialog.Builder(this).setTitle("重置认知演示？")
                .setMessage("会恢复预置线索并清除本机演示中的草稿、主题和反馈，不影响账号其他数据。")
                .setPositiveButton("重置", (d, w) -> { CognitionRepositoryProvider.resetDemo(this); repository = CognitionRepositoryProvider.get(this); refresh(); })
                .setNegativeButton("取消", null).show();
    }

    private void confirmModeChange() {
        boolean currentlyHttp = CognitionRepositoryProvider.isHttp(this);
        new AlertDialog.Builder(this)
                .setTitle(currentlyHttp ? "进入离线演示？" : "连接真实账号数据？")
                .setMessage(currentlyHttp
                        ? "演示模式使用本机预置数据，不会写入你的账号。之后可以随时切回真实数据。"
                        : "将恢复使用登录账号的服务器数据；演示数据会保留在本机。")
                .setPositiveButton("切换", (dialog, which) -> {
                    if (currentlyHttp) CognitionRepositoryProvider.resetDemo(this);
                    else CognitionRepositoryProvider.useHttp(this, true);
                    repository = CognitionRepositoryProvider.get(this);
                    updateModeControls();
                    refresh();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void updateModeControls() {
        boolean http = CognitionRepositoryProvider.isHttp(this);
        modeButton.setText(http ? "当前：真实账号数据 · 切换到演示" : "当前：离线演示 · 切换到真实数据");
        resetDemo.setVisibility(http ? View.GONE : View.VISIBLE);
    }
    private TextView text(String value, int size) { TextView v = new TextView(this); v.setText(value); v.setTextSize(size); v.setTextColor(Color.rgb(45, 53, 50)); v.setLineSpacing(0, 1.15f); return v; }
    private String safe(String value) { return value == null || value.isEmpty() ? "理解仍在形成中" : value; }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
