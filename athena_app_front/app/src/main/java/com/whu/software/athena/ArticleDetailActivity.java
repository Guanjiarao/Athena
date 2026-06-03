package com.whu.software.athena;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.whu.software.athena.config.ApiConfig;
import com.whu.software.athena.utils.TokenManager;
import com.whu.software.athena.utils.UnsafeOkHttpClient;

import org.json.JSONObject;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class ArticleDetailActivity extends AppCompatActivity {
    private static final String TAG = "ArticleDetailActivity";

    private TextView tvTitle;
    private WebView webContent;
    private ImageView ivHeader;
    private OkHttpClient okHttpClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_article_detail);

        ImageView btnBack = findViewById(R.id.btn_back);
        tvTitle = findViewById(R.id.tv_title);
        webContent = findViewById(R.id.web_content);

        okHttpClient = UnsafeOkHttpClient.getUnsafeOkHttpClient();

        WebSettings settings = webContent.getSettings();
        settings.setJavaScriptEnabled(false);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);

        btnBack.setOnClickListener(v -> finish());

        String title = getIntent().getStringExtra("title");
        String blogId = getIntent().getStringExtra("blog_id");
        int noteId = getIntent().getIntExtra("noteId", 0);
        String prefetchedContent = getIntent().getStringExtra("article_content_html");
        String prefetchedAuthorName = getIntent().getStringExtra("article_author_name");
        if (TextUtils.isEmpty(blogId) && noteId > 0) {
            blogId = String.valueOf(noteId);
        }
        int type = getIntent().getIntExtra("type", -1);
        if (type < 0) {
            type = getIntent().getIntExtra("article_type", 100);
        }
        Log.d(TAG, "[ScienceAI] ArticleDetailActivity onCreate"
                + " extras=" + getIntent().getExtras()
                + " resolvedTitle=" + title
                + " resolvedBlogId=" + blogId
                + " resolvedNoteId=" + noteId
                + " resolvedType=" + type
                + " prefetchedContentLength=" + (prefetchedContent == null ? 0 : prefetchedContent.length())
                + " prefetchedAuthorName=" + prefetchedAuthorName);
        tvTitle.setText(title != null ? title : "文章详情");

        if (!TextUtils.isEmpty(prefetchedContent)) {
            Log.d(TAG, "[ScienceAI] ArticleDetailActivity render prefetched content"
                    + " blogId=" + blogId
                    + " type=" + type);
            renderArticleContent(prefetchedContent);
            return;
        }

        if (blogId != null && !blogId.isEmpty()) {
            fetchArticleDetail(blogId, type);
        } else {
            Log.e(TAG, "[ScienceAI] ArticleDetailActivity invalid blogId"
                    + " extras=" + getIntent().getExtras());
            Toast.makeText(this, "文章ID无效", Toast.LENGTH_SHORT).show();
        }
    }

    private void fetchArticleDetail(@NonNull String blogId, int type) {
        String token = TokenManager.getToken(this);
        if (token == null || token.isEmpty()) {
            Log.e(TAG, "[ScienceAI] fetchArticleDetail token empty"
                    + " blogId=" + blogId + " type=" + type);
            Toast.makeText(this, "登录状态失效，请重新登录", Toast.LENGTH_SHORT).show();
            return;
        }

        HttpUrl url = HttpUrl.parse(ApiConfig.API_BLOG_DETAIL).newBuilder()
                .addQueryParameter("blog_id", blogId)
                .addQueryParameter("type", String.valueOf(type))
                .build();
        Log.d(TAG, "[ScienceAI] detail request"
                + " blogId=" + blogId
                + " type=" + type
                + " url=" + url);

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + token)
                .get()
                .build();

        okHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "[ScienceAI] detail request failed"
                        + " blogId=" + blogId
                        + " type=" + type
                        + " error=" + e.getMessage(), e);
                runOnUiThread(() ->
                        Toast.makeText(ArticleDetailActivity.this, "文章加载失败，请稍后重试", Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                String body = response.body() != null ? response.body().string() : "";
                Log.d(TAG, "[ScienceAI] detail response"
                        + " httpCode=" + response.code()
                        + " blogId=" + blogId
                        + " type=" + type
                        + " body=" + body);
                try {
                    JSONObject root = new JSONObject(body);
                    if (root.optInt("code", -1) != 200) {
                        final String message = root.optString("message", "文章加载失败");
                        Log.e(TAG, "[ScienceAI] detail business failed"
                                + " blogId=" + blogId
                                + " type=" + type
                                + " code=" + root.optInt("code", -1)
                                + " message=" + message);
                        runOnUiThread(() ->
                                Toast.makeText(ArticleDetailActivity.this, message, Toast.LENGTH_SHORT).show());
                        return;
                    }

                    JSONObject data = root.optJSONObject("data");
                    String content = data != null ? data.optString("content", "") : "";
                    Log.d(TAG, "[ScienceAI] detail parsed data"
                            + " title=" + (data == null ? "" : data.optString("title"))
                            + " contentLength=" + (content == null ? 0 : content.length())
                            + " data=" + data);
                    if (content == null || content.trim().isEmpty()) {
                        Log.e(TAG, "[ScienceAI] detail content empty"
                                + " blogId=" + blogId
                                + " type=" + type
                                + " data=" + data);
                        runOnUiThread(() ->
                                Toast.makeText(ArticleDetailActivity.this, "文章内容为空", Toast.LENGTH_SHORT).show());
                        return;
                    }

                    runOnUiThread(() -> renderArticleContent(content));
                } catch (Exception e) {
                    Log.e(TAG, "[ScienceAI] detail parse failed"
                            + " blogId=" + blogId
                            + " type=" + type
                            + " error=" + e.getMessage()
                            + " rawBody=" + body, e);
                    runOnUiThread(() ->
                            Toast.makeText(ArticleDetailActivity.this, "文章内容解析失败", Toast.LENGTH_SHORT).show());
                }
            }
        });
    }

    private void renderArticleContent(@NonNull String content) {
        String css = "<style>img{max-width:100%;height:auto;border-radius:8px;} body{word-wrap:break-word; padding:12px; margin:0; font-family:sans-serif;}</style>";
        String finalHtml = "<html><head><meta name='viewport' content='width=device-width, initial-scale=1.0'>"
                + css + "</head><body>" + content + "</body></html>";
        webContent.loadDataWithBaseURL(null, finalHtml, "text/html", "UTF-8", null);
    }

    @Override
    protected void onDestroy() {
        if (webContent != null) {
            webContent.destroy();
        }
        super.onDestroy();
    }
}
