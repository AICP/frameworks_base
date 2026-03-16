/*
 * Copyright (C) 2024 The LibreMobileOS Foundation
 * Copyright (C) 2026 Android Ice Cold Project
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

package com.android.systemui.statusbar.pipeline.wifi.ui.model

import android.app.AppGlobals
import android.provider.Settings
import androidx.annotation.DrawableRes
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.res.R
import com.android.systemui.statusbar.pipeline.wifi.shared.model.VoWifiState

sealed interface VoWifiIcon {
    data class Visible(val icon: Icon): VoWifiIcon
    object Hidden: VoWifiIcon
}

val VoWifiState.icon: VoWifiIcon
    get() = when (this) {
        is VoWifiState.Enabled -> {

            val context = AppGlobals.getInitialApplication()
            val settingValue = Settings.System.getInt(
                context.contentResolver,
                "vowifi_icon_style",
                0
            )

            @DrawableRes
            val ic = if (activeSubCount == 2) {
                if (slots.size >= 2) {
                    R.drawable.ic_vowifi_dual
                } else {
                    val id = slots.firstOrNull() ?: -1
                    if (id == 0) {
                        // Sim 1
                        R.drawable.ic_vowifi_one
                    } else if (id == 1) {
                        // Sim 2
                        R.drawable.ic_vowifi_two
                    } else {
                        0
                    }
                }
            } else {
                when (settingValue) {
                    1 -> R.drawable.ic_vowifi_oneplus
                    2 -> R.drawable.ic_vowifi_moto
                    3 -> R.drawable.ic_vowifi_asus
                    4 -> R.drawable.ic_vowifi_emui
                    5 -> R.drawable.ic_vowifi_simple1
                    6 -> R.drawable.ic_vowifi_simple2
                    7 -> R.drawable.ic_vowifi_simple3
                    8 -> R.drawable.ic_vowifi_vivo
                    9 -> R.drawable.ic_vowifi_margaritov
                    else -> R.drawable.ic_vowifi
                }
            }

            if (ic == 0) {
                VoWifiIcon.Hidden
            } else {
                VoWifiIcon.Visible(
                    Icon.Resource(ic, null)
                )
            }
        }
        else -> VoWifiIcon.Hidden
    }
