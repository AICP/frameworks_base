package com.android.systemui.statusbar;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.drawable.ColorDrawable;

public class BackgroundAlphaColorDrawable extends ColorDrawable {
    int mBgColor;
    int mAlpha = 255;

    public BackgroundAlphaColorDrawable(int bgColor) {
        mBgColor = bgColor;
    }

    public void setBgColor(int color) {
        mBgColor = color;
    }

    public int getBgColor() {
        return mBgColor;
    }

    @Override
    public void draw(Canvas canvas) {
        canvas.drawColor(mBgColor, PorterDuff.Mode.SRC);
    }

    @Override
    public void setAlpha(int alpha) {
        if(alpha != mAlpha) {
            int r = Color.red(mBgColor);
            int g = Color.green(mBgColor);
            int b = Color.blue(mBgColor);
            setBgColor(Color.argb(alpha, r, g, b));
        }
        mAlpha = alpha;
        invalidateSelf();
    }

    @Override
    public int getAlpha() {
        return mAlpha;
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
