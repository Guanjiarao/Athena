package com.whu.software.athena;

import android.os.Bundle;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.whu.software.athena.cognition.CognitionModels.ActionFeedbackResult;
import com.whu.software.athena.cognition.CognitionModels.FeedbackResult;
import com.whu.software.athena.cognition.CognitionRepository;
import com.whu.software.athena.cognition.CognitionRepositoryProvider;

import java.time.Instant;

public class CognitionFeedbackActivity extends AppCompatActivity {
    public static final String EXTRA_ACTION_ID = "action_id";
    public static final String EXTRA_TOPIC_ID = "topic_id";
    private CognitionRepository repository;
    private String actionId;
    private String topicId;
    private RadioGroup resultGroup;
    private EditText note;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState); setContentView(R.layout.activity_cognition_feedback);
        repository = CognitionRepositoryProvider.get(this); actionId = getIntent().getStringExtra(EXTRA_ACTION_ID); topicId = getIntent().getStringExtra(EXTRA_TOPIC_ID);
        resultGroup = findViewById(R.id.group_feedback_accuracy); note = findViewById(R.id.et_feedback_note);
        findViewById(R.id.btn_back).setOnClickListener(v -> finish()); findViewById(R.id.btn_submit_feedback).setOnClickListener(v -> submit());
        if (actionId == null || topicId == null) { Toast.makeText(this, "行动信息不完整，请返回刷新", Toast.LENGTH_LONG).show(); findViewById(R.id.btn_submit_feedback).setEnabled(false); }
    }

    private void submit() {
        ActionFeedbackResult result; int checked = resultGroup.getCheckedRadioButtonId();
        if (checked == R.id.feedback_accurate) result = ActionFeedbackResult.OCCURRED;
        else if (checked == R.id.feedback_did_not_happen) result = ActionFeedbackResult.NOT_OCCURRED;
        else if (checked == R.id.feedback_not_sure) result = ActionFeedbackResult.SKIPPED;
        else result = ActionFeedbackResult.UNCERTAIN;
        findViewById(R.id.btn_submit_feedback).setEnabled(false);
        repository.submitFeedback(actionId, topicId, result, note.getText().toString().trim(), Instant.now().toString(),
                new CognitionRepository.Callback<FeedbackResult>() {
                    @Override public void onSuccess(FeedbackResult value) { Toast.makeText(CognitionFeedbackActivity.this, "反馈已保存，理解已刷新", Toast.LENGTH_SHORT).show(); setResult(RESULT_OK); finish(); }
                    @Override public void onError(String message) { findViewById(R.id.btn_submit_feedback).setEnabled(true); Toast.makeText(CognitionFeedbackActivity.this, message, Toast.LENGTH_LONG).show(); }
                });
    }
}
