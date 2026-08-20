package com.whu.software.athena;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.LinearLayout;
import android.graphics.Color;

import androidx.appcompat.app.AppCompatActivity;

import com.whu.software.athena.cognition.CognitionModels.*;
import com.whu.software.athena.cognition.CognitionRepository;
import com.whu.software.athena.cognition.CognitionRepositoryProvider;

public class CognitionTopicActivity extends AppCompatActivity {
    public static final String EXTRA_TOPIC_ID = "topic_id";
    private CognitionRepository repository;
    private long topicId;
    private Topic topic;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cognition_topic);
        repository = CognitionRepositoryProvider.get(this);
        topicId = getIntent().getLongExtra(EXTRA_TOPIC_ID, -1);
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_topic_action).setOnClickListener(v -> openAction());
        findViewById(R.id.btn_pause_topic).setOnClickListener(v -> move(TopicProgress.PAUSED));
        findViewById(R.id.btn_archive_topic).setOnClickListener(v -> move(TopicProgress.ARCHIVED));
    }

    @Override protected void onResume() { super.onResume(); loadTopics(); }

    private void loadTopics() {
        repository.listTopics(new CognitionRepository.Callback<java.util.List<Topic>>() {
            @Override public void onSuccess(java.util.List<Topic> topics) {
                renderTabs(topics);
                if (topicId <= 0 && !topics.isEmpty()) topicId = topics.get(0).topicId;
                load();
            }
            @Override public void onError(String message) { Toast.makeText(CognitionTopicActivity.this, message, Toast.LENGTH_SHORT).show(); }
        });
    }

    private void renderTabs(java.util.List<Topic> topics) {
        LinearLayout tabs = findViewById(R.id.layout_topic_tabs);
        tabs.removeAllViews();
        for (Topic item : topics) {
            TextView tab = new TextView(this);
            tab.setText(item.title);
            tab.setTextSize(14);
            tab.setTextColor(item.topicId == topicId ? Color.rgb(33, 83, 66) : Color.rgb(99, 105, 102));
            tab.setBackgroundColor(item.topicId == topicId ? Color.rgb(233, 240, 234) : Color.TRANSPARENT);
            tab.setPadding(dp(14), dp(9), dp(14), dp(9));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            params.setMarginEnd(dp(8));
            tab.setLayoutParams(params);
            tab.setOnClickListener(v -> { topicId = item.topicId; renderTabs(topics); load(); });
            tabs.addView(tab);
        }
    }

    private void load() {
        repository.getTopic(topicId, new CognitionRepository.Callback<Topic>() {
            @Override public void onSuccess(Topic value) {
                topic = value;
                set(R.id.tv_topic_title, value.title);
                set(R.id.tv_topic_status, displayMaturity(value.maturity) + " · " + displayProgress(value.progress)
                        + " · " + displayRisk(value.riskStatus));
                set(R.id.tv_topic_summary, value.summary);
                set(R.id.tv_topic_link, value.summary + "\n这只是当前可解释的联系，不代表已经确认因果关系。");
                set(R.id.tv_topic_evidence, evidenceText(value));
                set(R.id.tv_topic_uncertainty, value.uncertainty);
                set(R.id.tv_topic_trusted_content, "《如何记录身体变化》\n来源：Athena 健康内容库 · 已审核 · 2026-08 更新\n推荐理由：与你主动观察的主题有关，不依据浏览历史推荐。");
                set(R.id.tv_topic_recent, recentText(value));
                set(R.id.tv_topic_feedback, feedbackText(value));
                set(R.id.tv_topic_history, historyText(value));
                Action next = pendingAction(value);
                findViewById(R.id.btn_topic_action).setVisibility(next == null ? View.GONE : View.VISIBLE);
            }
            @Override public void onError(String message) { Toast.makeText(CognitionTopicActivity.this, message, Toast.LENGTH_SHORT).show(); }
        });
    }

    private void openAction() {
        Action action = pendingAction(topic);
        if (action != null) startActivity(new Intent(this, CognitionFeedbackActivity.class)
                .putExtra(CognitionFeedbackActivity.EXTRA_ACTION_ID, action.actionId));
    }

    private void move(TopicProgress progress) {
        repository.updateTopicProgress(topicId, progress, new CognitionRepository.Callback<Topic>() {
            @Override public void onSuccess(Topic value) { topic = value; load(); }
            @Override public void onError(String message) { Toast.makeText(CognitionTopicActivity.this, message, Toast.LENGTH_SHORT).show(); }
        });
    }

    private Action pendingAction(Topic value) {
        if (value == null) return null;
        for (Action action : value.actions) if (action.status == ActionStatus.PENDING) return action;
        return null;
    }
    private String evidenceText(Topic value) {
        if (value.evidence.isEmpty()) return "暂无证据；Athena 不会为了填满页面伪造依据。";
        StringBuilder out = new StringBuilder("来自 ").append(value.evidence.size()).append(" 条可追溯线索\n");
        for (Evidence evidence : value.evidence) out.append("• 线索 #").append(evidence.clueId)
                .append(" · ").append("QUESTION_CONTEXT".equals(evidence.evidenceRole) ? "用户疑问（低权重）" : "用户保存的观察上下文").append('\n');
        return out.toString().trim();
    }
    private String recentText(Topic value) {
        Action action = pendingAction(value);
        return action == null ? "当前没有待完成行动。你可以继续观察，或暂停这个主题。"
                : "下一步：" + action.title + "\n" + action.instruction;
    }
    private String feedbackText(Topic value) {
        if (value.feedback.isEmpty()) return "尚未提交行动反馈。反馈只校正后续理解，不会自动形成诊断。";
        Feedback latest = value.feedback.get(0);
        return "最近反馈：" + displayAccuracy(latest.accuracy) + " · " + (latest.completed ? "已完成行动" : "这次未完成")
                + (latest.note == null || latest.note.isEmpty() ? "" : "\n备注：" + latest.note);
    }
    private String historyText(Topic value) {
        if (value.history.isEmpty()) return "还没有历史版本";
        StringBuilder out = new StringBuilder();
        for (TopicVersion version : value.history) out.append("版本 ").append(version.version).append(" · ")
                .append(displayChange(version.changeReason)).append("\n");
        return out.toString().trim();
    }
    private String displayMaturity(CognitionMaturity value) {
        if (value == null) return "还需要了解";
        switch (value) { case CLUE: return "刚刚记下"; case INSUFFICIENT: return "还需要了解"; case EARLY_LINK: return "可能存在联系"; case REPEATED_PATTERN: return "多次出现"; default: return "已形成个人规律"; }
    }
    private String displayProgress(TopicProgress value) {
        if (value == null) return "等待确认";
        switch (value) { case FOLLOWING: return "持续关注"; case OBSERVING: return "正在观察"; case PAUSED: return "已暂停"; case ARCHIVED: return "已归档"; default: return "等待确认"; }
    }
    private String displayRisk(RiskStatus value) {
        if (value == RiskStatus.PROFESSIONAL_HELP) return "建议寻求专业帮助";
        if (value == RiskStatus.WATCH) return "值得继续留意";
        return "暂无额外风险提示";
    }
    private String displayAccuracy(FeedbackAccuracy value) {
        if (value == FeedbackAccuracy.ACCURATE) return "准确";
        if (value == FeedbackAccuracy.INACCURATE) return "不太准确";
        if (value == FeedbackAccuracy.DID_NOT_HAPPEN) return "这次没有发生";
        return "暂时不确定";
    }
    private String displayChange(String reason) {
        if ("DIGEST_ACCEPTED".equals(reason)) return "用户接受整理草稿";
        if ("ACTION_FEEDBACK".equals(reason)) return "收到一次行动反馈";
        if ("USER_PROGRESS_CHANGED".equals(reason)) return "用户调整观察进度";
        return "理解已更新";
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private void set(int id, String value) { ((TextView) findViewById(id)).setText(value == null ? "" : value); }
}
