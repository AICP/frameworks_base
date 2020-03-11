/*
 *  Copyright (C) 2018 The OmniROM Project
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
package com.android.internal.util.aicp;

import android.content.Context;
import android.media.AudioAttributes;
import android.os.UserHandle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.provider.Settings.Global;
import android.view.HapticFeedbackConstants;
//import com.android.internal.util.omni.DeviceUtils;

public class AicpVibe{


    // Vibrator pattern for haptic feedback of a long press.
    private static long[] mLongPressVibePattern;

    // Vibrator pattern for a short vibration when tapping on a day/month/year date of a Calendar.
    private static long[] mCalendarDateVibePattern;

    // Vibrator pattern for haptic feedback during boot when safe mode is enabled.
    private static long[] mSafeModeEnabledVibePattern;

    public static void AicpVibe(){
    }

    public static boolean performHapticFeedbackLw(int effectId, boolean always, Context mContext) {
        final boolean hapticsDisabled = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.HAPTIC_FEEDBACK_ENABLED, 0, UserHandle.USER_CURRENT) == 0;
        if (hapticsDisabled && !always) {
            return false;
        }

        VibrationEffect effect = getVibrationEffect(effectId);
        if (effect == null) {
            return false;
        }

        int owningUid;
        String owningPackage;
        owningUid = android.os.Process.myUid();
        owningPackage = mContext.getOpPackageName();
        final AudioAttributes VIBRATION_ATTRIBUTES = new AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .build();
        Vibrator mVibrator = (Vibrator) mContext.getSystemService(Context.VIBRATOR_SERVICE);
        if (mVibrator.hasVibrator()){
            //mVibrator.vibrate(owningUid, owningPackage, effect, VIBRATION_ATTRIBUTES);
        }
        return true;
    }

    private static VibrationEffect getVibrationEffect(int effectId) {
        long[] pattern;
        switch (effectId) {
            case HapticFeedbackConstants.CLOCK_TICK:
            case HapticFeedbackConstants.CONTEXT_CLICK:
                return VibrationEffect.get(VibrationEffect.EFFECT_TICK);
            case HapticFeedbackConstants.KEYBOARD_RELEASE:
            case HapticFeedbackConstants.TEXT_HANDLE_MOVE:
            case HapticFeedbackConstants.VIRTUAL_KEY_RELEASE:
            case HapticFeedbackConstants.ENTRY_BUMP:
            case HapticFeedbackConstants.DRAG_CROSSING:
            case HapticFeedbackConstants.GESTURE_END:
                return VibrationEffect.get(VibrationEffect.EFFECT_TICK, false);
            case HapticFeedbackConstants.KEYBOARD_TAP: // == KEYBOARD_PRESS
            case HapticFeedbackConstants.VIRTUAL_KEY:
            case HapticFeedbackConstants.EDGE_RELEASE:
            case HapticFeedbackConstants.CONFIRM:
            case HapticFeedbackConstants.GESTURE_START:
                return VibrationEffect.get(VibrationEffect.EFFECT_CLICK);
            case HapticFeedbackConstants.LONG_PRESS:
            case HapticFeedbackConstants.EDGE_SQUEEZE:
                return VibrationEffect.get(VibrationEffect.EFFECT_HEAVY_CLICK);
            case HapticFeedbackConstants.REJECT:
                return VibrationEffect.get(VibrationEffect.EFFECT_DOUBLE_CLICK);

            case HapticFeedbackConstants.CALENDAR_DATE:
                pattern = mCalendarDateVibePattern;
                break;
            case HapticFeedbackConstants.SAFE_MODE_ENABLED:
                pattern = mSafeModeEnabledVibePattern;
                break;

            default:
                return null;
        }
        if (pattern.length == 0) {
            // No vibration
            return null;
        } else if (pattern.length == 1) {
            // One-shot vibration
            return VibrationEffect.createOneShot(pattern[0], VibrationEffect.DEFAULT_AMPLITUDE);
        } else {
            // Pattern vibration
            return VibrationEffect.createWaveform(pattern, -1);
        }
    }
}
