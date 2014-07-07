
package com.android.systemui.statusbar.toggles;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.os.Handler;
import android.provider.Settings;
import android.view.View;

import com.android.systemui.R;

public class QuietHoursToggle extends StatefulToggle {
    QuietHoursObserver mObserver = null;

    @Override
    public void init(Context c, int style) {
        super.init(c, style);
        mObserver = new QuietHoursObserver(mHandler);
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
        Settings.AOKP.putInt(mContext.getContentResolver(),
                Settings.AOKP.QUIET_HOURS_ENABLED, 1);
    }

    @Override
    protected void doDisable() {
        Settings.AOKP.putInt(mContext.getContentResolver(),
                Settings.AOKP.QUIET_HOURS_ENABLED, 0);
    }

    @Override
    public boolean onLongClick(View v) {
        Intent intent = new Intent("android.intent.action.MAIN");
        intent.setClassName("com.android.settings", "com.android.settings.Settings$QuietHoursSettingsActivity");
        intent.addCategory("android.intent.category.LAUNCHER");
        startActivity(intent);
        return super.onLongClick(v);
    }

    @Override
    protected void updateView() {
        boolean enabled = Settings.AOKP.getInt(mContext.getContentResolver(),
                Settings.AOKP.QUIET_HOURS_ENABLED, 0) == 1;
        setEnabledState(enabled);
        setIcon(enabled ? R.drawable.ic_qs_quiet_hours_on : R.drawable.ic_qs_quiet_hours_off);
        setLabel(enabled ? R.string.quick_settings_quiet_hours_on_label
                : R.string.quick_settings_quiet_hours_off_label);
        super.updateView();
    }

    protected class QuietHoursObserver extends ContentObserver {
        QuietHoursObserver(Handler handler) {
            super(handler);
            observe();
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.AOKP.QUIET_HOURS_ENABLED), false, this);
            onChange(false);
        }

        @Override
        public void onChange(boolean selfChange) {
            scheduleViewUpdate();
        }
    }

    @Override
    public int getDefaultIconResId() {
        return R.drawable.ic_qs_quiet_hours_on;
    }
}
