package com.whu.software.athena;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

/** Explainable Mock analysis; deliberately avoids claiming a real health inference. */
public class CognitionAnalysisDemoActivity extends AppCompatActivity {
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cognition_analysis_demo);
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
    }
}
