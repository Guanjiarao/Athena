package com.whu.software.athena;

import android.os.Bundle;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.whu.software.athena.cognition.CognitionModels.Feedback;
import com.whu.software.athena.cognition.CognitionModels.FeedbackAccuracy;
import com.whu.software.athena.cognition.CognitionRepository;
import com.whu.software.athena.cognition.CognitionRepositoryProvider;

public class CognitionFeedbackActivity extends AppCompatActivity {
    public static final String EXTRA_ACTION_ID = "action_id";
    private CognitionRepository repository;
    private long actionId;
    private RadioGroup accuracyGroup;
    private EditText note;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cognition_feedback);
        repository = CognitionRepositoryProvider.get(this);
        actionId = getIntent().getLongExtra(EXTRA_ACTION_ID, -1);
        accuracyGroup = findViewById(R.id.group_feedback_accuracy);
        note = findViewById(R.id.et_feedback_note);
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_submit_feedback).setOnClickListener(v -> submit());
    }

    private void submit() {
        FeedbackAccuracy accuracy;
        int checked = accuracyGroup.getCheckedRadioButtonId();
        if (checked == R.id.feedback_accurate) accuracy = FeedbackAccuracy.ACCURATE;
        else if (checked == R.id.feedback_inaccurate) accuracy = FeedbackAccuracy.INACCURATE;
        else if (checked == R.id.feedback_did_not_happen) accuracy = FeedbackAccuracy.DID_NOT_HAPPEN;
        else accuracy = FeedbackAccuracy.NOT_SURE;
        findViewById(R.id.btn_submit_feedback).setEnabled(false);
        repository.submitFeedback(actionId, accuracy, true, note.getText().toString().trim(),
                new CognitionRepository.Callback<Feedback>() {
                    @Override public void onSuccess(Feedback value) {
                        Toast.makeText(CognitionFeedbackActivity.this, "反馈已保存", Toast.LENGTH_SHORT).show();
                        finish();
                    }
                    @Override public void onError(String message) {
                        findViewById(R.id.btn_submit_feedback).setEnabled(true);
                        Toast.makeText(CognitionFeedbackActivity.this, message, Toast.LENGTH_SHORT).show();
                    }
                });
    }
}
