package com.whu.software.athena;

import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

/**
 * 0~12 岁身份选择中转页
 */
public class Age0To12RoleActivity extends AppCompatActivity {
    private static final int TYPE_CHILD = 10;
    private static final int TYPE_PARENT = 11;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 状态栏：白底深色图标，风格与科普/发布页统一
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Window window = getWindow();
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            window.setStatusBarColor(Color.WHITE);
            window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }

        setContentView(R.layout.activity_age_0_to_12_role);

        ImageView btnBack = findViewById(R.id.btn_back);
        View cardChild    = findViewById(R.id.card_child);
        View cardParent   = findViewById(R.id.card_parent);

        btnBack.setOnClickListener(v -> finish());

        cardChild.setOnClickListener(v -> {
            Intent intent = new Intent(this, KidsArticleListActivity.class);
            intent.putExtra("article_type", TYPE_CHILD);
            startActivity(intent);
        });

        cardParent.setOnClickListener(v -> {
            Intent intent = new Intent(this, ParentsArticleListActivity.class);
            intent.putExtra("article_type", TYPE_PARENT);
            startActivity(intent);
        });
    }
}

