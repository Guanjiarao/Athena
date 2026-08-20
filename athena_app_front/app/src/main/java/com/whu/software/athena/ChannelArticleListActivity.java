package com.whu.software.athena;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
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
import java.util.Iterator;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * 通用频道文章列表页。
 * 通过 Intent 接收 channel_name（标题）和 channel_id（频道 ID）进行数据拉取。
 * 使用方式：
 *   ChannelArticleListActivity.start(context, "个性化护肤", 1);
 */
public class ChannelArticleListActivity extends AppCompatActivity {

    private static final String TAG = "ChannelArticleListActivity";
    /** Logcat 单行过长会被截断，响应体只打前若干字符 */
    private static final int LOG_BODY_MAX_CHARS = 6000;

    public static final String EXTRA_CHANNEL_NAME = "channel_name";
    public static final String EXTRA_CHANNEL_ID   = "channel_id";
    public static final String EXTRA_INITIAL_FILTER = "initial_filter";
    private static final String FILTER_ALL = "全部";
    private static final String[] SAFETY_FILTERS = {
            FILTER_ALL, "避孕失败", "短效药", "避孕套", "紧急避孕", "验孕", "感染预防"
    };

    private final List<ArticleItem> allItems = new ArrayList<>();
    private final List<ArticleItem> items = new ArrayList<>();
    private ArticleCardAdapter adapter;
    private OkHttpClient okHttpClient;
    private int channelId;
    private String channelName = "";
    private String selectedFilter = FILTER_ALL;

    /** 便捷启动方法，调用方无需记忆 Extra 键名 */
    public static void start(Context context, String channelName, int channelId) {
        Log.i(TAG, "start: channelId=" + channelId + ", channelName=" + channelName
                + ", api=" + ApiConfig.API_BLOG_LIST_BY_CHANNEL);
        Intent intent = new Intent(context, ChannelArticleListActivity.class);
        intent.putExtra(EXTRA_CHANNEL_NAME, channelName);
        intent.putExtra(EXTRA_CHANNEL_ID, channelId);
        context.startActivity(intent);
    }

    public static void startWithFilter(Context context,
                                       String channelName,
                                       int channelId,
                                       String initialFilter) {
        Intent intent = new Intent(context, ChannelArticleListActivity.class);
        intent.putExtra(EXTRA_CHANNEL_NAME, channelName);
        intent.putExtra(EXTRA_CHANNEL_ID, channelId);
        intent.putExtra(EXTRA_INITIAL_FILTER, initialFilter);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_channel_article_list);

        String nameExtra = getIntent().getStringExtra(EXTRA_CHANNEL_NAME);
        channelName = nameExtra != null ? nameExtra : "";
        channelId = getIntent().getIntExtra(EXTRA_CHANNEL_ID, -1);
        selectedFilter = normalizeFilter(getIntent().getStringExtra(EXTRA_INITIAL_FILTER));

        ImageView btnBack  = findViewById(R.id.btn_back);
        TextView  tvTitle  = findViewById(R.id.tv_title);
        RecyclerView rv    = findViewById(R.id.rv_articles);
        HorizontalScrollView filterScroll = findViewById(R.id.hsv_filter_chips);
        LinearLayout filterChips = findViewById(R.id.ll_filter_chips);

        tvTitle.setText(channelName);
        btnBack.setOnClickListener(v -> finish());
        setupFilterChips(filterScroll, filterChips);

        okHttpClient = UnsafeOkHttpClient.getUnsafeOkHttpClient();
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ArticleCardAdapter(items);
        rv.setAdapter(adapter);

        if (channelId < 0) {
            Log.e(TAG, logPrefix() + "onCreate: 无效 channelId=" + channelId);
            Toast.makeText(this, "频道参数无效", Toast.LENGTH_SHORT).show();
            return;
        }
        Log.i(TAG, logPrefix() + "onCreate: 开始请求列表");
        fetchArticleList();
    }

    private void setupFilterChips(HorizontalScrollView filterScroll, LinearLayout filterChips) {
        if (filterScroll == null || filterChips == null) {
            return;
        }
        if (!shouldShowSafetyFilters()) {
            filterScroll.setVisibility(View.GONE);
            selectedFilter = FILTER_ALL;
            return;
        }

        filterScroll.setVisibility(View.VISIBLE);
        filterChips.removeAllViews();
        for (String label : SAFETY_FILTERS) {
            TextView chip = createFilterChip(label);
            filterChips.addView(chip);
        }
        updateFilterChipState();
    }

    private boolean shouldShowSafetyFilters() {
        return channelName.contains("避孕") || channelName.contains("身体安全");
    }

    private TextView createFilterChip(String label) {
        TextView chip = new TextView(this);
        chip.setText(label);
        chip.setGravity(Gravity.CENTER);
        chip.setTextSize(13);
        chip.setTag(label);
        chip.setPadding(dp(13), dp(7), dp(13), dp(7));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        lp.setMarginEnd(dp(8));
        chip.setLayoutParams(lp);
        chip.setOnClickListener(v -> selectFilter(label));
        return chip;
    }

    private void selectFilter(String filter) {
        selectedFilter = normalizeFilter(filter);
        updateFilterChipState();
        applyArticleFilter(true);
    }

    private void updateFilterChipState() {
        LinearLayout chipRow = findViewById(R.id.ll_filter_chips);
        if (chipRow == null) {
            return;
        }
        for (int i = 0; i < chipRow.getChildCount(); i++) {
            View child = chipRow.getChildAt(i);
            if (!(child instanceof TextView)) {
                continue;
            }
            TextView chip = (TextView) child;
            String label = String.valueOf(chip.getTag());
            boolean selected = selectedFilter.equals(label);
            chip.setBackgroundResource(selected
                    ? R.drawable.bg_science_filter_chip_selected
                    : R.drawable.bg_science_filter_chip);
            chip.setTextColor(selected ? 0xFF2F6257 : 0xFF68605C);
        }
    }

    private static String normalizeFilter(String filter) {
        if (filter == null || filter.trim().isEmpty()) {
            return FILTER_ALL;
        }
        String trimmed = filter.trim();
        for (String allowed : SAFETY_FILTERS) {
            if (allowed.equals(trimmed)) {
                return trimmed;
            }
        }
        return FILTER_ALL;
    }

    private void applyArticleFilter(boolean showEmptyHint) {
        items.clear();
        for (ArticleItem item : allItems) {
            if (matchesSelectedFilter(item)) {
                items.add(item);
            }
        }
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
        if (showEmptyHint && !allItems.isEmpty() && items.isEmpty()) {
            Toast.makeText(this, "该标签下暂无文章", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean matchesSelectedFilter(ArticleItem item) {
        if (FILTER_ALL.equals(selectedFilter)) {
            return true;
        }
        String title = item.title == null ? "" : item.title;
        switch (selectedFilter) {
            case "避孕失败":
                return containsAny(title, "避孕失败", "破", "滑落", "脱落", "体外");
            case "短效药":
                return containsAny(title, "短效", "口服", "漏服", "优思明", "妈富隆");
            case "避孕套":
                return containsAny(title, "避孕套", "安全套", "破了", "滑落");
            case "紧急避孕":
                return containsAny(title, "紧急", "事后", "左炔", "乌利司他");
            case "验孕":
                return containsAny(title, "验孕", "早孕", "怀孕", "月经推迟", "试纸");
            case "感染预防":
                return containsAny(title, "感染", "性传播", "HPV", "HIV", "梅毒", "淋病", "衣原体");
            default:
                return true;
        }
    }

    private static boolean containsAny(String value, String... keywords) {
        for (String keyword : keywords) {
            if (value.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private String logPrefix() {
        return "[channelId=" + channelId + " name=" + channelName + "] ";
    }

    private static String truncateForLog(String s, int maxChars) {
        if (s == null) {
            return "(null)";
        }
        if (s.length() <= maxChars) {
            return s;
        }
        return s.substring(0, maxChars) + "...<truncated totalLen=" + s.length() + ">";
    }

    private static String jsonObjectKeySample(JSONObject obj) {
        if (obj == null) {
            return "(null)";
        }
        StringBuilder sb = new StringBuilder();
        for (Iterator<String> it = obj.keys(); it.hasNext(); ) {
            if (sb.length() > 0) sb.append(',');
            sb.append(it.next());
        }
        return sb.toString();
    }

    // -----------------------------------------------------------------------
    // 数据模型
    // -----------------------------------------------------------------------

    private static class ArticleItem {
        final String blogId;
        final String title;
        final String coverUrl;
        /** 后端详情接口必填，与列表行一致（如 127） */
        final int blogType;

        ArticleItem(String blogId, String title, String coverUrl, int blogType) {
            this.blogId   = blogId;
            this.title    = title;
            this.coverUrl = coverUrl;
            this.blogType = blogType;
        }
    }

    /** 频道列表接口行数据：blogId、coverUrl、title、type */
    private static ArticleItem parseChannelBlogRow(JSONObject obj) {
        String blogId = obj.optString("blogId", "");
        if (blogId.isEmpty()) {
            blogId = obj.optString("id", "");
        }

        String title = obj.optString("title", "");
        if (title.isEmpty()) {
            title = obj.optString("blogTitle", "");
        }

        String coverUrl = obj.optString("coverUrl", "");
        if (coverUrl.isEmpty()) {
            coverUrl = obj.optString("image_url", "");
        }
        if (coverUrl.isEmpty()) {
            coverUrl = obj.optString("cover_url", "");
        }

        int blogType = ArticleListParseHelper.parseTypeField(obj);
        if (blogType == ArticleListParseHelper.MISSING_ID) {
            blogType = obj.optInt("blogType", -1);
        }

        return new ArticleItem(blogId, title, coverUrl, blogType);
    }

    // -----------------------------------------------------------------------
    // 网络请求
    // -----------------------------------------------------------------------

    private void fetchArticleList() {
        String token = TokenManager.getToken(this);
        if (token == null || token.isEmpty()) {
            Log.e(TAG, logPrefix() + "fetchArticleList: token 为空，取消请求");
            Toast.makeText(this, "登录状态失效，请重新登录", Toast.LENGTH_SHORT).show();
            return;
        }

        String url = ApiConfig.API_BLOG_LIST_BY_CHANNEL
                + "?channelId=" + channelId
                + "&pageNum=1&pageSize=50";

        Log.i(TAG, logPrefix() + "GET " + url);
        Log.d(TAG, logPrefix() + "Authorization: Bearer <len=" + token.length() + ">");

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + token)
                .get()
                .build();

        okHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, logPrefix() + "onFailure url=" + call.request().url()
                        + " msg=" + e.getMessage(), e);
                runOnUiThread(() -> {
                    allItems.clear();
                    items.clear();
                    adapter.notifyDataSetChanged();
                    Toast.makeText(ChannelArticleListActivity.this,
                            "文章列表加载失败，请稍后重试", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                int httpCode = response.code();
                boolean httpOk = response.isSuccessful();
                String body = response.body() != null ? response.body().string() : "";

                Log.i(TAG, logPrefix() + "onResponse http=" + httpCode + " ok=" + httpOk
                        + " bodyLen=" + (body != null ? body.length() : 0));
                Log.d(TAG, logPrefix() + "bodyPreview=" + truncateForLog(body, LOG_BODY_MAX_CHARS));

                runOnUiThread(() -> {
                    allItems.clear();
                    items.clear();
                    adapter.notifyDataSetChanged();
                });
                try {
                    JSONObject root = new JSONObject(body);
                    int bizCode = root.optInt("code", -1);
                    String bizMsg = root.optString("message", "");
                    Log.i(TAG, logPrefix() + "json bizCode=" + bizCode + " message=" + bizMsg);

                    if (bizCode != 200) {
                        Log.w(TAG, logPrefix() + "业务非200，终止解析");
                        String msg = root.optString("message", "文章列表加载失败");
                        runOnUiThread(() ->
                                Toast.makeText(ChannelArticleListActivity.this, msg, Toast.LENGTH_SHORT).show());
                        return;
                    }

                    Object dataRaw = root.opt("data");
                    Log.d(TAG, logPrefix() + "data类型="
                            + (dataRaw == null ? "null" : dataRaw.getClass().getSimpleName()));

                    JSONArray listArray = extractListArray(root);
                    int rawLen = listArray.length();
                    Log.i(TAG, logPrefix() + "解析到数组长度 rawLen=" + rawLen);

                    if (rawLen > 0) {
                        JSONObject sample = listArray.optJSONObject(0);
                        Log.d(TAG, logPrefix() + "首条字段keys=" + jsonObjectKeySample(sample));
                    }

                    List<ArticleItem> result = new ArrayList<>();
                    int skippedChannel = 0;
                    int skippedNoId = 0;
                    int skippedNotObject = 0;
                    for (int i = 0; i < listArray.length(); i++) {
                        JSONObject obj = listArray.optJSONObject(i);
                        if (obj == null) {
                            skippedNotObject++;
                            continue;
                        }

                        int rowChannel = ArticleListParseHelper.parseChannelIdField(obj);
                        if (rowChannel != ArticleListParseHelper.MISSING_ID
                                && rowChannel != channelId) {
                            skippedChannel++;
                            if (skippedChannel <= 3) {
                                Log.v(TAG, logPrefix() + "跳过channel不匹配 i=" + i
                                        + " rowChannel=" + rowChannel + " expect=" + channelId);
                            }
                            continue;
                        }

                        ArticleItem row = parseChannelBlogRow(obj);
                        if (row.blogId.isEmpty()) {
                            skippedNoId++;
                            if (skippedNoId <= 3) {
                                Log.v(TAG, logPrefix() + "跳过无blogId i=" + i
                                        + " keys=" + jsonObjectKeySample(obj));
                            }
                            continue;
                        }
                        result.add(row);
                    }

                    Log.i(TAG, logPrefix() + "过滤统计 accepted=" + result.size()
                            + " skippedChannel=" + skippedChannel
                            + " skippedNoBlogId=" + skippedNoId
                            + " skippedNotObject=" + skippedNotObject);

                    List<ArticleItem> finalResult = result;
                    runOnUiThread(() -> {
                        allItems.clear();
                        allItems.addAll(finalResult);
                        applyArticleFilter(false);
                        if (allItems.isEmpty()) {
                            Log.w(TAG, logPrefix() + "最终列表为空（可能接口无数据或全部被过滤）");
                            Toast.makeText(ChannelArticleListActivity.this,
                                    "暂无文章数据", Toast.LENGTH_SHORT).show();
                        }
                    });
                } catch (Exception e) {
                    Log.e(TAG, logPrefix() + "JSON解析异常: " + e.getMessage(), e);
                    runOnUiThread(() -> {
                        allItems.clear();
                        items.clear();
                        adapter.notifyDataSetChanged();
                        Toast.makeText(ChannelArticleListActivity.this,
                                "文章列表解析失败", Toast.LENGTH_SHORT).show();
                    });
                }
            }
        });
    }

    private static JSONArray extractListArray(JSONObject root) {
        Object data = root.opt("data");
        if (data instanceof JSONArray) {
            return (JSONArray) data;
        }
        if (data instanceof JSONObject) {
            JSONObject obj = (JSONObject) data;
            JSONArray records = obj.optJSONArray("records");
            if (records != null) {
                return records;
            }
            JSONArray list = obj.optJSONArray("list");
            if (list != null) {
                return list;
            }
            Log.w(TAG, "extractListArray: data 为 JSONObject 但无 records/list，keys="
                    + jsonObjectKeySample(obj));
        }
        Log.w(TAG, "extractListArray: 未得到数组，返回空 JSONArray");
        return new JSONArray();
    }

    // -----------------------------------------------------------------------
    // 通用 Adapter（复用 item_article_card.xml）
    // -----------------------------------------------------------------------

    private class ArticleCardAdapter extends RecyclerView.Adapter<ArticleCardAdapter.VH> {
        private final List<ArticleItem> data;

        ArticleCardAdapter(List<ArticleItem> data) { this.data = data; }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_channel_article_card, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            ArticleItem item = data.get(position);
            Glide.with(ChannelArticleListActivity.this)
                    .load(item.coverUrl)
                    .placeholder(R.drawable.kids_help)
                    .error(R.drawable.kids_help)
                    .centerCrop()
                    .into(holder.ivCover);
            holder.tvTitle.setMaxLines(2);
            holder.tvTitle.setText(item.title);
            holder.tvAge.setText(inferAgeLabel(item.title));
            holder.tvReadTime.setText("阅读时间：3 分钟");
            holder.tvTrust.setText("可信度：来源引用");
            holder.itemView.setOnClickListener(v -> {
                int detailType = item.blogType >= 0 ? item.blogType : 100;
                Intent intent = new Intent(ChannelArticleListActivity.this, ArticleDetailActivity.class);
                intent.putExtra("blog_id", item.blogId);
                intent.putExtra("title", item.title);
                intent.putExtra("type", detailType);
                intent.putExtra("article_type", detailType);
                startActivity(intent);
            });
        }

        @Override
        public int getItemCount() { return data.size(); }

        class VH extends RecyclerView.ViewHolder {
            final ImageView ivCover;
            final TextView  tvTitle;
            final TextView  tvAge;
            final TextView  tvReadTime;
            final TextView  tvTrust;

            VH(@NonNull View itemView) {
                super(itemView);
                ivCover = itemView.findViewById(R.id.iv_cover);
                tvTitle = itemView.findViewById(R.id.tv_article_title);
                tvAge = itemView.findViewById(R.id.tv_article_age);
                tvReadTime = itemView.findViewById(R.id.tv_article_read_time);
                tvTrust = itemView.findViewById(R.id.tv_article_trust);
            }
        }
    }

    private static String inferAgeLabel(String title) {
        String value = title == null ? "" : title;
        if (containsAny(value, "青春期", "未成年", "女童", "孩子", "儿童")) {
            return "适合年龄：青春期";
        }
        if (containsAny(value, "更年期", "绝经")) {
            return "适合年龄：55岁以上";
        }
        if (containsAny(value, "备孕", "孕前", "排卵")) {
            return "适合年龄：成年";
        }
        return "适合年龄：青春期 / 成年";
    }
}
