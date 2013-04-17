package com.android.systemui.statusbar.toggles;

import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.os.Handler;
import android.provider.Settings;
import android.view.View;

import com.android.systemui.R;

public class StayAwakeToggle extends StatefulToggle {

    private static final String TAG = "AOKPInsomnia";
    private static final int FALLBACK_SCREEN_TIMEOUT_VALUE = 30000;
    private static final int neverSleep = Integer.MAX_VALUE; // MAX_VALUE equates to approx 24 days

    private boolean enabled;
    private int currentTimeout, storedUserTimeout;

    SettingsObserver mObserver = null;

    @Override
    protected void init(Context c, int style) {
        super.init(c, style);

        mObserver = new SettingsObserver(mHandler);
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
    public boolean onLongClick(View v) {
       startActivity(android.provider.Settings.ACTION_DISPLAY_SETTINGS);
       return super.onLongClick(v);
    }

    @Override
    protected void doEnable() {
        toggleInsomnia(true);
    }

    @Override
    protected void doDisable() {
        toggleInsomnia(false);
    }

    @Override
    public void updateView() {
       currentTimeout = Settings.System.getInt(mContext.getContentResolver(),
                        Settings.System.SCREEN_OFF_TIMEOUT, FALLBACK_SCREEN_TIMEOUT_VALUE);
       if (currentTimeout == neverSleep) {
           enabled = true;
       } else {
           enabled = false;
       }
       setEnabledState(enabled);
       setLabel(enabled ? R.string.quick_settings_stayawake_on
               : R.string.quick_settings_stayawake_off);
       setIcon(enabled ? R.drawable.ic_qs_stayawake_on : R.drawable.ic_qs_stayawake_off);
       super.updateView();
    }

    protected void toggleInsomnia(boolean state) {
        if (state) {
            storedUserTimeout = currentTimeout;
            Settings.System.putInt(mContext.getContentResolver(),
                    Settings.System.SCREEN_OFF_TIMEOUT, neverSleep);
        } else {
            if (currentTimeout != storedUserTimeout) {
                currentTimeout = storedUserTimeout;
            } else {
                currentTimeout = FALLBACK_SCREEN_TIMEOUT_VALUE;
            }
            Settings.System.putInt(mContext.getContentResolver(),
                    Settings.System.SCREEN_OFF_TIMEOUT, currentTimeout);
        }
    }

    class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(Settings.Global
                    .getUriFor(Settings.System.SCREEN_OFF_TIMEOUT), false,
                    this);
        }

        @Override
        public void onChange(boolean selfChange) {
            scheduleViewUpdate();
        }
    }
}
