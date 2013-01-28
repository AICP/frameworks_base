
package com.android.systemui;

import android.app.ActivityManager;
import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Log;

import com.android.systemui.statusbar.NavigationBarView;
import com.android.systemui.statusbar.phone.PanelBar;

import java.util.List;

public class TransparencyManager {

    public static final float KEYGUARD_ALPHA = 0.44f;

    private static final String TAG = TransparencyManager.class.getSimpleName();

    NavigationBarView mNavbar;
    PanelBar mStatusbar;

    SomeInfo mNavbarInfo = new SomeInfo();
    SomeInfo mStatusbarInfo = new SomeInfo();

    final Context mContext;

    Handler mHandler = new Handler();

    boolean mIsHomeShowing;
    boolean mIsKeyguardShowing;

    private class SomeInfo {
        float keyguardAlpha;
        float homeAlpha;
        boolean tempDisable;
    }

    private final Runnable updateTransparencyRunnable = new Runnable() {
        @Override
        public void run() {
            doTransparentUpdate();
        }
    };

    public TransparencyManager(Context context) {
        mContext = context;

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Intent.ACTION_SCREEN_OFF);
        context.registerReceiver(new BroadcastReceiver() {

            @Override
            public void onReceive(Context context, Intent intent) {
                update();
            }
        }, intentFilter);

        SettingsObserver settingsObserver = new SettingsObserver(new Handler());
        settingsObserver.observe();
    }

    public void update() {
        mHandler.removeCallbacks(updateTransparencyRunnable);
        mHandler.postDelayed(updateTransparencyRunnable, 100);
    }

    public void setNavbar(NavigationBarView n) {
        mNavbar = n;
    }

    public void setStatusbar(PanelBar s) {
        mStatusbar = s;
    }

    public void setTempStatusbarState(boolean state) {
        mStatusbarInfo.tempDisable = state;
    }

    public void setTempNavbarState(boolean state) {
        mNavbarInfo.tempDisable = state;
    }

    private void doTransparentUpdate() {
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                mIsHomeShowing = isLauncherShowing();
                mIsKeyguardShowing = isKeyguardShowing();
                return null;
            }

            @Override
            protected void onPostExecute(Void v) {
                // TODO animate alpha~
                if (mNavbar != null) {
                    if (mNavbarInfo.tempDisable) {
                        mNavbar.setBackgroundAlpha(1);
                        mNavbarInfo.tempDisable = false;
                    } else if (mIsKeyguardShowing) {
                        mNavbar.setBackgroundAlpha(mNavbarInfo.keyguardAlpha);
                    } else if (mIsHomeShowing) {
                        mNavbar.setBackgroundAlpha(mNavbarInfo.homeAlpha);
                    } else {
                        mNavbar.setBackgroundAlpha(1);
                    }
                }
                if (mStatusbar != null) {
                    if (mStatusbarInfo.tempDisable) {
                        mStatusbar.setBackgroundAlpha(1);
                        mStatusbarInfo.tempDisable = false;
                    } else if (mIsKeyguardShowing) {
                        mStatusbar.setBackgroundAlpha(mStatusbarInfo.keyguardAlpha);
                    } else if (mIsHomeShowing) {
                        mStatusbar.setBackgroundAlpha(mStatusbarInfo.homeAlpha);
                    } else {
                        mStatusbar.setBackgroundAlpha(1);
                    }
                }
            }
        }.execute();
    }

    private boolean isLauncherShowing() {
        ActivityManager am = (ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE);
        final List<ActivityManager.RecentTaskInfo> recentTasks = am
                .getRecentTasksForUser(
                        1, ActivityManager.RECENT_WITH_EXCLUDED,
                        UserHandle.CURRENT.getIdentifier());
        if (recentTasks.size() > 0) {
            ActivityManager.RecentTaskInfo recentInfo = recentTasks.get(0);
            Intent intent = new Intent(recentInfo.baseIntent);
            if (recentInfo.origActivity != null) {
                intent.setComponent(recentInfo.origActivity);
            }
            if (isCurrentHomeActivity(intent.getComponent(), null)) {
                return true;
            }
        }
        return false;
    }

    private boolean isKeyguardShowing() {
        KeyguardManager km = (KeyguardManager) mContext.getSystemService(Context.KEYGUARD_SERVICE);
        if (km == null)
            return false;
        return km.isKeyguardLocked();
    }

    private boolean isCurrentHomeActivity(ComponentName component, ActivityInfo homeInfo) {
        if (homeInfo == null) {
            homeInfo = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                    .resolveActivityInfo(mContext.getPackageManager(), 0);
        }
        return homeInfo != null
                && homeInfo.packageName.equals(component.getPackageName())
                && homeInfo.name.equals(component.getClassName());
    }

    class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();

            resolver.registerContentObserver(
                    Settings.System.getUriFor(Settings.System.NAVIGATION_BAR_ALPHA_CONFIG), false,
                    this);
            resolver.registerContentObserver(
                    Settings.System.getUriFor(Settings.System.STATUS_BAR_ALPHA_CONFIG), false,
                    this);
            updateSettings();
        }

        @Override
        public void onChange(boolean selfChange) {
            updateSettings();
        }
    }

    protected void updateSettings() {
        ContentResolver resolver = mContext.getContentResolver();

        final float defaultAlpha = new Float(mContext.getResources().getInteger(
                R.integer.navigation_bar_transparency) / 255);
        String alphas[];
        String settingValue = Settings.System.getString(resolver,
                Settings.System.NAVIGATION_BAR_ALPHA_CONFIG);
        Log.e(TAG, "nav bar config: " + settingValue);
        if (settingValue == null) {
            mNavbarInfo.homeAlpha = defaultAlpha;
            mNavbarInfo.keyguardAlpha = KEYGUARD_ALPHA;
        } else {
            alphas = settingValue.split(";");
            if (alphas != null && alphas.length == 2) {
                mNavbarInfo.homeAlpha = Float.parseFloat(alphas[0]) / 255;
                mNavbarInfo.keyguardAlpha = Float.parseFloat(alphas[1]) / 255;
            }
        }

        settingValue = Settings.System.getString(resolver,
                Settings.System.STATUS_BAR_ALPHA_CONFIG);
        Log.e(TAG, "status bar config: " + settingValue);
        if (settingValue == null) {
            mStatusbarInfo.homeAlpha = defaultAlpha;
            mStatusbarInfo.keyguardAlpha = KEYGUARD_ALPHA;
        } else {
            alphas = settingValue.split(";");
            if (alphas != null && alphas.length == 2) {
                mStatusbarInfo.homeAlpha = Float.parseFloat(alphas[0]) / 255;
                mStatusbarInfo.keyguardAlpha = Float.parseFloat(alphas[1]) / 255;
            }
        }

        update();
    }
}
