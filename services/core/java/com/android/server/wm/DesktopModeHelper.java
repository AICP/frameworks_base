/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.server.wm;

import android.annotation.NonNull;
import android.content.Context;
import android.os.SystemProperties;
import android.provider.Settings;
import android.window.DesktopExperienceFlags;
import android.window.DesktopModeFlags;

import com.android.internal.R;
import com.android.internal.annotations.VisibleForTesting;
import com.android.window.flags.Flags;

/**
 * Constants for desktop mode feature
 */
public final class DesktopModeHelper {

    /**
     * Flag to indicate whether to restrict desktop mode to supported devices.
     */
    private static final boolean ENFORCE_DEVICE_RESTRICTIONS = SystemProperties.getBoolean(
            "persist.wm.debug.desktop_mode_enforce_device_restrictions", true);

    /** Whether desktop mode is enabled. */
    private static boolean isDesktopModeEnabled() {
        return DesktopModeFlags.ENABLE_DESKTOP_WINDOWING_MODE.isTrue();
    }

    /**
     * Return {@code true} if desktop mode should be restricted to supported devices.
     */
    @VisibleForTesting
    static boolean shouldEnforceDeviceRestrictions() {
        return ENFORCE_DEVICE_RESTRICTIONS;
    }

    /**
     * Return {@code true} if the current device supports desktop mode.
     */
    // TODO(b/337819319): use a companion object instead.
    private static boolean isDesktopModeSupported(@NonNull Context context) {
        return context.getResources().getBoolean(R.bool.config_isDesktopModeSupported);
    }

    static boolean isDesktopModeDevOptionsSupported(@NonNull Context context) {
        return context.getResources().getBoolean(R.bool.config_isDesktopModeDevOptionSupported);
    }

    /**
     * Return {@code true} if desktop mode is unrestricted on the current device or it can host
     * desktop sessions on its internal display.
     */
    public static boolean isDesktopModeSupportedOnInternalDisplay(@NonNull Context context) {
        return !shouldEnforceDeviceRestrictions() ||  canInternalDisplayHostDesktops(context);
    }

    /**
     * Return {@code true} if the current device can hosts desktop sessions on its internal display.
     */
    public static boolean canInternalDisplayHostDesktops(@NonNull Context context) {
        boolean isEnabledInSettings = Settings.System.getInt(mContext.getContentResolver(),
            Settings.System.DESKTOP_MODE_INTERNAL_DISPLAY, 0) == 1;
        return mContext.getResources().getBoolean(com.android.internal.R.bool.config_canInternalDisplayHostDesktops)
           || isEnabledInSettings;
    }

    /**
     * Check if Desktop mode should be enabled because the dev option is shown and enabled.
     */
    private static boolean isDesktopModeEnabledByDevOption(@NonNull Context context) {
        return DesktopModeFlags.isDesktopModeForcedEnabled() && (isDesktopModeDevOptionsSupported(
                context) || isDeviceEligibleForDesktopMode(context));
    }

    @VisibleForTesting
    public static boolean isDeviceEligibleForDesktopMode(@NonNull Context context) {
        if (!shouldEnforceDeviceRestrictions()) {
            return true;
        }
        final boolean desktopModeSupportedByDevOptions =
                Flags.enableDesktopModeThroughDevOption()
                        && isDesktopModeDevOptionsSupported(context);
        return isDesktopModeSupported(context) || desktopModeSupportedByDevOptions;
    }

    /**
     * Return {@code true} if desktop mode can be entered on the current device.
     */
    public static boolean canEnterDesktopMode(@NonNull Context context) {
        return (isDeviceEligibleForDesktopMode(context)
                && DesktopModeFlags.ENABLE_DESKTOP_WINDOWING_MODE.isTrue())
                || isDesktopModeEnabledByDevOption(context);
    }

    /** Returns {@code true} if desktop experience wallpaper is supported on this device. */
    public static boolean isDeviceEligibleForDesktopExperienceWallpaper(@NonNull Context context) {
        return DesktopExperienceFlags.ENABLE_CONNECTED_DISPLAYS_WALLPAPER.isTrue()
                && isDeviceEligibleForDesktopMode(context);
    }

    /** Returns {@code true} if the desktop experience developer option should be shown. */
    public static boolean isDesktopExperienceDevOptionSupported(@NonNull Context context) {
        return Flags.showDesktopExperienceDevOption() && isDeviceEligibleForDesktopMode(context);
    }
}
