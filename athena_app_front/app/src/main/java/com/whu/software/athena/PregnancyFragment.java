package com.whu.software.athena;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.widget.DatePicker;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.whu.software.athena.utils.HealthRecordSaver;
import com.whu.software.athena.utils.LocalPhotoManager;
import com.whu.software.athena.utils.RecordActionExtraBinder;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;
import android.graphics.drawable.ColorDrawable;
import android.view.Window;

/**
 * 怀孕模式 Fragment。
 *
 * <p><b>预产期驱动设计</b>
 * <ul>
 *   <li>首次进入时预产期未设置，日历只显示普通日历，底部文字提示"请先设置预产期"。</li>
 *   <li>点击"设置预产期"行弹出 MaterialDatePicker（只允许选未来日期），
 *       确认后保存到 SharedPreferences，立即刷新日历和信息栏。</li>
 *   <li>预产期行右侧副标签实时显示已设置的日期（或"去设置 >"）。</li>
 * </ul>
 *
 * <p><b>核心计算算法（严格遵循需求）</b>
 * <ul>
 *   <li>受孕首日 LMP = 预产期 - 280 天（40 × 7）</li>
 *   <li>某日孕天数 = (该日 0 点 - LMP 0 点) / 86400000</li>
 *   <li>孕周 = 孕天数 / 7（整除）</li>
 *   <li>孕早期：孕天数 0 ~ 97（第 0~13 周）→ 淡蓝</li>
 *   <li>孕中期：孕天数 98~195（第 14~27 周）→ 淡黄</li>
 *   <li>孕晚期：孕天数 196~280（第 28~40 周）→ 淡粉</li>
 *   <li>足月 = LMP + 259 天（第 37 周）</li>
 * </ul>
 *
 * <p><b>产检日联动</b>：选中某日 → 点"产检日 [是]" → 格子变淡粉描边。
 */
public class PregnancyFragment extends Fragment {

    private static final int CURRENT_MODE_TYPE = 3;

    // -----------------------------------------------------------------------
    // 常量
    // -----------------------------------------------------------------------
    private static final String PREFS_NAME   = "pregnancy_prefs";
    private static final String KEY_DUE_DATE = "due_date_ms";

    /** 孕期天数阈值 */
    private static final int DAYS_EARLY_END  = 97;   // 第 0~13 周末（含）
    private static final int DAYS_MID_END    = 195;  // 第 14~27 周末（含）
    private static final int DAYS_TERM       = 280;  // 足月上限（40 周）
    private static final int DAYS_FULL_TERM  = 259;  // 足月 = 第 37 周（LMP + 259）
    private static final long MS_PER_DAY     = 24L * 60 * 60 * 1000;
    private static final long DAYS_PREGNANCY = 280L; // 整个孕期天数

    // -----------------------------------------------------------------------
    // 列表行类型
    // -----------------------------------------------------------------------
    private enum RowType { DUE_DATE, YESNO, ADD, ARROW, MOOD, HABIT }

    private static class ActionRow {
        final int     iconRes;
        final String  title;
        final RowType rowType;

        ActionRow(int iconRes, String title, RowType rowType) {
            this.iconRes = iconRes;
            this.title   = title;
            this.rowType = rowType;
        }
    }

    // -----------------------------------------------------------------------
    // 状态
    // -----------------------------------------------------------------------

    /** 预产期 0 点时间戳（毫秒），0 表示未设置 */
    private long dueDateMs = 0L;

    /** 受孕首日（LMP）0 点时间戳 = dueDateMs - 280 × MS_PER_DAY */
    private long lmpMs = 0L;

    /** 产检日集合，key = "yyyy-M-d" */
    private final Set<String> checkupDays = new HashSet<>();

    // -----------------------------------------------------------------------
    // 日历状态
    // -----------------------------------------------------------------------
    private int displayYear;
    private int displayMonth; // 1~12

    private int todayYear;
    private int todayMonth;
    private int todayDay;

    // -----------------------------------------------------------------------
    // Views（所有 View 引用在 onDestroyView 时置 null）
    // -----------------------------------------------------------------------
    private TextView              tvMonthYear;
    private TextView              tvLunarDate;
    private TextView              tvPregnancyProgress;
    private TextView              tvPregnancyCountdown;
    private TextView              tvDueDateValue;  // 预产期行右侧副标签
    private TextView              tvHealthStatusKicker;
    private TextView              tvHealthStatusBadge;
    private TextView              tvHealthStatusTitle;
    private TextView              tvHealthStatusSubtitle;
    private TextView              tvHealthStatusHint;
    private View                  viewHealthStatusMarker;
    private RecyclerView          rvCalendar;
    private PregnancyCalendarAdapter calendarAdapter;
    private LinearLayout          actionListContainer;

    private ActivityResultLauncher<Uri> takePictureLauncher;
    private ActivityResultLauncher<String> cameraPermissionLauncher;
    private boolean pendingOpenGalleryAfterCapture;

    // -----------------------------------------------------------------------
    // Fragment 生命周期
    // -----------------------------------------------------------------------

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        cameraPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {
                    if (granted && isAdded()) {
                        startBellyCameraCapture();
                    } else if (isAdded()) {
                        Toast.makeText(requireContext(), "需要相机权限才能拍摄大肚照", Toast.LENGTH_SHORT).show();
                    }
                });
        takePictureLauncher = registerForActivityResult(
                new ActivityResultContracts.TakePicture(),
                success -> {
                    if (!isAdded()) return;
                    if (Boolean.TRUE.equals(success) && pendingOpenGalleryAfterCapture) {
                        startActivity(new Intent(requireContext(), PrivateGalleryActivity.class));
                    }
                    pendingOpenGalleryAfterCapture = false;
                });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_pregnancy, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initTodayInfo();
        loadDueDate();           // 从 SharedPreferences 读取已保存的预产期
        initTopTabs(view);
        initHealthStatusCard(view);
        initCalendar(view);
        updateBottomInfoBar();
        initActionList(view);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        rvCalendar          = null;
        calendarAdapter     = null;
        actionListContainer = null;
        tvMonthYear         = null;
        tvLunarDate         = null;
        tvPregnancyProgress = null;
        tvPregnancyCountdown = null;
        tvDueDateValue      = null;
        tvHealthStatusKicker = null;
        tvHealthStatusBadge = null;
        tvHealthStatusTitle = null;
        tvHealthStatusSubtitle = null;
        tvHealthStatusHint = null;
        viewHealthStatusMarker = null;
    }

    // -----------------------------------------------------------------------
    // 初始化
    // -----------------------------------------------------------------------

    private void initTodayInfo() {
        Calendar now = Calendar.getInstance();
        todayYear  = now.get(Calendar.YEAR);
        todayMonth = now.get(Calendar.MONTH) + 1;
        todayDay   = now.get(Calendar.DAY_OF_MONTH);
        displayYear  = todayYear;
        displayMonth = todayMonth;
    }

    /** 从 SharedPreferences 加载已保存的预产期，并更新 lmpMs */
    private void loadDueDate() {
        SharedPreferences prefs = requireContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        dueDateMs = prefs.getLong(KEY_DUE_DATE, 0L);
        recalcLmp();
    }

    /**
     * 保存预产期到 SharedPreferences，并更新 lmpMs 和全部 UI。
     *
     * @param newDueDateMs MaterialDatePicker 回调的 UTC 毫秒时间戳
     */
    private void saveDueDate(long newDueDateMs) {
        // MaterialDatePicker 返回 UTC 时间，需转为本地 0 点
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(newDueDateMs);
        cal.setTimeZone(TimeZone.getDefault());
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        dueDateMs = cal.getTimeInMillis();

        requireContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putLong(KEY_DUE_DATE, dueDateMs)
                .apply();

        recalcLmp();

        // 更新预产期行副标签
        if (tvDueDateValue != null) {
            tvDueDateValue.setText(formatDate(dueDateMs));
        }

        // 通知 Adapter 预产期已变更（传入新的 lmpMs 重建格子背景）
        if (calendarAdapter != null) {
            calendarAdapter.updateLmp(lmpMs);
        }

        // 重建日历格子（孕周、背景色）
        renderCalendar();
        updateBottomInfoBar();
    }

    /** 根据 dueDateMs 计算并缓存 lmpMs */
    private void recalcLmp() {
        if (dueDateMs > 0) {
            lmpMs = dueDateMs - DAYS_PREGNANCY * MS_PER_DAY;
        } else {
            lmpMs = 0L;
        }
    }

    // -----------------------------------------------------------------------
    // 核心计算算法
    // -----------------------------------------------------------------------

    /**
     * 计算指定日期对应的孕天数（从 LMP 当日算起，LMP 当日 = 第 0 天）。
     *
     * @param dayMs 目标日期 0 点时间戳
     * @return 孕天数（≥0），若 lmpMs 未设置或目标日在 LMP 之前则返回 -1
     */
    private int calcPregnancyDays(long dayMs) {
        if (lmpMs <= 0) return -1;
        long diffMs = dayMs - lmpMs;
        if (diffMs < 0) return -1;
        int days = (int) (diffMs / MS_PER_DAY);
        return days;
    }

    /**
     * 由孕天数得到孕期阶段枚举，供 Adapter 渲染背景色。
     * <ul>
     *   <li>0  ~ 97  → EARLY（淡蓝）</li>
     *   <li>98 ~ 195 → MID（淡黄）</li>
     *   <li>196~ 280 → LATE（淡粉）</li>
     *   <li>其他     → NONE</li>
     * </ul>
     */
    static int pregnancyStageFromDays(int days) {
        if (days < 0)               return STAGE_NONE;
        if (days <= DAYS_EARLY_END) return STAGE_EARLY;
        if (days <= DAYS_MID_END)   return STAGE_MID;
        if (days <= DAYS_TERM)      return STAGE_LATE;
        return STAGE_NONE;
    }

    static final int STAGE_NONE  = 0;
    static final int STAGE_EARLY = 1;
    static final int STAGE_MID   = 2;
    static final int STAGE_LATE  = 3;

    // -----------------------------------------------------------------------
    // 顶部 Tab
    // -----------------------------------------------------------------------

    private void initTopTabs(View root) {
        root.findViewById(R.id.tab_period).setOnClickListener(v -> {
            if (getParentFragment() instanceof RecordFragment) {
                ((RecordFragment) getParentFragment()).switchToPeriod();
            }
        });
        root.findViewById(R.id.tab_pregnancy_prep).setOnClickListener(v -> {
            if (getParentFragment() instanceof RecordFragment) {
                ((RecordFragment) getParentFragment()).switchToPregnancyPrep();
            }
        });
        root.findViewById(R.id.tab_pregnancy).setOnClickListener(v -> {
            // 当前已在怀孕页，无操作
        });
    }

    // -----------------------------------------------------------------------
    // 日历
    // -----------------------------------------------------------------------

    private void initHealthStatusCard(View root) {
        tvHealthStatusKicker = root.findViewById(R.id.tv_health_status_kicker);
        tvHealthStatusBadge = root.findViewById(R.id.tv_health_status_badge);
        tvHealthStatusTitle = root.findViewById(R.id.tv_health_status_title);
        tvHealthStatusSubtitle = root.findViewById(R.id.tv_health_status_subtitle);
        tvHealthStatusHint = root.findViewById(R.id.tv_health_status_hint);
        viewHealthStatusMarker = root.findViewById(R.id.view_health_status_marker);
        refreshPregnancyStatusCard();
    }

    private void refreshPregnancyStatusCard() {
        if (tvHealthStatusKicker == null
                || tvHealthStatusBadge == null
                || tvHealthStatusTitle == null
                || tvHealthStatusSubtitle == null
                || tvHealthStatusHint == null) {
            return;
        }

        tvHealthStatusKicker.setText("孕期状态");
        tvHealthStatusBadge.setText("怀孕模式");

        if (dueDateMs <= 0 || lmpMs <= 0) {
            tvHealthStatusTitle.setText("请先设置预产期");
            tvHealthStatusSubtitle.setText("设置后可显示孕周、阶段和倒计时");
            tvHealthStatusHint.setText("完善预产期后，Athena 会把孕期节律整理在这里。");
            updateHealthStatusMarker(0f);
            return;
        }

        Calendar todayCal = Calendar.getInstance();
        todayCal.set(todayYear, todayMonth - 1, todayDay, 0, 0, 0);
        todayCal.set(Calendar.MILLISECOND, 0);
        long todayMs = todayCal.getTimeInMillis();

        int pregDays = calcPregnancyDays(todayMs);
        if (pregDays < 0) {
            tvHealthStatusTitle.setText("孕期尚未开始");
            tvHealthStatusSubtitle.setText("请检查预产期设置是否准确");
            tvHealthStatusHint.setText("确认后，Athena 会重新计算孕周和阶段。");
            updateHealthStatusMarker(0f);
            return;
        }

        int weeks = pregDays / 7;
        int days = pregDays % 7;
        long fullTermMs = lmpMs + (long) DAYS_FULL_TERM * MS_PER_DAY;
        long daysToFullTerm = Math.max(0, (fullTermMs - todayMs) / MS_PER_DAY);
        long daysToDue = Math.max(0, (dueDateMs - todayMs) / MS_PER_DAY);

        tvHealthStatusTitle.setText("孕 " + weeks + " 周 + " + days + " 天");
        tvHealthStatusSubtitle.setText("当前处于" + resolvePregnancyStageLabel(pregDays));
        tvHealthStatusHint.setText("距离足月 " + daysToFullTerm + " 天 · 距预产期 " + daysToDue + " 天。");
        updateHealthStatusMarker(pregDays / (float) Math.max(1, DAYS_FULL_TERM));
    }

    private void updateHealthStatusMarker(float progress) {
        if (viewHealthStatusMarker == null) {
            return;
        }
        viewHealthStatusMarker.post(() -> {
            View parent = (View) viewHealthStatusMarker.getParent();
            if (parent == null) {
                return;
            }
            int travel = parent.getWidth() - viewHealthStatusMarker.getWidth();
            if (travel <= 0) {
                return;
            }
            float safeProgress = Math.max(0f, Math.min(1f, progress));
            viewHealthStatusMarker.animate()
                    .translationX((safeProgress - 0.5f) * travel)
                    .setDuration(360L)
                    .start();
        });
    }

    private String resolvePregnancyStageLabel(int pregDays) {
        if (pregDays <= DAYS_EARLY_END) {
            return "孕早期";
        }
        if (pregDays <= DAYS_MID_END) {
            return "孕中期";
        }
        return "孕晚期";
    }

    private void initCalendar(View root) {
        tvMonthYear          = root.findViewById(R.id.tv_month_year);
        tvLunarDate          = root.findViewById(R.id.tv_lunar_date);
        tvPregnancyProgress  = root.findViewById(R.id.tv_pregnancy_progress);
        tvPregnancyCountdown = root.findViewById(R.id.tv_pregnancy_countdown);
        rvCalendar           = root.findViewById(R.id.rv_calendar);

        calendarAdapter = new PregnancyCalendarAdapter(checkupDays, lmpMs);
        rvCalendar.setLayoutManager(new GridLayoutManager(requireContext(), 7));
        rvCalendar.setAdapter(calendarAdapter);

        root.<ImageButton>findViewById(R.id.btn_prev_month)
                .setOnClickListener(v -> navigateMonth(-1));
        root.<ImageButton>findViewById(R.id.btn_next_month)
                .setOnClickListener(v -> navigateMonth(+1));

        calendarAdapter.setOnDayClickListener(cell ->
                Toast.makeText(getContext(),
                        cell.month + "月" + cell.day + "日",
                        Toast.LENGTH_SHORT).show());

        renderCalendar();
    }

    private void navigateMonth(int delta) {
        displayMonth += delta;
        if (displayMonth > 12) { displayMonth = 1;  displayYear++; }
        if (displayMonth < 1)  { displayMonth = 12; displayYear--; }
        renderCalendar();
        updateBottomInfoBar();
    }

    /**
     * 重建当前显示月的格子列表，传入 LMP 驱动每格的孕周和孕期阶段。
     */
    private void renderCalendar() {
        tvMonthYear.setText(displayYear + "年" + displayMonth + "月");

        List<PregnancyCalendarAdapter.DayCell> cells = new ArrayList<>();

        Calendar cal = Calendar.getInstance();
        cal.set(displayYear, displayMonth - 1, 1);
        int firstDow = cal.get(Calendar.DAY_OF_WEEK) - 1;

        // 前置空格
        for (int i = 0; i < firstDow; i++) {
            cells.add(PregnancyCalendarAdapter.DayCell.empty(displayYear, displayMonth));
        }

        Calendar todayCal = Calendar.getInstance();
        todayCal.set(todayYear, todayMonth - 1, todayDay, 0, 0, 0);
        todayCal.set(Calendar.MILLISECOND, 0);
        long todayMs = todayCal.getTimeInMillis();

        int maxDay = daysInMonth(displayYear, displayMonth);
        for (int d = 1; d <= maxDay; d++) {
            Calendar dayCal = Calendar.getInstance();
            dayCal.set(displayYear, displayMonth - 1, d, 0, 0, 0);
            dayCal.set(Calendar.MILLISECOND, 0);
            long dayMs = dayCal.getTimeInMillis();

            boolean isToday  = (displayYear == todayYear
                    && displayMonth == todayMonth && d == todayDay);
            boolean isFuture = dayMs > todayMs;
            String  subLabel = isToday ? "今天" : null;

            // 孕天数 → 孕周（显示用）和孕期阶段（背景色用）
            int pregDays = calcPregnancyDays(dayMs);
            int pregWeek = (pregDays >= 0) ? pregDays / 7 : -1;
            int stage    = pregnancyStageFromDays(pregDays);

            cells.add(new PregnancyCalendarAdapter.DayCell(
                    d, displayYear, displayMonth, isToday, isFuture, subLabel, pregWeek, stage));
        }

        calendarAdapter.setCells(cells);
    }

    // -----------------------------------------------------------------------
    // 日历底部信息栏（由预产期动态驱动）
    // -----------------------------------------------------------------------

    private void updateBottomInfoBar() {
        refreshPregnancyStatusCard();
        if (tvLunarDate == null) return;

        // 农历占位（实际项目可接入农历库）
        tvLunarDate.setText("农历 " + displayYear + "年" + displayMonth + "月");

        if (dueDateMs <= 0 || lmpMs <= 0) {
            tvPregnancyProgress.setText("请先设置预产期");
            tvPregnancyCountdown.setText("—");
            return;
        }

        // 今天 0 点
        Calendar todayCal = Calendar.getInstance();
        todayCal.set(todayYear, todayMonth - 1, todayDay, 0, 0, 0);
        todayCal.set(Calendar.MILLISECOND, 0);
        long todayMs = todayCal.getTimeInMillis();

        // 今日孕天数
        int pregDays = calcPregnancyDays(todayMs);
        if (pregDays >= 0) {
            int weeks = pregDays / 7;
            int days  = pregDays % 7;
            tvPregnancyProgress.setText("孕 " + weeks + "周" + days + "天");
        } else {
            tvPregnancyProgress.setText("孕期尚未开始");
        }

        // 距足月天数 = (lmpMs + 259 天) - today
        long fullTermMs     = lmpMs + (long) DAYS_FULL_TERM * MS_PER_DAY;
        long daysToFullTerm = (fullTermMs - todayMs) / MS_PER_DAY;
        long daysToDue      = (dueDateMs  - todayMs) / MS_PER_DAY;
        if (daysToFullTerm < 0) daysToFullTerm = 0;
        if (daysToDue < 0)      daysToDue = 0;

        tvPregnancyCountdown.setText(
                "距宝宝足月 " + daysToFullTerm + " 天 · 距预产期 " + daysToDue + " 天");
    }

    // -----------------------------------------------------------------------
    // 产检日联动
    // -----------------------------------------------------------------------

    private void onCheckupYesClicked() {
        if (calendarAdapter == null) return;
        PregnancyCalendarAdapter.DayCell sel = calendarAdapter.getSelectedCell();
        if (sel == null || sel.day <= 0) {
            Toast.makeText(getContext(), "请先点选一个日期", Toast.LENGTH_SHORT).show();
            return;
        }
        String key = sel.year + "-" + sel.month + "-" + sel.day;
        checkupDays.add(key);
        calendarAdapter.notifyDataSetChanged();
        Toast.makeText(getContext(),
                sel.month + "/" + sel.day + " 已标记为产检日 🩺",
                Toast.LENGTH_SHORT).show();
    }

    private void onCheckupNoClicked() {
        if (calendarAdapter == null) return;
        PregnancyCalendarAdapter.DayCell sel = calendarAdapter.getSelectedCell();
        if (sel == null || sel.day <= 0) {
            Toast.makeText(getContext(), "请先点选一个日期", Toast.LENGTH_SHORT).show();
            return;
        }
        String key = sel.year + "-" + sel.month + "-" + sel.day;
        if (checkupDays.remove(key)) {
            calendarAdapter.notifyDataSetChanged();
            Toast.makeText(getContext(), "已取消该日产检标记", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(getContext(), "该日未被标记为产检日", Toast.LENGTH_SHORT).show();
        }
    }

    // -----------------------------------------------------------------------
    // 设置预产期弹窗（MaterialDatePicker）
    // -----------------------------------------------------------------------

    /**
     * 弹出底部滚轮日期选择器（iOS 闹钟风格）。
     *
     * <p>使用 {@link BottomSheetDialog} + 自定义布局
     * {@code dialog_bottom_date_picker.xml}，内含原生 spinner 模式 DatePicker。
     * 确认后复用 {@link #saveDueDate(long)} 完成所有计算和 UI 刷新。
     */
    private void showDueDatePicker() {
        // ── 1. 创建 BottomSheetDialog ─────────────────────────────────────
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());

        // 让 Dialog 背景透明，圆角由布局根 View 的 background 决定
        View contentView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_bottom_date_picker, null);
        dialog.setContentView(contentView);

        // 去掉 BottomSheetDialog 默认的白色方形背景，防止遮住圆角
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(
                    new ColorDrawable(android.graphics.Color.TRANSPARENT));
        }

        // ── 2. 取出 DatePicker，设置初始日期 ─────────────────────────────
        DatePicker datePicker = contentView.findViewById(R.id.date_picker);

        Calendar initCal = Calendar.getInstance();
        if (dueDateMs > 0) {
            initCal.setTimeInMillis(dueDateMs);
        } else {
            // 未设置时，默认展示今天起 280 天后（约正常孕期末）
            initCal.add(Calendar.DAY_OF_MONTH, 280);
        }
        datePicker.updateDate(
                initCal.get(Calendar.YEAR),
                initCal.get(Calendar.MONTH),
                initCal.get(Calendar.DAY_OF_MONTH));

        // 限制：最早可选今天（不允许选过去）
        datePicker.setMinDate(System.currentTimeMillis());

        // ── 3. 取消 / 确定 按钮 ─────────────────────────────────────────
        contentView.findViewById(R.id.btn_cancel).setOnClickListener(v -> dialog.dismiss());

        contentView.findViewById(R.id.btn_confirm).setOnClickListener(v -> {
            // 从 DatePicker 读取选中的年月日，转为本地 0 点时间戳
            Calendar selected = Calendar.getInstance();
            selected.set(
                    datePicker.getYear(),
                    datePicker.getMonth(),       // 0-based，与 Calendar 一致
                    datePicker.getDayOfMonth(),
                    0, 0, 0);
            selected.set(Calendar.MILLISECOND, 0);

            saveDueDate(selected.getTimeInMillis());
            Toast.makeText(getContext(),
                    "预产期已设置：" + formatDate(dueDateMs),
                    Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        dialog.show();
    }

    // -----------------------------------------------------------------------
    // 列表初始化
    // -----------------------------------------------------------------------

    private void initActionList(View root) {
        actionListContainer = root.findViewById(R.id.action_list_container);
        if (actionListContainer == null) return;
        actionListContainer.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        addActionSectionHeader("今日优先记录");
        for (ActionRow row : buildActionRows()) {
            if ("hCG".equals(row.title)) {
                addActionSectionHeader("更多记录");
            }
            actionListContainer.addView(inflateRow(inflater, row));
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
            case DUE_DATE: return inflateDueDateRow(inflater, row);
            case ARROW:    return inflateArrowRow(inflater, row);
            case YESNO:    return inflateYesNoRow(inflater, row);
            case MOOD:     return inflateMoodRow(inflater, row);
            case HABIT:    return inflateHabitRow(inflater, row);
            case ADD:
            default:       return inflateAddRow(inflater, row);
        }
    }

    /**
     * 设置预产期行（箭头布局复用，右侧副标签实时显示已设日期）。
     */
    private View inflateDueDateRow(LayoutInflater inflater, ActionRow row) {
        View v = inflater.inflate(R.layout.item_record_action_arrow, actionListContainer, false);
        ((ImageView) v.findViewById(R.id.action_icon)).setImageResource(row.iconRes);
        ((TextView)  v.findViewById(R.id.action_title)).setText(row.title);

        // 复用箭头行的 action_arrow TextView 作为副标签（显示已设日期）
        tvDueDateValue = v.findViewById(R.id.action_arrow);
        tvDueDateValue.setText(dueDateMs > 0 ? formatDate(dueDateMs) : "去设置 ›");
        tvDueDateValue.setTextColor(dueDateMs > 0 ? 0xFFF06A82 : 0xFF8B8078);
        tvDueDateValue.setTextSize(12f);

        v.setOnClickListener(view -> showDueDatePicker());
        return v;
    }

    private View inflateArrowRow(LayoutInflater inflater, ActionRow row) {
        View v = inflater.inflate(R.layout.item_record_action_arrow, actionListContainer, false);
        ((ImageView) v.findViewById(R.id.action_icon)).setImageResource(row.iconRes);
        ((TextView)  v.findViewById(R.id.action_title)).setText(row.title);
        TextView tvHistory = v.findViewById(R.id.btn_row_history);
        String t = row.title;

        if ("大肚照".equals(t)) {
            tvHistory.setVisibility(View.VISIBLE);
            tvHistory.setOnClickListener(view -> openPrivateGallery());
            v.setOnClickListener(view -> launchBellyCamera());
        } else if ("孕期日记".equals(t)) {
            tvHistory.setVisibility(View.GONE);
            v.setOnClickListener(view -> showPregnancyDiarySheet());
        } else {
            tvHistory.setVisibility(View.GONE);
            v.setOnClickListener(view ->
                    Toast.makeText(getContext(), "前往：" + t, Toast.LENGTH_SHORT).show());
        }
        return v;
    }

    private void launchBellyCamera() {
        if (!isAdded()) return;
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
            return;
        }
        startBellyCameraCapture();
    }

    private void startBellyCameraCapture() {
        if (!isAdded()) return;
        pendingOpenGalleryAfterCapture = true;
        File f = LocalPhotoManager.getInstance().createNewPhotoFile(requireContext());
        Uri uri = LocalPhotoManager.getInstance().getUriForCamera(requireContext(), f);
        takePictureLauncher.launch(uri);
    }

    private void openPrivateGallery() {
        if (!isAdded()) return;
        startActivity(new Intent(requireContext(), PrivateGalleryActivity.class));
    }

    private void showPregnancyDiarySheet() {
        if (!isAdded()) return;
        GenericInputBottomSheetFragment.newInstance(
                        "记录日记", "今天发生了什么美好的事情...", "key_diary")
                .show(getParentFragmentManager(), "diary");
    }

    private View inflateAddRow(LayoutInflater inflater, ActionRow row) {
        View v = inflater.inflate(R.layout.item_record_action, actionListContainer, false);
        ((ImageView) v.findViewById(R.id.action_icon)).setImageResource(row.iconRes);
        ((TextView)  v.findViewById(R.id.action_title)).setText(row.title);
        String t = row.title;
        v.findViewById(R.id.action_add_btn).setOnClickListener(view -> {
            if ("爱爱".equals(t)) {
                com.whu.software.athena.utils.SexRecordDialogHelper.show(
                        requireContext(),
                        (measure, time) -> HealthRecordSaver.postRecordSave(
                                requireContext(),
                                getOperateDateForRecord(),
                                2,
                                measure + " " + time,
                                CURRENT_MODE_TYPE,
                                () -> {
                                    if (isAdded()) {
                                        Toast.makeText(requireContext(), "爱爱记录已保存", Toast.LENGTH_SHORT).show();
                                    }
                                })
                );
            } else {
                openPregnancyAddBottomSheet(t);
            }
        });
        return v;
    }

    private View inflateYesNoRow(LayoutInflater inflater, ActionRow row) {
        View v = inflater.inflate(R.layout.item_record_action_yesno, actionListContainer, false);
        ((ImageView) v.findViewById(R.id.action_icon)).setImageResource(row.iconRes);
        ((TextView)  v.findViewById(R.id.action_title)).setText(row.title);

        TextView btnYes = v.findViewById(R.id.btn_yes);
        TextView btnNo  = v.findViewById(R.id.btn_no);

        if ("产检日".equals(row.title)) {
            btnYes.setOnClickListener(view -> onCheckupYesClicked());
            btnNo .setOnClickListener(view -> onCheckupNoClicked());
        } else {
            String t = row.title;
            btnYes.setOnClickListener(view ->
                    Toast.makeText(getContext(), "是：" + t, Toast.LENGTH_SHORT).show());
            btnNo.setOnClickListener(view ->
                    Toast.makeText(getContext(), "否：" + t, Toast.LENGTH_SHORT).show());
        }
        return v;
    }

    private View inflateMoodRow(LayoutInflater inflater, ActionRow row) {
        View v = inflater.inflate(R.layout.item_record_action, actionListContainer, false);
        ((ImageView) v.findViewById(R.id.action_icon)).setImageResource(row.iconRes);
        ((TextView)  v.findViewById(R.id.action_title)).setText(row.title);
        v.findViewById(R.id.action_extra_area).setVisibility(View.VISIBLE);
        RecordActionExtraBinder.bindMoodRow(v);
        v.findViewById(R.id.action_add_btn).setOnClickListener(view ->
                showGenericInputBottomSheet("记录心情", "请描述今天的心情...", "key_mood_pregnancy"));
        return v;
    }

    private void showGenericInputBottomSheet(String dialogTitle, String hint, String key) {
        if (!isAdded()) return;
        GenericInputBottomSheetFragment sheet = GenericInputBottomSheetFragment.newInstance(dialogTitle, hint, key);
        sheet.show(getParentFragmentManager(), "generic_bs_" + key);
    }

    public String getOperateDateForRecord() {
        if (calendarAdapter != null) {
            PregnancyCalendarAdapter.DayCell selectedCell = calendarAdapter.getSelectedCell();
            if (selectedCell != null && selectedCell.day > 0) {
                return String.format(
                        Locale.US,
                        "%04d-%02d-%02d",
                        selectedCell.year,
                        selectedCell.month,
                        selectedCell.day
                );
            }
        }
        return String.format(Locale.US, "%04d-%02d-%02d", todayYear, todayMonth, todayDay);
    }

    /** 怀孕 Tab：带加号的列表项（除爱爱）打开通用输入抽屉。 */
    private void openPregnancyAddBottomSheet(String actionTitle) {
        switch (actionTitle) {
            case "营养补充":
                showGenericInputBottomSheet("记录营养补充", "请记录今日营养补充...", "key_nutrition_pregnancy");
                break;
            case "孕期血糖":
                showGenericInputBottomSheet("记录孕期血糖", "请输入血糖值或备注...", "key_blood_sugar_pregnancy");
                break;
            case "便便":
                showGenericInputBottomSheet("记录便便", "请描述便便情况...", "key_poop_pregnancy");
                break;
            case "症状":
                showGenericInputBottomSheet("记录症状", "请描述您的症状...", "key_symptoms_pregnancy");
                break;
            case "体温":
                showGenericInputBottomSheet("记录体温", "请输入体温（如 36.5）...", "key_temp_pregnancy");
                break;
            case "计划":
                showGenericInputBottomSheet("记录计划", "请填写计划或备注...", "key_plan_pregnancy");
                break;
            default:
                showGenericInputBottomSheet("记录" + actionTitle, "请输入内容...", "key_pregnancy_" + actionTitle);
                break;
        }
    }

    private View inflateHabitRow(LayoutInflater inflater, ActionRow row) {
        View v = inflater.inflate(R.layout.item_record_action, actionListContainer, false);
        ((ImageView) v.findViewById(R.id.action_icon)).setImageResource(row.iconRes);
        ((TextView)  v.findViewById(R.id.action_title)).setText(row.title);

        LinearLayout extra = v.findViewById(R.id.action_extra_area);
        extra.setVisibility(View.VISIBLE);
        RecordActionExtraBinder.bindHabitRow(extra, requireContext());
        v.findViewById(R.id.action_add_btn).setVisibility(View.INVISIBLE);
        return v;
    }

    // -----------------------------------------------------------------------
    // 列表行定义（第一项改为"设置预产期"）
    // -----------------------------------------------------------------------

    private List<ActionRow> buildActionRows() {
        List<ActionRow> list = new ArrayList<>();
        list.add(new ActionRow(R.drawable.ic_action_plan,            "设置预产期",  RowType.DUE_DATE));
        list.add(new ActionRow(R.drawable.ic_action_checkup,         "产检日",      RowType.YESNO));
        list.add(new ActionRow(R.drawable.ic_action_symptom,         "症状",        RowType.ADD));
        list.add(new ActionRow(R.drawable.ic_action_mood,            "心情",        RowType.MOOD));
        list.add(new ActionRow(R.drawable.ic_action_pregnancy_diary, "孕期日记",    RowType.ARROW));
        list.add(new ActionRow(R.drawable.ic_action_nutrition,       "营养补充",    RowType.ADD));
        list.add(new ActionRow(R.drawable.ic_action_blood_sugar,     "孕期血糖",    RowType.ADD));
        list.add(new ActionRow(R.drawable.ic_action_temp,            "体温",        RowType.ADD));
        list.add(new ActionRow(R.drawable.ic_action_hcg,             "hCG",         RowType.ARROW));
        list.add(new ActionRow(R.drawable.ic_action_progesterone,    "孕酮",        RowType.ARROW));
        list.add(new ActionRow(R.drawable.ic_action_habit,           "好习惯",      RowType.HABIT));
        list.add(new ActionRow(R.drawable.ic_action_poop,            "便便",        RowType.ADD));
        list.add(new ActionRow(R.drawable.ic_action_belly_photo,     "大肚照",      RowType.ARROW));
        list.add(new ActionRow(R.drawable.ic_action_sex,             "爱爱",        RowType.ADD));
        list.add(new ActionRow(R.drawable.ic_action_plan,            "计划",        RowType.ADD));
        return list;
    }

    // -----------------------------------------------------------------------
    // 工具
    // -----------------------------------------------------------------------

    private static int daysInMonth(int year, int month) {
        Calendar cal = Calendar.getInstance();
        cal.set(year, month - 1, 1);
        return cal.getActualMaximum(Calendar.DAY_OF_MONTH);
    }

    private static String formatDate(long ms) {
        return new SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(new Date(ms));
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    // -----------------------------------------------------------------------
    // 怀孕日历 Adapter
    // -----------------------------------------------------------------------

    /**
     * 怀孕模式日历 Adapter。
     *
     * <p>格子背景优先级：产检日 > 选中/今天描边 > 孕期色块 > 普通。
     * 孕周由外部传入每格的 {@link DayCell#pregWeek}（Fragment 已算好）。
     * 孕期阶段由 {@link DayCell#stage} 决定背景色（Fragment 算好后传入）。
     *
     * <p>当预产期更新时，调用 {@link #updateLmp(long)} 通知 Adapter 重算；
     * 由于 Fragment 已在 {@code renderCalendar()} 中重建全部 DayCell，
     * Adapter 的 lmpMs 字段仅备用。
     */
    public static class PregnancyCalendarAdapter
            extends RecyclerView.Adapter<PregnancyCalendarAdapter.PVH> {

        // ── DayCell ──────────────────────────────────────────────────────────

        public static class DayCell {
            public final int     day;
            public final int     year;
            public final int     month;
            public       boolean isSelected;
            public final boolean isFuture;
            public final String  subLabel;
            /** 孕周（0-42），未设置预产期或不在孕期范围内则为 -1 */
            public final int     pregWeek;
            /** 孕期阶段：STAGE_NONE / STAGE_EARLY / STAGE_MID / STAGE_LATE */
            public final int     stage;

            public DayCell(int day, int year, int month,
                           boolean isSelected, boolean isFuture,
                           String subLabel, int pregWeek, int stage) {
                this.day        = day;
                this.year       = year;
                this.month      = month;
                this.isSelected = isSelected;
                this.isFuture   = isFuture;
                this.subLabel   = subLabel;
                this.pregWeek   = pregWeek;
                this.stage      = stage;
            }

            /** 工厂：创建空格（前置填充用） */
            public static DayCell empty(int year, int month) {
                return new DayCell(0, year, month, false, false, null, -1, STAGE_NONE);
            }
        }

        public interface OnDayClickListener { void onDayClick(DayCell cell); }

        // ── 字段 ──────────────────────────────────────────────────────────────

        private List<DayCell>       cells = new ArrayList<>();
        private int                 selectedPosition = RecyclerView.NO_POSITION;
        private OnDayClickListener  clickListener;

        private final Set<String> checkupDays;
        private       long        lmpMs;

        public PregnancyCalendarAdapter(Set<String> checkupDays, long lmpMs) {
            this.checkupDays = checkupDays;
            this.lmpMs       = lmpMs;
        }

        public void updateLmp(long newLmpMs) {
            this.lmpMs = newLmpMs;
            // 不调 notifyDataSetChanged，由 Fragment.renderCalendar() 重建格子后调 setCells
        }

        // ── 公共 API ──────────────────────────────────────────────────────────

        public void setCells(List<DayCell> cells) {
            this.cells = cells;
            selectedPosition = RecyclerView.NO_POSITION;
            for (int i = 0; i < cells.size(); i++) {
                if (cells.get(i).isSelected) { selectedPosition = i; break; }
            }
            notifyDataSetChanged();
        }

        public List<DayCell> getCells() { return cells; }

        public DayCell getSelectedCell() {
            if (selectedPosition == RecyclerView.NO_POSITION
                    || selectedPosition >= cells.size()) return null;
            return cells.get(selectedPosition);
        }

        public void setOnDayClickListener(OnDayClickListener l) { this.clickListener = l; }

        // ── RecyclerView.Adapter ──────────────────────────────────────────────

        @NonNull
        @Override
        public PVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_calendar_day_pregnancy, parent, false);
            return new PVH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull PVH h, int position) {
            DayCell cell = cells.get(position);

            // ── 空格 ────────────────────────────────────────────────────────
            if (cell.day == 0) {
                h.tvDay.setText("");
                h.tvSub.setVisibility(View.GONE);
                h.tvWeek.setVisibility(View.GONE);
                h.root.setBackground(null);
                h.root.setClickable(false);
                h.root.setOnClickListener(null);
                return;
            }

            // ── 日期数字 ────────────────────────────────────────────────────
            h.tvDay.setText(String.valueOf(cell.day));
            h.root.setClickable(true);
            h.root.setFocusable(true);

            // ── 副标签（今天）──────────────────────────────────────────────
            if (cell.subLabel != null && !cell.subLabel.isEmpty()) {
                h.tvSub.setVisibility(View.VISIBLE);
                h.tvSub.setText(cell.subLabel);
            } else {
                h.tvSub.setVisibility(View.GONE);
            }

            // ── 孕周标签 ────────────────────────────────────────────────────
            if (cell.pregWeek >= 0) {
                h.tvWeek.setVisibility(View.VISIBLE);
                h.tvWeek.setText(cell.pregWeek + "周");
            } else {
                h.tvWeek.setVisibility(View.GONE);
            }

            // ── 文字颜色 ────────────────────────────────────────────────────
            h.tvDay.setTextColor(cell.isFuture ? 0xFFB8AEA7 : 0xFF2F2926);

            // ── 背景（优先级：产检日 > 选中 > 孕期色块 > 普通）────────────
            String checkKey = cell.year + "-" + cell.month + "-" + cell.day;
            if (checkupDays.contains(checkKey)) {
                h.root.setBackgroundResource(R.drawable.bg_calendar_day_checkup);
            } else if (cell.isSelected) {
                h.root.setBackgroundResource(R.drawable.bg_calendar_day_selected);
            } else {
                switch (cell.stage) {
                    case STAGE_LATE:
                        h.root.setBackgroundResource(R.drawable.bg_calendar_day_pregnancy_late);
                        break;
                    case STAGE_MID:
                        h.root.setBackgroundResource(R.drawable.bg_calendar_day_pregnancy_mid);
                        break;
                    case STAGE_EARLY:
                        h.root.setBackgroundResource(R.drawable.bg_calendar_day_pregnancy_early);
                        break;
                    default:
                        h.root.setBackground(null);
                        break;
                }
            }

            // ── 点击 ────────────────────────────────────────────────────────
            h.root.setOnClickListener(v -> {
                if (cell.isFuture) return;
                int pos = h.getAdapterPosition();
                if (pos == RecyclerView.NO_POSITION) return;

                if (selectedPosition != RecyclerView.NO_POSITION
                        && selectedPosition < cells.size()) {
                    cells.get(selectedPosition).isSelected = false;
                }
                cells.get(pos).isSelected = true;
                selectedPosition = pos;
                notifyDataSetChanged();

                if (clickListener != null) clickListener.onDayClick(cell);
            });
        }

        @Override
        public int getItemCount() { return cells.size(); }

        // ── ViewHolder ───────────────────────────────────────────────────────

        static class PVH extends RecyclerView.ViewHolder {
            final View     root;
            final TextView tvDay;
            final TextView tvSub;
            final TextView tvWeek;

            PVH(@NonNull View v) {
                super(v);
                root   = v.findViewById(R.id.calendar_day_root);
                tvDay  = v.findViewById(R.id.tv_day_number);
                tvSub  = v.findViewById(R.id.tv_day_sub);
                tvWeek = v.findViewById(R.id.tv_pregnancy_week);
            }
        }
    }
}
