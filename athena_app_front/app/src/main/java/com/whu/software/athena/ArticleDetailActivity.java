package com.whu.software.athena;

import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;

import com.whu.software.athena.cognition.CognitionModels.ClueCreateRequest;
import com.whu.software.athena.cognition.CognitionModels.ClueCreateResult;
import com.whu.software.athena.cognition.CognitionModels.ClueIntent;
import com.whu.software.athena.cognition.CognitionModels.ClueSource;
import com.whu.software.athena.cognition.CognitionModels.ClueType;
import com.whu.software.athena.cognition.CognitionModels.CycleRelation;
import com.whu.software.athena.cognition.CognitionModels.HelpRequestType;
import com.whu.software.athena.cognition.CognitionModels.QuestionType;
import com.whu.software.athena.cognition.CognitionModels.RelationType;
import com.whu.software.athena.cognition.CognitionRepository;
import com.whu.software.athena.cognition.CognitionRepositoryProvider;
import com.whu.software.athena.config.ApiConfig;
import com.whu.software.athena.utils.TokenManager;
import com.whu.software.athena.utils.UnsafeOkHttpClient;

import org.json.JSONObject;
import org.json.JSONArray;

import java.io.IOException;
import java.time.Instant;

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
    private CognitionRepository cognitionRepository;
    private String currentArticleId = "";
    private String currentArticleTitle = "文章详情";
    private int currentArticleType = 100;
    private String currentArticleSource = "Athena 健康内容库";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_article_detail);

        ImageView btnBack = findViewById(R.id.btn_back);
        tvTitle = findViewById(R.id.tv_title);
        webContent = findViewById(R.id.web_content);

        okHttpClient = UnsafeOkHttpClient.getUnsafeOkHttpClient();
        cognitionRepository = CognitionRepositoryProvider.get(this);

        WebSettings settings = webContent.getSettings();
        settings.setJavaScriptEnabled(false);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);

        btnBack.setOnClickListener(v -> finish());

        String title = getIntent().getStringExtra("title");
        String blogId = resolveBlogId(getIntent());
        currentArticleId = blogId;
        currentArticleTitle = title != null ? title : "文章详情";
        int noteId = getIntent().getIntExtra("noteId", 0);
        if (noteId <= 0) {
            long noteIdLong = getIntent().getLongExtra("noteId", -1L);
            if (noteIdLong > 0 && noteIdLong <= Integer.MAX_VALUE) {
                noteId = (int) noteIdLong;
            }
        }
        String prefetchedContent = getIntent().getStringExtra("article_content_html");
        String prefetchedAuthorName = getIntent().getStringExtra("article_author_name");
        int type = getIntent().getIntExtra("type", -1);
        if (type < 0) {
            type = getIntent().getIntExtra("article_type", 100);
        }
        currentArticleType = type < 0 ? 100 : type;
        Log.d(TAG, "[ScienceAI] ArticleDetailActivity onCreate"
                + " resolvedTitle=" + title
                + " resolvedBlogId=" + blogId
                + " resolvedNoteId=" + noteId
                + " resolvedType=" + type
                + " prefetchedContentLength=" + (prefetchedContent == null ? 0 : prefetchedContent.length())
                + " hasAuthor=" + !TextUtils.isEmpty(prefetchedAuthorName));
        currentArticleSource = TextUtils.isEmpty(prefetchedAuthorName)
                ? "Athena 健康内容库" : prefetchedAuthorName;
        tvTitle.setText(title != null ? title : "文章详情");
        findViewById(R.id.btn_mark_related).setOnClickListener(v -> captureSelection(ClueIntent.RELATED));
        findViewById(R.id.btn_mark_question).setOnClickListener(v -> captureSelection(ClueIntent.QUESTION));
        findViewById(R.id.btn_mark_knowledge).setOnClickListener(v -> captureSelection(ClueIntent.KNOWLEDGE_ONLY));

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

    @NonNull
    private String resolveBlogId(@NonNull android.content.Intent intent) {
        String blogId = intent.getStringExtra("blog_id");
        if (!TextUtils.isEmpty(blogId)) {
            return blogId;
        }
        blogId = intent.getStringExtra("blogId");
        if (!TextUtils.isEmpty(blogId)) {
            return blogId;
        }
        blogId = intent.getStringExtra("id");
        if (!TextUtils.isEmpty(blogId)) {
            return blogId;
        }
        int noteId = intent.getIntExtra("noteId", 0);
        if (noteId > 0) {
            return String.valueOf(noteId);
        }
        long noteIdLong = intent.getLongExtra("noteId", -1L);
        if (noteIdLong > 0) {
            return String.valueOf(noteIdLong);
        }
        return "";
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
                        + " bodyLength=" + body.length());
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
                            + " contentLength=" + (content == null ? 0 : content.length()));
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
                            + " error=" + e.getMessage(), e);
                    runOnUiThread(() ->
                            Toast.makeText(ArticleDetailActivity.this, "文章内容解析失败", Toast.LENGTH_SHORT).show());
                }
            }
        });
    }

    private void renderArticleContent(@NonNull String content) {
        int readingMinutes = Math.max(1, Math.round(stripHtml(content).length() / 360f));
        String css = "<style>"
                + ":root{color-scheme:light;}html,body{background:#FAF7F2;}"
                + "*{box-sizing:border-box;}"
                + "body{word-wrap:break-word;padding:22px 26px 118px;margin:0;font-family:'Noto Serif SC','Source Han Serif SC','Songti SC',serif;"
                + "color:#3A353B;font-size:17px;line-height:1.9;-webkit-user-select:text;user-select:text;}"
                + ".article-title{margin:22px 0 12px;color:#211F23;font-family:'Noto Serif SC','Source Han Serif SC','Songti SC',serif;font-size:36px;line-height:1.3;letter-spacing:-.025em;font-weight:700;}"
                + ".article-meta{display:flex;align-items:center;gap:9px;margin:26px 0 28px;color:#928C94;font-family:-apple-system,BlinkMacSystemFont,'Noto Sans SC',sans-serif;font-size:13px;}"
                + ".meta-dot{width:3px;height:3px;border-radius:50%;background:#B7AFB8;}"
                + ".article-body h1,.article-body h2,.article-body h3{color:#29262B;font-family:'Noto Serif SC','Source Han Serif SC','Songti SC',serif;line-height:1.42;letter-spacing:-.015em;margin:1.55em 0 .65em;font-weight:700;}"
                + ".article-body h1{font-size:27px}.article-body h2{font-size:23px}.article-body h3{font-size:20px}"
                + ".article-body p{margin:.85em 0;}a{color:#7059DF;}"
                + ".article-body img{max-width:100%;height:auto;border-radius:18px;margin:12px 0;}"
                + ".article-body blockquote{margin:18px 0;padding:12px 15px;border-left:3px solid #9D83F7;background:#F3EEFF;border-radius:0 14px 14px 0;}"
                + ".trust-card{margin:38px 0 4px;padding:17px 18px 18px;border:1px solid rgba(255,255,255,.92);border-radius:23px;"
                + "background:linear-gradient(135deg,rgba(237,232,255,.78),rgba(255,250,249,.92) 48%,rgba(231,248,249,.78));"
                + "box-shadow:0 12px 30px rgba(65,49,72,.08);color:#5F5962;font-size:13px;line-height:1.72;}"
                + ".trust-title{display:flex;align-items:center;gap:10px;margin:0 0 9px;color:#252329;font-size:18px;font-weight:700;}"
                + ".trust-icon{position:relative;display:inline-block;width:28px;height:28px;flex:0 0 28px;border-radius:50%;background:rgba(140,119,239,.12);}"
                + ".trust-icon:before{content:'';position:absolute;left:9px;top:7px;width:10px;height:13px;border:1.6px solid #8472E7;border-radius:2px;}"
                + ".trust-icon:after{content:'';position:absolute;left:12px;top:11px;width:4px;height:4px;border-left:1.5px solid #8472E7;border-bottom:1.5px solid #8472E7;}"
                + ".trust-row{margin:3px 0;}"
                + "::selection{background:#DCCBFF;color:#17151A;}"
                + "</style>";
        String header = "<h1 class='article-title'>" + escapeHtml(currentArticleTitle) + "</h1>"
                + "<div class='article-meta'><span>阅读约 " + readingMinutes + " 分钟</span>"
                + "<span class='meta-dot'></span><span>健康科普</span></div>";
        String trust = "<section class='trust-card'><div class='trust-title'><span class='trust-icon'></span>内容说明</div>"
                + "<div class='trust-row'>来源：" + escapeHtml(currentArticleSource) + "</div>"
                + "<div class='trust-row'>审核状态：健康内容编辑审核</div>"
                + "<div class='trust-row'>科普信息不能替代诊断</div>"
                + "<div class='trust-row'>和我有关：本内容与日常健康管理相关</div>"
                + "<div class='trust-row'>更新日期：2026-08</div></section>";
        String finalHtml = "<html><head><meta name='viewport' content='width=device-width, initial-scale=1.0'>"
                + css + "</head><body>" + header + "<article class='article-body'>" + content
                + "</article>" + trust + "</body></html>";
        webContent.loadDataWithBaseURL(null, finalHtml, "text/html", "UTF-8", null);
    }

    @NonNull
    private String stripHtml(@NonNull String html) {
        return android.text.Html.fromHtml(html, android.text.Html.FROM_HTML_MODE_LEGACY)
                .toString().replaceAll("\\s+", "").trim();
    }

    @NonNull
    private String escapeHtml(String value) {
        return TextUtils.htmlEncode(value == null ? "" : value);
    }

    private void captureSelection(@NonNull ClueIntent intent) {
        webContent.getSettings().setJavaScriptEnabled(true);
        webContent.evaluateJavascript("(function(){return window.getSelection().toString();})()", raw -> {
            webContent.getSettings().setJavaScriptEnabled(false);
            String selected = decodeJavascriptString(raw);
            if (TextUtils.isEmpty(selected)) {
                Toast.makeText(this, "请先长按选择一段文章文字", Toast.LENGTH_SHORT).show();
                return;
            }
            if (intent == ClueIntent.QUESTION) showQuestionTypeDialog(selected);
            else if (intent == ClueIntent.RELATED) showRelatedDialog(selected);
            else saveClue(intent, selected, null, RelationType.KNOWLEDGE_ONLY,
                    HelpRequestType.SAVE_ONLY, null, "保存知识");
        });
    }

    private void showRelatedDialog(String selected) {
        String[] relations = {"我现在有类似情况", "以前出现过", "我不确定，但想观察", "只是觉得值得了解"};
        RelationType[] values = {RelationType.CURRENT, RelationType.PAST,
                RelationType.OBSERVE, RelationType.KNOWLEDGE_ONLY};
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("这段内容和你是什么关系？")
                .setItems(relations, (innerDialog, which) -> showDesiredHelpDialog(selected, values[which]))
                .setNegativeButton("取消", null)
                .create();
        showGlassDialog(dialog);
    }

    private void showDesiredHelpDialog(String selected, RelationType detail) {
        String[] labels = {"帮我持续观察", "帮我找可信知识", "帮我了解是否需要留意", "暂时只保存"};
        HelpRequestType[] values = {HelpRequestType.OBSERVE, HelpRequestType.KNOWLEDGE,
                HelpRequestType.ATTENTION, HelpRequestType.SAVE_ONLY};
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("你希望 Athena 做什么？")
                .setItems(labels, (innerDialog, which) ->
                        saveClue(ClueIntent.RELATED, selected, null, detail, values[which], null, "和我有关"))
                .setNegativeButton("取消", null)
                .create();
        showGlassDialog(dialog);
    }

    private void showQuestionTypeDialog(String selected) {
        String[] labels = {"这常见吗", "可能有哪些原因", "我能做什么", "什么时候需要专业帮助", "自定义问题"};
        QuestionType[] values = {QuestionType.IS_COMMON, QuestionType.POSSIBLE_CAUSES,
                QuestionType.SELF_CARE, QuestionType.PROFESSIONAL_HELP, QuestionType.CUSTOM};
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("你最想弄明白什么？")
                .setItems(labels, (innerDialog, which) -> showQuestionDialog(selected, values[which], labels[which]))
                .setNegativeButton("取消", null)
                .create();
        showGlassDialog(dialog);
    }

    private void showQuestionDialog(String selected, QuestionType questionType, String defaultQuestion) {
        EditText input = new EditText(this);
        input.setHint("你想弄明白什么？");
        if (questionType != QuestionType.CUSTOM) input.setText(defaultQuestion + "？");
        input.setMinLines(2);
        input.setTextColor(Color.parseColor("#2F2B32"));
        input.setHintTextColor(Color.parseColor("#8D858F"));
        input.setBackgroundResource(R.drawable.bg_article_dialog_input);
        int padding = Math.round(20 * getResources().getDisplayMetrics().density);
        input.setPadding(padding, padding, padding, padding);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("记录我的疑问")
                .setView(input)
                .setPositiveButton("保存", (innerDialog, which) -> {
                    String question = input.getText().toString().trim();
                    if (question.isEmpty()) Toast.makeText(this, "请写下你的问题", Toast.LENGTH_SHORT).show();
                    else saveClue(ClueIntent.QUESTION, selected, question,
                            RelationType.OBSERVE, HelpRequestType.KNOWLEDGE, questionType, "我有疑问");
                })
                .setNegativeButton("取消", null)
                .create();
        showGlassDialog(dialog);
    }

    private void saveClue(ClueIntent intent, String selected, String question,
                          RelationType relationType, HelpRequestType helpRequestType,
                          QuestionType questionType, String originalLabel) {
        ClueCreateRequest request = new ClueCreateRequest();
        // Contract §6.2: questions created from an article are ARTICLE_HIGHLIGHT + QUESTION.
        request.type = ClueType.ARTICLE_HIGHLIGHT;
        request.intent = intent;
        request.relationType = relationType;
        request.helpRequestType = helpRequestType;
        request.articleId = currentArticleId;
        request.articleTitle = currentArticleTitle;
        request.articleType = currentArticleType;
        request.selectedText = selected.length() > 4000 ? selected.substring(0, 4000) : selected;
        request.questionType = questionType;
        request.questionText = question;
        request.occurredAt = Instant.now().toString();
        request.cycleRelation = CycleRelation.UNKNOWN;
        request.source = ClueSource.KNOWLEDGE_ARTICLE;
        request.suggestedTopicTitle = currentArticleTitle;
        request.originalLabel = originalLabel;
        cognitionRepository.createClue(request, new CognitionRepository.Callback<ClueCreateResult>() {
            @Override public void onSuccess(ClueCreateResult value) {
                String message = value != null && value.digestTask != null && value.digestTask.triggered
                        ? "已保存，Athena 已开始整理" : intent == ClueIntent.QUESTION ? "疑问已保存" : "已加入我的身体线索";
                if (value == null || value.clue == null || value.clue.id == null) {
                    Toast.makeText(ArticleDetailActivity.this, message, Toast.LENGTH_SHORT).show();
                    return;
                }
                AlertDialog dialog = new AlertDialog.Builder(ArticleDetailActivity.this).setMessage(message)
                        .setPositiveButton("知道了", null)
                        .setNegativeButton("撤销", (innerDialog, which) -> undoClue(value.clue.id)).create();
                showGlassDialog(dialog);
            }
            @Override public void onError(String message) {
                Toast.makeText(ArticleDetailActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void undoClue(String clueId) {
        cognitionRepository.deleteClue(clueId, new CognitionRepository.Callback<String>() {
            @Override public void onSuccess(String value) {
                Toast.makeText(ArticleDetailActivity.this, "已撤销", Toast.LENGTH_SHORT).show();
            }
            @Override public void onError(String message) {
                Toast.makeText(ArticleDetailActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void showGlassDialog(@NonNull AlertDialog dialog) {
        dialog.setOnShowListener(ignored -> {
            Window window = dialog.getWindow();
            if (window != null) {
                window.setBackgroundDrawableResource(R.drawable.bg_article_dialog_glass);
                WindowManager.LayoutParams params = window.getAttributes();
                params.width = Math.round(getResources().getDisplayMetrics().widthPixels * 0.88f);
                params.dimAmount = 0.22f;
                window.setAttributes(params);
                window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            }
            styleDialogText(dialog.getWindow() == null ? null : dialog.getWindow().getDecorView());
            tintDialogButton(dialog.getButton(AlertDialog.BUTTON_POSITIVE));
            tintDialogButton(dialog.getButton(AlertDialog.BUTTON_NEGATIVE));
            ListView listView = dialog.getListView();
            if (listView != null) {
                listView.setDivider(new ColorDrawable(Color.parseColor("#1A6952C8")));
                listView.setDividerHeight(1);
            }
        });
        dialog.show();
    }

    private void tintDialogButton(Button button) {
        if (button == null) return;
        button.setTextColor(Color.parseColor("#6952C8"));
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setBackgroundColor(Color.TRANSPARENT);
    }

    private void styleDialogText(View view) {
        if (view == null) return;
        if (view instanceof TextView && !(view instanceof Button)) {
            TextView textView = (TextView) view;
            textView.setTextColor(Color.parseColor("#3A353B"));
            if (view.getId() == getResources().getIdentifier("alertTitle", "id", "android")) {
                textView.setTextColor(Color.parseColor("#211F23"));
                textView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                styleDialogText(group.getChildAt(i));
            }
        }
    }

    private String decodeJavascriptString(String raw) {
        if (raw == null || "null".equals(raw)) return "";
        try { return new JSONArray("[" + raw + "]").getString(0).trim(); }
        catch (Exception ignored) { return ""; }
    }

    @Override
    protected void onDestroy() {
        if (webContent != null) {
            webContent.destroy();
        }
        super.onDestroy();
    }
}
