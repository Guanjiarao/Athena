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

import java.util.ArrayList;
import java.util.List;

/** Pending, organized, and question sections for all personal cognition inputs. */
public class BodyCluesActivity extends AppCompatActivity {

    private CognitionRepository repository;
    private LinearLayout list;
    private TextView stateText;
    private View digestButton;
    private final List<Long> visibleClueIds = new ArrayList<>();
    private ClueSection section = ClueSection.PENDING;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_body_clues);
        repository = CognitionRepositoryProvider.get(this);
        list = findViewById(R.id.layout_clue_list);
        stateText = findViewById(R.id.tv_clue_state);
        digestButton = findViewById(R.id.btn_digest_visible);
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        findViewById(R.id.tab_pending).setOnClickListener(v -> load(ClueSection.PENDING));
        findViewById(R.id.tab_organized).setOnClickListener(v -> load(ClueSection.ORGANIZED));
        findViewById(R.id.tab_questions).setOnClickListener(v -> load(ClueSection.QUESTIONS));
        digestButton.setOnClickListener(v -> createDigest());
        findViewById(R.id.btn_pending_digests).setOnClickListener(v -> openPendingDigest());
        load(section);
    }

    private void load(ClueSection next) {
        section = next;
        stateText.setVisibility(View.VISIBLE);
        stateText.setText("正在读取…");
        list.removeAllViews();
        visibleClueIds.clear();
        repository.listClues(section, new CognitionRepository.Callback<List<Clue>>() {
            @Override public void onSuccess(List<Clue> clues) {
                stateText.setVisibility(clues.isEmpty() ? View.VISIBLE : View.GONE);
                stateText.setText(section == ClueSection.QUESTIONS ? "还没有保存的问题" : "这里暂时没有内容");
                for (Clue clue : clues) {
                    visibleClueIds.add(clue.clueId);
                    list.addView(clueView(clue));
                }
                digestButton.setVisibility((section == ClueSection.PENDING || section == ClueSection.QUESTIONS)
                        && !visibleClueIds.isEmpty() ? View.VISIBLE : View.GONE);
            }
            @Override public void onError(String message) { stateText.setText(message); }
        });
    }

    private View clueView(Clue clue) {
        TextView view = new TextView(this);
        String title = clue.markIntent == MarkIntent.QUESTION ? "我的疑问" :
                clue.clueType == ClueType.BODY_RECORD ? "确认过的身体记录" : "身体线索";
        String relationship = clue.relationDetail == RelationDetail.CURRENT ? "我现在有类似情况" :
                clue.relationDetail == RelationDetail.HISTORICAL ? "以前出现过" :
                clue.relationDetail == RelationDetail.KNOWLEDGE_ONLY ? "只保存为知识" : "不确定，想继续观察";
        String source = clue.clueType == ClueType.BODY_RECORD ? "用户主动记录" : safe(clue.articleTitle)
                + " · " + safe(clue.sourceName);
        String question = clue.markIntent == MarkIntent.QUESTION
                ? "\n问题：" + safe(clue.questionText) + "\n这里只表示你关心这个问题，不代表你有对应症状。" : "";
        view.setText(title + "\n" + safe(clue.excerpt) + question
                + "\n关系：" + relationship + "\n来源：" + source
                + "\n当前状态：" + displayStatus(clue.status));
        view.setTextColor(Color.rgb(45, 53, 50));
        view.setTextSize(15);
        view.setLineSpacing(0, 1.15f);
        view.setBackgroundColor(Color.WHITE);
        int p = dp(16);
        view.setPadding(p, p, p, p);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(10);
        view.setLayoutParams(params);
        return view;
    }

    private void createDigest() {
        repository.createDigestTask(new ArrayList<>(visibleClueIds), new CognitionRepository.Callback<DigestTask>() {
            @Override public void onSuccess(DigestTask task) {
                if (task.status == DigestTaskStatus.FAILED) {
                    Toast.makeText(BodyCluesActivity.this, "整理失败，可稍后重试", Toast.LENGTH_SHORT).show();
                } else if (task.digestId != null) {
                    startActivity(new Intent(BodyCluesActivity.this, CognitionDigestActivity.class)
                            .putExtra(CognitionDigestActivity.EXTRA_DIGEST_ID, task.digestId));
                }
            }
            @Override public void onError(String message) { Toast.makeText(BodyCluesActivity.this, message, Toast.LENGTH_SHORT).show(); }
        });
    }

    private void openPendingDigest() {
        repository.listPendingDigests(new CognitionRepository.Callback<List<Digest>>() {
            @Override public void onSuccess(List<Digest> values) {
                if (values.isEmpty()) Toast.makeText(BodyCluesActivity.this, "没有待确认草稿", Toast.LENGTH_SHORT).show();
                else startActivity(new Intent(BodyCluesActivity.this, CognitionDigestActivity.class)
                        .putExtra(CognitionDigestActivity.EXTRA_DIGEST_ID, values.get(0).digestId));
            }
            @Override public void onError(String message) { Toast.makeText(BodyCluesActivity.this, message, Toast.LENGTH_SHORT).show(); }
        });
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private static String safe(String value) { return value == null || value.trim().isEmpty() ? "未填写" : value; }
    private static String displayStatus(ClueStatus status) {
        if (status == ClueStatus.ORGANIZED) return "已进入认知主题";
        if (status == ClueStatus.KNOWLEDGE_ONLY) return "已保存为知识";
        if (status == ClueStatus.IN_DIGEST) return "正在整理";
        if (status == ClueStatus.REJECTED) return "草稿已拒绝，原始表达已保留";
        return "等待整理";
    }
}
