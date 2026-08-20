package com.whu.software.athena;

import android.os.Bundle;
import android.widget.Switch;
import android.widget.Toast;
import android.content.Intent;
import androidx.appcompat.app.AlertDialog;

import androidx.appcompat.app.AppCompatActivity;

import com.whu.software.athena.cognition.CognitionRepositoryProvider;

/** Plain-language cognition data boundary and environment control for the first handoff. */
public class DataPrivacyActivity extends AppCompatActivity {
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_data_privacy);
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        Switch cloud = findViewById(R.id.switch_cognition_cloud);
        boolean useHttp = getSharedPreferences("athena_cognition_config", MODE_PRIVATE)
                .getBoolean("use_http", false);
        cloud.setChecked(useHttp);
        cloud.setOnCheckedChangeListener((button, checked) -> {
            CognitionRepositoryProvider.useHttp(this, checked);
            Toast.makeText(this, checked ? "认知数据将使用登录账号的云端服务" : "认知演示数据仅保存在本机",
                    Toast.LENGTH_SHORT).show();
        });
        android.content.SharedPreferences privacy = getSharedPreferences("athena_privacy_demo", MODE_PRIVATE);
        Switch personalization = findViewById(R.id.switch_personalization);
        personalization.setChecked(privacy.getBoolean("personalization", true));
        personalization.setOnCheckedChangeListener((button, checked) -> {
            privacy.edit().putBoolean("personalization", checked).apply();
            Toast.makeText(this, checked ? "个性化内容已开启" : "个性化内容已关闭，记录不受影响", Toast.LENGTH_SHORT).show();
        });
        findViewById(R.id.btn_export_data).setOnClickListener(v -> exportData());
        findViewById(R.id.btn_clear_data).setOnClickListener(v -> confirmClear());
    }

    private void exportData() {
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("application/json");
        share.putExtra(Intent.EXTRA_SUBJECT, "Athena 本机认知数据导出");
        share.putExtra(Intent.EXTRA_TEXT, CognitionRepositoryProvider.exportDemo(this));
        startActivity(Intent.createChooser(share, "导出到"));
    }

    private void confirmClear() {
        new AlertDialog.Builder(this).setTitle("删除本机认知数据？")
                .setMessage("将清除线索、草稿、主题和反馈。此操作只影响本机 Mock，不能撤销。")
                .setPositiveButton("删除", (d, w) -> {
                    CognitionRepositoryProvider.clearDemo(this);
                    Toast.makeText(this, "本机认知演示数据已删除", Toast.LENGTH_LONG).show();
                }).setNegativeButton("取消", null).show();
    }
}
