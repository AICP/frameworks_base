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

public class MagiskManagerTile extends QSTile<QSTile.BooleanState> {
    private boolean mListening;

    private static final String TAG = "MagiskManagerTile";
    private static final String PACKAGENAME = "com.topjohnwu.magisk";
    private static final Intent MAGISKMANAGER = new Intent().setComponent(new ComponentName(
            PACKAGENAME, "com.topjohnwu.magisk.SplashActivity"));
    private static final Intent MAGISKSETTINGS = new Intent().setComponent(new ComponentName(
            PACKAGENAME, "com.topjohnwu.magisk.SettingsActivity"));

    public MagiskManagerTile(Host host) {
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
      // Collapse the panels, so the user can see the toast.
        mHost.collapsePanels();

        if (!isMagiskSupported()) {
            showNotSupportedToast();
            return;
        }
        startMagiskManager();
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

        if (!isMagiskSupported()) {
            showNotSupportedToast();
            return;
        }
        startMagiskSettings();
        refreshState();
    }

    @Override
    public CharSequence getTileLabel() {
        return mContext.getString(R.string.quick_magiskmanager_label);
    }

    private boolean isMagiskSupported(){
      boolean isSupported = false;
      try {
        isSupported = (mContext.getPackageManager().getPackageInfo(PACKAGENAME, 0).versionCode > 0);
      } catch (PackageManager.NameNotFoundException e) {
      }
      return isSupported;
    }

    @Override
    public boolean isAvailable(){
      return isMagiskSupported();
    }

    private void showNotSupportedToast(){
      SysUIToast.makeText(mContext, mContext.getString(
            R.string.quick_magiskmanager_na),
            Toast.LENGTH_LONG).show();
    }

    protected void startMagiskManager() {
        mHost.startActivityDismissingKeyguard(MAGISKMANAGER);
    }

    protected void startMagiskSettings() {
        mHost.startActivityDismissingKeyguard(MAGISKSETTINGS);
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        state.icon = ResourceIcon.get(R.drawable.ic_qs_magiskmanager);
        state.label = mContext.getString(R.string.quick_magiskmanager_label);
    }

    @Override
    public void setListening(boolean listening) {
        if (mListening == listening) return;
        mListening = listening;
    }
}
