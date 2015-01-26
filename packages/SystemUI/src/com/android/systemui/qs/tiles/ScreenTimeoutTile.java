/*
 * Copyright (C) 2014 The Android Open Source Project
 * Copyright (C) 2013-2015 The CyanogenMod Project
 * Copyright (C) 2015 The Euphoria-OS Project
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

package com.android.systemui.qs.tiles;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.os.UserHandle;
import android.provider.Settings;
import android.view.View;

import com.android.systemui.R;
import com.android.systemui.qs.QSTile;

/** Quick settings tile: Screen timeout **/
public class ScreenTimeoutTile extends QSTile<QSTile.BooleanState> {

    // timeout values
    private static final int SCREEN_TIMEOUT_MIN    =  15000;
    private static final int SCREEN_TIMEOUT_LOW    =  30000;
    private static final int SCREEN_TIMEOUT_NORMAL =  60000;
    private static final int SCREEN_TIMEOUT_HIGH   = 120000;
    private static final int SCREEN_TIMEOUT_MAX    = 300000;

    private static final int MODE_15_60_300 = 0;
    private static final int MODE_30_120_300 = 1;

    private static final Intent DISPLAY_SETTINGS = new Intent(Settings.ACTION_DISPLAY_SETTINGS);

    private boolean mListening;

    public ScreenTimeoutTile(Host host) {
        super(host);
    }

    @Override
    protected BooleanState newTileState() {
        return new BooleanState();
    }

    public void setListening(boolean listening) {
        if (mListening == listening) return;
        mListening = listening;
    }

    @Override
    public void handleClick() {
        toggleState();
        refreshState();
    }

    @Override
    protected void handleSecondaryClick() {
        mHost.startSettingsActivity(DISPLAY_SETTINGS);
    }

    @Override
    public void handleLongClick() {
        mHost.startSettingsActivity(DISPLAY_SETTINGS);
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        int timeout = getScreenTimeout();
        state.visible = true;
        state.label = makeTimeoutSummaryString(mContext, timeout);
        state.iconId = R.drawable.ic_qs_screen_timeout_on;
    }

    protected void toggleState() {
        int screenTimeout = getScreenTimeout();
        int currentMode = getCurrentMode();

        if (screenTimeout < SCREEN_TIMEOUT_MIN) {
            if (currentMode == MODE_15_60_300) {
                screenTimeout = SCREEN_TIMEOUT_MIN;
            } else {
                screenTimeout = SCREEN_TIMEOUT_LOW;
            }
        } else if (screenTimeout < SCREEN_TIMEOUT_LOW) {
            if (currentMode == MODE_15_60_300) {
                screenTimeout = SCREEN_TIMEOUT_NORMAL;
            } else {
                screenTimeout = SCREEN_TIMEOUT_LOW;
            }
        } else if (screenTimeout < SCREEN_TIMEOUT_NORMAL) {
            if (currentMode == MODE_15_60_300) {
                screenTimeout = SCREEN_TIMEOUT_NORMAL;
            } else {
                screenTimeout = SCREEN_TIMEOUT_HIGH;
            }
        } else if (screenTimeout < SCREEN_TIMEOUT_HIGH) {
            if (currentMode == MODE_15_60_300) {
                screenTimeout = SCREEN_TIMEOUT_MAX;
            } else {
                screenTimeout = SCREEN_TIMEOUT_HIGH;
            }
        } else if (screenTimeout < SCREEN_TIMEOUT_MAX) {
            screenTimeout = SCREEN_TIMEOUT_MAX;
        } else if (currentMode == MODE_30_120_300) {
            screenTimeout = SCREEN_TIMEOUT_LOW;
        } else {
            screenTimeout = SCREEN_TIMEOUT_MIN;
        }

        Settings.System.putIntForUser(mContext.getContentResolver(),
                Settings.System.SCREEN_OFF_TIMEOUT, screenTimeout, UserHandle.USER_CURRENT);
    }

    private String makeTimeoutSummaryString(Context context, int timeout) {
        Resources res = context.getResources();
        int resId;
        String timeoutSummary = null;

        /* ms -> seconds */
        timeout /= 1000;

        if (timeout >= 60 && timeout % 60 == 0) {
            /* seconds -> minutes */
            timeout /= 60;
            if (timeout >= 60 && timeout % 60 == 0) {
                /* minutes -> hours */
                timeout /= 60;
                resId = timeout == 1
                        ? com.android.internal.R.string.hour
                        : com.android.internal.R.string.hours;
            } else {
                resId = timeout == 1
                        ? com.android.internal.R.string.minute
                        : com.android.internal.R.string.minutes;
            }
        } else {
            resId = timeout == 1
                    ? com.android.internal.R.string.second
                    : com.android.internal.R.string.seconds;
        }

        timeoutSummary = res.getString(R.string.quick_settings_screen_timeout_summary,
                    timeout, res.getString(resId));
        return timeoutSummary;
    }

    private int getScreenTimeout() {
        return Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.SCREEN_OFF_TIMEOUT, 0, UserHandle.USER_CURRENT);
    }

    private int getCurrentMode() {
        return Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.QS_EXPANDED_SCREENTIMEOUT_MODE, MODE_15_60_300,
                UserHandle.USER_CURRENT);
    }
}

