/**
 * Copyright (c) 2013, The Android Open Kang Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.statusbar.policy;

import android.content.Context;
import android.database.ContentObserver;
import android.os.Handler;
import android.provider.Settings;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TextView;

import com.android.systemui.R;
import com.android.systemui.statusbar.policy.BatteryController.BatteryStateChangeCallback;

public class BatteryTextMeterView extends TextView implements BatteryStateChangeCallback {

    private boolean mEnabled; // is indicator enabled
    private boolean mPluggedEnabled; // indicate charging
    private int mLevel;
    private boolean mPlugged;

    private ContentObserver mObserver = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
            updateSettings();
        }

        public void onChange(boolean selfChange, android.net.Uri uri) {
            updateSettings();
        };
    };

    public BatteryTextMeterView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        getContext().getContentResolver().registerContentObserver(
                Settings.AOKP.getUriFor(Settings.AOKP.BATTERY_PERCENTAGE_INDICATOR),
                false, mObserver);
        getContext().getContentResolver().registerContentObserver(
                Settings.AOKP.getUriFor(Settings.AOKP.BATTERY_PERCENTAGE_INDICATOR_PLUGGED),
                false, mObserver);
        updateSettings();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        getContext().getContentResolver().unregisterContentObserver(mObserver);
    }

    private void updateSettings() {
        mEnabled = Settings.AOKP.getBoolean(mContext.getContentResolver(),
                        Settings.AOKP.BATTERY_PERCENTAGE_INDICATOR, false);
        mPluggedEnabled = Settings.AOKP.getBoolean(mContext.getContentResolver(),
                        Settings.AOKP.BATTERY_PERCENTAGE_INDICATOR_PLUGGED, false);
        refreshView();
    }

    private void refreshView() {
        if(mEnabled) {
            setText(String.format(getContext().getString(R.string.battery_percent_format), mLevel));
            if(getVisibility() != View.VISIBLE) {
                setVisibility(View.VISIBLE);
            }
            if(mPluggedEnabled && mPlugged) {
                setTextAppearance(getContext(), R.style.BatteryTextPlugged);
            } else {
                setTextAppearance(getContext(), R.style.BatteryText);
            }
        } else {
            if(getVisibility() != View.GONE) {
                setText(null);
                setVisibility(View.GONE);
            }
        }
    }

    @Override
    public void onBatteryLevelChanged(int level, boolean pluggedIn) {
        mLevel = level;
        mPlugged = pluggedIn;
        refreshView();
    }
}
