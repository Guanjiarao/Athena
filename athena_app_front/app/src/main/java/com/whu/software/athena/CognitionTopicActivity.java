package com.whu.software.athena;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.whu.software.athena.cognition.CognitionModels.*;
import com.whu.software.athena.cognition.CognitionRepository;
import com.whu.software.athena.cognition.CognitionRepositoryProvider;

import java.util.Collections;
import java.util.List;

/** Composite topic detail supplied by GET /topics/{topicId}. */
public class CognitionTopicActivity extends AppCompatActivity {
    public static final String EXTRA_TOPIC_ID = "topic_id";
    private CognitionRepository repository;
    private String topicId;
    private TopicDetail detail;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState); setContentView(R.layout.activity_cognition_topic);
        repository = CognitionRepositoryProvider.get(this); topicId = getIntent().getStringExtra(EXTRA_TOPIC_ID);
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_topic_action).setOnClickListener(v -> openAction());
        // Progress mutation is not part of the handed-off API. Hide these controls until a contract exists.
        findViewById(R.id.btn_pause_topic).setVisibility(View.GONE); findViewById(R.id.btn_archive_topic).setVisibility(View.GONE);
    }

    @Override protected void onResume() { super.onResume(); loadTopics(); }

    private void loadTopics() {
        repository.listTopics(1, 20, new CognitionRepository.Callback<Page<Topic>>() {
            @Override public void onSuccess(Page<Topic> page) {
                List<Topic> topics = page == null ? Collections.emptyList() : page.data; renderTabs(topics);
                if ((topicId == null || topicId.isEmpty()) && !topics.isEmpty()) topicId = topics.get(0).id;
                if (topicId != null) load();
            }
            @Override public void onError(String message) { Toast.makeText(CognitionTopicActivity.this, message, Toast.LENGTH_SHORT).show(); }
        });
    }

    private void renderTabs(List<Topic> topics) {
        LinearLayout tabs = findViewById(R.id.layout_topic_tabs); tabs.removeAllViews();
        for (Topic item : topics) {
            TextView tab = new TextView(this); tab.setText(item.title); tab.setTextSize(14);
            boolean selected = item.id != null && item.id.equals(topicId);
            tab.setTextColor(selected ? Color.rgb(33, 83, 66) : Color.rgb(99, 105, 102));
            tab.setBackgroundColor(selected ? Color.rgb(233, 240, 234) : Color.TRANSPARENT); tab.setPadding(dp(14), dp(9), dp(14), dp(9));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, -2); params.setMarginEnd(dp(8)); tab.setLayoutParams(params);
            tab.setOnClickListener(v -> { topicId = item.id; renderTabs(topics); load(); }); tabs.addView(tab);
        }
    }

    private void load() {
        repository.getTopic(topicId, new CognitionRepository.Callback<TopicDetail>() {
            @Override public void onSuccess(TopicDetail value) { detail = value; render(value); }
            @Override public void onError(String message) { Toast.makeText(CognitionTopicActivity.this, message, Toast.LENGTH_LONG).show(); }
        });
    }

    private void render(TopicDetail value) {
        if (value == null || value.topic == null) return; Topic topic = value.topic;
        set(R.id.tv_topic_title, topic.title); set(R.id.tv_topic_status, maturity(topic.maturity) + " · " + progress(topic.userProgress) + " · " + risk(topic.riskStatus));
        set(R.id.tv_topic_summary, safe(topic.stageUnderstanding));
        set(R.id.tv_topic_link, value.sourceDigest == null ? "目前还没有可展示的关系说明" : safe(value.sourceDigest.possibleRelation) + "\n这只是当前可解释的联系，不代表因果关系。");
        set(R.id.tv_topic_evidence, evidenceText(value.evidence));
        set(R.id.tv_topic_uncertainty, topic.openQuestions == null || topic.openQuestions.isEmpty() ? "还需要更多连续记录" : join(topic.openQuestions));
        set(R.id.tv_topic_trusted_content, articlesText(value.relatedArticles));
        set(R.id.tv_topic_recent, value.nextAction == null ? "当前没有待完成行动。" : "下一步：" + safe(value.nextAction.title) + "\n" + safe(value.nextAction.description));
        set(R.id.tv_topic_feedback, feedbackText(value.recentFeedback));
        set(R.id.tv_topic_history, "版本 " + topic.version + (value.recentChange == null ? "" : " · " + value.recentChange));
        findViewById(R.id.btn_topic_action).setVisibility(value.nextAction != null && value.nextAction.status == ActionStatus.PENDING ? View.VISIBLE : View.GONE);
        findViewById(R.id.tv_topic_trusted_content).setOnClickListener(v -> openFirstArticle(value.relatedArticles));
    }

    private void openAction() {
        if (detail == null || detail.topic == null || detail.nextAction == null) return;
        startActivity(new Intent(this, CognitionFeedbackActivity.class)
                .putExtra(CognitionFeedbackActivity.EXTRA_ACTION_ID, detail.nextAction.id)
                .putExtra(CognitionFeedbackActivity.EXTRA_TOPIC_ID, detail.topic.id));
    }

    private void openFirstArticle(List<RelatedArticle> articles) {
        if (articles == null || articles.isEmpty()) return; RelatedArticle item = articles.get(0);
        startActivity(new Intent(this, ArticleDetailActivity.class).putExtra("blog_id", item.articleId)
                .putExtra("title", item.articleTitle).putExtra("article_type", item.articleType == null ? 100 : item.articleType));
    }

    private String evidenceText(List<Evidence> evidence) {
        if (evidence == null || evidence.isEmpty()) return "暂无证据；Athena 不会为了填满页面伪造依据。";
        StringBuilder out = new StringBuilder("来自 ").append(evidence.size()).append(" 条可追溯证据\n");
        for (Evidence item : evidence) out.append("• ").append(safe(item.summary)).append(item.articleTitle == null ? "" : " · 《" + item.articleTitle + "》").append('\n');
        return out.toString().trim();
    }

    private String articlesText(List<RelatedArticle> articles) {
        if (articles == null || articles.isEmpty()) return "暂无相关文章"; StringBuilder out = new StringBuilder();
        for (RelatedArticle item : articles) out.append("《").append(safe(item.articleTitle)).append("》\n"); out.append("点击查看来源文章"); return out.toString();
    }

    private String feedbackText(List<Feedback> feedback) {
        if (feedback == null || feedback.isEmpty()) return "尚未提交行动反馈。反馈只校正后续理解，不会自动形成诊断。";
        Feedback item = feedback.get(0); return "最近反馈：" + feedbackLabel(item.result) + (item.note == null || item.note.isEmpty() ? "" : "\n备注：" + item.note);
    }

    private String maturity(Maturity value) { if (value == Maturity.CLUE) return "刚刚记下"; if (value == Maturity.EARLY_LINK) return "可能存在联系"; if (value == Maturity.REPEATED_PATTERN) return "多次出现"; if (value == Maturity.RELATIVELY_STABLE) return "相对稳定"; return "还需要了解"; }
    private String progress(UserProgress value) { if (value == UserProgress.FOLLOWING) return "持续关注"; if (value == UserProgress.OBSERVING) return "正在观察"; if (value == UserProgress.PAUSED) return "已暂停"; if (value == UserProgress.ARCHIVED) return "已归档"; return "等待确认"; }
    private String risk(RiskStatus value) { if (value == RiskStatus.PROFESSIONAL_HELP) return "建议寻求专业帮助"; if (value == RiskStatus.WATCH) return "值得继续留意"; return "暂无额外风险提示"; }
    private String feedbackLabel(ActionFeedbackResult value) { if (value == ActionFeedbackResult.OCCURRED) return "确实发生"; if (value == ActionFeedbackResult.NOT_OCCURRED) return "没有发生"; if (value == ActionFeedbackResult.SKIPPED) return "跳过"; return "暂不确定"; }
    private String join(List<String> values) { StringBuilder out = new StringBuilder(); for (String value : values) out.append("• ").append(value).append('\n'); return out.toString().trim(); }
    private String safe(String value) { return value == null || value.isEmpty() ? "暂无" : value; }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private void set(int id, String value) { ((TextView) findViewById(id)).setText(value == null ? "" : value); }
}
