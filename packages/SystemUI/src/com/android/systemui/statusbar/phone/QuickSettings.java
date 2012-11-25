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

import com.android.internal.view.RotationPolicy;
import com.android.internal.widget.LockPatternUtils;
import com.android.systemui.R;

import com.android.systemui.statusbar.phone.QuickSettingsModel.BluetoothState;
import com.android.systemui.statusbar.phone.QuickSettingsModel.RSSIState;
import com.android.systemui.statusbar.phone.QuickSettingsModel.State;
import com.android.systemui.statusbar.phone.QuickSettingsModel.UserState;
import com.android.systemui.statusbar.phone.QuickSettingsModel.WifiState;
import com.android.systemui.statusbar.policy.BatteryController;
import com.android.systemui.statusbar.policy.BluetoothController;
import com.android.systemui.statusbar.policy.BrightnessController;
import com.android.systemui.statusbar.policy.LocationController;
import com.android.systemui.statusbar.policy.NetworkController;
import com.android.systemui.statusbar.policy.ToggleSlider;

import android.app.ActivityManagerNative;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.CursorLoader;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.UserInfo;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LevelListDrawable;
import android.hardware.display.DisplayManager;
import android.hardware.display.WifiDisplayStatus;
import android.location.LocationManager;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.RemoteException;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Profile;
import android.provider.Settings;
import android.util.Log;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.WindowManagerGlobal;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;

/**
 *
 */
class QuickSettings {
    private static final String TAG = "QuickSettings";
    public static final boolean SHOW_IME_TILE = false;

    private static final String TOGGLE_PIPE = "|";

    private static final int USER_TILE = 0;
    private static final int BRIGHTNESS_TILE = 1;
    private static final int SETTINGS_TILE = 2;
    private static final int WIFI_TILE = 3;
    private static final int SIGNAL_TILE = 4;
    private static final int ROTATE_TILE = 5;
    private static final int CLOCK_TILE = 6;
    private static final int GPS_TILE = 7;
    private static final int IME_TILE = 8;
    private static final int BATTERY_TILE = 9;
    private static final int AIRPLANE_TILE = 10;
    private static final int BLUETOOTH_TILE = 11;

    public static final String USER_TOGGLE = "USER";
    public static final String BRIGHTNESS_TOGGLE = "BRIGHTNESS";
    public static final String SETTINGS_TOGGLE = "SETTINGS";
    public static final String WIFI_TOGGLE = "WIFI";
    public static final String SIGNAL_TOGGLE = "SIGNAL";
    public static final String ROTATE_TOGGLE = "ROTATE";
    public static final String CLOCK_TOGGLE = "CLOCK";
    public static final String GPS_TOGGLE = "GPS";
    public static final String IME_TOGGLE = "IME";
    public static final String BATTERY_TOGGLE = "BATTERY";
    public static final String AIRPLANE_TOGGLE = "AIRPLANE_MODE";
    public static final String BLUETOOTH_TOGGLE = "BLUETOOTH";

    private static final String DEFAULT_TOGGLES = "default";

    public String strGPSoff = "GPS Off";
    public String strGPSon = "GPS On";

    private Context mContext;
    private PanelBar mBar;
    private QuickSettingsModel mModel;
    private ViewGroup mContainerView;

    private DisplayManager mDisplayManager;
    private WifiDisplayStatus mWifiDisplayStatus;
    private WifiManager wifiManager;
    private LocationManager locationManager;
    private PhoneStatusBar mStatusBarService;
    private BluetoothState mBluetoothState;

    private BrightnessController mBrightnessController;
    private BluetoothController mBluetoothController;

    private Dialog mBrightnessDialog;
    private int mBrightnessDialogShortTimeout;
    private int mBrightnessDialogLongTimeout;

    private AsyncTask<Void, Void, Pair<String, Drawable>> mUserInfoTask;

    private LevelListDrawable mBatteryLevels;
    private LevelListDrawable mChargingBatteryLevels;

    boolean mTilesSetUp = false;

    private Handler mHandler;

    private ArrayList<String> toggles;
    private String userToggles = null;

    private HashMap<String, Integer> toggleMap;

    private HashMap<String, Integer> getToggleMap() {
        if (toggleMap == null) {
            toggleMap = new HashMap<String, Integer>();
            toggleMap.put(USER_TOGGLE, USER_TILE);
            toggleMap.put(BRIGHTNESS_TOGGLE, BRIGHTNESS_TILE);
            toggleMap.put(SETTINGS_TOGGLE, SETTINGS_TILE);
            toggleMap.put(WIFI_TOGGLE, WIFI_TILE);
            toggleMap.put(SIGNAL_TOGGLE, SIGNAL_TILE);
            toggleMap.put(ROTATE_TOGGLE, ROTATE_TILE);
            toggleMap.put(CLOCK_TOGGLE, CLOCK_TILE);
            toggleMap.put(GPS_TOGGLE, GPS_TILE);
            toggleMap.put(IME_TOGGLE, IME_TILE);
            toggleMap.put(BATTERY_TOGGLE, BATTERY_TILE);
            toggleMap.put(AIRPLANE_TOGGLE, AIRPLANE_TILE);
            toggleMap.put(BLUETOOTH_TOGGLE, BLUETOOTH_TILE);
        }
        return toggleMap;
    }

    // The set of QuickSettingsTiles that have dynamic spans (and need to be
    // updated on
    // configuration change)
    private final ArrayList<QuickSettingsTileView> mDynamicSpannedTiles =
            new ArrayList<QuickSettingsTileView>();

    private final RotationPolicy.RotationPolicyListener mRotationPolicyListener =
            new RotationPolicy.RotationPolicyListener() {
                @Override
                public void onChange() {
                    mModel.onRotationLockChanged();
                }
            };

    public QuickSettings(Context context, QuickSettingsContainerView container) {
        mDisplayManager = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
        mContext = context;
        mContainerView = container;
        mModel = new QuickSettingsModel(context);
        mWifiDisplayStatus = new WifiDisplayStatus();
        wifiManager = (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);
        locationManager = (LocationManager) mContext.getSystemService(Context.LOCATION_SERVICE);
        mBluetoothState = new QuickSettingsModel.BluetoothState();
        mHandler = new Handler();

        Resources r = mContext.getResources();
        mBatteryLevels = (LevelListDrawable) r.getDrawable(R.drawable.qs_sys_battery);
        mChargingBatteryLevels =
                (LevelListDrawable) r.getDrawable(R.drawable.qs_sys_battery_charging);
        mBrightnessDialogLongTimeout =
                r.getInteger(R.integer.quick_settings_brightness_dialog_long_timeout);
        mBrightnessDialogShortTimeout =
                r.getInteger(R.integer.quick_settings_brightness_dialog_short_timeout);

        IntentFilter filter = new IntentFilter();
        filter.addAction(DisplayManager.ACTION_WIFI_DISPLAY_STATUS_CHANGED);
        filter.addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED);
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        filter.addAction(Intent.ACTION_USER_SWITCHED);
        mContext.registerReceiver(mReceiver, filter);

        IntentFilter profileFilter = new IntentFilter();
        profileFilter.addAction(ContactsContract.Intents.ACTION_PROFILE_CHANGED);
        profileFilter.addAction(Intent.ACTION_USER_INFO_CHANGED);
        mContext.registerReceiverAsUser(mProfileReceiver, UserHandle.ALL, profileFilter,
                null, null);

        new SettingsObserver(new Handler()).observe();
    }

    void setBar(PanelBar bar) {
        mBar = bar;
    }

    public void setService(PhoneStatusBar phoneStatusBar) {
        mStatusBarService = phoneStatusBar;
    }

    public PhoneStatusBar getService() {
        return mStatusBarService;
    }

    public void setImeWindowStatus(boolean visible) {
        mModel.onImeWindowStatusChanged(visible);
    }

    void setup(NetworkController networkController, BluetoothController bluetoothController,
            BatteryController batteryController, LocationController locationController) {
        mBluetoothController = bluetoothController;

        setupQuickSettings();
        updateWifiDisplayStatus();
        updateResources();

        if (getCustomUserTiles().contains(SIGNAL_TOGGLE))
            networkController.addNetworkSignalChangedCallback(mModel);
        if (getCustomUserTiles().contains(BLUETOOTH_TOGGLE))
            bluetoothController.addStateChangedCallback(mModel);
        if (getCustomUserTiles().contains(BATTERY_TOGGLE))
            batteryController.addStateChangedCallback(mModel);
        if (getCustomUserTiles().contains(GPS_TOGGLE))
            locationController.addStateChangedCallback(mModel);
        if (getCustomUserTiles().contains(ROTATE_TOGGLE))
            RotationPolicy.registerRotationPolicyListener(mContext, mRotationPolicyListener,
                    UserHandle.USER_ALL);
    }

    private void queryForUserInformation() {
        Context currentUserContext = null;
        UserInfo userInfo = null;
        try {
            userInfo = ActivityManagerNative.getDefault().getCurrentUser();
            currentUserContext = mContext.createPackageContextAsUser("android", 0,
                    new UserHandle(userInfo.id));
        } catch (NameNotFoundException e) {
            Log.e(TAG, "Couldn't create user context", e);
            throw new RuntimeException(e);
        } catch (RemoteException e) {
            Log.e(TAG, "Couldn't get user info", e);
        }
        final int userId = userInfo.id;
        final String userName = userInfo.name;

        final Context context = currentUserContext;
        mUserInfoTask = new AsyncTask<Void, Void, Pair<String, Drawable>>() {
            @Override
            protected Pair<String, Drawable> doInBackground(Void... params) {
                final UserManager um =
                        (UserManager) mContext.getSystemService(Context.USER_SERVICE);

                // Fall back to the UserManager nickname if we can't read the
                // name from the local
                // profile below.
                String name = userName;
                Drawable avatar = null;
                Bitmap rawAvatar = um.getUserIcon(userId);
                if (rawAvatar != null) {
                    avatar = new BitmapDrawable(mContext.getResources(), rawAvatar);
                } else {
                    avatar = mContext.getResources().getDrawable(R.drawable.ic_qs_default_user);
                }

                // If it's a single-user device, get the profile name, since the
                // nickname is not
                // usually valid
                if (um.getUsers().size() <= 1) {
                    // Try and read the display name from the local profile
                    final Cursor cursor = context.getContentResolver().query(
                            Profile.CONTENT_URI, new String[] {
                                    Phone._ID, Phone.DISPLAY_NAME
                            },
                            null, null, null);
                    if (cursor != null) {
                        try {
                            if (cursor.moveToFirst()) {
                                name = cursor.getString(cursor.getColumnIndex(Phone.DISPLAY_NAME));
                            }
                        } finally {
                            cursor.close();
                        }
                    }
                }
                return new Pair<String, Drawable>(name, avatar);
            }

            @Override
            protected void onPostExecute(Pair<String, Drawable> result) {
                super.onPostExecute(result);
                mModel.setUserTileInfo(result.first, result.second);
                mUserInfoTask = null;
            }
        };
        mUserInfoTask.execute();
    }

    private void setupQuickSettings() {
        // Setup the tiles that we are going to be showing (including the
        // temporary ones)
        LayoutInflater inflater = LayoutInflater.from(mContext);

        addUserTiles(mContainerView, inflater);
        addTemporaryTiles(mContainerView, inflater);

        queryForUserInformation();
        mTilesSetUp = true;
    }

    private void startSettingsActivity(String action) {
        Intent intent = new Intent(action);
        startSettingsActivity(intent);
    }

    private void startSettingsActivity(Intent intent) {
        startSettingsActivity(intent, true);
    }

    private void startSettingsActivity(Intent intent, boolean onlyProvisioned) {
        if (onlyProvisioned && !getService().isDeviceProvisioned())
            return;
        try {
            // Dismiss the lock screen when Settings starts.
            ActivityManagerNative.getDefault().dismissKeyguardOnNextActivity();
        } catch (RemoteException e) {
        }
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        mContext.startActivityAsUser(intent, new UserHandle(UserHandle.USER_CURRENT));
        getService().animateCollapsePanels();
    }

    private QuickSettingsTileView getTile(int tile, ViewGroup parent, LayoutInflater inflater) {
        QuickSettingsTileView quick = null;
        switch (tile) {
            case USER_TILE:
                quick = (QuickSettingsTileView)
                        inflater.inflate(R.layout.quick_settings_tile, parent, false);
                quick.setContent(R.layout.quick_settings_tile_user, inflater);
                quick.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        mBar.collapseAllPanels(true);
                        final UserManager um =
                                (UserManager) mContext.getSystemService(Context.USER_SERVICE);
                        if (um.getUsers(true).size() > 1) {
                            try {
                                WindowManagerGlobal.getWindowManagerService().lockNow(
                                        LockPatternUtils.USER_SWITCH_LOCK_OPTIONS);
                            } catch (RemoteException e) {
                                Log.e(TAG, "Couldn't show user switcher", e);
                            }
                        } else {
                            Intent intent = ContactsContract.QuickContact.composeQuickContactsIntent(
                                    mContext, v, ContactsContract.Profile.CONTENT_URI,
                                    ContactsContract.QuickContact.MODE_LARGE, null);
                            mContext.startActivityAsUser(intent, new UserHandle(UserHandle.USER_CURRENT));
                        }
                    }
                });
                mModel.addUserTile(quick, new QuickSettingsModel.RefreshCallback() {
                    @Override
                    public void refreshView(QuickSettingsTileView view, State state) {
                        UserState us = (UserState) state;
                        ImageView iv = (ImageView) view.findViewById(R.id.user_imageview);
                        TextView tv = (TextView) view.findViewById(R.id.user_textview);
                        tv.setText(state.label);
                        iv.setImageDrawable(us.avatar);
                        view.setContentDescription(mContext.getString(
                                R.string.accessibility_quick_settings_user, state.label));
                    }
                });
                mDynamicSpannedTiles.add(quick);
                break;
            case CLOCK_TILE:
                quick = (QuickSettingsTileView)
                        inflater.inflate(R.layout.quick_settings_tile, parent, false);
                quick.setContent(R.layout.quick_settings_tile_time, inflater);
                quick.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        // Quick. Clock. Quick. Clock. Quick. Clock.
                        startSettingsActivity(Intent.ACTION_QUICK_CLOCK);
                    }
                });
                mModel.addTimeTile(quick, new QuickSettingsModel.RefreshCallback() {
                    @Override
                    public void refreshView(QuickSettingsTileView view, State alarmState) {
                    }
                });
                mDynamicSpannedTiles.add(quick);
                break;
            case SIGNAL_TILE:
                if (mModel.deviceSupportsTelephony()) {
                    // Mobile Network state
                    quick = (QuickSettingsTileView)
                            inflater.inflate(R.layout.quick_settings_tile, parent, false);
                    quick.setContent(R.layout.quick_settings_tile_rssi, inflater);
                    quick.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            Intent intent = new Intent();
                            intent.setComponent(new ComponentName(
                                    "com.android.settings",
                                    "com.android.settings.Settings$DataUsageSummaryActivity"));
                            startSettingsActivity(intent);
                        }
                    });
                    mModel.addRSSITile(quick, new QuickSettingsModel.RefreshCallback() {
                        @Override
                        public void refreshView(QuickSettingsTileView view, State state) {
                            RSSIState rssiState = (RSSIState) state;
                            ImageView iv = (ImageView) view.findViewById(R.id.rssi_image);
                            ImageView iov = (ImageView) view.findViewById(R.id.rssi_overlay_image);
                            TextView tv = (TextView) view.findViewById(R.id.rssi_textview);
                            iv.setImageResource(rssiState.signalIconId);

                            if (rssiState.dataTypeIconId > 0) {
                                iov.setImageResource(rssiState.dataTypeIconId);
                            } else {
                                iov.setImageDrawable(null);
                            }
                            tv.setText(state.label);
                            view.setContentDescription(mContext.getResources().getString(
                                    R.string.accessibility_quick_settings_mobile,
                                    rssiState.signalContentDescription, rssiState.dataContentDescription,
                                    state.label));
                        }
                    });
                }
                break;
            case BRIGHTNESS_TILE:
                quick = (QuickSettingsTileView)
                        inflater.inflate(R.layout.quick_settings_tile, parent, false);
                quick.setContent(R.layout.quick_settings_tile_brightness, inflater);
                quick.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        mBar.collapseAllPanels(true);
                        showBrightnessDialog();
                    }
                });
                mModel.addBrightnessTile(quick, new QuickSettingsModel.RefreshCallback() {
                    @Override
                    public void refreshView(QuickSettingsTileView view, State state) {
                        TextView tv = (TextView) view.findViewById(R.id.brightness_textview);
                        tv.setCompoundDrawablesWithIntrinsicBounds(0, state.iconId, 0, 0);
                        tv.setText(state.label);
                        dismissBrightnessDialog(mBrightnessDialogShortTimeout);
                    }
                });
                mDynamicSpannedTiles.add(quick);
                break;
            case SETTINGS_TILE:
                quick = (QuickSettingsTileView)
                        inflater.inflate(R.layout.quick_settings_tile, parent, false);
                quick.setContent(R.layout.quick_settings_tile_settings, inflater);
                quick.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startSettingsActivity(android.provider.Settings.ACTION_SETTINGS);
                    }
                });
                quick.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View v) {
                        Intent intent = new Intent("android.intent.action.MAIN");
                        intent.setComponent(ComponentName.unflattenFromString("com.aokp.romcontrol/.ROMControlActivity"));
                        intent.addCategory("android.intent.category.LAUNCHER");
                        startSettingsActivity(intent);
                        return true;
                    }
                });
                mModel.addSettingsTile(quick, new QuickSettingsModel.RefreshCallback() {
                    @Override
                    public void refreshView(QuickSettingsTileView view, State state) {
                        TextView tv = (TextView) view.findViewById(R.id.settings_tileview);
                        tv.setText(state.label);
                    }
                });
                mDynamicSpannedTiles.add(quick);
                break;
            case WIFI_TILE:
                quick = (QuickSettingsTileView)
                        inflater.inflate(R.layout.quick_settings_tile, parent, false);
                quick.setContent(R.layout.quick_settings_tile_wifi, inflater);
                quick.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (wifiManager.isWifiEnabled()) {
                            wifiManager.setWifiEnabled(false);
                        } else {
                            wifiManager.setWifiEnabled(true);
                        }
                    }
                });
                quick.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View v) {
                        startSettingsActivity(android.provider.Settings.ACTION_WIFI_SETTINGS);
                        return true;
                    }
                });
                mModel.addWifiTile(quick, new QuickSettingsModel.RefreshCallback() {
                    @Override
                    public void refreshView(QuickSettingsTileView view, State state) {
                        WifiState wifiState = (WifiState) state;
                        TextView tv = (TextView) view.findViewById(R.id.wifi_textview);
                        tv.setCompoundDrawablesWithIntrinsicBounds(0, wifiState.iconId, 0, 0);
                        tv.setText(wifiState.label);
                        view.setContentDescription(mContext.getString(
                                R.string.accessibility_quick_settings_wifi,
                                wifiState.signalContentDescription,
                                (wifiState.connected) ? wifiState.label : ""));
                    }
                });
                break;
            case ROTATE_TILE:
                quick = (QuickSettingsTileView)
                        inflater.inflate(R.layout.quick_settings_tile, parent, false);
                quick.setContent(R.layout.quick_settings_tile_rotation_lock, inflater);
                quick.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        boolean locked = RotationPolicy.isRotationLocked(mContext);
                        RotationPolicy.setRotationLock(mContext, !locked);
                    }
                });
                mModel.addRotationLockTile(quick, new QuickSettingsModel.RefreshCallback() {
                    @Override
                    public void refreshView(QuickSettingsTileView view, State state) {
                        TextView tv = (TextView) view.findViewById(R.id.rotation_lock_textview);
                        tv.setCompoundDrawablesWithIntrinsicBounds(0, state.iconId, 0, 0);
                        tv.setText(state.label);
                    }
                });
                break;
            case BATTERY_TILE:
                quick = (QuickSettingsTileView)
                        inflater.inflate(R.layout.quick_settings_tile, parent, false);
                quick.setContent(R.layout.quick_settings_tile_battery, inflater);
                quick.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startSettingsActivity(Intent.ACTION_POWER_USAGE_SUMMARY);
                    }
                });
                mModel.addBatteryTile(quick, new QuickSettingsModel.RefreshCallback() {
                    @Override
                    public void refreshView(QuickSettingsTileView view, State state) {
                        QuickSettingsModel.BatteryState batteryState =
                                (QuickSettingsModel.BatteryState) state;
                        TextView tv = (TextView) view.findViewById(R.id.battery_textview);
                        ImageView iv = (ImageView) view.findViewById(R.id.battery_image);
                        Drawable d = batteryState.pluggedIn
                                ? mChargingBatteryLevels
                                : mBatteryLevels;
                        String t;
                        if (batteryState.batteryLevel == 100) {
                            t = mContext.getString(R.string.quick_settings_battery_charged_label);
                        } else {
                            t = batteryState.pluggedIn
                                    ? mContext.getString(R.string.quick_settings_battery_charging_label,
                                            batteryState.batteryLevel)
                                    : mContext.getString(R.string.status_bar_settings_battery_meter_format,
                                            batteryState.batteryLevel);
                        }
                        iv.setImageDrawable(d);
                        iv.setImageLevel(batteryState.batteryLevel);
                        tv.setText(t);
                        view.setContentDescription(
                                mContext.getString(R.string.accessibility_quick_settings_battery, t));
                    }
                });
                break;
            case AIRPLANE_TILE:
                quick = (QuickSettingsTileView)
                        inflater.inflate(R.layout.quick_settings_tile, parent, false);
                quick.setContent(R.layout.quick_settings_tile_airplane, inflater);
                mModel.addAirplaneModeTile(quick, new QuickSettingsModel.RefreshCallback() {
                    @Override
                    public void refreshView(QuickSettingsTileView view, State state) {
                        TextView tv = (TextView) view.findViewById(R.id.airplane_mode_textview);
                        tv.setCompoundDrawablesWithIntrinsicBounds(0, state.iconId, 0, 0);

                        String airplaneState = mContext.getString(
                                (state.enabled) ? R.string.accessibility_desc_on
                                        : R.string.accessibility_desc_off);
                        view.setContentDescription(
                                mContext.getString(R.string.accessibility_quick_settings_airplane, airplaneState));
                        tv.setText(state.label);
                    }
                });
                break;
            case BLUETOOTH_TILE:
                quick = (QuickSettingsTileView)
                        inflater.inflate(R.layout.quick_settings_tile, parent, false);
                quick.setContent(R.layout.quick_settings_tile_bluetooth, inflater);
                quick.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        final BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
                        if (adapter.isEnabled()) {
                            adapter.disable();
                        } else {
                            adapter.enable();
                        }
                    }
                });
                quick.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View v) {
                        startSettingsActivity(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS);
                        return true;
                    }
                });
                mModel.addBluetoothTile(quick, new QuickSettingsModel.RefreshCallback() {
                    @Override
                    public void refreshView(QuickSettingsTileView view, State state) {
                        BluetoothState bluetoothState = (BluetoothState) state;
                        TextView tv = (TextView) view.findViewById(R.id.bluetooth_textview);
                        tv.setCompoundDrawablesWithIntrinsicBounds(0, state.iconId, 0, 0);

                        Resources r = mContext.getResources();
                        String label = state.label;
                        /*
                         * //TODO: Show connected bluetooth device label
                         * Set<BluetoothDevice> btDevices =
                         * mBluetoothController.getBondedBluetoothDevices(); if
                         * (btDevices.size() == 1) { // Show the name of the
                         * bluetooth device you are connected to label =
                         * btDevices.iterator().next().getName(); } else if
                         * (btDevices.size() > 1) { // Show a generic label
                         * about the number of bluetooth devices label =
                         * r.getString(R.string
                         * .quick_settings_bluetooth_multiple_devices_label,
                         * btDevices.size()); }
                         */
                        view.setContentDescription(mContext.getString(
                                R.string.accessibility_quick_settings_bluetooth,
                                bluetoothState.stateContentDescription));
                        tv.setText(label);
                    }
                });
                break;
            case GPS_TILE:
                quick = (QuickSettingsTileView)
                        inflater.inflate(R.layout.quick_settings_tile, parent, false);
                quick.setContent(R.layout.quick_settings_tile_location, inflater);
                quick.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        boolean gpsEnabled = Settings.Secure.isLocationProviderEnabled(
                                mContext.getContentResolver(), LocationManager.GPS_PROVIDER);
                        Settings.Secure.setLocationProviderEnabled(mContext.getContentResolver(),
                                LocationManager.GPS_PROVIDER, gpsEnabled ? false : true);
                        TextView tv = (TextView) v.findViewById(R.id.location_textview);
                        tv.setText(gpsEnabled ? strGPSoff : strGPSon);
                    }
                });
                quick.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View v) {
                        startSettingsActivity(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                        return true;
                    }
                });
                mModel.addLocationTile(quick, new QuickSettingsModel.RefreshCallback() {
                    @Override
                    public void refreshView(QuickSettingsTileView view, State state) {
                        boolean gpsEnabled = Settings.Secure.isLocationProviderEnabled(
                                mContext.getContentResolver(), LocationManager.GPS_PROVIDER);
                        TextView tv = (TextView) view.findViewById(R.id.location_textview);
                        String newString = state.label;
                        if ((newString == null) || (newString.equals(""))) {
                            tv.setText(gpsEnabled ? strGPSon : strGPSoff);
                        } else {
                            tv.setText(state.label);
                        }
                    }
                });
                break;
            case IME_TILE:
                quick = (QuickSettingsTileView)
                        inflater.inflate(R.layout.quick_settings_tile, parent, false);
                quick.setContent(R.layout.quick_settings_tile_ime, inflater);
                quick.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        try {
                            mBar.collapseAllPanels(true);
                            Intent intent = new Intent(Settings.ACTION_SHOW_INPUT_METHOD_PICKER);
                            PendingIntent pendingIntent = PendingIntent.getBroadcast(mContext, 0, intent, 0);
                            pendingIntent.send();
                        } catch (Exception e) {
                        }
                    }
                });
                mModel.addImeTile(quick, new QuickSettingsModel.RefreshCallback() {
                    @Override
                    public void refreshView(QuickSettingsTileView view, State state) {
                        TextView tv = (TextView) view.findViewById(R.id.ime_textview);
                        if (state.label != null) {
                            tv.setText(state.label);
                        }
                    }
                });
                break;
        }
        return quick;
    }

    protected ArrayList<String> getCustomUserTiles() {
        ArrayList<String> tiles = new ArrayList<String>();

        if (userToggles == null)
            return getDefaultTiles();

        String[] splitter = userToggles.split("\\" + TOGGLE_PIPE);
        for (String toggle : splitter) {
            tiles.add(toggle);
        }

        return tiles;
    }

    private ArrayList<String> getDefaultTiles() {
        ArrayList<String> tiles = new ArrayList<String>();
        tiles.add(USER_TOGGLE);
        tiles.add(BRIGHTNESS_TOGGLE);
        tiles.add(SETTINGS_TOGGLE);
        tiles.add(WIFI_TOGGLE);
        if (mModel.deviceSupportsTelephony()) {
            tiles.add(SIGNAL_TOGGLE);
        }
        if (mContext.getResources().getBoolean(R.bool.quick_settings_show_rotation_lock)) {
            tiles.add(ROTATE_TOGGLE);
        }
        tiles.add(BATTERY_TOGGLE);
        tiles.add(AIRPLANE_TOGGLE);
        if (mModel.deviceSupportsBluetooth()) {
            tiles.add(BLUETOOTH_TOGGLE);
        }
        return tiles;
    }

    private void addUserTiles(ViewGroup parent, LayoutInflater inflater) {
        if (parent.getChildCount() > 0)
            parent.removeAllViews();
        toggles = getCustomUserTiles();
        if (mDynamicSpannedTiles.size() > 0)
            mDynamicSpannedTiles.clear();

        if (!toggles.get(0).equals("")) {
            for (String toggle : toggles) {
                parent.addView(getTile(getToggleMap().get(toggle), parent, inflater));
            }
        }
    }

    private void addTemporaryTiles(final ViewGroup parent, final LayoutInflater inflater) {
        // Alarm tile
        QuickSettingsTileView alarmTile = (QuickSettingsTileView)
                inflater.inflate(R.layout.quick_settings_tile, parent, false);
        alarmTile.setContent(R.layout.quick_settings_tile_alarm, inflater);
        alarmTile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // TODO: Jump into the alarm application
                Intent intent = new Intent();
                intent.setComponent(new ComponentName(
                        "com.google.android.deskclock",
                        "com.android.deskclock.AlarmClock"));
                startSettingsActivity(intent);
            }
        });
        mModel.addAlarmTile(alarmTile, new QuickSettingsModel.RefreshCallback() {
            @Override
            public void refreshView(QuickSettingsTileView view, State alarmState) {
                TextView tv = (TextView) view.findViewById(R.id.alarm_textview);
                tv.setText(alarmState.label);
                view.setVisibility(alarmState.enabled ? View.VISIBLE : View.GONE);
                view.setContentDescription(mContext.getString(
                        R.string.accessibility_quick_settings_alarm, alarmState.label));
            }
        });
        parent.addView(alarmTile);

        // Wifi Display
        QuickSettingsTileView wifiDisplayTile = (QuickSettingsTileView)
                inflater.inflate(R.layout.quick_settings_tile, parent, false);
        wifiDisplayTile.setContent(R.layout.quick_settings_tile_wifi_display, inflater);
        wifiDisplayTile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startSettingsActivity(android.provider.Settings.ACTION_WIFI_DISPLAY_SETTINGS);
            }
        });
        mModel.addWifiDisplayTile(wifiDisplayTile, new QuickSettingsModel.RefreshCallback() {
            @Override
            public void refreshView(QuickSettingsTileView view, State state) {
                TextView tv = (TextView) view.findViewById(R.id.wifi_display_textview);
                tv.setText(state.label);
                tv.setCompoundDrawablesWithIntrinsicBounds(0, state.iconId, 0, 0);
                view.setVisibility(state.enabled ? View.VISIBLE : View.GONE);
            }
        });
        parent.addView(wifiDisplayTile);

        // Bug reports
        QuickSettingsTileView bugreportTile = (QuickSettingsTileView)
                inflater.inflate(R.layout.quick_settings_tile, parent, false);
        bugreportTile.setContent(R.layout.quick_settings_tile_bugreport, inflater);
        bugreportTile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mBar.collapseAllPanels(true);
                showBugreportDialog();
            }
        });
        mModel.addBugreportTile(bugreportTile, new QuickSettingsModel.RefreshCallback() {
            @Override
            public void refreshView(QuickSettingsTileView view, State state) {
                view.setVisibility(state.enabled ? View.VISIBLE : View.GONE);
            }
        });
        parent.addView(bugreportTile);
        /*
         * QuickSettingsTileView mediaTile = (QuickSettingsTileView)
         * inflater.inflate(R.layout.quick_settings_tile, parent, false);
         * mediaTile.setContent(R.layout.quick_settings_tile_media, inflater);
         * parent.addView(mediaTile); QuickSettingsTileView imeTile =
         * (QuickSettingsTileView)
         * inflater.inflate(R.layout.quick_settings_tile, parent, false);
         * imeTile.setContent(R.layout.quick_settings_tile_ime, inflater);
         * imeTile.setOnClickListener(new View.OnClickListener() {
         * @Override public void onClick(View v) { parent.removeViewAt(0); } });
         * parent.addView(imeTile);
         */
    }

    void updateResources() {
        Resources r = mContext.getResources();

        // Update the model
        mModel.updateResources(getCustomUserTiles());

        // Update the User, Time, and Settings tiles spans, and reset everything
        // else
        int span = r.getInteger(R.integer.quick_settings_user_time_settings_tile_span);
        for (QuickSettingsTileView v : mDynamicSpannedTiles) {
            v.setColumnSpan(span);
        }
        ((QuickSettingsContainerView) mContainerView).updateResources();
        mContainerView.requestLayout();

        // Reset the dialog
        boolean isBrightnessDialogVisible = false;
        if (mBrightnessDialog != null) {
            removeAllBrightnessDialogCallbacks();

            isBrightnessDialogVisible = mBrightnessDialog.isShowing();
            mBrightnessDialog.dismiss();
        }
        mBrightnessDialog = null;
        if (isBrightnessDialogVisible) {
            showBrightnessDialog();
        }
    }

    private void removeAllBrightnessDialogCallbacks() {
        mHandler.removeCallbacks(mDismissBrightnessDialogRunnable);
    }

    private Runnable mDismissBrightnessDialogRunnable = new Runnable() {
        public void run() {
            if (mBrightnessDialog != null && mBrightnessDialog.isShowing()) {
                mBrightnessDialog.dismiss();
            }
            removeAllBrightnessDialogCallbacks();
        };
    };

    private void showBrightnessDialog() {
        if (mBrightnessDialog == null) {
            mBrightnessDialog = new Dialog(mContext);
            mBrightnessDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
            mBrightnessDialog.setContentView(R.layout.quick_settings_brightness_dialog);
            mBrightnessDialog.setCanceledOnTouchOutside(true);

            mBrightnessController = new BrightnessController(mContext,
                    (ImageView) mBrightnessDialog.findViewById(R.id.brightness_icon),
                    (ToggleSlider) mBrightnessDialog.findViewById(R.id.brightness_slider));
            mBrightnessController.addStateChangedCallback(mModel);
            mBrightnessDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                @Override
                public void onDismiss(DialogInterface dialog) {
                    mBrightnessController = null;
                }
            });

            mBrightnessDialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
            mBrightnessDialog.getWindow().getAttributes().privateFlags |=
                    WindowManager.LayoutParams.PRIVATE_FLAG_SHOW_FOR_ALL_USERS;
            mBrightnessDialog.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }
        if (!mBrightnessDialog.isShowing()) {
            try {
                WindowManagerGlobal.getWindowManagerService().dismissKeyguard();
            } catch (RemoteException e) {
            }
            mBrightnessDialog.show();
            dismissBrightnessDialog(mBrightnessDialogLongTimeout);
        }
    }

    private void dismissBrightnessDialog(int timeout) {
        removeAllBrightnessDialogCallbacks();
        if (mBrightnessDialog != null) {
            mHandler.postDelayed(mDismissBrightnessDialogRunnable, timeout);
        }
    }

    private void showBugreportDialog() {
        final AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
        builder.setPositiveButton(com.android.internal.R.string.report, new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (which == DialogInterface.BUTTON_POSITIVE) {
                    // Add a little delay before executing, to give the
                    // dialog a chance to go away before it takes a
                    // screenshot.
                    mHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                ActivityManagerNative.getDefault()
                                        .requestBugReport();
                            } catch (RemoteException e) {
                            }
                        }
                    }, 500);
                }
            }
        });
        builder.setMessage(com.android.internal.R.string.bugreport_message);
        builder.setTitle(com.android.internal.R.string.bugreport_title);
        builder.setCancelable(true);
        final Dialog dialog = builder.create();
        dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
        try {
            WindowManagerGlobal.getWindowManagerService().dismissKeyguard();
        } catch (RemoteException e) {
        }
        dialog.show();
    }

    private void updateWifiDisplayStatus() {
        mWifiDisplayStatus = mDisplayManager.getWifiDisplayStatus();
        applyWifiDisplayStatus();
    }

    private void applyWifiDisplayStatus() {
        mModel.onWifiDisplayStateChanged(mWifiDisplayStatus);
    }

    private void applyBluetoothStatus() {
        mModel.onBluetoothStateChange(mBluetoothState);
    }

    void reloadUserInfo() {
        if (mUserInfoTask != null) {
            mUserInfoTask.cancel(false);
            mUserInfoTask = null;
        }
        if (mTilesSetUp) {
            queryForUserInformation();
        }
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (DisplayManager.ACTION_WIFI_DISPLAY_STATUS_CHANGED.equals(action)) {
                WifiDisplayStatus status = (WifiDisplayStatus) intent.getParcelableExtra(
                        DisplayManager.EXTRA_WIFI_DISPLAY_STATUS);
                mWifiDisplayStatus = status;
                applyWifiDisplayStatus();
            } else if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,
                        BluetoothAdapter.ERROR);
                mBluetoothState.enabled = (state == BluetoothAdapter.STATE_ON);
                applyBluetoothStatus();
            } else if (BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED.equals(action)) {
                int status = intent.getIntExtra(BluetoothAdapter.EXTRA_CONNECTION_STATE,
                        BluetoothAdapter.STATE_DISCONNECTED);
                mBluetoothState.connected = (status == BluetoothAdapter.STATE_CONNECTED);
                applyBluetoothStatus();
            } else if (Intent.ACTION_USER_SWITCHED.equals(action)) {
                reloadUserInfo();
            }
        }
    };

    private final BroadcastReceiver mProfileReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (ContactsContract.Intents.ACTION_PROFILE_CHANGED.equals(action) ||
                    Intent.ACTION_USER_INFO_CHANGED.equals(action)) {
                try {
                    final int userId = ActivityManagerNative.getDefault().getCurrentUser().id;
                    if (getSendingUserId() == userId) {
                        reloadUserInfo();
                    }
                } catch (RemoteException e) {
                    Log.e(TAG, "Couldn't get current user id for profile change", e);
                }
            }

        }
    };

    private void updateSettings() {
        ContentResolver resolver = mContext.getContentResolver();
        userToggles = Settings.System.getString(resolver, Settings.System.QUICK_TOGGLES);
        setupQuickSettings();
        updateWifiDisplayStatus();
        updateResources();
    }

    class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(Settings.System
                    .getUriFor(Settings.System.QUICK_TOGGLES),
                    false, this);
            updateSettings();
        }

        @Override
        public void onChange(boolean selfChange) {
            updateSettings();
        }
    }

}
