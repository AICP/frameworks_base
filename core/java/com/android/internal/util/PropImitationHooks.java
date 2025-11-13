/*
 * Copyright (C) 2022 Paranoid Android
 *           (C) 2023 StatiXOS
 *           (C) 2023 ArrowOS
 *           (C) 2025 MicaOS
 *           (C) 2023 The LibreMobileOS Foundation
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

package com.android.internal.util;

import android.app.ActivityTaskManager;
import android.app.Application;
import android.app.TaskStackListener;
import android.content.ComponentName;
import android.content.Context;
import android.content.res.Resources;
import android.os.Build;
import android.os.Binder;
import android.os.Environment;
import android.os.Process;
import android.os.SystemProperties;
import android.text.TextUtils;
import android.util.Log;

import com.android.internal.R;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @hide
 */
public class PropImitationHooks {

    private static final String TAG = "PropImitationHooks";
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);
    private static final String DATA_FILE = "gms_certified_props.json";

    private static final String PACKAGE_AIWALLPAPERS = "com.google.android.apps.aiwallpapers";
    private static final String PACKAGE_ARCORE = "com.google.ar.core";
    private static final String PACKAGE_ASI = "com.google.android.as";
    private static final String PACKAGE_BARD = "com.google.android.apps.bard";
    private static final String PACKAGE_EMOJIWALLPAPER = "com.google.android.apps.emojiwallpaper";
    private static final String PACKAGE_FINSKY = "com.android.vending";
    private static final String PACKAGE_GMS = "com.google.android.gms";
    private static final String PACKAGE_GPHOTOS = "com.google.android.apps.photos";
    private static final String PACKAGE_NETFLIX = "com.netflix.mediaclient";
    private static final String PACKAGE_PIXELCREATIVE = "com.google.android.apps.pixel.creativeassistant";
    private static final String PACKAGE_PIXELTHEMES = "com.google.android.apps.customization.pixel";
    private static final String PACKAGE_PIXELWALLPAPER = "com.google.android.apps.wallpaper.pixel";
    private static final String PACKAGE_LIVEWALLPAPER = "com.google.pixel.livewallpaper";
    private static final String PACKAGE_SUBSCRIPTION_RED = "com.google.android.apps.subscriptions.red";
    private static final String PACKAGE_WALLPAPER = "com.google.android.apps.wallpaper";
    private static final String PACKAGE_WALLPAPEREFFECTS = "com.google.android.wallpaper.effects";
    private static final String PACKAGE_WEATHER = "com.google.android.apps.weather";
    private static final String PACKAGE_CUSTOMIZATION = "com.google.android.apps.pixel.customizationbundle";
    private static final String PACKAGE_MAGICPORTRAIT = "com.google.android.apps.magicportrait";
    private static final String PACKAGE_MAPS = "com.google.android.apps.maps";

    private static final String PROCESS_GMS_UNSTABLE = PACKAGE_GMS + ".unstable";

    private static final String PROP_SECURITY_PATCH = "persist.sys.pihooks.security_patch";
    private static final String PROP_FIRST_API_LEVEL = "persist.sys.pihooks.first_api_level";

    private static final String SPOOF_PIHOOKS_PI = "persist.sys.pihooks.pi";

    private static final ComponentName GMS_ADD_ACCOUNT_ACTIVITY = ComponentName.unflattenFromString(
            "com.google.android.gms/.auth.uiflows.minutemaid.MinuteMaidActivity");

    private static final Map<String, String> sPixelTenXLProps = Map.of(
            "PRODUCT", "mustang",
            "DEVICE", "mustang",
            "HARDWARE", "mustang",
            "MANUFACTURER", "Google",
            "BRAND", "google",
            "MODEL", "Pixel 10 Pro XL",
            "ID", "BD3A.251105.010.E1",
            "FINGERPRINT", "google/mustang/mustang:16/BD3A.251105.010.E1/14337626:user/release-keys"
    );

    private static final Map<String, String> sPixelXLProps = Map.of(
            "PRODUCT", "marlin",
            "DEVICE", "marlin",
            "HARDWARE", "marlin",
            "MANUFACTURER", "Google",
            "BRAND", "google",
            "MODEL", "Pixel XL",
            "ID", "QP1A.191005.007.A3",
            "FINGERPRINT", "google/marlin/marlin:10/QP1A.191005.007.A3/5972272:user/release-keys"
    );

    private static final Set<String> sNexusFeatures = Set.of(
            "NEXUS_PRELOAD",
            "nexus_preload",
            "GOOGLE_BUILD",
            "GOOGLE_EXPERIENCE",
            "PIXEL_EXPERIENCE"
    );

    private static final Set<String> sPixelFeatures = Set.of(
            "GOOGLE_BUILD",
            "GOOGLE_EXPERIENCE",
            "PIXEL_2017_EXPERIENCE",
            "PIXEL_2017_PRELOAD",
            "PIXEL_2018_EXPERIENCE",
            "PIXEL_2018_PRELOAD",
            "PIXEL_2019_EXPERIENCE",
            "PIXEL_2019_MIDYEAR_EXPERIENCE",
            "PIXEL_2019_MIDYEAR_PRELOAD",
            "PIXEL_2019_PRELOAD",
            "PIXEL_2020_EXPERIENCE",
            "PIXEL_2020_MIDYEAR_EXPERIENCE",
            "PIXEL_2021_MIDYEAR_EXPERIENCE"
    );

    private static final Set<String> sTensorFeatures = Set.of(
            "PIXEL_2021_EXPERIENCE",
            "PIXEL_2022_EXPERIENCE",
            "PIXEL_2022_MIDYEAR_EXPERIENCE",
            "PIXEL_2023_EXPERIENCE",
            "PIXEL_2023_MIDYEAR_EXPERIENCE",
            "PIXEL_2024_EXPERIENCE",
            "PIXEL_2024_MIDYEAR_EXPERIENCE",
            "PIXEL_2025_EXPERIENCE",
            "PIXEL_2025_MIDYEAR_EXPERIENCE"
    );

    private static volatile List<String> sCertifiedProps = new ArrayList<>();
    private static volatile String sStockFp;
    private static volatile String sNetflixModel;

    private static volatile String sProcessName;
    private static volatile boolean sIsPhotos;

    public static void setProps(Context context) {
        final String packageName = context.getPackageName();
        final String processName = Application.getProcessName();

        if (TextUtils.isEmpty(packageName) || TextUtils.isEmpty(processName)) {
            Log.e(TAG, "Null package or process name");
            return;
        }

        final Resources res = context.getResources();
        if (res == null) {
            Log.e(TAG, "Null resources");
            return;
        }

        sStockFp = res.getString(R.string.config_stockFingerprint);
        if (sStockFp == null || sStockFp.isEmpty()) {
            sStockFp = SystemProperties.get("ro.build.fingerprint", "");
        }
        sNetflixModel = res.getString(R.string.config_netflixSpoofModel);

        sProcessName = processName;
        sIsPhotos = packageName.equals(PACKAGE_GPHOTOS);

        /* Set certified properties for GMSCore
         * Set stock fingerprint for ARCore
         * Set Pixel 10 Pro XL for Specific Google apps
         * Set custom model for Netflix
         */

        switch (processName) {
            case PROCESS_GMS_UNSTABLE:
                dlog("Setting certified props for: " + packageName + " process: " + processName);
                setCertifiedPropsForGms(context);
                return;
        }

        switch (packageName) {
            case PACKAGE_AIWALLPAPERS:
            case PACKAGE_BARD:
            case PACKAGE_EMOJIWALLPAPER:
            case PACKAGE_LIVEWALLPAPER:
            case PACKAGE_GPHOTOS:
            case PACKAGE_PIXELCREATIVE:
            case PACKAGE_PIXELTHEMES:
            case PACKAGE_PIXELWALLPAPER:
            case PACKAGE_SUBSCRIPTION_RED:
            case PACKAGE_WALLPAPER:
            case PACKAGE_WALLPAPEREFFECTS:
            case PACKAGE_WEATHER:
            case PACKAGE_CUSTOMIZATION:
            case PACKAGE_MAGICPORTRAIT:
            case PACKAGE_MAPS:
                dlog("Spoofing Pixel 10 Pro XL for: " + packageName + " process: " + processName);
                setProps(sPixelTenXLProps);
                return;
            case PACKAGE_NETFLIX:
                if (!sNetflixModel.isEmpty()) {
                    dlog("Setting model to " + sNetflixModel + " for Netflix");
                    setPropValue("MODEL", sNetflixModel);;
                }
                return;
            case PACKAGE_ARCORE:
                if (!sStockFp.isEmpty()) {
                    dlog("Setting stock fingerprint for: " + packageName);
                    setPropValue("FINGERPRINT", sStockFp);;
                }
                return;
        }
    }

    private static void setProps(Map<String, String> props) {
        props.forEach(PropImitationHooks::setPropValue);
    }

    private static void setPropValue(String key, String value) {
        try {
            dlog("Setting prop " + key + " to " + value.toString());
            Class clazz = Build.class;
            if (key.startsWith("VERSION.")) {
                clazz = Build.VERSION.class;
                key = key.substring(8);
            }
            Field field = clazz.getDeclaredField(key);
            field.setAccessible(true);
            // Cast the value to int if it's an integer field, otherwise string.
            field.set(null, field.getType().equals(Integer.TYPE) ? Integer.parseInt(value) : value);
            field.setAccessible(false);
        } catch (Exception e) {
            Log.e(TAG, "Failed to set prop " + key, e);
        }
    }

    private static void setCertifiedPropsForGms(Context context) {
        if (!SystemProperties.getBoolean(SPOOF_PIHOOKS_PI, true))
            return;

        File dataFile = new File(Environment.getDataSystemDirectory(), DATA_FILE);
        String savedProps = readFromFile(dataFile);

        if (TextUtils.isEmpty(savedProps)) {
            Log.d(TAG, "Parsing props locally - data file unavailable");
            sCertifiedProps = Arrays.asList(context.getResources().getStringArray(R.array.config_certifiedBuildProperties));
        } else {
            Log.d(TAG, "Parsing props fetched by attestation service");
            try {
                JSONObject parsedProps = new JSONObject(savedProps);
                Iterator<String> keys = parsedProps.keys();

                while (keys.hasNext()) {
                    String key = keys.next();
                    String value = parsedProps.getString(key);
                    sCertifiedProps.add(key + ":" + value);
                }
            } catch (JSONException e) {
                Log.e(TAG, "Error parsing JSON data", e);
                Log.d(TAG, "Parsing props locally as fallback");
                sCertifiedProps = Arrays.asList(context.getResources().getStringArray(R.array.config_certifiedBuildProperties));
            }
        }
        final boolean was = isGmsAddAccountActivityOnTop();
        final TaskStackListener taskStackListener = new TaskStackListener() {
            @Override
            public void onTaskStackChanged() {
                final boolean is = isGmsAddAccountActivityOnTop();
                if (is ^ was) {
                    dlog("GmsAddAccountActivityOnTop is:" + is + " was:" + was +
                            ", killing myself!"); // process will restart automatically later
                    Process.killProcess(Process.myPid());
                }
            }
        };
        if (!was) {
            dlog("Spoofing build for GMS");
            setCertifiedProps();
        } else {
            dlog("Skip spoofing build for GMS, because GmsAddAccountActivityOnTop");
        }
        try {
            ActivityTaskManager.getService().registerTaskStackListener(taskStackListener);
        } catch (Exception e) {
            Log.e(TAG, "Failed to register task stack listener!", e);
        }
    }

    private static void setCertifiedProps() {
        for (String entry : sCertifiedProps) {
            // Each entry must be of the format FIELD:value
            final String[] fieldAndProp = entry.split(":", 2);
            if (fieldAndProp.length != 2) {
                Log.e(TAG, "Invalid entry in certified props: " + entry);
                continue;
            }
            setPropValue(fieldAndProp[0], fieldAndProp[1]);
        }
        setSystemProperty(PROP_SECURITY_PATCH, Build.VERSION.SECURITY_PATCH);
        setSystemProperty(PROP_FIRST_API_LEVEL,
                Integer.toString(Build.VERSION.DEVICE_INITIAL_SDK_INT));
    }

    private static String readFromFile(File file) {
        StringBuilder content = new StringBuilder();

        if (file.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String line;

                while ((line = reader.readLine()) != null) {
                    content.append(line);
                }
            } catch (IOException e) {
                Log.e(TAG, "Error reading from file", e);
            }
        }
        return content.toString();
    }

    private static void setSystemProperty(String name, String value) {
        try {
            SystemProperties.set(name, value);
            dlog("Set system prop " + name + "=" + value);
        } catch (Exception e) {
            Log.e(TAG, "Failed to set system prop " + name + "=" + value, e);
        }
    }

    private static boolean isGmsAddAccountActivityOnTop() {
        try {
            final ActivityTaskManager.RootTaskInfo focusedTask =
                    ActivityTaskManager.getService().getFocusedRootTaskInfo();
            return focusedTask != null && focusedTask.topActivity != null
                    && focusedTask.topActivity.equals(GMS_ADD_ACCOUNT_ACTIVITY);
        } catch (Exception e) {
            Log.e(TAG, "Unable to get top activity!", e);
        }
        return false;
    }

    public static boolean shouldBypassTaskPermission(Context context) {
        // GMS doesn't have MANAGE_ACTIVITY_TASKS permission
        final int callingUid = Binder.getCallingUid();
        final int gmsUid;
        try {
            gmsUid = context.getPackageManager().getApplicationInfo(PACKAGE_GMS, 0).uid;
            dlog("shouldBypassTaskPermission: gmsUid:" + gmsUid + " callingUid:" + callingUid);
        } catch (Exception e) {
            Log.e(TAG, "shouldBypassTaskPermission: unable to get gms uid", e);
            return false;
        }
        return gmsUid == callingUid;
    }

    public static boolean hasSystemFeature(String name, boolean has) {
        if (sIsPhotos) {
            if (has && (sPixelFeatures.stream().anyMatch(name::contains)
                    || sNexusFeatures.stream().anyMatch(name::contains))) {
                dlog("Blocked system feature " + name + " for Google Photos");
                has = true;
            } else if (!has && sTensorFeatures.stream().anyMatch(name::contains)) {
                dlog("Enabled system feature " + name + " for Google Photos");
                has = false;
            }
        }
        return has;
    }

    public static void dlog(String msg) {
        if (DEBUG) Log.d(TAG, "[" + sProcessName + "] " + msg);
    }
}
