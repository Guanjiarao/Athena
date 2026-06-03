package com.whu.software.athena;

import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.bumptech.glide.Glide;
import com.google.gson.Gson;
import com.whu.software.athena.config.ApiConfig;
import com.whu.software.athena.entity.BlogEntity;
import com.whu.software.athena.entity.CommentBean;
import com.whu.software.athena.entity.CommentResponse;
import com.whu.software.athena.entity.PublishCommentRequest;
import com.whu.software.athena.net.FollowRequestManager;
import com.whu.software.athena.utils.TokenManager;
import com.whu.software.athena.utils.UnsafeOkHttpClient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.json.JSONObject;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class NoteDetailActivity extends AppCompatActivity {

    private ImageView ivBack;
    private ViewPager2 vpImagesDetail;
    private TextView tvImageIndicator;
    private TextView tvTitle;
    private ImageView ivUserAvatar;
    private TextView tvUsername;
    private TextView tvContent;
    private TextView tvPublishTime;
    private TextView tvLikeCount;
    private TextView tvCommentCount;
    private TextView tvCollectCount;
    private TextView tvLikeTotal;
    private TextView tvCollectTotal;
    private LinearLayout layoutLike;
    private LinearLayout layoutComment;
    private LinearLayout layoutCollect;
    private EditText etCommentInput;
    private TextView btnSendComment;
    private TextView btnFollow;
    private ImageView ivLikeIcon;
    private ImageView ivCollectIcon;

    private boolean isFollowed = false;
    private boolean isLiked = false;
    private boolean isCollected = false;

    /** null = 直接评论文章；非 null = 当前正在回复的评论对象 */
    private CommentBean currentReplyTarget = null;

    private FeedItem feedItem;
    private ImagePagerAdapter imageAdapter;
    private List<String> imageUrls = new ArrayList<>();
    private Long userId;
    private String blogId;

    private FollowRequestManager followRequestManager;

    // 评论列表
    private RecyclerView rvComments;
    private CommentAdapter commentAdapter;
    private OkHttpClient commentHttpClient;

    // 状态请求完成计数器
    private int statusRequestCount = 0;
    private static final int TOTAL_STATUS_REQUESTS = 3;
    
    // 浏览记录相关
    private long enterTime;
    private boolean reported = false;

    /** 多图轮播排查专用：adb logcat -s NOTE_DETAIL_IMG */
    private static final String TAG_NOTE_IMG = "NOTE_DETAIL_IMG";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupStatusBar();
        setContentView(R.layout.activity_note_detail);

        // 记录进入页面时间
        enterTime = System.currentTimeMillis();
        
        followRequestManager = FollowRequestManager.getInstance(this);
        feedItem = (FeedItem) getIntent().getSerializableExtra("feed_item");
        commentHttpClient = UnsafeOkHttpClient.getUnsafeOkHttpClient();

        initViews();
        setupClickListeners();
        // 先从Intent获取blogId和userId
        Intent intent = getIntent();
        blogId = intent.getStringExtra("blog_id");
        long userIdExtra = intent.getLongExtra("user_id", -1L);
        if (userIdExtra != -1L) {
            userId = userIdExtra;
        }
        // 确保blogId和userId都设置后再加载状态
        ArrayList<String> intentPhotosDbg = intent.getStringArrayListExtra("photo");
        Log.i(TAG_NOTE_IMG, "onCreate | blogId=" + blogId + " userId=" + userId
                + " cover_url=" + intent.getStringExtra("cover_url")
                + " photoExtra条数=" + (intentPhotosDbg != null ? intentPhotosDbg.size() : "null")
                + " feed_item=" + (feedItem != null));
        if (blogId != null && !blogId.isEmpty() && userId != null && userId != -1) {
            Log.i(TAG_NOTE_IMG, "onCreate | 分支=loadLikeAndFollowStatus→fetchBlogDetail（首张图依赖接口返回，未先调 loadData）");
            loadLikeAndFollowStatus();
        } else {
            Log.i(TAG_NOTE_IMG, "onCreate | 分支=loadData（Intent 占位 + 可能再 fetch）");
            loadData();
        }
        
        // 停留3秒后上报浏览记录
        new android.os.Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!reported && blogId != null && !blogId.isEmpty()) {
                    reportView(3);
                    reported = true;
                }
            }
        }, 3000);
        
        initCommentList();
        loadComments(1);
    }


    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            View focused = getCurrentFocus();
            if (focused instanceof EditText) {
                int[] location = new int[2];
                focused.getLocationOnScreen(location);
                float x = ev.getRawX();
                float y = ev.getRawY();
                boolean touchedOutside = x < location[0]
                        || x > location[0] + focused.getWidth()
                        || y < location[1]
                        || y > location[1] + focused.getHeight();

                if (touchedOutside) {
                    // 如果点击在底部整条评论栏内（包括发送按钮），不重置回复目标，
                    // 否则会在点击“发送”前把 currentReplyTarget 清空
                    View bottomBar = findViewById(R.id.layout_bottom_bar);
                    boolean inBottomBar = false;
                    if (bottomBar != null) {
                        int[] barLoc = new int[2];
                        bottomBar.getLocationOnScreen(barLoc);
                        float bx = barLoc[0];
                        float by = barLoc[1];
                        float br = bx + bottomBar.getWidth();
                        float bb = by + bottomBar.getHeight();
                        inBottomBar = (x >= bx && x <= br && y >= by && y <= bb);
                    }

                    Log.d(TAG_COMMENT, "dispatchTouchEvent: touchedOutsideEditText="
                            + true + ", inBottomBar=" + inBottomBar
                            + ", clear reply target=" + !inBottomBar);

                    focused.clearFocus();
                    hideKeyboard(focused);

                    if (!inBottomBar) {
                        // 只有点击编辑区域以外、且不在底部评论栏时，才认为是“退出回复模式”
                        currentReplyTarget = null;
                        etCommentInput.setHint("说点什么...");
                    }
                }
            }
        }
        return super.dispatchTouchEvent(ev);
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



    private void setupStatusBar() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window window = getWindow();
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(Color.TRANSPARENT);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                window.getDecorView().setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                                | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                );
            }
        }
    }


    private void initViews() {
        ivBack = findViewById(R.id.iv_back);
        vpImagesDetail = findViewById(R.id.vp_images_detail);
        tvImageIndicator = findViewById(R.id.tv_image_indicator);
        tvTitle = findViewById(R.id.tv_title);
        ivUserAvatar = findViewById(R.id.iv_user_avatar);
        tvUsername = findViewById(R.id.tv_username);
        tvContent = findViewById(R.id.tv_content);
        tvPublishTime = findViewById(R.id.tv_publish_time);
        tvLikeCount = findViewById(R.id.tv_like_count);
        tvCommentCount = findViewById(R.id.tv_comment_count);
        tvCollectCount = findViewById(R.id.tv_collect_count);
        tvLikeTotal = findViewById(R.id.tv_like_total);
        tvCollectTotal = findViewById(R.id.tv_collect_total);
        layoutLike = findViewById(R.id.layout_like);
        layoutComment = findViewById(R.id.layout_comment);
        layoutCollect = findViewById(R.id.layout_collect);
        etCommentInput = findViewById(R.id.et_comment_input);
        btnSendComment = findViewById(R.id.btn_send_comment);
        btnFollow = findViewById(R.id.btn_follow);
        ivLikeIcon = findViewById(R.id.iv_like);
        ivCollectIcon = findViewById(R.id.iv_collect);
        rvComments = findViewById(R.id.rv_comments);
    }

    // ─────────────────────────────────────────────────────────────
    // 评论列表
    // ─────────────────────────────────────────────────────────────
    private void initCommentList() {
        Log.d(TAG_COMMENT, "initCommentList");
        commentAdapter = new CommentAdapter();
        commentAdapter.setOnExpandClickListener(commentId -> {
            Log.d(TAG_COMMENT, "onExpandClick(flatten) commentId=" + commentId);
            loadMoreReplies(commentId);
        });
        commentAdapter.setOnCommentClickListener(clickedComment -> {
            String nick = clickedComment.getUserDTO() != null
                    ? clickedComment.getUserDTO().getNickName() : "";
            Log.d(TAG_COMMENT, "onCommentClickListener: commentId=" + clickedComment.getCommentId()
                    + ", nick=" + nick
                    + ", replyCommentId=" + clickedComment.getReplyCommentId()
                    + ", parentId=" + clickedComment.getParentId());
            currentReplyTarget = clickedComment;
            etCommentInput.setHint("回复 @" + nick + " :");
            showKeyboard(etCommentInput);
        });
        rvComments.setLayoutManager(new LinearLayoutManager(this));
        rvComments.setAdapter(commentAdapter);
        rvComments.setNestedScrollingEnabled(false);
    }

    private void loadComments(int pageNum) {
        if (blogId == null || blogId.isEmpty()) {
            Log.w(TAG_COMMENT, "loadComments: blogId 为空，跳过请求（等待详情接口回调后再由 fetchBlogDetailFromServer 触发）");
            return;
        }
        int id;
        try {
            id = Integer.parseInt(blogId);
        } catch (NumberFormatException e) {
            Log.e(TAG_COMMENT, "loadComments: blogId 无法转为 int: " + blogId);
            return;
        }

        String url = ApiConfig.API_COMMENT_LIST_PAGE
                + "?blogId=" + id
                + "&pageNum=" + pageNum
                + "&pageSize=10";
        Log.d(TAG_COMMENT, "loadComments url=" + url);

        Request request = new Request.Builder().url(url).get().build();
        commentHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG_COMMENT, "loadComments onFailure: " + e.getMessage(), e);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                Log.d(TAG_COMMENT, "loadComments onResponse httpCode=" + response.code());
                if (!response.isSuccessful() || response.body() == null) {
                    Log.w(TAG_COMMENT, "loadComments: unsuccessful or null body, code=" + response.code());
                    return;
                }
                String body = response.body().string();
                Log.d(TAG_COMMENT, "loadComments body=" + body);
                try {
                    CommentResponse result = new Gson().fromJson(body, CommentResponse.class);
                    if (result != null && result.getCode() == 200) {
                        List<CommentBean> data = result.getData();
                        Log.d(TAG_COMMENT, "loadComments parsed count=" + (data != null ? data.size() : 0));
                        List<CommentAdapter.RowItem> flattened = buildFlattenComments(data);
                        runOnUiThread(() -> commentAdapter.setData(flattened));
                    } else {
                        Log.w(TAG_COMMENT, "loadComments result null or code != 200，渲染空列表");
                        runOnUiThread(() -> commentAdapter.setData(new ArrayList<>()));
                    }
                } catch (Exception e) {
                    Log.e(TAG_COMMENT, "loadComments JSON parse error: " + e.getMessage(), e);
                    runOnUiThread(() -> commentAdapter.setData(new ArrayList<>()));
                }
            }
        });
    }

    /**
     * 展开某条主评论的剩余子评论，直接追加到同一 RecyclerView 中。
     */
    private void loadMoreReplies(long parentCommentId) {
        String url = ApiConfig.API_COMMENT_EXTEND
                + "?commentId=" + parentCommentId
                + "&pageNum=1&pageSize=50";
        Log.d(TAG_COMMENT, "loadMoreReplies url=" + url);

        Request request = new Request.Builder().url(url).get().build();
        commentHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG_COMMENT, "loadMoreReplies onFailure: " + e.getMessage(), e);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (!response.isSuccessful() || response.body() == null) {
                    Log.w(TAG_COMMENT, "loadMoreReplies: unsuccessful or null body, code=" + response.code());
                    return;
                }
                String body = response.body().string();
                Log.d(TAG_COMMENT, "loadMoreReplies body=" + body);
                try {
                    CommentResponse result = new Gson().fromJson(body, CommentResponse.class);
                    if (result != null && result.isSuccess()) {
                        List<CommentBean> data = result.getData();
                        if (data == null || data.isEmpty()) return;

                        runOnUiThread(() -> {
                            List<CommentAdapter.RowItem> items = commentAdapter.getItems();
                            if (items == null) return;

                            // 找到父评论和“展开更多”行的位置
                            int expandIndex = -1;
                            int insertIndex = -1;
                            for (int i = 0; i < items.size(); i++) {
                                CommentAdapter.RowItem row = items.get(i);
                                if (row.getType() == 0 && row.getComment() != null
                                        && row.getComment().getCommentId() == parentCommentId) {
                                    insertIndex = i + 1;
                                }
                                if (row.getType() == 1 && row.getParentCommentId() == parentCommentId) {
                                    expandIndex = i;
                                }
                            }

                            if (insertIndex == -1) return;

                            // 移除“展开更多”行（如果存在）
                            if (expandIndex >= 0 && expandIndex < items.size()) {
                                items.remove(expandIndex);
                                commentAdapter.notifyItemRemoved(expandIndex);
                                if (expandIndex < insertIndex) {
                                    insertIndex--;
                                }
                            }

                            // 将后端返回的子评论按顺序插入（全部作为二级评论）
                            int added = 0;
                            for (CommentBean reply : data) {
                                if (reply == null) continue;
                                CommentAdapter.RowItem rowItem =
                                        CommentAdapter.RowItem.comment(reply, true, parentCommentId);
                                items.add(insertIndex + added, rowItem);
                                commentAdapter.notifyItemInserted(insertIndex + added);
                                added++;
                            }
                        });
                    }
                } catch (Exception e) {
                    Log.e(TAG_COMMENT, "loadMoreReplies JSON parse error: " + e.getMessage(), e);
                }
            }
        });
    }

    /**
     * 将后端返回的主评论列表展平为：
     *   主评论
     *   第 1 条子评论（如果有）
     *   “展开另外 X 条回复 >” 行（如果还有更多）
     */
    private List<CommentAdapter.RowItem> buildFlattenComments(List<CommentBean> data) {
        List<CommentAdapter.RowItem> result = new ArrayList<>();
        if (data == null) return result;

        for (CommentBean parent : data) {
            if (parent == null) continue;
            long parentId = parent.getCommentId();
            // 主评论本身
            result.add(CommentAdapter.RowItem.comment(parent, false, parentId));

            // 第 1 条子评论（如果后端返回了 firstReplyComment）
            CommentBean firstReply = parent.getFirstReplyComment();
            if (firstReply != null) {
                result.add(CommentAdapter.RowItem.comment(firstReply, true, parentId));

                int remaining = parent.getReplyTotal() - 1;
                if (remaining > 0) {
                    // 展开另外 X 条回复 >
                    result.add(CommentAdapter.RowItem.expand(parentId, remaining));
                }
            }
        }
        return result;
    }

    private void setupClickListeners() {
        ivBack.setOnClickListener(v -> finish());

        /* 点赞 */
        layoutLike.setOnClickListener(v -> toggleLikeStatus());

        // 评论数按钮（点击后聚焦输入框）
        layoutComment.setOnClickListener(v -> showKeyboard(etCommentInput));

        // 收藏
        layoutCollect.setOnClickListener(v -> toggleCollectStatus());

        // 评论输入框：点击弹出软键盘
        etCommentInput.setOnClickListener(v -> showKeyboard(etCommentInput));

        // 点击发送（键盘 ActionSend）
        etCommentInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                submitComment();
                return true;
            }
            return false;
        });

        // 发送按钮点击
        btnSendComment.setOnClickListener(v -> submitComment());

        // 关注按钮
        btnFollow.setOnClickListener(v -> toggleFollowStatus());
    }

    // ─────────────────────────────────────────────────────────────
    // 收藏功能
    // ─────────────────────────────────────────────────────────────

    private void toggleCollectStatus() {
        if (blogId == null || blogId.isEmpty()) {
            Toast.makeText(this, "博客ID为空，无法操作收藏", Toast.LENGTH_SHORT).show();
            return;
        }
        layoutCollect.setEnabled(false);
        final boolean targetCollectStatus = !isCollected;

        // 前端实时更新：先计算新的收藏数并更新UI
        String currentCollectText = tvCollectTotal.getText().toString();
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
        tvCollectTotal.setText(formatCount(newCollectCount));

        followRequestManager.toggleBlogCollect(blogId, targetCollectStatus, new FollowRequestManager.CollectToggleCallback() {
            @Override
            public void onSuccess(boolean isCollect, int serverCollectCount) {
                // 服务器返回成功后，可以选择使用服务器返回的最终值更新UI
                // 这里我们已经在前端实时更新了，所以可以不做操作
                // 如果需要确保数据一致性，可以取消下面的注释
                /*
                if (serverCollectCount > 0) {
                    tvCollectCount.setText(formatCount(serverCollectCount));
                    tvCollectTotal.setText(formatCount(serverCollectCount));
                }
                */
                Toast.makeText(NoteDetailActivity.this, isCollect ? "收藏成功" : "取消收藏", Toast.LENGTH_SHORT).show();
                layoutCollect.setEnabled(true);
            }

            @Override
            public void onFailure(String errorMsg) {
                // 操作失败时，恢复原来的状态
                isCollected = !targetCollectStatus;
                updateCollectButtonUI();
                tvCollectCount.setText(formatCount(finalCurrentCollectCount));
                tvCollectTotal.setText(formatCount(finalCurrentCollectCount));

                layoutCollect.setEnabled(true);
                Toast.makeText(NoteDetailActivity.this, "操作失败：" + errorMsg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateCollectButtonUI() {
        ivCollectIcon.setSelected(isCollected);
        int textColor = isCollected
                ? getResources().getColor(R.color.athena_pink, getTheme())
                : getResources().getColor(R.color.text_secondary, getTheme());
        tvCollectCount.setTextColor(textColor);
    }

    // ─────────────────────────────────────────────────────────────
    // 评论提交
    // ─────────────────────────────────────────────────────────────
    private static final String TAG_COMMENT = "SubmitComment";

    private void submitComment() {
        String text = etCommentInput.getText().toString().trim();
        Log.d(TAG_COMMENT, "submitComment called, text=\"" + text + "\"");
        if (text.isEmpty()) {
            Toast.makeText(this, "评论不能为空", Toast.LENGTH_SHORT).show();
            return;
        }

        int id = 1;
        try {
            if (blogId != null && !blogId.isEmpty()) {
                id = Integer.parseInt(blogId);
            }
        } catch (NumberFormatException ignored) {}
        Log.d(TAG_COMMENT, "blogId=" + id);

        // ── 在发起请求前快照当前回复状态，避免回调时状态被重置 ─────────
        final CommentBean replyTargetSnapshot = currentReplyTarget;
        final String commentText = text;

        if (replyTargetSnapshot == null) {
            Log.d(TAG_COMMENT, "mode=主评论（直接评论文章）");
        } else {
            Log.d(TAG_COMMENT, "mode=子评论，replyCommentId=" + replyTargetSnapshot.getCommentId()
                    + ", replyUserId=" + (replyTargetSnapshot.getUserDTO() != null ? replyTargetSnapshot.getUserDTO().getId() : "null")
                    + ", replyUserName=" + (replyTargetSnapshot.getUserDTO() != null ? replyTargetSnapshot.getUserDTO().getNickName() : "null")
                    + ", parentId(raw)=" + replyTargetSnapshot.getParentId());
        }

        // ── 构造请求体 ────────────────────────────────────────────────
        PublishCommentRequest requestBody = new PublishCommentRequest();
        requestBody.setBlogId(id);
        requestBody.setContent(text);
        requestBody.setImageUrl("");

        if (replyTargetSnapshot != null) {
            long rId = replyTargetSnapshot.getCommentId();
            requestBody.setReplyCommentId(rId);
            requestBody.setReplyUserId(
                    replyTargetSnapshot.getUserDTO() != null
                            ? replyTargetSnapshot.getUserDTO().getId() : 0);
            requestBody.setReplyUserName(
                    replyTargetSnapshot.getUserDTO() != null
                            ? replyTargetSnapshot.getUserDTO().getNickName() : "");
            // parentId 规则：自身是主评论(parentId==0) → 以自身 id 为楼层根；否则继承
            long parent = replyTargetSnapshot.getParentId();
            requestBody.setParentId(parent == 0 ? rId : parent);
        } else {
            requestBody.setReplyCommentId(0);
            requestBody.setReplyUserId(0);
            requestBody.setReplyUserName("");
            requestBody.setParentId(0);
        }

        String json = new Gson().toJson(requestBody);
        Log.d(TAG_COMMENT, "request URL=" + ApiConfig.API_COMMENT_PUBLISH);
        Log.d(TAG_COMMENT, "request body=" + json);

        RequestBody body = RequestBody.create(
                MediaType.parse("application/json; charset=utf-8"), json);

        String token = TokenManager.getToken(this);
        Request request = new Request.Builder()
                .url(ApiConfig.API_COMMENT_PUBLISH)
                .addHeader("Authorization", "Bearer " + token)
                .post(body)
                .build();

        btnSendComment.setEnabled(false);

        commentHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG_COMMENT, "onFailure: " + e.getMessage(), e);
                runOnUiThread(() -> {
                    btnSendComment.setEnabled(true);
                    Toast.makeText(NoteDetailActivity.this, "网络异常，请重试", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                String responseBody = response.body() != null ? response.body().string() : "";
                Log.d(TAG_COMMENT, "onResponse httpCode=" + response.code() + ", body=" + responseBody);
                runOnUiThread(() -> {
                    btnSendComment.setEnabled(true);
                    try {
                        org.json.JSONObject jsonResp = new org.json.JSONObject(responseBody);
                        int code = jsonResp.optInt("code", -1);
                        Log.d(TAG_COMMENT, "business code=" + code);
                        if (code == 200) {
                            Toast.makeText(NoteDetailActivity.this, "发布成功", Toast.LENGTH_SHORT).show();

                            // ── 重置输入框 & 回复状态 ────────────────────────────
                            etCommentInput.setText("");
                            etCommentInput.setHint("说点什么...");
                            etCommentInput.clearFocus();
                            hideKeyboard(etCommentInput);
                            currentReplyTarget = null;

                            // ── 乐观局部插入（无需重新拉列表）──────────────────
                            long replyIdSnapshot = replyTargetSnapshot != null
                                    ? replyTargetSnapshot.getCommentId() : 0;
                            CommentBean newComment = CommentBean.createLocal(
                                    commentText, "我", replyIdSnapshot);
                            List<CommentBean> list = commentAdapter.getComments();
                            Log.d(TAG_COMMENT, "optimistic insert: replyIdSnapshot=" + replyIdSnapshot
                                    + ", listSize=" + list.size());

                            if (replyTargetSnapshot == null) {
                                // 主评论：插到列表最顶部
                                commentAdapter.insertAt(0, newComment);
                                rvComments.scrollToPosition(0);
                                Log.d(TAG_COMMENT, "inserted at top (主评论)");
                            } else {
                                // 子评论：找到父评论位置，紧接在其下方插入
                                int insertIndex = -1;
                                for (int i = 0; i < list.size(); i++) {
                                    if (list.get(i).getCommentId() == replyIdSnapshot) {
                                        insertIndex = i + 1;
                                        break;
                                    }
                                }
                                Log.d(TAG_COMMENT, "insertIndex=" + insertIndex);
                                if (insertIndex != -1) {
                                    commentAdapter.insertAt(insertIndex, newComment);
                                    rvComments.scrollToPosition(insertIndex);
                                } else {
                                    // 兜底：父评论不在列表中，插到顶部
                                    Log.w(TAG_COMMENT, "父评论未在列表中，兜底插到顶部");
                                    commentAdapter.insertAt(0, newComment);
                                    rvComments.scrollToPosition(0);
                                }
                            }
                        } else {
                            String msg = jsonResp.optString("message", "发布失败");
                            Log.w(TAG_COMMENT, "业务失败: " + msg);
                            Toast.makeText(NoteDetailActivity.this, msg, Toast.LENGTH_SHORT).show();
                        }
                    } catch (Exception e) {
                        Log.e(TAG_COMMENT, "解析响应异常: " + e.getMessage(), e);
                        Toast.makeText(NoteDetailActivity.this, "发布失败，请重试", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    // ─────────────────────────────────────────────────────────────
    // 点赞功能
    // ─────────────────────────────────────────────────────────────
    private void loadLikeAndFollowStatus() {
        if (blogId == null || blogId.isEmpty()) {
            return;
        }
        if (userId == null || userId == -1) {
            return;
        }

        // 重置计数器
        statusRequestCount = 0;

        followRequestManager.requestBlogLikeStatus(blogId, new FollowRequestManager.LikeStatusCallback() {
            @Override
            public void onSuccess(boolean liked) {
                NoteDetailActivity.this.isLiked = liked;
                updateLikeButtonUI();
                checkStatusRequestsComplete();
            }

            @Override
            public void onFailure(String errorMsg) {
                // 静默失败，不打扰用户
                checkStatusRequestsComplete();
            }
        });

        followRequestManager.requestUserFollowStatus(String.valueOf(userId), new FollowRequestManager.FollowStatusCallback() {
            @Override
            public void onSuccess(boolean followed) {
                NoteDetailActivity.this.isFollowed = followed;
                updateFollowButtonUI();
                checkStatusRequestsComplete();
            }

            @Override
            public void onFailure(String errorMsg) {
                // 静默失败
                checkStatusRequestsComplete();
            }
        });

        followRequestManager.requestBlogCollectStatus(blogId, new FollowRequestManager.CollectStatusCallback() {
            @Override
            public void onSuccess(boolean collected) {
                NoteDetailActivity.this.isCollected = collected;
                updateCollectButtonUI();
                checkStatusRequestsComplete();
            }

            @Override
            public void onFailure(String errorMsg) {
                // 静默失败，不打扰用户
                checkStatusRequestsComplete();
            }
        });
    }

    /**
     * 检查所有状态请求是否完成
     */
    private void checkStatusRequestsComplete() {
        statusRequestCount++;
        Log.d("DETAIL_DEBUG", "[状态请求] 完成请求数: " + statusRequestCount + "/" + TOTAL_STATUS_REQUESTS);
        if (statusRequestCount >= TOTAL_STATUS_REQUESTS) {
            Log.d("DETAIL_DEBUG", "[状态请求] 所有状态请求已完成，开始获取博客详情");
            // 所有状态请求完成后，获取博客详情
            fetchBlogDetailFromServer(blogId);
        }
    }

    private void updateLikeButtonUI() {
        if (isLiked) {
            ivLikeIcon.setColorFilter(getResources().getColor(R.color.red, getTheme()));
            tvLikeCount.setTextColor(getResources().getColor(R.color.red, getTheme()));
        } else {
            ivLikeIcon.setColorFilter(getResources().getColor(R.color.gray, getTheme()));
            tvLikeCount.setTextColor(getResources().getColor(R.color.gray, getTheme()));
        }
    }

    private void updateFollowButtonUI() {
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

    private void toggleLikeStatus() {
        if (blogId == null || blogId.isEmpty()) {
            Toast.makeText(this, "博客ID为空，无法操作点赞", Toast.LENGTH_SHORT).show();
            return;
        }
        layoutLike.setEnabled(false);
        final boolean targetLikeStatus = !isLiked;

        // 前端实时更新：先计算新的点赞数并更新UI
        String currentLikeText = tvLikeTotal.getText().toString();
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
        tvLikeTotal.setText(formatCount(newLikeCount));

        followRequestManager.toggleBlogLike(blogId, targetLikeStatus, new FollowRequestManager.LikeToggleCallback() {
            @Override
            public void onSuccess(boolean isLike, int serverLikeCount) {
                // 服务器返回成功后，可以选择使用服务器返回的最终值更新UI
                // 这里我们已经在前端实时更新了，所以可以不做操作
                // 如果需要确保数据一致性，可以取消下面的注释
                /*
                if (serverLikeCount > 0) {
                    tvLikeCount.setText(formatCount(serverLikeCount));
                    tvLikeTotal.setText(formatCount(serverLikeCount));
                }
                */
                Toast.makeText(NoteDetailActivity.this, isLike ? "点赞成功" : "取消点赞", Toast.LENGTH_SHORT).show();
                layoutLike.setEnabled(true);
            }

            @Override
            public void onFailure(String errorMsg) {
                // 操作失败时，恢复原来的状态
                isLiked = !targetLikeStatus;
                updateLikeButtonUI();
                tvLikeCount.setText(formatCount(finalCurrentLikeCount));
                tvLikeTotal.setText(formatCount(finalCurrentLikeCount));

                layoutLike.setEnabled(true);
                Toast.makeText(NoteDetailActivity.this, "操作失败：" + errorMsg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void toggleFollowStatus() {
        if (userId == null || userId == -1L) {
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
                Toast.makeText(NoteDetailActivity.this, isFollow ? "关注成功" : "取消关注成功", Toast.LENGTH_SHORT).show();
                btnFollow.setEnabled(true);
            }

            @Override
            public void onFailure(String errorMsg) {
                btnFollow.setEnabled(true);
                Toast.makeText(NoteDetailActivity.this, "操作失败：" + errorMsg, Toast.LENGTH_SHORT).show();
            }
        });
    }
    
    // 上报浏览记录
    private void reportView(int duration) {
        if (blogId == null || blogId.isEmpty()) return;
        
        try {
            // 获取本地Token并添加到请求头
            String token = TokenManager.getToken(this);
            okhttp3.HttpUrl.Builder urlBuilder = okhttp3.HttpUrl.parse(ApiConfig.BASE_URL + "blog/view").newBuilder();
            urlBuilder.addQueryParameter("noteId", blogId);
            urlBuilder.addQueryParameter("duration", String.valueOf(duration));
            
            Request request = new Request.Builder()
                    .url(urlBuilder.build())
                    .addHeader("Authorization", "Bearer " + token)
                    .post(RequestBody.create(new byte[0], MediaType.parse("application/json")))
                    .build();
            
            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .writeTimeout(15, TimeUnit.SECONDS)
                    .build();
            
            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    Log.d("VIEW_REPORT", "上报浏览记录失败: " + e.getMessage());
                }
                
                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    Log.d("VIEW_REPORT", "上报浏览记录成功: " + response.code());
                    if (response.body() != null) {
                        Log.d("VIEW_REPORT", "响应内容: " + response.body().string());
                    }
                }
            });
        } catch (Exception e) {
            Log.d("VIEW_REPORT", "上报浏览记录异常: " + e.getMessage());
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 离开页面时上报最终停留时长
        if (blogId != null && !blogId.isEmpty()) {
            int duration = (int) ((System.currentTimeMillis() - enterTime) / 1000);
            if (duration >= 3) {
                // 直接使用普通请求上报
                reportView(duration);
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 数据加载
    // ─────────────────────────────────────────────────────────────
    private void loadData() {
        Intent intent = getIntent();
        blogId = intent.getStringExtra("blog_id");
        String title = intent.getStringExtra("title");
        String content = intent.getStringExtra("content");
        String userName = intent.getStringExtra("user_name");
        int likeNumber = intent.getIntExtra("like_number", 0);
        int likeTotal = intent.getIntExtra("likeTotal", 0);
        int collectTotal = intent.getIntExtra("collectTotal", 0);
        String coverUrl = intent.getStringExtra("cover_url");
        ArrayList<String> photoList = intent.getStringArrayListExtra("photo");

        long userIdExtra = intent.getLongExtra("user_id", -1L);
        if (userIdExtra != -1L) {
            userId = userIdExtra;
        }

        Log.d("DETAIL_DEBUG", "───────────────────────────────────");
        Log.d("DETAIL_DEBUG", "[loadData] Intent 字段一览:");
        Log.d("DETAIL_DEBUG", "  blog_id   = \"" + blogId + "\"");
        Log.d("DETAIL_DEBUG", "  title     = \"" + title + "\"");
        Log.d("DETAIL_DEBUG", "  content   = \"" + (content != null ? content.substring(0, Math.min(30, content.length())) + "..." : "null") + "\"");
        Log.d("DETAIL_DEBUG", "  user_name = \"" + userName + "\"");
        Log.d("DETAIL_DEBUG", "  user_id   = " + userIdExtra);
        Log.d("DETAIL_DEBUG", "  cover_url = \"" + coverUrl + "\"");
        Log.d("DETAIL_DEBUG", "  photo[]   = " + (photoList != null ? photoList.size() + " 张" : "null"));
        Log.d("DETAIL_DEBUG", "  feedItem  = " + (feedItem != null ? "非null" : "null"));
        Log.i(TAG_NOTE_IMG, "loadData START | blogId=" + blogId + " title+content齐全=" + (title != null && content != null)
                + " cover_url=" + coverUrl + " photo条数=" + (photoList != null ? photoList.size() : "null"));

        if (title != null && content != null) {
            Log.d("DETAIL_DEBUG", "[loadData] 走分支①: Intent 携带了 title+content，直接渲染");
            tvTitle.setText(title);
            tvUsername.setText(userName != null ? userName : "未知用户");
            tvContent.setText(content);
            tvLikeCount.setText(formatCount(likeNumber));
            tvLikeTotal.setText(formatCount(likeTotal));
            tvCommentCount.setText("0");
            tvCollectCount.setText("0");
            tvCollectTotal.setText(formatCount(collectTotal));
            tvPublishTime.setText("刚刚");
            // 将封面图（coverUrl）放在图片列表最前面，再追加多图
            List<String> mergedPhotos = new ArrayList<>();
            if (coverUrl != null && !coverUrl.isEmpty()) {
                mergedPhotos.add(coverUrl);
                Log.d("DETAIL_DEBUG", "[loadData①] 添加封面图: \"" + coverUrl + "\"");
            } else {
                Log.w("DETAIL_DEBUG", "[loadData①] cover_url 为空，跳过封面图");
            }
            if (photoList != null && !photoList.isEmpty()) {
                for (String p : photoList) {
                    if (p != null && !p.isEmpty() && !p.equals(coverUrl)) {
                        mergedPhotos.add(p);
                        Log.d("DETAIL_DEBUG", "[loadData①] 追加多图: \"" + p + "\"");
                    }
                }
            } else {
                Log.w("DETAIL_DEBUG", "[loadData①] photo[] 为空或null，无多图追加");
            }
            if (mergedPhotos.isEmpty()) {
                Log.w("DETAIL_DEBUG", "[loadData①] ⚠️ 合并后图片列表为空，使用占位空串");
                imageUrls = Arrays.asList("");
            } else {
                Log.d("DETAIL_DEBUG", "[loadData①] 合并后图片列表共 " + mergedPhotos.size() + " 张");
                imageUrls = mergedPhotos;
            }
        } else if (feedItem != null && feedItem.imageUrls != null && !feedItem.imageUrls.isEmpty()) {
            Log.d("DETAIL_DEBUG", "[loadData] 走分支②: FeedItem 携带数据，直接渲染");
            Log.d("DETAIL_DEBUG", "[loadData②] FeedItem.imageUrls 共 " + feedItem.imageUrls.size() + " 张:");
            for (int i = 0; i < feedItem.imageUrls.size(); i++) {
                Log.d("DETAIL_DEBUG", "[loadData②]   [" + i + "] = \"" + feedItem.imageUrls.get(i) + "\"");
            }
            imageUrls = feedItem.imageUrls;
            tvTitle.setText(feedItem.title);
            tvUsername.setText(feedItem.username);
            tvContent.setText(feedItem.content);
            tvPublishTime.setText(feedItem.publishTime);
            tvLikeCount.setText(formatCount(feedItem.likeCount));
            tvLikeTotal.setText(formatCount(feedItem.likeCount));
            tvCommentCount.setText(formatCount(feedItem.commentCount));
            tvCollectCount.setText(formatCount(feedItem.collectCount));
            tvCollectTotal.setText(formatCount(feedItem.collectCount));
        } else {
            Log.d("DETAIL_DEBUG", "[loadData] 走分支③: 只有部分基础数据，先用 Intent 的 cover_url 占位，并等待网络回调");
            // 用传入的封面图立即渲染占位，避免图片区空白
            if (coverUrl != null && !coverUrl.isEmpty() && !coverUrl.equalsIgnoreCase("null")) {
                imageUrls = new ArrayList<>();
                imageUrls.add(coverUrl);
                Log.d("DETAIL_DEBUG", "[loadData③] 成功使用传入的 cover_url 占位: \"" + coverUrl + "\"");
            } else {
                imageUrls = Arrays.asList("");
                Log.w("DETAIL_DEBUG", "[loadData③] cover_url 为空，使用占位空串");
            }
            tvTitle.setText(title != null ? title : "");
            tvUsername.setText(userName != null ? userName : "未知用户");
            tvLikeCount.setText(formatCount(likeNumber));
            tvContent.setText("");
            tvPublishTime.setText("");
            tvLikeTotal.setText("0");
            tvCommentCount.setText("0");
            tvCollectCount.setText("0");
            tvCollectTotal.setText("0");
        }

        Log.d("DETAIL_DEBUG", "[loadData] 即将设置 Adapter，imageUrls.size()=" + imageUrls.size());
        for (int i = 0; i < imageUrls.size(); i++) {
            Log.d("DETAIL_DEBUG", "[loadData]   imageUrls[" + i + "] = \"" + imageUrls.get(i) + "\"");
        }
        Log.i(TAG_NOTE_IMG, "loadData 即将绑定 ViewPager | 条数=" + imageUrls.size());
        for (int i = 0; i < imageUrls.size(); i++) {
            Log.i(TAG_NOTE_IMG, "  vp[" + i + "]=" + imageUrls.get(i));
        }
        imageAdapter = new ImagePagerAdapter(imageUrls);
        vpImagesDetail.setAdapter(imageAdapter);
        updateIndicator(0);
        vpImagesDetail.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                updateIndicator(position);
            }
        });

        // 如果blogId存在，调用fetchBlogDetailFromServer获取详情数据
        if (blogId != null && !blogId.isEmpty()) {
            Log.d("DETAIL_DEBUG", "[loadData] blogId=\"" + blogId + "\", 调用fetchBlogDetailFromServer获取详情");
            fetchBlogDetailFromServer(blogId);
        } else {
            Log.e("DETAIL_DEBUG", "[loadData] ❌ blogId 为空，不发起详情请求！页面将保持空白。");
        }
    }

    /**
     * 从服务端拉取博客详情，完全复用广场页的 GET 接口逻辑：
     *   GET API_BLOG_DETAIL?blog_id={id}&type=1
     *   Header: Authorization: {token}
     * 回调在 OkHttp 线程池中，UI 更新统一包裹在 runOnUiThread。
     */
    private void fetchBlogDetailFromServer(String requestBlogId) {
        String url;
        try {
            okhttp3.HttpUrl.Builder urlBuilder =
                    okhttp3.HttpUrl.parse(ApiConfig.API_BLOG_DETAIL).newBuilder();
            urlBuilder.addQueryParameter("blog_id", requestBlogId);
            urlBuilder.addQueryParameter("type", "1");
            url = urlBuilder.build().toString();
        } catch (Exception e) {
            Log.e("DETAIL_DEBUG", "[fetch] ❌ 构建 URL 异常: " + e.getMessage(), e);
            return;
        }

        String token = TokenManager.getToken(this);
        Log.d("DETAIL_DEBUG", "[fetch] 请求 URL = " + url);
        Log.d("DETAIL_DEBUG", "[fetch] token " + (token != null && !token.isEmpty() ? "已设置(长度=" + token.length() + ")" : "❌ 为空，可能导致401"));
        Log.i(TAG_NOTE_IMG, "fetchBlogDetail 请求 | blogId=" + requestBlogId + " url=" + url);

        Request.Builder reqBuilder = new Request.Builder().url(url).get();
        if (token != null && !token.isEmpty()) {
            reqBuilder.addHeader("Authorization", "Bearer " + token);
        }

        commentHttpClient.newCall(reqBuilder.build()).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e("DETAIL_DEBUG", "[fetch] ❌ 网络请求失败(onFailure): " + e.getMessage(), e);
                Log.e(TAG_NOTE_IMG, "fetchBlogDetail 网络失败 blogId=" + requestBlogId + " err=" + e.getMessage());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                Log.d("DETAIL_DEBUG", "[fetch] HTTP 状态码 = " + response.code());
                if (!response.isSuccessful() || response.body() == null) {
                    Log.e("DETAIL_DEBUG", "[fetch] ❌ HTTP 非成功或 body 为空, code=" + response.code());
                    return;
                }
                String responseStr = response.body().string();
                // ★ 这是最关键的一行日志：把完整 JSON 打出来
                Log.d("DETAIL_DEBUG", "[fetch] ★ 完整响应体 = " + responseStr);

                try {
                    JSONObject root = new JSONObject(responseStr);
                    int businessCode = root.optInt("code", -1);
                    Log.d("DETAIL_DEBUG", "[fetch] 业务 code = " + businessCode);
                    if (businessCode != 200) {
                        Log.e("DETAIL_DEBUG", "[fetch] ❌ 业务 code 非200, message=" + root.optString("message"));
                        return;
                    }

                    // 判断 data 的类型
                    Object dataRaw = root.opt("data");
                    Log.d("DETAIL_DEBUG", "[fetch] data 字段类型 = " + (dataRaw != null ? dataRaw.getClass().getSimpleName() : "null"));

                    String dataStr = root.optString("data", null);
                    if (dataStr == null || dataStr.isEmpty() || dataStr.equals("null")) {
                        JSONObject dataObj = root.optJSONObject("data");
                        if (dataObj == null) {
                            Log.e("DETAIL_DEBUG", "[fetch] ❌ data 字段为空或无法解析");
                            return;
                        }
                        dataStr = dataObj.toString();
                    }
                    Log.d("DETAIL_DEBUG", "[fetch] 最终用于解析的 dataStr = " + dataStr);

                    BlogEntity blogEntity = new Gson().fromJson(dataStr, BlogEntity.class);
                    if (blogEntity == null) {
                        Log.e("DETAIL_DEBUG", "[fetch] ❌ Gson 解析后 blogEntity 为 null");
                        return;
                    }

                    // 打印解析结果，直接暴露字段是否对齐
                    Log.d("DETAIL_DEBUG", "[fetch] ── BlogEntity 解析结果 ──────────────");
                    Log.d("DETAIL_DEBUG", "  blogId   = " + blogEntity.getBlogId());
                    Log.d("DETAIL_DEBUG", "  noteId   = " + blogEntity.getNoteId());
                    Log.d("DETAIL_DEBUG", "  id       = " + blogEntity.getId());
                    Log.d("DETAIL_DEBUG", "  title    = \"" + blogEntity.getTitle() + "\"");
                    Log.d("DETAIL_DEBUG", "  content  = \"" + (blogEntity.getContent() != null ? blogEntity.getContent().substring(0, Math.min(40, blogEntity.getContent().length())) + "..." : "null") + "\"");
                    Log.d("DETAIL_DEBUG", "  liked    = " + blogEntity.getLike_number());
                    Log.d("DETAIL_DEBUG", "  comments = " + blogEntity.getComments());
                    Log.d("DETAIL_DEBUG", "  userId   = " + blogEntity.getUserId());
                    Log.d("DETAIL_DEBUG", "  userDTO  = " + (blogEntity.getUserDTO() != null ? "非null, nickName=\"" + blogEntity.getUserDTO().getNickName() + "\"" : "null"));
                    Log.d("DETAIL_DEBUG", "  getUser_name() = \"" + blogEntity.getUser_name() + "\"");
                    Log.d("DETAIL_DEBUG", "  imgUrlsStr = \"" + blogEntity.getImgUrlsStr() + "\"");
                    Log.d("DETAIL_DEBUG", "  photo列表 = " + (blogEntity.getPhoto() != null ? blogEntity.getPhoto().size() + " 张" : "null"));
                    Log.d("DETAIL_DEBUG", "───────────────────────────────────────");
                    Log.i(TAG_NOTE_IMG, "Gson 后 coverUrl=" + blogEntity.getCoverUrl()
                            + " imgUrlsStr(逗号分隔)长度=" + (blogEntity.getImgUrlsStr() != null ? blogEntity.getImgUrlsStr().length() : -1));

                    NoteDetailActivity.this.blogId = blogEntity.getBlog_id();
                    if (blogEntity.getUserId() != null) {
                        NoteDetailActivity.this.userId = blogEntity.getUserId();
                    }

                    // 封面 + 详情区逗号分隔 imgUrls，与封面 URL 去重
                    String effectiveCover = blogEntity.getCoverUrl();
                    if (effectiveCover == null || effectiveCover.trim().isEmpty()
                            || "null".equalsIgnoreCase(effectiveCover.trim())) {
                        effectiveCover = getIntent().getStringExtra("cover_url");
                    }
                    String rawDetailUrls = blogEntity.getImgUrlsStr();
                    final List<String> finalPhotos = new ArrayList<>(
                            BlogEntity.mergeCoverAndCommaDetailImages(effectiveCover, rawDetailUrls));
                    Log.i(TAG_NOTE_IMG, "mergeCover+逗号imgUrls 后条数=" + finalPhotos.size()
                            + " effectiveCover=" + effectiveCover);

                    if (finalPhotos.isEmpty()) {
                        String intentCoverUrl = getIntent().getStringExtra("cover_url");
                        if (intentCoverUrl != null && !intentCoverUrl.trim().isEmpty() && !intentCoverUrl.equalsIgnoreCase("null")) {
                            finalPhotos.add(intentCoverUrl.trim());
                            Log.w(TAG_NOTE_IMG, "兜底：仅 Intent.cover_url");
                        } else {
                            ArrayList<String> intentPhotos = getIntent().getStringArrayListExtra("photo");
                            if (intentPhotos != null && !intentPhotos.isEmpty()) {
                                for (String p : intentPhotos) {
                                    if (p != null && !p.trim().isEmpty() && !p.equalsIgnoreCase("null")) {
                                        String c = p.replace("\"", "").replace("[", "").replace("]", "").trim();
                                        if (!c.isEmpty() && !finalPhotos.contains(c)) {
                                            finalPhotos.add(c);
                                        }
                                    }
                                }
                                Log.w(TAG_NOTE_IMG, "兜底：Intent.photo[] 条数=" + finalPhotos.size());
                            }
                        }
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        finalPhotos.removeIf(u -> u == null || u.trim().isEmpty() || u.equalsIgnoreCase("null"));
                    } else {
                        List<String> cleanList = new ArrayList<>();
                        for (String u : finalPhotos) {
                            if (u != null && !u.trim().isEmpty() && !u.equalsIgnoreCase("null")) {
                                cleanList.add(u);
                            }
                        }
                        finalPhotos.clear();
                        finalPhotos.addAll(cleanList);
                    }

                    // userDTO.nickName 优先，getUser_name() 已内置 nickName / userName 兜底
                    final String authorName = blogEntity.getUser_name();
                    // userDTO.icon 优先，getAuthorIcon() 已兜底平铺 icon 字段
                    final String authorIconUrl = blogEntity.getAuthorIcon();

                    Log.d("DETAIL_DEBUG", "[fetch] 准备 runOnUiThread 更新 UI");
                    Log.d("DETAIL_DEBUG", "  tvTitle    → \"" + blogEntity.getTitle() + "\"");
                    Log.d("DETAIL_DEBUG", "  tvUsername → \"" + authorName + "\"");
                    Log.d("DETAIL_DEBUG", "  authorIcon → \"" + authorIconUrl + "\"");
                    Log.d("DETAIL_DEBUG", "  图片总数   → " + finalPhotos.size());
                    Log.i(TAG_NOTE_IMG, "fetch 合并/清洗后 finalPhotos 条数=" + finalPhotos.size());
                    for (int i = 0; i < finalPhotos.size(); i++) {
                        Log.i(TAG_NOTE_IMG, "  final[" + i + "]=" + finalPhotos.get(i));
                    }

                    runOnUiThread(() -> {
                        tvTitle.setText(blogEntity.getTitle());
                        // data.userDTO.nickName → tv_username
                        tvUsername.setText(authorName);
                        // data.content → tv_content
                        tvContent.setText(blogEntity.getContent());
                        tvLikeCount.setText(formatCount(blogEntity.getLike_number()));
                        tvLikeTotal.setText(formatCount(blogEntity.getLiked()));
                        tvCommentCount.setText(formatCount(blogEntity.getComments()));
                        tvCollectCount.setText(formatCount(blogEntity.getCollect_number()));
                        tvCollectTotal.setText(formatCount(blogEntity.getCollectTotal()));

                        // 头像：placeholder + error 双重兜底，防止"hhhh"等脏 URL 留白
                        Log.d("DETAIL_DEBUG", "[fetch][头像] Glide 加载 \"" + authorIconUrl + "\"");
                        Glide.with(NoteDetailActivity.this)
                                .load(authorIconUrl)
                                .circleCrop()
                                .placeholder(R.drawable.circle_background)
                                .error(R.drawable.circle_background)
                                .into(ivUserAvatar);

                        // 核心：决定图片轮播的生死
                        if (finalPhotos.isEmpty()) {
                            Log.w("DETAIL_DEBUG", "[fetch][UI] 无图片，隐藏 vp_images_detail");
                            vpImagesDetail.setVisibility(View.GONE);
                            tvImageIndicator.setVisibility(View.GONE);
                        } else {
                            Log.d("DETAIL_DEBUG", "[fetch][UI] 绑定 Adapter，图片数=" + finalPhotos.size());
                            vpImagesDetail.setVisibility(View.VISIBLE);
                            imageUrls = new ArrayList<>(finalPhotos);
                            imageAdapter = new ImagePagerAdapter(imageUrls);
                            vpImagesDetail.setAdapter(imageAdapter);
                            updateIndicator(0);
                            Log.d("DETAIL_DEBUG", "[fetch][UI] Adapter 已绑定，vp_images_detail 可见");
                            if (vpImagesDetail.getAdapter() != null) {
                                Log.i(TAG_NOTE_IMG, "UI 已绑定 ViewPager2 adapter.getItemCount="
                                        + vpImagesDetail.getAdapter().getItemCount());
                            }
                        }

                        Log.d("DETAIL_DEBUG", "[fetch] ✅ UI 更新完成");
                        Log.d("DETAIL_DEBUG", "[fetch] ✅ 详情数据已返回");
                        // 移除loadLikeAndFollowStatus()调用，因为我们已经在状态请求完成后调用了它
                    });
                } catch (Exception e) {
                    Log.e("DETAIL_DEBUG", "[fetch] ❌ 解析异常: " + e.getMessage(), e);
                }
            }
        });
    }

    private void updateIndicator(int position) {
        int total = imageUrls.size();
        boolean hasRealImage = total > 1 || (total == 1 && !imageUrls.get(0).isEmpty());
        if (!hasRealImage || total <= 1) {
            tvImageIndicator.setVisibility(View.GONE);
        } else {
            tvImageIndicator.setVisibility(View.VISIBLE);
            tvImageIndicator.setText((position + 1) + "/" + total);
        }
    }

    private String formatCount(int count) {
        if (count >= 10000) {
            return String.format("%.1fw", count / 10000.0);
        } else if (count >= 1000) {
            return String.format("%.1fk", count / 1000.0);
        }
        return String.valueOf(count);
    }

    // ─────────────────────────────────────────────────────────────
    // 图片轮播适配器（升级版：centerInside + 全盘缓存 + 品牌色占位）
    // ─────────────────────────────────────────────────────────────
    private static class ImagePagerAdapter extends RecyclerView.Adapter<ImagePagerAdapter.ImageViewHolder> {

        private final List<String> imageUrls;

        public ImagePagerAdapter(List<String> imageUrls) {
            this.imageUrls = imageUrls;
            android.util.Log.d("DETAIL_DEBUG", "[ImagePagerAdapter] 创建，共 " + imageUrls.size() + " 张:");
            for (int i = 0; i < imageUrls.size(); i++) {
                android.util.Log.d("DETAIL_DEBUG", "[ImagePagerAdapter]   [" + i + "] = \"" + imageUrls.get(i) + "\"");
            }
        }

        @NonNull
        @Override
        public ImageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            android.util.Log.d("DETAIL_DEBUG", "[ImagePagerAdapter] onCreateViewHolder，总页数=" + imageUrls.size());
            android.view.View itemView = android.view.LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_note_image_full, parent, false);
            return new ImageViewHolder(itemView);
        }

        @Override
        public void onBindViewHolder(@NonNull ImageViewHolder holder, int position) {
            String url = imageUrls.get(position);
            android.util.Log.d("DETAIL_DEBUG", "[ImagePagerAdapter] onBindViewHolder position=" + position + ", url=\"" + url + "\"");
            if (url == null || url.isEmpty()) {
                android.util.Log.w("DETAIL_DEBUG", "[ImagePagerAdapter] ⚠️ position=" + position + " url为空，显示品牌色占位");
                holder.imageView.setBackgroundColor(0xFFFFF0F5);
            } else {
                android.util.Log.d("DETAIL_DEBUG", "[ImagePagerAdapter] 调用 Glide 加载: \"" + url + "\"");
                Glide.with(holder.imageView)
                        .load(url)
                        .centerInside()
                        .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL)
                        .placeholder(new android.graphics.drawable.ColorDrawable(0xFFFFF0F5))
                        .error(new android.graphics.drawable.ColorDrawable(0xFFFFB6C1))
                        .listener(new com.bumptech.glide.request.RequestListener<android.graphics.drawable.Drawable>() {
                            @Override
                            public boolean onLoadFailed(com.bumptech.glide.load.engine.GlideException e,
                                                        Object model, com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> target,
                                                        boolean isFirstResource) {
                                android.util.Log.e("DETAIL_DEBUG", "[ImagePagerAdapter] ❌ Glide 加载失败 position=" + position
                                        + ", url=\"" + url + "\", 原因: " + (e != null ? e.getMessage() : "null"));
                                return false;
                            }
                            @Override
                            public boolean onResourceReady(android.graphics.drawable.Drawable resource,
                                                           Object model, com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> target,
                                                           com.bumptech.glide.load.DataSource dataSource, boolean isFirstResource) {
                                android.util.Log.d("DETAIL_DEBUG", "[ImagePagerAdapter] ✅ Glide 加载成功 position=" + position
                                        + ", url=\"" + url + "\", dataSource=" + dataSource);
                                return false;
                            }
                        })
                        .into(holder.imageView);
            }
        }

        @Override
        public int getItemCount() {
            return imageUrls.size();
        }

        static class ImageViewHolder extends RecyclerView.ViewHolder {
            com.google.android.material.imageview.ShapeableImageView imageView;

            public ImageViewHolder(@NonNull View itemView) {
                super(itemView);
                imageView = itemView.findViewById(R.id.iv_note_image_item);
            }
        }
    }
}
