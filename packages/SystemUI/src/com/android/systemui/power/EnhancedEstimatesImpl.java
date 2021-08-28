package com.android.systemui.power;

import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.BatteryStats;
import android.os.Bundle;
import android.os.SystemClock;
import android.os.UserManager;
import android.provider.Settings;
import android.util.KeyValueListParser;
import android.util.Log;

import com.android.internal.os.BatteryStatsHelper;
import com.android.internal.util.aicp.PackageUtils;

import com.android.settingslib.fuelgauge.Estimate;
import com.android.settingslib.utils.PowerUtil;
import com.android.systemui.power.EnhancedEstimates;

import java.time.Duration;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class EnhancedEstimatesImpl implements EnhancedEstimates {

    private Context mContext;
    private final KeyValueListParser mParser;

    BatteryStatsHelper mBatteryStatsHelper;
    UserManager mUserManager;

    @Inject
    public EnhancedEstimatesImpl(Context context) {
        mContext = context;
        mParser = new KeyValueListParser(',');
        mBatteryStatsHelper = new BatteryStatsHelper(context,
                true /* collectBatteryBroadcast */);
        mUserManager = (UserManager) context.getSystemService(Context.USER_SERVICE);
    }

    @Override
    public boolean isHybridNotificationEnabled() {
        try {
            if (!mContext.getPackageManager().getPackageInfo("com.google.android.apps.turbo", 512).applicationInfo.enabled) {
                return false;
            }
            updateFlags();
            return mParser.getBoolean("hybrid_enabled", true);
        } catch (PackageManager.NameNotFoundException ex) {
            //If turbo is not installed, switch to alternate method for estimates by returning true at the end instead of returning false below
            //return false;
        }
        return true;
    }

    @Override
    public Estimate getEstimate() {
        String packageName = "com.google.android.apps.turbo";
        boolean turboAvailable = PackageUtils.isPackageAvailable(mContext, packageName);
        boolean turboSystemApp = PackageUtils.isSystemApp(mContext, packageName);

        if (turboAvailable && turboSystemApp) {
            Uri build = new Uri.Builder().scheme("content").authority("com.google.android.apps.turbo.estimated_time_remaining").appendPath("time_remaining").build();
            try {
                Cursor query = mContext.getContentResolver().query(build, (String[])null, (String)null, (String[])null, (String)null);
                if (query != null) {
                    try {
                        if (query.moveToFirst()) {
                            int columnIndex = query.getColumnIndex("is_based_on_usage");
                            boolean b = true;
                            if (columnIndex != -1) {
                                b = (query.getInt(query.getColumnIndex("is_based_on_usage")) != 0 && b);
                            }
                            int columnIndex2 = query.getColumnIndex("average_battery_life");
                            long roundTimeToNearestThreshold = -1L;
                            if (columnIndex2 != -1) {
                                long long1 = query.getLong(columnIndex2);
                                roundTimeToNearestThreshold = roundTimeToNearestThreshold;
                                if (long1 != -1L) {
                                    long n = Duration.ofMinutes(15L).toMillis();
                                    if (Duration.ofMillis(long1).compareTo(Duration.ofDays(1L)) >= 0) {
                                        n = Duration.ofHours(1L).toMillis();
                                    }
                                    roundTimeToNearestThreshold = PowerUtil.roundTimeToNearestThreshold(long1, n);
                                }
                            }
                            Estimate estimate = new Estimate(query.getLong(query.getColumnIndex("battery_estimate")), b, roundTimeToNearestThreshold);
                            if (query != null) {
                                query.close();
                            }
                            return estimate;
                        }
                    }
                    finally {
                        if (query != null) {
                            try {
                                query.close();
                            } finally {
                                query = null;
                            }
                        }
                    }
                }
                if (query != null) {
                    query.close();
                }
            } catch (Exception exception) {
                Log.d("EnhancedEstimates", "Something went wrong when getting an estimate from Turbo", exception);
            }
        } else {
            try {
                mBatteryStatsHelper.create((Bundle) null);
                mBatteryStatsHelper.clearStats();
                mBatteryStatsHelper.refreshStats(BatteryStats.STATS_SINCE_CHARGED, mUserManager.getUserProfiles());
                BatteryStats stats = mBatteryStatsHelper.getStats();
                if (stats != null){
                    long remaining = stats.computeBatteryTimeRemaining(PowerUtil.convertMsToUs(
                            SystemClock.elapsedRealtime()));
                    if (remaining != -1){
                        return new Estimate(PowerUtil.convertUsToMs(remaining), false,
                                -1L);
                    }
                }
            } catch (Exception exception) {
                Log.d("EnhancedEstimates", "Something went wrong when getting an estimate from BatteryStatsHelper", exception);
            }
        }
        return new Estimate(-1L, false, -1L);
    }

    @Override
    public long getLowWarningThreshold() {
        updateFlags();
        return mParser.getLong("low_threshold", Duration.ofHours(3L).toMillis());
    }

    @Override
    public long getSevereWarningThreshold() {
        updateFlags();
        return mParser.getLong("severe_threshold", Duration.ofHours(1L).toMillis());
    }

    @Override
    public boolean getLowWarningEnabled() {
        updateFlags();
        return mParser.getBoolean("low_warning_enabled", false);
    }

    protected void updateFlags() {
        String string = Settings.Global.getString(mContext.getContentResolver(), "hybrid_sysui_battery_warning_flags");
        try {
            mParser.setString(string);
        } catch (IllegalArgumentException ex) {
            Log.e("EnhancedEstimates", "Bad hybrid sysui warning flags");
        }
    }
}
