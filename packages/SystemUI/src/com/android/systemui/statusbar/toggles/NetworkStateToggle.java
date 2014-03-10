
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
import com.android.internal.telephony.RILConstants;
import com.android.systemui.R;

public class NetworkStateToggle extends StatefulToggle {

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
    protected void doEnable() {
        cycleSwitchNetworkMode();
    }

    @Override
    protected void doDisable() {
        cycleSwitchNetworkMode();
    }

    @Override
    protected void updateView() {
        int gType = getCurrentG(getCurrentPreferredNetworkMode(mContext));
        switch (gType) {
            case 4:
                updateCurrentState(State.DISABLED);
                setLabel(R.string.quick_settings_lte_on_label);
                setIcon(R.drawable.ic_qs_lte_on);
                break;
            case 3:
                updateCurrentState(State.ENABLED);
                setLabel(R.string.quick_settings_treeg_on_label);
                setIcon(R.drawable.ic_qs_3g_on);
                break;
            case 2:
                updateCurrentState(State.ENABLED);
                setLabel(R.string.quick_settings_twog_on_label);
                setIcon(R.drawable.ic_qs_2g_on);
                break;
        }
        super.updateView();
    }

    private static int getCurrentPreferredNetworkMode(Context context) {
        int preferredNetworkMode = RILConstants.PREFERRED_NETWORK_MODE;
        if (TelephonyManager.getLteOnCdmaModeStatic() == PhoneConstants.LTE_ON_CDMA_TRUE) {
            preferredNetworkMode = PhoneConstants.NT_MODE_GLOBAL;
        }
        int network = Settings.Global.getInt(context.getContentResolver(),
                    Settings.Global.PREFERRED_NETWORK_MODE, preferredNetworkMode);
        return network;
    }

    private static int getCurrentG(int network) {
        int gType = 0;
        switch (network) {
            case PhoneConstants.NT_MODE_GLOBAL:
            case PhoneConstants.NT_MODE_LTE_CDMA_EVDO:
            case PhoneConstants.NT_MODE_LTE_GSM_WCDMA:
            case PhoneConstants.NT_MODE_LTE_CMDA_EVDO_GSM_WCDMA:
            case PhoneConstants.NT_MODE_LTE_ONLY:
            case PhoneConstants.NT_MODE_LTE_WCDMA:
                gType = 4;
                break;
            case PhoneConstants.NT_MODE_WCDMA_PREF:
            case PhoneConstants.NT_MODE_GSM_UMTS:
            case PhoneConstants.NT_MODE_CDMA:
                gType = 3;
                break;
            case PhoneConstants.NT_MODE_GSM_ONLY:
                gType = 2;
                break;
        }
        return gType;
    }

    private void cycleSwitchNetworkMode() {
        int modes = Settings.AOKP.getInt(mContext.getContentResolver(),
                Settings.AOKP.NETWORK_MODES_TOGGLE, 1);
        int gType = getCurrentG(getCurrentPreferredNetworkMode(mContext));

        if (gType == 4) {
            if (modes == 2) {
                tm.toggle2G(true);
            } else {
                tm.toggleLTE();
            }
        } else if (gType == 3) {
            if (modes == 3) {
                tm.toggleLTE();
            } else {
                tm.toggle2G(true);
            }
        } else {
            if (modes == 4) {
                tm.toggle2G(false);
            } else {
                tm.toggleLTE();
            }
        }
    }

    @Override
    public int getDefaultIconResId() {
        return R.drawable.ic_qs_lte_on;
    }
}
