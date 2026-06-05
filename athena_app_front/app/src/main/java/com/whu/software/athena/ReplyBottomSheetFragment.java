package com.whu.software.athena;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.gson.Gson;
import com.whu.software.athena.config.ApiConfig;
import com.whu.software.athena.entity.CommentBean;
import com.whu.software.athena.entity.CommentResponse;
import com.whu.software.athena.utils.UnsafeOkHttpClient;

import java.io.IOException;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class ReplyBottomSheetFragment extends BottomSheetDialogFragment {

    private static final String ARG_COMMENT_ID = "comment_id";

    private long commentId;
    private ReplyAdapter replyAdapter;
    private OkHttpClient httpClient;

    public static ReplyBottomSheetFragment newInstance(long commentId) {
        Bundle args = new Bundle();
        args.putLong(ARG_COMMENT_ID, commentId);
        ReplyBottomSheetFragment fragment = new ReplyBottomSheetFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            commentId = getArguments().getLong(ARG_COMMENT_ID, 0);
        }
        httpClient = UnsafeOkHttpClient.getUnsafeOkHttpClient();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_reply_bottom_sheet, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        ImageView ivClose = view.findViewById(R.id.iv_close);
        ivClose.setOnClickListener(v -> dismissAllowingStateLoss());

        RecyclerView rvReplies = view.findViewById(R.id.rv_replies);
        replyAdapter = new ReplyAdapter();
        rvReplies.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvReplies.setAdapter(replyAdapter);

        loadReplies(1);
    }

    private void loadReplies(int pageNum) {
        String url = ApiConfig.API_COMMENT_EXTEND
                + "?commentId=" + commentId
                + "&pageNum=" + pageNum
                + "&pageSize=10";

        Request request = new Request.Builder().url(url).get().build();
        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                if (isAdded()) {
                    requireActivity().runOnUiThread(() ->
                            Toast.makeText(requireContext(), "加载回复失败", Toast.LENGTH_SHORT).show());
                }
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (!response.isSuccessful() || response.body() == null) return;
                String body = response.body().string();
                try {
                    CommentResponse result = new Gson().fromJson(body, CommentResponse.class);
                    if (result != null && result.isSuccess() && isAdded()) {
                        List<CommentBean> data = result.getData();
                        requireActivity().runOnUiThread(() -> replyAdapter.setData(data));
                    }
                } catch (Exception ignored) {}
            }
        });
    }
}