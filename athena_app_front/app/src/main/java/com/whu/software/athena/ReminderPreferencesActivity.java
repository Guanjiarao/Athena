package com.whu.software.athena;

import android.os.Bundle;
import android.widget.Switch;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class ReminderPreferencesActivity extends AppCompatActivity {
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reminder_preferences);
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        bind(R.id.switch_daily, "daily", true);
        bind(R.id.switch_cognition, "cognition", true);
        bind(R.id.switch_professional, "professional", true);
        bind(R.id.switch_quiet, "quiet", true);
    }
    private void bind(int id, String key, boolean defaultValue) {
        Switch control = findViewById(id);
        android.content.SharedPreferences prefs = getSharedPreferences("athena_reminder_demo", MODE_PRIVATE);
        control.setChecked(prefs.getBoolean(key, defaultValue));
        control.setOnCheckedChangeListener((button, checked) -> {
            prefs.edit().putBoolean(key, checked).apply();
            Toast.makeText(this, "提醒偏好已保存在本机", Toast.LENGTH_SHORT).show();
        });
    }
}
