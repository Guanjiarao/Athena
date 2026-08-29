package com.whu.software.athena;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

/** Segmented cycle dial: neutral track, phase arcs, inner ticks and a progress marker. */
public class CycleStatusRingView extends View {
    private final Paint trackEdgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint periodArcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint phaseArcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint markerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint markerDotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint markerCaptionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint markerNumberPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arcBounds = new RectF();
    private float progress = 0.62f;
    @Nullable private String markerLabel;

    public CycleStatusRingView(Context context) {
        this(context, null);
    }

    public CycleStatusRingView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        float density = getResources().getDisplayMetrics().density;
        trackEdgePaint.setStyle(Paint.Style.STROKE);
        trackEdgePaint.setStrokeWidth(23f * density);
        trackEdgePaint.setStrokeCap(Paint.Cap.ROUND);
        trackEdgePaint.setColor(0x24FFFFFF);
        trackEdgePaint.setShadowLayer(7f * density, 0f, 2f * density, 0x18000000);

        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeWidth(20f * density);
        trackPaint.setStrokeCap(Paint.Cap.ROUND);
        trackPaint.setColor(0x70FFFFFF);

        // Reuse the health dashboard hero palette: pink #FFB6D4 and purple #B6A5FF.
        configureArcPaint(periodArcPaint, Color.rgb(255, 182, 212), density);
        configureArcPaint(phaseArcPaint, Color.rgb(182, 165, 255), density);
        tickPaint.setStyle(Paint.Style.FILL);
        tickPaint.setColor(Color.rgb(222, 222, 222));
        markerPaint.setStyle(Paint.Style.FILL);
        markerPaint.setColor(Color.WHITE);
        markerPaint.setShadowLayer(4f * density, 0f, 1.5f * density, 0x36000000);
        markerDotPaint.setStyle(Paint.Style.FILL);
        markerDotPaint.setColor(Color.rgb(217, 185, 243));
        markerCaptionPaint.setColor(Color.rgb(75, 75, 75));
        markerCaptionPaint.setTextAlign(Paint.Align.CENTER);
        markerCaptionPaint.setTextSize(6f * density);
        markerCaptionPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        markerNumberPaint.setColor(Color.rgb(28, 28, 28));
        markerNumberPaint.setTextAlign(Paint.Align.CENTER);
        markerNumberPaint.setTextSize(14f * density);
        markerNumberPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        setLayerType(LAYER_TYPE_SOFTWARE, null);
    }

    private void configureArcPaint(Paint paint, int color, float density) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(21f * density);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setColor(color);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        float cx = w / 2f;
        float cy = h / 2f;
        Matrix rotation = new Matrix();
        rotation.setRotate(-90f, cx, cy);

        SweepGradient periodGradient = new SweepGradient(cx, cy,
                new int[]{0xFFFFD2E3, 0xFFFF9FC7, 0xFFDDA9FF, 0xFFFFD2E3},
                new float[]{0f, 0.18f, 0.34f, 1f});
        periodGradient.setLocalMatrix(rotation);
        periodArcPaint.setShader(periodGradient);

        SweepGradient phaseGradient = new SweepGradient(cx, cy,
                new int[]{0xFFD8CEFF, 0xFFA99BFF, 0xFFF2B8E7, 0xFFD8CEFF},
                new float[]{0f, 0.30f, 0.62f, 1f});
        phaseGradient.setLocalMatrix(rotation);
        phaseArcPaint.setShader(phaseGradient);
    }

    public void setProgress(float value) {
        progress = Math.max(0f, Math.min(1f, value));
        invalidate();
    }

    public void setMarkerLabel(@Nullable String label) {
        markerLabel = label;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float density = getResources().getDisplayMetrics().density;
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float radius = Math.min(getWidth(), getHeight()) / 2f - 26f * density;

        arcBounds.set(cx - radius, cy - radius, cx + radius, cy + radius);
        // A soft translucent rim plus a brighter inner stroke creates a frosted-glass track.
        canvas.drawArc(arcBounds, -90f, 360f, false, trackEdgePaint);
        canvas.drawArc(arcBounds, -90f, 360f, false, trackPaint);

        // Fixed phase segments mirror the reference while leaving most of the cycle neutral.
        canvas.drawArc(arcBounds, -82f, 56f, false, periodArcPaint);
        canvas.drawArc(arcBounds, 34f, 96f, false, phaseArcPaint);

        float tickRadius = radius - 25f * density;
        for (int i = 0; i < 28; i++) {
            double angle = Math.toRadians(-90f + i * (360f / 28f));
            float x = cx + (float) Math.cos(angle) * tickRadius;
            float y = cy + (float) Math.sin(angle) * tickRadius;
            canvas.drawCircle(x, y, 1.35f * density, tickPaint);
        }

        // A small phase dot sits within the teal segment, as in the visual reference.
        double phaseDotAngle = Math.toRadians(88f);
        float dotX = cx + (float) Math.cos(phaseDotAngle) * radius;
        float dotY = cy + (float) Math.sin(phaseDotAngle) * radius;
        canvas.drawCircle(dotX, dotY, 6f * density, markerDotPaint);

        // The day badge stays clear of the colored arcs and does not crowd the center copy.
        if (markerLabel != null && !markerLabel.isEmpty()) {
            double markerAngle = Math.toRadians(140f);
            float markerX = cx + (float) Math.cos(markerAngle) * radius;
            float markerY = cy + (float) Math.sin(markerAngle) * radius;
            canvas.drawCircle(markerX, markerY, 19f * density, markerPaint);
            canvas.drawText("DAY", markerX, markerY - 4f * density, markerCaptionPaint);
            canvas.drawText(markerLabel, markerX, markerY + 11f * density, markerNumberPaint);
        }
    }
}
