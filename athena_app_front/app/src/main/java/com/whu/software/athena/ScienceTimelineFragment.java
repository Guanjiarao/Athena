package com.whu.software.athena;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.whu.software.athena.entity.TimelineEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 科普时光轴 Fragment — 作为主页 ViewPager / BottomNav 下的标准 Fragment 使用。
 * 无顶部导航栏，无返回箭头，完全依赖外层 Activity 的顶栏和底栏。
 */
public class ScienceTimelineFragment extends Fragment {

    private static final String TAG = "ScienceTimelineFragment";

    private RecyclerView    rvTimeline;
    private RecyclerView    rvCategory;
    private TimelineAdapter adapter;
    private CategoryAdapter categoryAdapter;

    // -----------------------------------------------------------------------
    // Fragment 生命周期
    // -----------------------------------------------------------------------

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_science_timeline, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        rvTimeline = view.findViewById(R.id.rv_science_timeline);
        rvCategory = view.findViewById(R.id.rv_category);
        setupRecyclerView();
        loadMockData();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        rvTimeline      = null;
        rvCategory      = null;
        adapter         = null;
        categoryAdapter = null;
    }

    // -----------------------------------------------------------------------
    // RecyclerView 初始化
    // -----------------------------------------------------------------------

    private void setupRecyclerView() {
        // ── 顶部：年龄段横向导航 ───────────────────────────────────────────
        adapter = new TimelineAdapter();
        LinearLayoutManager layoutManager = new LinearLayoutManager(
                requireContext(),
                LinearLayoutManager.HORIZONTAL,
                false
        );
        rvTimeline.setLayoutManager(layoutManager);
        rvTimeline.setAdapter(adapter);
        // 顶部无需再绘制竖线/透明度动效，直接展示横向导航即可

        // ── 底部：单列分类卡片列表 ─────────────────────────────────────────
        categoryAdapter = new CategoryAdapter();
        rvCategory.setLayoutManager(new LinearLayoutManager(
                requireContext(),
                LinearLayoutManager.VERTICAL,
                false
        ));
        rvCategory.setAdapter(categoryAdapter);
        // 核心补丁：防止外层 ViewPager2 拦截左右滑动事件，解决“划不动”的Bug
        rvTimeline.addOnItemTouchListener(new RecyclerView.OnItemTouchListener() {
            @Override
            public boolean onInterceptTouchEvent(@NonNull RecyclerView rv, @NonNull android.view.MotionEvent e) {
                if (e.getAction() == android.view.MotionEvent.ACTION_DOWN) {
                    // 当手指摸到这排卡片时，霸道地告诉外层的 ViewPager2：“不许抢我的滑动事件！”
                    rv.getParent().requestDisallowInterceptTouchEvent(true);
                }
                return false;
            }
            @Override
            public void onTouchEvent(@NonNull RecyclerView rv, @NonNull android.view.MotionEvent e) {}
            @Override
            public void onRequestDisallowInterceptTouchEvent(boolean disallowIntercept) {}
        });
    }

    // -----------------------------------------------------------------------
    // Mock 数据（与 TimelineActivity 保持一致，便于替换真实数据）
    // -----------------------------------------------------------------------

    private static final String[] PLACEHOLDER_IMAGES = {
        "https://xiaoxiaolanfeng-java-ai.oss-cn-beijing.aliyuncs.com/9977fb324f344996997b48081aecfae2.jpg",
        "https://xiaoxiaolanfeng-java-ai.oss-cn-beijing.aliyuncs.com/9977fb324f344996997b48081aecfae2.jpg",
        "https://xiaoxiaolanfeng-java-ai.oss-cn-beijing.aliyuncs.com/9977fb324f344996997b48081aecfae2.jpg",
        "https://xiaoxiaolanfeng-java-ai.oss-cn-beijing.aliyuncs.com/9977fb324f344996997b48081aecfae2.jpg",
        "https://xiaoxiaolanfeng-java-ai.oss-cn-beijing.aliyuncs.com/9977fb324f344996997b48081aecfae2.jpg",
        "https://xiaoxiaolanfeng-java-ai.oss-cn-beijing.aliyuncs.com/9977fb324f344996997b48081aecfae2.jpg",
    };

    /** 分类名称 → channel_id 映射，与后端约定保持一致 */
    private static final Map<String, Integer> CHANNEL_ID_MAP = new HashMap<String, Integer>() {{
        put("个性化护肤", 1);
        put("健身",       2);
        put("全龄心理健康", 3);
        put("避孕指南",   4);
    }};

    private void loadMockData() {
        // ── 左侧：4 个年龄段 ────────────────────────────────────────────────
        List<TimelineEntity> ageList = new ArrayList<>();
        ageList.add(new TimelineEntity(R.drawable.age_0_12,  "0~12岁",
                "初步形成两性意识，学会拒绝和求助", "幼年阶段"));
        ageList.add(new TimelineEntity(R.drawable.age_12_22, "12~22岁",
                "进入青春期，科学正视自己的身体，了解生理期，深入认识两性关系，正确护肤", "青春期阶段"));
        ageList.add(new TimelineEntity(R.drawable.age_22_55, "22~55岁",
                "用科学正确的方式守护身体健康，正确科学的避孕和备孕", "成熟阶段"));
        ageList.add(new TimelineEntity(R.drawable.age_55, "55岁以上",
                "步入更年期，身体会出现一系列变化，科学认知身体，守护健康；骨质疏松预防、心血管保健", "科学养护"));
        adapter.setItems(ageList);
        

        // ── 右侧：4 个分类 ──────────────────────────────────────────────────
        List<TimelineEntity> categoryList = new ArrayList<>();
        
        categoryList.add(new TimelineEntity(R.drawable.c_1, "个性化护肤",
                "根据肤质制定科学护肤方案，保持皮肤屏障健康", "护肤指南"));
        categoryList.add(new TimelineEntity(R.drawable.c_2, "健身",
                "顺应女性的身体结构和激素变化，科学健身", "健身指南"));
        categoryList.add(new TimelineEntity(R.drawable.c_3, "全龄心理健康",
                "每个年龄段都需要关注情绪管理与心理韧性的培养", "心理健康指南"));
        categoryList.add(new TimelineEntity(R.drawable.c_4, "避孕指南",
                "不同年龄段，不同身体状况，需要选择适合自身的避孕方式", "避孕指南"));
        categoryAdapter.setItems(categoryList);
        categoryAdapter.setChannelIdMap(CHANNEL_ID_MAP);
    }

    // -----------------------------------------------------------------------
    // 右侧分类 Adapter（内部类，复用 TimelineEntity 数据模型）
    // -----------------------------------------------------------------------

    private static class CategoryAdapter
            extends RecyclerView.Adapter<CategoryAdapter.VH> {

        private List<TimelineEntity>  items        = new ArrayList<>();
        private Map<String, Integer>  channelIdMap = new HashMap<>();

        void setItems(List<TimelineEntity> data) {
            this.items = data;
            notifyDataSetChanged();
        }

        void setChannelIdMap(Map<String, Integer> map) {
            this.channelIdMap = map;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_science_category, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            TimelineEntity item = items.get(position);
            holder.tvTitle.setText(item.getTitle());
            holder.tvDesc.setText(item.getDescription());

            if (item.hasLocalDrawable()) {
                holder.ivCover.setImageResource(item.getDrawableResId());
            } else if (item.getImageUrl() != null && !item.getImageUrl().isEmpty()) {
                com.bumptech.glide.Glide.with(holder.ivCover.getContext())
                        .load(item.getImageUrl())
                        .placeholder(R.drawable.bg_category_cover)
                        .centerCrop()
                        .into(holder.ivCover);
            } else {
                holder.ivCover.setImageResource(R.drawable.bg_category_cover);
            }

            String title = item.getTitle();
            Integer channelId = channelIdMap.get(title);
            if (channelId != null) {
                final int id = channelId;
                holder.itemView.setOnClickListener(v -> {
                    Log.i(TAG, "科普大类点击: title=" + title + " -> channelId=" + id
                            + " 即将打开 ChannelArticleListActivity");
                    ChannelArticleListActivity.start(v.getContext(), title, id);
                });
            } else {
                holder.itemView.setOnClickListener(null);
            }
        }

        @Override
        public int getItemCount() { return items.size(); }

        static class VH extends RecyclerView.ViewHolder {
            final android.widget.ImageView ivCover;
            final android.widget.TextView  tvTitle;
            final android.widget.TextView  tvDesc;

            VH(@NonNull View v) {
                super(v);
                ivCover = v.findViewById(R.id.iv_category_cover);
                tvTitle = v.findViewById(R.id.tv_category_title);
                tvDesc  = v.findViewById(R.id.tv_category_desc);
                tvTitle.setBackground(null);
                tvDesc.setBackground(null);
            }
        }
    }
}
