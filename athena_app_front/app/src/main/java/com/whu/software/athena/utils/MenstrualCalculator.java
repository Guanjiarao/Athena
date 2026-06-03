package com.whu.software.athena.utils;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * 生理期前端纯算法引擎。
 *
 * 根据「最后一次真实月经起始日 + 规律设置」推算某一天的状态。
 */
public final class MenstrualCalculator {

    private MenstrualCalculator() {}

    // 状态常量（与需求保持一致）
    public static final int NORMAL          = 0;
    public static final int LOGGED_PERIOD   = 1;
    public static final int PREDICT_PERIOD  = 2;
    public static final int OVULATION_WINDOW = 3;
    public static final int OVULATION_DAY   = 4;

    /**
 * 计算指定日期的生理期状态（仅基于规律设置，不含真实记录标记）。
 *
 * @param targetDate     目标日期
 * @param lastPeriodStart 最后一次真实月经起始日；若为 null 视为无法预测
 * @param periodDays     经期时长（天）
 * @param cycleDays      月经周期（天）
 */
public static int getDayType(LocalDate targetDate,
                             LocalDate lastPeriodStart,
                             int periodDays,
                             int cycleDays) {
    if (targetDate == null || lastPeriodStart == null) {
        return NORMAL;
    }

    if (targetDate.isBefore(lastPeriodStart)) {
        return NORMAL;
    }

    long diffDays = ChronoUnit.DAYS.between(lastPeriodStart, targetDate);
    if (diffDays < 0) {
        return NORMAL;
    }

    if (cycleDays <= 0) {
        return NORMAL;
    }

    long dayOfCycle = diffDays % cycleDays;

    // 若 dayOfCycle < periodDays 且 diffDays >= cycleDays，返回预测经期
    if (dayOfCycle < periodDays && diffDays >= cycleDays) {
        return PREDICT_PERIOD;
    }

    // 排卵日：周期倒数 14 天
    if (dayOfCycle == cycleDays - 14) {
        return OVULATION_DAY;
    }

    // 排卵期 / 易孕期：周期倒数 19 天 ~ 倒数 10 天
    long startWindow = cycleDays - 19L;
    long endWindow   = cycleDays - 10L;
    if (dayOfCycle >= startWindow && dayOfCycle <= endWindow) {
        return OVULATION_WINDOW;
    }

    return NORMAL;
}

/**
 * 根据最新的月经起始日，计算下一次月经和排卵的预测日期。
 * 
 * @param lastPeriodStart 最后一次真实月经起始日
 * @param cycleDays      月经周期（天）
 * @return 预测结果，包含下次月经和排卵日期
 */
public static PredictionResult calculateNextPrediction(LocalDate lastPeriodStart, int cycleDays) {
    if (lastPeriodStart == null || cycleDays <= 0) {
        return null;
    }
    
    // 计算下次月经日期
    LocalDate nextPeriod = lastPeriodStart.plusDays(cycleDays);
    
    // 计算排卵日（下次月经前 14 天）
    LocalDate ovulationDay = nextPeriod.minusDays(14);
    
    // 计算排卵期窗口（排卵日前 5 天到后 4 天）
    LocalDate ovulationStart = ovulationDay.minusDays(5);
    LocalDate ovulationEnd = ovulationDay.plusDays(4);
    
    return new PredictionResult(nextPeriod, ovulationDay, ovulationStart, ovulationEnd);
}

/**
 * 预测结果数据类
 */
public static class PredictionResult {
    public final LocalDate nextPeriod;       // 下次月经日期
    public final LocalDate ovulationDay;     // 排卵日
    public final LocalDate ovulationStart;   // 排卵期开始
    public final LocalDate ovulationEnd;     // 排卵期结束
    
    public PredictionResult(LocalDate nextPeriod, LocalDate ovulationDay,
                           LocalDate ovulationStart, LocalDate ovulationEnd) {
        this.nextPeriod = nextPeriod;
        this.ovulationDay = ovulationDay;
        this.ovulationStart = ovulationStart;
        this.ovulationEnd = ovulationEnd;
    }
}
}

