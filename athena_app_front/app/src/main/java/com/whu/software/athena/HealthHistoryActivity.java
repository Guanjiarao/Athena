package com.whu.software.athena;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

/** Presentational history for the Mock-first product walkthrough. */
public class HealthHistoryActivity extends AppCompatActivity {
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_health_history);
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
    }
}
