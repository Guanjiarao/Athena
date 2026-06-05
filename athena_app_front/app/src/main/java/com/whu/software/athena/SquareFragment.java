package com.whu.software.athena;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.ColorDrawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
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
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.bumptech.glide.Glide;
import com.whu.software.athena.config.ApiConfig;
import com.whu.software.athena.entity.BlogEntity;
import com.whu.software.athena.utils.BlogCacheBean;
import com.whu.software.athena.utils.BlogCacheDBHelper;
import com.whu.software.athena.utils.TokenManager;
import com.google.android.material.tabs.TabLayout;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class SquareFragment extends Fragment {

    private static final String TAG = "SquareFragment";
    /** 广场仅展示用户动态图文（type=1），过滤科普等其它 type */
    private static final int SQUARE_POST_TYPE = 1;
    private static final int PAGE_SIZE = 50;
    private static final int TAB_POSITION_SQUARE = 1;
    private static final int SCROLL_LOAD_THRESHOLD = 3;

    // 记录上一次下拉刷新时，排在瀑布流最顶部的 6 个内容的 ID
    private List<String> lastTopIds = new ArrayList<>();

    private RecyclerView recyclerView;
    private SwipeRefreshLayout swipeRefresh;
    private BlogCardAdapter adapter;
    private List<BlogCacheBean> blogList = new ArrayList<>();
    private BlogCacheDBHelper dbHelper;
    private OkHttpClient okHttpClient;
    private Gson gson;
    private AtomicBoolean isLoading = new AtomicBoolean(false);
    private int currentPage = 1;
    private boolean isDestroyed = false;
    private List<Call> callList = new ArrayList<>();

    private RecyclerView.OnScrollListener scrollListener;
    private TabLayout.OnTabSelectedListener tabSelectedListener;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_square, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // 1. 初始化网络和工具类（使用跳过证书验证的特权客户端）
        okHttpClient = com.whu.software.athena.utils.UnsafeOkHttpClient.getUnsafeOkHttpClient();

        gson = new Gson();

        if (getContext() != null) {
            dbHelper = BlogCacheDBHelper.getInstance(getContext());
        }

        // 2. 初始化视图
        recyclerView = view.findViewById(R.id.recycler_view_videos);
        swipeRefresh = view.findViewById(R.id.swipe_refresh);
        TabLayout tabLayout = view.findViewById(R.id.tab_layout);
        View btnCreate = view.findViewById(R.id.btn_create);
        View btnSearch = view.findViewById(R.id.btn_search);

        // 3. 设置瀑布流布局
        StaggeredGridLayoutManager layoutManager = new StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL);
        layoutManager.setGapStrategy(StaggeredGridLayoutManager.GAP_HANDLING_MOVE_ITEMS_BETWEEN_SPANS);
        recyclerView.setLayoutManager(layoutManager);

        // 4. 初始化适配器
        adapter = new BlogCardAdapter(blogList, this);
        recyclerView.setAdapter(adapter);

        // 5. 设置监听器
        setupListeners(layoutManager, tabLayout);

        // 6. 按钮点击事件
        if (btnCreate != null) {
            btnCreate.setOnClickListener(v -> {
                Log.d(TAG, "创作按钮被点击");
                // 跳转到发布动态页面
                if (getContext() != null) {
                    Intent intent = new Intent(getContext(), PublishActivity.class);
                    startActivity(intent);
                } else {
                    Log.e(TAG, "Context 为 null，无法跳转");
                }
            });
            // 确保按钮可点击
            btnCreate.setClickable(true);
            btnCreate.setEnabled(true);
        } else {
            Log.e(TAG, "btnCreate 为 null");
        }

        if (btnSearch != null) {
            btnSearch.setOnClickListener(v -> {
                // 跳转到搜索页面，传递搜索类型参数：1表示广场搜索
                if (getContext() != null) {
                    Intent intent = new Intent(getContext(), SearchActivity.class);
                    intent.putExtra("search_type", SearchActivity.SEARCH_TYPE_SQUARE);
                    startActivity(intent);
                }
            });
        }

        // 7. 首次加载数据
        refreshBlogs();
    }

    private void setupListeners(StaggeredGridLayoutManager layoutManager, TabLayout tabLayout) {
        swipeRefresh.setOnRefreshListener(this::refreshBlogs);

        scrollListener = new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                if (dy > 0 && !isLoading.get()) {
                    int[] lastVisibleItemPositions = layoutManager.findLastVisibleItemPositions(null);
                    int lastVisibleItemPosition = getMaxPosition(lastVisibleItemPositions);
                    if (adapter.getItemCount() > 0 &&
                            lastVisibleItemPosition >= adapter.getItemCount() - SCROLL_LOAD_THRESHOLD) {
                        loadMoreBlogs();
                    }
                }
            }
        };
        recyclerView.addOnScrollListener(scrollListener);

        tabSelectedListener = new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                if (tab.getPosition() == TAB_POSITION_SQUARE) {
                    refreshBlogs();
                }
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        };
        tabLayout.addOnTabSelectedListener(tabSelectedListener);
    }

    private void refreshBlogs() {
        if (isLoading.get()) return;
        isLoading.set(true);
        swipeRefresh.setRefreshing(true);
        // 每次下拉刷新时自增页码，确保获取新的一页数据
        currentPage++;

        if (!isNetworkAvailable()) {
            showToast("无网络，仅加载本地缓存");
            loadCacheBlogs(true);
            return;
        }

        new Thread(() -> {
            // 🔥 演示模式优化：强制每次刷新/切换Tab都去服务器拿最新鲜的 50 条数据
            requestBlogsFromServer(() -> {
                // 请求成功写入数据库后，再从数据库拿出最新数据展示
                List<BlogCacheBean> newBlogs = dbHelper.getUnloadedBlogs(PAGE_SIZE);
                updateBlogListOnUiThread(newBlogs, true);
            });
        }).start();
    }

    private void loadMoreBlogs() {
        if (isLoading.get()) return;
        isLoading.set(true);

        if (!isNetworkAvailable()) {
            showToast("无网络，无法加载更多");
            isLoading.set(false);
            return;
        }

        new Thread(() -> {
            if (dbHelper.hasUnloadedBlogs()) {
                List<BlogCacheBean> unloadedBlogs = dbHelper.getUnloadedBlogs(PAGE_SIZE);
                updateBlogListOnUiThread(unloadedBlogs, false);
            } else {
                currentPage++;
                requestBlogsFromServer(() -> {
                    List<BlogCacheBean> newBlogs = dbHelper.getUnloadedBlogs(PAGE_SIZE);
                    updateBlogListOnUiThread(newBlogs, false);
                });
            }
        }).start();
    }

    private void loadCacheBlogs(boolean isRefresh) {
        List<BlogCacheBean> cacheBlogs = dbHelper.getUnloadedBlogs(PAGE_SIZE);
        updateBlogListOnUiThread(cacheBlogs, isRefresh);
    }

    private static List<BlogCacheBean> filterSquareBlogBeans(List<BlogCacheBean> source) {
        if (source == null) {
            return new ArrayList<>();
        }
        List<BlogCacheBean> out = new ArrayList<>(source.size());
        for (BlogCacheBean b : source) {
            if (b != null && (b.getType() == SQUARE_POST_TYPE || b.getType() == 2)) {
                out.add(b);
            }
        }
        return out;
    }

    private void updateBlogListOnUiThread(List<BlogCacheBean> newBlogs, boolean isRefresh) {
        runOnUiThreadSafe(() -> {
            List<BlogCacheBean> filtered = filterSquareBlogBeans(newBlogs);
            if (filtered.isEmpty() && isRefresh) {
                // 如果实在没数据，强制生成假数据展示
                generateMockData();
                return;
            }
            if (isRefresh) {
                // 🔥 国一演示专属黑科技：前端防疲劳度智能推荐算法
                if (filtered != null && filtered.size() > 6) {
                    List<BlogCacheBean> freshPool = new ArrayList<>(); // 未在上次顶部曝光的内容
                    List<BlogCacheBean> usedPool = new ArrayList<>();  // 上次就在顶部的内容（需要降权沉底）

                    // 1. 根据记忆池分离数据
                    for (BlogCacheBean blog : filtered) {
                        String currentId = blog != null ? String.valueOf(blog.getBlogId()) : "";
                        if (lastTopIds.contains(currentId)) {
                            usedPool.add(blog);
                        } else {
                            freshPool.add(blog);
                        }
                    }

                    // 2. 分别打乱新鲜池和降权池
                    java.util.Collections.shuffle(freshPool);
                    java.util.Collections.shuffle(usedPool);

                    // 3. 重新组装：新鲜池放最上面，降权池沉底
                    List<BlogCacheBean> smartShuffledList = new ArrayList<>();
                    smartShuffledList.addAll(freshPool);
                    smartShuffledList.addAll(usedPool);

                    // 4. 更新记忆池：记录本次排在最前面的 6 个 ID，供下次刷新避开
                    lastTopIds.clear();
                    for (int i = 0; i < Math.min(6, smartShuffledList.size()); i++) {
                        BlogCacheBean topItem = smartShuffledList.get(i);
                        String topId = topItem != null ? String.valueOf(topItem.getBlogId()) : "";
                        lastTopIds.add(topId);
                    }

                    // 5. 替换原数据（保持后续使用 filtered 的语义）
                    filtered.clear();
                    filtered.addAll(smartShuffledList);
                } else if (filtered != null) {
                    // 数据极少时的普通兜底
                    java.util.Collections.shuffle(filtered);

                    // 仍然同步一下记忆池，方便下一次下拉刷新继续做“疲劳度控制”
                    lastTopIds.clear();
                    for (int i = 0; i < Math.min(6, filtered.size()); i++) {
                        BlogCacheBean topItem = filtered.get(i);
                        String topId = topItem != null ? String.valueOf(topItem.getBlogId()) : "";
                        lastTopIds.add(topId);
                    }
                }

                blogList.clear();
                blogList.addAll(filtered);
                adapter.notifyDataSetChanged();

                // 下拉刷新交互体验：停止动画、回到顶部、提示文案
                if (swipeRefresh != null) {
                    swipeRefresh.setRefreshing(false);
                }
                if (recyclerView != null) {
                    recyclerView.scrollToPosition(0);
                }
                if (getContext() != null) {
                    Toast.makeText(getContext(), "已为您推荐最新内容", Toast.LENGTH_SHORT).show();
                }
            } else {
                if (!filtered.isEmpty()) {
                    // 上拉加载到真实数据：正常追加（并轻微打散保持新鲜感）
                    java.util.Collections.shuffle(filtered);
                    int startPos = blogList.size();
                    blogList.addAll(filtered);
                    adapter.notifyItemRangeInserted(startPos, filtered.size());
                } else if (!blogList.isEmpty()) {
                    // 数据库触底：进入“无限循环拼接”模式
                    List<BlogCacheBean> currentData = new ArrayList<>(blogList);
                    java.util.Collections.shuffle(currentData);
                    int startPos = blogList.size();
                    blogList.addAll(currentData);
                    adapter.notifyItemRangeInserted(startPos, currentData.size());
                }
            }
            isLoading.set(false);
            if (!isRefresh && swipeRefresh != null) {
                swipeRefresh.setRefreshing(false);
            }
        });
    }

    private void requestBlogsFromServer(Runnable onSuccess) {
        Request request;
        try {
            // 获取本地Token并添加到请求头
            String token = TokenManager.getToken(getContext());
            okhttp3.HttpUrl.Builder urlBuilder = okhttp3.HttpUrl.parse(ApiConfig.API_BLOG_LIST).newBuilder();
            urlBuilder.addQueryParameter("pageNum", String.valueOf(currentPage));
            urlBuilder.addQueryParameter("pageSize", String.valueOf(PAGE_SIZE));
            request = new Request.Builder()
                    .url(urlBuilder.build())
                    .addHeader("Authorization", "Bearer " + token)
                    .get()
                    .build();
        } catch (Exception e) {
            generateMockData();
            return;
        }

        Call call = okHttpClient.newCall(request);
        callList.add(call);

        call.enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "网络请求失败: " + e.getMessage());
                callList.remove(call);
                generateMockData();
                runOnUiThreadSafe(() -> {
                    showToast("网络请求失败，已加载模拟数据");
                    isLoading.set(false);
                    swipeRefresh.setRefreshing(false);
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                callList.remove(call);
                if (isDestroyed) return;

                // 检查响应头中的Token并更新本地
                String newToken = response.header("Authorization");
                if (newToken != null && !newToken.isEmpty()) {
                    TokenManager.updateToken(getContext(), "", newToken);
                }

                if (response.isSuccessful() && response.body() != null) {
                    try {
                        String responseStr = response.body().string();
                        Log.d(TAG, "服务器返回: " + responseStr);

                        // 解析统一包装层：{ "code": 200, "message": "...", "data": [...] }
                        JSONObject root = new JSONObject(responseStr);
                        if (root.optInt("code", -1) != 200) {
                            throw new IllegalArgumentException("接口返回非200: " + root.optString("message"));
                        }
                        JSONArray dataArray = root.optJSONArray("data");
                        if (dataArray == null) {
                            throw new IllegalArgumentException("data 字段为空或非数组");
                        }

                        List<BlogEntity> blogEntities = gson.fromJson(
                                dataArray.toString(),
                                new TypeToken<List<BlogEntity>>(){}.getType()
                        );

                        if (blogEntities == null || blogEntities.isEmpty()) {
                            // 数据为空，说明页码超出范围，重置页码并重新请求第一页
                            currentPage = 1;
                            runOnUiThreadSafe(() -> {
                                showToast("已加载");
                            });
                            
                            // 重新构建请求，获取第一页数据
                            try {
                                String token = TokenManager.getToken(getContext());
                                okhttp3.HttpUrl.Builder urlBuilder = okhttp3.HttpUrl.parse(ApiConfig.API_BLOG_LIST).newBuilder();
                                urlBuilder.addQueryParameter("pageNum", String.valueOf(currentPage));
                                urlBuilder.addQueryParameter("pageSize", String.valueOf(PAGE_SIZE));
                                Request newRequest = new Request.Builder()
                                        .url(urlBuilder.build())
                                        .addHeader("Authorization", "Bearer " + token)
                                        .get()
                                        .build();
                                
                                Call newCall = okHttpClient.newCall(newRequest);
                                callList.add(newCall);
                                
                                newCall.enqueue(new Callback() {
                                    @Override
                                    public void onFailure(@NonNull Call call, @NonNull IOException e) {
                                        Log.e(TAG, "重新请求失败: " + e.getMessage());
                                        callList.remove(call);
                                        generateMockData();
                                        runOnUiThreadSafe(() -> {
                                            showToast("网络请求失败，已加载模拟数据");
                                            isLoading.set(false);
                                            swipeRefresh.setRefreshing(false);
                                        });
                                    }
                                    
                                    @Override
                                    public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                                        callList.remove(call);
                                        if (isDestroyed) return;
                                        
                                        // 检查响应头中的Token并更新本地
                                        String newToken = response.header("Authorization");
                                        if (newToken != null && !newToken.isEmpty()) {
                                            TokenManager.updateToken(getContext(), "", newToken);
                                        }
                                        
                                        if (response.isSuccessful() && response.body() != null) {
                                            try {
                                                String responseStr = response.body().string();
                                                Log.d(TAG, "重新请求返回: " + responseStr);
                                                
                                                // 解析统一包装层
                                                JSONObject root = new JSONObject(responseStr);
                                                if (root.optInt("code", -1) != 200) {
                                                    throw new IllegalArgumentException("接口返回非200: " + root.optString("message"));
                                                }
                                                JSONArray dataArray = root.optJSONArray("data");
                                                if (dataArray == null) {
                                                    throw new IllegalArgumentException("data 字段为空或非数组");
                                                }
                                                
                                                List<BlogEntity> newBlogEntities = gson.fromJson(
                                                        dataArray.toString(),
                                                        new TypeToken<List<BlogEntity>>(){}.getType()
                                                );
                                                
                                                if (newBlogEntities == null || newBlogEntities.isEmpty()) {
                                                    // 如果第一页也为空，生成模拟数据
                                                    generateMockData();
                                                    runOnUiThreadSafe(() -> {
                                                        showToast("无数据，已加载模拟数据");
                                                        isLoading.set(false);
                                                        swipeRefresh.setRefreshing(false);
                                                    });
                                                    return;
                                                }
                                                
                                                List<BlogCacheBean> beans = new ArrayList<>();
                                                for (BlogEntity entity : newBlogEntities) {
                                                    int itemType = entity.getType();
                                                    if (itemType != SQUARE_POST_TYPE && itemType != 2) {
                                                        continue;
                                                    }
                                                    
                                                    Log.d(TAG, "解析博客: blogId=" + entity.getBlog_id()
                                                            + ", type=" + entity.getType()
                                                            + ", title=" + entity.getTitle()
                                                            + ", videoUrl=" + entity.getVideoUrl()
                                                            + ", coverUrl=" + entity.getImage_url()
                                                            + ", userId=" + entity.getUserId());
                                                    
                                                    BlogCacheBean bean = new BlogCacheBean(
                                                            entity.getUser_name(),
                                                            entity.getBlog_id(),
                                                            entity.getTitle(),
                                                            entity.getImage_url(),
                                                            entity.getImage_id(),
                                                            entity.getLike_number()
                                                    );
                                                    bean.setType(entity.getType());
                                                    bean.setVideoUrl(entity.getVideoUrl() != null ? entity.getVideoUrl() : "");
                                                    // 设置 userId，从 userDTO 中获取
                                                    if (entity.getUserId() != null) {
                                                        bean.setUserId(String.valueOf(entity.getUserId()));
                                                    }
                                                    float density = getContext().getResources().getDisplayMetrics().density;
                                                    bean.setHeight((int) ((150 + new Random().nextInt(100)) * density + 0.5f));
                                                    beans.add(bean);
                                                }
                                                
                                                dbHelper.batchAddBlogs(beans);
                                                if (onSuccess != null) onSuccess.run();
                                                
                                            } catch (Exception e) {
                                                Log.e(TAG, "重新请求数据解析失败: " + e.getMessage());
                                                generateMockData();
                                                runOnUiThreadSafe(() -> {
                                                    showToast("数据解析失败，已加载模拟数据");
                                                    isLoading.set(false);
                                                    swipeRefresh.setRefreshing(false);
                                                });
                                            }
                                        } else {
                                            Log.e(TAG, "重新请求失败，状态码: " + response.code());
                                            generateMockData();
                                            runOnUiThreadSafe(() -> {
                                                showToast("服务器错误，已加载模拟数据");
                                                isLoading.set(false);
                                                swipeRefresh.setRefreshing(false);
                                            });
                                        }
                                    }
                                });
                            } catch (Exception e) {
                                generateMockData();
                                runOnUiThreadSafe(() -> {
                                    showToast("请求构建失败，已加载模拟数据");
                                    isLoading.set(false);
                                    swipeRefresh.setRefreshing(false);
                                });
                            }
                            return;
                        }

                        List<BlogCacheBean> beans = new ArrayList<>();
                        for (BlogEntity entity : blogEntities) {
                            int itemType = entity.getType();
                            if (itemType != SQUARE_POST_TYPE && itemType != 2) {
                                continue;
                            }

                            Log.d(TAG, "解析博客: blogId=" + entity.getBlog_id()
                                    + ", type=" + entity.getType()
                                    + ", title=" + entity.getTitle()
                                    + ", videoUrl=" + entity.getVideoUrl()
                                    + ", coverUrl=" + entity.getImage_url()
                                    + ", userId=" + entity.getUserId());

                            BlogCacheBean bean = new BlogCacheBean(
                                    entity.getUser_name(),
                                    entity.getBlog_id(),
                                    entity.getTitle(),
                                    entity.getImage_url(),
                                    entity.getImage_id(),
                                    entity.getLike_number()
                            );
                            bean.setType(entity.getType());
                            bean.setVideoUrl(entity.getVideoUrl() != null ? entity.getVideoUrl() : "");
                            // 设置 userId，从 userDTO 中获取
                            if (entity.getUserId() != null) {
                                bean.setUserId(String.valueOf(entity.getUserId()));
                            }
                            float density = getContext().getResources().getDisplayMetrics().density;
                            bean.setHeight((int) ((150 + new Random().nextInt(100)) * density + 0.5f));
                            beans.add(bean);
                        }

                        dbHelper.batchAddBlogs(beans);
                        if (onSuccess != null) onSuccess.run();

                    } catch (Exception e) {
                        Log.e(TAG, "数据解析失败: " + e.getMessage());
                        generateMockData();
                        runOnUiThreadSafe(() -> {
                            showToast("数据解析失败，已加载模拟数据");
                            isLoading.set(false);
                            swipeRefresh.setRefreshing(false);
                        });
                    }
                } else {
                    Log.e(TAG, "请求失败，状态码: " + response.code());
                    generateMockData();
                    runOnUiThreadSafe(() -> {
                        showToast("服务器错误，已加载模拟数据");
                        isLoading.set(false);
                        swipeRefresh.setRefreshing(false);
                    });
                }
            }
        });
    }

    /**
     * 🔥 终极兜底逻辑：强制生成带有阿里云图片的假数据并立刻显示
     * 注意：此方法可能在 OkHttp 子线程中调用，需要线程安全处理
     */
    /*
    private void refreshSquareBlogs() {
        if (isLoading.get()) return;
        isLoading.set(true);
        if (swipeRefresh != null) {
            swipeRefresh.setRefreshing(true);
        }
        currentPage = 1;
        hasMoreServerData = true;

        if (!isNetworkAvailable()) {
            showToast("无网络，仅加载本地缓存");
            new Thread(() -> loadSquareCacheBlogs(true)).start();
            return;
        }

        new Thread(() -> requestSquareBlogsFromServer(currentPage, true)).start();
    }

    private void loadMoreSquareBlogs() {
        if (isLoading.get()) return;
        if (!hasMoreServerData) {
            showToast("已经到底了");
            return;
        }

        isLoading.set(true);
        new Thread(() -> {
            if (dbHelper != null && dbHelper.hasUnloadedBlogs()) {
                List<BlogCacheBean> cachedBlogs = dbHelper.getUnloadedBlogs(PAGE_SIZE);
                updateSquareBlogListOnUiThread(cachedBlogs, false);
                return;
            }

            if (!isNetworkAvailable()) {
                runOnUiThreadSafe(() -> {
                    showToast("无网络，无法加载更多");
                    finishSquareLoading();
                });
                return;
            }

            requestSquareBlogsFromServer(currentPage + 1, false);
        }).start();
    }

    private void loadSquareCacheBlogs(boolean isRefresh) {
        if (dbHelper == null) {
            updateSquareBlogListOnUiThread(new ArrayList<>(), isRefresh);
            return;
        }

        if (isRefresh) {
            dbHelper.resetLoadedState();
        }
        List<BlogCacheBean> cacheBlogs = dbHelper.getUnloadedBlogs(PAGE_SIZE);
        updateSquareBlogListOnUiThread(cacheBlogs, isRefresh);
    }

    private void requestSquareBlogsFromServer(int pageNum, boolean isRefresh) {
        try {
            List<BlogCacheBean> pageBlogs = fetchSquareBlogsByPage(pageNum);
            hasMoreServerData = !pageBlogs.isEmpty();

            if (dbHelper != null) {
                if (isRefresh) {
                    dbHelper.clearAllBlogCache();
                }
                if (!pageBlogs.isEmpty()) {
                    dbHelper.batchAddBlogs(pageBlogs);
                }
            }

            if (!pageBlogs.isEmpty()) {
                currentPage = pageNum;
            }

            List<BlogCacheBean> displayBlogs;
            if (dbHelper != null) {
                displayBlogs = dbHelper.getUnloadedBlogs(PAGE_SIZE);
            } else if (pageBlogs.size() > PAGE_SIZE) {
                displayBlogs = new ArrayList<>(pageBlogs.subList(0, PAGE_SIZE));
            } else {
                displayBlogs = pageBlogs;
            }
            updateSquareBlogListOnUiThread(displayBlogs, isRefresh);
        } catch (Exception e) {
            handleSquareRequestFailure(isRefresh, e);
        }
    }

    private List<BlogCacheBean> fetchSquareBlogsByPage(int pageNum) throws Exception {
        List<BlogCacheBean> noteBlogs = fetchSquareBlogsByType(SQUARE_POST_TYPE, pageNum);
        List<BlogCacheBean> videoBlogs = fetchSquareBlogsByType(SQUARE_VIDEO_TYPE, pageNum);
        List<BlogCacheBean> mergedBlogs = new ArrayList<>(noteBlogs.size() + videoBlogs.size());

        int maxSize = Math.max(noteBlogs.size(), videoBlogs.size());
        for (int i = 0; i < maxSize; i++) {
            if (i < noteBlogs.size()) {
                mergedBlogs.add(noteBlogs.get(i));
            }
            if (i < videoBlogs.size()) {
                mergedBlogs.add(videoBlogs.get(i));
            }
        }
        return deduplicateSquareBlogs(mergedBlogs);
    }

    private List<BlogCacheBean> fetchSquareBlogsByType(int requestedType, int pageNum) throws Exception {
        Context context = getContext();
        if (context == null) {
            throw new IllegalStateException("SquareFragment is not attached");
        }

        HttpUrl baseUrl = HttpUrl.parse(ApiConfig.API_BLOG_LIST_BY_TYPE);
        if (baseUrl == null) {
            throw new IllegalStateException("Invalid square list api url");
        }

        String token = TokenManager.getToken(context);
        Request request = new Request.Builder()
                .url(baseUrl.newBuilder()
                        .addQueryParameter("type", String.valueOf(requestedType))
                        .addQueryParameter("pageNum", String.valueOf(pageNum))
                        .addQueryParameter("pageSize", String.valueOf(PAGE_SIZE))
                        .build())
                .addHeader("Authorization", "Bearer " + token)
                .get()
                .build();

        Call call = okHttpClient.newCall(request);
        callList.add(call);
        try (Response response = call.execute()) {
            if (isDestroyed) {
                return new ArrayList<>();
            }

            updateSquareTokenFromResponse(response);
            if (!response.isSuccessful() || response.body() == null) {
                throw new IOException("HTTP " + response.code());
            }

            String responseStr = response.body().string();
            JSONObject root = new JSONObject(responseStr);
            if (root.optInt("code", -1) != 200) {
                throw new IOException(root.optString("message", "广场列表请求失败"));
            }

            JSONArray dataArray = extractSquareListArray(root);
            List<BlogEntity> blogEntities = gson.fromJson(
                    dataArray.toString(),
                    new TypeToken<List<BlogEntity>>(){}.getType()
            );
            return parseSquareBlogEntities(blogEntities, requestedType);
        } finally {
            callList.remove(call);
        }
    }

    private List<BlogCacheBean> parseSquareBlogEntities(List<BlogEntity> blogEntities, int requestedType) {
        List<BlogCacheBean> beans = new ArrayList<>();
        if (blogEntities == null) {
            return beans;
        }

        for (BlogEntity entity : blogEntities) {
            BlogCacheBean bean = toSquareBlogCacheBean(entity, requestedType);
            if (bean != null) {
                beans.add(bean);
            }
        }
        return beans;
    }

    private BlogCacheBean toSquareBlogCacheBean(BlogEntity entity, int requestedType) {
        if (entity == null) {
            return null;
        }

        String blogId = entity.getBlog_id();
        if (blogId == null || blogId.trim().isEmpty()) {
            return null;
        }

        int normalizedType = normalizeSquareType(entity.getType(), requestedType);
        if (!isSquareContentType(normalizedType)) {
            return null;
        }

        BlogCacheBean bean = new BlogCacheBean(
                entity.getUser_name(),
                blogId,
                entity.getTitle(),
                entity.getImage_url(),
                entity.getImage_id(),
                entity.getLike_number()
        );
        bean.setType(normalizedType);
        bean.setVideoUrl(entity.getVideoUrl() != null ? entity.getVideoUrl().trim() : "");
        if (entity.getUserId() != null) {
            bean.setUserId(String.valueOf(entity.getUserId()));
        }

        Context context = getContext();
        if (context != null) {
            float density = context.getResources().getDisplayMetrics().density;
            int stableHeightSeed = blogId.hashCode() & Integer.MAX_VALUE;
            bean.setHeight((int) ((150 + (stableHeightSeed % 100)) * density + 0.5f));
        }
        return bean;
    }

    private int normalizeSquareType(int rawType, int requestedType) {
        if (isSquareContentType(rawType)) {
            return rawType;
        }
        if (requestedType == SQUARE_VIDEO_TYPE && rawType == 0) {
            return SQUARE_VIDEO_TYPE;
        }
        return requestedType;
    }

    private boolean isSquareContentType(int type) {
        return type == SQUARE_POST_TYPE || type == SQUARE_VIDEO_TYPE;
    }

    private List<BlogCacheBean> deduplicateSquareBlogs(List<BlogCacheBean> source) {
        LinkedHashMap<String, BlogCacheBean> uniqueBlogs = new LinkedHashMap<>();
        if (source == null) {
            return new ArrayList<>();
        }

        for (BlogCacheBean bean : source) {
            if (bean == null) {
                continue;
            }
            String blogId = bean.getBlogId();
            if (blogId == null || blogId.trim().isEmpty()) {
                continue;
            }
            if (!uniqueBlogs.containsKey(blogId)) {
                uniqueBlogs.put(blogId, bean);
            }
        }
        return new ArrayList<>(uniqueBlogs.values());
    }

    private List<BlogCacheBean> filterSquareContent(List<BlogCacheBean> source) {
        List<BlogCacheBean> filtered = new ArrayList<>();
        if (source == null) {
            return filtered;
        }

        for (BlogCacheBean bean : source) {
            if (bean != null && isSquareContentType(bean.getType())) {
                filtered.add(bean);
            }
        }
        return filtered;
    }

    private List<BlogCacheBean> filterNewSquareBlogs(List<BlogCacheBean> source) {
        Set<String> existingIds = new HashSet<>();
        for (BlogCacheBean bean : blogList) {
            if (bean != null && bean.getBlogId() != null) {
                existingIds.add(bean.getBlogId());
            }
        }

        List<BlogCacheBean> newBlogs = new ArrayList<>();
        for (BlogCacheBean bean : deduplicateSquareBlogs(source)) {
            if (bean == null || bean.getBlogId() == null || existingIds.contains(bean.getBlogId())) {
                continue;
            }
            existingIds.add(bean.getBlogId());
            newBlogs.add(bean);
        }
        return newBlogs;
    }

    private void updateSquareBlogListOnUiThread(List<BlogCacheBean> newBlogs, boolean isRefresh) {
        runOnUiThreadSafe(() -> {
            List<BlogCacheBean> filtered = deduplicateSquareBlogs(filterSquareContent(newBlogs));

            if (isRefresh) {
                blogList.clear();
                blogList.addAll(filtered);
                adapter.notifyDataSetChanged();
                if (recyclerView != null) {
                    recyclerView.scrollToPosition(0);
                }
                if (filtered.isEmpty()) {
                    showToast("广场暂无内容");
                }
            } else {
                List<BlogCacheBean> appendable = filterNewSquareBlogs(filtered);
                if (!appendable.isEmpty()) {
                    int startPos = blogList.size();
                    blogList.addAll(appendable);
                    adapter.notifyItemRangeInserted(startPos, appendable.size());
                } else if (!hasMoreServerData) {
                    showToast("已经到底了");
                }
            }

            finishSquareLoading();
        });
    }

    private JSONArray extractSquareListArray(JSONObject root) {
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

    private void updateSquareTokenFromResponse(Response response) {
        String newToken = response.header("Authorization");
        if (newToken != null && !newToken.isEmpty()) {
            TokenManager.updateToken(getContext(), "", newToken);
        }
    }

    private void handleSquareRequestFailure(boolean isRefresh, Exception e) {
        Log.e(TAG, "square feed request failed: " + e.getMessage(), e);

        if (isRefresh && dbHelper != null) {
            dbHelper.resetLoadedState();
            List<BlogCacheBean> cacheBlogs = dbHelper.getUnloadedBlogs(PAGE_SIZE);
            if (!cacheBlogs.isEmpty()) {
                runOnUiThreadSafe(() -> showToast("网络请求失败，已展示本地缓存"));
                updateSquareBlogListOnUiThread(cacheBlogs, true);
                return;
            }
        }

        runOnUiThreadSafe(() -> {
            showToast(isRefresh ? "广场内容加载失败，请稍后重试" : "加载更多失败，请稍后重试");
            finishSquareLoading();
        });
    }

    private void finishSquareLoading() {
        isLoading.set(false);
        if (swipeRefresh != null) {
            swipeRefresh.setRefreshing(false);
        }
    }

    */
    private void generateMockData() {
        if (getContext() == null) return;

        // 在子线程中准备数据
        new Thread(() -> {
            try {
                List<BlogCacheBean> mockBeans = new ArrayList<>();
                float density = getContext().getResources().getDisplayMetrics().density;

                for (int i = 0; i < 10; i++) {
                    BlogCacheBean bean = new BlogCacheBean(
                            "Athena测试作者 " + (i + 1),
                            "mock_blog_" + System.currentTimeMillis() + "_" + i,
                            "这是模拟标题 " + (i + 1),
                            "https://xiaoxiaolanfeng-java-ai.oss-cn-beijing.aliyuncs.com/9977fb324f344996997b48081aecfae2.jpg",
                            "mock_image_" + i,
                            (int) (Math.random() * 1000)
                    );
                    bean.setHeight((int) ((150 + new Random().nextInt(100)) * density + 0.5f));
                    // 随机生成图文或视频类型的博客
                    if (i % 2 == 0) {
                        bean.setType(SQUARE_POST_TYPE); // 图文
                    } else {
                        bean.setType(2); // 视频
                        bean.setVideoUrl("https://example.com/video.mp4"); // 设置视频URL
                    }
                    mockBeans.add(bean);
                }

                // 线程安全的数据库操作
                if (dbHelper != null) {
                    boolean success = dbHelper.batchAddBlogs(mockBeans);
                    Log.d(TAG, "Mock 数据写入数据库: " + (success ? "成功" : "失败"));
                }

                // 切换到主线程更新 UI
                runOnUiThreadSafe(() -> {
                    blogList.clear();
                    blogList.addAll(mockBeans);
                    adapter.notifyDataSetChanged();
                    if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
                    isLoading.set(false);
                });
            } catch (Exception e) {
                Log.e(TAG, "生成 Mock 数据失败: " + e.getMessage(), e);
                runOnUiThreadSafe(() -> {
                    if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
                    isLoading.set(false);
                });
            }
        }).start();
    }

    private void requestBlogDetail(String blogId, int type, Runnable onSuccess) {
        Request request;
        try {
            okhttp3.HttpUrl.Builder urlBuilder = okhttp3.HttpUrl.parse(ApiConfig.API_BLOG_DETAIL).newBuilder();
            urlBuilder.addQueryParameter("blog_id", blogId);
            urlBuilder.addQueryParameter("type", String.valueOf(type));
            String url = urlBuilder.build().toString();

            // 获取本地Token并添加到请求头
            String token = TokenManager.getToken(getContext());
            request = new Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer " + token)
                    .get()
                    .build();
        } catch (Exception e) {
            runOnUiThreadSafe(() -> {
                Intent intent = new Intent(getContext(), NoteDetailActivity.class);
                intent.putExtra("blog_id", blogId);
                intent.putExtra("type", type);
                startActivity(intent);
            });
            return;
        }

        Call call = okHttpClient.newCall(request);
        callList.add(call);

        call.enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "详情请求失败: " + e.getMessage());
                callList.remove(call);
                runOnUiThreadSafe(() -> {
                    showToast("网络请求详情失败，进入模拟页面");
                    Intent intent = new Intent(getContext(), NoteDetailActivity.class);
                    intent.putExtra("blog_id", blogId);
                    startActivity(intent);
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                callList.remove(call);
                if (isDestroyed) return;

                // 检查响应头中的Token并更新本地
                String newToken = response.header("Authorization");
                if (newToken != null && !newToken.isEmpty()) {
                    TokenManager.updateToken(getContext(), "", newToken);
                }

                if (response.isSuccessful() && response.body() != null) {
                    try {
                        String responseStr = response.body().string();
                        Log.d(TAG, "详情返回: " + responseStr);

                        // 解析统一包装层：{ "code": 200, "message": "...", "data": {...} }
                        JSONObject root = new JSONObject(responseStr);
                        if (root.optInt("code", -1) != 200) {
                            throw new IllegalArgumentException("接口返回非200: " + root.optString("message"));
                        }
                        String dataStr = root.optString("data", null);
                        if (dataStr == null || dataStr.isEmpty() || dataStr.equals("null")) {
                            // 也尝试 JSONObject 形式的 data
                            JSONObject dataObj = root.optJSONObject("data");
                            if (dataObj == null) {
                                throw new IllegalArgumentException("data 字段为空");
                            }
                            dataStr = dataObj.toString();
                        }

                        BlogEntity blogEntity = gson.fromJson(dataStr, BlogEntity.class);

                        if (blogEntity != null) {
                            runOnUiThreadSafe(() -> {
                                Intent intent = new Intent(getContext(), NoteDetailActivity.class);
                                intent.putExtra("blog_id", blogEntity.getBlog_id());
                                intent.putExtra("title", blogEntity.getTitle());
                                intent.putExtra("content", blogEntity.getContent());
                                intent.putExtra("user_name", blogEntity.getUser_name());
                                intent.putExtra("like_number", blogEntity.getLike_number());
                                intent.putExtra("likeTotal", blogEntity.getLiked());
                                intent.putExtra("collectTotal", blogEntity.getCollectTotal());
                                if (blogEntity.getImage_url() != null && !blogEntity.getImage_url().isEmpty()) {
                                    intent.putExtra("cover_url", blogEntity.getImage_url());
                                }
                                if (blogEntity.getUserId() != null) {
                                    intent.putExtra("user_id", blogEntity.getUserId());
                                }
                                if (blogEntity.getPhoto() != null) {
                                    intent.putStringArrayListExtra("photo", new ArrayList<>(blogEntity.getPhoto()));
                                }
                                List<String> dbgPhotos = blogEntity.getPhoto();
                                Log.i("NOTE_DETAIL_IMG", "requestBlogDetail 进详情 | blogId=" + blogEntity.getBlog_id()
                                        + " getPhoto条数=" + (dbgPhotos != null ? dbgPhotos.size() : -1)
                                        + " cover=" + blogEntity.getImage_url());
                                startActivity(intent);
                            });
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "详情解析失败: " + e.getMessage());
                        runOnUiThreadSafe(() -> showToast("详情数据解析失败"));
                    }
                } else {
                    Log.e(TAG, "详情请求失败，状态码: " + response.code());
                    runOnUiThreadSafe(() -> showToast("服务器返回错误: " + response.code()));
                }
            }
        });
    }

    public static class BlogCardAdapter extends RecyclerView.Adapter<BlogCardAdapter.ViewHolder> {

        private List<BlogCacheBean> blogItems;
        private SquareFragment fragment;
        private boolean isDestroyed = false;

        public BlogCardAdapter(List<BlogCacheBean> blogItems, SquareFragment fragment) {
            this.blogItems = blogItems;
            this.fragment = fragment;
        }

        public void setDestroyed(boolean destroyed) {
            this.isDestroyed = destroyed;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_blog_card, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            if (isDestroyed) return;
            BlogCacheBean item = blogItems.get(position);

            String title = item.getTitle() != null ? item.getTitle().trim() : "";
            holder.tvTitle.setText(title.isEmpty() ? "Athena Post" : title);

            String userName = item.getUserName() != null ? item.getUserName().trim() : "";
            holder.tvAuthor.setText(userName.isEmpty() ? "Athena" : userName);
            holder.tvLikeCount.setText(formatLikeCount(item.getLikeNumber()));

            // 🔥 强制重新计算高度！(解决从数据库读出高度为 0 导致图片消失的问题)
            int h = item.getHeight();
            if (h <= 0) {
                float density = holder.itemView.getContext().getResources().getDisplayMetrics().density;
                h = (int) ((150 + new java.util.Random().nextInt(100)) * density + 0.5f);
                item.setHeight(h);
            }
            ViewGroup.LayoutParams params = holder.ivCover.getLayoutParams();
            params.height = h;
            holder.ivCover.setLayoutParams(params);

            // 提取图片 URL（统一使用 coverUrl，无论图文还是视频）
            String imageUrl = item.getImageUrl();

            // 如果后端没返回图片，强制用阿里云测试图片垫底
            if (imageUrl == null || imageUrl.isEmpty()) {
                imageUrl = ApiConfig.MOCK_COVER_URL;
            }

            // 使用 Glide 加载图片，加入粉色占位色
            Glide.with(holder.itemView.getContext())
                    .load(imageUrl)
                    .placeholder(new ColorDrawable(0xFFF5E6E6)) // 加载中显示浅粉色占位
                    .error(new ColorDrawable(0xFFCCCCCC))       // 错误时显示灰色
                    .into(holder.ivCover);

            Glide.with(holder.itemView.getContext())
                    .load(imageUrl)
                    .placeholder(new ColorDrawable(0xFFF6F6F6))
                    .error(new ColorDrawable(0xFFEEEEEE))
                    .into(holder.ivCover);

            holder.cardBlog.setOnClickListener(v -> {
                if (holder.isClickable) {
                    holder.isClickable = false;
                    holder.handler.postDelayed(() -> holder.isClickable = true, 500); // 500ms 防抖

                    Log.d(TAG, "点击广场卡片: blogId=" + item.getBlogId()
                            + ", title=" + item.getTitle()
                            + ", type=" + item.getType()
                            + ", videoUrl=" + item.getVideoUrl());

                    if (item.getType() == 2 || item.getType() == 0) {
                        // 视频类型：直接跳转视频详情页，传递缓存数据
                        Intent intent = new Intent(v.getContext(), VideoDetailActivity.class);
                        intent.putExtra("blog_id", item.getBlogId());
                        intent.putExtra("title", item.getTitle());
                        intent.putExtra("user_name", item.getUserName());
                        intent.putExtra("like_number", item.getLikeNumber());
                        intent.putExtra("video_url", item.getVideoUrl());
                        intent.putExtra("content_type", item.getType());
                        // 传递 userId
                        if (item.getUserId() != null && !item.getUserId().isEmpty()) {
                            intent.putExtra("user_id", Long.parseLong(item.getUserId()));
                        }

                        Log.d(TAG, "跳转 VideoDetailActivity, extras: "
                                + "blog_id=" + item.getBlogId()
                                + ", title=" + item.getTitle()
                                + ", user_name=" + item.getUserName()
                                + ", like_number=" + item.getLikeNumber()
                                + ", video_url=" + item.getVideoUrl()
                                + ", user_id=" + item.getUserId());

                        v.getContext().startActivity(intent);
                    } else {
                        // 图文类型：直接跳转，用列表封面图秒级占位，详情由 NoteDetailActivity 异步拉取
                        Log.d(TAG, "点击的是图文卡片, blogId=" + item.getBlogId() + ", type=" + item.getType());
                        Intent intent = new Intent(v.getContext(), NoteDetailActivity.class);
                        intent.putExtra("blog_id", item.getBlogId());
                        intent.putExtra("title", item.getTitle());
                        intent.putExtra("user_name", item.getUserName());
                        intent.putExtra("like_number", item.getLikeNumber());
                        // 将列表项现成的封面图直接传过去作为占位
                        String cover = item.getImageUrl();
                        if (cover == null || cover.isEmpty()) cover = ApiConfig.MOCK_COVER_URL;
                        intent.putExtra("cover_url", cover);
                        // 传递 userId
                        if (item.getUserId() != null && !item.getUserId().isEmpty()) {
                            intent.putExtra("user_id", Long.parseLong(item.getUserId()));
                        }
                        Log.d(TAG, "直接跳转 NoteDetailActivity: blog_id=" + item.getBlogId()
                                + ", cover_url=" + cover
                                + ", user_id=" + item.getUserId());
                        // 广场列表 BlogCacheBean 仅存单张封面，多图完全依赖详情接口 /blog/Detail 的 imgUrls
                        Log.i("NOTE_DETAIL_IMG", "广场卡片进详情 | blogId=" + item.getBlogId()
                                + " userId=" + item.getUserId()
                                + " Intent仅 cover_url(无 photo 列表) cover=" + cover);
                        v.getContext().startActivity(intent);
                    }
                }
            });
        }

        @Override
        public int getItemCount() {
            return blogItems.size();
        }

        private String formatLikeCount(int count) {
            if (count >= 10000) {
                return String.format("%.1fw", count / 10000.0);
            } else if (count >= 1000) {
                return String.format("%.1fk", count / 1000.0);
            }
            return String.valueOf(Math.max(count, 0));
        }

        public class ViewHolder extends RecyclerView.ViewHolder {
            CardView cardBlog;
            ImageView ivCover;
            TextView tvTitle;
            TextView tvAuthor;
            TextView tvLikeCount; // 👉 第 1 处新增：声明点赞数 TextView

            Handler handler = new Handler(Looper.getMainLooper());
            boolean isClickable = true;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                cardBlog = itemView.findViewById(R.id.card_blog);
                ivCover = itemView.findViewById(R.id.iv_blog_cover);
                tvTitle = itemView.findViewById(R.id.tv_blog_title);
                tvAuthor = itemView.findViewById(R.id.tv_blog_author);
                tvLikeCount = itemView.findViewById(R.id.tv_like_count); // 👉 第 2 处新增：绑定控件 ID
            }
        }
    }

    private void runOnUiThreadSafe(Runnable runnable) {
        if (isAdded() && getActivity() != null && !isDestroyed) {
            getActivity().runOnUiThread(runnable);
        }
    }

    private void showToast(String msg) {
        if (getContext() != null) Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();
    }

    private boolean isNetworkAvailable() {
        if (getContext() == null) return false;
        ConnectivityManager cm = (ConnectivityManager) getContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        return activeNetwork != null && activeNetwork.isConnected();
    }

    private int getMaxPosition(int[] positions) {
        int max = positions[0];
        for (int pos : positions) if (pos > max) max = pos;
        return max;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        isDestroyed = true;
        if (adapter != null) adapter.setDestroyed(true);
        if (recyclerView != null && scrollListener != null) recyclerView.removeOnScrollListener(scrollListener);
        swipeRefresh.setOnRefreshListener(null);
        for (Call call : callList) if (!call.isCanceled()) call.cancel();
        callList.clear();
        if (blogList != null) blogList.clear();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (dbHelper != null) {
            dbHelper.clearAllBlogCache();
        }
        // 清空缓存数据，但不关闭数据库（由单例管理）
        // 保留本地缓存，便于重新进入广场或离线时继续使用。
    }
    }
