package com.whu.software.athena;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

/** Safe device-data presentation used when the private wearable SDK is unavailable. */
public class DeviceDataDemoActivity extends AppCompatActivity {
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_data_demo);
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
    }
}
