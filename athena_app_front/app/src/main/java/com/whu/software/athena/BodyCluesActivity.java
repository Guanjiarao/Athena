package com.whu.software.athena;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.whu.software.athena.cognition.CognitionModels.*;
import com.whu.software.athena.cognition.CognitionRepository;
import com.whu.software.athena.cognition.CognitionRepositoryProvider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Contract V1 inbox: pending clues, organized knowledge/topics, and questions. */
public class BodyCluesActivity extends AppCompatActivity {
    private CognitionRepository repository;
    private LinearLayout list;
    private TextView stateText;
    private View digestButton;
    private View loadMoreButton;
    private View retryButton;
    private final List<String> visibleClueIds = new ArrayList<>();
    private ClueListView section = ClueListView.PENDING;
    private Inbox inbox;
    private int page = 1;
    private boolean hasMore;
    private String failedTaskId;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_body_clues);
        repository = CognitionRepositoryProvider.get(this);
        list = findViewById(R.id.layout_clue_list); stateText = findViewById(R.id.tv_clue_state);
        digestButton = findViewById(R.id.btn_digest_visible);
        loadMoreButton = findViewById(R.id.btn_load_more_clues);
        retryButton = findViewById(R.id.btn_retry_digest);
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        findViewById(R.id.tab_pending).setOnClickListener(v -> show(ClueListView.PENDING));
        findViewById(R.id.tab_organized).setOnClickListener(v -> show(ClueListView.ORGANIZED));
        findViewById(R.id.tab_questions).setOnClickListener(v -> show(ClueListView.QUESTIONS));
        digestButton.setOnClickListener(v -> createDigest());
        loadMoreButton.setOnClickListener(v -> loadMore());
        retryButton.setOnClickListener(v -> retryFailedTask());
        findViewById(R.id.btn_pending_digests).setOnClickListener(v -> openPendingDigest());
        loadInbox();
    }

    @Override protected void onResume() { super.onResume(); if (repository != null) loadInbox(); }

    private void loadInbox() {
        stateText.setVisibility(View.VISIBLE); stateText.setText("正在读取…"); list.removeAllViews();
        repository.getInbox(new CognitionRepository.Callback<Inbox>() {
            @Override public void onSuccess(Inbox value) { inbox = value == null ? new Inbox() : value; show(section); }
            @Override public void onError(String message) { stateText.setText(message); }
        });
    }

    private void show(ClueListView next) {
        section = next; page = 1; list.removeAllViews(); visibleClueIds.clear();
        if (inbox == null) return;
        if (section == ClueListView.PENDING) {
            for (Clue clue : safe(inbox.pendingClues)) { visibleClueIds.add(clue.id); list.addView(clueView(clue)); }
            setEmpty(inbox.pendingClues.isEmpty(), "还没有待整理线索\n从文章中标记“和我有关”，或者完成一次身体记录。");
        } else if (section == ClueListView.QUESTIONS) {
            for (Clue clue : safe(inbox.questions)) list.addView(clueView(clue));
            setEmpty(inbox.questions.isEmpty(), "还没有保存的问题");
        } else {
            for (Topic topic : safe(inbox.topics)) list.addView(topicView(topic));
            for (Digest digest : safe(inbox.knowledgeDigests)) list.addView(knowledgeDigestView(digest));
            for (Clue clue : safe(inbox.knowledgeClues)) list.addView(clueView(clue));
            boolean empty = inbox.topics.isEmpty() && inbox.knowledgeDigests.isEmpty() && inbox.knowledgeClues.isEmpty();
            setEmpty(empty, "这里暂时没有已整理内容");
        }
        digestButton.setVisibility(section == ClueListView.PENDING && !visibleClueIds.isEmpty() ? View.VISIBLE : View.GONE);
        hasMore = inbox.hasMore && section != ClueListView.ORGANIZED;
        loadMoreButton.setVisibility(hasMore ? View.VISIBLE : View.GONE);
        TextView pendingTab = findViewById(R.id.tab_pending); pendingTab.setText("待整理 " + inbox.counts.pending);
        TextView organizedTab = findViewById(R.id.tab_organized); organizedTab.setText("已整理 " + (inbox.counts.organizedTopics + inbox.counts.organizedKnowledge));
        TextView questionsTab = findViewById(R.id.tab_questions); questionsTab.setText("我的疑问 " + inbox.counts.questions);
    }

    private void setEmpty(boolean empty, String text) { stateText.setVisibility(empty ? View.VISIBLE : View.GONE); stateText.setText(text); }

    private View clueView(Clue clue) {
        String title = clue.intent == ClueIntent.QUESTION ? "我的疑问" : clue.type == ClueType.BODY_RECORD ? "身体记录" : "身体线索";
        String relation = clue.relationType == RelationType.CURRENT ? "我现在有类似情况" : clue.relationType == RelationType.PAST ? "以前出现过"
                : clue.relationType == RelationType.KNOWLEDGE_ONLY ? "只保存为知识" : "不确定，继续观察";
        String question = clue.intent == ClueIntent.QUESTION ? "\n问题：" + text(clue.questionText) : "";
        return card(title + "\n" + text(clue.selectedText) + question + "\n关系：" + relation
                + "\n来源：" + text(clue.articleTitle) + "\n状态：" + status(clue.status));
    }

    private View topicView(Topic topic) {
        TextView view = card("认知主题 · " + text(topic.title) + "\n" + text(topic.stageUnderstanding) + "\n证据 " + topic.evidenceCount + " 条 →");
        view.setOnClickListener(v -> startActivity(new Intent(this, CognitionTopicActivity.class).putExtra(CognitionTopicActivity.EXTRA_TOPIC_ID, topic.id)));
        return view;
    }

    private View knowledgeDigestView(Digest digest) {
        TextView view = card("已保存知识 · " + text(digest.title) + "\n" + text(digest.commonPoint));
        view.setOnClickListener(v -> startActivity(new Intent(this, CognitionDigestActivity.class).putExtra(CognitionDigestActivity.EXTRA_DIGEST_ID, digest.id)));
        return view;
    }

    private TextView card(String value) {
        TextView view = new TextView(this); view.setText(value); view.setTextColor(Color.rgb(45, 53, 50)); view.setTextSize(15);
        view.setLineSpacing(0, 1.15f); view.setBackgroundColor(Color.WHITE); int p = dp(16); view.setPadding(p, p, p, p);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.bottomMargin = dp(10); view.setLayoutParams(params); return view;
    }

    private void createDigest() {
        digestButton.setEnabled(false);
        repository.createDigestTask(new ArrayList<>(visibleClueIds), new CognitionRepository.Callback<DigestTask>() {
            @Override public void onSuccess(DigestTask task) {
                digestButton.setEnabled(true);
                handleTask(task, 0);
            }
            @Override public void onError(String message) { digestButton.setEnabled(true); Toast.makeText(BodyCluesActivity.this, message, Toast.LENGTH_LONG).show(); }
        });
    }

    private void handleTask(DigestTask task, int pollCount) {
        if (task == null) return;
        if (task.status == DigestTaskStatus.FAILED) {
            failedTaskId = task.taskId; retryButton.setVisibility(failedTaskId == null ? View.GONE : View.VISIBLE);
            Toast.makeText(this, "整理失败，原始线索仍然保留", Toast.LENGTH_LONG).show(); return;
        }
        if (task.status == DigestTaskStatus.SUCCEEDED && task.digestId != null) {
            failedTaskId = null; retryButton.setVisibility(View.GONE);
            startActivity(new Intent(this, CognitionDigestActivity.class).putExtra(CognitionDigestActivity.EXTRA_DIGEST_ID, task.digestId)); return;
        }
        if ((task.status == DigestTaskStatus.PENDING || task.status == DigestTaskStatus.RUNNING) && task.taskId != null && pollCount < 10) {
            stateText.setVisibility(View.VISIBLE); stateText.setText("Athena 正在整理…");
            handler.postDelayed(() -> repository.getDigestTask(task.taskId, new CognitionRepository.Callback<DigestTask>() {
                @Override public void onSuccess(DigestTask value) { handleTask(value, pollCount + 1); }
                @Override public void onError(String message) { stateText.setText(message); }
            }), 1200);
        }
    }

    private void retryFailedTask() {
        if (failedTaskId == null) return; retryButton.setEnabled(false);
        repository.retryDigestTask(failedTaskId, new CognitionRepository.Callback<DigestTask>() {
            @Override public void onSuccess(DigestTask value) { retryButton.setEnabled(true); handleTask(value, 0); }
            @Override public void onError(String message) { retryButton.setEnabled(true); Toast.makeText(BodyCluesActivity.this, message, Toast.LENGTH_LONG).show(); }
        });
    }

    private void loadMore() {
        if (!hasMore) return; loadMoreButton.setEnabled(false);
        repository.listClues(section, page + 1, 20, new CognitionRepository.Callback<Page<Clue>>() {
            @Override public void onSuccess(Page<Clue> result) {
                page++; loadMoreButton.setEnabled(true);
                for (Clue clue : result.data) { if (section == ClueListView.PENDING) visibleClueIds.add(clue.id); list.addView(clueView(clue)); }
                hasMore = (long) page * 20 < result.total; loadMoreButton.setVisibility(hasMore ? View.VISIBLE : View.GONE);
            }
            @Override public void onError(String message) { loadMoreButton.setEnabled(true); Toast.makeText(BodyCluesActivity.this, message, Toast.LENGTH_SHORT).show(); }
        });
    }

    @Override protected void onDestroy() { handler.removeCallbacksAndMessages(null); super.onDestroy(); }

    private void openPendingDigest() {
        if (inbox != null && inbox.activeDigest != null) {
            startActivity(new Intent(this, CognitionDigestActivity.class).putExtra(CognitionDigestActivity.EXTRA_DIGEST_ID, inbox.activeDigest.id)); return;
        }
        repository.listReadyDigests(1, 20, new CognitionRepository.Callback<Page<Digest>>() {
            @Override public void onSuccess(Page<Digest> values) {
                if (values.data.isEmpty()) Toast.makeText(BodyCluesActivity.this, "没有待确认草稿", Toast.LENGTH_SHORT).show();
                else startActivity(new Intent(BodyCluesActivity.this, CognitionDigestActivity.class).putExtra(CognitionDigestActivity.EXTRA_DIGEST_ID, values.data.get(0).id));
            }
            @Override public void onError(String message) { Toast.makeText(BodyCluesActivity.this, message, Toast.LENGTH_SHORT).show(); }
        });
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private static String text(String value) { return value == null || value.trim().isEmpty() ? "未填写" : value; }
    private static String status(ClueStatus value) { if (value == ClueStatus.PROCESSING) return "正在整理"; if (value == ClueStatus.ORGANIZED) return "已整理"; if (value == ClueStatus.DISMISSED) return "已撤销"; return "等待整理"; }
    private static <T> List<T> safe(List<T> values) { return values == null ? Collections.emptyList() : values; }
}
