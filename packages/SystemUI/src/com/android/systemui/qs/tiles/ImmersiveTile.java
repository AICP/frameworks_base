/*
 * Copyright (C) 2018 ABC ROM
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
import android.os.UserHandle;
import android.provider.Settings;
import android.service.quicksettings.Tile;

import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.systemui.Dependency;
import com.android.systemui.R;
import com.android.systemui.R.drawable;
import com.android.systemui.qs.QSHost;
import com.android.systemui.plugins.qs.QSTile.BooleanState;
import com.android.systemui.qs.tileimpl.QSTileImpl;

public class ImmersiveTile extends QSTileImpl<BooleanState> {

    private static final String IMMERSIVE_OFF = "immersive.preconfirms=apps,-com.google.android.googlequicksearchbox";
    private static final String IMMERSIVE_FULL = "immersive.full=apps, -com.google.android.googlequicksearchbox";
    private static final String IMMERSIVE_STATUSBAR = "immersive.status=apps, -com.google.android.googlequicksearchbox";
    private static final String IMMERSIVE_NAVBAR = "immersive.navigation=apps, -com.google.android.googlequicksearchbox";

    private String mMode;

    public ImmersiveTile(QSHost host) {
        super(host);
        mMode = Settings.Global.getStringForUser(mContext.getContentResolver(),
                Settings.Global.POLICY_CONTROL, UserHandle.USER_CURRENT);
        if (mMode == null) {
            mMode = IMMERSIVE_OFF;
        }
    }

    @Override
    public BooleanState newTileState() {
        return new BooleanState();
    }

    @Override
    public void handleSetListening(boolean listening) {
    }

    @Override
    public Intent getLongClickIntent() {
        return null;
    }

    @Override
    public void handleLongClick() {
        if (mMode.equals(IMMERSIVE_OFF)) return;

        mHost.collapsePanels();
        setImmersiveMode(mMode);
        refreshState();
    }

    @Override
    protected void handleClick() {
        switchMode();
    }

    private void switchMode() {
        if (mMode.equals(IMMERSIVE_FULL)) {
            mMode = IMMERSIVE_NAVBAR;
        } else if (mMode.equals(IMMERSIVE_NAVBAR)) {
            mMode = IMMERSIVE_STATUSBAR;
        } else if (mMode.equals(IMMERSIVE_STATUSBAR)) {
            mMode = IMMERSIVE_OFF;
        } else {
            mMode = IMMERSIVE_FULL;
        }
        refreshState();
    }

    private void setImmersiveMode(String mode) {
        boolean alreadyEnabled = mMode.equals(Settings.Global.getStringForUser(mContext.getContentResolver(),
                Settings.Global.POLICY_CONTROL, UserHandle.USER_CURRENT));
        // if the wanted immersive mode is enabled, long pressing the tile will disable immersive
        Settings.Global.putStringForUser(mContext.getContentResolver(),
                Settings.Global.POLICY_CONTROL, alreadyEnabled ? IMMERSIVE_OFF : mode, UserHandle.USER_CURRENT);
    }

    @Override
    public CharSequence getTileLabel() {
        return mContext.getString(R.string.quick_settings_immersive_tile);
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        if (mMode.equals(IMMERSIVE_FULL)) {
            state.contentDescription = mContext.getString(R.string.quick_settings_immersive_tile_full);
            state.label = mContext.getString(R.string.quick_settings_immersive_tile_full);
            state.icon = ResourceIcon.get(R.drawable.ic_qs_immersive_full);
        } else if (mMode.equals(IMMERSIVE_STATUSBAR)) {
            state.contentDescription = mContext.getString(R.string.quick_settings_immersive_tile_statusbar);
            state.label = mContext.getString(R.string.quick_settings_immersive_tile_statusbar);
            state.icon = ResourceIcon.get(R.drawable.ic_qs_immersive_statusbar);
        } else if (mMode.equals(IMMERSIVE_NAVBAR)) {
            state.contentDescription = mContext.getString(R.string.quick_settings_immersive_tile_navbar);
            state.label = mContext.getString(R.string.quick_settings_immersive_tile_navbar);
            state.icon = ResourceIcon.get(R.drawable.ic_qs_immersive_navbar);
        } else {
            state.contentDescription = mContext.getString(R.string.quick_settings_immersive_tile_off);
            state.label = mContext.getString(R.string.quick_settings_immersive_tile_off);
            state.icon = ResourceIcon.get(R.drawable.ic_qs_immersive_off);
        }

        state.value = mMode.equals(Settings.Global.getStringForUser(mContext.getContentResolver(),
                Settings.Global.POLICY_CONTROL, UserHandle.USER_CURRENT));
        state.state = state.value ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE;
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.AICP_METRICS;
    }
}
