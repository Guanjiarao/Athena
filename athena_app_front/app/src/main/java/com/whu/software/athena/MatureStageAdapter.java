package com.whu.software.athena;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

/**
 * 成熟阶段分类列表适配器。
 */
public class MatureStageAdapter extends RecyclerView.Adapter<MatureStageAdapter.ViewHolder> {

    private static final String TITLE_SKINCARE = "\u62a4\u80a4\u6307\u5357";
    private static final String TITLE_PREPARE = "\u79d1\u5b66\u5907\u5b55";
    private static final String TITLE_AVOID = "\u907f\u5b55\u6307\u5357";
    private static final String TITLE_PREGNANCY_CARE = "\u5b55\u671f\u62a4\u7406";
    private static final String TITLE_POSTPARTUM_RECOVERY = "\u6708\u5b50\u671f\u6062\u590d";
    private static final String TITLE_FERTILITY_SCIENCE = "\u751f\u80b2\u79d1\u666e";

    public interface OnCategoryClickListener {
        void onCategoryClick(String title);
    }

    private final List<String> items;
    private OnCategoryClickListener categoryClickListener;

    public MatureStageAdapter(List<String> items) {
        this.items = items;
    }

    public void setOnCategoryClickListener(OnCategoryClickListener listener) {
        this.categoryClickListener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_category_card, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        String title = items.get(position);
        holder.tvTitle.setText(title);
        holder.ivIcon.setImageResource(resolveIcon(title));

        holder.itemView.setOnClickListener(v -> {
            if (categoryClickListener != null) {
                categoryClickListener.onCategoryClick(title);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items != null ? items.size() : 0;
    }

    private int resolveIcon(String title) {
        if (TITLE_SKINCARE.equals(title)) {
            return R.drawable.skincare;
        }
        if (TITLE_PREPARE.equals(title)) {
            return R.drawable.prepare_pregnancy;
        }
        if (TITLE_AVOID.equals(title)) {
            return R.drawable.birth_control;
        }
        if (TITLE_PREGNANCY_CARE.equals(title)) {
            return R.drawable.pregnancy_care;
        }
        if (TITLE_POSTPARTUM_RECOVERY.equals(title)) {
            return R.drawable.postpartum_recovery;
        }
        if (TITLE_FERTILITY_SCIENCE.equals(title)) {
            return R.drawable.fertility_science;
        }
        return R.drawable.circle;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final ImageView ivIcon;
        final TextView tvTitle;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivIcon = itemView.findViewById(R.id.iv_icon);
            tvTitle = itemView.findViewById(R.id.tv_title);
        }
    }
}
