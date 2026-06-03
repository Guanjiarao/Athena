package com.whu.software.athena;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Arrays;
import java.util.List;

/**
 * 55 岁以上分类列表页。
 */
public class Age55PlusActivity extends AppCompatActivity {

    private static final String TITLE_DISEASE_PREVENTION = "\u75be\u75c5\u9884\u9632";
    private static final String TITLE_DISEASE_SIGN = "\u75be\u75c5\u5148\u5146";
    private static final String TITLE_HEALTH_PRESERVATION = "\u79d1\u5b66\u517b\u751f";
    private static final String TITLE_MENOPAUSE = "\u6b63\u89c6\u66f4\u5e74\u671f";

    private static final int TYPE_HEALTH_PRESERVATION = 72;
    private static final int TYPE_MENOPAUSE = 73;

    private static final List<String> CATEGORY_TITLES = Arrays.asList(
            TITLE_DISEASE_PREVENTION,
            TITLE_DISEASE_SIGN,
            TITLE_HEALTH_PRESERVATION,
            TITLE_MENOPAUSE
    );

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_age_55_plus);

        View btnBack = findViewById(R.id.btn_back);
        RecyclerView rvCategories = findViewById(R.id.rv_categories);

        btnBack.setOnClickListener(v -> finish());
        rvCategories.setLayoutManager(new LinearLayoutManager(this));
        rvCategories.setAdapter(new Age55CategoryAdapter());
    }

    private static int categoryCoverDrawable(String title) {
        if (TITLE_DISEASE_PREVENTION.equals(title)) {
            return R.drawable.disease_prevention;
        }
        if (TITLE_DISEASE_SIGN.equals(title)) {
            return R.drawable.sign;
        }
        if (TITLE_HEALTH_PRESERVATION.equals(title)) {
            return R.drawable.preservation;
        }
        if (TITLE_MENOPAUSE.equals(title)) {
            return R.drawable.envisage;
        }
        return R.drawable.bg_category_cover;
    }

    private Intent buildCategoryIntent(String title) {
        if (TITLE_DISEASE_PREVENTION.equals(title)) {
            return new Intent(this, PreventArticleListActivity.class);
        }
        if (TITLE_DISEASE_SIGN.equals(title)) {
            return new Intent(this, SignArticleListActivity.class);
        }
        if (TITLE_HEALTH_PRESERVATION.equals(title)) {
            return SkincareArticleListActivity.createIntent(this, title, TYPE_HEALTH_PRESERVATION);
        }
        if (TITLE_MENOPAUSE.equals(title)) {
            return SkincareArticleListActivity.createIntent(this, title, TYPE_MENOPAUSE);
        }
        return null;
    }

    private class Age55CategoryAdapter extends RecyclerView.Adapter<Age55CategoryAdapter.VH> {

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_age_55_category, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            String title = CATEGORY_TITLES.get(position);
            holder.tvTitle.setText(title);
            holder.ivCover.setImageResource(categoryCoverDrawable(title));

            holder.itemView.setOnClickListener(v -> {
                Intent intent = buildCategoryIntent(title);
                if (intent != null) {
                    startActivity(intent);
                }
            });
        }

        @Override
        public int getItemCount() {
            return CATEGORY_TITLES.size();
        }

        class VH extends RecyclerView.ViewHolder {
            final ImageView ivCover;
            final TextView tvTitle;

            VH(@NonNull View itemView) {
                super(itemView);
                ivCover = itemView.findViewById(R.id.iv_category_cover);
                tvTitle = itemView.findViewById(R.id.tv_category_title);
            }
        }
    }
}
