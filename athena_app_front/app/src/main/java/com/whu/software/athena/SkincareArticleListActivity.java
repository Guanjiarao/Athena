package com.whu.software.athena;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.whu.software.athena.config.ApiConfig;
import com.whu.software.athena.utils.ArticleListParseHelper;
import com.whu.software.athena.utils.TokenManager;
import com.whu.software.athena.utils.UnsafeOkHttpClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class SkincareArticleListActivity extends AppCompatActivity {
    private static final String TAG = "SkincareArticleListActivity";
    public static final String EXTRA_PAGE_TITLE = "page_title";
    private static final int DEFAULT_ARTICLE_TYPE = 50;
    private static final String DEFAULT_PAGE_TITLE = "\u62a4\u80a4\u6307\u5357";

    private final List<ArticleItem> items = new ArrayList<>();
    private SkincareArticleAdapter adapter;
    private OkHttpClient okHttpClient;
    private int currentArticleType = DEFAULT_ARTICLE_TYPE;
    private String currentPageTitle = DEFAULT_PAGE_TITLE;

    public static Intent createIntent(Context context, String pageTitle, int articleType) {
        Intent intent = new Intent(context, SkincareArticleListActivity.class);
        intent.putExtra("article_type", articleType);
        intent.putExtra(EXTRA_PAGE_TITLE, pageTitle);
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_skincare_article_list);

        ImageView btnBack = findViewById(R.id.btn_back);
        TextView tvTitle = findViewById(R.id.tv_title);
        RecyclerView rvArticles = findViewById(R.id.rv_articles);

        currentArticleType = getIntent().getIntExtra("article_type", DEFAULT_ARTICLE_TYPE);
        String pageTitle = getIntent().getStringExtra(EXTRA_PAGE_TITLE);
        if (pageTitle != null && !pageTitle.trim().isEmpty()) {
            currentPageTitle = pageTitle;
        }
        okHttpClient = UnsafeOkHttpClient.getUnsafeOkHttpClient();
        tvTitle.setText(currentPageTitle);
        btnBack.setOnClickListener(v -> finish());

        rvArticles.setLayoutManager(new LinearLayoutManager(this));
        adapter = new SkincareArticleAdapter(items);
        rvArticles.setAdapter(adapter);

        fetchArticleList();
    }

    private static class ArticleItem {
        final String articleId;
        final String title;
        final String coverUrl;

        ArticleItem(String articleId, String title, String coverUrl) {
            this.articleId = articleId;
            this.title = title;
            this.coverUrl = coverUrl;
        }
    }

    private void fetchArticleList() {
        String token = TokenManager.getToken(this);
        if (token == null || token.isEmpty()) {
            Toast.makeText(this, "\u767b\u5f55\u72b6\u6001\u5931\u6548\uff0c\u8bf7\u91cd\u65b0\u767b\u5f55", Toast.LENGTH_SHORT).show();
            return;
        }

        String url = ApiConfig.API_BLOG_LIST_BY_TYPE
                + "?type=" + currentArticleType
                + "&pageNum=1&pageSize=50";
        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + token)
                .get()
                .build();

        okHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "\u5217\u8868\u8bf7\u6c42\u5931\u8d25: " + e.getMessage(), e);
                runOnUiThread(() -> {
                    items.clear();
                    adapter.notifyDataSetChanged();
                    Toast.makeText(SkincareArticleListActivity.this, "\u6587\u7ae0\u5217\u8868\u52a0\u8f7d\u5931\u8d25\uff0c\u8bf7\u7a0d\u540e\u91cd\u8bd5", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                String body = response.body() != null ? response.body().string() : "";
                Log.d(TAG, "\u5217\u8868\u54cd\u5e94: " + body);
                runOnUiThread(() -> {
                    items.clear();
                    adapter.notifyDataSetChanged();
                });
                try {
                    JSONObject root = new JSONObject(body);
                    if (root.optInt("code", -1) != 200) {
                        final String message = root.optString("message", "\u6587\u7ae0\u5217\u8868\u52a0\u8f7d\u5931\u8d25");
                        runOnUiThread(() ->
                                Toast.makeText(SkincareArticleListActivity.this, message, Toast.LENGTH_SHORT).show());
                        return;
                    }

                    JSONArray listArray = extractListArray(root);
                    List<ArticleItem> result = new ArrayList<>();
                    for (int i = 0; i < listArray.length(); i++) {
                        JSONObject obj = listArray.optJSONObject(i);
                        if (obj == null) {
                            continue;
                        }

                        int rowType = ArticleListParseHelper.parseTypeField(obj);
                        if (rowType != currentArticleType) {
                            continue;
                        }

                        String blogId = obj.optString("blogId", "");
                        if (blogId.isEmpty()) {
                            blogId = obj.optString("id", "");
                        }
                        if (blogId.isEmpty()) {
                            continue;
                        }

                        String title = obj.optString("title", "");
                        String coverUrl = obj.optString("coverUrl", "");
                        result.add(new ArticleItem(blogId, title, coverUrl));
                    }

                    List<ArticleItem> finalResult = result;
                    runOnUiThread(() -> {
                        items.clear();
                        items.addAll(finalResult);
                        adapter.notifyDataSetChanged();
                        if (items.isEmpty()) {
                            Toast.makeText(SkincareArticleListActivity.this, "\u6682\u65e0\u6587\u7ae0\u6570\u636e", Toast.LENGTH_SHORT).show();
                        }
                    });
                } catch (Exception e) {
                    Log.e(TAG, "\u5217\u8868\u89e3\u6790\u5931\u8d25: " + e.getMessage(), e);
                    runOnUiThread(() -> {
                        items.clear();
                        adapter.notifyDataSetChanged();
                        Toast.makeText(SkincareArticleListActivity.this, "\u6587\u7ae0\u5217\u8868\u89e3\u6790\u5931\u8d25", Toast.LENGTH_SHORT).show();
                    });
                }
            }
        });
    }

    private JSONArray extractListArray(JSONObject root) {
        Object data = root.opt("data");
        if (data instanceof JSONArray) {
            return (JSONArray) data;
        }
        if (data instanceof JSONObject) {
            JSONObject dataObj = (JSONObject) data;
            JSONArray records = dataObj.optJSONArray("records");
            if (records != null) {
                return records;
            }
            JSONArray list = dataObj.optJSONArray("list");
            if (list != null) {
                return list;
            }
        }
        return new JSONArray();
    }

    private class SkincareArticleAdapter extends RecyclerView.Adapter<SkincareArticleAdapter.VH> {
        private final List<ArticleItem> data;

        SkincareArticleAdapter(List<ArticleItem> data) {
            this.data = data;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_article_card, parent, false);
            return new VH(view);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            ArticleItem item = data.get(position);
            Glide.with(SkincareArticleListActivity.this)
                    .load(item.coverUrl)
                    .placeholder(R.drawable.kids_help)
                    .error(R.drawable.kids_help)
                    .centerCrop()
                    .into(holder.ivCover);
            holder.tvTitle.setText(item.title);
            holder.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(SkincareArticleListActivity.this, ArticleDetailActivity.class);
                intent.putExtra("blog_id", item.articleId);
                intent.putExtra("title", item.title);
                intent.putExtra("type", currentArticleType);
                intent.putExtra("article_type", currentArticleType);
                startActivity(intent);
            });
        }

        @Override
        public int getItemCount() {
            return data.size();
        }

        class VH extends RecyclerView.ViewHolder {
            final ImageView ivCover;
            final TextView tvTitle;

            VH(@NonNull View itemView) {
                super(itemView);
                ivCover = itemView.findViewById(R.id.iv_cover);
                tvTitle = itemView.findViewById(R.id.tv_article_title);
            }
        }
    }
}
