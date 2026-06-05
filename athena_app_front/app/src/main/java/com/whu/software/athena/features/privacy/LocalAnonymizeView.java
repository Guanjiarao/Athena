package com.whu.software.athena.features.privacy;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.MotionEvent;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * LocalAnonymizeView — 端侧手绘脱敏画板。
 *
 * 渲染架构（离屏双 Pass）：
 *   onDraw：绘原图 → 遍历 strokes → saveLayer → 路径 Mask(白) + SRC_IN 凿入脱敏图
 *
 * 公共接口速查：
 *   setImageSource(Bitmap)              — 传原图，后台线程预计算脱敏副本
 *   setMode(@Mode)                      — 切换工具
 *   setStrokeWidth(float px)            — 笔刷粗细
 *   setProcessIntensity(int 1-50)       — 脱敏强度，变更后重算副本
 *   autoAnonymizeRegions(List<Rect>)    — 自动将矩形区域用马赛克覆盖
 *   getFinalAnonymizedBitmap()          — 获取最终合成图（原图尺寸）
 *   undo() / reset()                   — 撤销 / 清空
 */
public class LocalAnonymizeView extends AppCompatImageView {

    // ── 绘制模式 ─────────────────────────────────────────────────────────────
    public static final int MODE_NONE          = 0;
    public static final int MODE_MANUAL_MOSAIC = 1;
    public static final int MODE_MANUAL_BLUR   = 2;

    @IntDef({MODE_NONE, MODE_MANUAL_MOSAIC, MODE_MANUAL_BLUR})
    @Retention(RetentionPolicy.SOURCE)
    public @interface Mode {}

    // ── 自动区域块（来自 OCR 或外部调用）────────────────────────────────────
    private static class AutoRegion {
        final Rect rect;
        AutoRegion(Rect rect) { this.rect = rect; }
    }

    // ── 手绘笔画 ──────────────────────────────────────────────────────────────
    private static class Stroke {
        final Path path;
        @Mode final int mode;
        final float width;
        Stroke(Path path, @Mode int mode, float width) {
            this.path  = path;
            this.mode  = mode;
            this.width = width;
        }
    }

    // ── Bitmaps ──────────────────────────────────────────────────────────────
    private Bitmap originalBitmap;
    private volatile Bitmap mosaicBitmap;
    private volatile Bitmap blurBitmap;

    // ── 状态 ─────────────────────────────────────────────────────────────────
    @Mode private int currentMode    = MODE_NONE;
    private float     strokeWidth    = 60f;
    private int       processIntensity = 15;

    private final ArrayList<Stroke>     strokes     = new ArrayList<>();
    private final ArrayList<AutoRegion> autoRegions = new ArrayList<>();
    private Path   currentPath;
    @Mode private int currentPathMode;

    // ── Paint（复用）─────────────────────────────────────────────────────────
    private final Paint maskPaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint srcInPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint autoRegionPaint = new Paint();

    // ── 后台线程 ──────────────────────────────────────────────────────────────
    private final ExecutorService bgExecutor = Executors.newSingleThreadExecutor();
    private final Handler         mainHandler = new Handler(Looper.getMainLooper());

    // ── 回调 ──────────────────────────────────────────────────────────────────
    public interface OnEffectReadyListener {
        void onEffectReady();
    }
    private OnEffectReadyListener effectReadyListener;

    public void setOnEffectReadyListener(OnEffectReadyListener l) {
        effectReadyListener = l;
    }

    // ─────────────────────────────────────────────────────────────────────────

    public LocalAnonymizeView(@NonNull Context context) {
        super(context);
        init();
    }

    public LocalAnonymizeView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public LocalAnonymizeView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        // 离屏 Porter-Duff 需要软件层
        setLayerType(LAYER_TYPE_SOFTWARE, null);

        // Mask 画笔：圆头圆角实心笔触，作为 SRC_IN 的形状遮罩
        maskPaint.setStyle(Paint.Style.STROKE);
        maskPaint.setStrokeCap(Paint.Cap.ROUND);
        maskPaint.setStrokeJoin(Paint.Join.ROUND);
        maskPaint.setColor(Color.WHITE);

        // SRC_IN 画笔：将脱敏图凿入 mask 形状内
        srcInPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));

        // 自动区域：半透明马赛克遮罩（实际渲染时用 mosaicBitmap 剪切，此 paint 仅备用）
        autoRegionPaint.setStyle(Paint.Style.FILL);
    }

    // ════════════════════════════════════════════════════════════════════════
    //  公共 API
    // ════════════════════════════════════════════════════════════════════════

    /**
     * 传入原始 Bitmap，在后台线程预计算马赛克 / 模糊版本，完成后回调主线程刷新。
     * 这是使用该 View 的第一步，务必在 UI 线程调用。
     */
    public void setImageSource(@NonNull Bitmap src) {
        originalBitmap = src.copy(Bitmap.Config.ARGB_8888, false);
        mosaicBitmap   = null;
        blurBitmap     = null;
        strokes.clear();
        autoRegions.clear();

        // 立即显示原图，后台同时计算脱敏版本
        super.setImageBitmap(originalBitmap);
        invalidate();

        rebuildEffectBitmapsAsync(processIntensity);
    }

    public void setMode(@Mode int mode) {
        currentMode = mode;
    }

    /** 笔刷粗细，单位 px。 */
    public void setStrokeWidth(float px) {
        strokeWidth = Math.max(1f, px);
    }

    /**
     * 脱敏强度（马赛克块大小 / 模糊半径），范围 1–50。
     * 变更后异步重算脱敏 Bitmap。
     */
    public void setProcessIntensity(int intensity) {
        int clamped = Math.max(1, Math.min(50, intensity));
        if (clamped == processIntensity) return;
        processIntensity = clamped;
        rebuildEffectBitmapsAsync(clamped);
    }

    /** 撤销最后一笔（手绘或自动区域均可撤销）。 */
    public void undo() {
        if (!strokes.isEmpty()) {
            strokes.remove(strokes.size() - 1);
            invalidate();
            return;
        }
        if (!autoRegions.isEmpty()) {
            autoRegions.remove(autoRegions.size() - 1);
            invalidate();
        }
    }

    /** 清除全部笔画与自动区域。 */
    public void reset() {
        strokes.clear();
        autoRegions.clear();
        invalidate();
    }

    /**
     * 自动脱敏指定矩形区域（坐标为 Bitmap 像素坐标）。
     * 通常由 MLKitAnonymizeService 检测到 PII 后调用。
     * 使用马赛克效果覆盖；如需模糊，可扩展参数。
     */
    public void autoAnonymizeRegions(@NonNull List<Rect> regions) {
        for (Rect r : regions) {
            autoRegions.add(new AutoRegion(new Rect(r)));
        }
        invalidate();
    }

    /**
     * 获取含所有脱敏标注的最终合成 Bitmap（与 originalBitmap 等尺寸）。
     * 在调用线程上同步合成，建议在后台线程调用。
     */
    @Nullable
    public Bitmap getFinalAnonymizedBitmap() {
        if (originalBitmap == null) return null;

        Bitmap result = originalBitmap.copy(Bitmap.Config.ARGB_8888, true);
        Canvas canvas = new Canvas(result);

        // 渲染自动区域（bitmap 坐标系，缩放因子 = 1）
        drawAutoRegions(canvas, 1f, 1f);
        // 渲染手绘笔画
        drawStrokes(canvas, 1f, 1f);

        return result;
    }

    // ════════════════════════════════════════════════════════════════════════
    //  像素算法
    // ════════════════════════════════════════════════════════════════════════

    /**
     * 马赛克效果：先将 src 缩小到 1/blockSize，再放大回原尺寸。
     * 双线性插值（默认 FILTER_BITMAP_FLAG）在缩放时自动软化边缘，
     * 放大时 Paint 关掉过滤以保留锯齿感，形成马赛克。
     */
    public static Bitmap createMosaicEffect(@NonNull Bitmap src, int blockSize) {
        if (blockSize < 2) return src.copy(src.getConfig(), false);

        int w = src.getWidth();
        int h = src.getHeight();

        // 缩小：双线性
        int smallW = Math.max(1, w / blockSize);
        int smallH = Math.max(1, h / blockSize);
        Bitmap small = Bitmap.createScaledBitmap(src, smallW, smallH, true);

        // 放大：关闭过滤 → 保留像素块硬边
        Bitmap mosaic = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(mosaic);
        Paint p = new Paint();
        p.setFilterBitmap(false);
        c.drawBitmap(small, null, new RectF(0, 0, w, h), p);
        small.recycle();
        return mosaic;
    }

    /**
     * 高斯模糊效果：用 Stack Blur（O(n) 纯 Java 实现），radius 越大越模糊。
     * Android 12+ 可改用 RenderEffect，此处提供全版本兼容的纯 Java 路径。
     */
    public static Bitmap createGaussianBlurEffect(@NonNull Bitmap src, int radius) {
        if (radius < 1) return src.copy(src.getConfig(), false);

        Bitmap bitmap = src.copy(Bitmap.Config.ARGB_8888, true);
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        int[] pix = new int[w * h];
        bitmap.getPixels(pix, 0, w, 0, 0, w, h);

        stackBlur(pix, w, h, radius);

        bitmap.setPixels(pix, 0, w, 0, 0, w, h);
        return bitmap;
    }

    /**
     * Stack Blur — O(w*h) 时间复杂度的快速模糊算法，支持任意半径。
     * 原理：用一个滑动"堆栈"累加器代替逐像素卷积，避免 O(n*r) 的卷积开销。
     */
    private static void stackBlur(int[] pix, int w, int h, int radius) {
        int wm = w - 1, hm = h - 1, div = radius * 2 + 1;
        int[] r = new int[w * h], g = new int[w * h], b = new int[w * h];
        int[] vmin = new int[Math.max(w, h)];

        int divsum = ((div + 1) >> 1) * ((div + 1) >> 1);
        int[] dv = new int[256 * divsum];
        for (int i = 0; i < dv.length; i++) dv[i] = i / divsum;

        int[][] stack = new int[div][3];
        int yw = 0, yi = 0;

        for (int y = 0; y < h; y++) {
            int rsum = 0, gsum = 0, bsum = 0;
            int rinsum = 0, ginsum = 0, binsum = 0;
            int routsum = 0, goutsum = 0, boutsum = 0;
            int r1 = radius + 1;

            for (int i = -radius; i <= radius; i++) {
                int p = pix[yi + Math.min(wm, Math.max(i, 0))];
                int[] sir = stack[i + radius];
                sir[0] = (p >> 16) & 0xff;
                sir[1] = (p >> 8)  & 0xff;
                sir[2] =  p        & 0xff;
                int rbs = r1 - Math.abs(i);
                rsum += sir[0] * rbs; gsum += sir[1] * rbs; bsum += sir[2] * rbs;
                if (i > 0) { rinsum += sir[0]; ginsum += sir[1]; binsum += sir[2]; }
                else       { routsum += sir[0]; goutsum += sir[1]; boutsum += sir[2]; }
            }
            int stackpointer = radius;
            for (int x = 0; x < w; x++) {
                r[yi] = dv[rsum]; g[yi] = dv[gsum]; b[yi] = dv[bsum];
                rsum -= routsum; gsum -= goutsum; bsum -= boutsum;
                int ss = (stackpointer - radius + div) % div;
                int[] sir = stack[ss];
                routsum -= sir[0]; goutsum -= sir[1]; boutsum -= sir[2];
                if (y == 0) vmin[x] = Math.min(x + r1, wm);
                int p = pix[yw + vmin[x]];
                sir[0] = (p >> 16) & 0xff;
                sir[1] = (p >> 8)  & 0xff;
                sir[2] =  p        & 0xff;
                rinsum += sir[0]; ginsum += sir[1]; binsum += sir[2];
                rsum += rinsum; gsum += ginsum; bsum += binsum;
                stackpointer = (stackpointer + 1) % div;
                sir = stack[stackpointer];
                routsum += sir[0]; goutsum += sir[1]; boutsum += sir[2];
                rinsum -= sir[0]; ginsum -= sir[1]; binsum -= sir[2];
                yi++;
            }
            yw += w;
        }

        for (int x = 0; x < w; x++) {
            int rinsum = 0, ginsum = 0, binsum = 0;
            int routsum = 0, goutsum = 0, boutsum = 0;
            int rsum = 0, gsum = 0, bsum = 0;
            int r1 = radius + 1;
            int yp = -radius * w;

            for (int i = -radius; i <= radius; i++) {
                yi = Math.max(0, yp) + x;
                int[] sir = stack[i + radius];
                sir[0] = r[yi]; sir[1] = g[yi]; sir[2] = b[yi];
                int rbs = r1 - Math.abs(i);
                rsum += r[yi] * rbs; gsum += g[yi] * rbs; bsum += b[yi] * rbs;
                if (i > 0) { rinsum += sir[0]; ginsum += sir[1]; binsum += sir[2]; }
                else       { routsum += sir[0]; goutsum += sir[1]; boutsum += sir[2]; }
                if (i < hm) yp += w;
            }
            yi = x;
            int stackpointer = radius;
            for (int y = 0; y < h; y++) {
                pix[yi] = (0xff000000 & pix[yi]) | (dv[rsum] << 16) | (dv[gsum] << 8) | dv[bsum];
                rsum -= routsum; gsum -= goutsum; bsum -= boutsum;
                int ss = (stackpointer - radius + div) % div;
                int[] sir = stack[ss];
                routsum -= sir[0]; goutsum -= sir[1]; boutsum -= sir[2];
                if (x == 0) vmin[y] = Math.min(y + r1, hm) * w;
                int p = x + vmin[y];
                sir[0] = r[p]; sir[1] = g[p]; sir[2] = b[p];
                rinsum += sir[0]; ginsum += sir[1]; binsum += sir[2];
                rsum += rinsum; gsum += ginsum; bsum += binsum;
                stackpointer = (stackpointer + 1) % div;
                sir = stack[stackpointer];
                routsum += sir[0]; goutsum += sir[1]; boutsum += sir[2];
                rinsum -= sir[0]; ginsum -= sir[1]; binsum -= sir[2];
                yi += w;
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    //  绘制
    // ════════════════════════════════════════════════════════════════════════

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        if (originalBitmap == null) {
            super.onDraw(canvas);
            return;
        }

        RectF dst = fitCenterRect(getWidth(), getHeight(),
                originalBitmap.getWidth(), originalBitmap.getHeight());

        canvas.drawBitmap(originalBitmap, null, dst, null);

        if (strokes.isEmpty() && autoRegions.isEmpty() && currentPath == null) return;

        canvas.save();
        canvas.translate(dst.left, dst.top);
        float toX = dst.width()  / originalBitmap.getWidth();
        float toY = dst.height() / originalBitmap.getHeight();

        drawAutoRegions(canvas, toX, toY);
        drawStrokes(canvas, toX, toY);

        // 实时预览正在绘制的笔画（半透明蓝色轮廓，低开销）
        if (currentPath != null) {
            Paint previewPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            previewPaint.setStyle(Paint.Style.STROKE);
            previewPaint.setStrokeCap(Paint.Cap.ROUND);
            previewPaint.setStrokeJoin(Paint.Join.ROUND);
            previewPaint.setColor(0x604FA0FF);
            previewPaint.setStrokeWidth(strokeWidth * Math.min(toX, toY));
            canvas.drawPath(scalePath(currentPath, toX, toY), previewPaint);
        }

        canvas.restore();
    }

    /** 渲染自动识别区域（矩形马赛克块）。 */
    private void drawAutoRegions(Canvas canvas, float toX, float toY) {
        if (autoRegions.isEmpty() || mosaicBitmap == null) return;

        float bmpW = originalBitmap.getWidth();
        float bmpH = originalBitmap.getHeight();

        for (AutoRegion ar : autoRegions) {
            int saveCount = canvas.saveLayer(null, null);

            // Mask：用白色填充矩形区域
            Paint fillMask = new Paint();
            fillMask.setColor(Color.WHITE);
            fillMask.setStyle(Paint.Style.FILL);
            RectF regionView = new RectF(
                    ar.rect.left   * toX, ar.rect.top    * toY,
                    ar.rect.right  * toX, ar.rect.bottom * toY);
            canvas.drawRect(regionView, fillMask);

            // SRC_IN：将 mosaicBitmap 对应区域凿入
            RectF fullDst = new RectF(0, 0, bmpW * toX, bmpH * toY);
            canvas.drawBitmap(mosaicBitmap, null, fullDst, srcInPaint);

            canvas.restoreToCount(saveCount);
        }
    }

    /** 渲染手绘笔画（离屏双 Pass：Mask + SRC_IN）。 */
    private void drawStrokes(Canvas canvas, float toX, float toY) {
        for (Stroke stroke : strokes) {
            Bitmap effectSrc = stroke.mode == MODE_MANUAL_MOSAIC ? mosaicBitmap : blurBitmap;
            if (effectSrc == null) continue;

            int saveCount = canvas.saveLayer(null, null);

            float sw = stroke.width * Math.min(toX, toY);
            maskPaint.setStrokeWidth(sw);
            canvas.drawPath(scalePath(stroke.path, toX, toY), maskPaint);

            float bmpW = originalBitmap.getWidth();
            float bmpH = originalBitmap.getHeight();
            canvas.drawBitmap(effectSrc, null,
                    new RectF(0, 0, bmpW * toX, bmpH * toY), srcInPaint);

            canvas.restoreToCount(saveCount);
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    //  触摸
    // ════════════════════════════════════════════════════════════════════════

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (originalBitmap == null || currentMode == MODE_NONE) {
            return super.onTouchEvent(event);
        }

        RectF imgRect = fitCenterRect(getWidth(), getHeight(),
                originalBitmap.getWidth(), originalBitmap.getHeight());
        float bx = (event.getX() - imgRect.left) / imgRect.width()  * originalBitmap.getWidth();
        float by = (event.getY() - imgRect.top)  / imgRect.height() * originalBitmap.getHeight();

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                currentPath     = new Path();
                currentPathMode = currentMode;
                currentPath.moveTo(bx, by);
                invalidate();
                return true;

            case MotionEvent.ACTION_MOVE:
                if (currentPath != null) {
                    currentPath.lineTo(bx, by);
                    invalidate();
                }
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (currentPath != null) {
                    currentPath.lineTo(bx, by);
                    strokes.add(new Stroke(currentPath, currentPathMode, strokeWidth));
                    currentPath = null;
                    invalidate();
                }
                return true;
        }
        return super.onTouchEvent(event);
    }

    // ════════════════════════════════════════════════════════════════════════
    //  内部辅助
    // ════════════════════════════════════════════════════════════════════════

    /** 后台线程预计算脱敏 Bitmap，完成后回调主线程刷新。 */
    private void rebuildEffectBitmapsAsync(final int intensity) {
        if (originalBitmap == null) return;
        final Bitmap srcSnapshot = originalBitmap; // 快照引用，线程安全
        bgExecutor.execute(() -> {
            Bitmap newMosaic = createMosaicEffect(srcSnapshot, intensity);
            Bitmap newBlur   = createGaussianBlurEffect(srcSnapshot, Math.max(1, intensity / 2));
            mainHandler.post(() -> {
                mosaicBitmap = newMosaic;
                blurBitmap   = newBlur;
                invalidate();
                if (effectReadyListener != null) effectReadyListener.onEffectReady();
            });
        });
    }

    private static RectF fitCenterRect(int viewW, int viewH, int bmpW, int bmpH) {
        float scale = Math.min((float) viewW / bmpW, (float) viewH / bmpH);
        float dw = bmpW * scale, dh = bmpH * scale;
        float left = (viewW - dw) / 2f, top = (viewH - dh) / 2f;
        return new RectF(left, top, left + dw, top + dh);
    }

    private static Path scalePath(Path src, float sx, float sy) {
        Matrix m = new Matrix();
        m.setScale(sx, sy);
        Path dst = new Path();
        src.transform(m, dst);
        return dst;
    }
}
