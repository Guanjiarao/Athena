package com.whu.software.athena;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.whu.software.athena.cognition.CognitionModels.*;
import com.whu.software.athena.cognition.CognitionRepository;
import com.whu.software.athena.cognition.CognitionRepositoryProvider;

/** Evidence-first digest view with version-safe decisions. */
public class CognitionDigestActivity extends AppCompatActivity {
    public static final String EXTRA_DIGEST_ID = "digest_id";
    private CognitionRepository repository;
    private String digestId;
    private Digest digest;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState); setContentView(R.layout.activity_cognition_digest);
        repository = CognitionRepositoryProvider.get(this); digestId = getIntent().getStringExtra(EXTRA_DIGEST_ID);
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_accept_digest).setOnClickListener(v -> decide(DigestDecision.ACCEPT_AS_TOPIC));
        findViewById(R.id.btn_save_knowledge).setOnClickListener(v -> decide(DigestDecision.KEEP_AS_KNOWLEDGE));
        findViewById(R.id.btn_reject_digest).setOnClickListener(v -> decide(DigestDecision.REJECT));
        if (digestId == null || digestId.isEmpty()) { Toast.makeText(this, "草稿 ID 无效", Toast.LENGTH_SHORT).show(); finish(); return; }
        load();
    }

    private void load() {
        repository.getDigest(digestId, new CognitionRepository.Callback<Digest>() {
            @Override public void onSuccess(Digest value) {
                digest = value; if (value == null) return;
                set(R.id.tv_digest_title, value.title); set(R.id.tv_digest_evidence, evidenceText(value));
                set(R.id.tv_digest_link, value.possibleRelation); set(R.id.tv_digest_uncertainty, value.uncertainty);
                set(R.id.tv_digest_action, value.suggestedAction);
                findViewById(R.id.layout_digest_decisions).setVisibility(value.status == DigestStatus.READY ? View.VISIBLE : View.GONE);
                TextView evidence = findViewById(R.id.tv_digest_evidence);
                evidence.setOnClickListener(v -> openFirstArticle(value));
            }
            @Override public void onError(String message) { Toast.makeText(CognitionDigestActivity.this, message, Toast.LENGTH_LONG).show(); }
        });
    }

    private void decide(DigestDecision decision) {
        if (digest == null || digest.status != DigestStatus.READY) return;
        setButtonsEnabled(false);
        repository.decideDigest(digestId, decision, null, digest.version, new CognitionRepository.Callback<DigestDecisionResult>() {
            @Override public void onSuccess(DigestDecisionResult value) {
                if (value != null && value.topic != null && value.topic.id != null) {
                    startActivity(new Intent(CognitionDigestActivity.this, CognitionTopicActivity.class)
                            .putExtra(CognitionTopicActivity.EXTRA_TOPIC_ID, value.topic.id));
                } else Toast.makeText(CognitionDigestActivity.this,
                        decision == DigestDecision.KEEP_AS_KNOWLEDGE ? "已保存为知识" : "已拒绝这份草稿", Toast.LENGTH_SHORT).show();
                finish();
            }
            @Override public void onError(String message) { setButtonsEnabled(true); Toast.makeText(CognitionDigestActivity.this, message, Toast.LENGTH_LONG).show(); load(); }
        });
    }

    private String evidenceText(Digest value) {
        StringBuilder out = new StringBuilder("使用了 ").append(value.evidence == null ? 0 : value.evidence.size()).append(" 条证据\n");
        if (value.evidence != null) for (Evidence item : value.evidence) out.append("• ").append(safe(item.summary))
                .append(item.articleTitle == null ? "" : "\n  来源：《" + item.articleTitle + "》").append('\n');
        out.append("\n共同点：").append(safe(value.commonPoint));
        if (value.evidence != null) for (Evidence item : value.evidence) if (item.articleId != null) { out.append("\n\n点击查看来源文章"); break; }
        return out.toString();
    }

    private void openFirstArticle(Digest value) {
        if (value.evidence == null) return;
        for (Evidence item : value.evidence) if (item.articleId != null && !item.articleId.isEmpty()) {
            startActivity(new Intent(this, ArticleDetailActivity.class).putExtra("blog_id", item.articleId)
                    .putExtra("title", item.articleTitle).putExtra("article_type", item.articleType == null ? 100 : item.articleType)); return;
        }
    }

    private void setButtonsEnabled(boolean enabled) { findViewById(R.id.btn_accept_digest).setEnabled(enabled); findViewById(R.id.btn_save_knowledge).setEnabled(enabled); findViewById(R.id.btn_reject_digest).setEnabled(enabled); }
    private void set(int id, String text) { ((TextView) findViewById(id)).setText(safe(text)); }
    private static String safe(String value) { return value == null ? "" : value; }
}
