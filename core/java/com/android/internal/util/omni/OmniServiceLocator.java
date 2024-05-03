/*
 *  Copyright (C) 2021 The OmniROM Project
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package com.android.internal.util.omni;

import android.content.Context;
import android.net.Uri;
import android.provider.Settings;
import android.text.TextUtils;
import android.webkit.URLUtil;

public class OmniServiceLocator {

    private static String getWalllpaperBaseUrl(Context context) {
        String s = Settings.System.getString(context.getContentResolver(), "wallpaper_base_url");
        if (TextUtils.isEmpty(s)) {
            return "https://dl.omnirom.org/";
        }
        return s;
    }

    private static String getWalllpaperRootUri(Context context) {
        String s = Settings.System.getString(context.getContentResolver(), "wallpaper_root_uri");
        if (TextUtils.isEmpty(s)) {
            return "images/wallpapers/";
        }
        return s;
    }

    private static String getWalllpaperQueryUri(Context context) {
        String s = Settings.System.getString(context.getContentResolver(), "wallpaper_query_uri");
        if (TextUtils.isEmpty(s)) {
            return "images/wallpapers/thumbs/json_wallpapers_xml.php";
        }
        return s;
    }

    private static String getHeaderBaseUrl(Context context) {
        String s = Settings.System.getString(context.getContentResolver(), "header_base_url");
        if (TextUtils.isEmpty(s)) {
            return "https://dl.omnirom.org/";
        }
        return s;
    }

    private static String getHeaderRootUri(Context context) {
        String s = Settings.System.getString(context.getContentResolver(), "header_root_uri");
        if (TextUtils.isEmpty(s)) {
            return "images/headers/";
        }
        return s;
    }

    private static String getHeaderQueryUri(Context context) {
        String s = Settings.System.getString(context.getContentResolver(), "header_query_uri");
        if (TextUtils.isEmpty(s)) {
            return "images/headers/thumbs/json_headers_xml.php";
        }
        return s;
    }

    // OmniStore is external apk and dont use this so its just FYI
    private static String getStoreBaseUrl(Context context) {
        String s = Settings.System.getString(context.getContentResolver(), "store_base_url");
        if (TextUtils.isEmpty(s)) {
            return "https://dl.omnirom.org/";
        }
        return s;
    }

    private static String getStoreRootUri(Context context) {
        String s = Settings.System.getString(context.getContentResolver(), "store_root_uri");
        if (TextUtils.isEmpty(s)) {
            return "store/";
        }
        return s;
    }

    private static String getStoreQuertUri(Context context) {
        String s = Settings.System.getString(context.getContentResolver(), "store_query_uri");
        if (TextUtils.isEmpty(s)) {
            return "store/apps.json";
        }
        return s;
    }

    private static String getBuildsBaseUrl(Context context) {
        String s = Settings.System.getString(context.getContentResolver(), "builds_base_url");
        if (TextUtils.isEmpty(s)) {
            return "https://dl.omnirom.org/";
        }
        return s;
    }

    private static String getBuildsRootUri(Context context) {
        String s = Settings.System.getString(context.getContentResolver(), "builds_root_uri");
        if (TextUtils.isEmpty(s)) {
            return null;
        }
        return s;
    }

    private static String getBuildsQueryUri(Context context) {
        String s = Settings.System.getString(context.getContentResolver(), "builds_query_uri");
        if (TextUtils.isEmpty(s)) {
            return "json.php";
        }
        return s;
    }

    private static String getBuildsSecondaryBaseUrl(Context context) {
        String s = Settings.System.getString(context.getContentResolver(), "builds_secondary_base_url");
        if (TextUtils.isEmpty(s)) {
            return "https://dl.omnirom.org/tmp/";
        }
        return s;
    }

    private static String getBuildsDeltaBaseUrl(Context context) {
        String s = Settings.System.getString(context.getContentResolver(), "builds_delta_base_url");
        if (TextUtils.isEmpty(s)) {
            return "https://delta.omnirom.org/";
        }
        return s;
    }

    private static String getBuildsDeltaRootUri(Context context) {
        String s = Settings.System.getString(context.getContentResolver(), "builds_delta_root_uri");
        if (TextUtils.isEmpty(s)) {
            return "weeklies/";
        }
        return s;
    }

    public static String buildWalllpaperQueryUrl(Context context) {
        String queryUri = getWalllpaperQueryUri(context);
        if (URLUtil.isNetworkUrl(queryUri)) {
            return queryUri;
        }
        Uri base = Uri.parse(getWalllpaperBaseUrl(context));
        Uri u = Uri.withAppendedPath(base, queryUri);
        return u.toString();
    }

    public static String buildWalllpaperRootUrl(Context context) {
        String rootUri = getWalllpaperRootUri(context);
        if (TextUtils.isEmpty(rootUri)) {
            return getWalllpaperBaseUrl(context);
        }
        if (URLUtil.isNetworkUrl(rootUri)) {
            return rootUri;
        }
        Uri base = Uri.parse(getWalllpaperBaseUrl(context));
        Uri u = Uri.withAppendedPath(base, rootUri);
        return u.toString();
    }
    
    public static String buildHeaderQueryUrl(Context context) {
        String queryUri = getHeaderQueryUri(context);
        if (URLUtil.isNetworkUrl(queryUri)) {
            return queryUri;
        }
        Uri base = Uri.parse(getHeaderBaseUrl(context));
        Uri u = Uri.withAppendedPath(base, queryUri);
        return u.toString();
    }

    public static String buildHeaderRootUrl(Context context) {
        String rootUri = getHeaderRootUri(context);
        if (TextUtils.isEmpty(rootUri)) {
            return getHeaderBaseUrl(context);
        }
        if (URLUtil.isNetworkUrl(rootUri)) {
            return rootUri;
        }
        Uri base = Uri.parse(getHeaderBaseUrl(context));
        Uri u = Uri.withAppendedPath(base, rootUri);
        return u.toString();
    }

    public static String buildBuildsQueryUrl(Context context, boolean secondary) {
        String queryUri = getBuildsQueryUri(context);
        if (URLUtil.isNetworkUrl(queryUri)) {
            return queryUri;
        }
        Uri base = Uri.parse(secondary ? getBuildsSecondaryBaseUrl(context) : getBuildsBaseUrl(context));
        Uri u = Uri.withAppendedPath(base, queryUri);
        return u.toString();
    }

    public static String buildBuildsRootUrl(Context context, boolean secondary) {
        String rootUri = getBuildsRootUri(context);
        if (TextUtils.isEmpty(rootUri)) {
            return secondary ? getBuildsSecondaryBaseUrl(context) : getBuildsBaseUrl(context);
        }
        if (URLUtil.isNetworkUrl(rootUri)) {
            return rootUri;
        }
        Uri base = Uri.parse(secondary ? getBuildsSecondaryBaseUrl(context) : getBuildsBaseUrl(context));
        Uri u = Uri.withAppendedPath(base, rootUri);
        return u.toString();
    }

    public static String buildBuildsDeltasRootUrl(Context context) {
        String rootUri = getBuildsDeltaRootUri(context);
        if (TextUtils.isEmpty(rootUri)) {
            return getBuildsDeltaBaseUrl(context);
        }
        if (URLUtil.isNetworkUrl(rootUri)) {
            return rootUri;
        }
        Uri base = Uri.parse(getBuildsDeltaBaseUrl(context));
        Uri u = Uri.withAppendedPath(base, rootUri);
        return u.toString();
    }
}
