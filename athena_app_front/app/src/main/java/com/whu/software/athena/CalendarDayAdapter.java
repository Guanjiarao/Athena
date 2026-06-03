package com.whu.software.athena;

import android.graphics.Color;
import android.graphics.Typeface;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CalendarDayAdapter extends RecyclerView.Adapter<CalendarDayAdapter.DayViewHolder> {

    private static final String TAG = "CalendarDayAdapter";

    public static final int DAY_TYPE_EMPTY         = -1;
    public static final int DAY_TYPE_NORMAL        =  0;
    public static final int DAY_TYPE_PERIOD        =  1;
    public static final int DAY_TYPE_PREDICTED     =  2;
    public static final int DAY_TYPE_OVULATION     =  3;
    public static final int DAY_TYPE_OVULATION_DAY =  4;

    // ── DayCell ──────────────────────────────────────────────────────────────

    public static class DayCell {
        public final int     day;
        public final int     year;
        public final int     month;
        public       int     dayType;
        public       boolean isSelected;
        public       boolean isFuture;
        public       String  subLabel;

        public DayCell(int day, int year, int month, int dayType,
                       boolean isSelected, boolean isFuture, String subLabel) {
            this.day = day; this.year = year; this.month = month;
            this.dayType = dayType; this.isSelected = isSelected;
            this.isFuture = isFuture; this.subLabel = subLabel;
        }
    }

    public interface OnDayClickListener {
        void onDayClick(DayCell cell);
    }

    // ── 内部状态 ──────────────────────────────────────────────────────────────

    private List<DayCell>      cells            = new ArrayList<>();
    private int                selectedPosition = RecyclerView.NO_POSITION;
    private OnDayClickListener clickListener;

    // 视觉元素 A：背景色块——经期域（与 /record/marks 零交集）
    public Set<LocalDate> menstruationActualDates    = new HashSet<>(); // 实际经期
    public Set<LocalDate> menstruationPredictedDates = new HashSet<>(); // 预测经期
    public Set<LocalDate> ovulationWindowDates       = new HashSet<>(); // 排卵期（不含排卵日）
    public Set<LocalDate> ovulationDayDates          = new HashSet<>(); // 排卵日（1天）

    // 视觉元素 B：小圆点——仅由 /record/marks 驱动
    public Set<LocalDate> recordMarkDates = new HashSet<>();

    // ── 公开 API ──────────────────────────────────────────────────────────────

    public void setCells(List<DayCell> cells) {
        this.cells = cells;
        selectedPosition = RecyclerView.NO_POSITION;
        for (int i = 0; i < cells.size(); i++) {
            if (cells.get(i).isSelected) { selectedPosition = i; break; }
        }
        notifyDataSetChanged();
    }

    public List<DayCell> getCells() { return cells; }

    /**
     * 一次性替换三个预测相关集合（预测经期 + 排卵期 + 排卵日），立即刷新日历。
     * 由 Fragment 在本地算法计算完毕后调用，实现"乐观更新"。
     */
    public void updatePredictions(Set<LocalDate> predicted,
                                  Set<LocalDate> ovulationWindow,
                                  Set<LocalDate> ovulationDay) {
        Log.d(TAG, "updatePredictions input predictedCount=" + (predicted != null ? predicted.size() : -1)
                + ", ovulationWindowCount=" + (ovulationWindow != null ? ovulationWindow.size() : -1)
                + ", ovulationDayCount=" + (ovulationDay != null ? ovulationDay.size() : -1));
        menstruationPredictedDates = predicted != null ? new HashSet<>(predicted) : new HashSet<>();
        ovulationWindowDates       = ovulationWindow != null ? new HashSet<>(ovulationWindow) : new HashSet<>();
        ovulationDayDates          = ovulationDay != null ? new HashSet<>(ovulationDay) : new HashSet<>();
        Log.d(TAG, "updatePredictions applied predictedSetCount=" + menstruationPredictedDates.size()
                + ", ovulationWindowSetCount=" + ovulationWindowDates.size()
                + ", ovulationDaySetCount=" + ovulationDayDates.size());
        notifyDataSetChanged();
    }

    public void updateCycleVisualsForMonth(int year,
                                           int month,
                                           List<LocalDate> actualDates,
                                           List<LocalDate> predictedDates) {
        Log.d(TAG, "updateCycleVisualsForMonth input year=" + year
                + ", month=" + month
                + ", actualDatesCount=" + (actualDates != null ? actualDates.size() : -1)
                + ", actualDates=" + actualDates
                + ", predictedDatesCount=" + (predictedDates != null ? predictedDates.size() : -1)
                + ", predictedDates=" + predictedDates);
        removeDatesForMonth(menstruationActualDates, year, month);
        removeDatesForMonth(menstruationPredictedDates, year, month);
        if (actualDates != null) {
            for (LocalDate date : actualDates) {
                if (date != null) {
                    menstruationActualDates.add(date);
                }
            }
        }
        if (predictedDates != null) {
            for (LocalDate date : predictedDates) {
                if (date != null) {
                    menstruationPredictedDates.add(date);
                }
            }
        }
        Log.d(TAG, "updateCycleVisualsForMonth applied year=" + year
                + ", month=" + month
                + ", actualSetCount=" + menstruationActualDates.size()
                + ", actualSet=" + menstruationActualDates
                + ", predictedSetCount=" + menstruationPredictedDates.size()
                + ", predictedSet=" + menstruationPredictedDates);
        notifyDataSetChanged();
    }

    /** 添加实际经期日期（乐观更新"月经来了-是"时调用）。 */
    public void addMenstruationActualDates(List<LocalDate> newDates) {
        if (newDates == null || newDates.isEmpty()) return;
        Log.d(TAG, "addMenstruationActualDates inputCount=" + newDates.size()
                + ", inputDates=" + newDates
                + ", beforeCount=" + menstruationActualDates.size());
        for (LocalDate d : newDates) {
            if (d != null) menstruationActualDates.add(d);
        }
        Log.d(TAG, "addMenstruationActualDates applied afterCount=" + menstruationActualDates.size()
                + ", afterDates=" + menstruationActualDates);
        notifyDataSetChanged();
    }

    public void clearMenstruationActualDates() {
        if (menstruationActualDates.isEmpty()) {
            return;
        }
        Log.d(TAG, "clearMenstruationActualDates beforeCount=" + menstruationActualDates.size()
                + ", beforeDates=" + menstruationActualDates);
        menstruationActualDates.clear();
        Log.d(TAG, "clearMenstruationActualDates afterCount=" + menstruationActualDates.size());
        notifyDataSetChanged();
    }

    /** 删除某一天的实际经期（乐观更新"月经来了-否"时调用）。 */
    public void removeMenstruationActualDate(LocalDate dateToRemove) {
        Log.d(TAG, "removeMenstruationActualDate target=" + dateToRemove
                + ", beforeCount=" + menstruationActualDates.size()
                + ", beforeDates=" + menstruationActualDates);
        if (menstruationActualDates.remove(dateToRemove)) {
            Log.d(TAG, "removeMenstruationActualDate removed target=" + dateToRemove
                    + ", afterCount=" + menstruationActualDates.size()
                    + ", afterDates=" + menstruationActualDates);
            notifyDataSetChanged();
        }
    }

    /** 更新某月的健康记录圆点（仅影响 recordMarkDates，绝不改底色）。 */
    public void updateRecordMarksForMonth(int year, int month, List<String> dateStrings) {
        removeDatesForMonth(recordMarkDates, year, month);
        for (String s : dateStrings) {
            try { recordMarkDates.add(LocalDate.parse(s.trim())); } catch (Exception ignored) {}
        }
        notifyDataSetChanged();
    }

    public DayCell getSelectedCell() {
        if (selectedPosition == RecyclerView.NO_POSITION || selectedPosition >= cells.size()) return null;
        return cells.get(selectedPosition);
    }

    public void setOnDayClickListener(OnDayClickListener listener) {
        this.clickListener = listener;
    }

    // ── RecyclerView 实现 ─────────────────────────────────────────────────────

    @NonNull
    @Override
    public DayViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_calendar_day, parent, false);
        return new DayViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull DayViewHolder holder, int position) {
        DayCell cell = cells.get(position);

        if (cell.dayType == DAY_TYPE_EMPTY || cell.day == 0) {
            holder.tvDay.setText("");
            holder.tvSub.setVisibility(View.GONE);
            holder.vRecordDot.setVisibility(View.GONE);
            holder.root.setBackground(null);
            holder.root.setClickable(false);
            holder.root.setOnClickListener(null);
            return;
        }

        holder.tvDay.setText(String.valueOf(cell.day));
        holder.root.setClickable(true);
        holder.root.setFocusable(true);

        LocalDate cellDate = LocalDate.of(cell.year, cell.month, cell.day);

        // ── 四级优先级决定背景色（绝不查 recordMarkDates）────────────────────
        int effectiveType;
        if (menstruationActualDates.contains(cellDate)) {
            // 优先级 1：实际经期（最高优先级，用户已确认的真实数据）
            effectiveType = DAY_TYPE_PERIOD;
        } else if (ovulationDayDates.contains(cellDate)) {
            // 优先级 2：排卵日
            effectiveType = DAY_TYPE_OVULATION_DAY;
        } else if (ovulationWindowDates.contains(cellDate)) {
            // 优先级 3：排卵期
            effectiveType = DAY_TYPE_OVULATION;
        } else if (menstruationPredictedDates.contains(cellDate)) {
            // 优先级 4：预测经期
            effectiveType = DAY_TYPE_PREDICTED;
        } else {
            effectiveType = DAY_TYPE_NORMAL;
        }

        applyBackground(holder, cell, effectiveType);
        applyTextColor(holder, cell, effectiveType);

        // 副标题（"今天"等文案）
        if (cell.subLabel != null && !cell.subLabel.isEmpty()) {
            holder.tvSub.setVisibility(View.VISIBLE);
            holder.tvSub.setText(cell.subLabel);
            boolean darkBg = effectiveType == DAY_TYPE_PERIOD || effectiveType == DAY_TYPE_OVULATION_DAY;
            holder.tvSub.setTextColor(darkBg ? Color.WHITE : Color.parseColor("#99857B73"));
        } else {
            holder.tvSub.setVisibility(View.GONE);
        }

        // 小圆点：只由 recordMarkDates 控制，与背景色零耦合
        holder.vRecordDot.setVisibility(
                recordMarkDates.contains(cellDate) ? View.VISIBLE : View.GONE);

        holder.root.setOnClickListener(v -> {
            if (cell.isFuture) return;

            int clickedPos = holder.getAdapterPosition();
            if (clickedPos == RecyclerView.NO_POSITION) return;

            if (selectedPosition != RecyclerView.NO_POSITION && selectedPosition < cells.size()) {
                cells.get(selectedPosition).isSelected = false;
            }
            cells.get(clickedPos).isSelected = true;
            selectedPosition = clickedPos;

            notifyDataSetChanged();
            if (clickListener != null) clickListener.onDayClick(cell);
        });
    }

    @Override
    public int getItemCount() { return cells.size(); }

    // ── 私有辅助 ──────────────────────────────────────────────────────────────

    private void applyBackground(@NonNull DayViewHolder h, DayCell cell, int effectiveType) {
        boolean solidBg = effectiveType == DAY_TYPE_PERIOD || effectiveType == DAY_TYPE_OVULATION_DAY;
        if (cell.isSelected && !solidBg) {
            h.root.setBackgroundResource(R.drawable.bg_calendar_day_selected);
            return;
        }
        if (cell.subLabel != null && !cell.subLabel.isEmpty() && effectiveType == DAY_TYPE_NORMAL) {
            h.root.setBackgroundResource(R.drawable.bg_calendar_day_today);
            return;
        }
        switch (effectiveType) {
            case DAY_TYPE_PERIOD:        h.root.setBackgroundResource(R.drawable.bg_calendar_day_period);        break;
            case DAY_TYPE_PREDICTED:     h.root.setBackgroundResource(R.drawable.bg_calendar_day_predicted);     break;
            case DAY_TYPE_OVULATION:     h.root.setBackgroundResource(R.drawable.bg_calendar_day_ovulation);     break;
            case DAY_TYPE_OVULATION_DAY: h.root.setBackgroundResource(R.drawable.bg_calendar_day_ovulation_day); break;
            default:                     h.root.setBackground(null);                                              break;
        }
    }

    private void applyTextColor(@NonNull DayViewHolder h, DayCell cell, int effectiveType) {
        switch (effectiveType) {
            case DAY_TYPE_PERIOD:
            case DAY_TYPE_OVULATION_DAY:
                h.tvDay.setTextColor(Color.WHITE);
                h.tvDay.setTypeface(null, Typeface.BOLD);
                break;
            case DAY_TYPE_PREDICTED:
                h.tvDay.setTextColor(Color.parseColor("#F06A82"));
                h.tvDay.setTypeface(null, Typeface.NORMAL);
                break;
            case DAY_TYPE_OVULATION:
                h.tvDay.setTextColor(Color.parseColor("#8D74CB"));
                h.tvDay.setTypeface(null, Typeface.NORMAL);
                break;
            default:
                if (cell.isSelected) {
                    h.tvDay.setTextColor(Color.parseColor("#F06A82"));
                    h.tvDay.setTypeface(null, Typeface.BOLD);
                } else if (cell.isFuture) {
                    h.tvDay.setTextColor(Color.parseColor("#B8AEA7"));
                    h.tvDay.setTypeface(null, Typeface.NORMAL);
                } else {
                    h.tvDay.setTextColor(Color.parseColor("#2F2926"));
                    h.tvDay.setTypeface(null, Typeface.NORMAL);
                }
                break;
        }
    }

    private void removeDatesForMonth(Set<LocalDate> set, int year, int month) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            set.removeIf(d -> d.getYear() == year && d.getMonthValue() == month);
        } else {
            List<LocalDate> toRemove = new ArrayList<>();
            for (LocalDate d : set) {
                if (d.getYear() == year && d.getMonthValue() == month) toRemove.add(d);
            }
            set.removeAll(toRemove);
        }
    }

    // ── ViewHolder ────────────────────────────────────────────────────────────

    static class DayViewHolder extends RecyclerView.ViewHolder {
        final View     root;
        final TextView tvDay;
        final TextView tvSub;
        final View     vRecordDot;

        DayViewHolder(@NonNull View itemView) {
            super(itemView);
            root       = itemView.findViewById(R.id.calendar_day_root);
            tvDay      = itemView.findViewById(R.id.tv_day_number);
            tvSub      = itemView.findViewById(R.id.tv_day_sub);
            vRecordDot = itemView.findViewById(R.id.v_record_dot);
        }
    }
}
