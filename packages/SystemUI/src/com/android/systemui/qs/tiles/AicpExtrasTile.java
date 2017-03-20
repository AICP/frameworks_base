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

import com.android.internal.logging.MetricsProto.MetricsEvent;
import com.android.systemui.R;
import com.android.systemui.SysUIToast;
import com.android.systemui.qs.QSTile;
import com.android.systemui.qs.QSTileView;

public class AicpExtrasTile extends QSTile<QSTile.BooleanState> {
    private boolean mListening;

    private static final String TAG = "AicpExtrasTile";

    private static final String AE_PKG_NAME = "com.lordclockan";
    private static final String OTA_PKG_NAME = "com.aicp.aicpota";

    private static final Intent AICP_EXTRAS = new Intent()
        .setComponent(new ComponentName(AE_PKG_NAME,
        "com.lordclockan.aicpextras.MainActivity"));
    private static final Intent OTA_INTENT = new Intent()
        .setComponent(new ComponentName(OTA_PKG_NAME,
        "com.aicp.aicpota.MainActivity"));

    public AicpExtrasTile(Host host) {
        super(host);
    }

    @Override
    public BooleanState newTileState() {
        return new BooleanState();
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.QUICK_SETTINGS;
    }

    @Override
    protected void handleClick() {
        mHost.collapsePanels();
        startAicpExtras();
        refreshState();
    }

    @Override
    public Intent getLongClickIntent() {
        return null;
    }

    @Override
    public void handleLongClick() {
        // Collapse the panels, so the user can see the toast.
        mHost.collapsePanels();
        if (!isOTABundled()) {
            showNotSupportedToast();
            return;
        }
        startAicpOTA();
        refreshState();
    }

    @Override
    public CharSequence getTileLabel() {
        return mContext.getString(R.string.quick_aicp_extras_label);
    }

    protected void startAicpExtras() {
        mHost.startActivityDismissingKeyguard(AICP_EXTRAS);
    }

    protected void startAicpOTA() {
        mHost.startActivityDismissingKeyguard(OTA_INTENT);
    }

    private void showNotSupportedToast(){
        SysUIToast.makeText(mContext, mContext.getString(
              R.string.quick_aicp_extras_toast),
              Toast.LENGTH_LONG).show();
    }

    private boolean isOTABundled(){
      boolean isBundled = false;
      try {
        isBundled = (mContext.getPackageManager().getPackageInfo(OTA_PKG_NAME, 0).versionCode > 0);
      } catch (PackageManager.NameNotFoundException e) {
      }
      return isBundled;
    }

    private boolean isAEAvailable(){
      boolean isBundled = false;
      try {
        isBundled = (mContext.getPackageManager().getPackageInfo(AE_PKG_NAME, 0).versionCode > 0);
      } catch (PackageManager.NameNotFoundException e) {
      }
      return isBundled;
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
    public void setListening(boolean listening) {
        if (mListening == listening) return;
        mListening = listening;
    }
}
