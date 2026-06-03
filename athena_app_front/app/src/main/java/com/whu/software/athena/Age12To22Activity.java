package com.whu.software.athena;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

/**
 * 12~22岁 专属静态详情页
 */
public class Age12To22Activity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_age_12_to_22);

        View btnBack = findViewById(R.id.btn_back);
        View cardModule1 = findViewById(R.id.card_module_1);
        View cardModule2 = findViewById(R.id.card_module_2);

        btnBack.setOnClickListener(v -> finish());

        cardModule1.setOnClickListener(v ->
                startActivity(new Intent(this, MenstrualArticleListActivity.class)));

        cardModule2.setOnClickListener(v ->
                startActivity(new Intent(this, BisexualArticleListActivity.class)));
    }
}
