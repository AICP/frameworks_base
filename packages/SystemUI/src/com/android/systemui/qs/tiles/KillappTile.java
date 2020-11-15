/*
 * Copyright (C) 2020 The Android Open Source Project
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
import android.service.quicksettings.Tile;

import com.android.internal.util.aicp.AicpUtils;
import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.systemui.R;
import com.android.systemui.plugins.ActivityStarter;
import com.android.systemui.plugins.qs.QSTile;
import com.android.systemui.qs.QSHost;
import com.android.systemui.qs.tileimpl.QSTileImpl;
import com.android.systemui.statusbar.phone.KeyguardDismissUtil;

import javax.inject.Inject;

/**
 * Quick settings tile for screen recording
 */
public class KillappTile extends QSTileImpl<QSTile.BooleanState> {
    private static final String TAG = "KillappTile";
    private KeyguardDismissUtil mKeyguardDismissUtil;

    @Inject
    public KillappTile(QSHost host,
            KeyguardDismissUtil keyguardDismissUtil) {
        super(host);
        mKeyguardDismissUtil = keyguardDismissUtil;
    }

    @Override
    public BooleanState newTileState() {
        BooleanState state = new BooleanState();
        state.label = mContext.getString(R.string.quick_settings_killapp_label);
        state.handlesLongClick = false;
        return state;
    }

    @Override
    protected void handleClick() {
        killApp();
        refreshState();
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        state.state = Tile.STATE_INACTIVE;
        state.label = mContext.getString(R.string.quick_settings_killapp_label);
        state.icon = ResourceIcon.get(R.drawable.ic_killapp_tile);
        state.contentDescription = state.label;
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.AICP_METRICS;
    }

    @Override
    public Intent getLongClickIntent() {
        return null;
    }

    @Override
    public CharSequence getTileLabel() {
        return mContext.getString(R.string.quick_settings_killapp_label);
    }

    private void killApp() {
        // Close QS
        getHost().collapsePanels();
        ActivityStarter.OnDismissAction dismissAction = () -> {
            AicpUtils.killForegroundApp();
            return false;
        };
        mKeyguardDismissUtil.executeWhenUnlocked(dismissAction, false);
    }
}
