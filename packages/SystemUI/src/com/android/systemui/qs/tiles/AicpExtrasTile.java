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

import android.content.Context;
import android.content.Intent;
import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.widget.Toast;

import com.android.internal.logging.MetricsLogger;
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

public class AicpExtrasTile extends QSTileImpl<BooleanState> {
    private boolean mListening;
    private final ActivityStarter mActivityStarter;

    private static final String TAG = "AicpExtrasTile";

    private static final String AE_PKG_NAME = "com.aicp.extras";
    private static final String OTA_PKG_NAME = "com.aicp.updater3";

    private static final Intent AE_INTENT = new Intent()
        .setComponent(new ComponentName(AE_PKG_NAME,
        "com.aicp.extras.SettingsActivity"));
    private static final Intent OTA_INTENT = new Intent()
        .setComponent(new ComponentName(OTA_PKG_NAME,
        "org.lineageos.updater.UpdatesActivity"));

    @Inject
    public AicpExtrasTile(QSHost host) {
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
        startAicpExtras();
        refreshState();
    }

    @Override
    public Intent getLongClickIntent() {
        if (isOTABundled()) {
            return OTA_INTENT;
        }
        showNotSupportedToast();
        return null;
    }

    @Override
    protected void handleSecondaryClick() {
        if (isOTABundled()) {
            startAicpOTA();
        }
    }

    @Override
    public CharSequence getTileLabel() {
        return mContext.getString(R.string.quick_aicp_extras_label);
    }

    protected void startAicpExtras() {
        mActivityStarter.postStartActivityDismissingKeyguard(AE_INTENT, 0);
    }

    protected void startAicpOTA() {
        mActivityStarter.postStartActivityDismissingKeyguard(OTA_INTENT, 0);
    }

    private void showNotSupportedToast(){
        SysUIToast.makeText(mContext, mContext.getString(
                R.string.quick_aicp_extras_toast),
                Toast.LENGTH_LONG).show();
    }

    private boolean isOTABundled(){
        return PackageUtils.isPackageAvailable(mContext, OTA_PKG_NAME);
    }

    private boolean isAEAvailable(){
        boolean isInstalled = false;
        boolean isNotHidden = false;
        isInstalled = PackageUtils.isPackageInstalled(mContext, AE_PKG_NAME);
        isNotHidden = PackageUtils.isPackageAvailable(mContext, AE_PKG_NAME);
        return isInstalled || isNotHidden;
    }

    @Override
    public boolean isAvailable(){
      return isAEAvailable();
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        state.icon = ResourceIcon.get(R.drawable.ic_qs_aicp_extras);
        state.label = mContext.getString(R.string.quick_aicp_extras_label);
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
