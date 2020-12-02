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

import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.net.ConnectivityManager;
import android.nfc.NfcAdapter;
import android.os.SystemProperties;
import android.os.Vibrator;
import android.os.UserHandle;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.util.DisplayMetrics;
import android.view.DisplayInfo;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.util.Log;

import com.android.internal.telephony.PhoneConstants;
import static android.hardware.Sensor.TYPE_LIGHT;
import static android.hardware.Sensor.TYPE_PROXIMITY;

import static com.android.internal.util.aicp.DeviceKeysConstants.*;

import java.util.ArrayList;
import java.util.List;

public class DeviceUtils {

    // Device types
    public static final int DEVICE_PHONE  = 0;
    public static final int DEVICE_HYBRID = 1;
    public static final int DEVICE_TABLET = 2;

    public static boolean deviceSupportsMobileData(Context context) {
        ConnectivityManager cm =
            (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        return cm.isNetworkSupported(ConnectivityManager.TYPE_MOBILE);
    }

    public static boolean deviceSupportsBluetooth() {
        return (BluetoothAdapter.getDefaultAdapter() != null);
    }

    //public static boolean deviceSupportsLte(Context context) {
    //    final TelephonyManager tm =
    //        (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
    //    return (tm.getLteOnCdmaMode() == PhoneConstants.LTE_ON_CDMA_TRUE);
    //                // || tm.getLteOnGsmMode() != 0; // add back if when we have support on LP for it
    //}

    public static boolean deviceSupportsGps(Context context) {
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS);
    }

    public static boolean deviceSupportsNfc(Context context) {
        return (NfcAdapter.getDefaultAdapter(context) != null) ||
           context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_NFC);
    }

    public static boolean deviceSupportsVibrator(Context ctx) {
        Vibrator vibrator = (Vibrator) ctx.getSystemService(Context.VIBRATOR_SERVICE);
        return vibrator.hasVibrator();
    }

    public static boolean deviceSupportsProximitySensor(Context context) {
        SensorManager sm = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        return sm.getDefaultSensor(TYPE_PROXIMITY) != null;
    }

    public static boolean deviceSupportsLightSensor(Context context) {
        SensorManager sm = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        return sm.getDefaultSensor(TYPE_LIGHT) != null;
    }

    public static boolean deviceSupportNavigationBar(Context context) {
        return deviceSupportNavigationBarForUser(context, UserHandle.USER_CURRENT);
    }

    public static boolean deviceSupportNavigationBarForUser(Context context, int userId) {
        final boolean showByDefault = context.getResources().getBoolean(
                com.android.internal.R.bool.config_showNavigationBar);
        final int hasNavigationBar = Settings.System.getIntForUser(
                context.getContentResolver(),
                Settings.System.FORCE_SHOW_NAVBAR, -1,
                userId);

        if (hasNavigationBar == -1) {
            String navBarOverride = SystemProperties.get("qemu.hw.mainkeys");
            if ("1".equals(navBarOverride)) {
                return false;
            } else if ("0".equals(navBarOverride)) {
                return true;
            } else {
                return showByDefault;
            }
        } else {
            return hasNavigationBar == 1;
        }
    }

    private static int getScreenType(Context con) {
        WindowManager wm = (WindowManager)con.getSystemService(Context.WINDOW_SERVICE);
        DisplayInfo outDisplayInfo = new DisplayInfo();
        wm.getDefaultDisplay().getDisplayInfo(outDisplayInfo);
        int shortSize = Math.min(outDisplayInfo.logicalHeight, outDisplayInfo.logicalWidth);
        int shortSizeDp =
            shortSize * DisplayMetrics.DENSITY_DEFAULT / outDisplayInfo.logicalDensityDpi;
        if (shortSizeDp < 600) {
            return DEVICE_PHONE;
        } else if (shortSizeDp < 720) {
            return DEVICE_HYBRID;
        } else {
            return DEVICE_TABLET;
        }
    }

    public static boolean isPhone(Context con) {
        return getScreenType(con) == DEVICE_PHONE;
    }

    public static boolean isHybrid(Context con) {
        return getScreenType(con) == DEVICE_HYBRID;
    }

    public static boolean isTablet(Context con) {
        return getScreenType(con) == DEVICE_TABLET;
    }

    public static boolean isLandscapePhone(Context context) {
        Configuration config = context.getResources().getConfiguration();
        return config.orientation == Configuration.ORIENTATION_LANDSCAPE
                && config.smallestScreenWidthDp < 600;
    }

    public static boolean isDataEncrypted() {
        String voldState = SystemProperties.get("vold.decrypt");
        return "1".equals(voldState) || "trigger_restart_min_framework".equals(voldState);
    }

    public static boolean deviceSupportsFlashLight(Context context) {
        CameraManager cameraManager = (CameraManager) context.getSystemService(
                Context.CAMERA_SERVICE);
        try {
            String[] ids = cameraManager.getCameraIdList();
            for (String id : ids) {
                CameraCharacteristics c = cameraManager.getCameraCharacteristics(id);
                Boolean flashAvailable = c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                Integer lensFacing = c.get(CameraCharacteristics.LENS_FACING);
                if (flashAvailable != null
                        && flashAvailable
                        && lensFacing != null
                        && lensFacing == CameraCharacteristics.LENS_FACING_BACK) {
                    return true;
                }
            }
        } catch (CameraAccessException | AssertionError e) {
            // Ignore
        }
        return false;
    }

    public static boolean deviceSupportsCompass(Context ctx) {
        SensorManager sm = (SensorManager) ctx.getSystemService(Context.SENSOR_SERVICE);
        return sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null
                && sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) != null;
    }

    public static int getDeviceKeys(Context context) {
        return context.getResources().getInteger(
                com.android.internal.R.integer.config_deviceHardwareKeys);
    }

    public static int getDeviceWakeKeys(Context context) {
        return context.getResources().getInteger(
                com.android.internal.R.integer.config_deviceHardwareWakeKeys);
    }

    /* returns whether the device has power key or not. */
    public static boolean hasPowerKey() {
        return KeyCharacterMap.deviceHasKey(KeyEvent.KEYCODE_POWER);
    }

    /* returns whether the device has home key or not. */
    public static boolean hasHomeKey(Context context) {
        return (getDeviceKeys(context) & KEY_MASK_HOME) != 0;
    }

    /* returns whether the device has back key or not. */
    public static boolean hasBackKey(Context context) {
        return (getDeviceKeys(context) & KEY_MASK_BACK) != 0;
    }

    /* returns whether the device has menu key or not. */
    public static boolean hasMenuKey(Context context) {
        return (getDeviceKeys(context) & KEY_MASK_MENU) != 0;
    }

    /* returns whether the device has assist key or not. */
    public static boolean hasAssistKey(Context context) {
        return (getDeviceKeys(context) & KEY_MASK_ASSIST) != 0;
    }

    /* returns whether the device has app switch key or not. */
    public static boolean hasAppSwitchKey(Context context) {
        return (getDeviceKeys(context) & KEY_MASK_APP_SWITCH) != 0;
    }

    /* returns whether the device has camera key or not. */
    public static boolean hasCameraKey(Context context) {
        return (getDeviceKeys(context) & KEY_MASK_CAMERA) != 0;
    }

    /* returns whether the device has volume rocker or not. */
    public static boolean hasVolumeKeys(Context context) {
        return (getDeviceKeys(context) & KEY_MASK_VOLUME) != 0;
    }

    /* returns whether the device can be waken using the home key or not. */
    public static boolean canWakeUsingHomeKey(Context context) {
        return (getDeviceWakeKeys(context) & KEY_MASK_HOME) != 0;
    }

    /* returns whether the device can be waken using the back key or not. */
    public static boolean canWakeUsingBackKey(Context context) {
        return (getDeviceWakeKeys(context) & KEY_MASK_BACK) != 0;
    }

    /* returns whether the device can be waken using the menu key or not. */
    public static boolean canWakeUsingMenuKey(Context context) {
        return (getDeviceWakeKeys(context) & KEY_MASK_MENU) != 0;
    }

    /* returns whether the device can be waken using the assist key or not. */
    public static boolean canWakeUsingAssistKey(Context context) {
        return (getDeviceWakeKeys(context) & KEY_MASK_ASSIST) != 0;
    }

    /* returns whether the device can be waken using the app switch key or not. */
    public static boolean canWakeUsingAppSwitchKey(Context context) {
        return (getDeviceWakeKeys(context) & KEY_MASK_APP_SWITCH) != 0;
    }

    /* returns whether the device can be waken using the camera key or not. */
    public static boolean canWakeUsingCameraKey(Context context) {
        return (getDeviceWakeKeys(context) & KEY_MASK_CAMERA) != 0;
    }

    /* returns whether the device can be waken using the volume rocker or not. */
    public static boolean canWakeUsingVolumeKeys(Context context) {
        return (getDeviceWakeKeys(context) & KEY_MASK_VOLUME) != 0;
    }

    /* returns whether the device supports button backlight adjusment or not. */
    public static boolean hasButtonBacklightSupport(Context context) {
        final boolean buttonBrightnessControlSupported = context.getResources().getInteger(
                com.android.internal.R.integer
                        .config_deviceSupportsButtonBrightnessControl) != 0;

        // All hardware keys besides volume and camera can possibly have a backlight
        return buttonBrightnessControlSupported
                && (hasHomeKey(context) || hasBackKey(context) || hasMenuKey(context)
                || hasAssistKey(context) || hasAppSwitchKey(context));
    }

    /* returns whether the device supports keyboard backlight adjusment or not.
    public static boolean hasKeyboardBacklightSupport(Context context) {
        return context.getResources().getInteger(com.android.internal.R.integer
                .config_deviceSupportsKeyboardBrightnessControl) != 0;
    } */

    public static FilteredDeviceFeaturesArray filterUnsupportedDeviceFeatures(Context context,
            String[] valuesArray, String[] entriesArray) {
        if (valuesArray == null || entriesArray == null || context == null) {
            return null;
        }
        List<String> finalEntries = new ArrayList<String>();
        List<String> finalValues = new ArrayList<String>();
        FilteredDeviceFeaturesArray filteredDeviceFeaturesArray =
            new FilteredDeviceFeaturesArray();

        for (int i = 0; i < valuesArray.length; i++) {
            if (isSupportedFeature(context, valuesArray[i])) {
                finalEntries.add(entriesArray[i]);
                finalValues.add(valuesArray[i]);
            }
        }
        filteredDeviceFeaturesArray.entries =
            finalEntries.toArray(new String[finalEntries.size()]);
        filteredDeviceFeaturesArray.values =
            finalValues.toArray(new String[finalValues.size()]);
        return filteredDeviceFeaturesArray;
    }

    private static boolean isSupportedFeature(Context context, String action) {
        if (action.equals(ActionConstants.ACTION_TORCH)
                        && !deviceSupportsFlashLight(context)
                || action.equals(ActionConstants.ACTION_VIB)
                        && !deviceSupportsVibrator(context)
                || action.equals(ActionConstants.ACTION_VIB_SILENT)
                        && !deviceSupportsVibrator(context)) {
            return false;
        }
        return true;
    }

    public static class FilteredDeviceFeaturesArray {
        public String[] entries;
        public String[] values;
    }
}
