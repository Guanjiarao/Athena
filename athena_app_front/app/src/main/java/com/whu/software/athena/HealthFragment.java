package com.whu.software.athena;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.whu.software.athena.cognition.CognitionModels.Action;
import com.whu.software.athena.cognition.CognitionModels.Home;
import com.whu.software.athena.cognition.CognitionRepository;
import com.whu.software.athena.cognition.CognitionRepositoryProvider;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Health is the default entry and renders the server-selected cognition priority. */
public class HealthFragment extends Fragment {

    private CognitionRepository repository;
    private TextView date;
    private TextView headline;
    private TextView summary;
    private TextView digestCount;
    private TextView topicTitle;
    private TextView actionText;
    private View topicCard;
    private View actionCard;
    private Home home;
    private TextView focusContext;
    private TextView focusResponse;
    private TextView recommendedContent;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_health, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        repository = CognitionRepositoryProvider.get(requireContext());
        date = view.findViewById(R.id.tv_health_date);
        headline = view.findViewById(R.id.tv_health_headline);
        summary = view.findViewById(R.id.tv_health_summary);
        digestCount = view.findViewById(R.id.tv_digest_count);
        topicTitle = view.findViewById(R.id.tv_primary_topic);
        actionText = view.findViewById(R.id.tv_next_action);
        topicCard = view.findViewById(R.id.card_primary_topic);
        actionCard = view.findViewById(R.id.card_next_action);
        focusContext = view.findViewById(R.id.tv_focus_context);
        focusResponse = view.findViewById(R.id.tv_focus_response);
        recommendedContent = view.findViewById(R.id.tv_recommended_content);
        recommendedContent.setOnClickListener(v -> openDemoArticle());
        date.setText(new SimpleDateFormat("M 月 d 日 EEEE", Locale.CHINA).format(new Date()));

        view.findViewById(R.id.btn_body_clues).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), MyCognitionActivity.class)));
        view.findViewById(R.id.btn_quick_record).setOnClickListener(v ->
                requireActivity().getSupportFragmentManager().beginTransaction()
                        .replace(R.id.nav_host_fragment, new RecordFragment())
                        .addToBackStack("health-record")
                        .commit());
        topicCard.setOnClickListener(v -> openTopic());
        actionCard.setOnClickListener(v -> openAction());
        view.findViewById(R.id.btn_cycle_tool).setOnClickListener(v -> openPeriod());
        view.findViewById(R.id.btn_history_tool).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), HealthHistoryActivity.class)));
        view.findViewById(R.id.btn_analysis_tool).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), CognitionAnalysisDemoActivity.class)));
        view.findViewById(R.id.btn_device_tool).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), DeviceDataDemoActivity.class)));
        showColdStartIfNeeded();
    }

    private void showColdStartIfNeeded() {
        android.content.SharedPreferences prefs = requireContext()
                .getSharedPreferences("athena_cognition_onboarding", android.content.Context.MODE_PRIVATE);
        String saved = prefs.getString("focus", "");
        if (!saved.isEmpty()) {
            renderFocus(saved);
            return;
        }
        String[] choices = {"经期是否规律", "某种不适是否正常", "情绪和周期是否有关",
                "避孕与性健康", "只是想更了解身体", "暂时不确定"};
        new AlertDialog.Builder(requireContext())
                .setTitle("最近你最想弄清哪件事？")
                .setSingleChoiceItems(choices, -1, (dialog, which) -> {
                    prefs.edit().putString("focus", choices[which]).apply();
                    renderFocus(choices[which]);
                    dialog.dismiss();
                })
                .setNegativeButton("暂时跳过", (dialog, which) -> {
                    prefs.edit().putString("focus", "暂时不确定").apply();
                    renderFocus("暂时不确定");
                })
                .show();
    }

    private void renderFocus(String focus) {
        focusContext.setText("你现在最想弄清：" + focus + "\n这只是关注方向，不是身体结论。");
        String action;
        String content;
        switch (focus) {
            case "经期是否规律":
                action = "今天可以确认一次经期日期，连续记录后再看是否形成个人规律。";
                content = "推荐：怎样理解周期长度 · 因为你正在关注周期规律";
                break;
            case "某种不适是否正常":
                action = "先记录出现时间、持续时长和影响程度，不急着给它下结论。";
                content = "推荐：身体不适应该记录什么 · 因为你想判断是否需要继续留意";
                break;
            case "情绪和周期是否有关":
                action = "今天记录一次睡眠和情绪，后续结合周期位置观察。";
                content = "推荐：周期与情绪观察指南 · 因为你正在观察时间上的联系";
                break;
            case "避孕与性健康":
                action = "先保存一个最想弄清的问题；这里不会默认你的伴侣或生育计划。";
                content = "推荐：避孕方式如何选择 · 因为你主动选择了这个关注方向";
                break;
            case "只是想更了解身体":
                action = "从今天最明显的一种感受开始，完成一次低负担记录。";
                content = "推荐：建立自己的身体观察方法 · 不是根据浏览历史推荐";
                break;
            default:
                action = "你可以先完成一次身体记录，或阅读一篇经过审核的基础内容。";
                content = "推荐：如何开始观察身体 · 不会据此生成健康结论";
        }
        focusResponse.setText(action);
        recommendedContent.setText(content);
    }

    private void openPeriod() {
        requireActivity().getSupportFragmentManager().beginTransaction()
                .replace(R.id.nav_host_fragment, new PeriodFragment())
                .addToBackStack("health-period")
                .commit();
    }

    private void openDemoArticle() {
        String html = "<h1>如何开始观察睡眠与情绪</h1>"
                + "<p>睡眠、压力、周期位置和日常事件都可能影响情绪。一次变化不能说明原因，连续记录能帮助你看见是否重复出现。</p>"
                + "<h2>可以先记录什么</h2><p>睡眠时长、醒来后的感受、下午的情绪，以及当天是否有明显压力事件。</p>"
                + "<h2>什么时候寻求帮助</h2><p>如果情绪变化持续影响生活，或出现伤害自己的想法，请及时联系专业机构或当地紧急支持。</p>";
        startActivity(new Intent(requireContext(), ArticleDetailActivity.class)
                .putExtra("blog_id", "demo-sleep-mood")
                .putExtra("title", "如何开始观察睡眠与情绪")
                .putExtra("article_content_html", html)
                .putExtra("article_author_name", "Athena 健康内容编辑部"));
    }

    @Override
    public void onResume() {
        super.onResume();
        if (repository != null) refresh();
    }

    private void refresh() {
        repository.getHome(new CognitionRepository.Callback<Home>() {
            @Override public void onSuccess(Home value) {
                if (!isAdded()) return;
                home = value;
                headline.setText(value.headline);
                summary.setText(value.summary);
                digestCount.setText(value.pendingDigestCount > 0
                        ? value.pendingDigestCount + " 份整理草稿等待确认"
                        : "没有待确认的整理草稿");
                topicCard.setVisibility(value.primaryTopic == null ? View.GONE : View.VISIBLE);
                if (value.primaryTopic != null) topicTitle.setText(value.primaryTopic.title + "\n" + value.primaryTopic.summary);
                actionCard.setVisibility(value.nextAction == null ? View.GONE : View.VISIBLE);
                if (value.nextAction != null) actionText.setText(value.nextAction.title + "\n" + value.nextAction.instruction);
            }
            @Override public void onError(String message) {
                if (isAdded()) Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void openTopic() {
        if (home == null || home.primaryTopic == null) return;
        startActivity(new Intent(requireContext(), CognitionTopicActivity.class)
                .putExtra(CognitionTopicActivity.EXTRA_TOPIC_ID, home.primaryTopic.topicId));
    }

    private void openAction() {
        Action action = home == null ? null : home.nextAction;
        if (action == null) return;
        startActivity(new Intent(requireContext(), CognitionFeedbackActivity.class)
                .putExtra(CognitionFeedbackActivity.EXTRA_ACTION_ID, action.actionId));
    }
}
