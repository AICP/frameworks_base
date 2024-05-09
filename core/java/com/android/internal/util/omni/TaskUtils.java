/*
* Copyright (C) 2014-2022 The OmniROM Project
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
package com.android.internal.util.omni;

import android.app.Activity;
import android.app.ActivityOptions;
import android.app.ActivityManager;
import android.app.ActivityManagerNative;
import android.content.Context;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.RemoteException;

import com.android.internal.R;

import java.util.List;

public class TaskUtils {

    public static void toggleLastApp(Context context, int userId) {
        final ActivityInfo homeInfo = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME).resolveActivityInfo(context.getPackageManager(), 0);
        final ActivityManager am = (ActivityManager) context
                .getSystemService(Activity.ACTIVITY_SERVICE);
        final List<ActivityManager.RecentTaskInfo> tasks = am.getRecentTasks(8,
                ActivityManager.RECENT_IGNORE_UNAVAILABLE |
                ActivityManager.RECENT_WITH_EXCLUDED);

        int lastAppId = 0;
        for (int i = 1; i < tasks.size(); i++) {
            final ActivityManager.RecentTaskInfo info = tasks.get(i);
            boolean isExcluded = (info.baseIntent.getFlags() & Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                    == Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS;
            if (isExcluded) {
                continue;
            }
            if (isCurrentHomeActivity(info.baseIntent.getComponent(), homeInfo)) {
                continue;
            }
            lastAppId = info.persistentId;
            break;
        }
        if (lastAppId > 0) {
            ActivityOptions options = ActivityOptions.makeCustomAnimation(context,
                    R.anim.last_app_in, R.anim.last_app_out);
            try {
                ActivityManagerNative.getDefault().startActivityFromRecents(
                        lastAppId,  options.toBundle());
            } catch (RemoteException e) {
            }
        }
    }

    private static boolean isCurrentHomeActivity(ComponentName component,
            ActivityInfo homeInfo) {
        return homeInfo != null
                && homeInfo.packageName.equals(component.getPackageName())
                && homeInfo.name.equals(component.getClassName());
    }

    private static int getRunningTask(Context context) {
        final ActivityManager am = (ActivityManager) context
                .getSystemService(Context.ACTIVITY_SERVICE);

        List<ActivityManager.RunningTaskInfo> tasks = am.getRunningTasks(1);
        if (tasks != null && !tasks.isEmpty()) {
            return tasks.get(0).id;
        }
        return -1;
    }

    public static ActivityInfo getRunningActivityInfo(Context context) {
        final ActivityManager am = (ActivityManager) context
                .getSystemService(Context.ACTIVITY_SERVICE);
        final PackageManager pm = context.getPackageManager();

        List<ActivityManager.RunningTaskInfo> tasks = am.getRunningTasks(1);
        if (tasks != null && !tasks.isEmpty()) {
            ActivityManager.RunningTaskInfo top = tasks.get(0);
            try {
                return pm.getActivityInfo(top.topActivity, 0);
            } catch (PackageManager.NameNotFoundException e) {
            }
        }
        return null;
    }
}
