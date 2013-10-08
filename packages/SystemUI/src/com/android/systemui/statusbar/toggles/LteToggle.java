
package com.android.systemui.statusbar.toggles;

import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.os.Handler;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.telephony.TelephonyManager;
import android.view.View;

import com.android.internal.telephony.PhoneConstants;
import com.android.systemui.R;

public class LteToggle extends StatefulToggle {

    SettingsObserver mObserver;
    TelephonyManager tm;

    @Override
    public void init(Context c, int style) {
        super.init(c, style);

        tm = (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);

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

    @Override
    public boolean onLongClick(View v) {
        startActivity(android.provider.Settings.ACTION_DATA_ROAMING_SETTINGS);
        return super.onLongClick(v);
    }

    class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(Settings.Global
                    .getUriFor(Settings.Global.PREFERRED_NETWORK_MODE), false,
                    this);
            scheduleViewUpdate();
        }

        @Override
        public void onChange(boolean selfChange) {
            scheduleViewUpdate();
        }
    }

    @Override
    protected void updateView() {
        boolean enabled = validLteMode(getCurrentPreferredNetworkMode(mContext));
        setLabel(enabled ? R.string.quick_settings_lte_on_label
                : R.string.quick_settings_lte_off_label);
        setIcon(enabled ? R.drawable.ic_qs_lte_on : R.drawable.ic_qs_lte_off);
        updateCurrentState(enabled ? State.ENABLED : State.DISABLED);
        super.updateView();
    }

    private static int getCurrentPreferredNetworkMode(Context context) {
        int network = -1;
        try {
            network = Settings.Global.getInt(context.getContentResolver(),
                    Settings.Global.PREFERRED_NETWORK_MODE);
        } catch (SettingNotFoundException e) {
            e.printStackTrace();
        }
        return network;
    }

    @Override
    protected void doEnable() {
        tm.toggleLTE(true);
    }

    @Override
    protected void doDisable() {
        tm.toggleLTE(false);
    }

    private boolean validLteMode(int mode) {
        if (tm.getLteOnCdmaMode()
                    == PhoneConstants.LTE_ON_CDMA_TRUE) {
            return mode == PhoneConstants.NT_MODE_LTE_CDMA_EVDO;
        } else {
            return mode == PhoneConstants.NT_MODE_GLOBAL
                || mode == PhoneConstants.NT_MODE_LTE_GSM_WCDMA
                || mode == PhoneConstants.NT_MODE_LTE_CMDA_EVDO_GSM_WCDMA
                || mode == PhoneConstants.NT_MODE_LTE_ONLY
                || mode == PhoneConstants.NT_MODE_LTE_WCDMA;
        }
    }
}

