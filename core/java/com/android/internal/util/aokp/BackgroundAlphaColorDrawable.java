
package com.android.internal.util.aokp;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff.Mode;
import android.graphics.drawable.ColorDrawable;

public class BackgroundAlphaColorDrawable extends ColorDrawable {
    int mBgColor;
    int mAlpha = 255;
    int mComputedDrawColor = 0;

    public BackgroundAlphaColorDrawable(int bgColor) {
        setBgColor(mBgColor = bgColor);
        updateColor();
    }

    public void setBgColor(int color) {
        if (color < 0) {
            color = Color.BLACK;
        }
        mBgColor = color;
        updateColor();
    }

    @Override
    public void setAlpha(int alpha) {
        if (alpha > 255) {
            alpha = 255;
        } else if (alpha < 0) {
            alpha = 0;
        }
        mAlpha = alpha;
        updateColor();
    }

    public int getBgColor() {
        return mBgColor;
    }

    @Override
    public void draw(Canvas canvas) {
        canvas.drawColor(mComputedDrawColor, Mode.SRC);
    }

    private void updateColor() {
        int r = Color.red(mBgColor);
        int g = Color.green(mBgColor);
        int b = Color.blue(mBgColor);
        mComputedDrawColor = Color.argb(mAlpha, r, g, b);
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
