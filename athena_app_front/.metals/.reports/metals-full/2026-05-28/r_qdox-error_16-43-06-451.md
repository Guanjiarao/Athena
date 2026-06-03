error id: file:///D:/aa/athena-zyj%20(2)/athena_app_front/app/src/main/java/com/whu/software/athena/PregnancyPrepFragment.java
file:///D:/aa/athena-zyj%20(2)/athena_app_front/app/src/main/java/com/whu/software/athena/PregnancyPrepFragment.java
### com.thoughtworks.qdox.parser.ParseException: syntax error @[436,10]

error in qdox parser
file content:
```java
offset: 18950
uri: file:///D:/aa/athena-zyj%20(2)/athena_app_front/app/src/main/java/com/whu/software/athena/PregnancyPrepFragment.java
text:
```scala
package com.whu.software.athena;

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

    /** 缁忔湡銆屾槸/鍚︺€嶇綉缁滆姹傞槻鎶栵細UI 绔嬪嵆鍝嶅簲锛岃姹傚湪鐢ㄦ埛鍋滄墜 1s 鍚庡彧鍙戞渶鍚庝竴娆?*/
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
                        Log.d(NET_TAG, "鏀跺埌璁板綍鏇存柊浜嬩欢: date=" + date);
                        refreshDailyRecordsAfterMutation(date, true);
                    });
                });
        // 鍏堢敤鏈湴缂撳瓨绔嬪嵆娓叉煋锛涗簯绔熀绾垮湪 onResume 寮哄埗鎷夊彇瑕嗗啓锛堝惈浠庡悗鍙拌繑鍥炪€佸绔悓姝ワ級
        refreshLocalPredictionsUI();
        fetchDailyRecord(String.format(Locale.US, "%04d-%02d-%02d", todayYear, todayMonth, todayDay));
        syncCycleStateForMonth(todayYear, todayMonth);
    }

    private void initTitleToItemIdMap() {
        titleToItemId.clear();
        titleToItemId.put("鐖辩埍", 2);
        titleToItemId.put("鐥囩姸", 3);
        titleToItemId.put("蹇冩儏", 4);
        titleToItemId.put("鐧藉甫", 5);
        titleToItemId.put("鍩虹浣撴俯", 6);
        titleToItemId.put("浣撻噸", 7);
        titleToItemId.put("鏃ヨ", 8);
        titleToItemId.put("濂戒範鎯?, 9);
        titleToItemId.put("渚夸究", 10);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (periodNetworkRunnable != null) {
            debounceHandler.removeCallbacks(periodNetworkRunnable);
            periodNetworkRunnable = null;
        }
        rvCalendar = null; calendarAdapter = null; actionListContainer = null;
        actionTitleViews.clear();
        actionBaseTitles.clear();
    }

    @Override
    public void onResume() {
        super.onResume();
        syncCycleStateForMonth(displayYear, displayMonth);
    }

    // 鈹€鈹€ 浜戠鍩虹嚎鍚屾 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€

    /**
     * GET /menstruation/latest 鈥?鎷夊彇浜戠鏈€杩戜竴娆＄湡瀹炵粡鏈熻褰曪紝
     * 寮哄埗瑕嗙洊鏈湴 CycleDataManager锛岀劧鍚庨噸缁樻棩鍘嗐€?
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
                Log.d(NET_TAG, "寮€濮嬭姹? " + request.url() + " Body: ");
                Response response = HTTP_CLIENT.newCall(request).execute();
                if (!response.isSuccessful() || response.body() == null) {
                    String errBody = response.body() != null ? response.body().string() : "";
                    Log.d(NET_TAG, "璇锋眰缁撴灉: " + response.code() + " Body: " + errBody);
                    return;
                }

                String raw = response.body().string();
                Log.d(NET_TAG, "璇锋眰缁撴灉: " + response.code() + " Body: " + raw);
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
                Log.e(TAG, "fetchLatestMenstruationRecord 寮傚父", e);
            }
        }).start();
    }

    // 鈹€鈹€ 鏈湴棰勬祴鍒锋柊锛堜箰瑙傛洿鏂版牳蹇冿級 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€

    /**
     * 浠庢湰鍦?CycleDataManager 璇诲彇缁忔湡鍙傛暟锛岃皟鐢?PeriodCalculator 璁＄畻棰勬祴缁撴灉锛?
     * 骞剁珛鍗虫洿鏂?Adapter 鐨勪笁涓娴嬮泦鍚堬紝鐬棿閲嶇粯鏃ュ巻鈥斺€旂粷涓嶇瓑寰呯綉缁溿€?
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

    // 鈹€鈹€ 鍒濆鍖?鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€

    private void syncCycleStateForMonth(int year, int month) {
        if (!isAdded()) return;
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
                boolean actualVisible = CycleDataManager.isActualPeriodVisible(requireContext());
                if (actualVisible) {
                    CycleDataManager.saveLastPeriodStart(
                            requireContext(),
                            resolveLastKnownActualCycleStart(finalMonthView, finalLatestCycle)
                    );
                } else if (!actualVisible) {
                    CycleDataManager.saveLastPeriodStart(requireContext(), null);
                }
                refreshLocalPredictionsUI();
                refreshPeriodCardUi();

                if (calendarAdapter != null && finalMonthView != null) {
                    List<LocalDate> actualDatesForRender = actualVisible
                            ? new ArrayList<>(finalMonthView.actualDates)
                            : new ArrayList<>();
                    calendarAdapter.updateCycleVisualsForMonth(
                            year,
                            month,
                            actualDatesForRender,
                            finalMonthView.predictedDates
                    );
                    HealthSyncManager.clearCalendarMonthDirty(requireContext(), year, month);
                }
            });
        }).start();
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
        tvMonthYear.setText(displayYear + "骞? + displayMonth + "鏈?);
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
                    CalendarDayAdapter.DAY_TYPE_NORMAL, isToday, isFuture, isToday ? "浠婂ぉ" : null));
        }
        calendarAdapter.setCells(cells);
    }

    // 鈹€鈹€ Action 鍒楄〃 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€

    private void initActionList(View root) {
        actionListContainer = root.findViewById(R.id.action_list_container);
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (ActionRow row : buildActionRows()) {
            actionListContainer.addView(inflateRow(inflater, row));
        }
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
            if ("鎴戞€€瀛曚簡".equals(row.title)) {
                if (getParentFragment() instanceof RecordFragment)
                    ((RecordFragment) getParentFragment()).switchToPregnancy();
            } else if ("鎺掑嵉璇曠焊".equals(row.title)) {
                startActivity(new android.content.Intent(getContext(), OvulationScanActivity.class));
            } else if ("鏃ヨ".equals(row.title)) {
                showDiaryBottomSheet();
            } else {
                Toast.makeText(getContext(), "鍓嶅線锛? + row.title, Toast.LENGTH_SHORT).show();
            }
        })@@;
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
            if ("鐖辩埍".equals(t)) {
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

        TextView btnYes      = v.findViewById(R.id.btn_yes);
        TextView btnNo       = v.findViewById(R.id.btn_no);
        TextView tvCycleEntry = v.findViewById(R.id.tv_cycle_settings_entry);

        if ("鏈堢粡鏉ヤ簡".equals(row.title)) {
            tvPeriodTitle = v.findViewById(R.id.action_title);
            tvPeriodMeta = tvCycleEntry;
            btnPeriodYes = btnYes; btnPeriodNo = btnNo;
            btnYes.setOnClickListener(view -> onPeriodPrimaryActionClicked());
            btnNo.setOnClickListener(view  -> onPeriodSecondaryActionClicked());
            tvCycleEntry.setVisibility(View.VISIBLE);
            tvCycleEntry.setOnClickListener(view ->
                    com.whu.software.athena.utils.CycleSettingsDialogHelper.show(
                            requireContext(), (periodDays, cycleDays, irregular) -> {
                                Toast.makeText(requireContext(), "缁忔湡璁剧疆宸蹭繚瀛?, Toast.LENGTH_SHORT).show();
                                refreshLocalPredictionsUI();
                            }));
            refreshPeriodCardUi();
        } else if ("鎺掑嵉鏃?.equals(row.title)) {
            btnYes.setOnClickListener(view ->
                    Toast.makeText(getContext(), "宸叉爣璁颁粖鏃ヤ负鎺掑嵉鏃?馃", Toast.LENGTH_SHORT).show());
            btnNo.setOnClickListener(view ->
                    Toast.makeText(getContext(), "宸插彇娑堟帓鍗垫棩鏍囪", Toast.LENGTH_SHORT).show());
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
                showGenericInputBottomSheet("璁板綍蹇冩儏", "璇锋弿杩颁粖澶╃殑蹇冩儏...", "key_mood_prep"));
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
        list.add(new ActionRow(R.drawable.ic_action_pregnant,       "鎴戞€€瀛曚簡",  RowType.ARROW));
        list.add(new ActionRow(R.drawable.ic_blood_drop,            "鏈堢粡鏉ヤ簡",  RowType.YESNO));
        list.add(new ActionRow(R.drawable.ic_action_sex,            "鐖辩埍",      RowType.ADD));
        list.add(new ActionRow(R.drawable.ic_action_ovulation_test, "鎺掑嵉璇曠焊",  RowType.ARROW));
        list.add(new ActionRow(R.drawable.ic_action_temp,           "鍩虹浣撴俯",  RowType.ADD));
        list.add(new ActionRow(R.drawable.ic_action_discharge,      "鐧藉甫",      RowType.ADD));
        list.add(new ActionRow(R.drawable.ic_action_follicle,       "鍗垫场鐩戞祴",  RowType.ADD));
        list.add(new ActionRow(R.drawable.ic_action_symptom,        "鐥囩姸",      RowType.ADD));
        list.add(new ActionRow(R.drawable.ic_action_mood,           "蹇冩儏",      RowType.MOOD));
        list.add(new ActionRow(R.drawable.ic_action_nutrition,      "钀ュ吇琛ュ厖",  RowType.ADD));
        list.add(new ActionRow(R.drawable.ic_action_weight,         "浣撻噸",      RowType.ADD));
        list.add(new ActionRow(R.drawable.ic_action_diary,          "鏃ヨ",      RowType.ARROW));
        list.add(new ActionRow(R.drawable.ic_action_habit,          "濂戒範鎯?,    RowType.HABIT));
        list.add(new ActionRow(R.drawable.ic_action_poop,           "渚夸究",      RowType.ADD));
        list.add(new ActionRow(R.drawable.ic_action_plan,           "璁″垝",      RowType.ADD));
        return list;
    }

    // 鈹€鈹€ 缁忔湡"鏄?鍚?涔愯鏇存柊 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€

    private boolean hasOngoingPeriodCycle() {
        if (!isAdded() || !CycleDataManager.isActualPeriodVisible(requireContext())) {
            return false;
        }
        if (todayMonthViewState != null) {
            return todayMonthViewState.todayInActualCycle;
        }
        CycleApiService.CycleSlice fallbackCycle = buildFallbackDisplayCycle();
        if (fallbackCycle == null) {
            return false;
        }
        LocalDate today = LocalDate.now();
        LocalDate startDate = resolveCycleDisplayStart(fallbackCycle);
        if (startDate == null) {
            return false;
        }
        LocalDate resolvedEndDate = resolveCycleDisplayEnd(fallbackCycle);
        if (resolvedEndDate != null) {
            return !today.isBefore(startDate) && !today.isAfter(resolvedEndDate);
        }
        return !today.isBefore(startDate);
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
        if (tvPeriodTitle == null || tvPeriodMeta == null || btnPeriodYes == null || btnPeriodNo == null) {
            return;
        }
        if (hasOngoingPeriodCycle()) {
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
            tvPeriodTitle.setText("\u7ecf\u671f\u8fdb\u884c\u4e2d");
            if (displayStartDate != null && displayDays > 0) {
                tvPeriodMeta.setText("\u5f00\u59cb\u4e8e " + displayStartDate + " \u00b7 \u5df2\u6301\u7eed " + displayDays + " \u5929");
            } else if (displayStartDate != null) {
                tvPeriodMeta.setText("\u5f00\u59cb\u4e8e " + displayStartDate);
            } else {
                tvPeriodMeta.setText("\u8f93\u5165\u6708\u7ecf\u89c4\u5f8b");
            }
            btnPeriodNo.setText("\u64a4\u9500");
            btnPeriodYes.setText("\u7ed3\u675f");
            btnPeriodYes.setTextColor(android.graphics.Color.WHITE);
            btnPeriodYes.setBackgroundResource(R.drawable.bg_period_btn_yes);
            btnPeriodNo.setTextColor(0xFFE5375A);
            btnPeriodNo.setBackgroundResource(R.drawable.bg_period_btn_yes_outline);
        } else {
            tvPeriodTitle.setText("\u6708\u7ecf\u6765\u4e86");
            tvPeriodMeta.setText("\u8f93\u5165\u6708\u7ecf\u89c4\u5f8b");
            btnPeriodNo.setText("\u4e0d\u662f");
            btnPeriodYes.setText("\u662f");
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

        boolean   previousActualVisible = CycleDataManager.isActualPeriodVisible(requireContext());
        int       periodDays = CycleDataManager.getPeriodDays(requireContext());
        LocalDate startDate  = LocalDate.of(startYear, startMonth, startDay);
        Calendar  cursor     = Calendar.getInstance();
        cursor.set(startYear, startMonth - 1, startDay);

        List<LocalDate> periodDates = new ArrayList<>();
        for (int i = 0; i < periodDays; i++) {
            int y = cursor.get(Calendar.YEAR);
            int m = cursor.get(Calendar.MONTH) + 1;
            int d = cursor.get(Calendar.DAY_OF_MONTH);
            periodDates.add(LocalDate.of(y, m, d));
            cursor.add(Calendar.DAY_OF_MONTH, 1);
        }

        // 1. 鎸佷箙鍖栧埌鏈湴
        CycleDataManager.saveLastPeriodStart(requireContext(), startDate);
        CycleDataManager.setActualPeriodVisible(requireContext(), true);

        // 2. 涔愯鏇存柊瀹為檯缁忔湡搴曡壊
        calendarAdapter.clearMenstruationActualDates();
        calendarAdapter.addMenstruationActualDates(periodDates);
        latestCycleState = new CycleApiService.LatestCycle();
        latestCycleState.startDate = startDate;
        latestCycleState.endDate = null;
        latestCycleState.durationDays = periodDays;
        latestCycleState.displayDurationDays = periodDays;
        latestCycleState.displayEndDate = periodDates.get(periodDates.size() - 1);
        latestCycleState.monthStartDate = periodDates.get(0);
        latestCycleState.monthEndDate = periodDates.get(periodDates.size() - 1);
        latestCycleContainsToday = periodDates.contains(LocalDate.now());
        applyOptimisticTodayMonthTruth(startDate, periodDates, periodDays);

        // 3. 绔嬪嵆閲嶇畻骞跺埛鏂伴娴嬭壊鍧楋紙涓嶇瓑缃戠粶锛?
        refreshLocalPredictionsUI();

        Toast.makeText(getContext(),
                "宸茶褰?" + startMonth + "/" + startDay + " 璧疯繛缁?" + periodDays + " 澶╃粡鏈?,
                Toast.LENGTH_SHORT).show();
        applyPeriodButtonState("鏄?);

        refreshPeriodCardUi();
        final String startDateStr = String.format(Locale.US, "%04d-%02d-%02d", startYear, startMonth, startDay);
        if (periodNetworkRunnable != null) {
            debounceHandler.removeCallbacks(periodNetworkRunnable);
        }
        periodNetworkRunnable = () -> {
            if (!isAdded()) return;
            HealthRecordSaver.postMenstruationStart(requireContext(), startDateStr, success -> {
                if (success && isAdded()) {
                    refreshDailyRecordsAfterMutation(startDateStr, true);
                } else if (isAdded()) {
                    rollbackCycleMutation(previousActualVisible, startDateStr);
                }
            });
        };
        debounceHandler.postDelayed(periodNetworkRunnable, PERIOD_NETWORK_DEBOUNCE_MS);
    }

    private void onPeriodEndClicked() {
        if (calendarAdapter == null) return;
        CalendarDayAdapter.DayCell selected = calendarAdapter.getSelectedCell();
        if (selected == null || selected.day <= 0) return;
        boolean previousActualVisible = CycleDataManager.isActualPeriodVisible(requireContext());

        LocalDate dateToRemove = LocalDate.of(selected.year, selected.month, selected.day);
        if (latestCycleState != null && latestCycleState.startDate != null
                && dateToRemove.isBefore(latestCycleState.startDate)) {
            Toast.makeText(getContext(), "缁撴潫鏃ユ湡涓嶈兘鏃╀簬寮€濮嬫棩鏈?, Toast.LENGTH_SHORT).show();
            return;
        }

        // 1. 涔愯鍒犻櫎瀹為檯缁忔湡搴曡壊
        if (latestCycleState != null) {
            latestCycleState.endDate = dateToRemove;
        }
        latestCycleContainsToday = false;
        applyOptimisticTodayMonthEnd(dateToRemove);

        // 2. 寮哄埗娓呯┖鏈湴鍩虹嚎锛岀珛鍗虫姽闄ら娴嬭壊鍧?
        CycleDataManager.saveLastPeriodStart(requireContext(),
                latestCycleState != null ? latestCycleState.startDate : null);
        refreshLocalPredictionsUI();

        applyPeriodButtonState("鍚?);

        Toast.makeText(getContext(), "宸插彇娑堣鏃ョ殑缁忔湡璁板綍", Toast.LENGTH_SHORT).show();

        refreshPeriodCardUi();
        final String endDate = String.format(Locale.US, "%04d-%02d-%02d",
                selected.year, selected.month, selected.day);
        if (periodNetworkRunnable != null) {
            debounceHandler.removeCallbacks(periodNetworkRunnable);
        }
        periodNetworkRunnable = () -> {
            if (!isAdded()) return;
            HealthRecordSaver.postMenstruationEnd(requireContext(), endDate, success -> {
                if (success && isAdded()) {
                    refreshDailyRecordsAfterMutation(endDate, true);
                } else if (isAdded()) {
                    rollbackCycleMutation(previousActualVisible, endDate);
                }
            });
        };
        debounceHandler.postDelayed(periodNetworkRunnable, PERIOD_NETWORK_DEBOUNCE_MS);
    }

    private void onPeriodNoClicked() {
        CycleDataManager.setActualPeriodVisible(requireContext(), false);
        latestCycleContainsToday = false;
        if (calendarAdapter != null) {
            calendarAdapter.clearMenstruationActualDates();
        }
        refreshPeriodCardUi();
        applyPeriodButtonState("鍚?);
        Toast.makeText(getContext(), "宸叉爣璁拌繖涓€澶╀笉鏄粡鏈熷紑濮嬫棩", Toast.LENGTH_SHORT).show();
    }

    private void revokeLatestPeriodCycle() {
        if (latestCycleState != null && latestCycleState.id == null) {
            if (periodNetworkRunnable != null) {
                debounceHandler.removeCallbacks(periodNetworkRunnable);
                periodNetworkRunnable = null;
            }
            latestCycleState = null;
            todayMonthViewState = null;
            todayMonthActualCycle = null;
            latestCycleContainsToday = null;
            CycleDataManager.saveLastPeriodStart(requireContext(), null);
            calendarAdapter.clearMenstruationActualDates();
            refreshLocalPredictionsUI();
            refreshPeriodCardUi();
            Toast.makeText(getContext(), "宸叉挙閿€鏈缁忔湡璁板綍", Toast.LENGTH_SHORT).show();
            return;
        }
        if (latestCycleState == null || latestCycleState.id == null) {
            Toast.makeText(getContext(), "褰撳墠娌℃湁鍙挙閿€鐨勭粡鏈熻褰?, Toast.LENGTH_SHORT).show();
            return;
        }
        long cycleId = latestCycleState.id;
        new Thread(() -> {
            try {
                CycleApiService.deleteCycleSync(requireContext(), cycleId);
                MAIN.post(() -> {
                    if (!isAdded()) return;
                    latestCycleState = null;
                    todayMonthViewState = null;
                    todayMonthActualCycle = null;
                    latestCycleContainsToday = null;
                    CycleDataManager.saveLastPeriodStart(requireContext(), null);
                    CycleDataManager.setActualPeriodVisible(requireContext(), true);
                    calendarAdapter.clearMenstruationActualDates();
                    refreshLocalPredictionsUI();
                    refreshPeriodCardUi();
                    HealthSyncManager.markInsightDirty(requireContext());
                    syncCycleStateForMonth(displayYear, displayMonth);
                    Toast.makeText(requireContext(), "宸叉挙閿€鏈缁忔湡璁板綍", Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                Log.e(TAG, "revokeLatestPeriodCycle failed", e);
                MAIN.post(() -> Toast.makeText(
                        requireContext(),
                        e.getMessage() == null ? "鎾ら攢澶辫触锛岃绋嶅悗鍐嶈瘯" : e.getMessage(),
                        Toast.LENGTH_SHORT
                ).show());
            }
        }).start();
    }

    // 鈹€鈹€ BottomSheet 杈呭姪 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€

    private void showDiaryBottomSheet() {
        if (!isAdded()) return;
        GenericInputBottomSheetFragment
                .newInstance("璁板綍鏃ヨ", "浠婂ぉ鍙戠敓浜嗕粈涔堢編濂界殑浜嬫儏...", "key_diary",
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
            case "鍩虹浣撴俯": showGenericInputBottomSheet("璁板綍鍩虹浣撴俯", "璇疯緭鍏ュ熀纭€浣撴俯锛堝 36.5锛?..", "key_bbt_prep");       break;
            case "鐧藉甫":    showGenericInputBottomSheet("璁板綍鐧藉甫",    "璇锋弿杩扮櫧甯︽儏鍐?..",             "key_discharge_prep"); break;
            case "鍗垫场鐩戞祴":showGenericInputBottomSheet("璁板綍鍗垫场鐩戞祴","璇峰～鍐欑洃娴嬬粨鏋滄垨澶囨敞...",        "key_follicle_prep");  break;
            case "鐥囩姸":    showGenericInputBottomSheet("璁板綍鐥囩姸",    "璇锋弿杩版偍鐨勭棁鐘?..",             "key_symptoms_prep");  break;
            case "钀ュ吇琛ュ厖":showGenericInputBottomSheet("璁板綍钀ュ吇琛ュ厖","璇疯褰曚粖鏃ヨ惀鍏昏ˉ鍏?..",          "key_nutrition_prep"); break;
            case "浣撻噸":    showGenericInputBottomSheet("璁板綍浣撻噸",    "璇疯緭鍏ヤ綋閲嶏紙kg锛?..",           "key_weight_prep");    break;
            case "渚夸究":    showGenericInputBottomSheet("璁板綍渚夸究",    "璇锋弿杩颁究渚挎儏鍐?..",             "key_poop_prep");      break;
            case "璁″垝":    showGenericInputBottomSheet("璁板綍璁″垝",    "璇峰～鍐欏瀛曡鍒掓垨澶囨敞...",        "key_plan_prep");      break;
            default:        showGenericInputBottomSheet("璁板綍" + actionTitle, "璇疯緭鍏ュ唴瀹?..",
                    "key_prep_" + actionTitle); break;
        }
    }

    // 鈹€鈹€ 缃戠粶璇锋眰锛堝渾鐐?& 鎸夐挳鐘舵€侊紝涓嶅奖鍝嶅簳鑹诧級 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€

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
                    // 鍙洿鏂板渾鐐癸紝缁濅笉鏀瑰簳鑹?
                    calendarAdapter.updateRecordMarksForMonth(year, month, dateList);
                });
            } catch (Exception e) { Log.e(TAG, "fetchMonthMarks 寮傚父", e); }
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
                        records.add(gson.fromJson(
                                dataArr.getJSONObject(i).toString(), HealthRecordEntity.class));
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
                        applyPeriodButtonState("鍚?);
                    } else {
                        applyPeriodButtonState(finalValue);
                    }
                    // 1) 鍒濆鍖栨寜璁板綍椤瑰垎缁勭粨鏋勶紙鍗曡鏀寔澶氭潯璁板綍锛?
                    Map<Integer, List<HealthRecordEntity>> groupedByItemId = new HashMap<>();
                    // 2) 閬嶅巻鍚庣杩斿洖璁板綍锛屾寜 itemId 瀹夊叏杩藉姞锛堢粷涓嶈鐩栵級
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
                    // 3) 浼犵粰鐜版湁 UI 娓叉煋
                    bindDailyRecordCards(groupedByItemId);
                });
            } catch (Exception e) { Log.e(TAG, "fetchDailyRecord 寮傚父", e); }
        }).start();
    }

    private void bindRecordTitleView(String title, TextView titleView) {
        int itemId = resolveRecordItemIdByTitle(title);
        if (itemId <= 0) return;
        actionTitleViews.put(itemId, titleView);
        actionBaseTitles.put(itemId, title);
    }

    private int resolveRecordItemIdByTitle(String title) {
        if ("鐖辩埍".equals(title)) return 2;
        if ("鐥囩姸".equals(title)) return 3;
        if ("蹇冩儏".equals(title)) return 4;
        if ("鐧藉甫".equals(title)) return 5;
        if ("鍩虹浣撴俯".equals(title)) return 6;
        if ("浣撻噸".equals(title)) return 7;
        if ("鏃ヨ".equals(title)) return 8;
        if ("濂戒範鎯?.equals(title)) return 9;
        if ("渚夸究".equals(title)) return 10;
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
            String pureTitle = titleText.split("锛?)[0];
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
                tvVal.setText(values.size() + "鏉?);
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
                .setTitle("浠婃棩" + title + "璁板綍")
                .setItems(items, null)
                .setPositiveButton("鍏抽棴", null)
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

    // 鈹€鈹€ 宸ュ叿鏂规硶 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€

    private void applyPeriodButtonState(String value) {
        if (btnPeriodYes == null || btnPeriodNo == null) return;
        if ("鏄?.equals(value)) {
            btnPeriodYes.setTextColor(android.graphics.Color.WHITE);
            btnPeriodYes.setBackgroundResource(R.drawable.bg_period_btn_yes);
            btnPeriodNo.setTextColor(0xFF999999);
            btnPeriodNo.setBackgroundResource(R.drawable.bg_period_btn_no);
        } else if ("鍚?.equals(value)) {
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

```

```



#### Error stacktrace:

```
com.thoughtworks.qdox.parser.impl.Parser.yyerror(Parser.java:2025)
	com.thoughtworks.qdox.parser.impl.Parser.yyparse(Parser.java:2147)
	com.thoughtworks.qdox.parser.impl.Parser.parse(Parser.java:2006)
	com.thoughtworks.qdox.library.SourceLibrary.parse(SourceLibrary.java:232)
	com.thoughtworks.qdox.library.SourceLibrary.parse(SourceLibrary.java:190)
	com.thoughtworks.qdox.library.SourceLibrary.addSource(SourceLibrary.java:94)
	com.thoughtworks.qdox.library.SourceLibrary.addSource(SourceLibrary.java:89)
	com.thoughtworks.qdox.library.SortedClassLibraryBuilder.addSource(SortedClassLibraryBuilder.java:162)
	com.thoughtworks.qdox.JavaProjectBuilder.addSource(JavaProjectBuilder.java:174)
	scala.meta.internal.mtags.JavaMtags.indexRoot(JavaMtags.scala:49)
	scala.meta.internal.mtags.MtagsIndexer.index(MtagsIndexer.scala:22)
	scala.meta.internal.mtags.MtagsIndexer.index$(MtagsIndexer.scala:21)
	scala.meta.internal.mtags.JavaMtags.index(JavaMtags.scala:39)
	scala.meta.internal.mtags.Mtags$.allToplevels(Mtags.scala:155)
	scala.meta.internal.metals.DefinitionProvider.fromMtags(DefinitionProvider.scala:372)
	scala.meta.internal.metals.DefinitionProvider.$anonfun$positionOccurrence$6(DefinitionProvider.scala:291)
	scala.Option.orElse(Option.scala:477)
	scala.meta.internal.metals.DefinitionProvider.$anonfun$positionOccurrence$1(DefinitionProvider.scala:291)
	scala.Option.flatMap(Option.scala:283)
	scala.meta.internal.metals.DefinitionProvider.positionOccurrence(DefinitionProvider.scala:276)
	scala.meta.internal.metals.MetalsLspService.$anonfun$definitionOrReferences$1(MetalsLspService.scala:1736)
	scala.Option.map(Option.scala:242)
	scala.meta.internal.metals.MetalsLspService.definitionOrReferences(MetalsLspService.scala:1732)
	scala.meta.internal.metals.MetalsLspService.$anonfun$definition$1(MetalsLspService.scala:965)
	scala.meta.internal.metals.CancelTokens$.future(CancelTokens.scala:38)
	scala.meta.internal.metals.MetalsLspService.definition(MetalsLspService.scala:964)
	scala.meta.internal.metals.WorkspaceLspService.definition(WorkspaceLspService.scala:511)
	scala.meta.metals.lsp.DelegatingScalaService.definition(DelegatingScalaService.scala:65)
	java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
	java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:77)
	java.base/jdk.internal.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)
	java.base/java.lang.reflect.Method.invoke(Method.java:568)
	org.eclipse.lsp4j.jsonrpc.services.GenericEndpoint.lambda$recursiveFindRpcMethods$0(GenericEndpoint.java:65)
	org.eclipse.lsp4j.jsonrpc.services.GenericEndpoint.request(GenericEndpoint.java:128)
	org.eclipse.lsp4j.jsonrpc.RemoteEndpoint.handleRequest(RemoteEndpoint.java:265)
	org.eclipse.lsp4j.jsonrpc.RemoteEndpoint.consume(RemoteEndpoint.java:195)
	org.eclipse.lsp4j.jsonrpc.json.StreamMessageProducer.handleMessage(StreamMessageProducer.java:189)
	org.eclipse.lsp4j.jsonrpc.json.StreamMessageProducer.listen(StreamMessageProducer.java:97)
	org.eclipse.lsp4j.jsonrpc.json.ConcurrentMessageProcessor.run(ConcurrentMessageProcessor.java:97)
	java.base/java.util.concurrent.Executors$RunnableAdapter.call(Executors.java:539)
	java.base/java.util.concurrent.FutureTask.run(FutureTask.java:264)
	java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1136)
	java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:635)
	java.base/java.lang.Thread.run(Thread.java:833)
```
#### Short summary: 

QDox parse error in file:///D:/aa/athena-zyj%20(2)/athena_app_front/app/src/main/java/com/whu/software/athena/PregnancyPrepFragment.java