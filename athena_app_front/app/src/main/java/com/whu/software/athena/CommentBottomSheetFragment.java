package com.whu.software.athena;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.gson.Gson;
import com.whu.software.athena.config.ApiConfig;
import com.whu.software.athena.entity.CommentBean;
import com.whu.software.athena.entity.CommentResponse;
import com.whu.software.athena.entity.PublishCommentRequest;
import com.whu.software.athena.utils.TokenManager;
import com.whu.software.athena.utils.UnsafeOkHttpClient;

import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

public class CommentBottomSheetFragment extends BottomSheetDialogFragment {

    private static final String TAG = "CommentBottomSheet";
    private static final String TAG_COMMENT = "SubmitComment";

    private RecyclerView rvComments;
    private EditText etCommentInput;
    private TextView btnSendComment;
    private ImageView ivClose;
    private TextView tvTitle;

    private CommentAdapter commentAdapter;
    private OkHttpClient commentHttpClient;
    private CommentBean currentReplyTarget = null;
    private VideoDetailActivity mActivity;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof VideoDetailActivity) {
            mActivity = (VideoDetailActivity) context;
            Log.d(TAG_COMMENT, "onAttach: mActivity set to " + mActivity);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        Log.d(TAG_COMMENT, "onCreateView called, mActivity=" + mActivity + ", getActivity()=" + getActivity());
        View view = inflater.inflate(R.layout.fragment_video_comment, container, false);
        initViews(view);
        setupClickListeners();
        initCommentList();
        loadComments(1);
        return view;
    }

    private void initViews(View view) {
        rvComments = view.findViewById(R.id.rv_comments);
        etCommentInput = view.findViewById(R.id.et_comment_input);
        btnSendComment = view.findViewById(R.id.btn_send_comment);
        ivClose = view.findViewById(R.id.iv_close);
        tvTitle = view.findViewById(R.id.tv_title);
        commentHttpClient = UnsafeOkHttpClient.getUnsafeOkHttpClient();
    }

    private void setupClickListeners() {
        ivClose.setOnClickListener(v -> dismiss());

        // 评论输入框：点击弹出软键盘
        etCommentInput.setOnClickListener(v -> {
            etCommentInput.requestFocus();
            showKeyboard(etCommentInput);
        });

        // 点击发送（键盘 ActionSend）
        etCommentInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND) {
                submitComment();
                return true;
            }
            return false;
        });

        // 发送按钮点击
        btnSendComment.setOnClickListener(v -> submitComment());
    }

    private void showKeyboard(View view) {
        view.requestFocus();
        android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(view, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
        }
    }

    private void hideKeyboard(View view) {
        android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

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
        rvComments.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvComments.setAdapter(commentAdapter);
        rvComments.setNestedScrollingEnabled(false);
    }

    private void loadComments(int pageNum) {
        Log.d(TAG_COMMENT, "loadComments called, pageNum=" + pageNum);
        if (mActivity == null) {
            Log.e(TAG_COMMENT, "loadComments: mActivity is null");
            return;
        }

        String blogId = mActivity.blogId;
        Log.d(TAG_COMMENT, "loadComments: blogId=" + blogId);
        if (blogId == null || blogId.isEmpty()) {
            Log.w(TAG_COMMENT, "loadComments: blogId 为空，跳过请求");
            return;
        }
        int id;
        try {
            id = Integer.parseInt(blogId);
            Log.d(TAG_COMMENT, "loadComments: parsed blogId=" + id);
        } catch (NumberFormatException e) {
            Log.e(TAG_COMMENT, "loadComments: blogId 无法转为 int: " + blogId, e);
            return;
        }

        // 构建请求 URL
        String url = ApiConfig.API_COMMENT_LIST_PAGE
                + "?blogId=" + id
                + "&pageNum=" + pageNum
                + "&pageSize=10";
        Log.d(TAG_COMMENT, "loadComments url=" + url);

        // 添加 token 到请求头
        String token = TokenManager.getToken(mActivity);
        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + token)
                .get()
                .build();
        Log.d(TAG_COMMENT, "loadComments: sending request with token");
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
                        requireActivity().runOnUiThread(() -> commentAdapter.setData(flattened));
                    } else {
                        Log.w(TAG_COMMENT, "loadComments result null or code != 200，渲染空列表");
                        requireActivity().runOnUiThread(() -> commentAdapter.setData(new ArrayList<>()));
                    }
                } catch (Exception e) {
                    Log.e(TAG_COMMENT, "loadComments JSON parse error: " + e.getMessage(), e);
                    requireActivity().runOnUiThread(() -> commentAdapter.setData(new ArrayList<>()));
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

                        requireActivity().runOnUiThread(() -> {
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

    private void submitComment() {
        VideoDetailActivity activity = (VideoDetailActivity) getActivity();
        if (activity == null) return;

        String text = etCommentInput.getText().toString().trim();
        Log.d(TAG_COMMENT, "submitComment called, text=\"" + text + "\"");
        if (text.isEmpty()) {
            Toast.makeText(requireContext(), "评论不能为空", Toast.LENGTH_SHORT).show();
            return;
        }

        String blogId = activity.blogId;
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

        String token = TokenManager.getToken(requireContext());
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
                requireActivity().runOnUiThread(() -> {
                    btnSendComment.setEnabled(true);
                    Toast.makeText(requireContext(), "网络异常，请重试", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                String responseBody = response.body() != null ? response.body().string() : "";
                Log.d(TAG_COMMENT, "onResponse httpCode=" + response.code() + ", body=" + responseBody);
                requireActivity().runOnUiThread(() -> {
                    btnSendComment.setEnabled(true);
                    try {
                        JSONObject jsonResp = new JSONObject(responseBody);
                        int code = jsonResp.optInt("code", -1);
                        Log.d(TAG_COMMENT, "business code=" + code);
                        if (code == 200) {
                            Toast.makeText(requireContext(), "发布成功", Toast.LENGTH_SHORT).show();

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
                            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
                        }
                    } catch (Exception e) {
                        Log.e(TAG_COMMENT, "解析响应异常: " + e.getMessage(), e);
                        Toast.makeText(requireContext(), "发布失败，请重试", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }
}
