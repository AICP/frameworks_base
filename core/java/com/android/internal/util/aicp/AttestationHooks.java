/*
 * Copyright (C) 2021 The Android Open Source Project
 *           (C) 2023 ArrowOS
 *           (C) 2023 The LibreMobileOS Foundation
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

package com.android.internal.util.aicp;

import android.app.Application;
import android.content.Context;
import android.content.res.Resources;
import android.os.Build;
import android.os.SystemProperties;
import android.text.TextUtils;
import android.util.Log;

import com.android.internal.R;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * @hide
 */
public final class AttestationHooks {

    private static final String TAG = "AttestationHooks";
    private static final String DEVICE = "ro.aicp.device";
    private static final boolean DEBUG = false;

    private static final String PACKAGE_GPHOTOS = "com.google.android.apps.photos";
    private static final String PACKAGE_VENDING = "com.android.vending";
    private static final String PACKAGE_SNAPCHAT = "com.snapchat.android";

    private static final Map<String, Object> sMainlineDeviceProps = Map.of(
        "BRAND", "google",
        "MANUFACTURER", "Google",
        "DEVICE", "komodo",
        "PRODUCT", "komodo",
        "MODEL", "Pixel 9 Pro XL",
        "FINGERPRINT", "google/komodo/komodo:14/AD1A.240905.004/12196292:user/release-keys"
    );

    private static final Map<String, Object> sPixel5aProps = Map.of(
        "BRAND", "google",
        "MANUFACTURER", "Google",
        "DEVICE", "barbet",
        "PRODUCT", "barbet",
        "MODEL", "Pixel 5a",
        "FINGERPRINT", "google/barbet/barbet:14/AP2A.240805.005/12025142:user/release-keys"
    );

    private static final Map<String, Object> sPixelXLProps = Map.of(
        "BRAND", "google",
        "MANUFACTURER", "Google",
        "DEVICE", "marlin",
        "PRODUCT", "marlin",
        "MODEL", "Pixel XL",
        "FINGERPRINT", "google/marlin/marlin:10/QP1A.191005.007.A3/5972272:user/release-keys"
    );

    // Codenames for currently supported Pixels by Google
    private static final String[] pixelCodenames = {
            "rango",
            "mustang",
            "blazer",
            "frankel",
            "komodo",
            "caiman",
            "tokay",
            "comet",
            "akita",
            "husky",
            "shiba",
            "felix",
            "tangorpro",
            "lynx",
            "cheetah",
            "panther",
            "bluejay",
            "oriole",
            "raven",
            "barbet"
    };

    private static volatile String sProcessName;

    private AttestationHooks() { }

    public static void setProps(Context context) {
        final String packageName = context.getPackageName();
        final String processName = Application.getProcessName();

        if (TextUtils.isEmpty(packageName) || processName == null) {
            return;
        }

        sProcessName = processName;

        boolean isPixelDevice = Arrays.asList(pixelCodenames).contains(SystemProperties.get(DEVICE));
        if (packageName.equals(PACKAGE_GPHOTOS)) {
            if (SystemProperties.getBoolean("persist.sys.gphooks.enable", false)) {
                sPixelXLProps.forEach(AttestationHooks::setPropValue);
            } else {
                if (!isPixelDevice) {
                    sPixel5aProps.forEach(AttestationHooks::setPropValue);
                }
            }
        }

        if (packageName.equals(PACKAGE_VENDING)) {
            if (SystemProperties.getBoolean("persist.sys.vending.enable", false)) {
                sMainlineDeviceProps.forEach(AttestationHooks::setPropValue);
            }
        }

        if (packageName.equals(PACKAGE_SNAPCHAT)) {
            if (SystemProperties.getBoolean("persist.sys.snaphooks.enable", true)) {
                if (!isPixelDevice) {
                    sPixelXLProps.forEach(AttestationHooks::setPropValue);
                }
            }
        }
    }

    private static void setPropValue(String key, Object value) {
        try {
            dlog("Setting prop " + key + " to " + value.toString());
            Field field = Build.class.getDeclaredField(key);
            field.setAccessible(true);
            field.set(null, value);
            field.setAccessible(false);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            Log.e(TAG, "Failed to set prop " + key, e);
        }
    }

    public static void dlog(String msg) {
        if (DEBUG) Log.d(TAG, "[" + sProcessName + "] " + msg);
    }
}
