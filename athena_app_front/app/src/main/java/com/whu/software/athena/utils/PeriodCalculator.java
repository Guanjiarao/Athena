package com.whu.software.athena.utils;

import java.time.LocalDate;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * 本地经期周期预测算法。
 *
 * <p>所有计算均基于 {@link LocalDate}，无网络依赖，可在主线程直接调用。
 *
 * <p>算法规则：
 * <ul>
 *   <li>下次经期起始日 = lastPeriodStart + cycleDays</li>
 *   <li>预测经期集合  = 从下次起始日连续 periodDays 天</li>
 *   <li>排卵日        = 下次经期起始日 - 14 天</li>
 *   <li>排卵期集合    = 排卵日前5天 ~ 排卵日后4天（共10天）</li>
 * </ul>
 */
public final class PeriodCalculator {

    private PeriodCalculator() {}

    // ── 公开入口 ────────────────────────────────────────────────────────────

    /**
     * 根据上次经期起始日计算下一个周期的预测数据。
     *
     * @param lastPeriodStart 上次经期起始日；为 {@code null} 时返回全空结果。
     * @param periodDays      经期持续天数（默认 5）
     * @param cycleDays       月经周期天数（默认 28）
     * @return {@link PredictionResult}，包含预测经期、排卵期、排卵日三个集合
     */
    public static PredictionResult calculate(LocalDate lastPeriodStart,
                                             int periodDays,
                                             int cycleDays) {
        if (lastPeriodStart == null) {
            return PredictionResult.empty();
        }

        // 下次预测经期起始日
        LocalDate nextPeriodStart = lastPeriodStart.plusDays(cycleDays);

        // 预测经期集合：nextPeriodStart 起连续 periodDays 天
        Set<LocalDate> predictedPeriodDates = new HashSet<>();
        for (int i = 0; i < periodDays; i++) {
            predictedPeriodDates.add(nextPeriodStart.plusDays(i));
        }

        // 排卵日：下次经期起始日前 14 天
        LocalDate ovulationDay = nextPeriodStart.minusDays(14);

        // 排卵日集合（仅 1 天）
        Set<LocalDate> ovulationDayDates = new HashSet<>();
        ovulationDayDates.add(ovulationDay);

        // 排卵期集合：排卵日前5天 ~ 排卵日后4天（共10天）
        Set<LocalDate> ovulationWindowDates = new HashSet<>();
        for (int i = -5; i <= 4; i++) {
            LocalDate d = ovulationDay.plusDays(i);
            if (!d.equals(ovulationDay)) {          // 排卵日本身归入 ovulationDayDates
                ovulationWindowDates.add(d);
            }
        }

        return new PredictionResult(predictedPeriodDates, ovulationWindowDates, ovulationDayDates);
    }

    // ── 结果封装 ─────────────────────────────────────────────────────────────

    public static final class PredictionResult {

        /** 预测经期日期集合（粉色底色） */
        public final Set<LocalDate> predictedPeriodDates;

        /** 排卵期日期集合（紫色底色，不含排卵日当天） */
        public final Set<LocalDate> ovulationWindowDates;

        /** 排卵日集合（深紫/特殊底色，仅1天） */
        public final Set<LocalDate> ovulationDayDates;

        public PredictionResult(Set<LocalDate> predictedPeriodDates,
                                Set<LocalDate> ovulationWindowDates,
                                Set<LocalDate> ovulationDayDates) {
            this.predictedPeriodDates = Collections.unmodifiableSet(predictedPeriodDates);
            this.ovulationWindowDates = Collections.unmodifiableSet(ovulationWindowDates);
            this.ovulationDayDates    = Collections.unmodifiableSet(ovulationDayDates);
        }

        static PredictionResult empty() {
            return new PredictionResult(
                    Collections.emptySet(),
                    Collections.emptySet(),
                    Collections.emptySet());
        }
    }
}
