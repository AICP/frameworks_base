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
import android.provider.Settings;
import android.view.View;

import com.android.systemui.R;

public class ImmersiveModeToggle extends StatefulToggle {

    private boolean mImmersiveModeEnabled;

    @Override
    public void init(Context c, int style) {
        super.init(c, style);
        scheduleViewUpdate();
    }

    @Override
    public void updateView() {
        boolean enabled;
        mImmersiveModeEnabled = Settings.AOKP.getBoolean(mContext.getContentResolver(),
                Settings.AOKP.IMMERSIVE_MODE, false);
        enabled = mImmersiveModeEnabled;
        setEnabledState(enabled);
        setIcon(enabled ? R.drawable.ic_navbar_hide_on : R.drawable.ic_navbar_hide_off);
        setLabel(enabled ? R.string.quick_settings_immersive_mode_on
                : R.string.quick_settings_immersive_mode_off);
        super.updateView();
    }

    @Override
    protected void doEnable() {
        Settings.AOKP.putBoolean(mContext.getContentResolver(),
                Settings.AOKP.IMMERSIVE_MODE, true);
    }

    @Override
    protected void doDisable() {
        Settings.AOKP.putBoolean(mContext.getContentResolver(),
                Settings.AOKP.IMMERSIVE_MODE, false);
    }

    @Override
    public int getDefaultIconResId() {
        return R.drawable.ic_navbar_hide_on;
    }
}
