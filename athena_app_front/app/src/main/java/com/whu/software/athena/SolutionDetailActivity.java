package com.whu.software.athena;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.widget.NestedScrollView;

import com.whu.software.athena.core.LLMClient;
import com.whu.software.athena.core.Message;

import java.util.ArrayList;
import java.util.List;

import io.noties.markwon.Markwon;
import io.noties.markwon.image.glide.GlideImagesPlugin;

public class SolutionDetailActivity extends AppCompatActivity {

    public static final String EXTRA_ITEM_TITLE = "ITEM_TITLE";

    private TextView tvDetailTitle;
    private ProgressBar progressBar;
    private NestedScrollView scrollContent;
    private TextView tvDetailContent;

    private LLMClient llmClient;
    private Markwon markwon;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_solution_detail);

        tvDetailTitle = findViewById(R.id.tvDetailTitle);
        progressBar = findViewById(R.id.progressBar);
        scrollContent = findViewById(R.id.scrollContent);
        tvDetailContent = findViewById(R.id.tvDetailContent);
        ImageButton btnBack = findViewById(R.id.btnBack);

        llmClient = new LLMClient();
        markwon = Markwon.builder(this)
                .usePlugin(GlideImagesPlugin.create(this))
                .build();

        String title = getIntent().getStringExtra(EXTRA_ITEM_TITLE);
        if (title == null || title.isEmpty()) {
            title = "健康方案";
        }

        tvDetailTitle.setText("【" + title + "】专属方案");

        btnBack.setOnClickListener(v -> finish());

        fetchDetailFromLLM(title);
    }

    private void fetchDetailFromLLM(String title) {
        progressBar.setVisibility(View.VISIBLE);
        scrollContent.setVisibility(View.GONE);

        List<Message> messages = new ArrayList<>();
        messages.add(new Message("system",
                "你是一位权威的医学与健康助手，拥有皮肤科、营养学和健康管理的专业知识。\n" +
                "请使用规范的 Markdown 格式排版你的回答，包含标题、加粗、列表等结构化元素，" +
                "让内容清晰易读，语气专业且温暖。"));
        messages.add(new Message("user",
                "请为我详细介绍【" + title + "】的适用人群、核心成分分析、详细使用步骤以及注意事项。"));

        llmClient.getCompletion(this, messages, false, new LLMClient.LLMCallback() {
            @Override
            public void onSuccess(String response) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    scrollContent.setVisibility(View.VISIBLE);
                    markwon.setMarkdown(tvDetailContent, response);
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(SolutionDetailActivity.this,
                            "加载失败，请稍后重试：" + error, Toast.LENGTH_LONG).show();
                });
            }
        });
    }
}
