/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.systemui.statusbar.phone;

import android.app.ActivityManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothAdapter.BluetoothStateChangeCallback;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.graphics.drawable.Drawable;
import android.hardware.display.WifiDisplayStatus;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.wifi.WifiManager;
import android.nfc.NfcAdapter;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.InputMethodSubtype;

import com.android.internal.view.RotationPolicy;
import com.android.internal.telephony.PhoneConstants;
import com.android.systemui.R;
import com.android.systemui.statusbar.policy.BatteryController.BatteryStateChangeCallback;
import com.android.systemui.statusbar.policy.BrightnessController.BrightnessStateChangeCallback;
import com.android.systemui.statusbar.policy.CurrentUserTracker;
import com.android.systemui.statusbar.policy.LocationController.LocationGpsStateChangeCallback;
import com.android.systemui.statusbar.policy.NetworkController.NetworkSignalChangedCallback;
import com.android.systemui.statusbar.policy.Prefs;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

class QuickSettingsModel implements BluetoothStateChangeCallback,
        NetworkSignalChangedCallback,
        BatteryStateChangeCallback,
        LocationGpsStateChangeCallback,
        BrightnessStateChangeCallback {

    // Sett InputMethoManagerService
    private static final String TAG_TRY_SUPPRESSING_IME_SWITCHER = "TrySuppressingImeSwitcher";

    private String mFastChargePath;
    
    private int dataState = -1;

    private WifiManager wifiManager;

    /** Represents the state of a given attribute. */
    static class State {
        int iconId;
        String label;
        boolean enabled = false;
    }

    static class BatteryState extends State {
        int batteryLevel;
        boolean pluggedIn;
    }

    static class RSSIState extends State {
        int signalIconId;
        String signalContentDescription;
        int dataTypeIconId;
        String dataContentDescription;
    }

    static class WifiState extends State {
        String signalContentDescription;
        boolean connected;
    }

    static class UserState extends State {
        Drawable avatar;
    }

    static class BrightnessState extends State {
        boolean autoBrightness;
    }

    public static class BluetoothState extends State {
        boolean connected = false;
        String stateContentDescription;
    }

    /** The callback to update a given tile. */
    interface RefreshCallback {
        public void refreshView(QuickSettingsTileView view, State state);
    }

    /** Broadcast receive to determine if there is an alarm set. */
    private BroadcastReceiver mAlarmIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(Intent.ACTION_ALARM_CHANGED)) {
                onAlarmChanged(intent);
                onNextAlarmChanged();
            }
        }
    };

    /** ContentObserver to determine the next alarm */
    private class NextAlarmObserver extends ContentObserver {
        public NextAlarmObserver(Handler handler) {
            super(handler);
        }

        @Override
        public void onChange(boolean selfChange) {
            onNextAlarmChanged();
        }

        public void startObserving() {
            final ContentResolver cr = mContext.getContentResolver();
            cr.registerContentObserver(
                    Settings.System.getUriFor(Settings.System.NEXT_ALARM_FORMATTED), false, this);
        }
    }

    /** ContentObserver to watch adb */
    private class BugreportObserver extends ContentObserver {
        public BugreportObserver(Handler handler) {
            super(handler);
        }

        @Override
        public void onChange(boolean selfChange) {
            onBugreportChanged();
        }

        public void startObserving() {
            final ContentResolver cr = mContext.getContentResolver();
            cr.registerContentObserver(
                    Settings.Secure.getUriFor(Settings.Secure.BUGREPORT_IN_POWER_MENU), false, this);
        }
    }

    /** ContentObserver to watch brightness **/
    private class BrightnessObserver extends ContentObserver {
        public BrightnessObserver(Handler handler) {
            super(handler);
        }

        @Override
        public void onChange(boolean selfChange) {
            onBrightnessLevelChanged();
        }

        public void startObserving() {
            final ContentResolver cr = mContext.getContentResolver();
            cr.unregisterContentObserver(this);
            cr.registerContentObserver(
                    Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS_MODE),
                    false, this, mUserTracker.getCurrentUserId());
            cr.registerContentObserver(
                    Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS),
                    false, this, mUserTracker.getCurrentUserId());
        }
    }

    private final Context mContext;
    private final Handler mHandler;
    private final CurrentUserTracker mUserTracker;
    private final NextAlarmObserver mNextAlarmObserver;
    private final BugreportObserver mBugreportObserver;
    private final BrightnessObserver mBrightnessObserver;
    private NfcAdapter mNfcAdapter;

    private QuickSettingsTileView mUserTile;
    private RefreshCallback mUserCallback;
    private UserState mUserState = new UserState();

    private QuickSettingsTileView mFavContactTile;
    private RefreshCallback mFavContactCallback;
    private UserState mFavContactState = new UserState();

    private QuickSettingsTileView mTimeTile;
    private RefreshCallback mTimeCallback;
    private State mTimeState = new State();

    private QuickSettingsTileView mAlarmTile;
    private RefreshCallback mAlarmCallback;
    private State mAlarmState = new State();

    private QuickSettingsTileView mAirplaneModeTile;
    private RefreshCallback mAirplaneModeCallback;
    private State mAirplaneModeState = new State();

    private QuickSettingsTileView mWifiTile;
    private RefreshCallback mWifiCallback;
    private WifiState mWifiState = new WifiState();

    private QuickSettingsTileView mWifiDisplayTile;
    private RefreshCallback mWifiDisplayCallback;
    private State mWifiDisplayState = new State();

    private QuickSettingsTileView mRSSITile;
    private RefreshCallback mRSSICallback;
    private RSSIState mRSSIState = new RSSIState();

    private QuickSettingsTileView mBluetoothTile;
    private RefreshCallback mBluetoothCallback;
    private BluetoothState mBluetoothState = new BluetoothState();

    private QuickSettingsTileView mBatteryTile;
    private RefreshCallback mBatteryCallback;
    private BatteryState mBatteryState = new BatteryState();

    private QuickSettingsTileView mLocationTile;
    private RefreshCallback mLocationCallback;
    private State mLocationState = new State();

    private QuickSettingsTileView mImeTile;
    private RefreshCallback mImeCallback = null;
    private State mImeState = new State();

    private QuickSettingsTileView mRotationLockTile;
    private RefreshCallback mRotationLockCallback;
    private State mRotationLockState = new State();

    private QuickSettingsTileView mVibrateTile;
    private RefreshCallback mVibrateCallback;
    private State mVibrateState = new State();

    private QuickSettingsTileView mSilentTile;
    private RefreshCallback mSilentCallback;
    private State mSilentState = new State();

    private QuickSettingsTileView mSoundStateTile;
    private RefreshCallback mSoundStateCallback;
    private State mSoundStateState = new State();

    private QuickSettingsTileView mFChargeTile;
    private RefreshCallback mFChargeCallback;
    private State mFChargeState = new State();

    private QuickSettingsTileView mNFCTile;
    private RefreshCallback mNFCCallback;
    private State mNFCState = new State();

    private QuickSettingsTileView m2gTile;
    private RefreshCallback m2gCallback;
    private State m2gState = new State();

    private QuickSettingsTileView mLTETile;
    private RefreshCallback mLTECallback;
    private State mLTEState = new State();

    private QuickSettingsTileView mSyncTile;
    private RefreshCallback mSyncCallback;
    private State mSyncState = new State();

    private QuickSettingsTileView mTorchTile;
    private RefreshCallback mTorchCallback;
    private State mTorchState = new State();

    private QuickSettingsTileView mWifiTetherTile;
    private RefreshCallback mWifiTetherCallback;
    private State mWifiTetherState = new State();

 /*   private QuickSettingsTileView mBTTetherTile;
    private RefreshCallback mBTTetherCallback;
    private State mBTTetherState = new State(); */

    private QuickSettingsTileView mUSBTetherTile;
    private RefreshCallback mUSBTetherCallback;
    private State mUSBTetherState = new State();

    private QuickSettingsTileView mBrightnessTile;
    private RefreshCallback mBrightnessCallback;
    private BrightnessState mBrightnessState = new BrightnessState();

    private QuickSettingsTileView mBugreportTile;
    private RefreshCallback mBugreportCallback;
    private State mBugreportState = new State();

    private QuickSettingsTileView mSettingsTile;
    private RefreshCallback mSettingsCallback;
    private State mSettingsState = new State();

    public QuickSettingsModel(Context context) {
        mContext = context;
        mHandler = new Handler();
        mUserTracker = new CurrentUserTracker(mContext) {
            @Override
            public void onReceive(Context context, Intent intent) {
                super.onReceive(context, intent);
                onUserSwitched();
            }
        };

        mFastChargePath = mContext.getString(com.android.internal.R.string.config_fastChargePath);
        mNextAlarmObserver = new NextAlarmObserver(mHandler);
        mNextAlarmObserver.startObserving();
        mBugreportObserver = new BugreportObserver(mHandler);
        mBugreportObserver.startObserving();
        mBrightnessObserver = new BrightnessObserver(mHandler);
        mBrightnessObserver.startObserving();

        IntentFilter alarmIntentFilter = new IntentFilter();
        alarmIntentFilter.addAction(Intent.ACTION_ALARM_CHANGED);
        context.registerReceiver(mAlarmIntentReceiver, alarmIntentFilter);

        IntentFilter filter = new IntentFilter();
        filter.addAction(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED);
        context.registerReceiver(mBroadcastReceiver, filter);
    }

    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (NfcAdapter.ACTION_ADAPTER_STATE_CHANGED.equals(intent.getAction())) {
                refreshNFCTile();
            }
        }
    };

    void updateResources(ArrayList<String> toggles) {
        for (String toggle : toggles) {
            if (toggle.equals(QuickSettings.SETTINGS_TOGGLE))
                refreshSettingsTile();
            if (toggle.equals(QuickSettings.BATTERY_TOGGLE))
                refreshBatteryTile();
            if (toggle.equals(QuickSettings.GPS_TOGGLE))
                refreshLocationTile();
            if (toggle.equals(QuickSettings.BLUETOOTH_TOGGLE))
                refreshBluetoothTile();
            if (toggle.equals(QuickSettings.BRIGHTNESS_TOGGLE))
                refreshBrightnessTile();
            if (toggle.equals(QuickSettings.ROTATE_TOGGLE))
                refreshRotationLockTile();
            if (toggle.equals(QuickSettings.VIBRATE_TOGGLE))
                refreshVibrateTile();
            if (toggle.equals(QuickSettings.SILENT_TOGGLE))
                refreshSilentTile();
            if (toggle.equals(QuickSettings.NFC_TOGGLE))
                refreshNFCTile();
            if (toggle.equals(QuickSettings.SYNC_TOGGLE))
                refreshSyncTile();
            if (toggle.equals(QuickSettings.TORCH_TOGGLE))
                refreshTorchTile();
            if (toggle.equals(QuickSettings.WIFI_TETHER_TOGGLE))
                refreshWifiTetherTile();
            if (toggle.equals(QuickSettings.USB_TETHER_TOGGLE))
                refreshUSBTetherTile();
           /* if (toggle.equals(QuickSettings.BT_TETHER_TOGGLE))
                refreshBTTetherTile();  */
            if (toggle.equals(QuickSettings.FCHARGE_TOGGLE))
                refreshFChargeTile();
            if (toggle.equals(QuickSettings.TWOG_TOGGLE))
                refresh2gTile();
            if (toggle.equals(QuickSettings.LTE_TOGGLE))
                refreshLTETile();
        }

    }

    void refreshFChargeTile() {
        new AsyncTask<Void, Void, Boolean>() {
            @Override
            protected Boolean doInBackground(Void... params) {
                return isFastChargeOn();
            }

            @Override
            protected void onPostExecute(Boolean result) {
                Prefs.setLastFastChargeState(mContext, result);
                updateFastChargeTile(result);
            }
        }.execute();
    }

    void removeAllViews() {
        if (mUserTile != null)
            mUserTile.removeAllViews();
        if (mSettingsTile != null)
            mSettingsTile.removeAllViews();
    }

    // Settings
    void addSettingsTile(QuickSettingsTileView view, RefreshCallback cb) {
        mSettingsTile = view;
        mSettingsCallback = cb;
        refreshSettingsTile();
    }

    void refreshSettingsTile() {
        Resources r = mContext.getResources();
        mSettingsState.label = r.getString(R.string.quick_settings_settings_label);
        mSettingsCallback.refreshView(mSettingsTile, mSettingsState);
    }

    // User
    void addUserTile(QuickSettingsTileView view, RefreshCallback cb) {
        mUserTile = view;
        mUserCallback = cb;
        mUserCallback.refreshView(mUserTile, mUserState);
    }

    void setUserTileInfo(String name, Drawable avatar) {
        if (mUserCallback != null) {
            mUserState.label = name;
            mUserState.avatar = avatar;
            mUserCallback.refreshView(mUserTile, mUserState);
        }
    }

    // Favorite Contact
    void addFavContactTile(QuickSettingsTileView view, RefreshCallback cb) {
        mFavContactTile = view;
        mFavContactCallback = cb;
        mFavContactCallback.refreshView(mFavContactTile, mFavContactState);
    }

    void setFavContactTileInfo(String name, Drawable avatar) {
        if (mFavContactCallback != null) {
            mFavContactState.label = name;
            mFavContactState.avatar = avatar;
            mFavContactCallback.refreshView(mFavContactTile, mFavContactState);
        }
    }

    // Time
    void addTimeTile(QuickSettingsTileView view, RefreshCallback cb) {
        mTimeTile = view;
        mTimeCallback = cb;
        mTimeCallback.refreshView(view, mTimeState);
    }

    // Alarm
    void addAlarmTile(QuickSettingsTileView view, RefreshCallback cb) {
        mAlarmTile = view;
        mAlarmCallback = cb;
        mAlarmCallback.refreshView(view, mAlarmState);
    }

    void onAlarmChanged(Intent intent) {
        mAlarmState.enabled = intent.getBooleanExtra("alarmSet", false);
        mAlarmCallback.refreshView(mAlarmTile, mAlarmState);
    }

    void onNextAlarmChanged() {
        mAlarmState.label = Settings.System.getString(mContext.getContentResolver(),
                Settings.System.NEXT_ALARM_FORMATTED);
        mAlarmCallback.refreshView(mAlarmTile, mAlarmState);
    }

    // Airplane Mode
    void addAirplaneModeTile(QuickSettingsTileView view, RefreshCallback cb) {
        mAirplaneModeTile = view;
        mAirplaneModeTile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mAirplaneModeState.enabled) {
                    setAirplaneModeState(false);
                } else {
                    setAirplaneModeState(true);
                }
            }
        });
        mAirplaneModeCallback = cb;
        int airplaneMode = Settings.Global.getInt(mContext.getContentResolver(),
                Settings.Global.AIRPLANE_MODE_ON, 0);
        onAirplaneModeChanged(airplaneMode != 0);
    }

    private void setAirplaneModeState(boolean enabled) {
        // TODO: Sets the view to be "awaiting" if not already awaiting

        // Change the system setting
        Settings.Global.putInt(mContext.getContentResolver(), Settings.Global.AIRPLANE_MODE_ON,
                enabled ? 1 : 0);

        // Post the intent
        Intent intent = new Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        intent.putExtra("state", enabled);
        mContext.sendBroadcast(intent);
    }

    // NetworkSignalChanged callback
    @Override
    public void onAirplaneModeChanged(boolean enabled) {
        // TODO: If view is in awaiting state, disable
        Resources r = mContext.getResources();
        mAirplaneModeState.enabled = enabled;
        mAirplaneModeState.iconId = (enabled ?
                R.drawable.ic_qs_airplane_on :
                R.drawable.ic_qs_airplane_off);
        mAirplaneModeState.label = r.getString(R.string.quick_settings_airplane_mode_label);
        if (togglesContain(QuickSettings.AIRPLANE_TOGGLE))
            mAirplaneModeCallback.refreshView(mAirplaneModeTile, mAirplaneModeState);
    }

    // Wifi
    void addWifiTile(QuickSettingsTileView view, RefreshCallback cb) {
        mWifiTile = view;
        mWifiCallback = cb;
        mWifiCallback.refreshView(mWifiTile, mWifiState);
    }

    // Remove the double quotes that the SSID may contain
    public static String removeDoubleQuotes(String string) {
        if (string == null)
            return null;
        final int length = string.length();
        if ((length > 1) && (string.charAt(0) == '"') && (string.charAt(length - 1) == '"')) {
            return string.substring(1, length - 1);
        }
        return string;
    }

    // Remove the period from the network name
    public static String removeTrailingPeriod(String string) {
        if (string == null)
            return null;
        final int length = string.length();
        if (string.endsWith(".")) {
            string.substring(0, length - 1);
        }
        return string;
    }

    // NetworkSignalChanged callback
    @Override
    public void onWifiSignalChanged(boolean enabled, int wifiSignalIconId,
            String wifiSignalContentDescription, String enabledDesc) {
        // TODO: If view is in awaiting state, disable
        Resources r = mContext.getResources();

        boolean wifiConnected = enabled && (wifiSignalIconId > 0) && (enabledDesc != null);
        boolean wifiNotConnected = (wifiSignalIconId > 0) && (enabledDesc == null);
        mWifiState.enabled = enabled;
        mWifiState.connected = wifiConnected;
        if (wifiConnected) {
            mWifiState.iconId = wifiSignalIconId;
            mWifiState.label = removeDoubleQuotes(enabledDesc);
            mWifiState.signalContentDescription = wifiSignalContentDescription;
        } else if (wifiNotConnected) {
            mWifiState.iconId = R.drawable.ic_qs_wifi_0;
            mWifiState.label = r.getString(R.string.quick_settings_wifi_label);
            mWifiState.signalContentDescription = r.getString(R.string.accessibility_no_wifi);
        } else {
            mWifiState.iconId = R.drawable.ic_qs_wifi_no_network;
            mWifiState.label = r.getString(R.string.quick_settings_wifi_off_label);
            mWifiState.signalContentDescription = r.getString(R.string.accessibility_wifi_off);
        }
        if (togglesContain(QuickSettings.WIFI_TOGGLE))
            mWifiCallback.refreshView(mWifiTile, mWifiState);
    }

    // RSSI
    void addRSSITile(QuickSettingsTileView view, RefreshCallback cb) {
        mRSSITile = view;
        mRSSICallback = cb;
        mRSSICallback.refreshView(mRSSITile, mRSSIState);
    }

    boolean deviceSupportsTelephony() {
        PackageManager pm = mContext.getPackageManager();
        return pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY);
    }

    // NetworkSignalChanged callback
    @Override
    public void onMobileDataSignalChanged(
            boolean enabled, int mobileSignalIconId, String signalContentDescription,
            int dataTypeIconId, String dataContentDescription, String enabledDesc) {
        if (deviceSupportsTelephony()) {
            // TODO: If view is in awaiting state, disable
            Resources r = mContext.getResources();
            mRSSIState.signalIconId = enabled && (mobileSignalIconId > 0)
                    ? mobileSignalIconId
                    : R.drawable.ic_qs_signal_no_signal;
            mRSSIState.signalContentDescription = enabled && (mobileSignalIconId > 0)
                    ? signalContentDescription
                    : r.getString(R.string.accessibility_no_signal);
            mRSSIState.dataTypeIconId = enabled && (dataTypeIconId > 0) && !mWifiState.enabled
                    ? dataTypeIconId
                    : 0;
            mRSSIState.dataContentDescription = enabled && (dataTypeIconId > 0) && !mWifiState.enabled
                    ? dataContentDescription
                    : r.getString(R.string.accessibility_no_data);
            mRSSIState.label = enabled
                    ? removeTrailingPeriod(enabledDesc)
                    : r.getString(R.string.quick_settings_rssi_emergency_only);
            if (togglesContain(QuickSettings.SIGNAL_TOGGLE))
                mRSSICallback.refreshView(mRSSITile, mRSSIState);
        }
    }

    // Bluetooth
    void addBluetoothTile(QuickSettingsTileView view, RefreshCallback cb) {
        mBluetoothTile = view;
        mBluetoothCallback = cb;

        final BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        mBluetoothState.enabled = adapter.isEnabled();
        mBluetoothState.connected =
                (adapter.getConnectionState() == BluetoothAdapter.STATE_CONNECTED);
        onBluetoothStateChange(mBluetoothState);
    }

    boolean deviceSupportsBluetooth() {
        return (BluetoothAdapter.getDefaultAdapter() != null);
    }

    // BluetoothController callback
    @Override
    public void onBluetoothStateChange(boolean on) {
        mBluetoothState.enabled = on;
        onBluetoothStateChange(mBluetoothState);
    }

    public void onBluetoothStateChange(BluetoothState bluetoothStateIn) {
        // TODO: If view is in awaiting state, disable
        Resources r = mContext.getResources();
        mBluetoothState.enabled = bluetoothStateIn.enabled;
        mBluetoothState.connected = bluetoothStateIn.connected;
        if (mBluetoothState.enabled) {
            if (mBluetoothState.connected) {
                mBluetoothState.iconId = R.drawable.ic_qs_bluetooth_on;
                mBluetoothState.stateContentDescription = r.getString(R.string.accessibility_desc_connected);
            } else {
                mBluetoothState.iconId = R.drawable.ic_qs_bluetooth_not_connected;
                mBluetoothState.stateContentDescription = r.getString(R.string.accessibility_desc_on);
            }
            mBluetoothState.label = r.getString(R.string.quick_settings_bluetooth_label);
        } else {
            mBluetoothState.iconId = R.drawable.ic_qs_bluetooth_off;
            mBluetoothState.label = r.getString(R.string.quick_settings_bluetooth_off_label);
            mBluetoothState.stateContentDescription = r.getString(R.string.accessibility_desc_off);
        }
        if(mBluetoothTile != null) {
            mBluetoothCallback.refreshView(mBluetoothTile, mBluetoothState);
        }
    }

    void refreshBluetoothTile() {
        if (mBluetoothTile != null) {
            onBluetoothStateChange(mBluetoothState.enabled);
        }
    }

    // Battery
    void addBatteryTile(QuickSettingsTileView view, RefreshCallback cb) {
        mBatteryTile = view;
        mBatteryCallback = cb;
        mBatteryCallback.refreshView(mBatteryTile, mBatteryState);
    }

    // BatteryController callback
    @Override
    public void onBatteryLevelChanged(int level, boolean pluggedIn) {
        mBatteryState.batteryLevel = level;
        mBatteryState.pluggedIn = pluggedIn;
        if (togglesContain(QuickSettings.BATTERY_TOGGLE))
            mBatteryCallback.refreshView(mBatteryTile, mBatteryState);
    }

    void refreshBatteryTile() {
        mBatteryCallback.refreshView(mBatteryTile, mBatteryState);
    }

    // Location
    void addLocationTile(QuickSettingsTileView view, RefreshCallback cb) {
        mLocationTile = view;
        mLocationCallback = cb;
        refreshLocationTile();
    }

    void refreshLocationTile() {
        mLocationCallback.refreshView(mLocationTile, mLocationState);
    }

    // LocationController callback
    @Override
    public void onLocationGpsStateChanged(boolean inUse, String description) {
        mLocationState.enabled = inUse;
        mLocationState.iconId = inUse
                ? R.drawable.ic_qs_gps_on
                : R.drawable.ic_qs_gps_off;
        mLocationState.label = description;
        if (togglesContain(QuickSettings.GPS_TOGGLE))
            mLocationCallback.refreshView(mLocationTile, mLocationState);
    }

    // Bug report
    void addBugreportTile(QuickSettingsTileView view, RefreshCallback cb) {
        mBugreportTile = view;
        mBugreportCallback = cb;
        onBugreportChanged();
    }

    // SettingsObserver callback
    public void onBugreportChanged() {
        final ContentResolver cr = mContext.getContentResolver();
        boolean enabled = false;
        try {
            enabled = (Settings.Secure.getInt(cr, Settings.Secure.BUGREPORT_IN_POWER_MENU) != 0);
        } catch (SettingNotFoundException e) {
        }

        mBugreportState.enabled = enabled;
        mBugreportCallback.refreshView(mBugreportTile, mBugreportState);
    }

    // Wifi Display
    void addWifiDisplayTile(QuickSettingsTileView view, RefreshCallback cb) {
        mWifiDisplayTile = view;
        mWifiDisplayCallback = cb;
    }

    public void onWifiDisplayStateChanged(WifiDisplayStatus status) {
        mWifiDisplayState.enabled =
                (status.getFeatureState() == WifiDisplayStatus.FEATURE_STATE_ON);
        if (status.getActiveDisplay() != null) {
            mWifiDisplayState.label = status.getActiveDisplay().getFriendlyDisplayName();
            mWifiDisplayState.iconId = R.drawable.ic_qs_remote_display_connected;
        } else {
            mWifiDisplayState.label = mContext.getString(
                    R.string.quick_settings_wifi_display_no_connection_label);
            mWifiDisplayState.iconId = R.drawable.ic_qs_remote_display;
        }
        mWifiDisplayCallback.refreshView(mWifiDisplayTile, mWifiDisplayState);

    }

    // IME
    void addImeTile(QuickSettingsTileView view, RefreshCallback cb) {
        mImeTile = view;
        mImeCallback = cb;
        mImeCallback.refreshView(mImeTile, mImeState);
    }

    /*
     * This implementation is taken from
     * InputMethodManagerService.needsToShowImeSwitchOngoingNotification().
     */
    private boolean needsToShowImeSwitchOngoingNotification(InputMethodManager imm) {
        List<InputMethodInfo> imis = imm.getEnabledInputMethodList();
        final int N = imis.size();
        if (N > 2)
            return true;
        if (N < 1)
            return false;
        int nonAuxCount = 0;
        int auxCount = 0;
        InputMethodSubtype nonAuxSubtype = null;
        InputMethodSubtype auxSubtype = null;
        for (int i = 0; i < N; ++i) {
            final InputMethodInfo imi = imis.get(i);
            final List<InputMethodSubtype> subtypes = imm.getEnabledInputMethodSubtypeList(imi,
                    true);
            final int subtypeCount = subtypes.size();
            if (subtypeCount == 0) {
                ++nonAuxCount;
            } else {
                for (int j = 0; j < subtypeCount; ++j) {
                    final InputMethodSubtype subtype = subtypes.get(j);
                    if (!subtype.isAuxiliary()) {
                        ++nonAuxCount;
                        nonAuxSubtype = subtype;
                    } else {
                        ++auxCount;
                        auxSubtype = subtype;
                    }
                }
            }
        }
        if (nonAuxCount > 1 || auxCount > 1) {
            return true;
        } else if (nonAuxCount == 1 && auxCount == 1) {
            if (nonAuxSubtype != null && auxSubtype != null
                    && (nonAuxSubtype.getLocale().equals(auxSubtype.getLocale())
                            || auxSubtype.overridesImplicitlyEnabledSubtype()
                            || nonAuxSubtype.overridesImplicitlyEnabledSubtype())
                    && nonAuxSubtype.containsExtraValueKey(TAG_TRY_SUPPRESSING_IME_SWITCHER)) {
                return false;
            }
            return true;
        }
        return false;
    }

    void onImeWindowStatusChanged(boolean visible) {
        InputMethodManager imm =
                (InputMethodManager) mContext.getSystemService(Context.INPUT_METHOD_SERVICE);
        List<InputMethodInfo> imis = imm.getInputMethodList();

        mImeState.enabled = (visible && needsToShowImeSwitchOngoingNotification(imm));
        mImeState.label = getCurrentInputMethodName(mContext, mContext.getContentResolver(),
                imm, imis, mContext.getPackageManager());
        if (mImeCallback != null) {
            mImeCallback.refreshView(mImeTile, mImeState);
        }
    }

    private static String getCurrentInputMethodName(Context context, ContentResolver resolver,
            InputMethodManager imm, List<InputMethodInfo> imis, PackageManager pm) {
        if (resolver == null || imis == null)
            return null;
        final String currentInputMethodId = Settings.Secure.getString(resolver,
                Settings.Secure.DEFAULT_INPUT_METHOD);
        if (TextUtils.isEmpty(currentInputMethodId))
            return null;
        for (InputMethodInfo imi : imis) {
            if (currentInputMethodId.equals(imi.getId())) {
                final InputMethodSubtype subtype = imm.getCurrentInputMethodSubtype();
                final CharSequence summary = subtype != null
                        ? subtype.getDisplayName(context, imi.getPackageName(),
                                imi.getServiceInfo().applicationInfo)
                        : context.getString(R.string.quick_settings_ime_label);
                return summary.toString();
            }
        }
        return null;
    }

    // Rotation lock
    void addRotationLockTile(QuickSettingsTileView view, RefreshCallback cb) {
        mRotationLockTile = view;
        mRotationLockCallback = cb;
        onRotationLockChanged();
    }

    void onRotationLockChanged() {
        boolean locked = RotationPolicy.isRotationLocked(mContext);
        mRotationLockState.enabled = locked;
        mRotationLockState.iconId = locked
                ? R.drawable.ic_qs_rotation_locked
                : R.drawable.ic_qs_auto_rotate;
        mRotationLockState.label = locked
                ? mContext.getString(R.string.quick_settings_rotation_locked_label)
                : mContext.getString(R.string.quick_settings_rotation_unlocked_label);

        // may be called before addRotationLockTile due to
        // RotationPolicyListener in QuickSettings
        if (mRotationLockTile != null && mRotationLockCallback != null) {
            mRotationLockCallback.refreshView(mRotationLockTile, mRotationLockState);
        }
    }

    void refreshRotationLockTile() {
        if (mRotationLockTile != null) {
            onRotationLockChanged();
        }
    }

    // Vibrate
    void addVibrateTile(QuickSettingsTileView view, RefreshCallback cb) {
        mVibrateTile = view;
        mVibrateCallback = cb;
        onVibrateChanged();
    }

    void onVibrateChanged() {
        AudioManager am = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        boolean enabled = am.getRingerMode() == AudioManager.RINGER_MODE_VIBRATE;
        mVibrateState.enabled = enabled;
        mVibrateState.iconId = enabled
                ? R.drawable.ic_qs_vibrate_on
                : R.drawable.ic_qs_vibrate_off;
        mVibrateState.label = enabled
                ? mContext.getString(R.string.quick_settings_vibrate_on_label)
                : mContext.getString(R.string.quick_settings_vibrate_off_label);

        if (mVibrateTile != null && mVibrateCallback != null) {
            mVibrateCallback.refreshView(mVibrateTile, mVibrateState);
        }
    }

    void refreshVibrateTile() {
        if (mVibrateTile != null) {
            onVibrateChanged();
        }
    }

    // Silent
    void addSilentTile(QuickSettingsTileView view, RefreshCallback cb) {
        mSilentTile = view;
        mSilentCallback = cb;
        onSilentChanged();
    }

    void addSoundStateTile(QuickSettingsTileView view, RefreshCallback cb) {
        mSoundStateTile = view;
        mSoundStateCallback = cb;
        refreshSoundStateTile();
    }

    void onSilentChanged() {
        AudioManager am = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        boolean enabled = am.getRingerMode() == AudioManager.RINGER_MODE_SILENT;
        mSilentState.enabled = enabled;
        mSilentState.iconId = enabled
                ? R.drawable.ic_qs_silence_on
                : R.drawable.ic_qs_silence_off;
        mSilentState.label = enabled
                ? mContext.getString(R.string.quick_settings_silent_on_label)
                : mContext.getString(R.string.quick_settings_silent_off_label);

        if (mSilentTile != null && mSilentCallback != null) {
            mSilentCallback.refreshView(mSilentTile, mSilentState);
        }
    }

    void refreshSilentTile() {
        if (mSilentTile != null) {
            onSilentChanged();
        }
    }

    void refreshSoundStateTile() {
        if (mSoundStateTile != null) {
            AudioManager am = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
            boolean enabled;
            int iconId;
            int label;
            switch(am.getRingerMode()) {
                case AudioManager.RINGER_MODE_NORMAL:
                default:
                    enabled = false;
                    iconId = R.drawable.ic_qs_sound_off;
                    label = R.string.quick_settings_sound_on;
                    break;
                case AudioManager.RINGER_MODE_VIBRATE:
                    enabled = true;
                    iconId = R.drawable.ic_qs_vibrate_on;
                    label = R.string.quick_settings_vibrate_on_label;
                    break;
                case AudioManager.RINGER_MODE_SILENT:
                    enabled = true;
                    iconId = R.drawable.ic_qs_silence_on;
                    label = R.string.quick_settings_silent_on_label;
                    break;
            }
            mSoundStateState.enabled = enabled;
            mSoundStateState.iconId = iconId;
            mSoundStateState.label = mContext.getString(label);

            if (mSoundStateCallback != null) {
                mSoundStateCallback.refreshView(mSoundStateTile, mSoundStateState);
            }
        }
    }

    // Fcharge
    void addFChargeTile(QuickSettingsTileView view, RefreshCallback cb) {
        mFChargeTile = view;
        mFChargeCallback = cb;
        refreshFChargeTile();
    }

    void updateFastChargeTile(boolean enabled) {
        if (mFChargeTile != null) {
            mFChargeState.enabled = enabled;
            mFChargeState.iconId = enabled
                    ? R.drawable.ic_qs_fcharge_on
                    : R.drawable.ic_qs_fcharge_off;
            mFChargeState.label = enabled
                    ? mContext.getString(R.string.quick_settings_fcharge_on_label)
                    : mContext.getString(R.string.quick_settings_fcharge_off_label);

            if (mFChargeTile != null && mFChargeCallback != null) {
                mFChargeCallback.refreshView(mFChargeTile, mFChargeState);
            }
        }
    }

    // Sync
    void addSyncTile(QuickSettingsTileView view, RefreshCallback cb) {
        mSyncTile = view;
        mSyncCallback = cb;
        onSyncChanged();
    }

    void onSyncChanged() {
        boolean enabled = ContentResolver.getMasterSyncAutomatically();
        mSyncState.enabled = enabled;
        mSyncState.iconId = enabled
                ? R.drawable.ic_qs_sync_on
                : R.drawable.ic_qs_sync_off;
        mSyncState.label = enabled
                ? mContext.getString(R.string.quick_settings_sync_on_label)
                : mContext.getString(R.string.quick_settings_sync_off_label);

        if (mSyncTile != null && mSyncCallback != null) {
            mSyncCallback.refreshView(mSyncTile, mSyncState);
        }
    }

    void refreshSyncTile() {
        if (mSyncTile != null) {
            onSyncChanged();
        }
    }

    // LTE
    void addLTETile(QuickSettingsTileView view, RefreshCallback cb) {
        mLTETile = view;
        mLTECallback = cb;
        onLTEChanged();
    }

    void onLTEChanged() {
        try {
            dataState = Settings.Global.getInt(mContext.getContentResolver(), Settings.Global.PREFERRED_NETWORK_MODE);
        } catch (SettingNotFoundException e) {
            e.printStackTrace();
        }
        boolean enabled = (dataState == PhoneConstants.NT_MODE_LTE_CDMA_EVDO) || (dataState == PhoneConstants.NT_MODE_GLOBAL);
        mLTEState.enabled = enabled;
        mLTEState.iconId = enabled
                ? R.drawable.ic_qs_lte_on
                : R.drawable.ic_qs_lte_off;
        mLTEState.label = enabled
                ? mContext.getString(R.string.quick_settings_lte_on_label)
                : mContext.getString(R.string.quick_settings_lte_off_label);

        if (mLTETile != null && mLTECallback != null) {
            mLTECallback.refreshView(mLTETile, mLTEState);
        }
    }

    void refreshLTETile() {
        if (mLTETile != null) {
            onLTEChanged();
        }
    }

    // 2g
    void add2gTile(QuickSettingsTileView view, RefreshCallback cb) {
        m2gTile = view;
        m2gCallback = cb;
        on2gChanged();
    }

    void on2gChanged() {
        try {
            dataState = Settings.Global.getInt(mContext.getContentResolver(), Settings.Global.PREFERRED_NETWORK_MODE);
        } catch (SettingNotFoundException e) {
            e.printStackTrace();
        }
        boolean enabled = dataState == PhoneConstants.NT_MODE_GSM_ONLY;
        m2gState.enabled = enabled;
        m2gState.iconId = enabled
                ? R.drawable.ic_qs_2g_on
                : R.drawable.ic_qs_2g_off;
        m2gState.label = enabled
                ? mContext.getString(R.string.quick_settings_twog_on_label)
                : mContext.getString(R.string.quick_settings_twog_off_label);

        if (m2gTile != null && m2gCallback != null) {
            m2gCallback.refreshView(m2gTile, m2gState);
        }
    }

    void refresh2gTile() {
        if (m2gTile != null) {
            on2gChanged();
        }
    }

    // NFC
    void addNFCTile(QuickSettingsTileView view, RefreshCallback cb) {
        mNFCTile = view;
        mNFCCallback = cb;
        refreshNFCTile();
    }

    void onNFCChanged() {
        boolean enabled = false;
        if (mNfcAdapter != null) {
            enabled = mNfcAdapter.isEnabled();
        }
        mNFCState.enabled = enabled;
        mNFCState.iconId = enabled
                ? R.drawable.ic_qs_nfc_on
                : R.drawable.ic_qs_nfc_off;
        mNFCState.label = enabled
                ? mContext.getString(R.string.quick_settings_nfc_on_label)
                : mContext.getString(R.string.quick_settings_nfc_off_label);

        if (mNFCTile != null && mNFCCallback != null) {
            mNFCCallback.refreshView(mNFCTile, mNFCState);
        }
    }

    void refreshNFCTile() {
        if (mNFCTile != null) {
            onNFCChanged();
        }
    }

    // Wifi Tether
    void addWifiTetherTile(QuickSettingsTileView view, RefreshCallback cb) {
        mWifiTetherTile = view;
        mWifiTetherCallback = cb;
        onWifiTetherChanged();
    }

    void onWifiTetherChanged() {
        wifiManager = (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);
        int mWifiApState = wifiManager.getWifiApState();
        boolean enabled = mWifiApState == WifiManager.WIFI_AP_STATE_ENABLED || mWifiApState == WifiManager.WIFI_AP_STATE_ENABLING;
        mWifiTetherState.enabled = enabled;
        mWifiTetherState.iconId = enabled
                ? R.drawable.ic_qs_wifi_tether_on
                : R.drawable.ic_qs_wifi_tether_off;
        mWifiTetherState.label = enabled
                ? mContext.getString(R.string.quick_settings_wifi_tether_on_label)
                : mContext.getString(R.string.quick_settings_wifi_tether_off_label);

        if (mWifiTetherTile != null && mWifiTetherCallback != null) {
            mWifiTetherCallback.refreshView(mWifiTetherTile, mWifiTetherState);
        }
    }

    void refreshWifiTetherTile() {
        if (mWifiTetherTile != null) {
            onWifiTetherChanged();
        }
    }

    // USB Tether
    void addUSBTetherTile(QuickSettingsTileView view, RefreshCallback cb) {
        mUSBTetherTile = view;
        mUSBTetherCallback = cb;
        onUSBTetherChanged();
    }

    void onUSBTetherChanged() {
        boolean enabled = updateUsbState();
        mUSBTetherState.enabled = enabled;
        mUSBTetherState.iconId = enabled
                ? R.drawable.ic_qs_usb_tether_on
                : R.drawable.ic_qs_usb_tether_off;
        mUSBTetherState.label = enabled
                ? mContext.getString(R.string.quick_settings_usb_tether_on_label)
                : mContext.getString(R.string.quick_settings_usb_tether_off_label);

        if (mUSBTetherTile != null && mUSBTetherCallback != null) {
            mUSBTetherCallback.refreshView(mUSBTetherTile, mUSBTetherState);
        }
    }

    void refreshUSBTetherTile() {
        if (mUSBTetherTile != null) {
            onUSBTetherChanged();
        }
    }

    public boolean updateUsbState() {
        ConnectivityManager connManager = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        String[] mUsbRegexs = connManager.getTetherableUsbRegexs();
        String[] tethered = connManager.getTetheredIfaces();
        boolean usbTethered = false;
        for (String s : tethered) {
            for (String regex : mUsbRegexs) {
                if (s.matches(regex)) {
                    return true;
                } else {
                    return false;
                }
            }
        }
      return false;
    }

    // Torch
    void addTorchTile(QuickSettingsTileView view, RefreshCallback cb) {
        mTorchTile = view;
        mTorchCallback = cb;
        onTorchChanged();
    }

    void onTorchChanged() {
        boolean enabled = Settings.System.getBoolean(mContext.getContentResolver(), Settings.System.TORCH_STATE, false);
        mTorchState.enabled = enabled;
        mTorchState.iconId = enabled
                ? R.drawable.ic_qs_torch_on
                : R.drawable.ic_qs_torch_off;
        mTorchState.label = enabled
                ? mContext.getString(R.string.quick_settings_torch_on_label)
                : mContext.getString(R.string.quick_settings_torch_off_label);

        if (mTorchTile != null && mTorchCallback != null) {
            mTorchCallback.refreshView(mTorchTile, mTorchState);
        }
    }

    void refreshTorchTile() {
        if (mTorchTile != null) {
            onTorchChanged();
        }
    }

    // Brightness
    void addBrightnessTile(QuickSettingsTileView view, RefreshCallback cb) {
        mBrightnessTile = view;
        mBrightnessCallback = cb;
        onBrightnessLevelChanged();
    }

    @Override
    public void onBrightnessLevelChanged() {
        Resources r = mContext.getResources();
        int mode = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
                mUserTracker.getCurrentUserId());
        mBrightnessState.autoBrightness =
                (mode == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC);
        mBrightnessState.iconId = mBrightnessState.autoBrightness
                ? R.drawable.ic_qs_brightness_auto_on
                : R.drawable.ic_qs_brightness_auto_off;
        mBrightnessState.label = r.getString(R.string.quick_settings_brightness_label);
        if (togglesContain(QuickSettings.BRIGHTNESS_TOGGLE))
            mBrightnessCallback.refreshView(mBrightnessTile, mBrightnessState);
    }

    void refreshBrightnessTile() {
        onBrightnessLevelChanged();
    }

    /**
     * Method checks for if a tile is being used or not
     * 
     * @param QuickSettings Tile String Constant
     * @return if that tile is being used
     */
    private boolean togglesContain(String tile) {
        ContentResolver resolver = mContext.getContentResolver();
        String toggles = Settings.System.getString(resolver, Settings.System.QUICK_TOGGLES);
       
        if (toggles != null) {
            ArrayList tiles = new ArrayList();
            String[] splitter = toggles.split("\\|");
            for (String toggle : splitter) {
                tiles.add(toggle);
            }
            return tiles.contains(tile);
        }

        return getDefaultTiles().contains(tile);
    }
    
    private ArrayList getDefaultTiles() {
        ArrayList tiles = new ArrayList();
        tiles.add(QuickSettings.USER_TOGGLE);
        tiles.add(QuickSettings.BRIGHTNESS_TOGGLE);
        tiles.add(QuickSettings.SETTINGS_TOGGLE);
        tiles.add(QuickSettings.WIFI_TOGGLE);
        if (deviceSupportsTelephony()) {
            tiles.add(QuickSettings.SIGNAL_TOGGLE);
        }
        if (mContext.getResources().getBoolean(R.bool.quick_settings_show_rotation_lock)) {
            tiles.add(QuickSettings.ROTATE_TOGGLE);
        }
        tiles.add(QuickSettings.BATTERY_TOGGLE);
        tiles.add(QuickSettings.AIRPLANE_TOGGLE);
        if (deviceSupportsBluetooth()) {
            tiles.add(QuickSettings.BLUETOOTH_TOGGLE);
        }
        return tiles;
    }

    // User switch: need to update visuals of all tiles known to have per-user
    // state
    void onUserSwitched() {
        mBrightnessObserver.startObserving();
        onRotationLockChanged();
        onBrightnessLevelChanged();
        onNextAlarmChanged();
        onBugreportChanged();
    }

    public void setNfcAdapter(NfcAdapter adapter) {
        mNfcAdapter = adapter;
    }

    protected boolean isFastChargeOn() {
        if(mFastChargePath == null || mFastChargePath.isEmpty()) {
            return false;
        }
        File file = new File(mFastChargePath);
        if(!file.exists()) {
            return false;
        }
        String content = null;
        FileReader reader = null;
        try {
            reader = new FileReader(file);
            char[] chars = new char[(int) file.length()];
            reader.read(chars);
            content = new String(chars).trim();
        } catch (Exception e) {
            e.printStackTrace();
            content = null;
        } finally {
            try {
                reader.close();
            } catch (IOException e) {
                // ignore
            }
        }
        return "1".equals(content) || "Y".equalsIgnoreCase(content);
    }
}
