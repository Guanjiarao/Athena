package com.whu.software.athena;

import android.os.Bundle;
import android.os.Build;
import android.graphics.Color;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;

import com.google.android.material.navigation.NavigationView;
import com.whu.software.athena.features.chat.ChatFragment;
import com.whu.software.athena.features.privacy.PrivacyFragment;
import com.whu.software.athena.features.triage.TriageFragment;

/**
 * AI 多智能体工作台 Activity（工作台级重构版）。
 *
 * 架构改动：
 *  1. 废弃 ActionBarDrawerToggle + 原生 Toolbar，改用自定义 RelativeLayout Toolbar
 *  2. 左侧返回键：直接 finish() 退出整个 AI 模块
 *  3. 右侧汉堡图标：手动 openDrawer / closeDrawer
 *  4. 居中标题：随侧滑菜单选择动态切换
 *  5. 选中态高亮：通过 NavigationView.setCheckedItem + drawable selector 实现
 */
public class AIActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener {

    private DrawerLayout   drawer;
    private TextView       tvToolbarTitle;
    private NavigationView navigationView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 1. 绝对隐藏系统 ActionBar
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE);
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        // 2. 暴力将状态栏染成纯白色，并强制状态栏图标变黑
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Window window = getWindow();
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            window.setStatusBarColor(Color.parseColor("#FFFDFB"));
            window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }
        setContentView(R.layout.activity_ai);

        drawer         = findViewById(R.id.drawer_layout);
        tvToolbarTitle = findViewById(R.id.tv_toolbar_title);
        navigationView = findViewById(R.id.nav_view);

        navigationView.setNavigationItemSelectedListener(this);
        // 彻底关闭 NavigationView 对菜单图标的全局 tint，让 PNG 原色显示
        navigationView.setItemIconTintList(null);

        // 左侧返回键：退出整个 AI 模块
        ImageView btnBack = findViewById(R.id.btn_ai_back);
        btnBack.setOnClickListener(v -> finish());

        // 右侧菜单图标：打开 / 关闭侧滑抽屉
        ImageView btnOpenDrawer = findViewById(R.id.btn_open_drawer);
        btnOpenDrawer.setOnClickListener(v -> {
            if (drawer.isDrawerOpen(GravityCompat.START)) {
                drawer.closeDrawer(GravityCompat.START);
            } else {
                drawer.openDrawer(GravityCompat.START);
            }
        });

        // 物理返回键：有抽屉先关抽屉
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (drawer.isDrawerOpen(GravityCompat.START)) {
                    drawer.closeDrawer(GravityCompat.START);
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });

        // 默认：温声树洞
        if (savedInstanceState == null) {
            loadFragment(ChatFragment.newInstance("sister"),
                    getString(R.string.menu_sister));
            navigationView.setCheckedItem(R.id.nav_sister);
        }
    }

    // ── 侧滑菜单点击 ──────────────────────────────────────────────────────────

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        Fragment selectedFragment = null;
        String   title            = "";
        int id = item.getItemId();

        if (id == R.id.nav_sister) {
            selectedFragment = ChatFragment.newInstance("sister");
            title = getString(R.string.menu_sister);
        } else if (id == R.id.nav_pro) {
            selectedFragment = ChatFragment.newInstance("pro");
            title = getString(R.string.menu_pro);
        } else if (id == R.id.nav_triage) {
            selectedFragment = new TriageFragment();
            title = getString(R.string.menu_triage);
        } else if (id == R.id.nav_privacy) {
            selectedFragment = new PrivacyFragment();
            title = getString(R.string.menu_privacy);
        }

        if (selectedFragment != null) {
            loadFragment(selectedFragment, title);
        }

        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    // ── 切换 Fragment 并同步 Toolbar 标题 ─────────────────────────────────────

    private void loadFragment(Fragment fragment, String title) {
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.nav_host_fragment_content_main, fragment)
                .commit();
        // Toolbar 标题与侧滑菜单联动
        tvToolbarTitle.setText(title);
    }
}
