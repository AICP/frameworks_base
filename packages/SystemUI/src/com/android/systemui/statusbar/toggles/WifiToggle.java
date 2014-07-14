
package com.android.systemui.statusbar.toggles;

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.systemui.R;
import com.android.systemui.statusbar.policy.NetworkController.NetworkSignalChangedCallback;
import com.android.systemui.statusbar.phone.QuickSettingsTileView;

public class WifiToggle extends StatefulToggle implements NetworkSignalChangedCallback {

    private WifiManager wifiManager;

    @Override
    public void init(Context c, int style) {
        super.init(c, style);
        setInfo(mContext.getString(R.string.quick_settings_wifi_off_label),
                R.drawable.ic_qs_wifi_no_network);
        updateCurrentState(State.DISABLED);

        wifiManager = (WifiManager) c.getSystemService(Context.WIFI_SERVICE);
    }

    public static class WifiState {
        int wifiSignalIconId;
        String label;
    }

    private void changeWifiState(final boolean desiredState) {
        if (wifiManager == null) {
            return;
        }

        AsyncTask.execute(new Runnable() {
            public void run() {
                int wifiApState = wifiManager.getWifiApState();
                if (desiredState
                        && ((wifiApState == WifiManager.WIFI_AP_STATE_ENABLING)
                        || (wifiApState == WifiManager.WIFI_AP_STATE_ENABLED))) {
                    wifiManager.setWifiApEnabled(null, false);
                }

                wifiManager.setWifiEnabled(desiredState);
                return;
            }
        });
    }

    @Override
    public boolean onLongClick(View v) {
        startActivity(new Intent(android.provider.Settings.ACTION_WIFI_SETTINGS));
        return super.onLongClick(v);
    }

    @Override
    public void onAirplaneModeChanged(boolean enabled) {
    }

    @Override
    protected void doEnable() {
        changeWifiState(true);
    }

    @Override
    protected void doDisable() {
        changeWifiState(false);
    }

    @Override
    public QuickSettingsTileView createTileView() {
        QuickSettingsTileView quick = (QuickSettingsTileView)
                View.inflate(mContext, R.layout.toggle_tile_wifi, null);
        quick.setOnClickListener(this);
        quick.setOnLongClickListener(this);
        mLabel = (TextView) quick.findViewById(R.id.label);
        mIcon = (ImageView) quick.findViewById(R.id.icon);
        mActivityIn = (ImageView) quick.findViewById(R.id.activity_in);
        mActivityOut = (ImageView) quick.findViewById(R.id.activity_out);
        return quick;
    }

    @Override
    public View createTraditionalView() {
        View root = View.inflate(mContext, R.layout.toggle_traditional_wifi, null);
        root.setOnClickListener(this);
        root.setOnLongClickListener(this);
        mIcon = (ImageView) root.findViewById(R.id.icon);
        mActivityIn = (ImageView) root.findViewById(R.id.activity_in);
        mActivityOut = (ImageView) root.findViewById(R.id.activity_out);
        mLabel = null;
        return root;
    }

    @Override
    public void onWifiSignalChanged(boolean enabled, int wifiSignalIconId, boolean activityIn,
            boolean activityOut, String wifiSignalContentDescription, String enabledDesc) {
        Resources r = mContext.getResources();
        boolean wifiConnected = enabled && (wifiSignalIconId > 0) && (enabledDesc != null);
        boolean wifiNotConnected = (wifiSignalIconId > 0) && (enabledDesc == null);

        String label;
        int iconId;
        State newState = getState();
        if (wifiConnected) {
            iconId = wifiSignalIconId;
            label = enabledDesc;
            newState = State.ENABLED;
        } else if (wifiNotConnected) {
            iconId = R.drawable.ic_qs_wifi_0;
            label = r.getString(R.string.quick_settings_wifi_label);
            newState = State.ENABLED;
        } else {
            iconId = R.drawable.ic_qs_wifi_no_network;
            label = r.getString(R.string.quick_settings_wifi_off_label);
            newState = State.DISABLED;
        }
        updateCurrentState(newState);
        networkActivity(enabled && activityIn, enabled && activityOut);
        setInfo(removeDoubleQuotes(label), iconId);
    }

    @Override
    public void onMobileDataSignalChanged(boolean enabled, int mobileSignalIconId,
            String mobileSignalContentDescriptionId, int dataTypeIconId, boolean activityIn,
            boolean activityOut, String dataTypeContentDescriptionId, String description) {
    }

    @Override
    public int getDefaultIconResId() {
        return R.drawable.ic_qs_wifi_full_3;
    }
}
