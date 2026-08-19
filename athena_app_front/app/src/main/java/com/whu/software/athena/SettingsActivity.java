package com.whu.software.athena;

import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.whu.software.athena.utils.UserDao;

import java.util.ArrayList;
import java.util.List;

public class SettingsActivity extends AppCompatActivity {

    private static final String TAG = "SettingsActivity";
    private static final String PREF_AUTH = "auth_prefs";
    private static final String KEY_TOKEN = "token";

    private View ivBack;
    private View itemEditProfile;
    private View itemNotifications;
    private View itemPrivacy;
    private View itemAbout;
    private ChipGroup chipGroupPreferences;
    private MaterialButton btnLogout;

    private UserDao userDao;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupStatusBar();
        setContentView(R.layout.activity_settings);

        userDao = new UserDao(this);

        initViews();
        setupClickListeners();
        restorePreferenceChips();
    }

    // ─────────────────────────────────────────────
    // 状态栏沉浸
    // ─────────────────────────────────────────────

    private void setupStatusBar() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window window = getWindow();
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(Color.WHITE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                window.getDecorView().setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                );
            }
        }
    }

    // ─────────────────────────────────────────────
    // 初始化视图
    // ─────────────────────────────────────────────

    private void initViews() {
        ivBack               = findViewById(R.id.iv_back);
        itemEditProfile      = findViewById(R.id.item_edit_profile);
        itemNotifications    = findViewById(R.id.item_notifications);
        itemPrivacy          = findViewById(R.id.item_privacy);
        itemAbout            = findViewById(R.id.item_about);
        chipGroupPreferences = findViewById(R.id.chip_group_preferences);
        btnLogout            = findViewById(R.id.btn_logout);
    }

    // ─────────────────────────────────────────────
    // 点击事件
    // ─────────────────────────────────────────────

    private void setupClickListeners() {
        ivBack.setOnClickListener(v -> finish());

        // 编辑个人资料
        itemEditProfile.setOnClickListener(v -> {
            Intent intent = new Intent(this, EditProfileActivity.class);
            startActivity(intent);
        });

        // 消息通知
        itemNotifications.setOnClickListener(v -> {
            // TODO: 跳转到消息通知设置页面
            Toast.makeText(this, "消息通知（开发中）", Toast.LENGTH_SHORT).show();
        });

        // 隐私设置
        itemPrivacy.setOnClickListener(v -> {
            startActivity(new Intent(this, DataPrivacyActivity.class));
        });

        // 关于我们
        itemAbout.setOnClickListener(v -> {
            // TODO: 跳转到关于我们页面
            Toast.makeText(this, "关于我们（开发中）", Toast.LENGTH_SHORT).show();
        });

        // Chip 选中变化 → 自动保存偏好
        chipGroupPreferences.setOnCheckedStateChangeListener((group, checkedIds) ->
                savePreferenceChips(checkedIds));

        // 退出登录
        btnLogout.setOnClickListener(v -> confirmLogout());
    }

    // ─────────────────────────────────────────────
    // 偏好标签持久化
    // ─────────────────────────────────────────────

    /**
     * 将选中的 Chip id 列表保存到 SharedPreferences。
     * 后续可替换为网络接口同步到后端。
     */
    private void savePreferenceChips(List<Integer> checkedIds) {
        StringBuilder sb = new StringBuilder();
        for (int id : checkedIds) {
            Chip chip = chipGroupPreferences.findViewById(id);
            if (chip != null) {
                if (sb.length() > 0) sb.append(",");
                sb.append(chip.getText().toString());
            }
        }
        getSharedPreferences("athena_prefs", MODE_PRIVATE)
                .edit()
                .putString("user_preferences", sb.toString())
                .apply();
    }

    /**
     * 从 SharedPreferences 恢复上次选中状态。
     */
    private void restorePreferenceChips() {
        String saved = getSharedPreferences("athena_prefs", MODE_PRIVATE)
                .getString("user_preferences", "");
        if (saved.isEmpty()) return;

        String[] labels = saved.split(",");
        for (String label : labels) {
            for (int i = 0; i < chipGroupPreferences.getChildCount(); i++) {
                View child = chipGroupPreferences.getChildAt(i);
                if (child instanceof Chip) {
                    Chip chip = (Chip) child;
                    if (chip.getText().toString().equals(label.trim())) {
                        chip.setChecked(true);
                    }
                }
            }
        }
    }

    // ─────────────────────────────────────────────
    // 退出登录
    // ─────────────────────────────────────────────

    private void confirmLogout() {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("退出登录")
                .setMessage("确定要退出当前账号吗？")
                .setPositiveButton("退出", (dialogInterface, which) -> performLogout())
                .setNegativeButton("取消", null)
                .create();

        dialog.setOnShowListener(dialogInterface -> {
            // 设置确认按钮颜色为红色，使其更加醒目
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(getResources().getColor(android.R.color.holo_red_dark));
            // 设置取消按钮颜色
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(getResources().getColor(android.R.color.darker_gray));
        });

        dialog.show();
    }

    private void performLogout() {
        try {
            userDao.open();
            String[] currentUser = userDao.getCurrentLoginUser();
            if (currentUser != null) {
                int result = userDao.updateLoginStatus(currentUser[1], 0);
                if (result > 0) {
                    // 清理 SharedPreferences 中的登录 token，确保 ProfileFragment 回到未登录界面
                    getSharedPreferences(PREF_AUTH, MODE_PRIVATE)
                            .edit()
                            .remove(KEY_TOKEN)
                            .apply();
                    Toast.makeText(this, "已退出登录", Toast.LENGTH_SHORT).show();
                    // 跳回登录页并清空回退栈
                    Intent intent = new Intent(this, LoginActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
                            | Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                } else {
                    Toast.makeText(this, "退出登录失败，请重试", Toast.LENGTH_SHORT).show();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "退出登录失败", e);
            Toast.makeText(this, "退出登录失败", Toast.LENGTH_SHORT).show();
        } finally {
            if (userDao != null) {
                userDao.close();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (userDao != null) {
            userDao.close();
        }
    }
}
