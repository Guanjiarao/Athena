package com.whu.software.athena;

import android.os.Bundle;
import android.widget.Switch;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

/** User-facing cognition data boundary. Backend selection is deliberately not a privacy toggle. */
public class DataPrivacyActivity extends AppCompatActivity {
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState); setContentView(R.layout.activity_data_privacy);
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        android.content.SharedPreferences privacy = getSharedPreferences("athena_privacy_demo", MODE_PRIVATE);
        Switch personalization = findViewById(R.id.switch_personalization);
        personalization.setChecked(privacy.getBoolean("personalization", true));
        personalization.setOnCheckedChangeListener((button, checked) -> {
            privacy.edit().putBoolean("personalization", checked).apply();
            Toast.makeText(this, checked ? "个性化内容已开启" : "个性化内容已关闭，记录不受影响", Toast.LENGTH_SHORT).show();
        });
    }
}
