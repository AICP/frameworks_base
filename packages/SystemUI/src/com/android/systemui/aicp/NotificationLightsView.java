/*
* Copyright (C) 2019 crDroid Android Project
*
* This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU General Public License as published by
* the Free Software Foundation, either version 2 of the License, or
* (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
* GNU General Public License for more details.
*
* You should have received a copy of the GNU General Public License
* along with this program. If not, see <http://www.gnu.org/licenses/>.
*
*/
package com.android.systemui.aicp;

import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.app.WallpaperColors;
import android.app.WallpaperManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;

import androidx.palette.graphics.Palette;

import com.android.settingslib.Utils;
import com.android.systemui.R;

public class NotificationLightsView extends RelativeLayout {

    private View mNotificationAnimView;
    private ValueAnimator mLightAnimator;
    private boolean mPulsing;
    private WallpaperManager mWallManager;
    private int color;

    public NotificationLightsView(Context context) {
        this(context, null);
    }

    public NotificationLightsView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public NotificationLightsView(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public NotificationLightsView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        Log.e("NotificationLightsView", "new");
    }

    private Runnable mLightUpdate = new Runnable() {
        @Override
        public void run() {
            Log.e("NotificationLightsView", "run");
            animateNotification();
        }
    };

    public void setPulsing(boolean pulsing) {
        if (mPulsing == pulsing) {
            return;
        }
        mPulsing = pulsing;
    }

    @Override
    public void draw(Canvas canvas) {
        super.draw(canvas);
        Log.e("NotificationLightsView", "draw");
    }

    public void animateNotification() {
        int customColor = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.PULSE_AMBIENT_LIGHT_COLOR, 0xFF3980FF,
                UserHandle.USER_CURRENT);
        int duration = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.PULSE_AMBIENT_LIGHT_DURATION, 2,
                UserHandle.USER_CURRENT) * 1000;
        int repeat = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.PULSE_AMBIENT_LIGHT_REPEAT_COUNT, 0,
                UserHandle.USER_CURRENT);
        int colorMode = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.PULSE_AMBIENT_LIGHT_COLOR_MODE, 1,
                UserHandle.USER_CURRENT);

        if (colorMode == 0) {
            try {
                WallpaperManager wallpaperManager = WallpaperManager.getInstance(mContext);
                Drawable wallpaperDrawable = wallpaperManager.getDrawable();
                Bitmap bitmap = ((BitmapDrawable)wallpaperDrawable).getBitmap();
                if (bitmap != null) {
                    Palette p = Palette.from(bitmap).generate();
                    int wallColor = p.getDominantColor(color);
                    if (color != wallColor)
                        color = wallColor;
                }
            } catch (Exception e) {
                // Nothing to do
            }
        } else if (colorMode == 1) {
            color = Utils.getColorAccentDefaultColor(getContext());
        } else {
            color = customColor;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("animateNotification color ");
        sb.append(Integer.toHexString(color));
        Log.e("NotificationLightsView", sb.toString());
        ImageView leftView = (ImageView) findViewById(R.id.notification_animation_left);
        ImageView rightView = (ImageView) findViewById(R.id.notification_animation_right);
        leftView.setColorFilter(color);
        rightView.setColorFilter(color);
        mLightAnimator = ValueAnimator.ofFloat(new float[]{0.0f, 2.0f});
        mLightAnimator.setDuration(duration);
        if (repeat == 0) {
            mLightAnimator.setRepeatCount(ValueAnimator.INFINITE);
        } else {
            mLightAnimator.setRepeatCount(repeat);
        }
        mLightAnimator.setRepeatMode(ValueAnimator.RESTART);
        mLightAnimator.addUpdateListener(new AnimatorUpdateListener() {
            public void onAnimationUpdate(ValueAnimator animation) {
                Log.e("NotificationLightsView", "onAnimationUpdate");
                float progress = ((Float) animation.getAnimatedValue()).floatValue();
                leftView.setScaleY(progress);
                rightView.setScaleY(progress);
                float alpha = 1.0f;
                if (progress <= 0.3f) {
                    alpha = progress / 0.3f;
                } else if (progress >= 1.0f) {
                    alpha = 2.0f - progress;
                }
                leftView.setAlpha(alpha);
                rightView.setAlpha(alpha);
            }
        });
        Log.e("NotificationLightsView", "start");
        mLightAnimator.start();
    }
}
