/*
 * Copyright (C) 2015 The CyanogenMod Project
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
import android.database.ContentObserver;
import android.net.Uri;
import android.os.UserHandle;
import android.provider.Settings;
import com.android.systemui.R;
import com.android.systemui.qs.QSTile;

/** Quick settings tile: Heads Up **/
public class HeadsUpTile extends QSTile<QSTile.BooleanState> {

    private static final Intent HEADS_UP_SETTINGS = new Intent("android.settings.HEADS_UP_SETTINGS");

    public HeadsUpTile(Host host) {
        super(host);
    }

    @Override
    protected BooleanState newTileState() {
        return new BooleanState();
    }

    @Override
    protected void handleClick() {
        toggleState();
        refreshState();
        qsCollapsePanel();
    }

    @Override
    protected void handleSecondaryClick() {
        mHost.startSettingsActivity(HEADS_UP_SETTINGS);
    }

    @Override
    protected void handleLongClick() {
        mHost.startSettingsActivity(HEADS_UP_SETTINGS);
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        state.visible = true;
        state.value = isHeadsUpEnabled();
        if (state.value) {
            state.icon = ResourceIcon.get(R.drawable.ic_headsup_toggle_on);
            state.label = mContext.getString(R.string.accessibility_quick_settings_heads_up_on);
        } else {
            state.icon = ResourceIcon.get(R.drawable.ic_headsup_toggle_off);
	    state.label = mContext.getString(R.string.accessibility_quick_settings_heads_up_off);
        }
    }

    protected void toggleState() {
        Settings.System.putInt(mContext.getContentResolver(),
            Settings.System.HEADS_UP_NOTIFICATION, isHeadsUpEnabled() ? 0 : 1);
    }

    private boolean isHeadsUpEnabled() {
        return Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.HEADS_UP_NOTIFICATION, 1) == 1;
    }

    private ContentObserver mObserver = new ContentObserver(mHandler) {
        @Override
        public void onChange(boolean selfChange, Uri uri) {
            refreshState();
        }
    };

    @Override
    public void destroy() {
        mContext.getContentResolver().unregisterContentObserver(mObserver);
    }

    @Override
    public void setListening(boolean listening) {
        if (listening) {
            mContext.getContentResolver().registerContentObserver(
                    Settings.System.getUriFor(Settings.System.HEADS_UP_NOTIFICATION),
                    false, mObserver);
        } else {
            mContext.getContentResolver().unregisterContentObserver(mObserver);
        }
    }

}
