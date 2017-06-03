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

package android.provider.settings.backup;

import android.compat.annotation.UnsupportedAppUsage;
import android.provider.Settings;

/** Information about the system settings to back up */
public class SystemSettings {

    /**
     * Settings to backup.
     *
     * NOTE: Settings are backed up and restored in the order they appear
     *       in this array. If you have one setting depending on another,
     *       make sure that they are ordered appropriately.
     */
    @UnsupportedAppUsage
    public static final String[] SETTINGS_TO_BACKUP = {
        Settings.System.STAY_ON_WHILE_PLUGGED_IN,   // moved to global
        Settings.System.WIFI_USE_STATIC_IP,
        Settings.System.WIFI_STATIC_IP,
        Settings.System.WIFI_STATIC_GATEWAY,
        Settings.System.WIFI_STATIC_NETMASK,
        Settings.System.WIFI_STATIC_DNS1,
        Settings.System.WIFI_STATIC_DNS2,
        Settings.System.BLUETOOTH_DISCOVERABILITY,
        Settings.System.BLUETOOTH_DISCOVERABILITY_TIMEOUT,
        Settings.System.FONT_SCALE,
        Settings.System.DIM_SCREEN,
        Settings.System.SCREEN_OFF_TIMEOUT,
        Settings.System.SCREEN_BRIGHTNESS_MODE,
        Settings.System.SCREEN_AUTO_BRIGHTNESS_ADJ,
        Settings.System.SCREEN_BRIGHTNESS_FOR_VR,
        Settings.System.ADAPTIVE_SLEEP,             // moved to secure
        Settings.System.VIBRATE_INPUT_DEVICES,
        Settings.System.MODE_RINGER_STREAMS_AFFECTED,
        Settings.System.TEXT_AUTO_REPLACE,
        Settings.System.TEXT_AUTO_CAPS,
        Settings.System.TEXT_AUTO_PUNCTUATE,
        Settings.System.TEXT_SHOW_PASSWORD,
        Settings.System.AUTO_TIME,                  // moved to global
        Settings.System.AUTO_TIME_ZONE,             // moved to global
        Settings.System.TIME_12_24,
        Settings.System.DTMF_TONE_WHEN_DIALING,
        Settings.System.DTMF_TONE_TYPE_WHEN_DIALING,
        Settings.System.HEARING_AID,
        Settings.System.TTY_MODE,
        Settings.System.MASTER_MONO,
        Settings.System.MASTER_BALANCE,
        Settings.System.SOUND_EFFECTS_ENABLED,
        Settings.System.HAPTIC_FEEDBACK_ENABLED,
        Settings.System.POWER_SOUNDS_ENABLED,       // moved to global
        Settings.System.DOCK_SOUNDS_ENABLED,        // moved to global
        Settings.System.LOCKSCREEN_SOUNDS_ENABLED,
        Settings.System.SHOW_WEB_SUGGESTIONS,
        Settings.System.SIP_CALL_OPTIONS,
        Settings.System.SIP_RECEIVE_CALLS,
        Settings.System.POINTER_SPEED,
        Settings.System.VIBRATE_WHEN_RINGING,
        Settings.System.RINGTONE,
        Settings.System.LOCK_TO_APP_ENABLED,
        Settings.System.NOTIFICATION_SOUND,
        Settings.System.ACCELEROMETER_ROTATION,
        Settings.System.SHOW_BATTERY_PERCENT,
        Settings.System.NOTIFICATION_VIBRATION_INTENSITY,
        Settings.System.RING_VIBRATION_INTENSITY,
        Settings.System.HAPTIC_FEEDBACK_INTENSITY,
        Settings.System.DISPLAY_COLOR_MODE_VENDOR_HINT, // must precede DISPLAY_COLOR_MODE
        Settings.System.DISPLAY_COLOR_MODE,
        Settings.System.ALARM_ALERT,
        Settings.System.NOTIFICATION_LIGHT_PULSE,
        Settings.System.VOLUME_KEY_CURSOR_CONTROL,
        Settings.System.BACK_GESTURE_HAPTIC,
        Settings.System.ACCELEROMETER_ROTATION_ANGLES,
        Settings.System.AE_THEME,
        Settings.System.DOUBLE_TAP_SLEEP_GESTURE,
        Settings.System.DOUBLE_TAP_SLEEP_LOCKSCREEN,
        Settings.System.LOCKSCREEN_BATTERY_INFO,
        Settings.System.POWERMENU_SOUNDPANEL,
        Settings.System.POWERMENU_SCREENSHOT,
        Settings.System.POWERMENU_SETTINGS,
        Settings.System.POWERMENU_LOCKDOWN,
        Settings.System.POWERMENU_AIRPLANE,
        Settings.System.POWERMENU_ADVANCED,
        Settings.System.POWERMENU_USERS,
        Settings.System.POWERMENU_LOGOUT,
        Settings.System.POWERMENU_EMERGENCY,
        Settings.System.POWERMENU_TORCH,
        Settings.System.DOZE_TILT_GESTURE,
        Settings.System.DOZE_HANDWAVE_GESTURE,
        Settings.System.DOZE_POCKET_GESTURE,
        Settings.System.OMNI_DEVICE_PROXI_CHECK_ENABLED,
        Settings.System.OMNI_DEVICE_FEATURE_SETTINGS,
        Settings.System.OMNI_BUTTON_EXTRA_KEY_MAPPING,
        Settings.System.OMNI_SYSTEM_PROXI_CHECK_ENABLED,
        Settings.System.ALERT_SLIDER_NOTIFICATIONS,
        Settings.System.SHOW_BATTERY_IMAGE,
        Settings.System.STATUSBAR_BATTERY_BAR,
        Settings.System.STATUSBAR_BATTERY_BAR_COLOR,
        Settings.System.STATUSBAR_BATTERY_BAR_THICKNESS,
        Settings.System.STATUSBAR_BATTERY_BAR_STYLE,
        Settings.System.STATUSBAR_BATTERY_BAR_ANIMATE,
        Settings.System.STATUSBAR_BATTERY_BAR_LOCATION,
        Settings.System.STATUS_BAR_QUICK_QS_PULLDOWN,
        Settings.System.THREE_FINGER_GESTURE,
        Settings.System.NOTIFICATION_SOUND_VIB_SCREEN_ON,
        Settings.System.MUTE_ANNOYING_NOTIFICATIONS_THRESHOLD,
        Settings.System.LOCKSCREEN_PIN_SCRAMBLE_LAYOUT,
        Settings.System.QS_SMART_PULLDOWN,
        Settings.System.LESS_BORING_HEADS_UP,
        Settings.System.STATUSBAR_COLORED_ICONS,
        Settings.System.STATUSBAR_NOTIF_COUNT,
        Settings.System.QS_TILE_VERTICAL_LAYOUT,
        Settings.System.QS_LAYOUT_COLUMNS_LANDSCAPE,
        Settings.System.QS_LAYOUT_COLUMNS,
        Settings.System.QS_TILE_LABEL_HIDE,
        Settings.System.NAVIGATION_BAR_ARROW_KEYS,
        Settings.System.LOCKSCREEN_SMALL_CLOCK,
        Settings.System.AICP_ASPECT_RATIO_APPS_LIST,
        Settings.System.AICP_ASPECT_RATIO_APPS_ENABLED,
        Settings.System.LOCK_HIDE_STATUS_BAR,
        Settings.System.MUSIC_TILE_TITLE,
        Settings.System.CHARGING_ANIMATION,
        Settings.System.DOZE_ON_CHARGE,
        Settings.System.DOZE_ON_CHARGE_NOW,
        Settings.System.STATUS_BAR_BRIGHTNESS_CONTROL,
        Settings.System.FINGERPRINT_SUCCESS_VIB,
        Settings.System.SCREEN_OFF_ANIMATION,
        Settings.System.STATUS_BAR_LOGO,
        Settings.System.STATUS_BAR_LOGO_COLOR,
        Settings.System.STATUS_BAR_LOGO_POSITION,
        Settings.System.STATUS_BAR_LOGO_STYLE,
        Settings.System.STATUS_BAR_LOGO_COLOR_ACCENT,
        Settings.System.NETWORK_TRAFFIC_STATE,
        Settings.System.NETWORK_TRAFFIC_AUTOHIDE_THRESHOLD,
        Settings.System.SENSOR_BLOCK,
        Settings.System.NETWORK_TRAFFIC_EXPANDED_STATUS_BAR_STATE,
        Settings.System.GLOBAL_ACTIONS_ONTHEGO,
        Settings.System.ON_THE_GO_SERVICE_RESTART,
        Settings.System.ON_THE_GO_CAMERA,
        Settings.System.ON_THE_GO_ALPHA,
        Settings.System.USE_SLIM_RECENTS,
        Settings.System.RECENTS_MAX_APPS,
        Settings.System.RECENT_PANEL_GRAVITY,
        Settings.System.RECENT_PANEL_SCALE_FACTOR,
        Settings.System.RECENT_PANEL_FAVORITES,
        Settings.System.RECENT_PANEL_EXPANDED_MODE,
        Settings.System.RECENT_PANEL_BG_COLOR,
        Settings.System.RECENT_CARD_BG_COLOR,
        Settings.System.SLIM_RECENT_AICP_EMPTY_DRAWABLE,
        Settings.System.USE_RECENT_APP_SIDEBAR,
        Settings.System.RECENT_APP_SIDEBAR_CONTENT,
        Settings.System.RECENT_APP_SIDEBAR_DISABLE_LABELS,
        Settings.System.RECENT_APP_SIDEBAR_BG_COLOR,
        Settings.System.RECENT_APP_SIDEBAR_TEXT_COLOR,
        Settings.System.RECENT_APP_SIDEBAR_SCALE_FACTOR,
        Settings.System.RECENT_APP_SIDEBAR_OPEN_SIMULTANEOUSLY,
        Settings.System.SLIM_RECENTS_MEM_DISPLAY,
        Settings.System.SLIM_RECENTS_MEM_DISPLAY_LONG_CLICK_CLEAR,
        Settings.System.SLIM_RECENTS_ICON_PACK,
        Settings.System.SLIM_MEM_BAR_COLOR,
        Settings.System.SLIM_MEM_TEXT_COLOR,
        Settings.System.SLIM_RECENTS_CORNER_RADIUS,
        Settings.System.SLIM_RECENTS_BLACKLIST_VALUES,
        Settings.System.SLIM_RECENT_ENTER_EXIT_ANIMATION,
        Settings.System.ANBI_ENABLED_OPTION,
        Settings.System.TORCH_LONG_PRESS_POWER_GESTURE,
        Settings.System.TORCH_LONG_PRESS_POWER_TIMEOUT,
        Settings.System.VOLUME_ROCKER_WAKE,
        Settings.System.VOLUME_BUTTON_MUSIC_CONTROL,
        Settings.System.HOME_WAKE_SCREEN,
        Settings.System.BACK_WAKE_SCREEN,
        Settings.System.MENU_WAKE_SCREEN,
        Settings.System.ASSIST_WAKE_SCREEN,
        Settings.System.APP_SWITCH_WAKE_SCREEN,
        Settings.System.CAMERA_WAKE_SCREEN,
        Settings.System.POCKET_JUDGE,
        Settings.System.TOAST_ANIMATION,
        Settings.System.SWAP_VOLUME_BUTTONS,
        Settings.System.VOLUME_KEYS_CONTROL_RING_TONE,
        Settings.System.BUTTON_BACKLIGHT_TIMEOUT,
        Settings.System.BUTTON_BRIGHTNESS,
        Settings.System.BUTTON_BACKLIGHT_ONLY_WHEN_PRESSED,
    };
}
