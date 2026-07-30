package com.whu.software.athena;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.whu.software.athena.features.privacy.PrivacyFragment;

import java.util.List;

/**
 * 知识页根 Fragment。
 *
 * 布局结构：
 * 1. 顶部固定 Header，包含发布按钮、Tab 和右上角操作按钮。
 * 2. 中间使用 ViewPager2 承载两个子页面：
 *    - position 0: RecommendFragment
 *    - position 1: ScienceTimelineFragment
 *
 * 交互规则：
 * 1. 推荐页显示发布按钮，右上角显示普通搜索图标。
 * 2. 科普页左上角显示隐私守护入口，右上角显示 AI 科普入口图标。
 */
public class KnowledgeFragment extends Fragment {

    // 页面索引常量
    private static final int PAGE_RECOMMEND = 0;
    private static final int PAGE_SCIENCE = 1;

    // Header 视图
    private ImageButton btnCreate;
    private TabLayout tabLayout;
    private ImageButton btnSearch;

    // 内容区 ViewPager2
    private ViewPager2 contentPager;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_knowledge, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initHeaderViews(view);
        setupContentPager();
        setupTabWithPager();
        setupHeaderClickListeners();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        contentPager = null;
        tabLayout = null;
        btnCreate = null;
        btnSearch = null;
    }

    // 初始化头部视图
    private void initHeaderViews(View view) {
        btnCreate = view.findViewById(R.id.knowledge_btn_create);
        tabLayout = view.findViewById(R.id.knowledge_tab_layout);
        btnSearch = view.findViewById(R.id.knowledge_btn_search);
        contentPager = view.findViewById(R.id.knowledge_content_pager);
    }

    private void setupContentPager() {
        contentPager.setAdapter(new KnowledgePagerAdapter(requireActivity()));
        // 预加载相邻页面，避免来回切换时重复创建
        contentPager.setOffscreenPageLimit(1);
    }

    /**
     * 将 TabLayout 与 ViewPager2 绑定，并根据当前页面同步头部状态。
     */
    private void setupTabWithPager() {
        new TabLayoutMediator(tabLayout, contentPager,
                (tab, position) -> tab.setText(position == PAGE_RECOMMEND ? "推荐" : "科普")
        ).attach();

        contentPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                tabLayout.setVisibility(View.VISIBLE);
                btnSearch.setVisibility(View.VISIBLE);
                updateHeaderForPage(position);
            }
        });

        updateHeaderForPage(contentPager.getCurrentItem());
    }

    private void setupHeaderClickListeners() {
        btnCreate.setOnClickListener(v -> {
            if (contentPager.getCurrentItem() == PAGE_SCIENCE) {
                openPrivacyGuard();
                return;
            }
            Intent intent = new Intent(getContext(), PublishActivity.class);
            startActivity(intent);
        });

        btnSearch.setOnClickListener(v -> {
            if (getContext() != null) {
                Intent intent = contentPager.getCurrentItem() == PAGE_SCIENCE
                        ? new Intent(getContext(), ScienceAISearchActivity.class)
                        : new Intent(getContext(), SearchActivity.class);
                if (contentPager.getCurrentItem() == PAGE_RECOMMEND) {
                    intent.putExtra("search_type", SearchActivity.SEARCH_TYPE_KNOWLEDGE);
                }
                startActivity(intent);
            }
        });
    }

    private void updateHeaderForPage(int position) {
        if (position == PAGE_SCIENCE) {
            btnCreate.setVisibility(View.GONE);
            btnCreate.setImageResource(R.drawable.ic_privacy_shield_outline);
            btnCreate.setContentDescription("隐私守护");
            btnSearch.setImageResource(R.drawable.science_ai);
            btnSearch.setContentDescription(getString(R.string.science_ai_title));
        } else {
            btnCreate.setVisibility(View.GONE);
            btnCreate.setImageResource(R.drawable.ic_add);
            btnCreate.setContentDescription("创作");
            btnSearch.setImageResource(R.drawable.ic_search);
            btnSearch.setContentDescription("搜索");
        }
    }

    private void openPrivacyGuard() {
        FragmentActivity activity = getActivity();
        if (activity == null) {
            return;
        }
        if (activity instanceof MainActivity) {
            ((MainActivity) activity).setBottomNavigationVisible(false);
        }
        activity.getSupportFragmentManager().beginTransaction()
                .replace(R.id.nav_host_fragment, new PrivacyFragment())
                .addToBackStack("privacy_guard")
                .commit();
    }

    // ViewPager2 适配器
    private static class KnowledgePagerAdapter extends FragmentStateAdapter {

        KnowledgePagerAdapter(@NonNull FragmentActivity fa) {
            super(fa);
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            return position == PAGE_RECOMMEND
                    ? new RecommendFragment()
                    : new ScienceTimelineFragment();
        }

        @Override
        public int getItemCount() {
            return 2;
        }
    }

    // 公共数据类，供 RecommendFragment 复用
    public static class BannerItem {
        public final int id;
        public final String title;
        public final int backgroundColor;

        public BannerItem(int id, String title, int backgroundColor) {
            this.id = id;
            this.title = title;
            this.backgroundColor = backgroundColor;
        }
    }

    public static class VideoItem {
        public final int id;
        public final String title;
        public final String username;
        public final int likeCount;
        public final int coverHeight;

        public VideoItem(int id, String title, String username, int likeCount, int coverHeight) {
            this.id = id;
            this.title = title;
            this.username = username;
            this.likeCount = likeCount;
            this.coverHeight = coverHeight;
        }
    }

    // 公共轮播适配器，供 RecommendFragment 复用
    public static class BannerAdapter extends RecyclerView.Adapter<BannerAdapter.BannerViewHolder> {

        public interface OnItemClickListener {
            void onItemClick(BannerItem item);
        }

        private final List<BannerItem> items;
        private final OnItemClickListener listener;

        public BannerAdapter(List<BannerItem> items, OnItemClickListener listener) {
            this.items = items;
            this.listener = listener;
        }

        @NonNull
        @Override
        public BannerViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_knowledge_banner, parent, false);
            return new BannerViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull BannerViewHolder holder, int position) {
            BannerItem item = items.get(position % items.size());
            holder.image.setBackgroundColor(item.backgroundColor);
            holder.title.setText(item.title);
            holder.itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onItemClick(item);
                }
            });
        }

        @Override
        public int getItemCount() {
            return Integer.MAX_VALUE;
        }

        static class BannerViewHolder extends RecyclerView.ViewHolder {
            final ImageView image;
            final TextView title;

            BannerViewHolder(@NonNull View itemView) {
                super(itemView);
                image = itemView.findViewById(R.id.knowledge_banner_image);
                title = itemView.findViewById(R.id.knowledge_banner_title);
            }
        }
    }

    // 公共视频流适配器，供 RecommendFragment 复用
    public static class VideoAdapter extends RecyclerView.Adapter<VideoAdapter.VideoViewHolder> {

        public interface OnItemClickListener {
            void onItemClick(VideoItem item);
        }

        private final List<VideoItem> items;
        private final OnItemClickListener listener;

        public VideoAdapter(List<VideoItem> items, OnItemClickListener listener) {
            this.items = items;
            this.listener = listener;
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
            VideoItem item = items.get(position);

            ViewGroup.LayoutParams params = holder.cover.getLayoutParams();
            params.height = dpToPx(holder.itemView, item.coverHeight);
            holder.cover.setLayoutParams(params);
            holder.cover.setBackgroundColor(placeholderColor(item.id));

            holder.title.setText(item.title);
            holder.username.setText(item.username);
            holder.likeCount.setText(formatLikes(item.likeCount));

            holder.itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onItemClick(item);
                }
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        private String formatLikes(int count) {
            return count >= 10000 ? String.format("%.1fw", count / 10000.0) : String.valueOf(count);
        }

        private int dpToPx(View view, int dp) {
            return (int) (dp * view.getContext().getResources().getDisplayMetrics().density + 0.5f);
        }

        private int placeholderColor(int seed) {
            int[] colors = {
                    0xFFE8D5D5, 0xFFD5E8D5, 0xFFD5D5E8,
                    0xFFE8E8D5, 0xFFE8D5E8, 0xFFD5E8E8,
                    0xFFFFE4E1, 0xFFFFF0F5
            };
            return colors[seed % colors.length];
        }

        static class VideoViewHolder extends RecyclerView.ViewHolder {
            final CardView card;
            final ImageView cover;
            final TextView title;
            final ImageView avatar;
            final TextView username;
            final TextView likeCount;

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
    }
}
