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
import android.content.Intent;
import android.database.ContentObserver;
import android.os.Handler;
import android.provider.Settings;
import android.view.View;

import com.android.systemui.R;

public class PieToggle extends StatefulToggle {
    PieObserver mObserver = null;

    @Override
    public void init(Context c, int style) {
        super.init(c, style);
        mObserver = new PieObserver(mHandler);
        mObserver.observe();
    }

    @Override
    protected void cleanup() {
        if (mObserver != null) {
            mContext.getContentResolver().unregisterContentObserver(mObserver);
            mObserver = null;
        }
        super.cleanup();
    }

    @Override
    protected void doEnable() {
        Settings.System.putInt(mContext.getContentResolver(),
                Settings.System.PIE_CONTROLS, 1);
    }

    @Override
    protected void doDisable() {
        Settings.System.putInt(mContext.getContentResolver(),
                Settings.System.PIE_CONTROLS, 0);
    }

    @Override
    public boolean onLongClick(View v) {
        Intent intent = new Intent("android.intent.action.MAIN");
        intent.setClassName("com.android.settings", "com.android.settings.Settings$PieActivity");
        intent.addCategory("android.intent.category.LAUNCHER");
        startActivity(intent);
        return super.onLongClick(v);
    }

    @Override
    protected void updateView() {
        boolean enabled = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.PIE_CONTROLS, 0) != 0;
        setIcon(enabled
                ? R.drawable.ic_qs_pie_on
                : R.drawable.ic_qs_pie_off);
        setLabel(enabled
                ? R.string.quick_settings_pie_on
                : R.string.quick_settings_pie_off);
        updateCurrentState(enabled ? State.ENABLED : State.DISABLED);
        super.updateView();
    }

    protected class PieObserver extends ContentObserver {
        PieObserver(Handler handler) {
            super(handler);
            observe();
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.PIE_CONTROLS), false, this);
            onChange(false);
        }

        @Override
        public void onChange(boolean selfChange) {
            scheduleViewUpdate();
        }
    }

    @Override
    public int getDefaultIconResId() {
        return R.drawable.ic_qs_pie_on;
    }
}
