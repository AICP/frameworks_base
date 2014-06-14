package com.android.systemui.statusbar.toggles;

import android.app.ActivityManager;
import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.os.Handler;
import android.provider.Settings;
import android.view.View;

import com.android.systemui.R;
import com.android.systemui.statusbar.policy.LocationController.LocationSettingsChangeCallback;

public class LocationToggle extends StatefulToggle implements LocationSettingsChangeCallback {

    SettingsObserver mObserver;
    private boolean mLocationEnabled;

    @Override
    public void init(Context c, int style) {
        super.init(c, style);

        mObserver = new SettingsObserver(new Handler());
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

    class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(Settings.Global
                    .getUriFor(Settings.Secure.LOCATION_MODE), false,
                    this);
            scheduleViewUpdate();
        }

        @Override
        public void onChange(boolean selfChange) {
            scheduleViewUpdate();
        }
    }

    @Override
    public boolean onLongClick(View v) {
        startActivity(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS);
        return super.onLongClick(v);
    }

    @Override
    protected void doEnable() {
        cycleSwitchLocationMode();
    }

    @Override
    protected void doDisable() {
        cycleSwitchLocationMode();
    }

    @Override
    protected void updateView() {
        int locationMode = getCurrentLocationMode();
        switch (locationMode) {
            case Settings.Secure.LOCATION_MODE_HIGH_ACCURACY:
                updateCurrentState(State.ENABLED);
                setLabel(R.string.quick_settings_location_high_accuracy_label);
                setIcon(R.drawable.ic_qs_location_on);
                break;
            case Settings.Secure.LOCATION_MODE_BATTERY_SAVING:
                updateCurrentState(State.ENABLED);
                setLabel(R.string.quick_settings_location_battery_saving_label);
                setIcon(R.drawable.ic_qs_location_on);
                break;
            case Settings.Secure.LOCATION_MODE_SENSORS_ONLY:
                updateCurrentState(State.ENABLED);
                setLabel(R.string.quick_settings_location_device_only_label);
                setIcon(R.drawable.ic_qs_location_on);
                break;
            case Settings.Secure.LOCATION_MODE_OFF:
                updateCurrentState(State.DISABLED);
                setLabel(R.string.quick_settings_location_off_label);
                setIcon(R.drawable.ic_qs_location_off);
                break;
        }
        super.updateView();
    }

    private int getCurrentLocationMode() {
        int mode = Settings.Secure.getIntForUser(mContext.getContentResolver(), Settings.Secure.LOCATION_MODE,
                Settings.Secure.LOCATION_MODE_OFF, ActivityManager.getCurrentUser());
        return mode;
    }

    private void cycleSwitchLocationMode() {
        int modes = Settings.AOKP.getInt(mContext.getContentResolver(),
                Settings.AOKP.LOCATION_MODES_TOGGLE, 1);
        int locationMode = getCurrentLocationMode();

        if (locationMode == Settings.Secure.LOCATION_MODE_OFF) {
            if (modes == 1 || modes == 9) {
                setLocationMode(Settings.Secure.LOCATION_MODE_SENSORS_ONLY);
            } else if (modes == 6 || modes == 8 || modes == 7) {
                setLocationMode(Settings.Secure.LOCATION_MODE_BATTERY_SAVING);
            } else {
                setLocationMode(Settings.Secure.LOCATION_MODE_HIGH_ACCURACY);
            }
            updateCurrentState(State.ENABLING);
            collapseStatusBar();
        } else if (locationMode == Settings.Secure.LOCATION_MODE_SENSORS_ONLY) {
            if (modes == 1 || modes == 7) {
                setLocationMode(Settings.Secure.LOCATION_MODE_BATTERY_SAVING);
            } else if (modes == 6 || modes == 7 || modes == 9) {
                setLocationMode(Settings.Secure.LOCATION_MODE_OFF);
            } else {
                setLocationMode(Settings.Secure.LOCATION_MODE_HIGH_ACCURACY);
            }
        } else if (locationMode == Settings.Secure.LOCATION_MODE_BATTERY_SAVING) {
            if (modes == 2 || modes == 6 || modes == 7) {
                setLocationMode(Settings.Secure.LOCATION_MODE_SENSORS_ONLY);
            } else if (modes == 8) {
                setLocationMode(Settings.Secure.LOCATION_MODE_OFF);
            } else {
                setLocationMode(Settings.Secure.LOCATION_MODE_HIGH_ACCURACY);
            }
        } else {
            if (modes == 1 || modes == 5) {
                setLocationMode(Settings.Secure.LOCATION_MODE_OFF);
            } else if (modes == 4 || modes == 9) {
                setLocationMode(Settings.Secure.LOCATION_MODE_SENSORS_ONLY);
            } else {
                setLocationMode(Settings.Secure.LOCATION_MODE_BATTERY_SAVING);
            }
        }
    }

    public boolean setLocationMode(int mode) {
        int currentUserId = ActivityManager.getCurrentUser();
        return Settings.Secure
                .putIntForUser(mContext.getContentResolver(),
                    Settings.Secure.LOCATION_MODE, mode, currentUserId);
    }

    @Override
    public void onLocationSettingsChanged(boolean locationEnabled) {
        setEnabledState(locationEnabled);
        scheduleViewUpdate();
    }

    @Override
    public int getDefaultIconResId() {
        return R.drawable.ic_qs_location_on;
    }
}
