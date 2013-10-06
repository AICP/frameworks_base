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

package com.android.systemui.statusbar.phone;

import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Handler;
import android.provider.Settings;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.RelativeLayout;

import com.android.systemui.R;

import java.util.Random;

/**
 *
 */
public class QuickSettingsTileView extends RelativeLayout {

    private int mColSpan;
    private int mRowSpan;
    private int mCellWidth;

    private boolean mAttached = false;

    private SettingsObserver mSettingsObserver;
    private Handler mHandler = new Handler();

    public QuickSettingsTileView(Context context, AttributeSet attrs) {
        super(context, attrs);

        mColSpan = 1;
        mRowSpan = 1;

        setTileBackground();

    }

   @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        if (!mAttached) {
            mAttached = true;
            mSettingsObserver = new SettingsObserver(new Handler());
            mSettingsObserver.observe();
            setTileBackground();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        if (mAttached) {
            mContext.getContentResolver().unregisterContentObserver(mSettingsObserver);
            mAttached = false;
        }
    }

    void setColumnSpan(int span) {
        mColSpan = span;
    }

    public int getColumnSpan() {
        return mColSpan;
    }

    public void setContent(int layoutId, LayoutInflater inflater) {
        inflater.inflate(layoutId, this);
    }

    public class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver cr = mContext.getContentResolver();

            cr.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.QUICK_SETTINGS_BACKGROUND_STYLE), false, this);
            cr.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.QUICK_SETTINGS_BACKGROUND_COLOR), false, this);            
            cr.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.QUICK_SETTINGS_BACKGROUND_PRESSED_COLOR), false, this);
            cr.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.RANDOM_COLOR_ONE), false, this);
            cr.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.RANDOM_COLOR_TWO), false, this);
            cr.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.RANDOM_COLOR_THREE), false, this);
            cr.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.RANDOM_COLOR_FOUR), false, this);
            cr.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.RANDOM_COLOR_FIVE), false, this);
            cr.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.RANDOM_COLOR_SIX), false, this);

            setTileBackground();
        }

        @Override
        public void onChange(boolean selfChange) {
            setTileBackground();
        }
    }

    public void setTileBackground() {
        ContentResolver cr = mContext.getContentResolver();
        int tileBg = Settings.System.getInt(cr,
                Settings.System.QUICK_SETTINGS_BACKGROUND_STYLE, 2);
        int blueDark = Settings.System.getInt(cr,
                Settings.System.RANDOM_COLOR_ONE, android.R.color.holo_blue_dark);
        int greenDark = Settings.System.getInt(cr,
                Settings.System.RANDOM_COLOR_TWO, android.R.color.holo_green_dark);
        int redDark = Settings.System.getInt(cr,
                Settings.System.RANDOM_COLOR_THREE, android.R.color.holo_red_dark);
        int orangeDark = Settings.System.getInt(cr,
                Settings.System.RANDOM_COLOR_FOUR, android.R.color.holo_orange_dark);
        int purple = Settings.System.getInt(cr,
                Settings.System.RANDOM_COLOR_FIVE, android.R.color.holo_purple);
        int blueBright = Settings.System.getInt(cr,
                Settings.System.RANDOM_COLOR_SIX, android.R.color.holo_blue_bright);
        if (tileBg == 1) {
            int tileBgColor = Settings.System.getInt(cr,
                    Settings.System.QUICK_SETTINGS_BACKGROUND_COLOR, 0xFF000000);
            int presBgColor = Settings.System.getInt(cr,
                    Settings.System.QUICK_SETTINGS_BACKGROUND_PRESSED_COLOR, 0xFF161616);
            ColorDrawable bgDrawable = new ColorDrawable(tileBgColor);
            ColorDrawable presDrawable = new ColorDrawable(presBgColor);
            StateListDrawable states = new StateListDrawable();
            states.addState(new int[] {android.R.attr.state_pressed}, presDrawable);
            states.addState(new int[] {}, bgDrawable);
            this.setBackground(states);
        } else if (tileBg == 0) {
            int[] Colors = new int[] {
                blueDark,
                greenDark,
                redDark,
                orangeDark,
                purple,
                blueBright
            };
            Random generator = new Random();
            int presBgColor = Settings.System.getInt(cr,
                    Settings.System.QUICK_SETTINGS_BACKGROUND_PRESSED_COLOR, 0xFF161616);
            ColorDrawable bgDrawable = new ColorDrawable(Colors[generator.nextInt(Colors.length)]);
            ColorDrawable presDrawable = new ColorDrawable(presBgColor);
            StateListDrawable states = new StateListDrawable();
            states.addState(new int[] {android.R.attr.state_pressed}, presDrawable);
            states.addState(new int[] {}, bgDrawable);
            this.setBackground(states);
        } else {
            setBackgroundResource(R.drawable.qs_tile_background);
        }
    }
}
