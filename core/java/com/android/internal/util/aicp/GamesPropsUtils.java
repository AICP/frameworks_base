/*
 * Copyright (C) 2023 The Android Open Source Project
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

import android.content.Context;
import android.os.Build;
import android.os.SystemProperties;
import android.util.Log;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * @hide
 */
public final class GamesPropsUtils {

    private static final String TAG = GamesPropsUtils.class.getSimpleName();
    private static final boolean DEBUG = false;

    private static final Map<String, Object> propsToChangeBS4 = createMap("2SM-X706B", "blackshark");
    private static final String[] packagesToChangeBS4 = { // spoof as Black Shark 4
            "com.proximabeta.mf.uamo"
    };

    private static final Map<String, Object> propsToChangeMI11TP = createMap("2107113SI", "Xiaomi");
    private static final String[] packagesToChangeMI11TP = { // spoof as Mi 11T PRO
            "com.ea.gp.apexlegendsmobilefps",
            "com.levelinfinite.hotta.gp",
            "com.supercell.brawlstars",
            "com.supercell.clashofclans",
            "com.vng.mlbbvn"
    };

    private static final Map<String, Object> propsToChangeMI13P = createMap("2210132C", "Xiaomi");
    private static final String[] packagesToChangeMI13P = { // spoof as Mi 13 PRO
            "com.levelinfinite.sgameGlobal",
            "com.tencent.tmgp.sgame"
    };

    private static final Map<String, Object> propsToChangeOP8P = createMap("IN2020", "OnePlus");
    private static final String[] packagesToChangeOP8P = { // spoof as OnePlus 8 PRO
            "com.netease.lztgglobal",
            "com.riotgames.league.wildrift",
            "com.riotgames.league.wildrifttw",
            "com.riotgames.league.wildriftvn"
    };

    private static final Map<String, Object> propsToChangeOP9P = createMap("LE2101", "OnePlus");
    private static final String[] packagesToChangeOP9P = { // spoof as OnePlus 9 PRO
            "com.epicgames.fortnite",
            "com.epicgames.portal",
            "com.tencent.lolm"
    };

    private static final Map<String, Object> propsToChangeF5 = createMap("23049PCD8G", "Xiaomi");
    private static final String[] packagesToChangeF5 = { // spoof as POCO F5
            "com.dts.freefiremax",
            "com.dts.freefireth",
            "com.mobile.legends"
    };

    private static final Map<String, Object> propsToChangeROG6 = createMap("ASUS_AI2201", "asus");
    private static final String[] packagesToChangeROG6 = { // spoof as ROG Phone 6
    	    "com.ea.gp.fifamobile",
            "com.gameloft.android.ANMP.GloftA9HM",
            "com.madfingergames.legends",
            "com.pearlabyss.blackdesertm",
            "com.pearlabyss.blackdesertm.gl"
    };

    private static final Map<String, Object> propsToChangeROG8P = createMap("ASUS_AI2401_A", "asus");
    private static final String[] packagesToChangeROG8P = { // spoof as ROG Phone 8 Pro
            "com.pubg.imobile",
            "com.pubg.krmobile",
            "com.rekoo.pubgm",
            "com.tencent.ig",
            "com.tencent.tmgp.pubgmhd",
            "com.vng.pubgmobile"
    };

    private static final Map<String, Object> propsToChangeLenovoY700 = createMap("Lenovo TB-9707F", "Lenovo");
    private static final String[] packagesToChangeLenovoY700 = { // spoof as Lenovo TB-9707F
            "com.activision.callofduty.shooter",
            "com.garena.game.codm",
            "com.tencent.tmgp.kr.codm",
            "com.vng.codmvn"
    };

    private static Map<String, Object> createMap(String model, String manufacturer) {
        Map<String, Object> map = new HashMap<>();
        map.put("MODEL", model);
        map.put("MANUFACTURER", manufacturer);
        return map;
    }

    public static void setProps(Context context) {
        final String packageName = context.getPackageName();

        if (packageName == null || packageName.isEmpty()) {
            return;
        }
        Map<String, Object> propsToChange = null;
        if (!SystemProperties.getBoolean("persist.sys.gamehooks.enable", false)) {
            return;
        } else {
            if (Arrays.asList(packagesToChangeBS4).contains(packageName)) {
                propsToChange = propsToChangeBS4;
            } else if (Arrays.asList(packagesToChangeMI11TP).contains(packageName)) {
                propsToChange = propsToChangeMI11TP;
            } else if (Arrays.asList(packagesToChangeMI13P).contains(packageName)) {
                propsToChange = propsToChangeMI13P;
            } else if (Arrays.asList(packagesToChangeOP8P).contains(packageName)) {
                propsToChange = propsToChangeOP8P;
            } else if (Arrays.asList(packagesToChangeOP9P).contains(packageName)) {
                propsToChange = propsToChangeOP9P;
            } else if (Arrays.asList(packagesToChangeF5).contains(packageName)) {
                propsToChange = propsToChangeF5;
            } else if (Arrays.asList(packagesToChangeROG6).contains(packageName)) {
                propsToChange = propsToChangeROG6;
            } else if (Arrays.asList(packagesToChangeROG8P).contains(packageName)) {
                propsToChange = propsToChangeROG8P;
            } else if (Arrays.asList(packagesToChangeLenovoY700).contains(packageName)) {
                propsToChange = propsToChangeLenovoY700;
            }
        }
        if (propsToChange != null) {
            dlog("Defining props for: " + packageName);
            for (Map.Entry<String, Object> prop : propsToChange.entrySet()) {
                String key = prop.getKey();
                Object value = prop.getValue();
                setPropValue(key, value);
            }
        }
    }

    private static void setPropValue(String key, Object value) {
        try {
            dlog("Defining prop " + key + " to " + value.toString());
            Field field = Build.class.getDeclaredField(key);
            field.setAccessible(true);
            field.set(null, value);
            field.setAccessible(false);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            Log.e(TAG, "Failed to set prop " + key, e);
        }
    }

    public static void dlog(String msg) {
        if (DEBUG) Log.d(TAG, msg);
    }
}
