/*
 * Copyright (C) 2015 The CyanogenMod Project
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

package com.android.systemui.qs.tiles;

import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.Network;
import android.net.Uri;
import android.os.UserHandle;
import android.provider.Settings;
import android.service.quicksettings.Tile;
import android.widget.Toast;

import com.android.internal.logging.MetricsLogger;
import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.systemui.Dependency;
import com.android.systemui.R;
import com.android.systemui.plugins.ActivityStarter;
import com.android.systemui.plugins.qs.QSTile.BooleanState;
import com.android.systemui.qs.QSHost;
import com.android.systemui.qs.tileimpl.QSTileImpl;
import com.android.systemui.statusbar.policy.KeyguardMonitor;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.List;

public class AdbOverNetworkTile extends QSTileImpl<BooleanState> {

    private boolean mListening;
    private final Icon mIcon = ResourceIcon.get(R.drawable.ic_qs_network_adb);

    private final KeyguardMonitor mKeyguardMonitor;
    private final KeyguardMonitorCallback mCallback = new KeyguardMonitorCallback();

    private final ConnectivityManager mConnectivityManager;

    private String mNetworkAddress;

    public AdbOverNetworkTile(QSHost host) {
        super(host);
        mKeyguardMonitor = Dependency.get(KeyguardMonitor.class);
        mConnectivityManager = mContext.getSystemService(ConnectivityManager.class);
    }

    @Override
    public BooleanState newTileState() {
        return new BooleanState();
    }

    @Override
    protected void handleClick() {
        if (mKeyguardMonitor.isSecure() && !mKeyguardMonitor.canSkipBouncer()) {
            Dependency.get(ActivityStarter.class)
                    .postQSRunnableDismissingKeyguard(this::toggleAction);
        } else {
            toggleAction();
        }
    }

    @Override
    public CharSequence getTileLabel() {
        return mContext.getString(R.string.quick_settings_network_adb_label);
    }

    @Override
    public Intent getLongClickIntent() {
        return new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS);
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        state.icon = mIcon;
        state.label = mContext.getString(R.string.quick_settings_network_adb_label);

        if (!isAdbEnabled()) {
            state.state = Tile.STATE_INACTIVE;
            return;
        }

        if (isAdbNetworkEnabled()) {
            state.value = true;
            state.secondaryLabel = mNetworkAddress != null ? mNetworkAddress
                    : mContext.getString(R.string.quick_settings_network_adb_no_network);
            state.state = Tile.STATE_ACTIVE;
        } else {
            state.secondaryLabel = null;
            state.value = false;
            state.state = canEnableAdbNetwork() ? Tile.STATE_INACTIVE : Tile.STATE_UNAVAILABLE;
        }
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.CUSTOM_QUICK_TILES;
    }

    private boolean isAdbEnabled() {
        return Settings.Global.getInt(mContext.getContentResolver(),
                Settings.Global.ADB_ENABLED, 0) > 0;
    }

    private boolean isAdbNetworkEnabled() {
        return Settings.Global.getInt(mContext.getContentResolver(),
                Settings.Global.AICP_ADB_PORT, 0) > 0;
    }

    private boolean canEnableAdbNetwork() {
        return isAdbEnabled() && isNetworkAvailable();
    }

    private boolean isNetworkAvailable() {
        return mNetworkAddress != null;
    }

    private void toggleAction() {
        final boolean active = getState().value;
        // Always allow toggle off if currently on.
        if (!active && !canEnableAdbNetwork()) {
            return;
        }

        Settings.Global.putInt(mContext.getContentResolver(),
                Settings.Global.AICP_ADB_PORT, active ? 0 : 5555);
    }

    private ContentObserver mObserver = new ContentObserver(mHandler) {
        @Override
        public void onChange(boolean selfChange, Uri uri) {
            refreshState();
        }
    };

    @Override
    public void handleSetListening(boolean listening) {
        if (mObserver == null) {
            return;
        }
        if (mListening != listening) {
            mListening = listening;
            if (listening) {
                mContext.getContentResolver().registerContentObserver(
                        Settings.Global.getUriFor(Settings.Global.AICP_ADB_PORT),
                        false, mObserver);
                mContext.getContentResolver().registerContentObserver(
                        Settings.Global.getUriFor(Settings.Global.ADB_ENABLED),
                        false, mObserver);
                mKeyguardMonitor.addCallback(mCallback);
                mConnectivityManager.registerDefaultNetworkCallback(mNetworkCallback);
            } else {
                mContext.getContentResolver().unregisterContentObserver(mObserver);
                mKeyguardMonitor.removeCallback(mCallback);
                mConnectivityManager.unregisterNetworkCallback(mNetworkCallback);
            }
        }
    }

    private class KeyguardMonitorCallback implements KeyguardMonitor.Callback {
        @Override
        public void onKeyguardShowingChanged() {
            refreshState();
        }
    }

    private ConnectivityManager.NetworkCallback mNetworkCallback =
            new ConnectivityManager.NetworkCallback() {
        @Override
        public void onAvailable(Network network) {
            List<LinkAddress> linkAddresses =
                    mConnectivityManager.getLinkProperties(network).getLinkAddresses();
            // Determine local network address.
            // Use first IPv4 address if available, otherwise use first IPv6.
            String ipv4 = null, ipv6 = null;
            for (LinkAddress la : linkAddresses) {
                final InetAddress addr = la.getAddress();
                if (ipv4 == null && addr instanceof Inet4Address) {
                    ipv4 = addr.getHostAddress();
                    break;
                } else if (ipv6 == null && addr instanceof Inet6Address) {
                    ipv6 = addr.getHostAddress();
                }
            }
            mNetworkAddress = ipv4 != null ? ipv4 : ipv6;
            refreshState();
        }

        @Override
        public void onLost(Network network) {
            mNetworkAddress = null;
            refreshState();
        }
    };
}
