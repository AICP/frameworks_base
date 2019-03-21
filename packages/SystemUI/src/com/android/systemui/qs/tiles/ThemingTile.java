/*
 * Copyright (C) 2014 The Android Open Source Project
 * Copyright (C) 2019 Android Ice Cold Project
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

import android.content.Intent;
import android.os.Handler;
import android.os.Process;
import android.os.UserHandle;
import android.preference.PreferenceActivity;
import android.provider.Settings;
import android.service.quicksettings.Tile;

import com.android.systemui.R;
import com.android.systemui.plugins.qs.QSTile;
import com.android.systemui.plugins.qs.QSTile.State;
import com.android.systemui.qs.QSHost;
import com.android.systemui.qs.tileimpl.QSTileImpl;

import com.android.internal.logging.nano.MetricsProto.MetricsEvent;

import com.aicp.gear.util.ThemeOverlayHelper;

public class ThemingTile extends QSTileImpl<State> {

    private static final int DEFAULT_LIGHT_BASE_THEME = 0;
    private static final int DEFAULT_DARK_BASE_THEME = 1;

    private Handler mHandler = new Handler();

    public ThemingTile(QSHost host) {
        super(host);
    }

    @Override
    public State newTileState() {
        return new QSTile.State();
    }

    @Override
    public void handleSetListening(boolean listening) {
    }

    @Override
    protected void handleClick() {
        toggleTheme();
    }

    @Override
    protected void handleSecondaryClick() {
        toggleTheme();
    }

    private void toggleTheme() {
        int current = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.THEMING_BASE, 0, UserHandle.USER_CURRENT);
        int alternative = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.THEMING_BASE_ALT1, -1, UserHandle.USER_CURRENT);
        if (alternative == -1 || ThemeOverlayHelper.isDarkBaseTheme(alternative)
                                        == ThemeOverlayHelper.isDarkBaseTheme(current)) {
            // Not properly initialized, use default light/dark theme for toggle
            if (ThemeOverlayHelper.isDarkBaseTheme(current)) {
                alternative = DEFAULT_LIGHT_BASE_THEME;
            } else {
                alternative = DEFAULT_DARK_BASE_THEME;
            }
        } // else: user has selected favorite light/dark themes already
        // Exchange current and alternative = toggle
        Settings.System.putIntForUser(mContext.getContentResolver(),
                Settings.System.THEMING_BASE,
                alternative, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(mContext.getContentResolver(),
                Settings.System.THEMING_BASE_ALT1,
                current, UserHandle.USER_CURRENT);
        if (ThemeOverlayHelper.doesThemeChangeRequireSystemUIRestart(mContext,
                    Settings.System.THEMING_BASE, current, alternative)) {
            postRestartSystemUi();
        }
    }

    @Override
    public Intent getLongClickIntent() {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setClassName("com.aicp.extras", "com.aicp.extras.SettingsActivity");
        intent.putExtra(PreferenceActivity.EXTRA_SHOW_FRAGMENT,
                "com.aicp.extras.fragments.Theming");
        return intent;
    }

    @Override
    public CharSequence getTileLabel() {
        return mContext.getString(R.string.theming_tile_title);
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.CUSTOM_QUICK_TILES;
    }

    @Override
    protected void handleUpdateState(State state, Object arg) {
        state.state = Tile.STATE_ACTIVE;
        int current = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.THEMING_BASE, 0, UserHandle.USER_CURRENT);
        if (ThemeOverlayHelper.isDarkBaseTheme(current)) {
            state.icon = ResourceIcon.get(R.drawable.ic_qs_theming_base_dark);
            state.label = mContext.getString(R.string.theming_tile_dark_title);
        } else {
            state.icon = ResourceIcon.get(R.drawable.ic_qs_theming_base_light);
            state.label = mContext.getString(R.string.theming_tile_light_title);
        }
    }

    private void postRestartSystemUi() {
        mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    // We are SystemUI
                    Process.killProcess(Process.myPid());
                }
        }, 200);
    }
}
