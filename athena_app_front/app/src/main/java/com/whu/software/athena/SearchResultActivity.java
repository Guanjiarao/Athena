package com.whu.software.athena;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.whu.software.athena.config.ApiConfig;
import com.whu.software.athena.entity.BlogEntity;
import com.whu.software.athena.utils.BlogCacheBean;
import com.whu.software.athena.utils.TokenManager;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class SearchResultActivity extends AppCompatActivity {

    private EditText etSearch;
    private TextView tvSearch;
    private ImageView ivBack;
    private RecyclerView rvSearchResults;
    private SearchBlogAdapter blogAdapter;
    private List<BlogCacheBean> searchResults = new ArrayList<>();
    private String searchType;
    private String searchText;
    private int currentPage = 1;
    private boolean isLoading = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search_result);

        // 初始化视图
        initViews();

        // 获取搜索参数
        Intent intent = getIntent();
        searchType = intent.getStringExtra("searchType");
        searchText = intent.getStringExtra("searchText");

        // 设置搜索框内容
        if (searchText != null) {
            etSearch.setText(searchText);
        }

        // 执行搜索
        performSearch();
    }

    private void initViews() {
        etSearch = findViewById(R.id.et_search);
        tvSearch = findViewById(R.id.tv_search);
        ivBack = findViewById(R.id.iv_back);
        rvSearchResults = findViewById(R.id.rv_search_results);

        // 设置返回按钮点击事件
        ivBack.setOnClickListener(v -> finish());

        // 设置搜索按钮点击事件
        tvSearch.setOnClickListener(v -> {
            String text = etSearch.getText().toString().trim();
            if (!text.isEmpty()) {
                searchText = text;
                currentPage = 1;
                performSearch();
            } else {
                Toast.makeText(this, "请输入搜索内容", Toast.LENGTH_SHORT).show();
            }
        });

        // 设置软键盘搜索按钮
        etSearch.setOnEditorActionListener((v, actionId, event) -> {
            String text = etSearch.getText().toString().trim();
            if (!text.isEmpty()) {
                searchText = text;
                currentPage = 1;
                performSearch();
                return true;
            }
            return false;
        });

        // 初始化RecyclerView，使用瀑布流布局
        StaggeredGridLayoutManager layoutManager = new StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL);
        layoutManager.setGapStrategy(StaggeredGridLayoutManager.GAP_HANDLING_MOVE_ITEMS_BETWEEN_SPANS);
        rvSearchResults.setLayoutManager(layoutManager);
        blogAdapter = new SearchBlogAdapter();
        rvSearchResults.setAdapter(blogAdapter);

        // 添加滚动监听，实现下拉加载更多
        rvSearchResults.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                if (dy > 0 && !isLoading) {
                    int[] lastVisibleItemPositions = layoutManager.findLastVisibleItemPositions(null);
                    int lastVisibleItemPosition = getMaxPosition(lastVisibleItemPositions);
                    
                    if (lastVisibleItemPosition >= blogAdapter.getItemCount() - 2) {
                        // 接近底部，加载更多
                        loadMore();
                    }
                }
            }
        });
    }

    private void performSearch() {
        if (searchText == null || searchText.isEmpty()) return;

        isLoading = true;

        try {
            // 获取本地Token并添加到请求头
            String token = TokenManager.getToken(this);
            
            // 防止searchType为null导致的崩溃
            int typeValue = (searchType != null && searchType.equals("knowledge")) ? 0 : 1;
            
            // 构建GET请求URL，将参数作为查询参数
            String url = ApiConfig.BASE_URL + "blog/search/v1?keyword=" + URLEncoder.encode(searchText, "UTF-8") + 
                         "&type=" + typeValue + 
                         "&pageNum=" + currentPage + 
                         "&pageSize=10";

            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer " + token)
                    .get()
                    .build();

            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .writeTimeout(15, TimeUnit.SECONDS)
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e("SEARCH_RESULT", "搜索失败: " + e.getMessage());
                    runOnUiThread(() -> {
                        Toast.makeText(SearchResultActivity.this, "搜索失败，请重试", Toast.LENGTH_SHORT).show();
                        isLoading = false;
                    });
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (response.isSuccessful() && response.body() != null) {
                        String responseStr = response.body().string();
                        Log.d("SEARCH_RESULT", "========== 搜索请求响应 ==========");
                        Log.d("SEARCH_RESULT", "请求URL: " + ApiConfig.BASE_URL + "blog/search/v1");
                        Log.d("SEARCH_RESULT", "请求参数 - keyword: " + searchText);
                        Log.d("SEARCH_RESULT", "请求参数 - type: " + typeValue);
                        Log.d("SEARCH_RESULT", "请求参数 - pageNum: " + currentPage);
                        Log.d("SEARCH_RESULT", "请求参数 - pageSize: 10");
                        Log.d("SEARCH_RESULT", "响应状态码: " + response.code());
                        Log.d("SEARCH_RESULT", "完整响应数据: " + responseStr);
                        Log.d("SEARCH_RESULT", "=====================================");

                        // 解析响应
                        try {
                            org.json.JSONObject root = new org.json.JSONObject(responseStr);
                            int code = root.optInt("code", -1);
                            String message = root.optString("message", "");
                            Log.d("SEARCH_RESULT", "响应code: " + code);
                            Log.d("SEARCH_RESULT", "响应message: " + message);
                            
                            if (code == 200) {
                                org.json.JSONArray dataArray = root.optJSONArray("data");
                                if (dataArray != null) {
                                    Log.d("SEARCH_RESULT", "数据数组长度: " + dataArray.length());
                                    Gson gson = new Gson();
                                    List<BlogEntity> blogs = gson.fromJson(
                                            dataArray.toString(),
                                            new TypeToken<List<BlogEntity>>(){}.getType()
                                    );
                                    Log.d("SEARCH_RESULT", "解析到的博客数量: " + (blogs != null ? blogs.size() : 0));
                                    runOnUiThread(() -> {
                                        if (currentPage == 1) {
                                            searchResults.clear();
                                        }
                                        if (blogs != null) {
                                            // 将BlogEntity转换为BlogCacheBean
                                            for (BlogEntity blog : blogs) {
                                                BlogCacheBean cacheBean = convertToCacheBean(blog);
                                                searchResults.add(cacheBean);
                                            }
                                        }
                                        Log.d("SEARCH_RESULT", "更新UI，当前显示数量: " + searchResults.size());
                                        blogAdapter.setItems(searchResults);
                                        isLoading = false;
                                    });
                                } else {
                                    Log.w("SEARCH_RESULT", "数据数组为空");
                                    runOnUiThread(() -> isLoading = false);
                                }
                            } else {
                                Log.e("SEARCH_RESULT", "响应code不是200，code: " + code);
                                runOnUiThread(() -> isLoading = false);
                            }
                        } catch (Exception e) {
                            Log.e("SEARCH_RESULT", "解析失败: " + e.getMessage());
                            e.printStackTrace();
                            runOnUiThread(() -> isLoading = false);
                        }
                    } else {
                        Log.e("SEARCH_RESULT", "响应不成功，状态码: " + response.code());
                        runOnUiThread(() -> isLoading = false);
                    }
                }
            });
        } catch (Exception e) {
            Log.e("SEARCH_RESULT", "搜索异常: " + e.getMessage());
            runOnUiThread(() -> isLoading = false);
        }
    }

    private void loadMore() {
        currentPage++;
        performSearch();
    }

    /**
     * 获取最大位置，用于 StaggeredGridLayoutManager 的滚动监听
     */
    private int getMaxPosition(int[] positions) {
        int maxPosition = -1;
        for (int position : positions) {
            if (position > maxPosition) {
                maxPosition = position;
            }
        }
        return maxPosition;
    }

    /**
     * 将BlogEntity转换为BlogCacheBean
     */
    private BlogCacheBean convertToCacheBean(BlogEntity blog) {
        String userName = "";
        String userId = "";
        
        // 设置用户信息
        if (blog.getUserDTO() != null) {
            userName = blog.getUserDTO().getNickName() != null ? blog.getUserDTO().getNickName() : "";
            userId = String.valueOf(blog.getUserDTO().getId());
        }
        
        // 获取博客信息，防止空指针
        String title = blog.getTitle() != null ? blog.getTitle() : "";
        String coverUrl = blog.getCoverUrl() != null ? blog.getCoverUrl() : "";
        int blogId = blog.getBlogId();
        int liked = blog.getLiked();
        int type = blog.getType();
        
        BlogCacheBean cacheBean = new BlogCacheBean(
            userName,
            String.valueOf(blogId),
            title,
            coverUrl,
            null, // imageId
            liked
        );
        
        // 设置类型和视频URL
        cacheBean.setType(type);
        cacheBean.setUserId(userId);
        
        return cacheBean;
    }

    /**
     * 搜索结果适配器
     */
    private class SearchBlogAdapter extends RecyclerView.Adapter<SearchBlogAdapter.ViewHolder> {

        private List<BlogCacheBean> items = new ArrayList<>();

        void setItems(List<BlogCacheBean> items) {
            this.items = items != null ? items : new ArrayList<>();
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_blog_card, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            BlogCacheBean item = items.get(position);

            holder.tvTitle.setText(item.getTitle() != null ? item.getTitle() : "");
            holder.tvLikeCount.setText(String.valueOf(item.getLikeNumber()));

            // 强制重新计算高度，确保瀑布流布局效果
            int h = item.getHeight();
            if (h <= 0) {
                float density = holder.itemView.getContext().getResources().getDisplayMetrics().density;
                h = (int) ((150 + new java.util.Random().nextInt(100)) * density + 0.5f);
                item.setHeight(h);
            }
            ViewGroup.LayoutParams params = holder.ivCover.getLayoutParams();
            params.height = h;
            holder.ivCover.setLayoutParams(params);

            // 设置封面图
            String imageUrl = item.getImageUrl();
            if (imageUrl == null || imageUrl.isEmpty()) {
                imageUrl = ApiConfig.MOCK_COVER_URL;
            }

            Glide.with(holder.itemView.getContext())
                    .load(imageUrl)
                    .placeholder(new ColorDrawable(0xFFF5E6E6))
                    .error(new ColorDrawable(0xFFCCCCCC))
                    .centerCrop()
                    .into(holder.ivCover);

            // 点击卡片
            holder.cardBlog.setOnClickListener(v -> {
                if (!holder.clickable) return;
                holder.clickable = false;
                holder.handler.postDelayed(() -> holder.clickable = true, 500);

                String blogId = item.getBlogId();
                if (blogId == null || blogId.isEmpty()) {
                    Toast.makeText(v.getContext(), "暂无详情数据", Toast.LENGTH_SHORT).show();
                    return;
                }

                if (item.getType() == 2 || item.getType() == 0) {
                    // 视频类型
                    Intent intent = new Intent(v.getContext(), VideoDetailActivity.class);
                    intent.putExtra("blog_id", item.getBlogId());
                    intent.putExtra("title", item.getTitle());
                    intent.putExtra("user_name", item.getUserName());
                    intent.putExtra("like_number", item.getLikeNumber());
                    intent.putExtra("video_url", item.getVideoUrl());
                    intent.putExtra("content_type", item.getType());
                    if (item.getUserId() != null && !item.getUserId().isEmpty()) {
                        intent.putExtra("user_id", Long.parseLong(item.getUserId()));
                    }
                    v.getContext().startActivity(intent);
                } else {
                    // 图文类型
                    Intent intent = new Intent(v.getContext(), NoteDetailActivity.class);
                    intent.putExtra("blog_id", item.getBlogId());
                    intent.putExtra("title", item.getTitle());
                    intent.putExtra("user_name", item.getUserName());
                    intent.putExtra("like_number", item.getLikeNumber());
                    String cover = item.getImageUrl();
                    if (cover == null || cover.isEmpty()) cover = ApiConfig.MOCK_COVER_URL;
                    intent.putExtra("cover_url", cover);
                    if (item.getUserId() != null && !item.getUserId().isEmpty()) {
                        intent.putExtra("user_id", Long.parseLong(item.getUserId()));
                    }
                    v.getContext().startActivity(intent);
                }
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            ImageView ivCover;
            TextView tvTitle;
            TextView tvLikeCount;
            View cardBlog;
            boolean clickable = true;
            android.os.Handler handler = new android.os.Handler();

            ViewHolder(View itemView) {
                super(itemView);
                ivCover = itemView.findViewById(R.id.iv_blog_cover);
                tvTitle = itemView.findViewById(R.id.tv_blog_title);
                tvLikeCount = itemView.findViewById(R.id.tv_like_count);
                cardBlog = itemView.findViewById(R.id.card_blog);
            }
        }
    }
}
