package com.whu.software.athena;

import android.Manifest;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

public class SmartBandActivity extends AppCompatActivity {

    private static final String TAG = "SmartBandActivity";
    private static final int RC_PERMISSIONS = 1001;

    private TextView tvSteps, tvCalories, tvHeartRate, tvSleep;
    private ProgressBar pbSteps, pbCalories;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, ">>> onCreate");
        setContentView(R.layout.activity_smart_band);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        bindViews();
        bindFallbackData();
        checkPermissionsThenAuth();
    }

    private void bindViews() {
        Log.d(TAG, ">>> bindViews");
        tvSteps = findViewById(R.id.tv_steps);
        tvCalories = findViewById(R.id.tv_calories);
        pbSteps = findViewById(R.id.pb_steps);
        pbCalories = findViewById(R.id.pb_calories);
        tvHeartRate = findViewById(R.id.tv_heart_rate);
        tvSleep = findViewById(R.id.tv_sleep);

    }

    private void bindFallbackData() {
        tvSteps.setText("--");
        tvCalories.setText("--");
        tvHeartRate.setText("--");
        tvSleep.setText("--");
        pbSteps.setMax(10000);
        pbSteps.setProgress(0);
        pbCalories.setMax(500);
        pbCalories.setProgress(0);
    }

    // ─────────────────────── 权限 ───────────────────────

    private void checkPermissionsThenAuth() {
        Log.d(TAG, ">>> checkPermissionsThenAuth");
        List<String> needed = new ArrayList<>();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION)
                    != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.ACTIVITY_RECOGNITION);
            }
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS)
                != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.BODY_SENSORS);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.BLUETOOTH_CONNECT);
            }
        }

        Log.d(TAG, "Permissions needed: " + needed);
        if (needed.isEmpty()) {
            startSdkAuth();
        } else {
            ActivityCompat.requestPermissions(this,
                    needed.toArray(new String[0]), RC_PERMISSIONS);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == RC_PERMISSIONS) {
            boolean allGranted = true;
            for (int i = 0; i < grantResults.length; i++) {
                Log.d(TAG, "Permission " + permissions[i] + " = "
                        + (grantResults[i] == PackageManager.PERMISSION_GRANTED ? "GRANTED" : "DENIED"));
                if (grantResults[i] != PackageManager.PERMISSION_GRANTED) allGranted = false;
            }
            if (allGranted) {
                startSdkAuth();
            } else {
                Toast.makeText(this,"权限被拒绝，部分功能不可见", Toast.LENGTH_SHORT).show();
                Log.w(TAG, "Some permissions denied, proceeding anyway");
                startSdkAuth();
            }
        }
    }

    // ─────────────────────── SDK 授权 ───────────────────────

    /**
     * 静默分流入口：
     * - 本地已记录授权成功 → 直接拉取数据，不再弹出授权界面
     * - 首次使用或授权已失效 → 调起 OPPO 授权弹窗
     */
    private void startSdkAuth() {
        Log.d(TAG, ">>> startSdkAuth");
        SharedPreferences prefs = getSharedPreferences(OppoBandManager.PREFS_NAME, MODE_PRIVATE);
        boolean isAuthorized = prefs.getBoolean(OppoBandManager.KEY_OPPO_AUTHORIZED, false);
        Log.d(TAG, "OPPO 本地授权状态: " + isAuthorized);

        if (isAuthorized) {
            // 已授权：静默直接拉取健康数据，绝不再弹窗
            Log.i(TAG, "已授权，跳过弹窗，直接获取健康数据");
            fetchAllData();
        } else {
            // 未授权（首次 or 授权失效后重置）：调起 OPPO 授权界面
            Log.i(TAG, "未授权，调起 OPPO 授权界面");
            OppoBandManager.getInstance().requestAuth(this, this::fetchAllData);
        }
    }

    // ─────────────────────── 拉取数据 ───────────────────────

    private void fetchAllData() {
        Log.d(TAG, ">>> fetchAllData - starting all data queries");
        OppoBandManager mgr = OppoBandManager.getInstance();

        mgr.readUserInfo();
        fetchActivityData();
        fetchHeartRate();
        fetchSleep();
    }

    /**
     * 检查数据接口失败码是否属于"授权失效"类型（Token 过期 / 用户在 OPPO 侧撤销授权）。
     * 如果是，则重置本地 flag，下次进入页面将重新弹出授权界面。
     */
    private void handleDataFailure(int code, String scene) {
        Log.e(TAG, "<<< " + scene + " onFailure: code=" + code);
        if (code == 401 || code == -2) {
            OppoBandManager.getInstance().resetAuthFlag(this);
            runOnUiThread(() ->
                    Toast.makeText(this,
                            "OPPO 授权已过期，请重新进入页面连接设备",
                            Toast.LENGTH_LONG).show()
            );
        }
    }

    private void fetchActivityData() {
        Log.d(TAG, ">>> fetchActivityData");
        OppoBandManager.getInstance().getTodayActivityData(new OppoBandManager.DataCallback() {
            @Override
            public void onSuccess(int steps, int calories) {
                Log.d(TAG, "<<< fetchActivityData onSuccess: steps=" + steps + " cal=" + calories);
                runOnUiThread(() -> {
                    tvSteps.setText(String.format("%,d", steps));
                    pbSteps.setProgress(Math.min(steps, 10000));
                    tvCalories.setText(String.valueOf(calories));
                    pbCalories.setProgress(Math.min(calories, 500));
                });
            }

            @Override
            public void onFailure(int code, String msg) {
                handleDataFailure(code, "fetchActivityData");
                runOnUiThread(() ->
                        Toast.makeText(SmartBandActivity.this,
                                "获取运动数据失败: " + msg, Toast.LENGTH_SHORT).show()
                );
            }
        });
    }

    private void fetchHeartRate() {
        Log.d(TAG, ">>> fetchHeartRate");
        OppoBandManager.getInstance().getTodayHeartRate(new OppoBandManager.HeartRateCallback() {
            @Override
            public void onSuccess(int avgHeartRate) {
                Log.d(TAG, "<<< fetchHeartRate onSuccess: avgHr=" + avgHeartRate);
                runOnUiThread(() -> tvHeartRate.setText(avgHeartRate > 0 ? String.valueOf(avgHeartRate) : "--"));
            }

            @Override
            public void onFailure(int code, String msg) {
                handleDataFailure(code, "fetchHeartRate");
                runOnUiThread(() ->
                        Toast.makeText(SmartBandActivity.this,
                                "获取心率失败: " + msg, Toast.LENGTH_SHORT).show()
                );
            }
        });
    }

    private void fetchSleep() {
        Log.d(TAG, ">>> fetchSleep");
        OppoBandManager.getInstance().getTodaySleep(new OppoBandManager.SleepCallback() {
            @Override
            public void onSuccess(int totalMinutes) {
                Log.d(TAG, "<<< fetchSleep onSuccess: totalMin=" + totalMinutes);
                runOnUiThread(() -> {
                    if (totalMinutes > 0) {
                        int h = totalMinutes / 60;
                        int m = totalMinutes % 60;
                        tvSleep.setText(h + "h " + m + "m");
                    } else {
                        tvSleep.setText("--");
                    }
                });
            }

            @Override
            public void onFailure(int code, String msg) {
                handleDataFailure(code, "fetchSleep");
            }
        });
    }

}
