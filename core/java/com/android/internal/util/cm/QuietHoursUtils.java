/*
 * Copyright (C) 2013 The CyanogenMod Project
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

package com.android.internal.util.cm;

import android.content.Context;
import android.os.UserHandle;
import android.provider.Settings;

import java.util.Calendar;

public class QuietHoursUtils {
    public static boolean inQuietHours(Context context, String option) {
        boolean mode = true;
        boolean quietHoursEnabled = Settings.AOKP.getInt(context.getContentResolver(),
                Settings.AOKP.QUIET_HOURS_ENABLED, 0) != 0;
        int quietHoursStart = Settings.AOKP.getInt(context.getContentResolver(),
                Settings.AOKP.QUIET_HOURS_START, 0);
        int quietHoursEnd = Settings.AOKP.getInt(context.getContentResolver(),
                Settings.AOKP.QUIET_HOURS_END, 0);
        boolean quietHoursOption = Settings.AOKP.getInt(context.getContentResolver(),
                option, 0) != 0;

        if (option != null) {
            mode = Settings.AOKP.getIntForUser(context.getContentResolver(),
                    option, 0,
                    UserHandle.USER_CURRENT_OR_SELF) != 0;
        }

        if (quietHoursEnabled && mode) {
            // 24-hours toggleable
            if (quietHoursStart == quietHoursEnd) {
                return true;
            }
            // Get the date in "quiet hours" format.
            Calendar calendar = Calendar.getInstance();
            int minutes = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE);
            if (quietHoursEnd < quietHoursStart) {
                // Starts at night, ends in the morning.
                return (minutes >= quietHoursStart) || (minutes < quietHoursEnd);
            } else {
                return (minutes >= quietHoursStart) && (minutes < quietHoursEnd);
            }
        }
        return false;
    }
}
