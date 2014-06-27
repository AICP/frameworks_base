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
package com.android.systemui.statusbar.toggles;

import android.content.ContentResolver;
import android.content.Context;
import android.os.UserHandle;
import android.provider.Settings;
import android.view.View;

import com.android.systemui.R;

public class HeadsUpToggle extends StatefulToggle {

    private boolean mHeadsUpEnabled;

    @Override
    public void init(Context c, int style) {
        super.init(c, style);
        scheduleViewUpdate();
    }

    @Override
    public void updateView() {
        boolean enabled;
        mHeadsUpEnabled = Settings.System.getIntForUser(
                mContext.getContentResolver(),
                Settings.System.HEADS_UP_NOTIFICATION, 0, UserHandle.USER_CURRENT) == 1;

        enabled = mHeadsUpEnabled;
        setEnabledState(enabled);
        setIcon(enabled ? R.drawable.ic_qs_heads_up_on : R.drawable.ic_qs_heads_up_off);
        setLabel(enabled ? R.string.quick_settings_heads_up_on
                : R.string.quick_settings_heads_up_off);
        super.updateView();
    }

    @Override
    protected void doEnable() {
        Settings.System.putInt(mContext.getContentResolver(),
                Settings.System.HEADS_UP_NOTIFICATION, 1);
    }

    @Override
    protected void doDisable() {
        Settings.System.putInt(mContext.getContentResolver(),
                Settings.System.HEADS_UP_NOTIFICATION, 0);
    }

    @Override
    public int getDefaultIconResId() {
        return R.drawable.ic_qs_heads_up_on;
    }
}

