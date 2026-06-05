package com.whu.software.athena.utils;

import android.graphics.Bitmap;
import android.util.Base64;

import java.io.ByteArrayOutputStream;

/**
 * 图片工具类：将 Bitmap 编码为 Base64 字符串，供 AI 接口传输使用。
 */
public final class ImageUtil {

    private ImageUtil() {}

    /**
     * 将 Bitmap 压缩并转换为标准 Base64 字符串。
     *
     * 压缩策略：
     *   - 先等比缩放至最长边 ≤ 1024px（减小上传体积，同时保留足够细节）
     *   - 以 JPEG 格式、80% 质量编码
     *   - 使用 NO_WRAP 去掉换行，满足 HTTP JSON Body 的纯文本要求
     *
     * @param source 原始 Bitmap（不会被回收，调用方自行管理生命周期）
     * @return Base64 编码字符串；source 为 null 时返回空字符串
     */
    public static String bitmapToBase64(Bitmap source) {
        if (source == null) return "";

        Bitmap scaled = scaleBitmap(source, 1024);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            scaled.compress(Bitmap.CompressFormat.JPEG, 80, baos);
            byte[] bytes = baos.toByteArray();
            return Base64.encodeToString(bytes, Base64.NO_WRAP);
        } finally {
            try { baos.close(); } catch (Exception ignored) {}
            // 只回收缩放副本，不回收传入的原 Bitmap
            if (scaled != source) scaled.recycle();
        }
    }

    /** 等比缩放：若最长边超过 maxSize，则按比例缩放；否则原样返回。 */
    private static Bitmap scaleBitmap(Bitmap src, int maxSize) {
        int w = src.getWidth();
        int h = src.getHeight();
        if (w <= maxSize && h <= maxSize) return src;

        float ratio = (float) maxSize / Math.max(w, h);
        int newW = Math.round(w * ratio);
        int newH = Math.round(h * ratio);
        return Bitmap.createScaledBitmap(src, newW, newH, true);
    }
}
