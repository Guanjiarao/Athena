package com.whu.software.athena.utils;

import com.whu.software.athena.entity.HealthRecordEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * 健康记录列表 UI 聚合：同一天同一 {@code recordItemId} 多条记录时，拼接为一条展示文案。
 * <p>
 * 使用方式：{@link #collectValues(List, int)} 收集 → {@link #joinDisplay(List)} 拼接（{@code " | "}）。
 */
public final class HealthRecordUiAggregator {

    private static final String DISPLAY_JOINER = " | ";

    private HealthRecordUiAggregator() {}

    /**
     * 按接口返回顺序收集某日、某 {@code recordItemId} 下所有非空 {@code recordValue}。
     */
    public static List<String> collectValues(List<HealthRecordEntity> records, int recordItemId) {
        List<String> out = new ArrayList<>();
        if (records == null) return out;
        for (HealthRecordEntity e : records) {
            if (e == null || e.getRecordItemId() != recordItemId) continue;
            String v = e.getRecordValue();
            if (v == null) continue;
            v = v.trim();
            if (!v.isEmpty()) out.add(v);
        }
        return out;
    }

    /**
     * 多条值拼成一行展示（minSdk 24 兼容，不用 {@code String.join}）。
     */
    public static String joinDisplay(List<String> parts) {
        if (parts == null || parts.isEmpty()) return "";
        StringBuilder sb = new StringBuilder(parts.get(0));
        for (int i = 1; i < parts.size(); i++) {
            sb.append(DISPLAY_JOINER).append(parts.get(i));
        }
        return sb.toString();
    }
}
