package com.whu.software.athena.features.chat.history;

import android.text.InputType;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.whu.software.athena.R;

import java.util.List;

public class ConversationHistorySheet {

    public interface Listener {
        void onConversationSelected(ConversationSummary summary);

        void onConversationRenameRequested(ConversationSummary summary, String newTitle);

        void onConversationDeleteRequested(ConversationSummary summary);
    }

    private final FragmentActivity activity;
    private final Listener listener;
    private final BottomSheetDialog dialog;
    private final TextView tvTitle;
    private final TextView tvEmpty;
    private final ConversationHistoryAdapter adapter;

    public ConversationHistorySheet(@NonNull FragmentActivity activity,
                                    @NonNull Listener listener) {
        this.activity = activity;
        this.listener = listener;
        dialog = new BottomSheetDialog(activity);

        View contentView = LayoutInflater.from(activity)
                .inflate(R.layout.dialog_conversation_history, null, false);
        dialog.setContentView(contentView);

        tvTitle = contentView.findViewById(R.id.tv_conversation_history_title);
        tvEmpty = contentView.findViewById(R.id.tv_conversation_history_empty);
        RecyclerView recyclerView = contentView.findViewById(R.id.rv_conversation_history);

        adapter = new ConversationHistoryAdapter(new ConversationHistoryAdapter.Listener() {
            @Override
            public void onConversationSelected(ConversationSummary summary) {
                dialog.dismiss();
                listener.onConversationSelected(summary);
            }

            @Override
            public void onConversationRenameRequested(ConversationSummary summary) {
                promptRename(summary);
            }

            @Override
            public void onConversationDeleteRequested(ConversationSummary summary) {
                promptDelete(summary);
            }
        });

        recyclerView.setLayoutManager(new LinearLayoutManager(activity));
        recyclerView.setAdapter(adapter);

        tvEmpty.setText("\u6682\u65E0\u5386\u53F2\u4F1A\u8BDD");
    }

    public void show(@NonNull String title,
                     @NonNull List<ConversationSummary> conversations,
                     String currentConversationId) {
        tvTitle.setText(title);
        tvEmpty.setVisibility(conversations.isEmpty() ? View.VISIBLE : View.GONE);
        adapter.submitList(conversations, currentConversationId);
        dialog.show();
    }

    public void dismiss() {
        dialog.dismiss();
    }

    private void promptRename(@NonNull ConversationSummary summary) {
        EditText input = new EditText(activity);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setText(summary.getTitle());
        input.setSelection(input.getText().length());
        int horizontal = dp(20);
        int vertical = dp(12);
        input.setPadding(horizontal, vertical, horizontal, vertical);

        new AlertDialog.Builder(activity)
                .setTitle("\u91CD\u547D\u540D\u4F1A\u8BDD")
                .setView(input)
                .setNegativeButton("\u53D6\u6D88", null)
                .setPositiveButton("\u786E\u5B9A", (dialogInterface, which) -> {
                    String newTitle = input.getText().toString().trim();
                    if (TextUtils.isEmpty(newTitle)) {
                        return;
                    }
                    dialog.dismiss();
                    listener.onConversationRenameRequested(summary, newTitle);
                })
                .show();
    }

    private void promptDelete(@NonNull ConversationSummary summary) {
        new AlertDialog.Builder(activity)
                .setTitle("\u5220\u9664\u4F1A\u8BDD")
                .setMessage("\u5220\u9664\u540E\u5C06\u65E0\u6CD5\u6062\u590D")
                .setNegativeButton("\u53D6\u6D88", null)
                .setPositiveButton("\u5220\u9664", (dialogInterface, which) -> {
                    dialog.dismiss();
                    listener.onConversationDeleteRequested(summary);
                })
                .show();
    }

    private int dp(int value) {
        return Math.round(activity.getResources().getDisplayMetrics().density * value);
    }
}
