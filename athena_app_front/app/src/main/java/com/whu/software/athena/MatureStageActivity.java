package com.whu.software.athena;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Arrays;
import java.util.List;

/**
 * 22~55 岁“成熟阶段”分类列表页。
 */
public class MatureStageActivity extends AppCompatActivity {

    private static final String TITLE_SKINCARE = "\u62a4\u80a4\u6307\u5357";
    private static final String TITLE_PREPARE = "\u79d1\u5b66\u5907\u5b55";
    private static final String TITLE_AVOID = "\u907f\u5b55\u6307\u5357";
    private static final String TITLE_PREGNANCY_CARE = "\u5b55\u671f\u62a4\u7406";
    private static final String TITLE_POSTPARTUM_RECOVERY = "\u6708\u5b50\u671f\u6062\u590d";
    private static final String TITLE_FERTILITY_SCIENCE = "\u751f\u80b2\u79d1\u666e";

    private static final int TYPE_PREGNANCY_CARE = 53;
    private static final int TYPE_POSTPARTUM_RECOVERY = 54;
    private static final int TYPE_FERTILITY_SCIENCE = 55;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mature_stage);

        View btnBack = findViewById(R.id.btn_back);
        RecyclerView rvCategories = findViewById(R.id.rv_categories);

        btnBack.setOnClickListener(v -> finish());
        rvCategories.setLayoutManager(new LinearLayoutManager(this));

        List<String> categories = Arrays.asList(
                TITLE_SKINCARE,
                TITLE_PREPARE,
                TITLE_AVOID,
                TITLE_PREGNANCY_CARE,
                TITLE_POSTPARTUM_RECOVERY,
                TITLE_FERTILITY_SCIENCE
        );

        MatureStageAdapter adapter = new MatureStageAdapter(categories);
        adapter.setOnCategoryClickListener(this::openCategoryPage);
        rvCategories.setAdapter(adapter);
    }

    private void openCategoryPage(String title) {
        Intent intent;
        if (TITLE_SKINCARE.equals(title)) {
            intent = new Intent(this, SkincareArticleListActivity.class);
        } else if (TITLE_PREPARE.equals(title)) {
            intent = new Intent(this, PrepareArticleListActivity.class);
        } else if (TITLE_AVOID.equals(title)) {
            intent = new Intent(this, AvoidArticleListActivity.class);
        } else if (TITLE_PREGNANCY_CARE.equals(title)) {
            intent = SkincareArticleListActivity.createIntent(this, title, TYPE_PREGNANCY_CARE);
        } else if (TITLE_POSTPARTUM_RECOVERY.equals(title)) {
            intent = SkincareArticleListActivity.createIntent(this, title, TYPE_POSTPARTUM_RECOVERY);
        } else if (TITLE_FERTILITY_SCIENCE.equals(title)) {
            intent = SkincareArticleListActivity.createIntent(this, title, TYPE_FERTILITY_SCIENCE);
        } else {
            return;
        }
        startActivity(intent);
    }
}
