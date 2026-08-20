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

/** Shows evidence before asking for one of the three explicit user decisions. */
public class CognitionDigestActivity extends AppCompatActivity {

    public static final String EXTRA_DIGEST_ID = "digest_id";
    private CognitionRepository repository;
    private long digestId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cognition_digest);
        repository = CognitionRepositoryProvider.get(this);
        digestId = getIntent().getLongExtra(EXTRA_DIGEST_ID, -1);
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_accept_digest).setOnClickListener(v -> decide(DigestDecision.ACCEPT_TOPIC));
        findViewById(R.id.btn_save_knowledge).setOnClickListener(v -> decide(DigestDecision.SAVE_KNOWLEDGE));
        findViewById(R.id.btn_reject_digest).setOnClickListener(v -> decide(DigestDecision.REJECT));
        load();
    }

    private void load() {
        repository.getDigest(digestId, new CognitionRepository.Callback<Digest>() {
            @Override public void onSuccess(Digest value) {
                set(R.id.tv_digest_title, value.title);
                set(R.id.tv_digest_evidence, "使用了 " + value.evidence.size() + " 条线索\n" + value.commonPoint);
                set(R.id.tv_digest_link, value.possibleLink);
                set(R.id.tv_digest_uncertainty, value.uncertainty);
                set(R.id.tv_digest_action, value.suggestedAction);
                boolean pending = value.status == DigestStatus.PENDING_CONFIRMATION;
                findViewById(R.id.layout_digest_decisions).setVisibility(pending ? View.VISIBLE : View.GONE);
            }
            @Override public void onError(String message) { Toast.makeText(CognitionDigestActivity.this, message, Toast.LENGTH_SHORT).show(); }
        });
    }

    private void decide(DigestDecision decision) {
        setButtonsEnabled(false);
        repository.decideDigest(digestId, decision, null, new CognitionRepository.Callback<DigestDecisionResult>() {
            @Override public void onSuccess(DigestDecisionResult value) {
                if (value.topicId != null) {
                    startActivity(new Intent(CognitionDigestActivity.this, CognitionTopicActivity.class)
                            .putExtra(CognitionTopicActivity.EXTRA_TOPIC_ID, value.topicId));
                }
                finish();
            }
            @Override public void onError(String message) {
                setButtonsEnabled(true);
                Toast.makeText(CognitionDigestActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setButtonsEnabled(boolean enabled) {
        findViewById(R.id.btn_accept_digest).setEnabled(enabled);
        findViewById(R.id.btn_save_knowledge).setEnabled(enabled);
        findViewById(R.id.btn_reject_digest).setEnabled(enabled);
    }

    private void set(int id, String text) { ((TextView) findViewById(id)).setText(text == null ? "" : text); }
}
