package com.whu.software.athena;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.media.MediaMetadataRetriever;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;
import androidx.viewpager2.widget.ViewPager2;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.whu.software.athena.config.ApiConfig;
import com.whu.software.athena.entity.ApiResponse;
import com.whu.software.athena.entity.HealthRecordEntity;
import com.whu.software.athena.utils.ArticleListParseHelper;
import com.whu.software.athena.utils.BlogCacheBean;
import com.whu.software.athena.utils.BlogCacheDBHelper;
import com.whu.software.athena.utils.CycleApiService;
import com.whu.software.athena.utils.CycleDataManager;
import com.whu.software.athena.utils.HealthRecordApiService;
import com.whu.software.athena.utils.MenstrualCalculator;
import com.whu.software.athena.utils.TokenManager;
import com.whu.software.athena.utils.UnsafeOkHttpClient;
import com.whu.software.athena.utils.UserDao;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * 推荐页子 Fragment，作为 KnowledgeFragment 内 ViewPager2 的 position=0 页。
 * 包含：轮播图 Banner（动态拉取频道文章）、经期状态卡片、瀑布流视频列表（type==0）。
 */
public class RecommendFragment extends Fragment {

    private static final String TAG = "RecommendFragment";
    private static final String COVER_DEBUG_PREFIX = "[RecommendCoverDebug]";

    // Banner 相关
    private ViewPager2 bannerViewPager;
    private LinearLayout bannerIndicator;
    private BannerAdapter bannerAdapter;
    private final List<BlogCacheBean> bannerList = new ArrayList<>();
    private final Handler bannerHandler = new Handler(Looper.getMainLooper());
    private Runnable bannerRunnable;
    private int currentBannerPosition = 0;

    // 视频列表
    private RecyclerView videoRecyclerView;
    private RecommendVideoAdapter videoAdapter;
    private final List<BlogCacheBean> videoList = new ArrayList<>();

    private TextView statusGreeting;
    private TextView statusDescription;
    private String currentUserName = "";
    private int currentModeType = 1;

    // 网络 & 工具
    private OkHttpClient okHttpClient;
    private BlogCacheDBHelper dbHelper;
    private boolean isDestroyed = false;

    // -----------------------------------------------------------------------
    // BannerAdapter（数据为 BlogCacheBean：imageUrl 作封面，type 作详情 type）
    // -----------------------------------------------------------------------
    static class BannerAdapter extends RecyclerView.Adapter<BannerAdapter.VH> {

        private final List<BlogCacheBean> data;

        BannerAdapter(List<BlogCacheBean> data) {
            this.data = data;
        }

        void setData(List<BlogCacheBean> items) {
            data.clear();
            if (items != null) {
                data.addAll(items);
            }
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_recommend_banner, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            BlogCacheBean item = data.get(position % data.size());
            String cover = item.getImageUrl();
            if (cover == null || cover.isEmpty()) {
                cover = ApiConfig.MOCK_COVER_URL;
            }

            Glide.with(holder.ivCover.getContext())
                    .load(cover)
                    .placeholder(R.drawable.bg_category_cover)
                    .error(R.drawable.bg_category_cover)
                    .centerCrop()
                    .into(holder.ivCover);

            holder.tvTitle.setText(item.getTitle());

            holder.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(v.getContext(), ArticleDetailActivity.class);
                intent.putExtra("blog_id", item.getBlogId());
                intent.putExtra("title", item.getTitle());
                intent.putExtra("type", item.getType());
                intent.putExtra("article_type", item.getType());
                v.getContext().startActivity(intent);
            });
        }

        @Override
        public int getItemCount() {
            // Simulate infinite scroll: use a large count backed by modulo
            return data.isEmpty() ? 0 : Integer.MAX_VALUE;
        }

        static class VH extends RecyclerView.ViewHolder {
            final ImageView ivCover;
            final android.widget.TextView tvTitle;

            VH(@NonNull View itemView) {
                super(itemView);
                ivCover  = itemView.findViewById(R.id.banner_cover);
                tvTitle  = itemView.findViewById(R.id.banner_title);
            }
        }
    }

    private static class RecommendVideoAdapter
            extends RecyclerView.Adapter<RecommendVideoAdapter.VideoViewHolder> {

        private static final ExecutorService FRAME_EXECUTOR = Executors.newFixedThreadPool(2);
        private static final Map<String, Bitmap> VIDEO_FRAME_CACHE = new ConcurrentHashMap<>();
        private final List<BlogCacheBean> items;

        RecommendVideoAdapter(List<BlogCacheBean> items) {
            this.items = items;
        }

        @NonNull
        @Override
        public VideoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_knowledge_video, parent, false);
            return new VideoViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull VideoViewHolder holder, int position) {
            BlogCacheBean item = items.get(position);

            int height = item.getHeight();
            if (height <= 0) {
                float density = holder.itemView.getContext().getResources()
                        .getDisplayMetrics().density;
                height = (int) ((150 + new Random().nextInt(100)) * density + 0.5f);
                item.setHeight(height);
            }

            ViewGroup.LayoutParams params = holder.cover.getLayoutParams();
            params.height = height;
            holder.cover.setLayoutParams(params);

            String coverUrl = sanitizeMediaUrl(item.getImageUrl());
            String videoUrl = sanitizeMediaUrl(item.getVideoUrl());
            holder.boundBlogId = item.getBlogId();
            holder.boundTitle = item.getTitle();
            Log.d(TAG, COVER_DEBUG_PREFIX + " onBind position=" + position
                    + ", blogId=" + safeLog(holder.boundBlogId)
                    + ", title=" + safeLog(holder.boundTitle)
                    + ", coverUrl=" + shortUrl(coverUrl)
                    + ", videoUrl=" + shortUrl(videoUrl)
                    + ", height=" + height);
            bindVideoCover(holder, coverUrl, videoUrl);

            String userName = item.getUserName();
            String displayUserName = userName == null || userName.isEmpty()
                    ? "Unknown user"
                    : userName;
            holder.title.setText(item.getTitle());
            holder.username.setText(displayUserName);
            holder.likeCount.setText(formatLikes(item.getLikeNumber()));
            holder.avatar.setImageDrawable(null);

            holder.card.setOnClickListener(v -> {
                if (!holder.clickable) return;
                holder.clickable = false;
                holder.handler.postDelayed(() -> holder.clickable = true, 500);

                Intent intent = new Intent(v.getContext(), VideoDetailActivity.class);
                intent.putExtra("blog_id", item.getBlogId());
                intent.putExtra("title", item.getTitle());
                intent.putExtra("user_name", displayUserName);
                intent.putExtra("like_number", item.getLikeNumber());
                intent.putExtra("video_url", item.getVideoUrl());
                intent.putExtra("content_type", item.getType());
                if (item.getUserId() != null && !item.getUserId().isEmpty()) {
                    try {
                        intent.putExtra("user_id", Long.parseLong(item.getUserId()));
                    } catch (NumberFormatException e) {
                        Log.w(TAG, "Failed to parse video author userId: " + item.getUserId(), e);
                    }
                }
                v.getContext().startActivity(intent);
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        private String formatLikes(int count) {
            return count >= 10000
                    ? String.format(java.util.Locale.getDefault(), "%.1fw", count / 10000.0)
                    : String.valueOf(count);
        }

        private void bindVideoCover(@NonNull VideoViewHolder holder,
                                    @Nullable String coverUrl,
                                    @Nullable String videoUrl) {
            String requestCoverUrl = sanitizeMediaUrl(coverUrl);
            String requestVideoUrl = sanitizeMediaUrl(videoUrl);
            if (requestVideoUrl.isEmpty() && looksLikeVideoUrl(requestCoverUrl)) {
                Log.d(TAG, buildHolderLogPrefix(holder)
                        + " cover url looks like video url, reusing it as video source"
                        + ", coverUrl=" + shortUrl(requestCoverUrl));
                requestVideoUrl = requestCoverUrl;
                requestCoverUrl = "";
            }
            holder.boundVideoKey = requestVideoUrl;

            String fallbackReason = getFirstFrameFallbackReason(requestCoverUrl, requestVideoUrl);
            Log.d(TAG, buildHolderLogPrefix(holder)
                    + " bindVideoCover normalizedCover=" + shortUrl(requestCoverUrl)
                    + ", normalizedVideo=" + shortUrl(requestVideoUrl)
                    + ", fallbackReason=" + safeLog(fallbackReason));

            if (fallbackReason != null) {
                loadVideoFirstFrame(holder, requestVideoUrl);
                return;
            }

            loadImageCover(holder, requestCoverUrl, requestVideoUrl);
        }

        private void loadVideoFirstFrame(@NonNull VideoViewHolder holder, @NonNull String videoUrl) {
            if (videoUrl.isEmpty()) {
                Log.w(TAG, buildHolderLogPrefix(holder)
                        + " loadVideoFirstFrame skipped because video url is empty");
                holder.cover.setImageDrawable(new ColorDrawable(0xFFF5E6E6));
                return;
            }

            Log.d(TAG, buildHolderLogPrefix(holder)
                    + " loadVideoFirstFrame start, videoUrl=" + shortUrl(videoUrl));
            Glide.with(holder.itemView.getContext()).clear(holder.cover);

            Bitmap cachedFrame = VIDEO_FRAME_CACHE.get(videoUrl);
            if (cachedFrame != null && !cachedFrame.isRecycled()) {
                Log.d(TAG, buildHolderLogPrefix(holder)
                        + " loadVideoFirstFrame cache hit, size="
                        + cachedFrame.getWidth() + "x" + cachedFrame.getHeight());
                holder.cover.setImageBitmap(cachedFrame);
                return;
            }

            Log.d(TAG, buildHolderLogPrefix(holder)
                    + " loadVideoFirstFrame cache miss, extracting first frame");
            holder.cover.setImageDrawable(new ColorDrawable(0xFFF5E6E6));
            FRAME_EXECUTOR.execute(() -> {
                Bitmap frameBitmap = extractVideoFirstFrame(videoUrl);
                if (frameBitmap != null) {
                    VIDEO_FRAME_CACHE.put(videoUrl, frameBitmap);
                }
                holder.cover.post(() -> {
                    if (!videoUrl.equals(holder.boundVideoKey)) {
                        Log.d(TAG, buildHolderLogPrefix(holder)
                                + " loadVideoFirstFrame ignored because holder was rebound"
                                + ", requestVideo=" + shortUrl(videoUrl)
                                + ", currentBoundVideo=" + shortUrl(holder.boundVideoKey));
                        return;
                    }
                    if (frameBitmap != null) {
                        Log.d(TAG, buildHolderLogPrefix(holder)
                                + " loadVideoFirstFrame success, size="
                                + frameBitmap.getWidth() + "x" + frameBitmap.getHeight());
                        holder.cover.setImageBitmap(frameBitmap);
                    } else {
                        Log.w(TAG, buildHolderLogPrefix(holder)
                                + " loadVideoFirstFrame failed, keeping placeholder");
                        holder.cover.setImageDrawable(new ColorDrawable(0xFFF5E6E6));
                    }
                });
            });
        }

        private void loadImageCover(@NonNull VideoViewHolder holder,
                                    @Nullable String coverUrl,
                                    @Nullable String videoUrl) {
            String finalCoverUrl = sanitizeMediaUrl(coverUrl);
            String finalVideoUrl = sanitizeMediaUrl(videoUrl);
            if (finalCoverUrl.isEmpty()) {
                if (!finalVideoUrl.isEmpty()) {
                    Log.d(TAG, buildHolderLogPrefix(holder)
                            + " loadImageCover found empty cover, fallback to first frame"
                            + ", videoUrl=" + shortUrl(finalVideoUrl));
                    loadVideoFirstFrame(holder, finalVideoUrl);
                } else {
                    Log.w(TAG, buildHolderLogPrefix(holder)
                            + " loadImageCover found both cover and video empty");
                    holder.cover.setImageDrawable(new ColorDrawable(0xFFF5E6E6));
                }
                return;
            }
            Log.d(TAG, buildHolderLogPrefix(holder)
                    + " loadImageCover via Glide, coverUrl=" + shortUrl(finalCoverUrl)
                    + ", videoUrl=" + shortUrl(finalVideoUrl));
            Glide.with(holder.itemView.getContext())
                    .load(finalCoverUrl)
                    .placeholder(new ColorDrawable(0xFFF5E6E6))
                    .error(new ColorDrawable(0xFFCCCCCC))
                    .listener(new RequestListener<Drawable>() {
                        @Override
                        public boolean onLoadFailed(@Nullable GlideException e,
                                                    Object model,
                                                    Target<Drawable> target,
                                                    boolean isFirstResource) {
                            Log.w(TAG, buildHolderLogPrefix(holder)
                                    + " Glide cover load failed, coverUrl=" + shortUrl(finalCoverUrl)
                                    + ", videoUrl=" + shortUrl(finalVideoUrl)
                                    + ", reason=" + (e != null ? e.getMessage() : "null"));
                            if (!finalVideoUrl.isEmpty()
                                    && finalVideoUrl.equals(holder.boundVideoKey)) {
                                holder.cover.post(() -> {
                                    if (finalVideoUrl.equals(holder.boundVideoKey)) {
                                        Log.d(TAG, buildHolderLogPrefix(holder)
                                                + " Glide failure triggers first-frame fallback");
                                        loadVideoFirstFrame(holder, finalVideoUrl);
                                    }
                                });
                                return true;
                            }
                            return false;
                        }

                        @Override
                        public boolean onResourceReady(Drawable resource,
                                                       Object model,
                                                       Target<Drawable> target,
                                                       DataSource dataSource,
                                                       boolean isFirstResource) {
                            Log.d(TAG, buildHolderLogPrefix(holder)
                                    + " Glide cover load success, dataSource=" + dataSource
                                    + ", coverUrl=" + shortUrl(finalCoverUrl));
                            return false;
                        }
                    })
                    .centerCrop()
                    .into(holder.cover);
        }

        private boolean shouldUseVideoFirstFrame(@Nullable String coverUrl, @Nullable String videoUrl) {
            return getFirstFrameFallbackReason(coverUrl, videoUrl) != null;
        }

        @Nullable
        private String getFirstFrameFallbackReason(@Nullable String coverUrl, @Nullable String videoUrl) {
            String sanitizedVideoUrl = sanitizeMediaUrl(videoUrl);
            if (sanitizedVideoUrl.isEmpty()) {
                return null;
            }
            String sanitizedCoverUrl = sanitizeMediaUrl(coverUrl);
            if (sanitizedCoverUrl.isEmpty()) {
                return "cover_empty";
            }
            if (looksLikeVideoUrl(sanitizedCoverUrl)) {
                return "cover_is_video_url";
            }
            if (sanitizedCoverUrl.equals(ApiConfig.MOCK_COVER_URL)) {
                return "cover_is_mock";
            }
            if (lastPathSegment(sanitizedCoverUrl)
                    .equalsIgnoreCase(lastPathSegment(ApiConfig.MOCK_COVER_URL))) {
                return "cover_matches_mock_filename";
            }
            return null;
        }

        private boolean isFallbackCoverUrl(@Nullable String coverUrl) {
            String trimmed = sanitizeMediaUrl(coverUrl);
            if (trimmed.isEmpty()) {
                return true;
            }
            if (looksLikeVideoUrl(trimmed)) {
                return true;
            }
            if (trimmed.equals(ApiConfig.MOCK_COVER_URL)) {
                return true;
            }
            return lastPathSegment(trimmed).equalsIgnoreCase(lastPathSegment(ApiConfig.MOCK_COVER_URL));
        }

        @NonNull
        private String sanitizeMediaUrl(@Nullable String url) {
            if (url == null) {
                return "";
            }
            String sanitized = url.replace("`", "").trim();
            if (sanitized.isEmpty() || "null".equalsIgnoreCase(sanitized)) {
                return "";
            }
            return sanitized;
        }

        @NonNull
        private String lastPathSegment(@NonNull String url) {
            String normalized = url;
            int queryIndex = normalized.indexOf('?');
            if (queryIndex >= 0) {
                normalized = normalized.substring(0, queryIndex);
            }
            int slashIndex = normalized.lastIndexOf('/');
            return slashIndex >= 0 ? normalized.substring(slashIndex + 1) : normalized;
        }

        private boolean looksLikeVideoUrl(@Nullable String url) {
            String normalized = sanitizeMediaUrl(url).toLowerCase(java.util.Locale.ROOT);
            if (normalized.isEmpty()) {
                return false;
            }
            int queryIndex = normalized.indexOf('?');
            if (queryIndex >= 0) {
                normalized = normalized.substring(0, queryIndex);
            }
            return normalized.endsWith(".mp4")
                    || normalized.endsWith(".m3u8")
                    || normalized.endsWith(".mov")
                    || normalized.endsWith(".mkv")
                    || normalized.endsWith(".webm");
        }

        @Nullable
        private Bitmap extractVideoFirstFrame(@NonNull String videoUrl) {
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            try {
                Log.d(TAG, COVER_DEBUG_PREFIX + " extractVideoFirstFrame setDataSource="
                        + shortUrl(videoUrl));
                retriever.setDataSource(videoUrl, new HashMap<>());
                Bitmap bitmap = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                if (bitmap != null) {
                    Log.d(TAG, COVER_DEBUG_PREFIX + " extractVideoFirstFrame success, videoUrl="
                            + shortUrl(videoUrl) + ", size=" + bitmap.getWidth() + "x" + bitmap.getHeight());
                } else {
                    Log.w(TAG, COVER_DEBUG_PREFIX + " extractVideoFirstFrame returned null bitmap, videoUrl="
                            + shortUrl(videoUrl));
                }
                return bitmap;
            } catch (Exception e) {
                Log.w(TAG, COVER_DEBUG_PREFIX + " extractVideoFirstFrame failed, videoUrl="
                        + shortUrl(videoUrl), e);
                return null;
            } finally {
                try {
                    retriever.release();
                } catch (Exception ignored) {
                    // Ignore retriever cleanup errors.
                }
            }
        }

        static class VideoViewHolder extends RecyclerView.ViewHolder {
            final CardView card;
            final ImageView cover;
            final TextView title;
            final ImageView avatar;
            final TextView username;
            final TextView likeCount;
            final Handler handler = new Handler(Looper.getMainLooper());
            boolean clickable = true;
            @Nullable String boundVideoKey;
            @Nullable String boundBlogId;
            @Nullable String boundTitle;

            VideoViewHolder(@NonNull View itemView) {
                super(itemView);
                card = itemView.findViewById(R.id.knowledge_video_card);
                cover = itemView.findViewById(R.id.knowledge_video_cover);
                title = itemView.findViewById(R.id.knowledge_video_title);
                avatar = itemView.findViewById(R.id.knowledge_video_avatar);
                username = itemView.findViewById(R.id.knowledge_video_username);
                likeCount = itemView.findViewById(R.id.knowledge_video_like_count);
            }
        }

        @NonNull
        private String buildHolderLogPrefix(@NonNull VideoViewHolder holder) {
            int position = holder.getBindingAdapterPosition();
            return COVER_DEBUG_PREFIX
                    + " blogId=" + safeLog(holder.boundBlogId)
                    + ", position=" + position
                    + ", title=" + safeLog(holder.boundTitle)
                    + " |";
        }

        @NonNull
        private String shortUrl(@Nullable String url) {
            String sanitized = sanitizeMediaUrl(url);
            if (sanitized.isEmpty()) {
                return "<empty>";
            }
            if (sanitized.length() <= 120) {
                return sanitized;
            }
            return sanitized.substring(0, 60) + "..." + sanitized.substring(sanitized.length() - 30);
        }

        @NonNull
        private String safeLog(@Nullable String value) {
            if (value == null) {
                return "<null>";
            }
            String trimmed = value.trim();
            return trimmed.isEmpty() ? "<empty>" : trimmed;
        }
    }

    // -----------------------------------------------------------------------
    // Fragment 生命周期
    // -----------------------------------------------------------------------

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_recommend, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        isDestroyed = false;
        okHttpClient = UnsafeOkHttpClient.getUnsafeOkHttpClient();
        if (getContext() != null) {
            dbHelper = BlogCacheDBHelper.getInstance(getContext());
        }

        bannerViewPager   = view.findViewById(R.id.knowledge_banner);
        bannerIndicator   = view.findViewById(R.id.knowledge_banner_indicator);
        videoRecyclerView = view.findViewById(R.id.knowledge_video_grid);
        statusGreeting    = view.findViewById(R.id.knowledge_status_greeting);
        statusDescription = view.findViewById(R.id.knowledge_status_description);

        setupBanner();
        setupBannerNavButtons(view);
        setupVideoList();
        setupClickListeners(view);
        updateStatusCard();
        loadCurrentUserName();
        loadTodayMode();
        syncLatestCycleThenRefresh();

        loadBannerData();
        loadVideoData();
    }

    @Override
    public void onResume() {
        super.onResume();
        updateStatusCard();
        loadCurrentUserName();
        loadTodayMode();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        isDestroyed = true;
        bannerHandler.removeCallbacksAndMessages(null);
        bannerViewPager = null;
        videoRecyclerView = null;
    }

    // -----------------------------------------------------------------------
    // Banner 初始化（Adapter 先空列表，数据回来后刷新）
    // -----------------------------------------------------------------------

    private void setupBanner() {
        bannerAdapter = new BannerAdapter(bannerList);
        bannerViewPager.setAdapter(bannerAdapter);
        bannerViewPager.setUserInputEnabled(true);

        bannerViewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                currentBannerPosition = position;
                updateIndicators(position);
            }
        });
    }

    private void startAutoScroll() {
        if (bannerList.isEmpty()) return;
        if (bannerRunnable != null) {
            bannerHandler.removeCallbacks(bannerRunnable);
        }
        // Start from a position that allows infinite scroll in both directions
        int start = Integer.MAX_VALUE / 2 - (Integer.MAX_VALUE / 2 % bannerList.size());
        bannerViewPager.setCurrentItem(start, false);
        currentBannerPosition = start;

        bannerRunnable = () -> {
            if (bannerViewPager != null && !bannerList.isEmpty()) {
                bannerViewPager.setCurrentItem(++currentBannerPosition, true);
                bannerHandler.postDelayed(bannerRunnable, 3000);
            }
        };
        bannerHandler.postDelayed(bannerRunnable, 3000);
    }

    /** 手动翻页后推迟自动轮播，避免与点击冲突 */
    private void rescheduleBannerAutoScroll() {
        if (bannerRunnable == null || bannerList.isEmpty()) return;
        bannerHandler.removeCallbacks(bannerRunnable);
        bannerHandler.postDelayed(bannerRunnable, 3000);
    }

    private void setupBannerNavButtons(View root) {
        View btnPrev = root.findViewById(R.id.btn_banner_left);
        View btnNext = root.findViewById(R.id.btn_banner_right);
        if (btnPrev != null) {
            btnPrev.setOnClickListener(v -> {
                if (bannerViewPager == null || bannerList.isEmpty()) return;
                bannerViewPager.setCurrentItem(bannerViewPager.getCurrentItem() - 1, true);
                rescheduleBannerAutoScroll();
            });
        }
        if (btnNext != null) {
            btnNext.setOnClickListener(v -> {
                if (bannerViewPager == null || bannerList.isEmpty()) return;
                bannerViewPager.setCurrentItem(bannerViewPager.getCurrentItem() + 1, true);
                rescheduleBannerAutoScroll();
            });
        }
    }

    private void setupIndicators() {
        if (bannerIndicator == null) return;
        bannerIndicator.removeAllViews();
        for (int i = 0; i < bannerList.size(); i++) {
            ImageView dot = new ImageView(getContext());
            LinearLayout.LayoutParams params =
                    new LinearLayout.LayoutParams(dpToPx(8), dpToPx(8));
            params.setMargins(dpToPx(4), 0, dpToPx(4), 0);
            dot.setLayoutParams(params);
            dot.setImageResource(i == 0
                    ? R.drawable.knowledge_banner_indicator_selected
                    : R.drawable.knowledge_banner_indicator_unselected);
            bannerIndicator.addView(dot);
        }
    }

    private void updateIndicators(int position) {
        if (bannerList.isEmpty() || bannerIndicator == null) return;
        int real = position % bannerList.size();
        for (int i = 0; i < bannerIndicator.getChildCount(); i++) {
            ((ImageView) bannerIndicator.getChildAt(i)).setImageResource(
                    i == real
                            ? R.drawable.knowledge_banner_indicator_selected
                            : R.drawable.knowledge_banner_indicator_unselected);
        }
    }

    // -----------------------------------------------------------------------
    // 拉取 Banner：单后台线程内同步请求 channelId 1~4，每频道取 1 篇，避免并发错乱
    // -----------------------------------------------------------------------

    private void loadBannerData() {
        if (!isNetworkAvailable()) {
            Log.w(TAG, "无网络，Banner 数据无法加载");
            return;
        }
        Context ctx = getContext();
        if (ctx == null) return;
        final String token = TokenManager.getToken(ctx);
        final String authToken = token != null ? token : "";

        new Thread(() -> {
            List<BlogCacheBean> bannerItems = new ArrayList<>();
            for (int channelId = 1; channelId <= 4; channelId++) {
                if (isDestroyed) {
                    return;
                }
                try {
                    HttpUrl url = HttpUrl.parse(ApiConfig.API_BLOG_LIST_BY_CHANNEL).newBuilder()
                            .addQueryParameter("channelId", String.valueOf(channelId))
                            .addQueryParameter("pageNum", "1")
                            .addQueryParameter("pageSize", "1")
                            .build();

                    Request request = new Request.Builder()
                            .url(url)
                            .addHeader("Authorization", "Bearer " + authToken)
                            .get()
                            .build();

                    try (Response response = okHttpClient.newCall(request).execute()) {
                        if (!response.isSuccessful() || response.body() == null) {
                            Log.w(TAG, "Banner 频道 " + channelId + " HTTP=" + response.code());
                            continue;
                        }
                        String json = response.body().string();
                        BlogCacheBean bean = parseFirstBannerRowFromJson(json);
                        if (bean != null) {
                            bannerItems.add(bean);
                            Log.d(TAG, "Banner 频道 " + channelId + " 取到 blogId=" + bean.getBlogId());
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Banner 频道 " + channelId + " 请求失败", e);
                }
            }

            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    if (isDestroyed || bannerViewPager == null) {
                        return;
                    }
                    if (!bannerItems.isEmpty()) {
                        bannerAdapter.setData(bannerItems);
                        bannerAdapter.notifyDataSetChanged();
                        setupIndicators();
                        startAutoScroll();
                    }
                });
            }
        }, "RecommendBannerLoad").start();
    }

    /**
     * 解析频道列表接口响应，取 data 列表第一条，封装为 {@link BlogCacheBean}（封面用 imageUrl 字段存 coverUrl）。
     */
    @Nullable
    private BlogCacheBean parseFirstBannerRowFromJson(String body) {
        try {
            JSONObject root = new JSONObject(body);
            if (root.optInt("code", -1) != 200) {
                return null;
            }
            JSONArray listArray = extractListArray(root);
            if (listArray == null || listArray.length() == 0) {
                return null;
            }
            JSONObject obj = listArray.optJSONObject(0);
            if (obj == null) {
                return null;
            }

            String blogId = obj.optString("blogId", "");
            if (blogId.isEmpty()) blogId = obj.optString("id", "");
            if (blogId.isEmpty()) blogId = obj.optString("blog_id", "");
            if (blogId.isEmpty()) {
                return null;
            }

            String title = obj.optString("title", "");
            if (title.isEmpty()) title = obj.optString("blogTitle", "");

            String coverUrl = obj.optString("coverUrl", "");
            if (coverUrl.isEmpty()) coverUrl = obj.optString("image_url", "");
            if (coverUrl.isEmpty()) coverUrl = obj.optString("imageUrl", "");
            if (coverUrl.isEmpty()) coverUrl = ApiConfig.MOCK_COVER_URL;

            int blogType = ArticleListParseHelper.parseTypeField(obj);
            if (blogType == ArticleListParseHelper.MISSING_ID) {
                blogType = obj.optInt("blogType", 100);
            }

            BlogCacheBean bean = new BlogCacheBean("", blogId, title, coverUrl, "", 0);
            bean.setType(blogType);
            return bean;
        } catch (Exception e) {
            Log.e(TAG, "parseFirstBannerRowFromJson: " + e.getMessage(), e);
            return null;
        }
    }

    /** 从 JSON 根对象中提取列表数组，兼容 data 为对象嵌套 records/list 或直接为数组的情况 */
    @Nullable
    private JSONArray extractListArray(JSONObject root) {
        try {
            Object data = root.opt("data");
            if (data instanceof JSONArray) {
                return (JSONArray) data;
            }
            if (data instanceof JSONObject) {
                JSONObject dataObj = (JSONObject) data;
                JSONArray arr = dataObj.optJSONArray("records");
                if (arr != null) return arr;
                arr = dataObj.optJSONArray("list");
                if (arr != null) return arr;
            }
        } catch (Exception e) {
            Log.e(TAG, "extractListArray 失败: " + e.getMessage());
        }
        return null;
    }

    @Nullable
    private BlogCacheBean parseRecommendVideoRow(@Nullable JSONObject obj, float density) {
        if (obj == null) {
            Log.w(TAG, COVER_DEBUG_PREFIX + " parseRecommendVideoRow skipped: row is null");
            return null;
        }

        JSONObject blogObj = obj.optJSONObject("blog");
        int type = ArticleListParseHelper.parseTypeField(obj);
        if (type == ArticleListParseHelper.MISSING_ID && blogObj != null) {
            type = ArticleListParseHelper.parseTypeField(blogObj);
        }
        if (type != 0) {
            Log.d(TAG, COVER_DEBUG_PREFIX + " parseRecommendVideoRow skipped non-video row"
                    + ", parsedType=" + type
                    + ", topLevelId=" + optString(obj, "id")
                    + ", blogId=" + optString(obj, "blogId"));
            return null;
        }

        String blogId = parseBlogId(obj, blogObj);
        if (blogId.isEmpty()) {
            Log.w(TAG, COVER_DEBUG_PREFIX + " parseRecommendVideoRow skipped: empty blogId"
                    + ", rawRow=" + obj.toString());
            return null;
        }

        String title = firstNonEmpty(
                optString(obj, "title"),
                optString(obj, "blogTitle"),
                optString(blogObj, "title"),
                optString(blogObj, "blogTitle")
        );

        String coverUrl = extractCoverUrl(obj, blogObj);
        String videoUrl = extractVideoUrl(obj, blogObj);

        int likeNumber = parseLikeNumber(obj, blogObj);
        String userName = parseUserName(obj, blogObj);
        String userId = parseUserId(obj, blogObj);

        BlogCacheBean bean = new BlogCacheBean(
                userName,
                blogId,
                title,
                coverUrl,
                "img_" + blogId,
                likeNumber
        );
        bean.setType(type);
        bean.setVideoUrl(videoUrl);
        if (!userId.isEmpty()) {
            bean.setUserId(userId);
        }
        bean.setHeight((int) ((150 + new Random().nextInt(100)) * density + 0.5f));

        Log.d(TAG, COVER_DEBUG_PREFIX + " Recommend video parsed: blogId=" + blogId
                + ", title=" + title
                + ", coverUrl=" + coverUrl
                + ", videoUrl=" + videoUrl
                + ", topLevelCandidates="
                + "{coverUrl=" + optString(obj, "coverUrl")
                + ", imageUrl=" + optString(obj, "imageUrl")
                + ", image_url=" + optString(obj, "image_url")
                + ", cover=" + optString(obj, "cover")
                + ", posterUrl=" + optString(obj, "posterUrl")
                + ", thumbnailUrl=" + optString(obj, "thumbnailUrl")
                + ", videoUrl=" + optString(obj, "videoUrl")
                + ", playUrl=" + optString(obj, "playUrl")
                + ", mediaUrl=" + optString(obj, "mediaUrl")
                + "}"
                + ", nestedCandidates="
                + (blogObj == null ? "<null>" : "{coverUrl=" + optString(blogObj, "coverUrl")
                + ", imageUrl=" + optString(blogObj, "imageUrl")
                + ", image_url=" + optString(blogObj, "image_url")
                + ", cover=" + optString(blogObj, "cover")
                + ", posterUrl=" + optString(blogObj, "posterUrl")
                + ", thumbnailUrl=" + optString(blogObj, "thumbnailUrl")
                + ", videoUrl=" + optString(blogObj, "videoUrl")
                + ", playUrl=" + optString(blogObj, "playUrl")
                + ", mediaUrl=" + optString(blogObj, "mediaUrl")
                + "}"));
        return bean;
    }

    @NonNull
    private String extractCoverUrl(@Nullable JSONObject obj, @Nullable JSONObject blogObj) {
        return firstNonEmpty(
                optString(obj, "coverUrl"),
                optString(obj, "cover"),
                optString(obj, "imageUrl"),
                optString(obj, "image_url"),
                optString(obj, "cover_url"),
                optString(obj, "posterUrl"),
                optString(obj, "poster_url"),
                optString(obj, "poster"),
                optString(obj, "thumbnailUrl"),
                optString(obj, "thumbnail"),
                optString(blogObj, "coverUrl"),
                optString(blogObj, "cover"),
                optString(blogObj, "imageUrl"),
                optString(blogObj, "image_url"),
                optString(blogObj, "cover_url"),
                optString(blogObj, "posterUrl"),
                optString(blogObj, "poster_url"),
                optString(blogObj, "poster"),
                optString(blogObj, "thumbnailUrl"),
                optString(blogObj, "thumbnail")
        );
    }

    @NonNull
    private String extractVideoUrl(@Nullable JSONObject obj, @Nullable JSONObject blogObj) {
        return firstNonEmpty(
                optString(obj, "videoUrl"),
                optString(obj, "video_url"),
                optString(obj, "playUrl"),
                optString(obj, "play_url"),
                optString(obj, "mediaUrl"),
                optString(obj, "media_url"),
                optString(obj, "fileUrl"),
                optString(obj, "file_url"),
                optString(blogObj, "videoUrl"),
                optString(blogObj, "video_url"),
                optString(blogObj, "playUrl"),
                optString(blogObj, "play_url"),
                optString(blogObj, "mediaUrl"),
                optString(blogObj, "media_url"),
                optString(blogObj, "fileUrl"),
                optString(blogObj, "file_url")
        );
    }

    @NonNull
    private String parseBlogId(@NonNull JSONObject obj, @Nullable JSONObject blogObj) {
        String blogId = firstValidId(
                optString(obj, "blogId"),
                optString(obj, "noteId"),
                optString(obj, "id"),
                optString(obj, "blog_id")
        );
        if (!blogId.isEmpty()) {
            return blogId;
        }
        return firstValidId(
                optString(blogObj, "blogId"),
                optString(blogObj, "noteId"),
                optString(blogObj, "id"),
                optString(blogObj, "blog_id")
        );
    }

    @NonNull
    private String parseUserName(@NonNull JSONObject obj, @Nullable JSONObject blogObj) {
        JSONObject userDTO = obj.optJSONObject("userDTO");
        JSONObject nestedUserDTO = blogObj != null ? blogObj.optJSONObject("userDTO") : null;
        return firstNonEmpty(
                optString(userDTO, "nickName"),
                optString(obj, "nickName"),
                optString(obj, "userName"),
                optString(nestedUserDTO, "nickName"),
                optString(blogObj, "nickName"),
                optString(blogObj, "userName")
        );
    }

    @NonNull
    private String parseUserId(@NonNull JSONObject obj, @Nullable JSONObject blogObj) {
        JSONObject userDTO = obj.optJSONObject("userDTO");
        JSONObject nestedUserDTO = blogObj != null ? blogObj.optJSONObject("userDTO") : null;
        return firstValidId(
                optString(userDTO, "userId"),
                optString(userDTO, "id"),
                optString(obj, "userId"),
                optString(nestedUserDTO, "userId"),
                optString(nestedUserDTO, "id"),
                optString(blogObj, "userId")
        );
    }

    private int parseLikeNumber(@NonNull JSONObject obj, @Nullable JSONObject blogObj) {
        int[] values = {
                parseIntField(obj, "liked"),
                parseIntField(obj, "likeTotal"),
                parseIntField(obj, "like_number"),
                parseIntField(obj, "likeCount"),
                parseIntField(blogObj, "liked"),
                parseIntField(blogObj, "likeTotal"),
                parseIntField(blogObj, "like_number"),
                parseIntField(blogObj, "likeCount")
        };
        for (int value : values) {
            if (value >= 0) {
                return value;
            }
        }
        return 0;
    }

    private int parseIntField(@Nullable JSONObject obj, @NonNull String key) {
        if (obj == null || !obj.has(key) || obj.isNull(key)) {
            return -1;
        }
        try {
            return obj.getInt(key);
        } catch (Exception ignored) {
            try {
                String raw = obj.optString(key, "").trim();
                return raw.isEmpty() ? -1 : Integer.parseInt(raw);
            } catch (Exception ignoredAgain) {
                return -1;
            }
        }
    }

    @NonNull
    private String firstValidId(String... values) {
        for (String value : values) {
            if (value == null) {
                continue;
            }
            String trimmed = value.trim();
            if (!trimmed.isEmpty() && !"0".equals(trimmed) && !"null".equalsIgnoreCase(trimmed)) {
                return trimmed;
            }
        }
        return "";
    }

    @NonNull
    private String firstNonEmpty(String... values) {
        for (String value : values) {
            if (value == null) {
                continue;
            }
            String trimmed = value.trim();
            if (!trimmed.isEmpty() && !"null".equalsIgnoreCase(trimmed)) {
                return trimmed;
            }
        }
        return "";
    }

    @NonNull
    private String optString(@Nullable JSONObject obj, @NonNull String key) {
        if (obj == null || !obj.has(key) || obj.isNull(key)) {
            return "";
        }
        return obj.optString(key, "").trim();
    }

    private int getAlternateRecommendVideoType(int currentType) {
        return currentType == 0 ? 2 : 0;
    }

    private void enrichRecommendVideoDetails(@NonNull List<BlogCacheBean> items, @Nullable String token) {
        if (items.isEmpty()) {
            return;
        }
        String authToken = token == null ? "" : token.trim();
        for (BlogCacheBean item : items) {
            if (item == null) {
                continue;
            }
            String blogId = item.getBlogId();
            if (blogId == null || blogId.trim().isEmpty()) {
                continue;
            }
            String existingCoverUrl = firstNonEmpty(item.getImageUrl());
            String existingVideoUrl = firstNonEmpty(item.getVideoUrl());
            if (!existingCoverUrl.isEmpty() && !existingVideoUrl.isEmpty()) {
                continue;
            }
            requestRecommendVideoDetail(blogId, item.getType(), false, authToken);
        }
    }

    private void requestRecommendVideoDetail(@NonNull String blogId,
                                             int requestedType,
                                             boolean hasRetriedAlternateType,
                                             @NonNull String authToken) {
        HttpUrl baseUrl = HttpUrl.parse(ApiConfig.API_BLOG_DETAIL);
        if (baseUrl == null) {
            Log.e(TAG, COVER_DEBUG_PREFIX + " detail enrich skipped because detail url is invalid");
            return;
        }

        HttpUrl detailUrl = baseUrl.newBuilder()
                .addQueryParameter("blog_id", blogId)
                .addQueryParameter("type", String.valueOf(requestedType))
                .build();
        Request.Builder requestBuilder = new Request.Builder()
                .url(detailUrl)
                .get();
        if (!authToken.isEmpty()) {
            requestBuilder.addHeader("Authorization", "Bearer " + authToken);
        }

        Log.d(TAG, COVER_DEBUG_PREFIX + " detail enrich request"
                + " blogId=" + blogId
                + ", type=" + requestedType
                + ", retried=" + hasRetriedAlternateType
                + ", url=" + detailUrl);
        okHttpClient.newCall(requestBuilder.build()).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                if (!hasRetriedAlternateType) {
                    int alternateType = getAlternateRecommendVideoType(requestedType);
                    Log.w(TAG, COVER_DEBUG_PREFIX + " detail enrich network failed, retry alternate type"
                            + " blogId=" + blogId
                            + ", fromType=" + requestedType
                            + ", toType=" + alternateType
                            + ", error=" + e.getMessage());
                    requestRecommendVideoDetail(blogId, alternateType, true, authToken);
                    return;
                }
                Log.e(TAG, COVER_DEBUG_PREFIX + " detail enrich failed"
                        + " blogId=" + blogId
                        + ", type=" + requestedType
                        + ", error=" + e.getMessage(), e);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                String body = response.body() != null ? response.body().string() : "";
                try {
                    JSONObject root = new JSONObject(body);
                    int code = root.optInt("code", -1);
                    if (code != 200) {
                        if (!hasRetriedAlternateType) {
                            int alternateType = getAlternateRecommendVideoType(requestedType);
                            Log.w(TAG, COVER_DEBUG_PREFIX + " detail enrich non-200, retry alternate type"
                                    + " blogId=" + blogId
                                    + ", fromType=" + requestedType
                                    + ", toType=" + alternateType
                                    + ", code=" + code
                                    + ", message=" + root.optString("message"));
                            requestRecommendVideoDetail(blogId, alternateType, true, authToken);
                            return;
                        }
                        Log.e(TAG, COVER_DEBUG_PREFIX + " detail enrich non-200"
                                + " blogId=" + blogId
                                + ", type=" + requestedType
                                + ", code=" + code
                                + ", message=" + root.optString("message"));
                        return;
                    }

                    JSONObject data = root.optJSONObject("data");
                    if (data == null) {
                        String dataStr = root.optString("data", "");
                        if (!dataStr.isEmpty() && !"null".equalsIgnoreCase(dataStr)) {
                            data = new JSONObject(dataStr);
                        }
                    }
                    if (data == null) {
                        if (!hasRetriedAlternateType) {
                            int alternateType = getAlternateRecommendVideoType(requestedType);
                            Log.w(TAG, COVER_DEBUG_PREFIX + " detail enrich data null, retry alternate type"
                                    + " blogId=" + blogId
                                    + ", fromType=" + requestedType
                                    + ", toType=" + alternateType);
                            requestRecommendVideoDetail(blogId, alternateType, true, authToken);
                            return;
                        }
                        Log.e(TAG, COVER_DEBUG_PREFIX + " detail enrich data null"
                                + " blogId=" + blogId
                                + ", type=" + requestedType);
                        return;
                    }

                    JSONObject nestedBlogObj = data.optJSONObject("blog");
                    String detailTitle = firstNonEmpty(
                            optString(data, "title"),
                            optString(nestedBlogObj, "title"),
                            optString(data, "blogTitle"),
                            optString(nestedBlogObj, "blogTitle")
                    );
                    String detailCoverUrl = extractCoverUrl(data, nestedBlogObj);
                    String detailVideoUrl = extractVideoUrl(data, nestedBlogObj);
                    String detailUserName = parseUserName(data, nestedBlogObj);
                    String detailUserId = parseUserId(data, nestedBlogObj);
                    int detailLikeNumber = parseLikeNumber(data, nestedBlogObj);

                    Log.d(TAG, COVER_DEBUG_PREFIX + " detail enrich parsed"
                            + " blogId=" + blogId
                            + ", type=" + requestedType
                            + ", title=" + detailTitle
                            + ", coverUrl=" + detailCoverUrl
                            + ", videoUrl=" + detailVideoUrl
                            + ", userId=" + detailUserId);

                    if (detailVideoUrl.isEmpty() && !hasRetriedAlternateType) {
                        int alternateType = getAlternateRecommendVideoType(requestedType);
                        Log.w(TAG, COVER_DEBUG_PREFIX + " detail enrich videoUrl empty, retry alternate type"
                                + " blogId=" + blogId
                                + ", fromType=" + requestedType
                                + ", toType=" + alternateType
                                + ", coverUrl=" + detailCoverUrl);
                        requestRecommendVideoDetail(blogId, alternateType, true, authToken);
                        return;
                    }

                    final String finalDetailTitle = detailTitle;
                    final String finalDetailCoverUrl = detailCoverUrl;
                    final String finalDetailVideoUrl = detailVideoUrl;
                    final String finalDetailUserName = detailUserName;
                    final String finalDetailUserId = detailUserId;
                    final int finalDetailLikeNumber = detailLikeNumber;
                    runOnUiThreadSafe(() -> mergeRecommendVideoDetailIntoList(
                            blogId,
                            finalDetailTitle,
                            finalDetailCoverUrl,
                            finalDetailVideoUrl,
                            finalDetailUserName,
                            finalDetailUserId,
                            finalDetailLikeNumber,
                            requestedType
                    ));
                } catch (Exception e) {
                    if (!hasRetriedAlternateType) {
                        int alternateType = getAlternateRecommendVideoType(requestedType);
                        Log.w(TAG, COVER_DEBUG_PREFIX + " detail enrich parse failed, retry alternate type"
                                + " blogId=" + blogId
                                + ", fromType=" + requestedType
                                + ", toType=" + alternateType
                                + ", error=" + e.getMessage());
                        requestRecommendVideoDetail(blogId, alternateType, true, authToken);
                        return;
                    }
                    Log.e(TAG, COVER_DEBUG_PREFIX + " detail enrich parse failed"
                            + " blogId=" + blogId
                            + ", type=" + requestedType
                            + ", body=" + body, e);
                }
            }
        });
    }

    private void mergeRecommendVideoDetailIntoList(@NonNull String blogId,
                                                   @Nullable String title,
                                                   @Nullable String coverUrl,
                                                   @Nullable String videoUrl,
                                                   @Nullable String userName,
                                                   @Nullable String userId,
                                                   int likeNumber,
                                                   int resolvedType) {
        for (int i = 0; i < videoList.size(); i++) {
            BlogCacheBean item = videoList.get(i);
            if (item == null || !blogId.equals(item.getBlogId())) {
                continue;
            }

            boolean changed = false;
            String finalTitle = firstNonEmpty(title);
            String finalCoverUrl = firstNonEmpty(coverUrl);
            String finalVideoUrl = firstNonEmpty(videoUrl);
            String finalUserName = firstNonEmpty(userName);
            String finalUserId = firstNonEmpty(userId);

            if (!finalTitle.isEmpty() && !finalTitle.equals(item.getTitle())) {
                item.setTitle(finalTitle);
                changed = true;
            }
            if (!finalCoverUrl.isEmpty() && !finalCoverUrl.equals(item.getImageUrl())) {
                item.setImageUrl(finalCoverUrl);
                changed = true;
            }
            if (!finalVideoUrl.isEmpty() && !finalVideoUrl.equals(item.getVideoUrl())) {
                item.setVideoUrl(finalVideoUrl);
                changed = true;
            }
            if (!finalUserName.isEmpty() && !finalUserName.equals(item.getUserName())) {
                item.setUserName(finalUserName);
                changed = true;
            }
            if (!finalUserId.isEmpty() && !finalUserId.equals(item.getUserId())) {
                item.setUserId(finalUserId);
                changed = true;
            }
            if (likeNumber >= 0 && likeNumber != item.getLikeNumber()) {
                item.setLikeNumber(likeNumber);
                changed = true;
            }
            if (resolvedType != item.getType()) {
                item.setType(resolvedType);
                changed = true;
            }

            Log.d(TAG, COVER_DEBUG_PREFIX + " detail merge"
                    + " blogId=" + blogId
                    + ", changed=" + changed
                    + ", coverUrl=" + item.getImageUrl()
                    + ", videoUrl=" + item.getVideoUrl()
                    + ", type=" + item.getType());
            if (changed && videoAdapter != null) {
                videoAdapter.notifyItemChanged(i);
            }
            return;
        }
        Log.w(TAG, COVER_DEBUG_PREFIX + " detail merge skipped because blogId is no longer in list"
                + " blogId=" + blogId);
    }

    // -----------------------------------------------------------------------
    // 视频列表（两列瀑布流，type == 0）
    // -----------------------------------------------------------------------

    private void setupVideoList() {
        videoAdapter = new RecommendVideoAdapter(videoList);
        StaggeredGridLayoutManager lm =
                new StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL);
        lm.setGapStrategy(StaggeredGridLayoutManager.GAP_HANDLING_MOVE_ITEMS_BETWEEN_SPANS);
        videoRecyclerView.setLayoutManager(lm);
        videoRecyclerView.setAdapter(videoAdapter);
        videoRecyclerView.setNestedScrollingEnabled(false);
    }

    private void loadVideoData() {
        if (!isNetworkAvailable()) {
            Log.w(TAG, "无网络，无法加载视频推荐");
            return;
        }

        String token = TokenManager.getToken(getContext());
        String authToken = token == null ? "" : token.trim();
        Request request;
        try {
            request = new Request.Builder()
                    .url(HttpUrl.parse(ApiConfig.BASE_URL + "blog/listByTypeId").newBuilder()
                            .addQueryParameter("type", "0")
                            .addQueryParameter("pageNum", "1")
                            .addQueryParameter("pageSize", "10")
                            .build())
                    .addHeader("Authorization", "Bearer " + authToken)
                    .get()
                    .build();
        } catch (Exception e) {
            Log.e(TAG, "构建视频请求失败: " + e.getMessage());
            return;
        }

        Log.e(TAG, ">>> 实际请求的完整 URL: " + request.url().toString());
        okHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "视频推荐请求失败: " + e.getMessage());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (isDestroyed || response.body() == null) return;

                try {
                    String responseStr = response.body().string();
                    Log.d(TAG, "视频推荐响应: " + responseStr);

                    JSONObject root = new JSONObject(responseStr);
                    if (root.optInt("code", -1) != 200) {
                        Log.e(TAG, "视频接口非200: " + root.optString("message"));
                        return;
                    }
                    JSONArray dataArray = extractListArray(root);
                    if (dataArray == null) {
                        Log.e(TAG, "data 字段为空或非数组");
                        return;
                    }

                    float density = getContext() != null
                            ? getContext().getResources().getDisplayMetrics().density : 3f;

                    List<BlogCacheBean> videosOnly = new ArrayList<>();
                    for (int i = 0; i < dataArray.length(); i++) {
                        BlogCacheBean bean = parseRecommendVideoRow(dataArray.optJSONObject(i), density);
                        if (bean != null) {
                            videosOnly.add(bean);
                        }
                    }

                    Log.d(TAG, "过滤后视频数量: " + videosOnly.size() + " / " + dataArray.length());

                    runOnUiThreadSafe(() -> {
                        videoList.clear();
                        videoList.addAll(videosOnly);
                        videoAdapter.notifyDataSetChanged();
                    });
                    enrichRecommendVideoDetails(videosOnly, authToken);

                } catch (Exception e) {
                    Log.e(TAG, "视频推荐数据解析失败: " + e.getMessage());
                }
            }
        });
    }

    // -----------------------------------------------------------------------
    // 点击监听 & 工具方法
    // -----------------------------------------------------------------------

    private void updateStatusCard() {
        if (statusGreeting == null || statusDescription == null || getContext() == null) {
            return;
        }
        String displayName = firstNonEmpty(currentUserName, getLocalUserNameFallback());
        statusGreeting.setText("Hi~ " + displayName);
        statusDescription.setText(buildCycleDescription());
    }

    private void loadCurrentUserName() {
        Context ctx = getContext();
        if (ctx == null || okHttpClient == null) {
            return;
        }
        String token = TokenManager.getToken(ctx);
        if (TextUtils.isEmpty(token)) {
            currentUserName = "";
            updateStatusCard();
            return;
        }

        Request request = new Request.Builder()
                .url(ApiConfig.API_USER_GET_INFO)
                .addHeader("Authorization", "Bearer " + token)
                .get()
                .build();
        okHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.w(TAG, "loadCurrentUserName failed", e);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                String body = response.body() != null ? response.body().string() : "";
                if (!response.isSuccessful()) {
                    Log.w(TAG, "loadCurrentUserName HTTP=" + response.code());
                    return;
                }
                String name = parseUserNameFromProfile(body);
                if (!TextUtils.isEmpty(name)) {
                    runOnUiThreadSafe(() -> {
                        currentUserName = name;
                        updateStatusCard();
                    });
                }
            }
        });
    }

    private void loadTodayMode() {
        Context ctx = getContext();
        if (ctx == null || TextUtils.isEmpty(TokenManager.getToken(ctx))) {
            return;
        }
        String today = LocalDate.now().toString();
        HealthRecordApiService.getDailyDetail(ctx, today,
                new HealthRecordApiService.Callback<ApiResponse<List<HealthRecordEntity>>>() {
                    @Override
                    public void onSuccess(ApiResponse<List<HealthRecordEntity>> result) {
                        if (result == null || result.getCode() != 200 || result.getData() == null) {
                            return;
                        }
                        int modeType = resolveModeType(result.getData());
                        runOnUiThreadSafe(() -> {
                            currentModeType = modeType;
                            updateStatusCard();
                        });
                    }

                    @Override
                    public void onError(String message) {
                        Log.w(TAG, "loadTodayMode failed: " + message);
                    }
                });
    }

    private void syncLatestCycleThenRefresh() {
        Context ctx = getContext();
        if (ctx == null || TextUtils.isEmpty(TokenManager.getToken(ctx))) {
            return;
        }
        new Thread(() -> {
            try {
                CycleApiService.LatestCycle latest = CycleApiService.getLatestCycleSync(ctx);
                if (latest != null && latest.startDate != null) {
                    CycleDataManager.saveLastPeriodStart(ctx, latest.startDate);
                    if (latest.durationDays != null && latest.durationDays > 0) {
                        CycleDataManager.saveSettings(
                                ctx,
                                latest.durationDays,
                                latest.cycleLength != null && latest.cycleLength > 0
                                        ? latest.cycleLength
                                        : CycleDataManager.getCycleDays(ctx),
                                CycleDataManager.isIrregular(ctx)
                        );
                    } else if (latest.cycleLength != null && latest.cycleLength > 0) {
                        CycleDataManager.saveSettings(
                                ctx,
                                CycleDataManager.getPeriodDays(ctx),
                                latest.cycleLength,
                                CycleDataManager.isIrregular(ctx)
                        );
                    }
                    LocalDate today = LocalDate.now();
                    LocalDate end = latest.displayEndDate != null ? latest.displayEndDate : latest.endDate;
                    boolean visible = end == null
                            ? !today.isBefore(latest.startDate)
                            : (!today.isBefore(latest.startDate) && !today.isAfter(end));
                    CycleDataManager.setActualPeriodVisible(ctx, visible);
                    runOnUiThreadSafe(this::updateStatusCard);
                }
            } catch (Exception e) {
                Log.w(TAG, "syncLatestCycleThenRefresh failed", e);
            }
        }, "RecommendCycleSync").start();
    }

    private String buildCycleDescription() {
        Context ctx = getContext();
        if (ctx == null) {
            return modeLabel() + "\u6a21\u5f0f\uff0c\u5c1a\u672a\u83b7\u53d6\u751f\u7406\u5468\u671f\u4fe1\u606f";
        }
        LocalDate lastStart = CycleDataManager.getLastPeriodStart(ctx);
        if (lastStart == null) {
            return "\u76ee\u524d\u662f" + modeLabel() + "\u6a21\u5f0f\uff0c\u8bf7\u5728\u5065\u5eb7\u6a21\u5757\u8bb0\u5f55\u6700\u8fd1\u4e00\u6b21\u7ecf\u671f";
        }

        int periodDays = Math.max(1, CycleDataManager.getPeriodDays(ctx));
        int cycleDays = Math.max(periodDays + 10, CycleDataManager.getCycleDays(ctx));
        LocalDate today = LocalDate.now();
        long diff = ChronoUnit.DAYS.between(lastStart, today);
        if (diff < 0) {
            return "\u76ee\u524d\u662f" + modeLabel() + "\u6a21\u5f0f\uff0c\u8bf7\u786e\u8ba4\u6700\u8fd1\u4e00\u6b21\u7ecf\u671f\u65e5\u671f";
        }

        int dayOfCycle = (int) (diff % cycleDays);
        int cycleDay = dayOfCycle + 1;
        PhaseInfo phase = resolveCyclePhase(ctx, today, lastStart, periodDays, cycleDays, dayOfCycle, cycleDay);
        return "\u76ee\u524d\u662f" + modeLabel() + "\u6a21\u5f0f\uff0c\u4eca\u5929\u662f"
                + phase.name + "\u7b2c" + phase.day + "\u5929"
                + "\uff08\u5468\u671f\u7b2c" + cycleDay + "\u5929\uff09";
    }

    private PhaseInfo resolveCyclePhase(Context ctx,
                                        LocalDate today,
                                        LocalDate lastStart,
                                        int periodDays,
                                        int cycleDays,
                                        int dayOfCycle,
                                        int cycleDay) {
        long sinceLastStart = ChronoUnit.DAYS.between(lastStart, today);
        if (CycleDataManager.isActualPeriodVisible(ctx) && sinceLastStart >= 0 && sinceLastStart < periodDays) {
            return new PhaseInfo("\u6708\u7ecf\u671f", (int) sinceLastStart + 1);
        }

        int dayType = MenstrualCalculator.getDayType(today, lastStart, periodDays, cycleDays);
        if (dayType == MenstrualCalculator.PREDICT_PERIOD || dayOfCycle < periodDays) {
            return new PhaseInfo("\u6708\u7ecf\u671f", dayOfCycle + 1);
        }

        int ovulationDay = Math.max(periodDays + 1, cycleDays - 14);
        int ovulationStart = Math.max(periodDays, ovulationDay - 5);
        int ovulationEnd = Math.min(cycleDays - 1, ovulationDay + 4);
        if (dayType == MenstrualCalculator.OVULATION_DAY
                || dayType == MenstrualCalculator.OVULATION_WINDOW
                || (dayOfCycle >= ovulationStart && dayOfCycle <= ovulationEnd)) {
            return new PhaseInfo("\u6392\u5375\u671f", dayOfCycle - ovulationStart + 1);
        }
        if (dayOfCycle < ovulationStart) {
            return new PhaseInfo("\u5375\u6ce1\u671f", Math.max(1, cycleDay - periodDays));
        }
        return new PhaseInfo("\u9ec4\u4f53\u671f", dayOfCycle - ovulationEnd + 1);
    }

    private int resolveModeType(List<HealthRecordEntity> records) {
        int fallback = currentModeType == 2 || currentModeType == 3 ? currentModeType : 1;
        if (records == null || records.isEmpty()) {
            return fallback;
        }
        int latest = fallback;
        for (HealthRecordEntity record : records) {
            if (record == null) {
                continue;
            }
            int modeType = record.getModeType();
            if (modeType == 1 || modeType == 2 || modeType == 3) {
                latest = modeType;
            }
        }
        return latest;
    }

    private String modeLabel() {
        if (currentModeType == 2) {
            return "\u5907\u5b55";
        }
        if (currentModeType == 3) {
            return "\u6000\u5b55";
        }
        return "\u7ecf\u671f";
    }

    private String parseUserNameFromProfile(String body) {
        try {
            JSONObject root = new JSONObject(body);
            JSONObject data = root.optJSONObject("data");
            JSONObject target = data != null ? data : root;
            return firstNonEmpty(
                    target.optString("nickName", ""),
                    target.optString("nickname", ""),
                    target.optString("userName", ""),
                    target.optString("name", "")
            );
        } catch (Exception e) {
            Log.w(TAG, "parseUserNameFromProfile failed", e);
            return "";
        }
    }

    private String getLocalUserNameFallback() {
        Context ctx = getContext();
        if (ctx == null) {
            return "\u7528\u6237";
        }
        UserDao userDao = new UserDao(ctx);
        try {
            userDao.open();
            String[] user = userDao.getCurrentLoginUser();
            if (user != null && user.length >= 2 && !TextUtils.isEmpty(user[1])) {
                String phone = user[1].trim();
                return "\u7528\u6237" + (phone.length() >= 4 ? phone.substring(phone.length() - 4) : phone);
            }
        } catch (Exception e) {
            Log.w(TAG, "getLocalUserNameFallback failed", e);
        } finally {
            userDao.close();
        }
        return "\u7528\u6237";
    }

    private static class PhaseInfo {
        final String name;
        final int day;

        PhaseInfo(String name, int day) {
            this.name = name;
            this.day = Math.max(1, day);
        }
    }

    private void setupClickListeners(View view) {
        View btnAnalysis = view.findViewById(R.id.knowledge_btn_analysis);
        if (btnAnalysis != null) {
            btnAnalysis.setOnClickListener(v ->
                    startActivity(new Intent(getActivity(), AnalysisReportActivity.class)));
        }
    }

    private void runOnUiThreadSafe(Runnable runnable) {
        if (isAdded() && getActivity() != null && !isDestroyed) {
            getActivity().runOnUiThread(runnable);
        }
    }

    private boolean isNetworkAvailable() {
        if (getContext() == null) return false;
        ConnectivityManager cm =
                (ConnectivityManager) getContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo active = cm.getActiveNetworkInfo();
        return active != null && active.isConnected();
    }

    private int dpToPx(int dp) {
        if (getContext() == null) return dp;
        return (int) (dp * getContext().getResources().getDisplayMetrics().density + 0.5f);
    }
}
