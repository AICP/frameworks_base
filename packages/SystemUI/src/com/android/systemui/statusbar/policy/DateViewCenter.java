/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.systemui.statusbar.policy;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.text.format.DateFormat;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewParent;
import android.widget.TextView;
import android.provider.Settings;
import android.os.Handler;

import com.android.systemui.R;
import com.android.internal.util.aokp.StatusBarHelpers;

import java.util.Date;

public class DateViewCenter extends TextView {
    private static final String TAG = "DateViewCenter";
    private SettingsObserver mSettingsObserver;
    private boolean mAttached;
    private int mClockColor;
    private int mStockFontSize;
    private boolean mShowDate;
    protected int mClockStyle = Clock.STYLE_CLOCK_RIGHT;
    
    private BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (Intent.ACTION_TIME_TICK.equals(action)
                    || Intent.ACTION_TIME_CHANGED.equals(action)
                    || Intent.ACTION_TIMEZONE_CHANGED.equals(action)) {
                updateClock();
            }
        }
    };

    public DateViewCenter(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    protected void updateClock() {
        final String dateFormat = getContext().getString(R.string.system_ui_date_pattern);
        setText(DateFormat.format(dateFormat, new Date()));
    }
    
    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!mAttached) {
        	mAttached = true;

            mClockColor = getTextColors().getDefaultColor();
            mStockFontSize = StatusBarHelpers.pixelsToSp(mContext,getTextSize());

            // Register for Intent broadcasts for the clock and battery
            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_TIME_TICK);
            filter.addAction(Intent.ACTION_TIME_CHANGED);
            filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
            mContext.registerReceiver(mIntentReceiver, filter, null, null);

            mSettingsObserver = new SettingsObserver(new Handler());
            mSettingsObserver.observe();
        }
        updateSettings();
    }    

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (mAttached) {
            getContext().unregisterReceiver(mIntentReceiver);
            mContext.getContentResolver().unregisterContentObserver(mSettingsObserver);
            mAttached = false;
        }
    }
        
    protected class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(Settings.System
                    .getUriFor(Settings.System.STATUSBAR_SHOW_DATE), false,
                    this);
            resolver.registerContentObserver(Settings.System
                    .getUriFor(Settings.System.STATUSBAR_CLOCK_COLOR), false,
                    this);
            resolver.registerContentObserver(Settings.System
                    .getUriFor(Settings.System.STATUSBAR_CLOCK_STYLE), false,
                    this);

            updateSettings();
        }

        @Override
        public void onChange(boolean selfChange) {
            updateSettings();
        }
    }

    protected void updateSettings() {
        ContentResolver resolver = mContext.getContentResolver();
        int newColor = 0;

        newColor = Settings.System.getInt(resolver,
                Settings.System.STATUSBAR_CLOCK_COLOR, mClockColor);
		mShowDate = Settings.System.getBoolean(resolver,
                Settings.System.STATUSBAR_SHOW_DATE, false);
        mClockStyle = Settings.System.getInt(resolver,
                Settings.System.STATUSBAR_CLOCK_STYLE, Clock.STYLE_CLOCK_RIGHT);

        if (newColor < 0 && newColor != mClockColor) {
            mClockColor = newColor;
            setTextColor(mClockColor);
        }
        updateDateVisibility();
        updateClock();
    }

    protected void updateDateVisibility() {
    	if (mShowDate && mClockStyle != Clock.STYLE_CLOCK_CENTER){
        	setVisibility(View.VISIBLE);
        } else {
        	setVisibility(View.GONE);
        }
    }
}
