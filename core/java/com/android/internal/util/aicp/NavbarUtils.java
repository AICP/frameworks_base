/*
* Copyright (C) 2021 The Pixel Experience Project
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

import android.content.Context;
import android.content.om.IOverlayManager;
import android.content.om.OverlayInfo;

import android.provider.Settings;
import android.os.SystemProperties;
import android.os.UserHandle;

import static com.android.internal.util.aicp.hwkeys.DeviceKeysConstants.*;
import com.android.internal.lineage.hardware.LineageHardwareManager;

import static android.view.WindowManagerPolicyConstants.NAV_BAR_MODE_2BUTTON_OVERLAY;
import static android.view.WindowManagerPolicyConstants.NAV_BAR_MODE_3BUTTON_OVERLAY;
import static android.view.WindowManagerPolicyConstants.NAV_BAR_MODE_GESTURAL_OVERLAY;

public class NavbarUtils {
    private static final String NAV_BAR_MODE_GESTURAL_OVERLAY_NARROW_BACK
            = "com.android.internal.systemui.navbar.gestural_narrow_back";

    private static final String NAV_BAR_MODE_GESTURAL_OVERLAY_WIDE_BACK
            = "com.android.internal.systemui.navbar.gestural_wide_back";

    private static final String NAV_BAR_MODE_GESTURAL_OVERLAY_EXTRA_WIDE_BACK
            = "com.android.internal.systemui.navbar.gestural_extra_wide_back";

    private static final String[] NAVBAR_MODES_OVERLAYS = {
            NAV_BAR_MODE_2BUTTON_OVERLAY,
            NAV_BAR_MODE_3BUTTON_OVERLAY,
            NAV_BAR_MODE_GESTURAL_OVERLAY,
            NAV_BAR_MODE_GESTURAL_OVERLAY_NARROW_BACK,
            NAV_BAR_MODE_GESTURAL_OVERLAY_WIDE_BACK,
            NAV_BAR_MODE_GESTURAL_OVERLAY_EXTRA_WIDE_BACK
    };

    public static String getNavigationBarModeOverlay(Context context, IOverlayManager overlayManager) {
        for (int i = 0; i < NAVBAR_MODES_OVERLAYS.length; i++) {
            OverlayInfo info = null;
            try {
                info = overlayManager.getOverlayInfo(NAVBAR_MODES_OVERLAYS[i], UserHandle.USER_CURRENT);
            } catch (Exception e) { /* Do nothing */ }
            if (info != null && info.isEnabled()) {
                return info.getPackageName();
            }
        }
        return "";
    }

    public static boolean hasNavbarByDefault(Context context) {
        boolean needsNav = context.getResources().getBoolean(com.android.internal.R.bool.config_showNavigationBar);
        String navBarOverride = SystemProperties.get("qemu.hw.mainkeys");
        if ("1".equals(navBarOverride)) {
            needsNav = false;
        } else if ("0".equals(navBarOverride)) {
            needsNav = true;
        }
        return needsNav;
    }

    public static boolean isEnabled(Context context) {
        return Settings.System.getIntForUser(context.getContentResolver(),
        "navigation_bar_show", hasNavbarByDefault(context) ? 1 : 0, UserHandle.USER_CURRENT) != 0;
    }

    public static void setEnabled(Context context, boolean enabled) {
        if (!canDisable(context)){
            return;
        }
        Settings.System.putIntForUser(context.getContentResolver(),
        "navigation_bar_show", enabled ? 1 : 0, UserHandle.USER_CURRENT);
    }

    public static boolean canDisable(Context context) {
        boolean canForceDisable = context.getResources().getBoolean(com.android.internal.R.bool.config_canForceDisableNavigationBar);
        if (canForceDisable){
            return true;
        }
        LineageHardwareManager lineageHardware = LineageHardwareManager.getInstance(context);
        if (lineageHardware != null && lineageHardware.isSupported(LineageHardwareManager.FEATURE_KEY_DISABLE)) {
            return true;
        }
        final int deviceKeys = context.getResources().getInteger(
                com.android.internal.R.integer.config_deviceHardwareKeys);
        final boolean hasHomeKey = (deviceKeys & KEY_MASK_HOME) != 0;
        final boolean hasBackKey = (deviceKeys & KEY_MASK_BACK) != 0;
        return hasHomeKey && hasBackKey;
    }
}
