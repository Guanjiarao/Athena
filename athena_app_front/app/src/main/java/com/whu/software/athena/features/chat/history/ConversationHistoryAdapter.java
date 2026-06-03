package com.whu.software.athena.features.chat.history;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.whu.software.athena.R;

import java.util.ArrayList;
import java.util.List;

public class ConversationHistoryAdapter
        extends RecyclerView.Adapter<ConversationHistoryAdapter.ViewHolder> {

    public interface Listener {
        void onConversationSelected(ConversationSummary summary);

        void onConversationRenameRequested(ConversationSummary summary);

        void onConversationDeleteRequested(ConversationSummary summary);
    }

    private final List<ConversationSummary> conversations = new ArrayList<>();
    private final Listener listener;
    private String currentConversationId;

    public ConversationHistoryAdapter(@NonNull Listener listener) {
        this.listener = listener;
    }

    public void submitList(@NonNull List<ConversationSummary> newItems,
                           String selectedConversationId) {
        conversations.clear();
        conversations.addAll(newItems);
        currentConversationId = selectedConversationId;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_conversation_history, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ConversationSummary summary = conversations.get(position);
        holder.tvTitle.setText(summary.getTitle());
        holder.tvTime.setText(summary.getDisplayTime());
        holder.itemView.setBackgroundResource(
                TextUtils.equals(currentConversationId, summary.getConversationId())
                        ? R.drawable.bg_conversation_item_selected
                        : R.drawable.bg_conversation_item
        );
        holder.itemView.setOnClickListener(v -> listener.onConversationSelected(summary));
        holder.tvRename.setOnClickListener(v -> listener.onConversationRenameRequested(summary));
        holder.tvDelete.setOnClickListener(v -> listener.onConversationDeleteRequested(summary));
    }

    @Override
    public int getItemCount() {
        return conversations.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView tvTitle;
        final TextView tvTime;
        final TextView tvRename;
        final TextView tvDelete;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tv_conversation_title);
            tvTime = itemView.findViewById(R.id.tv_conversation_time);
            tvRename = itemView.findViewById(R.id.tv_conversation_rename);
            tvDelete = itemView.findViewById(R.id.tv_conversation_delete);
            tvRename.setText("\u91CD\u547D\u540D");
            tvDelete.setText("\u5220\u9664");
        }
    }
}
