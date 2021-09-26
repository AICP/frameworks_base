/*
 * Copyright (C) 2015 The Dirty Unicorns Project
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

import android.content.ComponentName;
import android.content.Intent;
import android.widget.Toast;

import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.internal.util.aicp.PackageUtils;
import com.android.systemui.Dependency;
import com.android.systemui.R;
import com.android.systemui.SysUIToast;
import com.android.systemui.plugins.ActivityStarter;
import com.android.systemui.plugins.qs.QSTile.BooleanState;
import com.android.systemui.qs.QSHost;
import com.android.systemui.qs.tileimpl.QSTileImpl;

import javax.inject.Inject;

public class PartsBinTile extends QSTileImpl<BooleanState> {
    private boolean mListening;
    private final ActivityStarter mActivityStarter;

    private static final String TAG = "PartsBinTile";

    private static final String PB_PKG_NAME = "com.aicp.device";

    private static final Intent PB_INTENT = new Intent()
            .setComponent(new ComponentName(PB_PKG_NAME,
                "com.aicp.device.DeviceSettings"));

    @Inject
    public PartsBinTile(QSHost host) {
        super(host);
        mActivityStarter = Dependency.get(ActivityStarter.class);
    }

    @Override
    public BooleanState newTileState() {
        return new BooleanState();
    }

    @Override
    protected void handleClick() {
        mHost.collapsePanels();
        startPartsBin();
        refreshState();
    }

    @Override
    public Intent getLongClickIntent() {
        showNotSupportedToast();
        return null;
    }

    @Override
    protected void handleSecondaryClick() {
    }

    @Override
    public CharSequence getTileLabel() {
        return mContext.getString(R.string.quick_aicp_partsbin_label);
    }

    protected void startPartsBin() {
        mActivityStarter.postStartActivityDismissingKeyguard(PB_INTENT, 0);
    }

    private void showNotSupportedToast() {
        // Collapse the panels, so the user can see the toast.
        mHost.collapsePanels();
        SysUIToast.makeText(mContext, mContext.getString(
                R.string.quick_aicp_partsbin_toast),
                Toast.LENGTH_LONG).show();
    }

    private boolean isPBAvailable() {
        boolean isInstalled = false;
        boolean isNotHidden = false;
        isInstalled = PackageUtils.isPackageInstalled(mContext, PB_PKG_NAME);
        isNotHidden = PackageUtils.isPackageAvailable(mContext, PB_PKG_NAME);
        return isInstalled || isNotHidden;
    }

    @Override
    public boolean isAvailable() {
        return isPBAvailable();
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        state.icon = ResourceIcon.get(R.drawable.ic_settings_partsbin);
        state.label = mContext.getString(R.string.quick_aicp_partsbin_label);
    }

    @Override
    public void handleSetListening(boolean listening) {
        if (mListening == listening) return;
        mListening = listening;
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.CUSTOM_QUICK_TILES;
    }
}
