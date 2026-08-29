package com.whu.software.athena;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.gson.Gson;
import com.whu.software.athena.config.ApiConfig;
import com.whu.software.athena.entity.HealthRecordEntity;
import com.whu.software.athena.utils.CycleDataManager;
import com.whu.software.athena.utils.CycleApiService;
import com.whu.software.athena.utils.HealthSyncManager;
import com.whu.software.athena.utils.HealthRecordModeMapper;
import com.whu.software.athena.utils.HealthRecordSaver;
import com.whu.software.athena.utils.PeriodCalculator;
import com.whu.software.athena.utils.RecordActionExtraBinder;
import com.whu.software.athena.utils.TokenManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class PregnancyPrepFragment extends Fragment {
    private static final int CURRENT_MODE_TYPE = 2;

    private enum RowType { ARROW, ADD, YESNO, MOOD, HABIT }

    private static class ActionRow {
        final int     iconRes;
        final String  title;
        final RowType rowType;
        ActionRow(int iconRes, String title, RowType rowType) {
            this.iconRes = iconRes; this.title = title; this.rowType = rowType;
        }
    }

    private int displayYear, displayMonth;
    private int todayYear, todayMonth, todayDay;

    private TextView           tvMonthYear;
    private RecyclerView       rvCalendar;
    private CalendarDayAdapter calendarAdapter;
    private LinearLayout       actionListContainer;
    private TextView tvHealthStatusKicker;
    private TextView tvHealthStatusBadge;
    private TextView tvHealthStatusTitle;
    private TextView tvHealthStatusSubtitle;
    private TextView tvHealthStatusHint;
    private CycleStatusRingView cycleStatusRingView;

    private TextView btnPeriodYes;
    private TextView btnPeriodNo;
    private TextView btnOvulationYes;
    private TextView btnOvulationNo;
    private TextView tvPeriodTitle;
    private TextView tvPeriodMeta;
    @Nullable
    private CycleApiService.LatestCycle latestCycleState;
    @Nullable
    private CycleApiService.MonthView todayMonthViewState;
    @Nullable
    private CycleApiService.CycleSlice todayMonthActualCycle;
    @Nullable
    private Boolean latestCycleContainsToday;
    private final Map<Integer, TextView> actionTitleViews = new HashMap<>();
    private final Map<Integer, String> actionBaseTitles = new HashMap<>();
    private final Map<String, Integer> titleToItemId = new HashMap<>();
    private final Map<Integer, List<HealthRecordEntity>> groupedRecordsByModeType = new HashMap<>();

    /** 备孕状态「是/否」网络请求防抖：UI 立即响应，请求在用户停手 1s 后只发最后一次 */
    private final Handler debounceHandler = new Handler(Looper.getMainLooper());
    private Runnable periodNetworkRunnable;
    private static final long PERIOD_NETWORK_DEBOUNCE_MS = 1000L;

    private static final String TAG = "PregnancyPrepFragment";
    private static final String NET_TAG = "AthenaNet";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final OkHttpClient HTTP_CLIENT = new OkHttpClient.Builder()
            .connectTimeout(ApiConfig.CONNECT_TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(ApiConfig.READ_TIMEOUT, TimeUnit.SECONDS)
            .build();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_pregnancy_prep, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initTodayInfo();
        initTopTabs(view);
        initHealthStatusCard(view);
        initTitleToItemIdMap();
        initCalendar(view);
        initActionList(view);
        getParentFragmentManager().setFragmentResultListener(
                GenericInputBottomSheetFragment.REQUEST_KEY_RECORD_SAVED,
                getViewLifecycleOwner(),
                (requestKey, result) -> {
                    String date = result.getString(GenericInputBottomSheetFragment.BUNDLE_KEY_DATE, null);
                    if (date == null || date.isEmpty()) return;
                    MAIN.post(() -> {
                        if (!isAdded()) return;
                        Log.d(NET_TAG, "收到记录更新事件: date=" + date);
                        refreshDailyRecordsAfterMutation(date, true);
                    });
                });
        // 先用本地缓存立即渲染；云端基线在 onResume 强制拉取覆盖（含从后台返回、多端同步）
        refreshLocalPredictionsUI();
        fetchDailyRecord(String.format(Locale.US, "%04d-%02d-%02d", todayYear, todayMonth, todayDay));
        syncCycleStateForMonth(todayYear, todayMonth);
    }

    private void initTitleToItemIdMap() {
        titleToItemId.clear();
        titleToItemId.put("爱爱", 2);
        titleToItemId.put("症状", 3);
        titleToItemId.put("心情", 4);
        titleToItemId.put("白带", 5);
        titleToItemId.put("基础体温", 6);
        titleToItemId.put("体重", 7);
        titleToItemId.put("日记", 8);
        titleToItemId.put("好习惯", 9);
        titleToItemId.put("便便", 10);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (periodNetworkRunnable != null) {
            debounceHandler.removeCallbacks(periodNetworkRunnable);
            periodNetworkRunnable = null;
        }
        rvCalendar = null; calendarAdapter = null; actionListContainer = null;
        tvHealthStatusKicker = null;
        tvHealthStatusBadge = null;
        tvHealthStatusTitle = null;
        tvHealthStatusSubtitle = null;
        tvHealthStatusHint = null;
        cycleStatusRingView = null;
        actionTitleViews.clear();
        actionBaseTitles.clear();
    }

    @Override
    public void onResume() {
        super.onResume();
        syncCycleStateForMonth(displayYear, displayMonth);
    }

    // ── 云端基线同步 ─────────────────────────────────────────────────────────────

    /**
     * GET /menstruation/latest —— 拉取云端最近一次真实经期记录，
     * 强制覆盖本地 CycleDataManager，然后重绘日历。
     */
    private void fetchLatestMenstruationRecord() {
        if (!isAdded()) return;
        String token = TokenManager.getToken(requireContext());

        new Thread(() -> {
            try {
                Request request = new Request.Builder()
                        .url(ApiConfig.API_MENSTRUATION_LATEST)
                        .addHeader("Authorization", "Bearer " + token)
                        .get().build();
                Log.d(NET_TAG, "开始请求: " + request.url() + " Body: ");
                Response response = HTTP_CLIENT.newCall(request).execute();
                if (!response.isSuccessful() || response.body() == null) {
                    String errBody = response.body() != null ? response.body().string() : "";
                    Log.d(NET_TAG, "请求结果: " + response.code() + " Body: " + errBody);
                    return;
                }

                String raw = response.body().string();
                Log.d(NET_TAG, "请求结果: " + response.code() + " Body: " + raw);
                JSONObject root = new JSONObject(raw);
                if (root.optInt("code", -1) != 200) return;

                if (root.isNull("data")) {
                    MAIN.post(() -> {
                        if (!isAdded()) return;
                        CycleDataManager.saveLastPeriodStart(requireContext(), null);
                        refreshLocalPredictionsUI();
                    });
                    return;
                }

                JSONObject data = root.optJSONObject("data");
                if (data == null) {
                    MAIN.post(() -> {
                        if (!isAdded()) return;
                        CycleDataManager.saveLastPeriodStart(requireContext(), null);
                        refreshLocalPredictionsUI();
                    });
                    return;
                }

                String lastPeriodStartStr = data.optString("lastPeriodStart", null);
                if (lastPeriodStartStr == null || lastPeriodStartStr.isEmpty()) {
                    MAIN.post(() -> {
                        if (!isAdded()) return;
                        CycleDataManager.saveLastPeriodStart(requireContext(), null);
                        refreshLocalPredictionsUI();
                    });
                    return;
                }

                LocalDate cloudDate = LocalDate.parse(lastPeriodStartStr);

                MAIN.post(() -> {
                    if (!isAdded()) return;
                    CycleDataManager.saveLastPeriodStart(requireContext(), cloudDate);
                    refreshLocalPredictionsUI();
                });
            } catch (Exception e) {
                Log.e(TAG, "fetchLatestMenstruationRecord 异常", e);
            }
        }).start();
    }

    // ── 本地预测刷新（乐观更新核心） ───────────────────────────────────────────────────

    /**
     * 从本地 CycleDataManager 读取经期参数，调用 PeriodCalculator 计算预测结果，
     * 并立即更新 Adapter 的三个预测集合，瞬间重绘日历——绝不等待网络。
     */
    private void refreshLocalPredictionsUI() {
        if (calendarAdapter == null || !isAdded()) return;
        LocalDate lastStart  = CycleDataManager.getLastPeriodStart(requireContext());
        int       periodDays = CycleDataManager.getPeriodDays(requireContext());
        int       cycleDays  = CycleDataManager.getCycleDays(requireContext());

        PeriodCalculator.PredictionResult result =
                PeriodCalculator.calculate(lastStart, periodDays, cycleDays);

        calendarAdapter.updatePredictions(
                result.predictedPeriodDates,
                result.ovulationWindowDates,
                result.ovulationDayDates);
    }

    // ── 初始化 ─────────────────────────────────────────────────────────────

    private void syncCycleStateForMonth(int year, int month) {
        if (!isAdded()) return;
        Log.d(TAG, "syncCycleStateForMonth begin year=" + year
                + ", month=" + month
                + ", displayYear=" + displayYear
                + ", displayMonth=" + displayMonth
                + ", actualVisibleLocal=" + CycleDataManager.isActualPeriodVisible(requireContext())
                + ", lastPeriodStartLocal=" + CycleDataManager.getLastPeriodStart(requireContext())
                + ", latestCycleStateStart=" + (latestCycleState != null ? latestCycleState.startDate : null)
                + ", latestCycleStateEnd=" + (latestCycleState != null ? latestCycleState.endDate : null)
                + ", todayMonthViewStateTodayInActual=" + (todayMonthViewState != null && todayMonthViewState.todayInActualCycle));
        fetchMonthMarks(year, month);
        new Thread(() -> {
            CycleApiService.LatestCycle latestCycle = null;
            CycleApiService.CycleStats cycleStats = null;
            CycleApiService.MonthView monthView = null;
            try {
                latestCycle = CycleApiService.getLatestCycleSync(requireContext());
            } catch (Exception e) {
                Log.w(TAG, "sync latest cycle failed", e);
            }
            try {
                cycleStats = CycleApiService.getCycleStatsSync(requireContext());
            } catch (Exception e) {
                Log.w(TAG, "sync cycle stats failed", e);
            }
            try {
                monthView = CycleApiService.getMonthViewSync(requireContext(), year, month);
            } catch (Exception e) {
                Log.w(TAG, "sync month view failed", e);
            }

            CycleApiService.LatestCycle finalLatestCycle = latestCycle;
            CycleApiService.CycleStats finalCycleStats = cycleStats;
            CycleApiService.MonthView finalMonthView = monthView;
            MAIN.post(() -> {
                if (!isAdded()) return;
                latestCycleState = finalLatestCycle;
                if (year == todayYear && month == todayMonth) {
                    todayMonthViewState = finalMonthView;
                    todayMonthActualCycle = resolveMostRelevantActualCycle(finalMonthView);
                    latestCycleContainsToday = finalMonthView != null
                            ? finalMonthView.todayInActualCycle
                            : null;
                }
                boolean backendOngoing = finalMonthView != null && finalMonthView.todayInActualCycle;
                boolean localVisible = CycleDataManager.isActualPeriodVisible(requireContext());
                LocalDate localStart = CycleDataManager.getLastPeriodStart(requireContext());
                if (backendOngoing && !localVisible) {
                    CycleDataManager.setActualPeriodVisible(requireContext(), true);
                    CycleApiService.CycleSlice activeCycle = resolveMostRelevantActualCycle(finalMonthView);
                    localStart = activeCycle != null
                            ? activeCycle.startDate
                            : (finalLatestCycle != null ? finalLatestCycle.startDate : null);
                    CycleDataManager.saveLastPeriodStart(requireContext(), localStart);
                    localVisible = true;
                }

                List<LocalDate> actualDatesForRender = new ArrayList<>();
                List<LocalDate> predictedDatesForRender = new ArrayList<>();
                if (finalMonthView != null) {
                    if (finalMonthView.actualDates != null) actualDatesForRender.addAll(finalMonthView.actualDates);
                    if (finalMonthView.predictedDates != null) predictedDatesForRender.addAll(finalMonthView.predictedDates);
                }

                if (localVisible && localStart != null) {
                    List<LocalDate> safeActuals = new ArrayList<>();
                    for (LocalDate d : actualDatesForRender) {
                        if (d.isBefore(localStart)) safeActuals.add(d);
                    }
                    actualDatesForRender = safeActuals;

                    List<LocalDate> safePredicts = new ArrayList<>();
                    for (LocalDate d : predictedDatesForRender) {
                        if (d.isBefore(localStart)) safePredicts.add(d);
                    }
                    predictedDatesForRender = safePredicts;

                    actualDatesForRender.addAll(buildLocalActualDatesForRender(localStart, year, month));
                } else {
                    CycleDataManager.setActualPeriodVisible(requireContext(), false);
                    CycleDataManager.saveLastPeriodStart(
                            requireContext(),
                            resolveLastKnownActualCycleStart(finalMonthView, finalLatestCycle)
                    );
                }
                boolean actualVisible = CycleDataManager.isActualPeriodVisible(requireContext());
                Log.d(TAG, "syncCycleStateForMonth apply year=" + year
                        + ", month=" + month
                        + ", backendOngoing=" + backendOngoing
                        + ", localVisible=" + localVisible
                        + ", actualVisibleApplied=" + actualVisible
                        + ", localVisibleAfterApply=" + CycleDataManager.isActualPeriodVisible(requireContext())
                        + ", localLastStartAfterApply=" + CycleDataManager.getLastPeriodStart(requireContext())
                        + ", finalMonthViewActualCount=" + (finalMonthView != null ? finalMonthView.actualDates.size() : -1)
                        + ", finalMonthViewPredictedCount=" + (finalMonthView != null ? finalMonthView.predictedDates.size() : -1)
                        + ", actualDatesForRenderCount=" + actualDatesForRender.size()
                        + ", predictedDatesForRenderCount=" + predictedDatesForRender.size());
                refreshLocalPredictionsUI();
                refreshPeriodCardUi();

                if (calendarAdapter != null && finalMonthView != null) {
                    calendarAdapter.updateCycleVisualsForMonth(
                            year,
                            month,
                            actualDatesForRender,
                            predictedDatesForRender
                    );
                    HealthSyncManager.clearCalendarMonthDirty(requireContext(), year, month);
                }
            });
        }).start();
    }

    @NonNull
    private List<LocalDate> buildLocalActualDatesForRender(@NonNull LocalDate startDate, int year, int month) {
        int localDays = Math.max(0, CycleDataManager.getPeriodDays(requireContext()));
        List<LocalDate> actualDates = new ArrayList<>();
        for (int i = 0; i < localDays; i++) {
            LocalDate date = startDate.plusDays(i);
            if (date.getYear() == year && date.getMonthValue() == month) {
                actualDates.add(date);
            }
        }
        return actualDates;
    }

    private void initTodayInfo() {
        Calendar now = Calendar.getInstance();
        todayYear  = now.get(Calendar.YEAR);
        todayMonth = now.get(Calendar.MONTH) + 1;
        todayDay   = now.get(Calendar.DAY_OF_MONTH);
        displayYear = todayYear; displayMonth = todayMonth;
    }

    private void initTopTabs(View root) {
        root.findViewById(R.id.tab_period).setOnClickListener(v -> {
            if (getParentFragment() instanceof RecordFragment)
                ((RecordFragment) getParentFragment()).switchToPeriod();
        });
        root.findViewById(R.id.tab_pregnancy).setOnClickListener(v -> {
            if (getParentFragment() instanceof RecordFragment)
                ((RecordFragment) getParentFragment()).switchToPregnancy();
        });
    }

    private void initCalendar(View root) {
        tvMonthYear    = root.findViewById(R.id.tv_month_year);
        rvCalendar     = root.findViewById(R.id.rv_calendar);
        calendarAdapter = new CalendarDayAdapter();
        rvCalendar.setLayoutManager(new GridLayoutManager(requireContext(), 7));
        rvCalendar.setAdapter(calendarAdapter);

        root.<ImageButton>findViewById(R.id.btn_prev_month)
                .setOnClickListener(v -> navigateMonth(-1));
        root.<ImageButton>findViewById(R.id.btn_next_month)
                .setOnClickListener(v -> navigateMonth(+1));

        calendarAdapter.setOnDayClickListener(cell -> {
            String dateStr = String.format(Locale.US, "%04d-%02d-%02d",
                    cell.year, cell.month, cell.day);
            fetchDailyRecord(dateStr);
        });
        renderCalendar();
    }

    private void navigateMonth(int delta) {
        displayMonth += delta;
        if (displayMonth > 12) { displayMonth = 1;  displayYear++; }
        if (displayMonth < 1)  { displayMonth = 12; displayYear--; }
        renderCalendar();
        syncCycleStateForMonth(displayYear, displayMonth);
    }

    private void renderCalendar() {
        tvMonthYear.setText(displayYear + "年" + displayMonth + "月");
        List<CalendarDayAdapter.DayCell> cells = new ArrayList<>();
        Calendar cal = Calendar.getInstance();
        cal.set(displayYear, displayMonth - 1, 1);
        int firstDow = cal.get(Calendar.DAY_OF_WEEK) - 1;

        for (int i = 0; i < firstDow; i++) {
            cells.add(new CalendarDayAdapter.DayCell(0, displayYear, displayMonth,
                    CalendarDayAdapter.DAY_TYPE_EMPTY, false, false, null));
        }

        Calendar todayCal = Calendar.getInstance();
        todayCal.set(todayYear, todayMonth - 1, todayDay, 0, 0, 0);
        long todayMs = todayCal.getTimeInMillis();

        int maxDay = daysInMonth(displayYear, displayMonth);
        for (int d = 1; d <= maxDay; d++) {
            Calendar dayCal = Calendar.getInstance();
            dayCal.set(displayYear, displayMonth - 1, d, 0, 0, 0);
            boolean isToday  = (displayYear == todayYear && displayMonth == todayMonth && d == todayDay);
            boolean isFuture = dayCal.getTimeInMillis() > todayMs;
            cells.add(new CalendarDayAdapter.DayCell(d, displayYear, displayMonth,
                    CalendarDayAdapter.DAY_TYPE_NORMAL, isToday, isFuture, isToday ? "今天" : null));
        }
        calendarAdapter.setCells(cells);
    }

    // ── Action 列表 ─────────────────────────────────────────────────────────────

    private void initHealthStatusCard(View root) {
        tvHealthStatusKicker = root.findViewById(R.id.tv_health_status_kicker);
        tvHealthStatusBadge = root.findViewById(R.id.tv_health_status_badge);
        tvHealthStatusTitle = root.findViewById(R.id.tv_health_status_title);
        tvHealthStatusSubtitle = root.findViewById(R.id.tv_health_status_subtitle);
        tvHealthStatusHint = root.findViewById(R.id.tv_health_status_hint);
        cycleStatusRingView = root.findViewById(R.id.view_cycle_status_ring);
        refreshHealthStatusCard();
    }

    private void initActionList(View root) {
        actionListContainer = root.findViewById(R.id.action_list_container);
        if (actionListContainer == null) return;
        actionListContainer.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        addActionSectionHeader("今日优先记录");
        for (ActionRow row : buildActionRows()) {
            if ("我怀孕了".equals(row.title)) {
                addActionSectionHeader("更多记录");
            }
            View rowView = inflateRow(inflater, row);
            RecordActionReadingStyle.apply(rowView);
            actionListContainer.addView(rowView);
        }
    }

    private void addActionSectionHeader(String title) {
        TextView header = new TextView(requireContext());
        header.setText(title);
        header.setTextColor(0xFF80766F);
        header.setTextSize(12f);
        header.setLetterSpacing(0.08f);
        header.setPadding(dpToPx(18), dpToPx(16), dpToPx(18), dpToPx(6));
        actionListContainer.addView(header);
    }

    private View inflateRow(LayoutInflater inflater, ActionRow row) {
        switch (row.rowType) {
            case ARROW: return inflateArrowRow(inflater, row);
            case YESNO: return inflateYesNoRow(inflater, row);
            case MOOD:  return inflateMoodRow(inflater, row);
            case HABIT: return inflateHabitRow(inflater, row);
            case ADD: default: return inflateAddRow(inflater, row);
        }
    }

    private View inflateArrowRow(LayoutInflater inflater, ActionRow row) {
        View v = inflater.inflate(R.layout.item_record_action_arrow, actionListContainer, false);
        ((android.widget.ImageView) v.findViewById(R.id.action_icon)).setImageResource(row.iconRes);
        ((TextView) v.findViewById(R.id.action_title)).setText(row.title);
        v.setOnClickListener(view -> {
            if ("我怀孕了".equals(row.title)) {
                if (getParentFragment() instanceof RecordFragment) {
                    ((RecordFragment) getParentFragment()).switchToPregnancy();
                }
            } else if ("排卵试纸".equals(row.title)) {
                startActivity(new android.content.Intent(getContext(), OvulationScanActivity.class));
            } else if ("日记".equals(row.title)) {
                showDiaryBottomSheet();
            } else {
                Toast.makeText(getContext(), "前往：" + row.title, Toast.LENGTH_SHORT).show();
            }
        });
        return v;
    }

    private View inflateAddRow(LayoutInflater inflater, ActionRow row) {
        View v = inflater.inflate(R.layout.item_record_action, actionListContainer, false);
        ((android.widget.ImageView) v.findViewById(R.id.action_icon)).setImageResource(row.iconRes);
        TextView titleView = v.findViewById(R.id.action_title);
        titleView.setText(row.title);
        bindRecordTitleView(row.title, titleView);
        String t = row.title;
        v.findViewById(R.id.action_add_btn).setOnClickListener(view -> {
            if ("爱爱".equals(t)) {
                com.whu.software.athena.utils.SexRecordDialogHelper.show(
                        requireContext(),
                        (measure, time) -> {
                            String date = getOperateDateForRecord();
                            String recordValue = measure + " " + time;
                            HealthRecordSaver.postRecordSave(
                                    requireContext(),
                                    date,
                                    2,
                                    recordValue,
                                    CURRENT_MODE_TYPE,
                                    () -> {
                                        if (!isAdded()) return;
                                        String d = getOperateDateForRecord();
                                        refreshDailyRecordsAfterMutation(d, true);
                                    });
                        });
            } else {
                openPrepAddBottomSheet(t);
            }
        });
        return v;
    }

    private View inflateYesNoRow(LayoutInflater inflater, ActionRow row) {
        View v = inflater.inflate(R.layout.item_record_action_yesno, actionListContainer, false);
        ((android.widget.ImageView) v.findViewById(R.id.action_icon)).setImageResource(row.iconRes);
        ((TextView) v.findViewById(R.id.action_title)).setText(row.title);

        TextView btnYes = v.findViewById(R.id.btn_yes);
        TextView btnNo = v.findViewById(R.id.btn_no);
        TextView tvCycleEntry = v.findViewById(R.id.tv_cycle_settings_entry);

        if ("月经来了".equals(row.title)) {
            tvPeriodTitle = v.findViewById(R.id.action_title);
            tvPeriodMeta = tvCycleEntry;
            btnPeriodYes = btnYes;
            btnPeriodNo = btnNo;
            btnYes.setOnClickListener(view -> onPeriodPrimaryActionClicked());
            btnNo.setOnClickListener(view -> onPeriodSecondaryActionClicked());
            tvCycleEntry.setVisibility(View.VISIBLE);
            tvCycleEntry.setOnClickListener(view ->
                    com.whu.software.athena.utils.CycleSettingsDialogHelper.show(
                            requireContext(), (periodDays, cycleDays, irregular) -> {
                                Toast.makeText(requireContext(), "经期设置已保存", Toast.LENGTH_SHORT).show();
                                refreshLocalPredictionsUI();
                            }));
            refreshPeriodCardUi();
        } else if ("排卵日".equals(row.title)) {
            btnYes.setOnClickListener(view ->
                    Toast.makeText(getContext(), "已标记今日为排卵日", Toast.LENGTH_SHORT).show());
            btnNo.setOnClickListener(view ->
                    Toast.makeText(getContext(), "已取消排卵日标记", Toast.LENGTH_SHORT).show());
        }
        return v;
    }

    private View inflateMoodRow(LayoutInflater inflater, ActionRow row) {
        View v = inflater.inflate(R.layout.item_record_action, actionListContainer, false);
        ((android.widget.ImageView) v.findViewById(R.id.action_icon)).setImageResource(row.iconRes);
        TextView titleView = v.findViewById(R.id.action_title);
        titleView.setText(row.title);
        bindRecordTitleView(row.title, titleView);
        v.findViewById(R.id.action_extra_area).setVisibility(View.VISIBLE);
        RecordActionExtraBinder.bindMoodRow(v);
        v.findViewById(R.id.action_add_btn).setOnClickListener(view ->
                showGenericInputBottomSheet("记录心情", "请描述今天的心情...", "key_mood_prep"));
        return v;
    }

    private View inflateHabitRow(LayoutInflater inflater, ActionRow row) {
        View v = inflater.inflate(R.layout.item_record_action, actionListContainer, false);
        ((android.widget.ImageView) v.findViewById(R.id.action_icon)).setImageResource(row.iconRes);
        TextView titleView = v.findViewById(R.id.action_title);
        titleView.setText(row.title);
        bindRecordTitleView(row.title, titleView);
        LinearLayout extra = v.findViewById(R.id.action_extra_area);
        extra.setVisibility(View.VISIBLE);
        RecordActionExtraBinder.bindHabitRow(extra, requireContext());
        v.findViewById(R.id.action_add_btn).setVisibility(View.INVISIBLE);
        return v;
    }

    private List<ActionRow> buildActionRows() {
        List<ActionRow> list = new ArrayList<>();
        list.add(new ActionRow(R.drawable.ic_record_drop_outline,   "月经来了",  RowType.YESNO));
        list.add(new ActionRow(R.drawable.ic_action_ovulation_test, "排卵试纸",  RowType.ARROW));
        list.add(new ActionRow(R.drawable.ic_action_temp,           "基础体温",  RowType.ADD));
        list.add(new ActionRow(R.drawable.ic_action_sex,            "爱爱",      RowType.ADD));
        list.add(new ActionRow(R.drawable.ic_action_symptom,        "症状",      RowType.ADD));
        list.add(new ActionRow(R.drawable.ic_action_mood,           "心情",      RowType.MOOD));
        list.add(new ActionRow(R.drawable.ic_action_diary,          "日记",      RowType.ARROW));
        list.add(new ActionRow(R.drawable.ic_action_pregnant,       "我怀孕了",  RowType.ARROW));
        list.add(new ActionRow(R.drawable.ic_action_discharge,      "白带",      RowType.ADD));
        list.add(new ActionRow(R.drawable.ic_action_follicle,       "卵泡监测",  RowType.ADD));
        list.add(new ActionRow(R.drawable.ic_action_nutrition,      "营养补充",  RowType.ADD));
        list.add(new ActionRow(R.drawable.ic_action_weight,         "体重",      RowType.ADD));
        list.add(new ActionRow(R.drawable.ic_action_habit,          "好习惯",    RowType.HABIT));
        list.add(new ActionRow(R.drawable.ic_action_poop,           "便便",      RowType.ADD));
        list.add(new ActionRow(R.drawable.ic_action_plan,           "计划",      RowType.ADD));
        return list;
    }

    // ── 经期"是/否"乐观更新 ─────────────────────────────────────────────────────────────

    private void refreshHealthStatusCard() {
        if (tvHealthStatusKicker == null
                || tvHealthStatusBadge == null
                || tvHealthStatusTitle == null
                || tvHealthStatusSubtitle == null
                || tvHealthStatusHint == null) {
            return;
        }

        tvHealthStatusKicker.setText("备孕状态");
        tvHealthStatusBadge.setText("备孕模式");

        if (hasOngoingPeriodCycle()) {
            tvHealthStatusTitle.setText("经期进行中");
            tvHealthStatusSubtitle.setText("当前先关注身体恢复和舒适度");
            tvHealthStatusHint.setText("经期结束后，可以继续记录基础体温和排卵试纸。");
            updateHealthStatusMarker(0f);
            return;
        }

        int cycleDay = resolveCurrentCycleDay();
        if (cycleDay > 0) {
            int daysToOvulation = resolveEstimatedDaysToOvulation(cycleDay);
            int cycleDays = Math.max(1, CycleDataManager.getCycleDays(requireContext()));
            updateHealthStatusMarker((cycleDay - 1f) / Math.max(1, cycleDays - 1));
            tvHealthStatusTitle.setText(daysToOvulation <= 2 ? "接近易孕窗口" : "周期第 " + cycleDay + " 天");
            if (daysToOvulation >= 0) {
                tvHealthStatusSubtitle.setText("预计 " + daysToOvulation + " 天后排卵");
            } else {
                tvHealthStatusSubtitle.setText("本周期排卵窗口可能已过");
            }
            tvHealthStatusHint.setText("今天优先记录基础体温、排卵试纸和同房情况。");
        } else {
            updateHealthStatusMarker(0.5f);
            tvHealthStatusTitle.setText("准备建立备孕节律");
            tvHealthStatusSubtitle.setText("先记录最近一次月经开始日");
            tvHealthStatusHint.setText("记录 2-3 个周期后，Athena 会帮你整理易孕窗口。");
        }
    }

    private void updateHealthStatusMarker(float progress) {
        if (cycleStatusRingView == null) {
            return;
        }
        cycleStatusRingView.setProgress(progress);
    }

    private int resolveCurrentCycleDay() {
        if (!isAdded()) {
            return -1;
        }
        LocalDate startDate = latestCycleState != null && latestCycleState.startDate != null
                ? latestCycleState.startDate
                : CycleDataManager.getLastPeriodStart(requireContext());
        if (startDate == null) {
            return -1;
        }
        long elapsed = ChronoUnit.DAYS.between(startDate, LocalDate.now());
        if (elapsed < 0) {
            return -1;
        }
        int cycleDays = Math.max(1, CycleDataManager.getCycleDays(requireContext()));
        return (int) (elapsed % cycleDays) + 1;
    }

    private int resolveEstimatedDaysToOvulation(int cycleDay) {
        if (!isAdded() || cycleDay <= 0) {
            return -1;
        }
        int cycleDays = Math.max(1, CycleDataManager.getCycleDays(requireContext()));
        int ovulationDay = Math.max(1, cycleDays - 14);
        return ovulationDay - cycleDay;
    }

    private boolean hasOngoingPeriodCycle() {
        if (!isAdded()) {
            return false;
        }
        LocalDate today = LocalDate.now();
        boolean visibleLocal = CycleDataManager.isActualPeriodVisible(requireContext());
        Log.d(TAG, "hasOngoingPeriodCycle check today=" + today
                + ", visibleLocal=" + visibleLocal
                + ", todayMonthViewStateExists=" + (todayMonthViewState != null)
                + ", todayMonthInActual=" + (todayMonthViewState != null && todayMonthViewState.todayInActualCycle)
                + ", latestCycleStart=" + (latestCycleState != null ? latestCycleState.startDate : null)
                + ", latestCycleEnd=" + (latestCycleState != null ? latestCycleState.endDate : null)
                + ", localLastStart=" + CycleDataManager.getLastPeriodStart(requireContext()));
        if (latestCycleState != null && latestCycleState.startDate != null) {
            if (latestCycleState.endDate == null) {
                Log.d(TAG, "hasOngoingPeriodCycle result=true from latestCycleState open cycle");
                return true;
            }
            Log.d(TAG, "hasOngoingPeriodCycle result=false from latestCycleState closed cycle");
            return false;
        }
        if (todayMonthActualCycle != null && todayMonthActualCycle.startDate != null) {
            if (todayMonthActualCycle.endDate == null) {
                Log.d(TAG, "hasOngoingPeriodCycle result=true from todayMonthActualCycle open cycle");
                return true;
            }
            Log.d(TAG, "hasOngoingPeriodCycle result=false from todayMonthActualCycle closed cycle");
            return false;
        }
        if (todayMonthViewState != null) {
            CycleApiService.CycleSlice monthCycle = resolveMostRelevantActualCycle(todayMonthViewState);
            if (monthCycle != null && monthCycle.startDate != null) {
                if (monthCycle.endDate == null) {
                    Log.d(TAG, "hasOngoingPeriodCycle result=true from monthView open cycle");
                    return true;
                }
                Log.d(TAG, "hasOngoingPeriodCycle result=false from monthView closed cycle start=" + monthCycle.startDate
                        + ", end=" + monthCycle.endDate);
                return false;
            }
        }
        Log.d(TAG, "hasOngoingPeriodCycle result=false");
        return false;
    }

    private void onPeriodPrimaryActionClicked() {
        if (hasOngoingPeriodCycle()) {
            onPeriodEndClicked();
        } else {
            onPeriodYesClicked();
        }
    }

    private void onPeriodSecondaryActionClicked() {
        if (hasOngoingPeriodCycle()) {
            revokeLatestPeriodCycle();
        } else {
            onPeriodNoClicked();
        }
    }

    private void refreshPeriodCardUi() {
        refreshHealthStatusCard();
        if (tvPeriodTitle == null || tvPeriodMeta == null || btnPeriodYes == null || btnPeriodNo == null) {
            return;
        }
        boolean ongoing = hasOngoingPeriodCycle();
        Log.d(TAG, "refreshPeriodCardUi begin ongoing=" + ongoing
                + ", latestCycleStart=" + (latestCycleState != null ? latestCycleState.startDate : null)
                + ", latestCycleEnd=" + (latestCycleState != null ? latestCycleState.endDate : null)
                + ", todayMonthViewExists=" + (todayMonthViewState != null)
                + ", todayMonthActualCycleStart=" + (todayMonthActualCycle != null ? todayMonthActualCycle.startDate : null)
                + ", localVisible=" + CycleDataManager.isActualPeriodVisible(requireContext())
                + ", localLastStart=" + CycleDataManager.getLastPeriodStart(requireContext()));
        if (ongoing) {
            CycleApiService.CycleSlice displayCycle = todayMonthActualCycle != null
                    ? todayMonthActualCycle
                    : buildFallbackDisplayCycle();
            LocalDate displayStartDate = resolveCycleDisplayStart(displayCycle);
            int displayDays = resolveCycleDisplayDuration(displayCycle);
            if (displayDays <= 0 && displayStartDate != null) {
                LocalDate effectiveEndDate = LocalDate.now();
                LocalDate resolvedEndDate = resolveCycleDisplayEnd(displayCycle);
                if (resolvedEndDate != null && resolvedEndDate.isBefore(effectiveEndDate)) {
                    effectiveEndDate = resolvedEndDate;
                }
                displayDays = (int) (ChronoUnit.DAYS.between(displayStartDate, effectiveEndDate) + 1);
            }
            tvPeriodTitle.setText("经期进行中");
            if (displayStartDate != null && displayDays > 0) {
                tvPeriodMeta.setText("开始于 " + displayStartDate + " · 已持续 " + displayDays + " 天");
            } else if (displayStartDate != null) {
                tvPeriodMeta.setText("开始于 " + displayStartDate);
            } else {
                tvPeriodMeta.setText("输入月经规律");
            }
            btnPeriodNo.setText("撤销");
            btnPeriodYes.setText("结束");
            btnPeriodYes.setTextColor(android.graphics.Color.WHITE);
            btnPeriodYes.setBackgroundResource(R.drawable.bg_period_btn_yes);
            btnPeriodNo.setTextColor(0xFFE5375A);
            btnPeriodNo.setBackgroundResource(R.drawable.bg_period_btn_yes_outline);
        } else {
            tvPeriodTitle.setText("月经来了");
            tvPeriodMeta.setText("输入月经规律");
            btnPeriodNo.setText("不是");
            btnPeriodYes.setText("是");
            applyPeriodButtonState(null);
        }
    }

    private void onPeriodYesClicked() {
        if (calendarAdapter == null) return;
        CalendarDayAdapter.DayCell selected = calendarAdapter.getSelectedCell();
        int startYear, startMonth, startDay;
        if (selected != null && selected.day > 0) {
            startYear = selected.year; startMonth = selected.month; startDay = selected.day;
        } else {
            startYear = todayYear; startMonth = todayMonth; startDay = todayDay;
            displayYear = todayYear; displayMonth = todayMonth;
            renderCalendar();
        }

        int periodDays = CycleDataManager.getPeriodDays(requireContext());
        LocalDate startDate = LocalDate.of(startYear, startMonth, startDay);
        Log.d(TAG, "onPeriodYesClicked start selectedDate=" + startDate
                + ", displayYear=" + displayYear
                + ", displayMonth=" + displayMonth
                + ", periodDays=" + periodDays
                + ", previousVisible=" + CycleDataManager.isActualPeriodVisible(requireContext())
                + ", localLastStartBefore=" + CycleDataManager.getLastPeriodStart(requireContext())
                + ", todayMonthHasActual=" + (todayMonthViewState != null && todayMonthViewState.todayInActualCycle));
        Calendar cursor = Calendar.getInstance();
        cursor.set(startYear, startMonth - 1, startDay);

        List<LocalDate> periodDates = new ArrayList<>();
        for (int i = 0; i < periodDays; i++) {
            int y = cursor.get(Calendar.YEAR);
            int m = cursor.get(Calendar.MONTH) + 1;
            int d = cursor.get(Calendar.DAY_OF_MONTH);
            periodDates.add(LocalDate.of(y, m, d));
            cursor.add(Calendar.DAY_OF_MONTH, 1);
        }
        Log.d(TAG, "onPeriodYesClicked generatedDates=" + periodDates);

        CycleDataManager.saveLastPeriodStart(requireContext(), startDate);
        CycleDataManager.setActualPeriodVisible(requireContext(), true);
        Log.d(TAG, "onPeriodYesClicked local state after optimistic save actualVisible="
                + CycleDataManager.isActualPeriodVisible(requireContext())
                + ", lastStart=" + CycleDataManager.getLastPeriodStart(requireContext()));

        calendarAdapter.clearMenstruationActualDates();
        calendarAdapter.addMenstruationActualDates(periodDates);
        latestCycleState = new CycleApiService.LatestCycle();
        latestCycleState.id = null;
        latestCycleState.startDate = startDate;
        latestCycleState.endDate = null;
        latestCycleState.durationDays = periodDays;
        latestCycleState.displayDurationDays = periodDays;
        latestCycleState.displayEndDate = periodDates.get(periodDates.size() - 1);
        latestCycleState.monthStartDate = periodDates.get(0);
        latestCycleState.monthEndDate = periodDates.get(periodDates.size() - 1);
        latestCycleContainsToday = periodDates.contains(LocalDate.now());
        applyOptimisticTodayMonthTruth(startDate, periodDates, periodDays);

        refreshLocalPredictionsUI();

        Toast.makeText(getContext(),
                "已记录 " + startMonth + "/" + startDay + " 起连续 " + periodDays + " 天经期",
                Toast.LENGTH_SHORT).show();
        applyPeriodButtonState("是");

        refreshPeriodCardUi();
        final String startDateStr = String.format(Locale.US, "%04d-%02d-%02d", startYear, startMonth, startDay);
        if (periodNetworkRunnable != null) {
            debounceHandler.removeCallbacks(periodNetworkRunnable);
        }
        periodNetworkRunnable = () -> {
            if (!isAdded()) return;
            Log.d(TAG, "onPeriodYesClicked debounce fire startDateStr=" + startDateStr
                    + ", currentLocalVisible=" + CycleDataManager.isActualPeriodVisible(requireContext())
                    + ", currentLocalLastStart=" + CycleDataManager.getLastPeriodStart(requireContext()));
            HealthRecordSaver.postMenstruationStart(requireContext(), startDateStr, (success, message) -> {
                Log.d(TAG, "postMenstruationStart callback success=" + success
                        + ", anchorDate=" + startDateStr
                        + ", message=" + message
                        + ", localVisibleBeforeHandle=" + CycleDataManager.isActualPeriodVisible(requireContext())
                        + ", localLastStartBeforeHandle=" + CycleDataManager.getLastPeriodStart(requireContext())
                        + ", todayMonthViewExists=" + (todayMonthViewState != null)
                        + ", todayMonthActualCycleStart=" + (todayMonthActualCycle != null ? todayMonthActualCycle.startDate : null));
                if (success && isAdded()) {
                    refreshDailyRecordsAfterMutation(startDateStr, true);
                } else if (isAdded()) {
                    if (isDuplicateOpenCycleFailure(message)) {
                        Toast.makeText(requireContext(), "当前已有未结束的经期，请先结束", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(requireContext(), "记录失败，请稍后再试", Toast.LENGTH_SHORT).show();
                    }
                    Log.w(TAG, "postMenstruationStart failed, recover to ongoing cloud state, anchorDate=" + startDateStr);
                    recoverToOngoingCycleFromCloud();
                }
            });
        };
        debounceHandler.postDelayed(periodNetworkRunnable, PERIOD_NETWORK_DEBOUNCE_MS);
    }

    private void onPeriodEndClicked() {
        if (calendarAdapter == null) return;
        CalendarDayAdapter.DayCell selected = calendarAdapter.getSelectedCell();
        if (selected == null || selected.day <= 0) return;
        LocalDate dateToRemove = LocalDate.of(selected.year, selected.month, selected.day);
        Log.d(TAG, "onPeriodEndClicked selectedDate=" + dateToRemove
                + ", localVisibleBefore=" + CycleDataManager.isActualPeriodVisible(requireContext())
                + ", localLastStartBefore=" + CycleDataManager.getLastPeriodStart(requireContext())
                + ", latestCycleStart=" + (latestCycleState != null ? latestCycleState.startDate : null)
                + ", latestCycleEnd=" + (latestCycleState != null ? latestCycleState.endDate : null));
        if (latestCycleState != null && latestCycleState.startDate != null
                && dateToRemove.isBefore(latestCycleState.startDate)) {
            Toast.makeText(getContext(), "结束日期不能早于开始日期", Toast.LENGTH_SHORT).show();
            return;
        }

        if (latestCycleState != null) {
            latestCycleState.endDate = dateToRemove;
        }
        latestCycleContainsToday = false;
        applyOptimisticTodayMonthEnd(dateToRemove);

        CycleDataManager.saveLastPeriodStart(requireContext(),
                latestCycleState != null ? latestCycleState.startDate : null);
        refreshLocalPredictionsUI();

        applyPeriodButtonState("否");
        Toast.makeText(getContext(), "已取消该日的经期记录", Toast.LENGTH_SHORT).show();

        refreshPeriodCardUi();
        final String endDate = String.format(Locale.US, "%04d-%02d-%02d",
                selected.year, selected.month, selected.day);
        if (periodNetworkRunnable != null) {
            debounceHandler.removeCallbacks(periodNetworkRunnable);
        }
        periodNetworkRunnable = () -> {
            if (!isAdded()) return;
            Log.d(TAG, "onPeriodEndClicked debounce fire endDate=" + endDate
                    + ", currentLocalVisible=" + CycleDataManager.isActualPeriodVisible(requireContext())
                    + ", currentLocalLastStart=" + CycleDataManager.getLastPeriodStart(requireContext()));
            HealthRecordSaver.postMenstruationEnd(requireContext(), endDate, (success, message) -> {
                Log.d(TAG, "postMenstruationEnd callback success=" + success
                        + ", anchorDate=" + endDate
                        + ", message=" + message
                        + ", localVisibleBeforeHandle=" + CycleDataManager.isActualPeriodVisible(requireContext())
                        + ", localLastStartBeforeHandle=" + CycleDataManager.getLastPeriodStart(requireContext()));
                if (success && isAdded()) {
                    refreshDailyRecordsAfterMutation(endDate, true);
                } else if (isAdded()) {
                    Toast.makeText(requireContext(), "结束失败，请稍后再试", Toast.LENGTH_SHORT).show();
                    Log.w(TAG, "postMenstruationEnd failed, refresh from cloud instead of rollback, anchorDate=" + endDate);
                    recoverToOngoingCycleFromCloud();
                }
            });
        };
        debounceHandler.postDelayed(periodNetworkRunnable, PERIOD_NETWORK_DEBOUNCE_MS);
    }

    private void onPeriodNoClicked() {
        Log.d(TAG, "onPeriodNoClicked before localVisible=" + CycleDataManager.isActualPeriodVisible(requireContext())
                + ", localLastStart=" + CycleDataManager.getLastPeriodStart(requireContext())
                + ", latestCycleStart=" + (latestCycleState != null ? latestCycleState.startDate : null)
                + ", latestCycleEnd=" + (latestCycleState != null ? latestCycleState.endDate : null)
                + ", todayMonthActualCycleStart=" + (todayMonthActualCycle != null ? todayMonthActualCycle.startDate : null)
                + ", todayMonthActualCycleEnd=" + (todayMonthActualCycle != null ? todayMonthActualCycle.endDate : null));

        CalendarDayAdapter.DayCell selected = calendarAdapter != null ? calendarAdapter.getSelectedCell() : null;
        LocalDate targetDate = selected != null && selected.day > 0
                ? LocalDate.of(selected.year, selected.month, selected.day)
                : LocalDate.of(todayYear, todayMonth, todayDay);
        long remoteCycleId = resolveRemoteCycleIdForDate(targetDate);
        final int syncYear = displayYear;
        final int syncMonth = displayMonth;
        final Context context = requireContext();
        Log.d(TAG, "onPeriodNoClicked targetDate=" + targetDate + ", resolvedRemoteCycleId=" + remoteCycleId);

        if (remoteCycleId <= 0L) {
            CycleDataManager.setActualPeriodVisible(context, false);
            latestCycleContainsToday = false;
            if (calendarAdapter != null) {
                calendarAdapter.clearMenstruationActualDates();
            }
            Log.d(TAG, "onPeriodNoClicked after localVisible=" + CycleDataManager.isActualPeriodVisible(context)
                    + ", localLastStart=" + CycleDataManager.getLastPeriodStart(context));
            refreshPeriodCardUi();
            applyPeriodButtonState("否");
            Toast.makeText(getContext(), "已撤销", Toast.LENGTH_SHORT).show();
            MAIN.post(() -> {
                if (!isAdded()) return;
                syncCycleStateForMonth(syncYear, syncMonth);
            });
            return;
        }

        new Thread(() -> {
            try {
                Log.d(TAG, "onPeriodNoClicked remote delete begin cycleId=" + remoteCycleId);
                CycleApiService.deleteCycleSync(context, remoteCycleId);
                Log.d(TAG, "onPeriodNoClicked remote delete success cycleId=" + remoteCycleId);
                MAIN.post(() -> {
                    if (!isAdded()) return;
                    CycleDataManager.setActualPeriodVisible(context, false);
                    latestCycleContainsToday = false;
                    if (calendarAdapter != null) {
                        calendarAdapter.clearMenstruationActualDates();
                    }
                    refreshPeriodCardUi();
                    applyPeriodButtonState("否");
                    Toast.makeText(requireContext(), "已标记这一天不是经期开始日", Toast.LENGTH_SHORT).show();
                    syncCycleStateForMonth(syncYear, syncMonth);
                });
            } catch (Exception e) {
                Log.e(TAG, "onPeriodNoClicked remote delete failed cycleId=" + remoteCycleId, e);
                MAIN.post(() -> {
                    if (!isAdded()) return;
                    Toast.makeText(requireContext(), "撤销失败，请稍后再试", Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private void revokeLatestPeriodCycle() {
        Log.d(TAG, "revokeLatestPeriodCycle entry latestCycleState.id="
                + (latestCycleState != null ? latestCycleState.id : null)
                + ", latestCycleState.startDate=" + (latestCycleState != null ? latestCycleState.startDate : null)
                + ", latestCycleState.endDate=" + (latestCycleState != null ? latestCycleState.endDate : null)
                + ", todayMonthActualCycle.id=" + (todayMonthActualCycle != null ? todayMonthActualCycle.id : null)
                + ", todayMonthActualCycle.startDate=" + (todayMonthActualCycle != null ? todayMonthActualCycle.startDate : null)
                + ", todayMonthActualCycle.endDate=" + (todayMonthActualCycle != null ? todayMonthActualCycle.endDate : null)
                + ", todayMonthViewStateExists=" + (todayMonthViewState != null)
                + ", localVisible=" + CycleDataManager.isActualPeriodVisible(requireContext())
                + ", localLastStart=" + CycleDataManager.getLastPeriodStart(requireContext()));

        final int syncYear = displayYear;
        final int syncMonth = displayMonth;
        final Context context = requireContext();
        long cycleId = latestCycleState != null && latestCycleState.id != null ? latestCycleState.id : -1L;

        if (cycleId <= 0L) {
            Log.d(TAG, "revokeLatestPeriodCycle local branch, cycleId=" + cycleId);
            if (periodNetworkRunnable != null) {
                debounceHandler.removeCallbacks(periodNetworkRunnable);
                periodNetworkRunnable = null;
            }
            latestCycleState = null;
            todayMonthViewState = null;
            todayMonthActualCycle = null;
            latestCycleContainsToday = null;
            CycleDataManager.saveLastPeriodStart(context, null);
            CycleDataManager.setActualPeriodVisible(context, false);
            if (calendarAdapter != null) {
                calendarAdapter.clearMenstruationActualDates();
            }
            refreshLocalPredictionsUI();
            refreshPeriodCardUi();
            Toast.makeText(getContext(), "已撤销", Toast.LENGTH_SHORT).show();
            MAIN.post(() -> {
                if (!isAdded()) return;
                syncCycleStateForMonth(syncYear, syncMonth);
            });
            return;
        }

        Log.d(TAG, "revokeLatestPeriodCycle remote branch, cycleId=" + cycleId);
        new Thread(() -> {
            try {
                CycleApiService.deleteCycleSync(context, cycleId);
                MAIN.post(() -> {
                    if (!isAdded()) return;
                    latestCycleState = null;
                    todayMonthViewState = null;
                    todayMonthActualCycle = null;
                    latestCycleContainsToday = null;
                    CycleDataManager.saveLastPeriodStart(context, null);
                    CycleDataManager.setActualPeriodVisible(context, false);
                    if (calendarAdapter != null) {
                        calendarAdapter.clearMenstruationActualDates();
                    }
                    refreshLocalPredictionsUI();
                    refreshPeriodCardUi();
                    HealthSyncManager.markInsightDirty(context);
                    Toast.makeText(requireContext(), "已撤销", Toast.LENGTH_SHORT).show();
                    syncCycleStateForMonth(syncYear, syncMonth);
                });
            } catch (Exception e) {
                Log.e(TAG, "revokeLatestPeriodCycle failed", e);
                MAIN.post(() -> {
                    if (!isAdded()) return;
                    Toast.makeText(requireContext(), "撤销失败，请稍后再试", Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private void showDiaryBottomSheet() {
        if (!isAdded()) return;
        GenericInputBottomSheetFragment
                .newInstance("记录日记", "今天发生了什么美好的事情...", "key_diary",
                        getOperateDateForRecord(), 8, CURRENT_MODE_TYPE)
                .show(getParentFragmentManager(), "diary");
    }

    private void showGenericInputBottomSheet(String dialogTitle, String hint, String key) {
        if (!isAdded()) return;
        int itemId = resolveRecordItemIdByKey(key);
        GenericInputBottomSheetFragment
                .newInstance(dialogTitle, hint, key, getOperateDateForRecord(), itemId, CURRENT_MODE_TYPE)
                .show(getParentFragmentManager(), "generic_bs_" + key);
    }

    private int resolveRecordItemIdByKey(String key) {
        if ("key_symptoms_prep".equals(key)) return 3;
        if ("key_mood_prep".equals(key)) return 4;
        if ("key_discharge_prep".equals(key)) return 5;
        if ("key_bbt_prep".equals(key)) return 6;
        if ("key_weight_prep".equals(key)) return 7;
        if ("key_diary".equals(key)) return 8;
        if ("key_poop_prep".equals(key)) return 10;
        return 3;
    }

    private void openPrepAddBottomSheet(String actionTitle) {
        switch (actionTitle) {
            case "基础体温":
                showGenericInputBottomSheet("记录基础体温", "请输入基础体温（如 36.5）...", "key_bbt_prep");
                break;
            case "白带":
                showGenericInputBottomSheet("记录白带", "请描述白带情况...", "key_discharge_prep");
                break;
            case "卵泡监测":
                showGenericInputBottomSheet("记录卵泡监测", "请填写监测结果或备注...", "key_follicle_prep");
                break;
            case "症状":
                showGenericInputBottomSheet("记录症状", "请描述您的症状...", "key_symptoms_prep");
                break;
            case "营养补充":
                showGenericInputBottomSheet("记录营养补充", "请记录今日营养补充...", "key_nutrition_prep");
                break;
            case "体重":
                showGenericInputBottomSheet("记录体重", "请输入体重（kg）...", "key_weight_prep");
                break;
            case "便便":
                showGenericInputBottomSheet("记录便便", "请描述便便情况...", "key_poop_prep");
                break;
            case "计划":
                showGenericInputBottomSheet("记录计划", "请填写备孕计划或备注...", "key_plan_prep");
                break;
            default:
                showGenericInputBottomSheet("记录" + actionTitle, "请输入内容...", "key_prep_" + actionTitle);
                break;
        }
    }

    // ── 网络请求（圆点 & 按钮状态，不影响底色） ─────────────────────────────────────────────

    private void fetchMonthMarks(int year, int month) {
        if (!isAdded()) return;
        String token = TokenManager.getToken(requireContext());
        String url   = ApiConfig.API_RECORD_MARKS + "?year=" + year + "&month=" + month;

        new Thread(() -> {
            try {
                Request  request  = new Request.Builder().url(url)
                        .addHeader("Authorization", "Bearer " + token).get().build();
                Response response = HTTP_CLIENT.newCall(request).execute();
                if (!response.isSuccessful() || response.body() == null) return;

                JSONObject   root    = new JSONObject(response.body().string());
                if (root.optInt("code", -1) != 200) return;

                JSONArray    dataArr  = root.optJSONArray("data");
                List<String> dateList = new ArrayList<>();
                if (dataArr != null) {
                    for (int i = 0; i < dataArr.length(); i++) dateList.add(dataArr.getString(i));
                }

                MAIN.post(() -> {
                    if (!isAdded() || calendarAdapter == null) return;
                    // 只更新圆点，绝不改底色
                    calendarAdapter.updateRecordMarksForMonth(year, month, dateList);
                });
            } catch (Exception e) { Log.e(TAG, "fetchMonthMarks 异常", e); }
        }).start();
    }

    private void fetchDailyRecord(String date) {
        if (!isAdded()) return;
        String token = TokenManager.getToken(requireContext());
        String url   = ApiConfig.API_RECORD_DETAIL + "?date=" + date;

        new Thread(() -> {
            try {
                Request  request  = new Request.Builder().url(url)
                        .addHeader("Authorization", "Bearer " + token).get().build();
                Response response = HTTP_CLIENT.newCall(request).execute();
                if (!response.isSuccessful() || response.body() == null) return;

                JSONObject root = new JSONObject(response.body().string());
                if (root.optInt("code", -1) != 200) return;

                JSONArray                dataArr = root.optJSONArray("data");
                List<HealthRecordEntity> records = new ArrayList<>();
                if (dataArr != null) {
                    Gson gson = new Gson();
                    for (int i = 0; i < dataArr.length(); i++) {
                        HealthRecordEntity record = gson.fromJson(
                                dataArr.getJSONObject(i).toString(), HealthRecordEntity.class);
                        if (record != null) {
                            record.setModeType(HealthRecordModeMapper.toUiModeType(record.getModeType()));
                            records.add(record);
                        }
                    }
                }

                Map<Integer, List<HealthRecordEntity>> groupedRecords = new HashMap<>();
                for (HealthRecordEntity e : records) {
                    if (e == null) continue;
                    int modeType = e.getModeType();
                    List<HealthRecordEntity> list = groupedRecords.get(modeType);
                    if (list == null) {
                        list = new ArrayList<>();
                        groupedRecords.put(modeType, list);
                    }
                    list.add(e);
                }

                List<HealthRecordEntity> currentModeRecords = groupedRecords.get(CURRENT_MODE_TYPE);
                if (currentModeRecords == null) {
                    currentModeRecords = new ArrayList<>();
                }

                String periodValue = null;
                for (HealthRecordEntity e : currentModeRecords) {
                    if (e.getRecordItemId() == 1) { periodValue = e.getRecordValue(); break; }
                }

                final List<HealthRecordEntity> finalRecords = currentModeRecords;
                final String finalValue = periodValue;

                MAIN.post(() -> {
                    if (!isAdded()) return;
                    groupedRecordsByModeType.clear();
                    groupedRecordsByModeType.putAll(groupedRecords);
                    resetAllTvActionValues();
                    if (!CycleDataManager.isActualPeriodVisible(requireContext())) {
                        applyPeriodButtonState("否");
                    } else {
                        applyPeriodButtonState(finalValue);
                    }
                    // 1) 初始化按记录项分组结构（单行支持多条记录）
                    Map<Integer, List<HealthRecordEntity>> groupedByItemId = new HashMap<>();
                    // 2) 遍历后端返回记录，按 itemId 安全追加（绝不覆盖）
                    refreshPeriodCardUi();
                    for (HealthRecordEntity e : finalRecords) {
                        if (e == null) continue;
                        int itemId = e.getRecordItemId();
                        List<HealthRecordEntity> list = groupedByItemId.get(itemId);
                        if (list == null) {
                            list = new ArrayList<>();
                            groupedByItemId.put(itemId, list);
                        }
                        list.add(e);
                    }
                    // 3) 传给现有 UI 渲染
                    bindDailyRecordCards(groupedByItemId);
                });
            } catch (Exception e) { Log.e(TAG, "fetchDailyRecord 异常", e); }
        }).start();
    }

    private void bindRecordTitleView(String title, TextView titleView) {
        int itemId = resolveRecordItemIdByTitle(title);
        if (itemId <= 0) return;
        actionTitleViews.put(itemId, titleView);
        actionBaseTitles.put(itemId, title);
    }


    private long resolveRemoteCycleIdForDate(@NonNull LocalDate targetDate) {
        if (latestCycleState != null
                && latestCycleState.id != null
                && latestCycleState.id > 0L
                && latestCycleState.startDate != null) {
            LocalDate endDate = resolveLatestCycleEndDate();
            boolean coversTarget = !targetDate.isBefore(latestCycleState.startDate)
                    && (endDate == null || !targetDate.isAfter(endDate));
            if (coversTarget) {
                return latestCycleState.id;
            }
        }
        if (todayMonthActualCycle != null
                && todayMonthActualCycle.id != null
                && todayMonthActualCycle.id > 0L
                && todayMonthActualCycle.startDate != null) {
            LocalDate startDate = resolveCycleDisplayStart(todayMonthActualCycle);
            LocalDate endDate = resolveCycleDisplayEnd(todayMonthActualCycle);
            boolean coversTarget = startDate != null
                    && !targetDate.isBefore(startDate)
                    && (endDate == null || !targetDate.isAfter(endDate));
            if (coversTarget) {
                return todayMonthActualCycle.id;
            }
        }
        return -1L;
    }    private int resolveRecordItemIdByTitle(String title) {
        if ("爱爱".equals(title)) return 2;
        if ("症状".equals(title)) return 3;
        if ("心情".equals(title)) return 4;
        if ("白带".equals(title)) return 5;
        if ("基础体温".equals(title)) return 6;
        if ("体重".equals(title)) return 7;
        if ("日记".equals(title)) return 8;
        if ("好习惯".equals(title)) return 9;
        if ("便便".equals(title)) return 10;
        return -1;
    }

    private void bindDailyRecordCards(Map<Integer, List<HealthRecordEntity>> groupedRecords) {
        if (actionListContainer == null) return;
        for (int i = 0; i < actionListContainer.getChildCount(); i++) {
            View child = actionListContainer.getChildAt(i);
            TextView title = child.findViewById(R.id.action_title);
            TextView tvVal = child.findViewById(R.id.tv_action_value);
            View valuesScroll = child.findViewById(R.id.hsv_action_values);
            LinearLayout valuesContainer = child.findViewById(R.id.action_values_container);
            if (title == null || tvVal == null || valuesScroll == null || valuesContainer == null) continue;
            CharSequence cs = title.getText();
            if (cs == null) continue;
            String titleText = cs.toString();
            String pureTitle = titleText.split("：")[0];
            Integer itemId = titleToItemId.get(pureTitle);
            if (itemId == null) continue;
            List<HealthRecordEntity> values = groupedRecords.get(itemId);
            if (values != null && !values.isEmpty()) {
                valuesContainer.removeAllViews();
                List<String> history = new ArrayList<>();
                for (HealthRecordEntity entity : values) {
                    if (entity == null || entity.getRecordValue() == null) continue;
                    String value = entity.getRecordValue().trim();
                    if (value.isEmpty()) continue;
                    history.add(value);
                    TextView chip = new TextView(requireContext());
                    chip.setText(value);
                    chip.setTextSize(12f);
                    chip.setTextColor(android.graphics.Color.parseColor("#666666"));
                    chip.setSingleLine(true);
                    chip.setMaxEms(8);
                    chip.setEllipsize(android.text.TextUtils.TruncateAt.END);
                    chip.setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4));
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                    );
                    lp.setMarginEnd(dpToPx(6));
                    chip.setLayoutParams(lp);
                    chip.setBackgroundResource(R.drawable.bg_habit_chip_selected);
                    valuesContainer.addView(chip);
                }
                valuesScroll.setVisibility(history.isEmpty() ? View.GONE : View.VISIBLE);
                tvVal.setVisibility(View.VISIBLE);
                tvVal.setText(values.size() + "条");
                tvVal.setTextColor(android.graphics.Color.parseColor("#555555"));
                tvVal.setOnClickListener(v -> showHistoryDialog(pureTitle, history));
            } else {
                valuesContainer.removeAllViews();
                valuesScroll.setVisibility(View.GONE);
                tvVal.setOnClickListener(null);
                tvVal.setVisibility(View.GONE);
                tvVal.setText("");
            }
        }
    }

    private void resetAllTvActionValues() {
        if (actionListContainer == null) return;
        for (int i = 0; i < actionListContainer.getChildCount(); i++) {
            View child = actionListContainer.getChildAt(i);
            TextView tv = child.findViewById(R.id.tv_action_value);
            View valuesScroll = child.findViewById(R.id.hsv_action_values);
            LinearLayout valuesContainer = child.findViewById(R.id.action_values_container);
            if (tv != null) {
                tv.setOnClickListener(null);
                tv.setVisibility(View.GONE);
                tv.setText("");
            }
            if (valuesContainer != null) {
                valuesContainer.removeAllViews();
            }
            if (valuesScroll != null) {
                valuesScroll.setVisibility(View.GONE);
            }
        }
    }

    private void refreshDailyRecordsAfterMutation(String date, boolean refreshMarks) {
        if (!isAdded()) return;
        fetchDailyRecord(date);
        if (refreshMarks) {
            int[] ym = parseYearMonthFromDate(date);
            HealthSyncManager.markCycleMutation(requireContext(), date);
            syncCycleStateForMonth(ym[0], ym[1]);
            if (!isTodayMonth(ym[0], ym[1])) {
                syncCycleStateForMonth(todayYear, todayMonth);
            }
        }
    }

    private void showHistoryDialog(String title, List<String> records) {
        if (!isAdded() || getContext() == null) return;
        String[] items = records.toArray(new String[0]);
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("今日" + title + "记录")
                .setItems(items, null)
                .setPositiveButton("关闭", null)
                .show();
    }

    public String getOperateDateForRecord() {
        CalendarDayAdapter.DayCell selected = calendarAdapter != null ? calendarAdapter.getSelectedCell() : null;
        if (selected != null && selected.day > 0) {
            return String.format(Locale.US, "%04d-%02d-%02d", selected.year, selected.month, selected.day);
        }
        return String.format(Locale.US, "%04d-%02d-%02d", todayYear, todayMonth, todayDay);
    }

    private int[] parseYearMonthFromDate(String date) {
        try {
            LocalDate d = LocalDate.parse(date);
            return new int[]{d.getYear(), d.getMonthValue()};
        } catch (Exception e) {
            return new int[]{displayYear, displayMonth};
        }
    }

    private boolean isTodayMonth(int year, int month) {
        return year == todayYear && month == todayMonth;
    }

    private void rollbackCycleMutation(boolean previousActualVisible, @Nullable String anchorDate) {
        if (!isAdded()) {
            return;
        }
        CycleDataManager.setActualPeriodVisible(requireContext(), previousActualVisible);
        if (!previousActualVisible && calendarAdapter != null) {
            calendarAdapter.clearMenstruationActualDates();
        }
        if (anchorDate != null && !anchorDate.isEmpty()) {
            fetchDailyRecord(anchorDate);
        }
        syncCycleStateForMonth(displayYear, displayMonth);
        if (!isTodayMonth(displayYear, displayMonth)) {
            syncCycleStateForMonth(todayYear, todayMonth);
        }
    }

    private void refreshCycleStateFromCloud(@Nullable String anchorDate) {
        if (!isAdded()) {
            return;
        }
        Log.d(TAG, "refreshCycleStateFromCloud anchorDate=" + anchorDate
                + ", displayYear=" + displayYear
                + ", displayMonth=" + displayMonth
                + ", localVisible=" + CycleDataManager.isActualPeriodVisible(requireContext())
                + ", localLastStart=" + CycleDataManager.getLastPeriodStart(requireContext())
                + ", latestCycleStart=" + (latestCycleState != null ? latestCycleState.startDate : null)
                + ", latestCycleEnd=" + (latestCycleState != null ? latestCycleState.endDate : null));
        if (anchorDate != null && !anchorDate.isEmpty()) {
            fetchDailyRecord(anchorDate);
        }
        syncCycleStateForMonth(displayYear, displayMonth);
        if (!isTodayMonth(displayYear, displayMonth)) {
            syncCycleStateForMonth(todayYear, todayMonth);
        }
    }

    private void recoverToOngoingCycleFromCloud() {
        if (!isAdded()) {
            return;
        }
        if (displayYear != todayYear || displayMonth != todayMonth) {
            displayYear = todayYear;
            displayMonth = todayMonth;
            renderCalendar();
        }
        syncCycleStateForMonth(todayYear, todayMonth);
    }

    private boolean isDuplicateOpenCycleFailure(@Nullable String message) {
        if (message == null) {
            return false;
        }
        return message.contains("未结束的经期") || message.contains("重复开始");
    }

    private void applyOptimisticTodayMonthTruth(@NonNull LocalDate startDate,
                                                @NonNull List<LocalDate> actualDates,
                                                int durationDays) {
        List<LocalDate> currentMonthDates = new ArrayList<>();
        for (LocalDate date : actualDates) {
            if (date != null && isTodayMonth(date.getYear(), date.getMonthValue())) {
                currentMonthDates.add(date);
            }
        }
        if (currentMonthDates.isEmpty()) {
            return;
        }
        if (todayMonthViewState == null) {
            todayMonthViewState = new CycleApiService.MonthView();
        }
        todayMonthViewState.year = todayYear;
        todayMonthViewState.month = todayMonth;
        todayMonthViewState.actualDates = new ArrayList<>(currentMonthDates);
        todayMonthViewState.todayInActualCycle = currentMonthDates.contains(LocalDate.now());

        CycleApiService.CycleSlice cycle = new CycleApiService.CycleSlice();
        cycle.id = null;
        cycle.startDate = startDate;
        cycle.monthStartDate = currentMonthDates.get(0);
        cycle.monthEndDate = currentMonthDates.get(currentMonthDates.size() - 1);
        cycle.displayEndDate = cycle.monthEndDate;
        cycle.durationDays = durationDays;
        cycle.displayDurationDays = currentMonthDates.size();
        cycle.predicted = false;
        todayMonthActualCycle = cycle;
    }

    private void applyOptimisticTodayMonthEnd(@NonNull LocalDate endDate) {
        if (todayMonthViewState != null) {
            todayMonthViewState.todayInActualCycle = false;
        }
        if (todayMonthActualCycle != null) {
            todayMonthActualCycle.displayEndDate = endDate;
            todayMonthActualCycle.monthEndDate = endDate;
            LocalDate startDate = resolveCycleDisplayStart(todayMonthActualCycle);
            if (startDate != null && !endDate.isBefore(startDate)) {
                todayMonthActualCycle.displayDurationDays =
                        (int) (ChronoUnit.DAYS.between(startDate, endDate) + 1);
            }
        }
    }

    @Nullable
    private LocalDate resolveLastKnownActualCycleStart(@Nullable CycleApiService.MonthView monthView,
                                                       @Nullable CycleApiService.LatestCycle latestCycle) {
        if (latestCycle != null && latestCycle.startDate != null) {
            return latestCycle.startDate;
        }
        CycleApiService.CycleSlice cycle = resolveMostRelevantActualCycle(monthView);
        if (cycle != null && cycle.startDate != null) {
            return cycle.startDate;
        }
        if (monthView != null && !monthView.actualDates.isEmpty()) {
            return monthView.actualDates.get(0);
        }
        return null;
    }

    @Nullable
    private CycleApiService.CycleSlice resolveMostRelevantActualCycle(@Nullable CycleApiService.MonthView monthView) {
        if (monthView == null || monthView.actualCycleList == null || monthView.actualCycleList.isEmpty()) {
            return null;
        }
        LocalDate today = LocalDate.now();
        if (monthView.todayInActualCycle) {
            for (CycleApiService.CycleSlice cycle : monthView.actualCycleList) {
                LocalDate startDate = resolveCycleDisplayStart(cycle);
                LocalDate endDate = resolveCycleDisplayEnd(cycle);
                if (startDate != null && endDate != null
                        && !today.isBefore(startDate) && !today.isAfter(endDate)) {
                    return cycle;
                }
            }
        }
        CycleApiService.CycleSlice latestCycle = null;
        for (CycleApiService.CycleSlice cycle : monthView.actualCycleList) {
            if (cycle == null) {
                continue;
            }
            if (latestCycle == null) {
                latestCycle = cycle;
                continue;
            }
            LocalDate candidate = cycle.startDate;
            LocalDate current = latestCycle.startDate;
            if (candidate != null && (current == null || candidate.isAfter(current))) {
                latestCycle = cycle;
            }
        }
        return latestCycle;
    }

    @Nullable
    private CycleApiService.CycleSlice buildFallbackDisplayCycle() {
        if (latestCycleState == null) {
            return null;
        }
        CycleApiService.CycleSlice cycle = new CycleApiService.CycleSlice();
        cycle.id = latestCycleState.id;
        cycle.startDate = latestCycleState.startDate;
        cycle.endDate = latestCycleState.endDate;
        cycle.displayEndDate = latestCycleState.displayEndDate;
        cycle.durationDays = latestCycleState.durationDays;
        cycle.displayDurationDays = latestCycleState.displayDurationDays;
        cycle.cycleLength = latestCycleState.cycleLength;
        cycle.predicted = latestCycleState.predicted;
        cycle.monthStartDate = latestCycleState.monthStartDate;
        cycle.monthEndDate = latestCycleState.monthEndDate;
        return cycle;
    }

    @Nullable
    private LocalDate resolveCycleDisplayStart(@Nullable CycleApiService.CycleSlice cycle) {
        if (cycle == null) {
            return null;
        }
        return cycle.startDate != null ? cycle.startDate : cycle.monthStartDate;
    }

    @Nullable
    private LocalDate resolveCycleDisplayEnd(@Nullable CycleApiService.CycleSlice cycle) {
        if (cycle == null) {
            return null;
        }
        if (cycle.monthEndDate != null) {
            return cycle.monthEndDate;
        }
        if (cycle.displayEndDate != null) {
            return cycle.displayEndDate;
        }
        if (cycle.endDate != null) {
            return cycle.endDate;
        }
        LocalDate startDate = resolveCycleDisplayStart(cycle);
        Integer duration = cycle.displayDurationDays != null && cycle.displayDurationDays > 0
                ? cycle.displayDurationDays
                : cycle.durationDays;
        if (startDate != null && duration != null && duration > 0) {
            return startDate.plusDays(duration - 1L);
        }
        return null;
    }

    private int resolveCycleDisplayDuration(@Nullable CycleApiService.CycleSlice cycle) {
        if (cycle == null) {
            return 0;
        }
        if (cycle.displayDurationDays != null && cycle.displayDurationDays > 0) {
            return cycle.displayDurationDays;
        }
        LocalDate startDate = resolveCycleDisplayStart(cycle);
        LocalDate endDate = resolveCycleDisplayEnd(cycle);
        if (startDate != null && endDate != null && !endDate.isBefore(startDate)) {
            return (int) (ChronoUnit.DAYS.between(startDate, endDate) + 1);
        }
        return cycle.durationDays != null && cycle.durationDays > 0 ? cycle.durationDays : 0;
    }

    private List<LocalDate> sanitizeActualDatesForRender(CycleApiService.LatestCycle latestCycle,
                                                         List<LocalDate> backendDates,
                                                         int year,
                                                         int month,
                                                         int fallbackDurationDays) {
        List<LocalDate> safeDates = backendDates != null ? new ArrayList<>(backendDates) : new ArrayList<>();
        int maxReasonableDays = Math.max(8, fallbackDurationDays + 2);
        if (safeDates.size() <= maxReasonableDays) {
            return safeDates;
        }
        if (latestCycle == null || latestCycle.startDate == null) {
            return new ArrayList<>();
        }
        LocalDate endDate = latestCycle.endDate;
        if (endDate == null) {
            int duration = latestCycle.durationDays != null && latestCycle.durationDays > 0
                    ? latestCycle.durationDays
                    : Math.max(1, fallbackDurationDays);
            endDate = latestCycle.startDate.plusDays(duration - 1L);
        }
        List<LocalDate> bounded = new ArrayList<>();
        LocalDate cursor = latestCycle.startDate;
        while (!cursor.isAfter(endDate)) {
            if (cursor.getYear() == year && cursor.getMonthValue() == month) {
                bounded.add(cursor);
            }
            cursor = cursor.plusDays(1);
        }
        return bounded;
    }

    @Nullable
    private LocalDate resolveLatestCycleEndDate() {
        if (latestCycleState == null || latestCycleState.startDate == null) {
            return null;
        }
        if (latestCycleState.endDate != null) {
            return latestCycleState.endDate;
        }
        if (latestCycleState.durationDays != null && latestCycleState.durationDays > 0) {
            return latestCycleState.startDate.plusDays(latestCycleState.durationDays - 1L);
        }
        return null;
    }

    private int dpToPx(int dp) {
        if (!isAdded()) return dp;
        return (int) (dp * requireContext().getResources().getDisplayMetrics().density + 0.5f);
    }

    // ── 工具方法 ─────────────────────────────────────────────────────────────

    private void applyPeriodButtonState(String value) {
        if (btnPeriodYes == null || btnPeriodNo == null) return;
        if ("是".equals(value)) {
            btnPeriodYes.setTextColor(android.graphics.Color.WHITE);
            btnPeriodYes.setBackgroundResource(R.drawable.bg_period_btn_yes);
            btnPeriodNo.setTextColor(0xFF999999);
            btnPeriodNo.setBackgroundResource(R.drawable.bg_period_btn_no);
        } else if ("否".equals(value)) {
            btnPeriodNo.setTextColor(0xFFE5375A);
            btnPeriodNo.setBackgroundResource(R.drawable.bg_period_btn_yes_outline);
            btnPeriodYes.setTextColor(0xFF999999);
            btnPeriodYes.setBackgroundResource(R.drawable.bg_period_btn_no);
        } else {
            btnPeriodYes.setTextColor(android.graphics.Color.WHITE);
            btnPeriodYes.setBackgroundResource(R.drawable.bg_period_btn_yes);
            btnPeriodNo.setTextColor(0xFF999999);
            btnPeriodNo.setBackgroundResource(R.drawable.bg_period_btn_no);
        }
    }

    private static int daysInMonth(int year, int month) {
        Calendar cal = Calendar.getInstance();
        cal.set(year, month - 1, 1);
        return cal.getActualMaximum(Calendar.DAY_OF_MONTH);
    }
}
