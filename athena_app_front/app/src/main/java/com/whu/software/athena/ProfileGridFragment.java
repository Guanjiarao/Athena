package com.whu.software.athena;

import android.content.Intent;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;

import com.bumptech.glide.Glide;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.whu.software.athena.config.ApiConfig;
import com.whu.software.athena.entity.BlogEntity;
import com.whu.software.athena.utils.BlogCacheBean;
import com.whu.software.athena.utils.TokenManager;
import com.whu.software.athena.utils.UnsafeOkHttpClient;

import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * 个人主页内容 Fragment，由 {@link ProfileFragment} 的 ViewPager2 托管。
 *
 * <p>LayoutManager 与广场页完全一致：
 * {@code StaggeredGridLayoutManager(2, VERTICAL)}，高低错落瀑布流。
 *
 * <p>Adapter 复用广场同款 {@code item_blog_card.xml}（内联轻量版，无广场耦合）。
 *
 * <p>数据加载策略：
 * <ul>
 *   <li>0 = 作品  → GET /blog/myList</li>
 *   <li>1 = 收藏  → GET /blog/collectList</li>
 *   <li>2 = 点赞  → GET /blog/likeList</li>
 * </ul>
 *
 * <p>无兜底 Mock：仅展示后端真实返回数据；为空则展示空态文案。
 */
public class ProfileGridFragment extends Fragment {

    private static final String TAG          = "ProfileGridFragment";
    private static final String ARG_TAB_TYPE = "tab_type";

    private int tabType;

    private RecyclerView   rvGrid;
    private TextView       tvEmpty;
    private ProfileBlogAdapter adapter;
    private OkHttpClient   httpClient;

    // -----------------------------------------------------------------------
    // 工厂方法
    // -----------------------------------------------------------------------

    public static ProfileGridFragment newInstance(int tabType) {
        Bundle args = new Bundle();
        args.putInt(ARG_TAB_TYPE, tabType);
        ProfileGridFragment f = new ProfileGridFragment();
        f.setArguments(args);
        return f;
    }

    // -----------------------------------------------------------------------
    // 生命周期
    // -----------------------------------------------------------------------

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            tabType = getArguments().getInt(ARG_TAB_TYPE, 0);
        }
        httpClient = UnsafeOkHttpClient.getUnsafeOkHttpClient();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile_grid, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        rvGrid  = view.findViewById(R.id.rv_profile_grid);
        tvEmpty = view.findViewById(R.id.tv_empty);

        // ── 与广场页完全相同的 LayoutManager ────────────────────────────────
        StaggeredGridLayoutManager lm = new StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL);
        lm.setGapStrategy(StaggeredGridLayoutManager.GAP_HANDLING_MOVE_ITEMS_BETWEEN_SPANS);
        rvGrid.setLayoutManager(lm);

        adapter = new ProfileBlogAdapter();
        rvGrid.setAdapter(adapter);
        
        // 为历史记录添加滚动加载更多
        if (tabType == 3) {
            rvGrid.addOnScrollListener(new RecyclerView.OnScrollListener() {
                @Override
                public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                    super.onScrolled(recyclerView, dx, dy);
                    if (dy > 0) { // 向下滚动
                        StaggeredGridLayoutManager layoutManager = (StaggeredGridLayoutManager) recyclerView.getLayoutManager();
                        if (layoutManager != null) {
                            int[] lastVisibleItemPositions = layoutManager.findLastVisibleItemPositions(null);
                            int lastVisibleItemPosition = getMaxValue(lastVisibleItemPositions);
                            int totalItemCount = layoutManager.getItemCount();
                            
                            // 当滚动到倒数第2个item时加载更多
                            if (lastVisibleItemPosition >= totalItemCount - 2 && hasMore && !loading) {
                                loadData();
                            }
                        }
                    }
                }
            });
        }

        loadData();
    }
    
    // 获取数组中的最大值
    private int getMaxValue(int[] array) {
        int max = array[0];
        for (int value : array) {
            if (value > max) {
                max = value;
            }
        }
        return max;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        rvGrid   = null;
        tvEmpty  = null;
        adapter  = null;
    }

    // 历史记录相关
    private long cursor = 0;
    private boolean hasMore = true;
    private boolean loading = false;
    
    // -----------------------------------------------------------------------
    // 数据加载
    // -----------------------------------------------------------------------

    private void loadData() {
        if (loading) return;
        
        String url = resolveUrl(tabType);
        // 历史记录接口需要游标分页
        if (tabType == 3) {
            url += "?pageSize=10";
            if (cursor > 0) {
                url += "&cursor=" + cursor;
            }
        }
        
        Log.d(TAG, "loadData tabType=" + tabType + ", url=" + url);
        
        String token = TokenManager.getToken(requireContext());

        Request.Builder builder = new Request.Builder().url(url).get();
        if (token != null && !token.isEmpty()) {
            builder.addHeader("Authorization", "Bearer " + token);
        }

        loading = true;
        httpClient.newCall(builder.build()).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.w(TAG, "Network failed tabType=" + tabType + ", show empty", e);
                renderOnUiThread(new ArrayList<>());
                loading = false;
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                String body = response.body() != null ? response.body().string() : "";
                Log.d(TAG, "tabType=" + tabType + " httpCode=" + response.code() + " raw: " + body);

                // 检查响应状态码
                if (!response.isSuccessful()) {
                    Log.e(TAG, "HTTP error tabType=" + tabType + " code=" + response.code());
                    // 如果是历史记录接口404，显示特殊提示
                    if (tabType == 3 && response.code() == 404) {
                        requireActivity().runOnUiThread(() -> {
                            if (isAdded() && tvEmpty != null) {
                                tvEmpty.setText("历史记录功能即将上线");
                                tvEmpty.setVisibility(View.VISIBLE);
                            }
                        });
                    }
                    renderOnUiThread(new ArrayList<>());
                    loading = false;
                    return;
                }

                List<BlogCacheBean> result = parseResponse(body);
                if (result == null || result.isEmpty()) {
                    Log.d(TAG, "Empty data tabType=" + tabType);
                    result = new ArrayList<>();
                    if (tabType == 3) {
                        hasMore = false;
                    }
                } else if (tabType == 3) {
                    // 解析历史记录的游标值
                    try {
                        JsonObject root = JsonParser.parseString(body).getAsJsonObject();
                        if (root.has("total")) {
                            cursor = root.get("total").getAsLong();
                            Log.d(TAG, "tabType=3 cursor updated to " + cursor);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "解析游标值失败: " + e.getMessage());
                    }
                }
                renderOnUiThread(result);
                loading = false;
            }
        });
    }

    private void renderOnUiThread(List<BlogCacheBean> data) {
        if (!isAdded() || getActivity() == null) return;
        requireActivity().runOnUiThread(() -> {
            if (!isAdded()) return;
            if (tabType == 3) {
                // 历史记录：添加到现有列表
                List<BlogCacheBean> currentItems = new ArrayList<>();
                if (adapter != null) {
                    currentItems = adapter.getItems();
                }
                currentItems.addAll(data);
                adapter.setItems(currentItems);
            } else {
                // 其他类型：替换整个列表
                adapter.setItems(data);
            }
            tvEmpty.setVisibility(adapter.getItemCount() == 0 ? View.VISIBLE : View.GONE);
        });
    }

    // -----------------------------------------------------------------------
    // URL 路由
    // -----------------------------------------------------------------------

    private static String resolveUrl(int tabType) {
        switch (tabType) {
            case 1:  return ApiConfig.API_BLOG_COLLECT_LIST;
            case 2:  return ApiConfig.API_BLOG_LIKE_LIST;
            case 3:  return ApiConfig.API_BLOG_VIEW_HISTORY;
            default: return ApiConfig.API_BLOG_MY_LIST;
        }
    }

    // -----------------------------------------------------------------------
    // JSON 解析（兼容两种后端结构）
    // -----------------------------------------------------------------------

    private List<BlogCacheBean> parseResponse(String body) {
        if (body == null || body.isEmpty()) {
            Log.d(TAG, "parseResponse: body is null or empty");
            return null;
        }
        try {
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            Log.d(TAG, "parseResponse: root=" + root.toString());
            if (!root.has("code")) {
                Log.d(TAG, "parseResponse: no code field");
                return null;
            }
            int code = root.get("code").getAsInt();
            Log.d(TAG, "parseResponse: code=" + code);
            if (code != 200) return null;
            if (!root.has("data") || root.get("data").isJsonNull()) {
                Log.d(TAG, "parseResponse: no data field");
                return null;
            }

            JsonArray array;
            if (root.get("data").isJsonArray()) {
                array = root.getAsJsonArray("data");
            } else {
                JsonObject dataObj = root.getAsJsonObject("data");
                if (!dataObj.has("records")) return null;
                array = dataObj.getAsJsonArray("records");
            }

            Gson gson = new Gson();
            List<BlogCacheBean> list = new ArrayList<>();
            float density = getResources().getDisplayMetrics().density;

            for (int i = 0; i < array.size(); i++) {
                JsonObject obj = array.get(i).getAsJsonObject();

                // 兼容后端不同版本的 ID 字段名：blogId → noteId → id → blog_id，任何一个非零即可
                String blogId = "";
                for (String key : new String[]{"blogId", "noteId", "id", "blog_id"}) {
                    if (obj.has(key) && !obj.get(key).isJsonNull()) {
                        String val = obj.get(key).getAsString();
                        if (!val.isEmpty() && !val.equals("0")) {
                            blogId = val;
                            break;
                        }
                    }
                }

                // 对于收藏和点赞列表，可能需要从嵌套结构中获取 blogId
                if (blogId.isEmpty() && obj.has("blog")) {
                    JsonObject blogObj = obj.getAsJsonObject("blog");
                    if (blogObj != null) {
                        for (String key : new String[]{"blogId", "noteId", "id", "blog_id"}) {
                            if (blogObj.has(key) && !blogObj.get(key).isJsonNull()) {
                                String val = blogObj.get(key).getAsString();
                                if (!val.isEmpty() && !val.equals("0")) {
                                    blogId = val;
                                    break;
                                }
                            }
                        }
                    }
                }

                // 解析标题和封面，优先从嵌套结构中获取
                String title  = "";
                String cover  = "";
                int type = 1;
                String videoUrl = "";

                // 先尝试从顶层获取
                if (obj.has("title") && !obj.get("title").isJsonNull()) {
                    title = obj.get("title").getAsString();
                }
                if (obj.has("coverUrl") && !obj.get("coverUrl").isJsonNull()) {
                    cover = obj.get("coverUrl").getAsString();
                }
                if (obj.has("type") && !obj.get("type").isJsonNull()) {
                    type = obj.get("type").getAsInt();
                }
                if (obj.has("videoUrl") && !obj.get("videoUrl").isJsonNull()) {
                    videoUrl = obj.get("videoUrl").getAsString();
                }

                // 如果从顶层获取失败，尝试从嵌套的 blog 结构中获取
                if ((title.isEmpty() || cover.isEmpty()) && obj.has("blog")) {
                    JsonObject blogObj = obj.getAsJsonObject("blog");
                    if (blogObj != null) {
                        if (title.isEmpty() && blogObj.has("title") && !blogObj.get("title").isJsonNull()) {
                            title = blogObj.get("title").getAsString();
                        }
                        if (cover.isEmpty() && blogObj.has("coverUrl") && !blogObj.get("coverUrl").isJsonNull()) {
                            cover = blogObj.get("coverUrl").getAsString();
                        }
                        if (blogObj.has("type") && !blogObj.get("type").isJsonNull()) {
                            type = blogObj.get("type").getAsInt();
                        }
                        if (blogObj.has("videoUrl") && !blogObj.get("videoUrl").isJsonNull()) {
                            videoUrl = blogObj.get("videoUrl").getAsString();
                        }
                    }
                }

                // 兼容多种点赞字段名，防止取不到数据
                int liked = 0;
                if (obj.has("liked") && !obj.get("liked").isJsonNull()) {
                    liked = obj.get("liked").getAsInt();
                } else if (obj.has("likeTotal") && !obj.get("likeTotal").isJsonNull()) {
                    liked = obj.get("likeTotal").getAsInt();
                } else if (obj.has("like_number") && !obj.get("like_number").isJsonNull()) {
                    liked = obj.get("like_number").getAsInt();
                } else if (obj.has("blog") && obj.getAsJsonObject("blog").has("like_number")) {
                    // 从嵌套结构中获取点赞数
                    JsonObject blogObj = obj.getAsJsonObject("blog");
                    if (blogObj != null && !blogObj.get("like_number").isJsonNull()) {
                        liked = blogObj.get("like_number").getAsInt();
                    }
                }

                Log.d("DETAIL_DEBUG", "[parseResponse] 第" + i + "条 → blogId=\"" + blogId
                        + "\", title=\"" + title + "\", cover=\"" + cover + "\", type=" + type + ", videoUrl=\"" + videoUrl + "\"");

                // 确保blogId不为空才添加到列表
                if (blogId != null && !blogId.isEmpty()) {
                    BlogCacheBean bean = new BlogCacheBean("", blogId, title, cover, "", liked);
                    bean.setHeight(0);
                    bean.setType(type);
                    bean.setVideoUrl(videoUrl);
                    list.add(bean);
                    Log.d(TAG, "parseResponse: added item " + i + " to list, list size=" + list.size());
                } else {
                    Log.w(TAG, "parseResponse: skipped item " + i + " because blogId is empty");
                }
            }
            Log.d(TAG, "parseResponse: returning list with " + list.size() + " items");
            return list;

        } catch (Exception e) {
            Log.e(TAG, "parseResponse error: " + e.getMessage());
            return null;
        }
    }

    private void renderOnUiThread(Runnable runnable) {
        if (!isAdded() || getActivity() == null) return;
        requireActivity().runOnUiThread(runnable);
    }

    // -----------------------------------------------------------------------
    // 内联轻量 Adapter（复用 item_blog_card.xml，无广场耦合）
    // -----------------------------------------------------------------------

    /**
     * 与广场 {@link SquareFragment.BlogCardAdapter} 完全对等，
     * 但移除了对 {@link SquareFragment} 的强引用，点击跳详情功能后续可扩展。
     */
    class ProfileBlogAdapter
            extends RecyclerView.Adapter<ProfileBlogAdapter.VH> {

        private List<BlogCacheBean> items = new ArrayList<>();

        void setItems(List<BlogCacheBean> items) {
            this.items = items != null ? items : new ArrayList<>();
            notifyDataSetChanged();
        }
        
        List<BlogCacheBean> getItems() {
            return items;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_blog_card, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            BlogCacheBean item = items.get(position);

            String title = item.getTitle() != null ? item.getTitle().trim() : "";
            h.tvTitle.setText(title.isEmpty() ? "Athena Post" : title);

            String userName = item.getUserName() != null ? item.getUserName().trim() : "";
            h.tvAuthor.setText(userName.isEmpty() ? "Athena" : userName);
            // 点赞数展示
            h.tvLikeCount.setText(String.valueOf(item.getLikeNumber()));

            // 高度：与广场保持相同逻辑——height<=0 时随机生成
            int px = item.getHeight();
            if (px <= 0) {
                float density = h.itemView.getContext()
                        .getResources().getDisplayMetrics().density;
                px = (int) ((150 + new Random().nextInt(200)) * density + 0.5f);
                item.setHeight(px);
            }
            ViewGroup.LayoutParams lp = h.ivCover.getLayoutParams();
            lp.height = px;
            h.ivCover.setLayoutParams(lp);

            String url = item.getImageUrl();

            Glide.with(h.itemView.getContext())
                    .load(url)
                    .placeholder(new ColorDrawable(0xFFF6F6F6))
                    .error(new ColorDrawable(0xFFEEEEEE))
                    .centerCrop()
                    .into(h.ivCover);

            // 点击卡片 → 与广场页完全相同的跳转逻辑
            h.card.setOnClickListener(v -> {
                if (!h.clickable) return;
                h.clickable = false;
                h.handler.postDelayed(() -> h.clickable = true, 500);

                String blogId = item.getBlogId();
                Log.d("DETAIL_DEBUG", "═══════════════════════════════════");
                Log.d("DETAIL_DEBUG", "[ProfileGrid] 点击卡片");
                Log.d("DETAIL_DEBUG", "[ProfileGrid] item.getBlogId() = \"" + blogId + "\"");
                Log.d("DETAIL_DEBUG", "[ProfileGrid] item.getTitle()   = \"" + item.getTitle() + "\"");
                Log.d("DETAIL_DEBUG", "[ProfileGrid] item.getType()    = " + item.getType());
                Log.d("DETAIL_DEBUG", "[ProfileGrid] item.getVideoUrl() = \"" + item.getVideoUrl() + "\"");
                Log.d("DETAIL_DEBUG", "[ProfileGrid] tabType = " + tabType);
                if (blogId == null || blogId.isEmpty()) {
                    Log.e("DETAIL_DEBUG", "[ProfileGrid] ❌ blogId 为空，拒绝跳转");
                    Toast.makeText(v.getContext(), "暂无详情数据", Toast.LENGTH_SHORT).show();
                    h.clickable = true;  // 恢复防抖，让用户可以再次点击
                    return;
                }

                if (item.getType() == 2 || item.getType() == 0) {
                    // 视频类型：直接跳转视频详情页，传递缓存数据
                    Intent intent = new Intent(v.getContext(), VideoDetailActivity.class);
                    intent.putExtra("blog_id", item.getBlogId());
                    intent.putExtra("title", item.getTitle());
                    intent.putExtra("user_name", item.getUserName());
                    intent.putExtra("like_number", item.getLikeNumber());
                    intent.putExtra("video_url", item.getVideoUrl());
                    intent.putExtra("content_type", item.getType());
                    // 根据当前列表类型传递默认状态
                    intent.putExtra("is_collected", tabType == 1); // 收藏列表
                    intent.putExtra("is_liked", tabType == 2); // 点赞列表

                    Log.d("DETAIL_DEBUG", "[ProfileGrid] 跳转 VideoDetailActivity, extras: "
                            + "blog_id=" + item.getBlogId()
                            + ", title=" + item.getTitle()
                            + ", user_name=" + item.getUserName()
                            + ", like_number=" + item.getLikeNumber()
                            + ", video_url=" + item.getVideoUrl()
                            + ", is_collected=" + (tabType == 1)
                            + ", is_liked=" + (tabType == 2));

                    v.getContext().startActivity(intent);
                } else {
                    // 图文类型：直接跳转，用列表封面图秒级占位，详情由 NoteDetailActivity 异步拉取
                    Log.d("DETAIL_DEBUG", "[ProfileGrid] 点击的是图文卡片, blogId=" + item.getBlogId() + ", type=" + item.getType());
                    Intent intent = new Intent(v.getContext(), NoteDetailActivity.class);
                    intent.putExtra("blog_id", item.getBlogId());
                    intent.putExtra("title", item.getTitle());
                    intent.putExtra("user_name", item.getUserName());
                    intent.putExtra("like_number", item.getLikeNumber());

                    // 将列表项现成的封面图直接传过去作为占位，确保详情页一定有图！
                    String cover = item.getImageUrl();
                    intent.putExtra("cover_url", cover);

                    Log.d("DETAIL_DEBUG", "直接跳转 NoteDetailActivity: blog_id=" + item.getBlogId() + ", cover_url=" + cover);
                    v.getContext().startActivity(intent);
                }
            });
        }

        @Override
        public int getItemCount() { return items.size(); }

        private String formatLikeCount(int count) {
            if (count >= 10000) {
                return String.format("%.1fw", count / 10000.0);
            } else if (count >= 1000) {
                return String.format("%.1fk", count / 1000.0);
            }
            return String.valueOf(Math.max(count, 0));
        }

        class VH extends RecyclerView.ViewHolder {
            final CardView  card;
            final ImageView ivCover;
            final TextView  tvTitle;
            final TextView  tvAuthor;
            final TextView  tvLikeCount;
            final Handler   handler = new Handler(Looper.getMainLooper());
            boolean clickable = true;

            VH(@NonNull View v) {
                super(v);
                card        = v.findViewById(R.id.card_blog);
                ivCover     = v.findViewById(R.id.iv_blog_cover);
                tvTitle     = v.findViewById(R.id.tv_blog_title);
                tvAuthor    = v.findViewById(R.id.tv_blog_author);
                tvLikeCount = v.findViewById(R.id.tv_like_count);
            }
        }
    }
}
