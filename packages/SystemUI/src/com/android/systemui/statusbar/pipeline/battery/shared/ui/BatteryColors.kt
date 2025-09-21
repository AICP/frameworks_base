/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the License);
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an AS IS BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.statusbar.pipeline.battery.shared.ui

import androidx.compose.ui.graphics.Color
import com.android.systemui.statusbar.pipeline.battery.shared.ui.BatteryColors.LightTheme.Charging
import com.android.systemui.statusbar.pipeline.battery.shared.ui.BatteryColors.LightTheme.Error
import com.android.systemui.statusbar.pipeline.battery.shared.ui.BatteryColors.LightTheme.PowerSave

sealed interface BatteryColors {
    /** Foreground color for battery glyphs (e.g. percentage or charging bolt) */
    val glyph: Color
    /** Foreground color for the filled portion representing the level */
    val fill: Color
    /**
     * Background color for when there are no glyphs. Should provide better contrast to the [fill]
     * color to improve the ability to glance at the level
     *
     * Note that the [Charging] and [PowerSave] states will always have a foreground glyph, so they
     * will not use this color.
     */
    val backgroundOnly: Color

    /** Background color suitable for providing contrast with the [glyph] color */
    val backgroundWithGlyph: Color

    /** The layered attribution. Should match the tint of other status bar icons */
    val attribution: Color

    /**
     * Light theme: light background, dark icons
     *
     * For color profiles that are non-default ([Charging], [Error], [PowerSave]), the foreground
     * [glyph] color is darker. This means that we want to use the low-alpha background definition
     * for the backgrounds. Using the low alpha variant will mix with a lighter background more and
     * thus allow for higher contrast with the darker [glyph] colors.
     */
    sealed class LightTheme : BatteryColors {
        override val attribution = Color.Black
        override val glyph = Color.Black.copy(alpha = 0.75f)
        override val backgroundOnly = lowAlphaBg
        override val backgroundWithGlyph = lowAlphaBg

        data object Default : LightTheme() {
            override val glyph = Color.White.copy(alpha = 0.9f)
            override val fill = Color.Black

            /** Use a higher opacity here because the foreground is white */
            override val backgroundWithGlyph = highAlphaBg
        }

        data object Charging : LightTheme() {
            override val fill = Color(0xFF18CC47)
        }

        data object Error : LightTheme() {
            override val fill = Color(0xFFFF0E01)
        }

        data object PowerSave : LightTheme() {
            override val fill = Color(0xFFFFC917)
        }

        companion object {
            private val lowAlphaBg = Color.Black.copy(alpha = 0.20f)
            private val highAlphaBg = Color.Black.copy(alpha = 0.55f)
        }
    }

    /**
     * Dark theme: dark background, light icons
     *
     * Similar to the light theme, the non-default ([Charging], [Error], [PowerSave]) colors use a
     * darker [glyph] color. But since these icons will be drawn onto darker backgrounds, we use the
     * opposite approach for the background, choosing the higher alpha variants to maximize
     * contrast.
     */
    sealed class DarkTheme : BatteryColors {
        override val attribution = Color.White
        override val backgroundOnly = lowAlphaBg
        override val backgroundWithGlyph = highAlphaBg
        override val glyph = Color.Black.copy(alpha = 0.75f)

        data object Default : DarkTheme() {
            override val fill = Color.White
        }

        data object Charging : DarkTheme() {
            override val fill = Color(0xFF18CC47)
        }

        data object Error : DarkTheme() {
            override val fill = Color(0xFFFF0E01)
        }

        data object PowerSave : DarkTheme() {
            override val fill = Color(0xFFFFC917)
        }

        companion object {
            private val lowAlphaBg = Color.White.copy(alpha = 0.45f)
            private val highAlphaBg = Color.White.copy(alpha = 0.55f)
        }
    }

    /** Accent color theme for light mode */
    class AccentLightTheme(private val accentColor: Color) : LightTheme() {
        override val attribution = accentColor
        override val glyph = accentColor.copy(alpha = 0.75f)
        override val fill = accentColor
        override val backgroundOnly = accentColor.copy(alpha = 0.20f)
        override val backgroundWithGlyph = accentColor.copy(alpha = 0.55f)
    }

    /** Accent color theme for dark mode */
    class AccentDarkTheme(private val accentColor: Color) : DarkTheme() {
        override val attribution = accentColor
        override val glyph = accentColor.copy(alpha = 0.75f)
        override val fill = accentColor
        override val backgroundOnly = accentColor.copy(alpha = 0.45f)
        override val backgroundWithGlyph = accentColor.copy(alpha = 0.55f)
    }

    companion object {
        /** Create accent color themes from Android color int */
        fun createAccentThemes(accentColorInt: Int): Pair<LightTheme, DarkTheme> {
            val accentColor = Color(accentColorInt)
            return Pair(AccentLightTheme(accentColor), AccentDarkTheme(accentColor))
        }
    }
}
