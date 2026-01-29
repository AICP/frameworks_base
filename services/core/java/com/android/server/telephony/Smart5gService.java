/*
 * SPDX-FileCopyrightText: 2023 ArrowOS
 * SPDX-FileCopyrightText: 2025 The LibreMobileOS Foundation
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.server.telephony;

import static android.os.PowerManager.ACTION_POWER_SAVE_MODE_CHANGED;
import static android.provider.Settings.Global.MOBILE_DATA;
import static android.telephony.SubscriptionManager.INVALID_SUBSCRIPTION_ID;
import static android.telephony.TelephonyManager.ACTION_DEFAULT_DATA_SUBSCRIPTION_CHANGED;
import static android.telephony.TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_POWER;
import static android.telephony.TelephonyManager.NETWORK_TYPE_BITMASK_NR;
import static android.provider.Settings.System.SMART_5G;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerExecutor;
import android.os.Looper;
import android.os.PowerManager;
import android.os.UserHandle;
import android.provider.Settings;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.util.Slog;

import com.android.server.SystemService;

import java.util.Arrays;
import java.util.concurrent.Executor;

/**
 * This service is used to disable 5G for a subscription in the following scenarios:
 * - Battery saver mode is ON
 * - Mobile data is not active, eg. while using wifi
 * - Mobile data is not turned ON for the subscription
 * - Subscription is not the default data SIM
 *
 * Not smart enough yet, but we're getting there.
 */
public class Smart5gService extends SystemService {

    private static final String TAG = "Smart5gService";
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);
    private static final Uri SETTING_URI = Settings.System.getUriFor(SMART_5G);

    private final Context mContext;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final Executor mExecutor = new HandlerExecutor(mHandler);

    private TelephonyManager mTelephonyManager;
    private SubscriptionManager mSubManager;
    private ConnectivityManager mConnectivityManager;
    private PowerManager mPowerManager;

    private boolean mIsEnabled;
    private boolean mIsOnMobileData;
    private boolean mIsPowerSaveMode;
    private int[] mActiveSubIds = new int[0];
    private int mDefaultDataSubId = INVALID_SUBSCRIPTION_ID;

    private final ContentObserver mSettingObserver = new ContentObserver(mHandler) {
        @Override
        public void onChange(boolean selfChange, Uri uri) {
            dlog("SettingObserver: onChange uri=" + uri);
            if (SETTING_URI.equals(uri)) {
                boolean enabled = isEnabled();
                if (enabled != mIsEnabled) {
                    dlog("SettingObserver: enabled=" + enabled);
                    mIsEnabled = enabled;
                    if (!enabled) {
                        unregisterListeners();
                    } else {
                        registerListeners();
                    }
                    update();
                }
            } else {
                // mobile data setting changed
                update();
            }
        }
    };

    private final BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            dlog("received intent: " + action);
            if (action == null) return;
            switch (action) {
                case ACTION_POWER_SAVE_MODE_CHANGED:
                    final boolean on = mPowerManager.isPowerSaveMode();
                    if (on != mIsPowerSaveMode) {
                        mIsPowerSaveMode = on;
                        dlog("power save mode changed, new: " + on);
                        update();
                    }
                    break;
                case ACTION_DEFAULT_DATA_SUBSCRIPTION_CHANGED:
                    final int subId = mSubManager.getDefaultDataSubscriptionId();
                    if (subId != mDefaultDataSubId) {
                        mDefaultDataSubId = subId;
                        dlog("dds changed, new: " + subId);
                        update();
                    }
                    break;
                default:
                    Slog.e(TAG, "Unhandled intent: " + action);
            }
        }
    };

    private final ConnectivityManager.NetworkCallback mDefaultNetworkCallback =
            new ConnectivityManager.NetworkCallback() {
        Network mDefaultNetwork = null;

        @Override
        public void onAvailable(Network network) {
            dlog("NetworkCallback: onAvailable: " + network);
            mDefaultNetwork = network;
            refresh();
        }

        @Override
        public void onLost(Network network) {
            dlog("NetworkCallback: onLost: " + network);
            mDefaultNetwork = null;
            refresh();
        }

        private void refresh() {
            boolean isMobileDataActive = isMobileDataNetwork(mDefaultNetwork);
            if (isMobileDataActive != mIsOnMobileData) {
                dlog("NetworkCallback: isMobileDataActive:" + isMobileDataActive);
                mIsOnMobileData = isMobileDataActive;
                update();
            }
        }
    };

    private final SubscriptionManager.OnSubscriptionsChangedListener mSubListener =
            new SubscriptionManager.OnSubscriptionsChangedListener() {
        @Override
        public void onSubscriptionsChanged() {
            dlog("onSubscriptionsChanged");
            final int[] subs = mSubManager.getActiveSubscriptionIdList();
            if (!Arrays.equals(subs, mActiveSubIds)) {
                dlog("active subs changed, was: " + Arrays.toString(mActiveSubIds)
                        + ", now: " + Arrays.toString(subs));
                for (int subId : subs) {
                    dlog("registering content observer for subId " + subId);
                    mContext.getContentResolver().registerContentObserver(
                            Settings.Global.getUriFor(MOBILE_DATA + subId), false, mSettingObserver);
                }
                mActiveSubIds = subs;
                update();
            }
        }
    };

    public Smart5gService(Context context) {
        super(context);
        mContext = context;
    }

    @Override
    public void onStart() {
        Slog.v(TAG, "Starting Smart5gService");
        publishLocalService(Smart5gService.class, this);
    }

    @Override
    public void onBootPhase(int phase) {
        if (phase == SystemService.PHASE_SYSTEM_SERVICES_READY) {
            dlog("onBootPhase PHASE_SYSTEM_SERVICES_READY");
            mTelephonyManager = mContext.getSystemService(TelephonyManager.class);
            mSubManager = mContext.getSystemService(SubscriptionManager.class);
            mConnectivityManager = mContext.getSystemService(ConnectivityManager.class);
            mPowerManager = mContext.getSystemService(PowerManager.class);
        } else if (phase == SystemService.PHASE_BOOT_COMPLETED) {
            dlog("onBootPhase PHASE_BOOT_COMPLETED");
            mIsEnabled = isEnabled();
            mIsPowerSaveMode = mPowerManager.isPowerSaveMode();
            mDefaultDataSubId = mSubManager.getDefaultDataSubscriptionId();
            mIsOnMobileData = isMobileDataNetwork(mConnectivityManager.getActiveNetwork());
            mContext.getContentResolver().registerContentObserver(
                    Settings.System.getUriFor(SMART_5G), false, mSettingObserver);
            if (mIsEnabled) {
                registerListeners();
            }
        }
    }

    private boolean isEnabled() {
        return Settings.System.getIntForUser(mContext.getContentResolver(), SMART_5G, 1,
                UserHandle.USER_CURRENT) == 1;
    }

    private boolean isMobileDataNetwork(Network network) {
        // if we cant get a default network, assume mobile data to stop further switching 4g/5g.
        // otherwise, since switching can bring down the modem for a brief moment, this triggers
        // onLost() in the network callback and network=null which will again trigger a switch,
        // starting an infinite loop.
        if (network == null) return true;
        final NetworkCapabilities caps = mConnectivityManager.getNetworkCapabilities(network);
        return caps == null || caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR);
    }

    private void registerListeners() {
        final IntentFilter filter = new IntentFilter(ACTION_POWER_SAVE_MODE_CHANGED);
        filter.addAction(ACTION_DEFAULT_DATA_SUBSCRIPTION_CHANGED);
        mContext.registerReceiver(mIntentReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        mConnectivityManager.registerDefaultNetworkCallback(mDefaultNetworkCallback);
        mSubManager.addOnSubscriptionsChangedListener(mExecutor, mSubListener);
    }

    private void unregisterListeners() {
        mContext.unregisterReceiver(mIntentReceiver);
        mConnectivityManager.unregisterNetworkCallback(mDefaultNetworkCallback);
        mSubManager.removeOnSubscriptionsChangedListener(mSubListener);
    }

    private boolean isMobileDataEnabled(int subId) {
        return Settings.Global.getInt(mContext.getContentResolver(), MOBILE_DATA + subId, 1) == 1;
    }

    private synchronized void update() {
        if (mActiveSubIds == null || mActiveSubIds.length == 0) {
            dlog("update: return, no active subs!");
            return;
        }
        for (int subId : mActiveSubIds) {
            final TelephonyManager tm = mTelephonyManager.createForSubscriptionId(subId);
            final long supportedRat = tm.getSupportedRadioAccessFamily();
            if ((supportedRat & NETWORK_TYPE_BITMASK_NR) == 0) {
                dlog("subId " + subId + " does not support NR!");
                continue;
            }
            long allowedNetworkTypes = tm.getAllowedNetworkTypesForReason(
                    ALLOWED_NETWORK_TYPES_REASON_POWER);
            final boolean is5gAllowed = (allowedNetworkTypes & NETWORK_TYPE_BITMASK_NR) != 0;
            final boolean shouldDisable = shouldDisable5g(subId);
            dlog("update: subId=" + subId + " is5gAllowed=" + is5gAllowed + " shouldDisable="
                    + shouldDisable);
            if (shouldDisable && is5gAllowed) {
                allowedNetworkTypes &= ~NETWORK_TYPE_BITMASK_NR;
            } else if (!shouldDisable && !is5gAllowed) {
                allowedNetworkTypes |= NETWORK_TYPE_BITMASK_NR;
            } else {
                continue;
            }
            tm.setAllowedNetworkTypesForReason(ALLOWED_NETWORK_TYPES_REASON_POWER,
                    allowedNetworkTypes);
        }
    }

    private boolean shouldDisable5g(int subId) {
        if (!mIsEnabled) {
            dlog("shouldDisable5g: smart 5g is disabled");
            return false;
        }

        boolean isDataDisabled = !isMobileDataEnabled(subId);
        boolean isNotDataSub =
                (mDefaultDataSubId != INVALID_SUBSCRIPTION_ID && subId != mDefaultDataSubId);

        dlog("shouldDisable5g: subId=" + subId + " mIsPowerSaveMode=" + mIsPowerSaveMode
                + " mIsOnMobileData=" + mIsOnMobileData + " mDefaultDataSubId="
                + mDefaultDataSubId + " isDataDisabled=" + isDataDisabled);

        return mIsPowerSaveMode || !mIsOnMobileData || isDataDisabled || isNotDataSub;
    }

    private static void dlog(String msg) {
        if (DEBUG) Slog.d(TAG, msg);
    }
}
