package com.whu.software.athena.utils;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;

public class PrivacyUtil {

    public static Bitmap applyPrivacyMosaic(Bitmap original) {
        int width = original.getWidth();
        int height = original.getHeight();

        // 1. 制造“极度毛玻璃”背景 (通过极度缩小再放大，形成高级的模糊感，而不是低级的方块马赛克)
        int blurScale = 25; // 模糊程度，数值越大越糊
        Bitmap tiny = Bitmap.createScaledBitmap(original, width / blurScale, height / blurScale, true);
        Bitmap blurBg = Bitmap.createScaledBitmap(tiny, width, height, true);
        tiny.recycle();

        // 2. 准备合成画布
        Bitmap result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(result);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        // 3. 画上毛玻璃背景，并蒙上一层极浅的白色（增加医学高级感）
        canvas.drawBitmap(blurBg, 0, 0, paint);
        canvas.drawColor(0x33FFFFFF); // 20%透明度的纯白遮罩
        blurBg.recycle();

        // 4. 计算中心保留区 (宽60%, 高80%)
        int cropWidth = (int) (width * 0.6f);
        int cropHeight = (int) (height * 0.8f);
        int left = (width - cropWidth) / 2;
        int top = (height - cropHeight) / 2;
        Rect centerRect = new Rect(left, top, left + cropWidth, top + cropHeight);
        RectF centerRectF = new RectF(centerRect);

        // 5. 挖空中心，露出清晰的试纸（带平滑圆角，过渡自然）
        canvas.saveLayer(0, 0, width, height, null);
        paint.setColor(0xFFFFFFFF);
        canvas.drawRoundRect(centerRectF, 30f, 30f, paint);

        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        canvas.drawBitmap(original, centerRect, centerRect, paint);

        paint.setXfermode(null);
        canvas.restore();

        return result;
    }
}