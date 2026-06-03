package com.whu.software.athena.utils;

import org.json.JSONObject;

/**
 * 文章列表 JSON 解析辅助：严格读取 type / channelId，避免错误数据混入列表。
 */
public final class ArticleListParseHelper {

    private ArticleListParseHelper() {}

    /** 字段缺失或不可解析时返回，调用方应跳过该行 */
    public static final int MISSING_ID = Integer.MIN_VALUE;

    /**
     * 解析博客条目上的分类 type（支持数字或数字字符串）。
     * 依次尝试 type、articleType、blogType、article_type，与常见后端字段对齐。
     */
    public static int parseTypeField(JSONObject obj) {
        if (obj == null) {
            return MISSING_ID;
        }
        String[] keys = {"type", "articleType", "blogType", "article_type"};
        for (String k : keys) {
            if (!obj.has(k) || obj.isNull(k)) {
                continue;
            }
            try {
                Object t = obj.get(k);
                if (t instanceof Number) {
                    return ((Number) t).intValue();
                }
                String s = String.valueOf(t).trim();
                if (s.isEmpty()) {
                    continue;
                }
                return Integer.parseInt(s);
            } catch (Exception ignored) {
                // try next key
            }
        }
        return MISSING_ID;
    }

    /**
     * 解析频道条目上的 channelId（兼容 channel_id）。
     */
    public static int parseChannelIdField(JSONObject obj) {
        if (obj == null) {
            return MISSING_ID;
        }
        String[] keys = {"channelId", "channel_id", "channel"};
        for (String k : keys) {
            if (!obj.has(k) || obj.isNull(k)) {
                continue;
            }
            try {
                Object v = obj.get(k);
                if (v instanceof Number) {
                    return ((Number) v).intValue();
                }
                String s = String.valueOf(v).trim();
                if (s.isEmpty()) {
                    continue;
                }
                return Integer.parseInt(s);
            } catch (Exception ignored) {
                // try next key
            }
        }
        return MISSING_ID;
    }
}
