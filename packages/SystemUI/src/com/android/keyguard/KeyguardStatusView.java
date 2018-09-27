/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.keyguard;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;

import androidx.core.graphics.ColorUtils;

import com.android.systemui.R;
import com.android.systemui.omni.CurrentWeatherView;
import com.android.systemui.statusbar.CrossFadeHelper;

import java.io.PrintWriter;
import java.util.Set;

/**
 * View consisting of:
 * - keyguard clock
 * - media player (split shade mode only)
 */
public class KeyguardStatusView extends GridLayout {
    private static final boolean DEBUG = KeyguardConstants.DEBUG;
    private static final String TAG = "KeyguardStatusView";

    private ViewGroup mStatusViewContainer;
    private KeyguardClockSwitch mClockView;
    private KeyguardSliceView mKeyguardSlice;
    private View mMediaHostContainer;
    private CurrentWeatherView mWeatherView;

    private float mDarkAmount = 0;
    private int mTextColor;

    public KeyguardStatusView(Context context) {
        this(context, null, 0);
    }

    public KeyguardStatusView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public KeyguardStatusView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mStatusViewContainer = findViewById(R.id.status_view_container);

        mClockView = findViewById(R.id.keyguard_clock_container);
        if (KeyguardClockAccessibilityDelegate.isNeeded(mContext)) {
            mClockView.setAccessibilityDelegate(new KeyguardClockAccessibilityDelegate(mContext));
        }

        mKeyguardSlice = findViewById(R.id.keyguard_slice_view);

        mWeatherView = (CurrentWeatherView) findViewById(R.id.weather_container);

        mTextColor = mClockView.getCurrentTextColor();

        mMediaHostContainer = findViewById(R.id.status_view_media_container);

        updateDark();
        updateSettings();

    }

    void setDarkAmount(float darkAmount) {
        if (mDarkAmount == darkAmount) {
            return;
        }
        mDarkAmount = darkAmount;
        mClockView.setDarkAmount(darkAmount);
        CrossFadeHelper.fadeOut(mMediaHostContainer, darkAmount);
        updateDark();
    }

    void updateDark() {
        final int blendedTextColor = ColorUtils.blendARGB(mTextColor, Color.WHITE, mDarkAmount);
        mKeyguardSlice.setDarkAmount(mDarkAmount);
        mClockView.setTextColor(blendedTextColor);
        if (mWeatherView != null) {
            mWeatherView.blendARGB(mDarkAmount);
        }
    }

    /** Sets a translationY value on every child view except for the media view. */
    public void setChildrenTranslationYExcludingMediaView(float translationY) {
        setChildrenTranslationYExcluding(translationY, Set.of(mMediaHostContainer));
    }

    /** Sets a translationY value on every view except for the views in the provided set. */
    private void setChildrenTranslationYExcluding(float translationY, Set<View> excludedViews) {
        for (int i = 0; i < mStatusViewContainer.getChildCount(); i++) {
            final View child = mStatusViewContainer.getChildAt(i);

            if (!excludedViews.contains(child)) {
                child.setTranslationY(translationY);
            }
        }
    }

    public void dump(PrintWriter pw, String[] args) {
        pw.println("KeyguardStatusView:");
        pw.println("  mDarkAmount: " + mDarkAmount);
        pw.println("  mTextColor: " + Integer.toHexString(mTextColor));
        if (mClockView != null) {
            mClockView.dump(pw, args);
        }
        if (mKeyguardSlice != null) {
            mKeyguardSlice.dump(pw, args);
        }
    }

    protected void updateSettings() {
        final ContentResolver resolver = getContext().getContentResolver();
        final Resources res = getContext().getResources();
        boolean showWeather = Settings.System.getIntForUser(resolver,
                Settings.System.OMNI_LOCKSCREEN_WEATHER_ENABLED, 0,
                UserHandle.USER_CURRENT) == 1;

        if (mWeatherView != null) {
            if (showWeather) {
                mWeatherView.setVisibility(View.VISIBLE);
                mWeatherView.enableUpdates();
            }
            if (!showWeather) {
                mWeatherView.setVisibility(View.GONE);
                mWeatherView.disableUpdates();
            }
        }
    }
}
