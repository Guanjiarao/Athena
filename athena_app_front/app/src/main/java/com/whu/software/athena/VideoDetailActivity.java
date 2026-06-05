package com.whu.software.athena;

import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.shuyu.gsyvideoplayer.GSYVideoManager;
import com.shuyu.gsyvideoplayer.video.StandardGSYVideoPlayer;
import com.whu.software.athena.config.ApiConfig;
import com.whu.software.athena.entity.CommentBean;
import com.whu.software.athena.entity.CommentResponse;
import com.whu.software.athena.entity.PublishCommentRequest;
import com.whu.software.athena.net.FollowRequestManager;
import com.whu.software.athena.utils.TokenManager;
import com.whu.software.athena.utils.UnsafeOkHttpClient;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.gson.Gson;
import android.content.Context;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CircleCrop;

public class VideoDetailActivity extends AppCompatActivity {

    private static final String TAG = "VideoDetailActivity";
    private static final int KNOWLEDGE_VIDEO_TYPE = 0;
    private static final int SQUARE_VIDEO_TYPE = 2;

    private ImageView  ivBack;
    private ImageView  ivUserAvatar;
    private TextView   tvUsername;
    private TextView   btnFollow;
    private TextView   tvVideoTitle;
    private TextView   tvLikeCount;
    private TextView   tvCommentCount;
    private TextView   tvCollectCount;
    private LinearLayout layoutLike;
    private LinearLayout layoutComment;
    private LinearLayout layoutCollect;
    private LinearLayout layoutShare;
    private StandardGSYVideoPlayer videoPlayer;

    // 点赞收藏相关
    private ImageView ivLikeIcon;
    private ImageView ivCollectIcon;
    private boolean isLiked = false;
    private boolean isCollected = false;
    private boolean isFollowed = false;
    private FollowRequestManager followRequestManager;

    // 评论相关
    private OkHttpClient commentHttpClient;
    private CommentAdapter commentAdapter;
    private RecyclerView rvComments;
    private EditText etCommentInput;
    private TextView btnSendComment;
    private CommentBean currentReplyTarget = null;
    private static final String TAG_COMMENT = "VideoComment";

    // 从 Intent 中解出的字段（作为首屏立即显示的初始数据）
    public String blogId;
    private Long userId;
    private String title;
    private String userName;
    private int    likeNumber;
    private String videoUrl;
    private int contentType = SQUARE_VIDEO_TYPE;
    private boolean hasRetriedDetailType = false;
    private boolean isFromCollectionList = false;
    private boolean isFromLikeList = false;

    private OkHttpClient okHttpClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        lockToCurrentOrientation();
        setupFullScreen();
        setContentView(R.layout.activity_video_detail);

        // 接收广场 Adapter 传来的各字段
        blogId     = getIntent().getStringExtra("blog_id");
        title      = getIntent().getStringExtra("title");
        userName   = getIntent().getStringExtra("user_name");
        likeNumber = getIntent().getIntExtra("like_number", 0);
        videoUrl   = getIntent().getStringExtra("video_url");
        contentType = getIntent().getIntExtra("content_type", SQUARE_VIDEO_TYPE);
        // 接收来自个人页面的状态信息
        isFromCollectionList = getIntent().getBooleanExtra("is_collected", false);
        isFromLikeList = getIntent().getBooleanExtra("is_liked", false);

        // 去除videoUrl中的反引号和空格
        if (videoUrl != null) {
            videoUrl = videoUrl.replace("`", "").trim();
        }

        Log.d(TAG, "onCreate: blogId=" + blogId);
        Log.d(TAG, "onCreate: title=" + title);
        Log.d(TAG, "onCreate: userName=" + userName);
        Log.d(TAG, "onCreate: likeNumber=" + likeNumber);
        Log.d(TAG, "onCreate: videoUrl=" + videoUrl);
        Log.d(TAG, "onCreate: contentType=" + contentType);

        okHttpClient = new OkHttpClient.Builder()
                .connectTimeout(ApiConfig.CONNECT_TIMEOUT, TimeUnit.SECONDS)
                .readTimeout(ApiConfig.READ_TIMEOUT, TimeUnit.SECONDS)
                .writeTimeout(ApiConfig.WRITE_TIMEOUT, TimeUnit.SECONDS)
                .build();

        followRequestManager = FollowRequestManager.getInstance(this);
        commentHttpClient = UnsafeOkHttpClient.getUnsafeOkHttpClient();

        initViews();
        setupClickListeners();
        loadLikeAndCollectStatus();

        // 检查网络连接
        if (!isNetworkAvailable()) {
            Log.w(TAG, "onCreate: 网络连接不可用");
            Toast.makeText(this, "网络连接不可用，视频可能无法播放", Toast.LENGTH_SHORT).show();
        }

        // 先用 Intent 数据首屏渲染，再异步拉取真实详情
        renderInitialData();
        fetchBlogDetailCompat();
    }

    /**
     * Some real tablets report portrait and reverse-portrait differently from phones.
     * Locking the activity to the current visible orientation prevents the detail page
     * from flipping upside down when the player activity starts.
     */
    private void lockToCurrentOrientation() {
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);
    }

    /** 检查网络连接状态 */
    private boolean isNetworkAvailable() {
        android.net.ConnectivityManager cm = (android.net.ConnectivityManager) getSystemService(android.content.Context.CONNECTIVITY_SERVICE);
        android.net.NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        return activeNetwork != null && activeNetwork.isConnected();
    }

    private void setupFullScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window window = getWindow();
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(Color.TRANSPARENT);
            window.setNavigationBarColor(Color.BLACK);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                window.getDecorView().setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                );
            }
        }
    }

    private void initViews() {
        ivBack         = findViewById(R.id.iv_back);
        ivUserAvatar   = findViewById(R.id.iv_user_avatar);
        tvUsername     = findViewById(R.id.tv_username);
        tvVideoTitle   = findViewById(R.id.tv_video_title);
        tvLikeCount    = findViewById(R.id.tv_like_count);
        tvCommentCount = findViewById(R.id.tv_comment_count);
        tvCollectCount = findViewById(R.id.tv_collect_count);
        layoutLike     = findViewById(R.id.layout_like);
        layoutComment  = findViewById(R.id.layout_comment);
        layoutCollect  = findViewById(R.id.layout_collect);
        layoutShare    = findViewById(R.id.layout_share);
        videoPlayer    = findViewById(R.id.video_player);
        btnFollow      = findViewById(R.id.btn_follow);

        // 获取点赞收藏图标
        ivLikeIcon = ((ImageView) ((LinearLayout) layoutLike).getChildAt(0));
        ivCollectIcon = ((ImageView) ((LinearLayout) layoutCollect).getChildAt(0));
    }

    private void setupClickListeners() {
        ivBack.setOnClickListener(v -> finish());

        layoutLike.setOnClickListener(v -> toggleLikeStatus());

        layoutComment.setOnClickListener(v -> showCommentBottomSheet());

        layoutCollect.setOnClickListener(v -> toggleCollectStatus());

        layoutShare.setOnClickListener(v ->
                Toast.makeText(this, "分享", Toast.LENGTH_SHORT).show());

        btnFollow.setOnClickListener(v -> toggleFollowStatus());
    }

    /** 用 Intent 传来的数据快速填充首屏，同时尝试播放已有的 videoUrl */
    private void renderInitialData() {
        tvVideoTitle.setText(title != null ? title : "视频加载中...");
        tvUsername.setText(userName != null ? userName : "");
        tvLikeCount.setText(formatCount(likeNumber));
        tvCommentCount.setText("0");
        tvCollectCount.setText("0");

        Log.d(TAG, "renderInitialData: videoUrl=" + videoUrl);
        if (videoUrl != null && !videoUrl.isEmpty()) {
            // 去除videoUrl中的反引号和空格
            String cleanedVideoUrl = videoUrl.replace("`", "").trim();
            Log.d(TAG, "renderInitialData: 清理后的videoUrl=" + cleanedVideoUrl);
            if (!cleanedVideoUrl.isEmpty()) {
                Log.d(TAG, "renderInitialData: 开始播放视频");
                playVideo(cleanedVideoUrl);
            } else {
                Log.w(TAG, "renderInitialData: 清理后videoUrl为空，无法播放视频");
            }
        } else {
            Log.w(TAG, "renderInitialData: videoUrl为空，无法播放视频");
        }
    }

    /** 向后端请求 /blog/Detail，获取完整数据后覆盖渲染 */
    private int getAlternateVideoType(int currentType) {
        return currentType == KNOWLEDGE_VIDEO_TYPE ? SQUARE_VIDEO_TYPE : KNOWLEDGE_VIDEO_TYPE;
    }

    private boolean retryFetchBlogDetailIfNeeded(String reason) {
        if (hasRetriedDetailType) {
            return false;
        }
        int alternateType = getAlternateVideoType(contentType);
        if (alternateType == contentType) {
            return false;
        }
        hasRetriedDetailType = true;
        Log.w(TAG, "fetchBlogDetail retry with alternate type, reason=" + reason
                + ", fromType=" + contentType + ", toType=" + alternateType);
        contentType = alternateType;
        fetchBlogDetailCompat();
        return true;
    }

    private void fetchBlogDetail() {
        if (blogId == null || blogId.isEmpty()) {
            Log.w(TAG, "fetchBlogDetail: blogId 为空，跳过请求");
            return;
        }

        String url = ApiConfig.API_BLOG_DETAIL
                + "?blog_id=" + blogId
                + "&type=" + contentType;
        Log.d(TAG, "fetchBlogDetail 请求: " + url);

        String token = TokenManager.getToken(this);
        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + token)
                .get()
                .build();

        okHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "fetchBlogDetail 网络请求失败: " + e.getMessage(), e);
                runOnUiThread(() ->
                        Toast.makeText(VideoDetailActivity.this, "加载失败，请检查网络", Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.body() == null) {
                    Log.e(TAG, "fetchBlogDetail 响应体为空");
                    runOnUiThread(() ->
                            Toast.makeText(VideoDetailActivity.this, "加载失败：服务器无返回", Toast.LENGTH_SHORT).show());
                    return;
                }

                String responseStr = response.body().string();
                Log.d(TAG, "fetchBlogDetail 响应: " + responseStr);

                try {
                    JSONObject root = new JSONObject(responseStr);
                    int code = root.optInt("code", -1);
                    if (code != 200) {
                        String msg = root.optString("message", "未知错误");
                        Log.e(TAG, "fetchBlogDetail 接口返回非200: " + msg);
                        runOnUiThread(() ->
                                Toast.makeText(VideoDetailActivity.this, "加载失败：" + msg, Toast.LENGTH_SHORT).show());
                        return;
                    }

                    JSONObject data = root.optJSONObject("data");
                    if (data == null) {
                        // 兼容 data 为字符串的情况
                        String dataStr = root.optString("data", "");
                        if (!dataStr.isEmpty() && !dataStr.equals("null")) {
                            data = new JSONObject(dataStr);
                        }
                    }
                    if (data == null) {
                        Log.e(TAG, "fetchBlogDetail data 字段为空");
                        return;
                    }

                    final String detailTitle = data.optString("title", "");
                    final String detailContent = data.optString("content", "");
                    final int    likeTotal     = data.optInt("likeTotal", likeNumber);
                    final int    commentTotal  = data.optInt("commentTotal", 0);
                    final int    collectTotal  = data.optInt("collectTotal", 0);
                    String tempVideoUrl = data.optString("videoUrl", videoUrl != null ? videoUrl : "");
                    // 去除videoUrl中的反引号和空格
                    final String detailVideoUrl = tempVideoUrl.replace("`", "").trim();
                    final Long detailUserId = data.optLong("userId", -1L);


                    // 🚨 专门针对你们后端 userDTO 结构的精准解析：

                    String parsedAvatar = "";
                    JSONObject userDTO = data.optJSONObject("userDTO");
                    if (userDTO != null) {
                        parsedAvatar = userDTO.optString("icon", ""); // 把藏在 userDTO 里的头像抠出来！
                    }
                    final String finalAvatarUrl = parsedAvatar;



                    Log.d(TAG, "fetchBlogDetail 解析成功: title=" + detailTitle
                            + ", likeTotal=" + likeTotal
                            + ", commentTotal=" + commentTotal
                            + ", collectTotal=" + collectTotal
                            + ", videoUrl=" + detailVideoUrl
                            + ", userId=" + detailUserId);

                    runOnUiThread(() -> {
                        // 🚨【新增】：使用 Glide 把头像渲染到 ivUserAvatar 上
                        if (!finalAvatarUrl.isEmpty() && !isDestroyed()) {
                            Glide.with(VideoDetailActivity.this)
                                    .load(finalAvatarUrl)
                                    .transform(new CircleCrop()) // 变成圆形
                                    .placeholder(android.R.drawable.sym_def_app_icon) // 加载中占位图
                                    .error(android.R.drawable.sym_def_app_icon) // 加载失败兜底图
                                    .into(ivUserAvatar);
                        }
                        // 标题 + 简介拼接（若 content 非空则追加）
                        String displayText = detailTitle;
                        if (!detailContent.isEmpty()) {
                            displayText = detailTitle + "\n" + detailContent;
                        }
                        tvVideoTitle.setText(displayText);

                        tvLikeCount.setText(formatCount(likeTotal));
                        tvCommentCount.setText(formatCount(commentTotal));
                        tvCollectCount.setText(formatCount(collectTotal));

                        // 播放视频（只要接口返回的 url 不为空，就播放）
                        if (!detailVideoUrl.isEmpty()) {
                            VideoDetailActivity.this.videoUrl = detailVideoUrl;
                            playVideo(VideoDetailActivity.this.videoUrl);
                        } else {
                            Log.w(TAG, "fetchBlogDetail: 解析后videoUrl为空，无法播放视频");
                        }

                        // 加载关注状态
                        if (detailUserId != null && detailUserId != -1L) {
                            VideoDetailActivity.this.userId = detailUserId;
                            loadFollowStatus();
                        }
                    });

                } catch (Exception e) {
                    Log.e(TAG, "fetchBlogDetail 解析失败: " + e.getMessage(), e);
                    runOnUiThread(() ->
                            Toast.makeText(VideoDetailActivity.this, "数据解析失败", Toast.LENGTH_SHORT).show());
                }
            }
        });
    }

    /** 使用 GSYVideoPlayer 播放视频 */
    private void fetchBlogDetailCompat() {
        if (blogId == null || blogId.isEmpty()) {
            Log.w(TAG, "fetchBlogDetailCompat: blogId is empty, skip request");
            return;
        }

        String url = ApiConfig.API_BLOG_DETAIL
                + "?blog_id=" + blogId
                + "&type=" + contentType;
        Log.d(TAG, "fetchBlogDetailCompat request: " + url);

        String token = TokenManager.getToken(this);
        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + token)
                .get()
                .build();

        okHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "fetchBlogDetailCompat network failed: " + e.getMessage(), e);
                runOnUiThread(() ->
                        Toast.makeText(VideoDetailActivity.this, "加载失败，请检查网络", Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.body() == null) {
                    if (retryFetchBlogDetailIfNeeded("empty response body")) {
                        return;
                    }
                    Log.e(TAG, "fetchBlogDetailCompat response body is null");
                    runOnUiThread(() ->
                            Toast.makeText(VideoDetailActivity.this, "加载失败：服务器无返回", Toast.LENGTH_SHORT).show());
                    return;
                }

                String responseStr = response.body().string();
                Log.d(TAG, "fetchBlogDetailCompat response: " + responseStr);

                try {
                    JSONObject root = new JSONObject(responseStr);
                    int code = root.optInt("code", -1);
                    if (code != 200) {
                        String msg = root.optString("message", "unknown error");
                        if (retryFetchBlogDetailIfNeeded("detail api code=" + code + ", message=" + msg)) {
                            return;
                        }
                        Log.e(TAG, "fetchBlogDetailCompat API returned non-200: " + msg);
                        runOnUiThread(() ->
                                Toast.makeText(VideoDetailActivity.this, "加载失败：" + msg, Toast.LENGTH_SHORT).show());
                        return;
                    }

                    JSONObject data = root.optJSONObject("data");
                    if (data == null) {
                        String dataStr = root.optString("data", "");
                        if (!dataStr.isEmpty() && !"null".equals(dataStr)) {
                            data = new JSONObject(dataStr);
                        }
                    }
                    if (data == null) {
                        if (retryFetchBlogDetailIfNeeded("data is null")) {
                            return;
                        }
                        Log.e(TAG, "fetchBlogDetailCompat data is null");
                        return;
                    }

                    JSONObject userDTO = data.optJSONObject("userDTO");
                    String detailTitle = data.optString("title", title != null ? title : "");
                    String detailContent = data.optString("content", "");
                    int likeTotal = data.has("likeTotal")
                            ? data.optInt("likeTotal", likeNumber)
                            : data.optInt("liked", likeNumber);
                    int commentTotal = data.has("commentTotal")
                            ? data.optInt("commentTotal", 0)
                            : data.optInt("comments", 0);
                    int collectTotal = data.has("collectTotal")
                            ? data.optInt("collectTotal", 0)
                            : data.optInt("collectCount", 0);

                    String tempVideoUrl = data.optString("videoUrl", videoUrl != null ? videoUrl : "");
                    String detailVideoUrl = tempVideoUrl.replace("`", "").trim();

                    long detailUserId = data.optLong("userId", -1L);
                    if (detailUserId == -1L && userDTO != null) {
                        detailUserId = userDTO.optLong("userId", userDTO.optLong("id", -1L));
                    }

                    String detailUserName = data.optString("nickName",
                            data.optString("userName", userName != null ? userName : ""));
                    if (userDTO != null) {
                        String dtoName = userDTO.optString("nickName", detailUserName);
                        if (!dtoName.isEmpty()) {
                            detailUserName = dtoName;
                        }
                    }

                    String avatarUrl = "";
                    if (userDTO != null) {
                        avatarUrl = userDTO.optString("icon", "");
                    }

                    Log.d(TAG, "fetchBlogDetailCompat parsed: title=" + detailTitle
                            + ", type=" + contentType
                            + ", videoUrl=" + detailVideoUrl
                            + ", userId=" + detailUserId);

                    if (detailVideoUrl.isEmpty()
                            && retryFetchBlogDetailIfNeeded("detail video url is empty")) {
                        return;
                    }

                    final String finalDetailTitle = detailTitle;
                    final String finalDetailContent = detailContent;
                    final int finalLikeTotal = likeTotal;
                    final int finalCommentTotal = commentTotal;
                    final int finalCollectTotal = collectTotal;
                    final String finalDetailVideoUrl = detailVideoUrl;
                    final long finalDetailUserId = detailUserId;
                    final String finalDetailUserName = detailUserName;
                    final String finalAvatarUrl = avatarUrl;

                    runOnUiThread(() -> {
                        if (!finalAvatarUrl.isEmpty() && !isDestroyed()) {
                            Glide.with(VideoDetailActivity.this)
                                    .load(finalAvatarUrl)
                                    .transform(new CircleCrop())
                                    .placeholder(android.R.drawable.sym_def_app_icon)
                                    .error(android.R.drawable.sym_def_app_icon)
                                    .into(ivUserAvatar);
                        }

                        if (!finalDetailUserName.isEmpty()) {
                            userName = finalDetailUserName;
                            tvUsername.setText(finalDetailUserName);
                        }

                        if (!finalDetailTitle.isEmpty()) {
                            title = finalDetailTitle;
                        }
                        String displayText = finalDetailTitle;
                        if (!finalDetailContent.isEmpty()) {
                            displayText = finalDetailTitle + "\n" + finalDetailContent;
                        }
                        tvVideoTitle.setText(displayText);

                        tvLikeCount.setText(formatCount(finalLikeTotal));
                        tvCommentCount.setText(formatCount(finalCommentTotal));
                        tvCollectCount.setText(formatCount(finalCollectTotal));

                        if (!finalDetailVideoUrl.isEmpty()) {
                            videoUrl = finalDetailVideoUrl;
                            playVideo(videoUrl);
                        } else {
                            Log.w(TAG, "fetchBlogDetailCompat: parsed videoUrl is empty, cannot play");
                        }

                        if (finalDetailUserId != -1L) {
                            userId = finalDetailUserId;
                            loadFollowStatus();
                        }
                    });
                } catch (Exception e) {
                    if (retryFetchBlogDetailIfNeeded("parse exception: " + e.getMessage())) {
                        return;
                    }
                    Log.e(TAG, "fetchBlogDetailCompat parse failed: " + e.getMessage(), e);
                    runOnUiThread(() ->
                            Toast.makeText(VideoDetailActivity.this, "数据解析失败", Toast.LENGTH_SHORT).show());
                }
            }
        });
    }

    private void playVideo(String url) {
        Log.d(TAG, "playVideo: " + url);
        if (videoPlayer == null) {
            Log.e(TAG, "playVideo: videoPlayer 为 null");
            return;
        }
        videoPlayer.setUp(url, true, title != null ? title : "");

        // 1. 强制隐藏播放器自带的顶部标题文字（解决右上角出现的杂乱文字）
        if (videoPlayer.getTitleTextView() != null) {
            videoPlayer.getTitleTextView().setVisibility(View.GONE);
        }

        // 2. 强制隐藏播放器自带的返回键（因为我们在 XML 里自己写了更好看的返回图标）
        if (videoPlayer.getBackButton() != null) {
            videoPlayer.getBackButton().setVisibility(View.GONE);
        }

        videoPlayer.startPlayLogic();
        Log.d(TAG, "playVideo: GSYVideoPlayer 开始播放");
    }

    private String formatCount(int count) {
        if (count >= 10000) {
            return String.format("%.1fw", count / 10000.0);
        } else if (count >= 1000) {
            return String.format("%.1fk", count / 1000.0);
        }
        return String.valueOf(count);
    }

    @Override
    protected void onPause() {
        super.onPause();
        GSYVideoManager.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        GSYVideoManager.onResume();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        GSYVideoManager.releaseAllVideos();
        if (okHttpClient != null) {
            okHttpClient.dispatcher().cancelAll();
        }
    }



    // ─────────────────────────────────────────────────────────────
    // 评论功能
    // ─────────────────────────────────────────────────────────────

    private void showCommentBottomSheet() {
        // 创建并显示评论底部弹窗
        CommentBottomSheetFragment bottomSheet = new CommentBottomSheetFragment();
        bottomSheet.show(getSupportFragmentManager(), "CommentBottomSheet");
    }

    private void hideKeyboard(View view) {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    private void showKeyboard(View view) {
        view.requestFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 点赞收藏功能
    // ─────────────────────────────────────────────────────────────

    private void loadLikeAndCollectStatus() {
        if (blogId == null || blogId.isEmpty()) {
            return;
        }

        // 如果是从收藏列表进入，先设置为已收藏状态
        if (isFromCollectionList) {
            isCollected = true;
            updateCollectButtonUI();
        }

        // 如果是从点赞列表进入，先设置为已点赞状态
        if (isFromLikeList) {
            isLiked = true;
            updateLikeButtonUI();
        }

        // 然后请求服务器状态进行验证
        followRequestManager.requestBlogLikeStatus(blogId, new FollowRequestManager.LikeStatusCallback() {
            @Override
            public void onSuccess(boolean liked) {
                VideoDetailActivity.this.isLiked = liked;
                updateLikeButtonUI();
            }

            @Override
            public void onFailure(String errorMsg) {
                // 静默失败，不打扰用户
            }
        });

        followRequestManager.requestBlogCollectStatus(blogId, new FollowRequestManager.CollectStatusCallback() {
            @Override
            public void onSuccess(boolean collected) {
                VideoDetailActivity.this.isCollected = collected;
                updateCollectButtonUI();
            }

            @Override
            public void onFailure(String errorMsg) {
                // 静默失败，不打扰用户
            }
        });
    }

    private void loadFollowStatus() {
        if (userId == null || userId == -1) {
            return;
        }

        followRequestManager.requestUserFollowStatus(String.valueOf(userId), new FollowRequestManager.FollowStatusCallback() {
            @Override
            public void onSuccess(boolean followed) {
                VideoDetailActivity.this.isFollowed = followed;
                updateFollowButtonUI();
            }

            @Override
            public void onFailure(String errorMsg) {
                // 静默失败，不打扰用户
            }
        });
    }

    private void updateLikeButtonUI() {
        if (ivLikeIcon != null) {
            if (isLiked) {
                ivLikeIcon.setColorFilter(getResources().getColor(R.color.red, getTheme()));
                tvLikeCount.setTextColor(getResources().getColor(R.color.red, getTheme()));
            } else {
                ivLikeIcon.setColorFilter(getResources().getColor(R.color.white, getTheme()));
                tvLikeCount.setTextColor(getResources().getColor(R.color.white, getTheme()));
            }
        }
    }

    private void updateCollectButtonUI() {
        if (ivCollectIcon != null) {
            if (isCollected) {
                ivCollectIcon.setColorFilter(getResources().getColor(R.color.athena_pink, getTheme()));
                tvCollectCount.setTextColor(getResources().getColor(R.color.athena_pink, getTheme()));
            } else {
                ivCollectIcon.setColorFilter(getResources().getColor(R.color.white, getTheme()));
                tvCollectCount.setTextColor(getResources().getColor(R.color.white, getTheme()));
            }
        }
    }

    private void updateFollowButtonUI() {
        if (btnFollow != null) {
            if (isFollowed) {
                btnFollow.setText("已关注");
                btnFollow.setTextColor(getResources().getColor(R.color.text_secondary, getTheme()));
                btnFollow.setBackgroundResource(R.drawable.bg_followed_button);
            } else {
                btnFollow.setText("+ 关注");
                btnFollow.setTextColor(getResources().getColor(android.R.color.white, getTheme()));
                btnFollow.setBackgroundResource(R.drawable.bg_follow_button);
            }
        }
    }

    private void toggleFollowStatus() {
        if (userId == null || userId == -1) {
            Toast.makeText(this, "用户ID为空，无法操作关注", Toast.LENGTH_SHORT).show();
            return;
        }
        btnFollow.setEnabled(false);
        boolean targetFollowStatus = !isFollowed;

        followRequestManager.toggleUserFollow(String.valueOf(userId), targetFollowStatus, new FollowRequestManager.FollowToggleCallback() {
            @Override
            public void onSuccess(boolean isFollow) {
                isFollowed = isFollow;
                updateFollowButtonUI();
                Toast.makeText(VideoDetailActivity.this, isFollow ? "关注成功" : "取消关注成功", Toast.LENGTH_SHORT).show();
                btnFollow.setEnabled(true);
            }

            @Override
            public void onFailure(String errorMsg) {
                btnFollow.setEnabled(true);
                Toast.makeText(VideoDetailActivity.this, "操作失败：" + errorMsg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void toggleLikeStatus() {
        if (blogId == null || blogId.isEmpty()) {
            Toast.makeText(this, "博客ID为空，无法操作点赞", Toast.LENGTH_SHORT).show();
            return;
        }
        layoutLike.setEnabled(false);
        final boolean targetLikeStatus = !isLiked;

        // 前端实时更新：先计算新的点赞数并更新UI
        String currentLikeText = tvLikeCount.getText().toString();
        int currentLikeCount = 0;
        try {
            if (currentLikeText.contains("w")) {
                currentLikeCount = (int) (Double.parseDouble(currentLikeText.replace("w", "")) * 10000);
            } else if (currentLikeText.contains("k")) {
                currentLikeCount = (int) (Double.parseDouble(currentLikeText.replace("k", "")) * 1000);
            } else {
                currentLikeCount = Integer.parseInt(currentLikeText);
            }
        } catch (Exception e) {
            currentLikeCount = 0;
        }

        // 根据操作类型更新数量
        final int newLikeCount = targetLikeStatus ? currentLikeCount + 1 : Math.max(0, currentLikeCount - 1);
        final int finalCurrentLikeCount = currentLikeCount;

        // 立即更新UI
        isLiked = targetLikeStatus;
        updateLikeButtonUI();
        tvLikeCount.setText(formatCount(newLikeCount));

        followRequestManager.toggleBlogLike(blogId, targetLikeStatus, new FollowRequestManager.LikeToggleCallback() {
            @Override
            public void onSuccess(boolean isLike, int serverLikeCount) {
                Toast.makeText(VideoDetailActivity.this, isLike ? "点赞成功" : "取消点赞", Toast.LENGTH_SHORT).show();
                layoutLike.setEnabled(true);
            }

            @Override
            public void onFailure(String errorMsg) {
                // 操作失败时，恢复原来的状态
                isLiked = !targetLikeStatus;
                updateLikeButtonUI();
                tvLikeCount.setText(formatCount(finalCurrentLikeCount));

                layoutLike.setEnabled(true);
                Toast.makeText(VideoDetailActivity.this, "操作失败：" + errorMsg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void toggleCollectStatus() {
        if (blogId == null || blogId.isEmpty()) {
            Toast.makeText(this, "博客ID为空，无法操作收藏", Toast.LENGTH_SHORT).show();
            return;
        }
        layoutCollect.setEnabled(false);
        final boolean targetCollectStatus = !isCollected;

        // 前端实时更新：先计算新的收藏数并更新UI
        String currentCollectText = tvCollectCount.getText().toString();
        int currentCollectCount = 0;
        try {
            if (currentCollectText.contains("w")) {
                currentCollectCount = (int) (Double.parseDouble(currentCollectText.replace("w", "")) * 10000);
            } else if (currentCollectText.contains("k")) {
                currentCollectCount = (int) (Double.parseDouble(currentCollectText.replace("k", "")) * 1000);
            } else {
                currentCollectCount = Integer.parseInt(currentCollectText);
            }
        } catch (Exception e) {
            currentCollectCount = 0;
        }

        // 根据操作类型更新数量
        final int newCollectCount = targetCollectStatus ? currentCollectCount + 1 : Math.max(0, currentCollectCount - 1);
        final int finalCurrentCollectCount = currentCollectCount;

        // 立即更新UI
        isCollected = targetCollectStatus;
        updateCollectButtonUI();
        tvCollectCount.setText(formatCount(newCollectCount));

        followRequestManager.toggleBlogCollect(blogId, targetCollectStatus, new FollowRequestManager.CollectToggleCallback() {
            @Override
            public void onSuccess(boolean isCollect, int serverCollectCount) {
                Toast.makeText(VideoDetailActivity.this, isCollect ? "收藏成功" : "取消收藏", Toast.LENGTH_SHORT).show();
                layoutCollect.setEnabled(true);
            }

            @Override
            public void onFailure(String errorMsg) {
                // 操作失败时，恢复原来的状态
                isCollected = !targetCollectStatus;
                updateCollectButtonUI();
                tvCollectCount.setText(formatCount(finalCurrentCollectCount));

                layoutCollect.setEnabled(true);
                Toast.makeText(VideoDetailActivity.this, "操作失败：" + errorMsg, Toast.LENGTH_SHORT).show();
            }
        });
    }
}
