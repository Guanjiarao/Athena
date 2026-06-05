package com.whu.software.athena;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class CycleWheelView extends View {

    private static final float START_ANGLE = -32f;
    private static final float GAP_ANGLE = 6f;

    private final Paint segmentPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint markerLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint markerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint markerHaloPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint centerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint centerGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint centerTitlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint centerDayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint centerFacePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF arcRect = new RectF();

    private int cycleDays = 28;
    private int periodDays = 5;
    private int currentDayInCycle = 0;
    private String centerTitle = "\u7ecf\u671f";
    private String centerDay = "DAY 01";

    public CycleWheelView(Context context) {
        super(context);
        init();
    }

    public CycleWheelView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public CycleWheelView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        segmentPaint.setStyle(Paint.Style.STROKE);
        segmentPaint.setStrokeCap(Paint.Cap.ROUND);

        dotPaint.setStyle(Paint.Style.FILL);
        dotPaint.setColor(0xFFFFFFFF);

        labelPaint.setColor(0xFF988D86);
        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setTextSize(dp(16));

        markerLinePaint.setStyle(Paint.Style.STROKE);
        markerLinePaint.setStrokeWidth(dp(5));
        markerLinePaint.setStrokeCap(Paint.Cap.ROUND);
        markerLinePaint.setColor(0xFFF7B400);

        markerPaint.setStyle(Paint.Style.FILL);
        markerHaloPaint.setStyle(Paint.Style.STROKE);
        markerHaloPaint.setColor(0x66FFFFFF);
        markerHaloPaint.setStrokeWidth(dp(2));
        markerHaloPaint.setPathEffect(new DashPathEffect(new float[]{dp(5), dp(5)}, 0f));

        centerGlowPaint.setStyle(Paint.Style.FILL);
        centerPaint.setStyle(Paint.Style.FILL);

        centerTitlePaint.setColor(0xFF352D2B);
        centerTitlePaint.setTextAlign(Paint.Align.CENTER);
        centerTitlePaint.setTextSize(dp(28));
        centerTitlePaint.setFakeBoldText(true);

        centerDayPaint.setColor(0xB2352D2B);
        centerDayPaint.setTextAlign(Paint.Align.CENTER);
        centerDayPaint.setTextSize(dp(12));

        centerFacePaint.setColor(0xFF2D2522);
        centerFacePaint.setStyle(Paint.Style.STROKE);
        centerFacePaint.setStrokeWidth(dp(4));
        centerFacePaint.setStrokeCap(Paint.Cap.ROUND);
    }

    public void bindCycleData(int cycleDays,
                              int periodDays,
                              int currentDayInCycle,
                              @NonNull String centerTitle,
                              @NonNull String centerDay) {
        this.cycleDays = Math.max(cycleDays, 21);
        this.periodDays = Math.max(1, Math.min(periodDays, this.cycleDays - 1));
        this.currentDayInCycle = Math.max(0, Math.min(currentDayInCycle, this.cycleDays - 1));
        this.centerTitle = centerTitle;
        this.centerDay = centerDay;
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int desired = (int) dp(320);
        int width = resolveSize(desired, widthMeasureSpec);
        int height = resolveSize(desired, heightMeasureSpec);
        int size = Math.min(width, height);
        setMeasuredDimension(size, size);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float w = getWidth();
        float h = getHeight();
        float cx = w / 2f;
        float cy = h / 2f;
        float outerRadius = Math.min(w, h) * 0.34f;
        float stroke = outerRadius * 0.23f;

        segmentPaint.setStrokeWidth(stroke);
        arcRect.set(cx - outerRadius, cy - outerRadius, cx + outerRadius, cy + outerRadius);

        int ovulationDayOffset = Math.max(periodDays + 1, cycleDays - 14);
        int follicularDays = Math.max(1, ovulationDayOffset - periodDays);
        int lutealDays = Math.max(1, cycleDays - ovulationDayOffset - 1);

        float periodSweep = sweepForDays(periodDays);
        float follicularSweep = sweepForDays(follicularDays);
        float lutealSweep = sweepForDays(lutealDays);

        float periodStart = START_ANGLE;
        float follicularStart = periodStart + periodSweep + GAP_ANGLE;
        float ovulationAngle = follicularStart + follicularSweep + GAP_ANGLE * 0.5f;
        float lutealStart = follicularStart + follicularSweep + GAP_ANGLE;

        drawSegment(canvas, periodStart, periodSweep, 0xFFF9E4EB, stroke);
        drawSegment(canvas, follicularStart, follicularSweep, 0xFFE5F2DC, stroke);
        drawSegment(canvas, lutealStart, lutealSweep, 0xFFFFB33D, stroke);
        drawLutealDots(canvas, lutealStart, lutealSweep, cx, cy, outerRadius, stroke);

        drawOvulationFlower(canvas, ovulationAngle, cx, cy, outerRadius + stroke * 0.08f);
        drawLabels(canvas, cx, cy, outerRadius, stroke,
                periodStart + periodSweep * 0.55f,
                follicularStart + follicularSweep * 0.58f,
                lutealStart + lutealSweep * 0.45f,
                ovulationAngle);

        float currentAngle = START_ANGLE + (360f * currentDayInCycle / cycleDays);
        drawMarker(canvas, cx, cy, currentAngle, outerRadius, stroke);
        drawCenterOrb(canvas, cx, cy, outerRadius, stroke);
    }

    private float sweepForDays(int days) {
        return Math.max(18f, 360f * days / cycleDays - GAP_ANGLE);
    }

    private void drawSegment(Canvas canvas, float startAngle, float sweep, int color, float stroke) {
        segmentPaint.setShader(null);
        segmentPaint.setColor(color);
        if (color == 0xFFFFB33D) {
            segmentPaint.setShader(new LinearGradient(
                    arcRect.left, arcRect.top, arcRect.right, arcRect.bottom,
                    new int[]{0xFFFFC357, 0xFFFFA62C}, null, Shader.TileMode.CLAMP));
        }
        canvas.drawArc(arcRect, startAngle, sweep, false, segmentPaint);
    }

    private void drawLutealDots(Canvas canvas,
                                float startAngle,
                                float sweep,
                                float cx,
                                float cy,
                                float radius,
                                float stroke) {
        int dotCount = 8;
        float dotRadius = stroke * 0.1f;
        float dotOrbit = radius;
        for (int i = 0; i < dotCount; i++) {
            float t = (i + 0.7f) / (dotCount + 0.7f);
            float angle = (float) Math.toRadians(startAngle + sweep * t);
            float x = cx + (float) Math.cos(angle) * dotOrbit;
            float y = cy + (float) Math.sin(angle) * dotOrbit;
            canvas.drawCircle(x, y, dotRadius, dotPaint);
        }
    }

    private void drawLabels(Canvas canvas,
                            float cx,
                            float cy,
                            float radius,
                            float stroke,
                            float periodAngle,
                            float follicularAngle,
                            float lutealAngle,
                            float ovulationAngle) {
        drawOuterLabel(canvas, "\u6708\u7ecf\u671f", cx, cy, radius + stroke * 0.9f, periodAngle);
        drawOuterLabel(canvas, "\u5375\u6ce1\u671f", cx, cy, radius + stroke * 0.95f, follicularAngle);
        drawOuterLabel(canvas, "\u9ec4\u4f53\u671f", cx, cy, radius + stroke * 0.95f, lutealAngle);
        drawOuterLabel(canvas, "\u6392\u5375\u65e5", cx, cy, radius + stroke * 1.18f, ovulationAngle + 3f);
    }

    private void drawOuterLabel(Canvas canvas, String text, float cx, float cy, float radius, float degrees) {
        float angle = (float) Math.toRadians(degrees);
        float x = cx + (float) Math.cos(angle) * radius;
        float y = cy + (float) Math.sin(angle) * radius;
        canvas.save();
        canvas.rotate(degrees + 90f, x, y);
        canvas.drawText(text, x, y, labelPaint);
        canvas.restore();
    }

    private void drawOvulationFlower(Canvas canvas, float degrees, float cx, float cy, float distance) {
        float angle = (float) Math.toRadians(degrees);
        float fx = cx + (float) Math.cos(angle) * distance;
        float fy = cy + (float) Math.sin(angle) * distance;

        Paint petalPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        petalPaint.setStyle(Paint.Style.FILL);
        petalPaint.setColor(0xFFE9D6FF);
        Paint center = new Paint(Paint.ANTI_ALIAS_FLAG);
        center.setColor(0xFFD8BEFF);

        float petalOffset = dp(10);
        float petalRadius = dp(10);
        canvas.drawCircle(fx - petalOffset, fy, petalRadius, petalPaint);
        canvas.drawCircle(fx + petalOffset, fy, petalRadius, petalPaint);
        canvas.drawCircle(fx, fy - petalOffset, petalRadius, petalPaint);
        canvas.drawCircle(fx, fy + petalOffset, petalRadius, petalPaint);
        canvas.drawCircle(fx, fy, petalRadius * 0.95f, center);
    }

    private void drawMarker(Canvas canvas, float cx, float cy, float degrees, float radius, float stroke) {
        float angle = (float) Math.toRadians(degrees);
        float markerRadius = stroke * 0.23f;
        float markerOrbit = radius;
        float markerX = cx + (float) Math.cos(angle) * markerOrbit;
        float markerY = cy + (float) Math.sin(angle) * markerOrbit;

        float centerRadius = radius * 0.42f;
        float lineEndX = cx + (float) Math.cos(angle) * (centerRadius + dp(10));
        float lineEndY = cy + (float) Math.sin(angle) * (centerRadius + dp(10));

        canvas.drawLine(markerX, markerY, lineEndX, lineEndY, markerLinePaint);
        canvas.drawCircle(markerX, markerY, markerRadius * 1.65f, markerHaloPaint);

        markerPaint.setShader(new RadialGradient(
                markerX, markerY, markerRadius * 1.35f,
                new int[]{0xFFFFF4B0, 0xFFFFC22F}, null, Shader.TileMode.CLAMP));
        canvas.drawCircle(markerX, markerY, markerRadius * 1.15f, markerPaint);

        Paint inner = new Paint(Paint.ANTI_ALIAS_FLAG);
        inner.setColor(0xFFFFFFFF);
        canvas.drawCircle(markerX, markerY, markerRadius * 0.52f, inner);
    }

    private void drawCenterOrb(Canvas canvas, float cx, float cy, float radius, float stroke) {
        float centerRadius = radius * 0.42f;

        centerGlowPaint.setShader(new RadialGradient(
                cx, cy, centerRadius * 1.08f,
                new int[]{0x33FFD4E7, 0x00FFFFFF}, null, Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy, centerRadius * 1.08f, centerGlowPaint);

        centerPaint.setShader(new RadialGradient(
                cx, cy, centerRadius,
                new int[]{0xFFFFF3F8, 0xFFFFC3DD, 0xFFF48DB6}, new float[]{0f, 0.55f, 1f},
                Shader.TileMode.CLAMP));

        Path blob = new Path();
        int points = 16;
        for (int i = 0; i < points; i++) {
            float angle = (float) (Math.PI * 2 * i / points);
            float r = (i % 2 == 0) ? centerRadius * 0.98f : centerRadius * 0.88f;
            float x = cx + (float) Math.cos(angle) * r;
            float y = cy + (float) Math.sin(angle) * r;
            if (i == 0) {
                blob.moveTo(x, y);
            } else {
                blob.lineTo(x, y);
            }
        }
        blob.close();
        canvas.drawPath(blob, centerPaint);

        Paint highlight = new Paint(Paint.ANTI_ALIAS_FLAG);
        highlight.setColor(0x66FFFFFF);
        canvas.drawCircle(cx + centerRadius * 0.22f, cy - centerRadius * 0.18f, centerRadius * 0.15f, highlight);

        canvas.drawText(centerTitle, cx, cy + dp(6), centerTitlePaint);
        canvas.drawText(centerDay, cx, cy + centerRadius * 0.48f, centerDayPaint);

        Path face = new Path();
        face.moveTo(cx - centerRadius * 0.28f, cy - centerRadius * 0.05f);
        face.lineTo(cx - centerRadius * 0.17f, cy - centerRadius * 0.12f);
        face.moveTo(cx + centerRadius * 0.10f, cy - centerRadius * 0.12f);
        face.lineTo(cx + centerRadius * 0.22f, cy - centerRadius * 0.04f);
        face.moveTo(cx - centerRadius * 0.12f, cy + centerRadius * 0.10f);
        face.cubicTo(cx - centerRadius * 0.02f, cy + centerRadius * 0.22f,
                cx + centerRadius * 0.12f, cy + centerRadius * 0.22f,
                cx + centerRadius * 0.22f, cy + centerRadius * 0.08f);
        canvas.drawPath(face, centerFacePaint);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
