/*
* Copyright (C) 2014-2018 The OmniROM Project
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package com.android.internal.util.aicp;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;

public class PackageUtils {

    public static boolean isPackageInstalled(Context context, String appUri) {
        try {
            PackageManager pm = context.getPackageManager();
            pm.getPackageInfo(appUri, PackageManager.GET_ACTIVITIES);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isPackageAvailable(Context context, String packageName) {
        final PackageManager pm = context.getPackageManager();
        try {
            pm.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES);
            int enabled = pm.getApplicationEnabledSetting(packageName);
            return enabled != PackageManager.COMPONENT_ENABLED_STATE_DISABLED &&
                enabled != PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER;
        } catch (NameNotFoundException e) {
            return false;
        }
    }

    public static <T> T notNullOrDefault(T value, T defValue) {
        return value == null ? defValue : value;
    }

    public static boolean isDozePackageAvailable(Context context) {
        return isPackageAvailable(context, PackageConstants.DOZE_PACKAGE_NAME) ||
            isPackageAvailable(context, PackageConstants.ONEPLUS_DOZE_PACKAGE_NAME) ||
            isPackageAvailable(context, PackageConstants.CUSTOM_DOZE_PACKAGE_NAME);
    }

    public static boolean isTouchGesturesPackageAvailable(Context context) {
        return isPackageAvailable(context, PackageConstants.TOUCHGESTURES_PACKAGE_NAME);
    }

    public static boolean isSystemApp(Context context, String pkgName) {
        return isSystemApp(context, null, pkgName);
    }

    public static boolean isSystemApp(Context context, Intent intent) {
        return isSystemApp(context, intent, null);
    }

    public static boolean isSystemApp(Context context, Intent intent, String pkgName) {
        PackageManager pm = context.getPackageManager();
        String packageName = null;
        // If the intent is not null, let's get the package name from the intent.
        if (intent != null) {
            ComponentName cn = intent.getComponent();
            if (cn == null) {
                ResolveInfo info = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY);
                if ((info != null) && (info.activityInfo != null)) {
                    packageName = info.activityInfo.packageName;
                }
            } else {
                packageName = cn.getPackageName();
            }
        }
        // Otherwise we have the package name passed from the method.
        else {
            packageName = pkgName;
        }
        // Check if the provided package is a system app.
        if (packageName != null) {
            try {
                PackageInfo info = pm.getPackageInfo(packageName, 0);
                return (info != null) && (info.applicationInfo != null) &&
                        ((info.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0);
            } catch (NameNotFoundException e) {
                return false;
            }
        } else {
            return false;
        }
    }
}
