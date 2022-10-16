/*
 * Copyright (C) 2019 The Android Open Source Project
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

package android.provider.settings.validators;

import static android.provider.settings.validators.SettingsValidators.ANY_STRING_VALIDATOR;
import static android.provider.settings.validators.SettingsValidators.BOOLEAN_VALIDATOR;
import static android.provider.settings.validators.SettingsValidators.COMPONENT_NAME_VALIDATOR;
import static android.provider.settings.validators.SettingsValidators.LENIENT_IP_ADDRESS_VALIDATOR;
import static android.provider.settings.validators.SettingsValidators.ANY_INTEGER_VALIDATOR;
import static android.provider.settings.validators.SettingsValidators.NON_NEGATIVE_INTEGER_VALIDATOR;
import static android.provider.settings.validators.SettingsValidators.URI_VALIDATOR;
import static android.provider.settings.validators.SettingsValidators.VIBRATION_INTENSITY_VALIDATOR;

import android.annotation.Nullable;
import android.compat.annotation.UnsupportedAppUsage;
import android.content.ComponentName;
import android.hardware.display.ColorDisplayManager;
import android.os.BatteryManager;
import android.provider.Settings.System;
import android.util.ArrayMap;
import android.text.TextUtils;

import java.util.Map;

/**
 * Validators for System settings
 */
public class SystemSettingsValidators {
    @UnsupportedAppUsage
    public static final Map<String, Validator> VALIDATORS = new ArrayMap<>();

    static {
        VALIDATORS.put(
                System.STAY_ON_WHILE_PLUGGED_IN,
                value -> {
                    try {
                        int val = Integer.parseInt(value);
                        return (val == 0)
                                || (val == BatteryManager.BATTERY_PLUGGED_AC)
                                || (val == BatteryManager.BATTERY_PLUGGED_USB)
                                || (val == BatteryManager.BATTERY_PLUGGED_WIRELESS)
                                || (val
                                        == (BatteryManager.BATTERY_PLUGGED_AC
                                                | BatteryManager.BATTERY_PLUGGED_USB))
                                || (val
                                        == (BatteryManager.BATTERY_PLUGGED_AC
                                                | BatteryManager.BATTERY_PLUGGED_WIRELESS))
                                || (val
                                        == (BatteryManager.BATTERY_PLUGGED_USB
                                                | BatteryManager.BATTERY_PLUGGED_WIRELESS))
                                || (val
                                        == (BatteryManager.BATTERY_PLUGGED_AC
                                                | BatteryManager.BATTERY_PLUGGED_USB
                                                | BatteryManager.BATTERY_PLUGGED_WIRELESS));
                    } catch (NumberFormatException e) {
                        return false;
                    }
                });
        VALIDATORS.put(System.END_BUTTON_BEHAVIOR, new InclusiveIntegerRangeValidator(0, 3));
        VALIDATORS.put(System.WIFI_USE_STATIC_IP, BOOLEAN_VALIDATOR);
        VALIDATORS.put(System.BLUETOOTH_DISCOVERABILITY, new InclusiveIntegerRangeValidator(0, 2));
        VALIDATORS.put(System.BLUETOOTH_DISCOVERABILITY_TIMEOUT, NON_NEGATIVE_INTEGER_VALIDATOR);
        VALIDATORS.put(
                System.NEXT_ALARM_FORMATTED,
                new Validator() {
                    private static final int MAX_LENGTH = 1000;

                    @Override
                    public boolean validate(String value) {
                        // TODO: No idea what the correct format is.
                        return value == null || value.length() < MAX_LENGTH;
                    }
                });
        VALIDATORS.put(System.FONT_SCALE, new InclusiveFloatRangeValidator(0.25f, 5.0f));
        VALIDATORS.put(System.DIM_SCREEN, BOOLEAN_VALIDATOR);
        VALIDATORS.put(
                System.DISPLAY_COLOR_MODE,
                new Validator() {
                    @Override
                    public boolean validate(@Nullable String value) {
                        // Assume the actual validation that this device can properly handle this
                        // kind of
                        // color mode further down in ColorDisplayManager / ColorDisplayService.
                        try {
                            final int setting = Integer.parseInt(value);
                            final boolean isInFrameworkRange =
                                    setting >= ColorDisplayManager.COLOR_MODE_NATURAL
                                            && setting <= ColorDisplayManager.COLOR_MODE_AUTOMATIC;
                            final boolean isInVendorRange =
                                    setting >= ColorDisplayManager.VENDOR_COLOR_MODE_RANGE_MIN
                                            && setting
                                                    <= ColorDisplayManager
                                                            .VENDOR_COLOR_MODE_RANGE_MAX;
                            return isInFrameworkRange || isInVendorRange;
                        } catch (NumberFormatException | NullPointerException e) {
                            return false;
                        }
                    }
                });
        VALIDATORS.put(System.DISPLAY_COLOR_MODE_VENDOR_HINT, ANY_STRING_VALIDATOR);
        VALIDATORS.put(System.SCREEN_OFF_TIMEOUT, NON_NEGATIVE_INTEGER_VALIDATOR);
        VALIDATORS.put(System.SCREEN_BRIGHTNESS_FOR_VR, new InclusiveIntegerRangeValidator(0, 255));
        VALIDATORS.put(System.SCREEN_BRIGHTNESS_MODE, BOOLEAN_VALIDATOR);
        VALIDATORS.put(System.ADAPTIVE_SLEEP, BOOLEAN_VALIDATOR);
        VALIDATORS.put(System.MODE_RINGER_STREAMS_AFFECTED, NON_NEGATIVE_INTEGER_VALIDATOR);
        VALIDATORS.put(System.MUTE_STREAMS_AFFECTED, NON_NEGATIVE_INTEGER_VALIDATOR);
        VALIDATORS.put(System.VIBRATE_ON, BOOLEAN_VALIDATOR);
        VALIDATORS.put(System.APPLY_RAMPING_RINGER, BOOLEAN_VALIDATOR);
        VALIDATORS.put(System.ALARM_VIBRATION_INTENSITY, VIBRATION_INTENSITY_VALIDATOR);
        VALIDATORS.put(System.MEDIA_VIBRATION_INTENSITY, VIBRATION_INTENSITY_VALIDATOR);
        VALIDATORS.put(System.NOTIFICATION_VIBRATION_INTENSITY, VIBRATION_INTENSITY_VALIDATOR);
        VALIDATORS.put(System.RING_VIBRATION_INTENSITY, VIBRATION_INTENSITY_VALIDATOR);
        VALIDATORS.put(System.HAPTIC_FEEDBACK_INTENSITY, VIBRATION_INTENSITY_VALIDATOR);
        VALIDATORS.put(System.HARDWARE_HAPTIC_FEEDBACK_INTENSITY, VIBRATION_INTENSITY_VALIDATOR);
        VALIDATORS.put(System.HAPTIC_FEEDBACK_ENABLED, BOOLEAN_VALIDATOR);
        VALIDATORS.put(System.RINGTONE, URI_VALIDATOR);
        VALIDATORS.put(System.NOTIFICATION_SOUND, URI_VALIDATOR);
        VALIDATORS.put(System.ALARM_ALERT, URI_VALIDATOR);
        VALIDATORS.put(System.TEXT_AUTO_REPLACE, BOOLEAN_VALIDATOR);
        VALIDATORS.put(System.TEXT_AUTO_CAPS, BOOLEAN_VALIDATOR);
        VALIDATORS.put(System.TEXT_AUTO_PUNCTUATE, BOOLEAN_VALIDATOR);
        VALIDATORS.put(System.TEXT_SHOW_PASSWORD, BOOLEAN_VALIDATOR);
        VALIDATORS.put(System.AUTO_TIME, BOOLEAN_VALIDATOR);
        VALIDATORS.put(System.AUTO_TIME_ZONE, BOOLEAN_VALIDATOR);
        VALIDATORS.put(System.SHOW_GTALK_SERVICE_STATUS, BOOLEAN_VALIDATOR);
        VALIDATORS.put(
                System.WALLPAPER_ACTIVITY,
                new Validator() {
                    private static final int MAX_LENGTH = 1000;

                    @Override
                    public boolean validate(String value) {
                        if (value != null && value.length() > MAX_LENGTH) {
                            return false;
                        }
                        return ComponentName.unflattenFromString(value) != null;
                    }
                });
        VALIDATORS.put(
                System.TIME_12_24, new DiscreteValueValidator(new String[] {"12", "24", null}));
        VALIDATORS.put(System.SETUP_WIZARD_HAS_RUN, BOOLEAN_VALIDATOR);
        VALIDATORS.put(System.ACCELEROMETER_ROTATION, BOOLEAN_VALIDATOR);
        VALIDATORS.put(System.USER_ROTATION, new InclusiveIntegerRangeValidator(0, 3));
        VALIDATORS.put(System.DTMF_TONE_WHEN_DIALING, BOOLEAN_VALIDATOR);
        VALIDATORS.put(System.SOUND_EFFECTS_ENABLED, BOOLEAN_VALIDATOR);
        VALIDATORS.put(System.HAPTIC_FEEDBACK_ENABLED, BOOLEAN_VALIDATOR);
        VALIDATORS.put(System.POWER_SOUNDS_ENABLED, BOOLEAN_VALIDATOR);
        VALIDATORS.put(System.DOCK_SOUNDS_ENABLED, BOOLEAN_VALIDATOR);
        VALIDATORS.put(System.SHOW_WEB_SUGGESTIONS, BOOLEAN_VALIDATOR);
        VALIDATORS.put(System.WIFI_USE_STATIC_IP, BOOLEAN_VALIDATOR);
        VALIDATORS.put(System.ADVANCED_SETTINGS, BOOLEAN_VALIDATOR);
        VALIDATORS.put(System.SCREEN_AUTO_BRIGHTNESS_ADJ, new InclusiveFloatRangeValidator(-1, 1));
        VALIDATORS.put(System.VIBRATE_INPUT_DEVICES, BOOLEAN_VALIDATOR);
        VALIDATORS.put(System.MASTER_MONO, BOOLEAN_VALIDATOR);
        VALIDATORS.put(System.MASTER_BALANCE, new InclusiveFloatRangeValidator(-1.f, 1.f));
        VALIDATORS.put(System.NOTIFICATIONS_USE_RING_VOLUME, BOOLEAN_VALIDATOR);
        VALIDATORS.put(System.VIBRATE_IN_SILENT, BOOLEAN_VALIDATOR);
        VALIDATORS.put(System.MEDIA_BUTTON_RECEIVER, COMPONENT_NAME_VALIDATOR);
        VALIDATORS.put(System.HIDE_ROTATION_LOCK_TOGGLE_FOR_ACCESSIBILITY, BOOLEAN_VALIDATOR);
        VALIDATORS.put(System.VIBRATE_WHEN_RINGING, BOOLEAN_VALIDATOR);
        VALIDATORS.put(System.DTMF_TONE_TYPE_WHEN_DIALING, BOOLEAN_VALIDATOR);
        VALIDATORS.put(System.HEARING_AID, BOOLEAN_VALIDATOR);
        VALIDATORS.put(System.TTY_MODE, new InclusiveIntegerRangeValidator(0, 3));
        VALIDATORS.put(System.NOTIFICATION_LIGHT_PULSE, BOOLEAN_VALIDATOR);
        VALIDATORS.put(System.POINTER_LOCATION, BOOLEAN_VALIDATOR);
        VALIDATORS.put(System.SHOW_TOUCHES, BOOLEAN_VALIDATOR);
        VALIDATORS.put(System.WINDOW_ORIENTATION_LISTENER_LOG, BOOLEAN_VALIDATOR);
        VALIDATORS.put(System.LOCKSCREEN_SOUNDS_ENABLED, BOOLEAN_VALIDATOR);
        VALIDATORS.put(System.LOCKSCREEN_DISABLED, BOOLEAN_VALIDATOR);
        VALIDATORS.put(System.SIP_RECEIVE_CALLS, BOOLEAN_VALIDATOR);
        VALIDATORS.put(
                System.SIP_CALL_OPTIONS,
                new DiscreteValueValidator(new String[] {"SIP_ALWAYS", "SIP_ADDRESS_ONLY"}));
        VALIDATORS.put(System.SIP_ALWAYS, BOOLEAN_VALIDATOR);
        VALIDATORS.put(System.SIP_ADDRESS_ONLY, BOOLEAN_VALIDATOR);
        VALIDATORS.put(System.SIP_ASK_ME_EACH_TIME, BOOLEAN_VALIDATOR);
        VALIDATORS.put(System.POINTER_SPEED, new InclusiveFloatRangeValidator(-7, 7));
        VALIDATORS.put(System.LOCK_TO_APP_ENABLED, BOOLEAN_VALIDATOR);
        VALIDATORS.put(
                System.EGG_MODE,
                new Validator() {
                    @Override
                    public boolean validate(@Nullable String value) {
                        try {
                            return Long.parseLong(value) >= 0;
                        } catch (NumberFormatException e) {
                            return false;
                        }
                    }
                });
        VALIDATORS.put(System.WIFI_STATIC_IP, LENIENT_IP_ADDRESS_VALIDATOR);
        VALIDATORS.put(System.WIFI_STATIC_GATEWAY, LENIENT_IP_ADDRESS_VALIDATOR);
        VALIDATORS.put(System.WIFI_STATIC_NETMASK, LENIENT_IP_ADDRESS_VALIDATOR);
        VALIDATORS.put(System.WIFI_STATIC_DNS1, LENIENT_IP_ADDRESS_VALIDATOR);
        VALIDATORS.put(System.WIFI_STATIC_DNS2, LENIENT_IP_ADDRESS_VALIDATOR);
        VALIDATORS.put(System.SHOW_BATTERY_PERCENT, BOOLEAN_VALIDATOR);
        VALIDATORS.put(System.NOTIFICATION_LIGHT_PULSE, BOOLEAN_VALIDATOR);
        VALIDATORS.put(System.AE_THEME, NON_NEGATIVE_INTEGER_VALIDATOR);
        VALIDATORS.put(System.ACCELEROMETER_ROTATION_ANGLES, NON_NEGATIVE_INTEGER_VALIDATOR);
        VALIDATORS.put(System.LOCKSCREEN_ROTATION, BOOLEAN_VALIDATOR);
        VALIDATORS.put(System.DISPLAY_TEMPERATURE_DAY, new InclusiveIntegerRangeValidator(0, 100000));
        VALIDATORS.put(System.DISPLAY_TEMPERATURE_NIGHT, new InclusiveIntegerRangeValidator(0, 100000));
        VALIDATORS.put(System.DISPLAY_TEMPERATURE_MODE, new InclusiveIntegerRangeValidator(0, 4));
        VALIDATORS.put(System.DISPLAY_AUTO_OUTDOOR_MODE, BOOLEAN_VALIDATOR);
        VALIDATORS.put(System.DISPLAY_READING_MODE, BOOLEAN_VALIDATOR);
        VALIDATORS.put(System.DISPLAY_CABC, BOOLEAN_VALIDATOR);
        VALIDATORS.put(System.DISPLAY_COLOR_ENHANCE, BOOLEAN_VALIDATOR);
        VALIDATORS.put(System.DISPLAY_AUTO_CONTRAST, BOOLEAN_VALIDATOR);
        VALIDATORS.put(System.DISPLAY_COLOR_ADJUSTMENT, new Validator() {
            @Override
            public boolean validate(String value) {
                String[] colorAdjustment = null;
                if (value != null) {
                    colorAdjustment = value.split(" ");
                }
                if (colorAdjustment != null && colorAdjustment.length != 3) {
                    return false;
                }
                final Validator floatValidator = new InclusiveFloatRangeValidator(0, 1);
                return colorAdjustment == null ||
                        floatValidator.validate(colorAdjustment[0]) &&
                        floatValidator.validate(colorAdjustment[1]) &&
                        floatValidator.validate(colorAdjustment[2]);
            }
        });
        VALIDATORS.put(System.DISPLAY_PICTURE_ADJUSTMENT, new Validator() {
            @Override
            public boolean validate(String value) {
                if (TextUtils.isEmpty(value)) {
                    return true;
                }
                final String[] sp = TextUtils.split(value, ",");
                for (String s : sp) {
                    final String[] sp2 = TextUtils.split(s, ":");
                    if (sp2.length != 2) {
                        return false;
                    }
                }
                return true;
            }
        });
        VALIDATORS.put(System.LIVE_DISPLAY_HINTED, new InclusiveIntegerRangeValidator(-3, 1));
        VALIDATORS.put(System.DISPLAY_ANTI_FLICKER, BOOLEAN_VALIDATOR);
        VALIDATORS.put(System.HIGH_TOUCH_POLLING_RATE_ENABLE, BOOLEAN_VALIDATOR);
        VALIDATORS.put(System.HIGH_TOUCH_SENSITIVITY_ENABLE, BOOLEAN_VALIDATOR);
        VALIDATORS.put(System.CLICK_PARTIAL_SCREENSHOT, BOOLEAN_VALIDATOR);
        VALIDATORS.put(System.QQS_SHOW_BRIGHTNESS, BOOLEAN_VALIDATOR);
        VALIDATORS.put(System.SHOW_AUTO_BRIGHTNESS_BUTTON, BOOLEAN_VALIDATOR);
        VALIDATORS.put(System.BRIGHTNESS_SLIDER_POSITION, new InclusiveIntegerRangeValidator(0, 1));
        VALIDATORS.put(System.TORCH_LONG_PRESS_POWER_GESTURE, BOOLEAN_VALIDATOR);
        VALIDATORS.put(System.TORCH_LONG_PRESS_POWER_TIMEOUT, ANY_INTEGER_VALIDATOR);
        VALIDATORS.put(System.STATUS_BAR_QUICK_QS_PULLDOWN, new InclusiveIntegerRangeValidator(0, 2));
        VALIDATORS.put(System.DOUBLE_TAP_SLEEP_GESTURE, BOOLEAN_VALIDATOR);
        VALIDATORS.put(System.NETWORK_TRAFFIC_STATE, BOOLEAN_VALIDATOR);
        VALIDATORS.put(System.NETWORK_TRAFFIC_AUTOHIDE_THRESHOLD, ANY_INTEGER_VALIDATOR);
        VALIDATORS.put(System.NETWORK_TRAFFIC_EXPANDED_STATUS_BAR_STATE, BOOLEAN_VALIDATOR);
        VALIDATORS.put(System.LOCKSCREEN_BATTERY_INFO, BOOLEAN_VALIDATOR);
        VALIDATORS.put(System.LOCKSCREEN_BATTERY_INFO_TEMP_UNIT, BOOLEAN_VALIDATOR);
        VALIDATORS.put(System.SCREEN_OFF_ANIMATION, ANY_INTEGER_VALIDATOR);
        VALIDATORS.put(System.VOLUME_KEY_CURSOR_CONTROL, NON_NEGATIVE_INTEGER_VALIDATOR);
        VALIDATORS.put(System.BACK_GESTURE_HAPTIC, BOOLEAN_VALIDATOR);
        VALIDATORS.put(System.HEADS_UP_NOTIFICATIONS_THRESHOLD, ANY_INTEGER_VALIDATOR);
        VALIDATORS.put(System.TOAST_ANIMATION, ANY_INTEGER_VALIDATOR);
        VALIDATORS.put(System.THREE_FINGER_GESTURE, BOOLEAN_VALIDATOR);
        VALIDATORS.put(System.POWERMENU_SOUNDPANEL, BOOLEAN_VALIDATOR);
        VALIDATORS.put(System.POWERMENU_SCREENSHOT, BOOLEAN_VALIDATOR);
        VALIDATORS.put(System.POWERMENU_SETTINGS, BOOLEAN_VALIDATOR);
        VALIDATORS.put(System.POWERMENU_LOCKDOWN, BOOLEAN_VALIDATOR);
        VALIDATORS.put(System.POWERMENU_AIRPLANE, BOOLEAN_VALIDATOR);
        VALIDATORS.put(System.POWERMENU_ADVANCED, BOOLEAN_VALIDATOR);
        VALIDATORS.put(System.POWERMENU_USERS, BOOLEAN_VALIDATOR);
        VALIDATORS.put(System.POWERMENU_LOGOUT, BOOLEAN_VALIDATOR);
        VALIDATORS.put(System.POWERMENU_EMERGENCY, BOOLEAN_VALIDATOR);
        VALIDATORS.put(System.POWERMENU_TORCH, BOOLEAN_VALIDATOR);
        VALIDATORS.put(System.OMNIJAWS_WEATHER_ICON_PACK, ANY_STRING_VALIDATOR);
    }
}
