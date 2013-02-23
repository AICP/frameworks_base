/*
 * Copyright (C) 2013 The CyanogenMod Project
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

package com.android.systemui.aokp;

import android.app.SearchManager;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuffXfermode;
import android.graphics.PorterDuff.Mode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Xfermode;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.StateListDrawable;
import android.net.Uri;
import android.media.AudioManager;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.TextUtils;

import static com.android.internal.util.aokp.AwesomeConstants.*;
import com.android.systemui.R;
import java.io.File;
import java.net.URISyntaxException;

public class NavBarHelpers {

    private NavBarHelpers() {
    }

    public static Drawable getIconImage(Context mContext, String uri) {
        if (TextUtils.isEmpty(uri)) {
            return mContext.getResources().getDrawable(R.drawable.ic_sysbar_null);
        }

        Drawable newDrawable = mContext.getResources().getDrawable(R.drawable.ic_sysbar_null);
        AwesomeConstant IconEnum = fromString(uri);
        switch (IconEnum) {
        case ACTION_HOME:
             newDrawable = mContext.getResources().getDrawable(R.drawable.ic_sysbar_home);
             break;
        case ACTION_BACK:
            newDrawable = mContext.getResources().getDrawable(R.drawable.ic_sysbar_back);
            break;
        case ACTION_RECENTS:
            newDrawable = mContext.getResources().getDrawable(R.drawable.ic_sysbar_recent);
            break;
        case ACTION_RECENTS_GB:
            newDrawable = mContext.getResources().getDrawable(R.drawable.ic_sysbar_recent_gb);
            break;
        case ACTION_MENU:
            newDrawable = mContext.getResources().getDrawable(R.drawable.ic_sysbar_menu_big);
            break;
        case ACTION_IME:
            newDrawable = mContext.getResources().getDrawable(R.drawable.ic_sysbar_ime_switcher);
            break;
        case ACTION_KILL:
            newDrawable = mContext.getResources().getDrawable(R.drawable.ic_sysbar_killtask);
            break;
        case ACTION_POWER:
            newDrawable = mContext.getResources().getDrawable(R.drawable.ic_sysbar_power);
            break;
        case ACTION_SEARCH:
            newDrawable = mContext.getResources().getDrawable(R.drawable.ic_sysbar_search);
            break;
        case ACTION_NOTIFICATIONS:
            newDrawable = mContext.getResources().getDrawable(R.drawable.ic_sysbar_notifications);
            break;
        case ACTION_LAST_APP:
            newDrawable = mContext.getResources().getDrawable(R.drawable.ic_sysbar_lastapp);
            break;
        case ACTION_APP:
            try {
                newDrawable = mContext.getPackageManager().getActivityIcon(Intent.parseUri(uri, 0));
            } catch (NameNotFoundException e) {
                e.printStackTrace();
                newDrawable = mContext.getResources().getDrawable(R.drawable.ic_sysbar_null);
            } catch (URISyntaxException e) {
                e.printStackTrace();
                newDrawable = mContext.getResources().getDrawable(R.drawable.ic_sysbar_null);
            }
            break;
        }
        return newDrawable;
    }

    public static String getProperSummary(Context mContext, String uri) {
        if (TextUtils.isEmpty(uri)) {
            return mContext.getResources().getString(R.string.action_none);
        }

        String newSummary = mContext.getResources().getString(R.string.action_none);
        AwesomeConstant stringEnum = fromString(uri);
        switch (stringEnum) {
        case ACTION_HOME:
            newSummary = mContext.getResources().getString(R.string.action_home);
            break;
        case ACTION_BACK:
            newSummary = mContext.getResources().getString(R.string.action_back);
            break;
        case ACTION_RECENTS:
            newSummary = mContext.getResources().getString(R.string.action_recents);
            break;
        case ACTION_MENU:
            newSummary = mContext.getResources().getString(R.string.action_menu);
            break;
        case ACTION_IME:
            newSummary = mContext.getResources().getString(R.string.action_ime);
            break;
        case ACTION_KILL:
            newSummary = mContext.getResources().getString(R.string.action_kill);
            break;
        case ACTION_POWER:
            newSummary = mContext.getResources().getString(R.string.action_power);
            break;
        case ACTION_SEARCH:
            newSummary = mContext.getResources().getString(R.string.action_search);
            break;
        case ACTION_NOTIFICATIONS:
            newSummary = mContext.getResources().getString(R.string.action_notifications);
            break;
        case ACTION_LAST_APP:
            newSummary = mContext.getResources().getString(R.string.action_lastapp);
            break;
        case ACTION_NULL:
            newSummary = mContext.getResources().getString(R.string.action_none);
            break;
        case ACTION_APP:
            try {
                Intent intent = Intent.parseUri(uri, 0);
                if (Intent.ACTION_MAIN.equals(intent.getAction())) {
                    return getFriendlyActivityName(mContext, intent);
                }
                return getFriendlyShortcutName(mContext, intent);
            } catch (URISyntaxException e) {
            newSummary = mContext.getResources().getString(R.string.action_none);
            }
            break;
        }
        return newSummary;
    }

    private static String getFriendlyActivityName(Context mContext, Intent intent) {
        PackageManager pm = mContext.getPackageManager();
        ActivityInfo ai = intent.resolveActivityInfo(pm, PackageManager.GET_ACTIVITIES);
        String friendlyName = null;

        if (ai != null) {
            friendlyName = ai.loadLabel(pm).toString();
            if (friendlyName == null) {
                friendlyName = ai.name;
            }
        }

        return (friendlyName != null) ? friendlyName : intent.toUri(0);
    }

    private static String getFriendlyShortcutName(Context mContext, Intent intent) {
        String activityName = getFriendlyActivityName(mContext, intent);
        String name = intent.getStringExtra(Intent.EXTRA_SHORTCUT_NAME);

        if (activityName != null && name != null) {
            return activityName + ": " + name;
        }
        return name != null ? name : intent.toUri(0);
    }
}
