/*
 * Copyright (C) 2022 Project Kaleidoscope
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

package ink.kaleidoscope.server;

import static android.os.Process.THREAD_PRIORITY_DEFAULT;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.RemoteException;
import android.provider.Settings;
import android.util.Log;

import com.android.internal.messages.nano.SystemMessageProto.SystemMessage;
import com.android.internal.R;
import com.android.server.ServiceThread;
import com.android.server.SystemService;

import java.lang.String;
import java.util.NoSuchElementException;

import ink.kaleidoscope.hardware.IOptimizedCharge;

import vendor.lineage.chgctrl.V1_0.IChargeControl;

public final class OptimizedChargeService extends SystemService {

    private static final String TAG = "OptimizedChargeService";
    private static final String SMNAME = "optimizedcharge";

    private final Context mContext;
    private final ContentResolver mResolver;
    private ServiceThread mWorker;
    private Handler mHandler;
    private SettingsObserver mSettingsObserver;
    private IChargeControl mChargeControl;
    private NotificationManager mNotificationManager;

    private float mLevel;
    private boolean mPlugged;
    private boolean mLastChargeEnabled;

    private int mCeiling;
    private int mFloor;
    private boolean mEnabled;

    private void updateAction() {
        Log.d(TAG, "pct: " + mLevel);
        Log.d(TAG, "plugged: " + mPlugged);
        Log.d(TAG, "enabled: " + mEnabled);
        Log.d(TAG, "ceiling: " + mCeiling);
        Log.d(TAG, "floor: " + mFloor);

        boolean chargeEnabled = getChargeEnabled();
        boolean failed = false;

        if (!mEnabled) {
            if (!chargeEnabled)
                failed = setChargeEnabled(true);
        } else {
            if (mLevel >= mCeiling) {
                mLastChargeEnabled = false;
                if (chargeEnabled)
                    failed = setChargeEnabled(false);
            } else if (mLevel < mFloor) {
                mLastChargeEnabled = true;
                if (!chargeEnabled)
                    failed = setChargeEnabled(true);
            } else if (mLevel >= mFloor && mLevel <= mCeiling &&
                    chargeEnabled != mLastChargeEnabled)
                failed = setChargeEnabled(mLastChargeEnabled);
        }

        if (failed)
            Log.e(TAG, "Failed when updating action");
    }

    private boolean getChargeEnabled() {
        try {
            return mChargeControl.getChargeEnabled();
        } catch (RemoteException e) {
            Log.e(TAG, "Unable to get charge status");
        }
        return true;
    }

    private boolean setChargeEnabled(boolean enabled) {
        boolean failed = true;
        try {
            failed = mChargeControl.setChargeEnabled(enabled);
        } catch (RemoteException e) {
            Log.e(TAG, "Unable to set charge status");
        }
        if (!failed) {
            if (!enabled) {
                mNotificationManager.notify(SystemMessage.NOTE_OPTIMIZED_CHARGE,
                        new Notification.Builder(mContext, SMNAME)
                                .setSmallIcon(R.drawable.ic_battery)
                                .setContentTitle(getUiContext().getString(
                                    R.string.optimized_charge_notification_title))
                                .setContentText(getUiContext().getString(
                                    R.string.optimized_charge_notification_text))
                                .setFlag(Notification.FLAG_NO_CLEAR, true)
                                .build());
            } else {
                mNotificationManager.cancel(SystemMessage.NOTE_OPTIMIZED_CHARGE);
            }
        }
        return failed;
    }

    private void onBatteryChanged(Intent batteryStatus) {
        int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);

        float newLevel = level * 100 / (float)scale;
        boolean newPlugged = status != 0;

        boolean shouldUpdate = false;
        if (newLevel != mLevel) {
            mLevel = newLevel;
            shouldUpdate = true;
        }
        if (newPlugged != mPlugged) {
            if (newPlugged)
                mLastChargeEnabled = false;
            mPlugged = newPlugged;
            shouldUpdate = true;
        }

        if (shouldUpdate && mPlugged)
            updateAction();

        if (!mPlugged)
            setChargeEnabled(true);
    }

    private void updateParam(String name) {
        if (name == null) {
            updateParamInternal(Settings.System.OPTIMIZED_CHARGE_CEILING);
            updateParamInternal(Settings.System.OPTIMIZED_CHARGE_FLOOR);
            updateParamInternal(Settings.System.OPTIMIZED_CHARGE_ENABLED);
            updateAction();
            return;
        }
        updateParamInternal(name);
        updateAction();
    }

    private void updateParamInternal(String name) {
        if (name.equals(Settings.System.OPTIMIZED_CHARGE_CEILING))
            mCeiling = Settings.System.getInt(mResolver, name, 80);
        else if (name.equals(Settings.System.OPTIMIZED_CHARGE_FLOOR))
            mFloor = Settings.System.getInt(mResolver, name, 75);
        else if (name.equals(Settings.System.OPTIMIZED_CHARGE_ENABLED))
            mEnabled = Settings.System.getInt(mResolver, name, 0) == 1;
    }

    private final class SettingsObserver extends ContentObserver {
        public SettingsObserver(Handler handler) {
            super(handler);
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            String name = uri.getLastPathSegment();
            updateParam(name);
        }
    }

    @Override
    public void onStart() {
        try {
            mChargeControl = IChargeControl.getService(true);
        } catch (NoSuchElementException | RemoteException e) {
            Log.i(TAG, "Unable to get ChargeManager service");
        }

        publishBinderService(SMNAME, new IOptimizedCharge.Stub() {
            @Override
            public boolean isSupported() throws RemoteException {
                return mChargeControl != null;
            }
        });

        if (mChargeControl == null)
            return;

        mWorker = new ServiceThread(TAG, THREAD_PRIORITY_DEFAULT, false);
        mWorker.start();
        mHandler = new Handler(mWorker.getLooper());
        mSettingsObserver = new SettingsObserver(mHandler);

        mNotificationManager = mContext.getSystemService(NotificationManager.class);
        mNotificationManager.createNotificationChannel(new NotificationChannel(
                                SMNAME, getUiContext().getString(
                                R.string.optimized_charge_notification_channel_label),
                                NotificationManager.IMPORTANCE_MIN));
    }

    @Override
    public void onBootPhase(int phase) {
        if (mChargeControl == null)
            return;
        if (phase != PHASE_BOOT_COMPLETED)
            return;

        updateParam(null);

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_BATTERY_CHANGED);
        filter.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY);
        mContext.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                onBatteryChanged(intent);
            }
        }, filter, null, mHandler);

        mResolver.registerContentObserver(
            Settings.System.getUriFor(Settings.System.OPTIMIZED_CHARGE_ENABLED),
                false, mSettingsObserver);

        mResolver.registerContentObserver(
            Settings.System.getUriFor(Settings.System.OPTIMIZED_CHARGE_CEILING),
                false, mSettingsObserver);

        mResolver.registerContentObserver(
            Settings.System.getUriFor(Settings.System.OPTIMIZED_CHARGE_FLOOR),
                false, mSettingsObserver);
    }

    public OptimizedChargeService(Context context) {
        super(context);
        mContext = context;
        mResolver = context.getContentResolver();
    }
}
