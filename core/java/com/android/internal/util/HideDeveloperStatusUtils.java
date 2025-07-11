package com.android.internal.util;

import android.content.ContentResolver;
import android.content.Context;
import android.os.UserHandle;
import android.provider.Settings;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * @hide
 */
public class HideDeveloperStatusUtils {

    private Context mContext;
    private Set<String> mApps;
    private static final Set<String> SETTINGS_TO_HIDE = Set.of(
        Settings.Global.ADB_ENABLED,
        Settings.Global.ADB_WIFI_ENABLED,
        Settings.Global.DEVELOPMENT_SETTINGS_ENABLED
    );

    public HideDeveloperStatusUtils(Context context) {
        mContext = context;
        mApps = getApps(context.getContentResolver());
    }

    public static boolean shouldHideDevStatus(ContentResolver cr, String packageName, String name) {
        return getApps(cr).contains(packageName) && SETTINGS_TO_HIDE.contains(name);
    }

    private static Set<String> getApps(ContentResolver cr) {
        String apps = Settings.Secure.getString(cr, Settings.Secure.HIDE_DEVELOPER_STATUS);
        return apps != null ? new HashSet<>(Arrays.asList(apps.split(","))) : new HashSet<>();
    }

    public void addApp(String packageName) {
        mApps.add(packageName);
        Settings.Secure.putString(mContext.getContentResolver(),
                Settings.Secure.HIDE_DEVELOPER_STATUS, String.join(",", mApps));
    }

    public void removeApp(String packageName) {
        mApps.remove(packageName);
        Settings.Secure.putString(mContext.getContentResolver(),
                Settings.Secure.HIDE_DEVELOPER_STATUS, String.join(",", mApps));
    }

    public Set<String> getApps() {
        return mApps;
    }

    public void setApps(Set<String> apps) {
        mApps = apps;
    }
}
