/*
 * Copyright (C) 2022 FlamingoOS Project
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
 * limitations under the License
 */

package com.android.systemui.settings.brightness

import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.UserHandle
import android.provider.Settings
import android.view.View
import android.widget.ImageView

import com.android.systemui.R
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.statusbar.policy.BrightnessMirrorController
import com.android.systemui.util.settings.SystemSettings

import javax.inject.Inject

class AutoBrightnessController internal constructor(
    private val autoBrightnessIcon: ImageView,
    @Main private val mainHandler: Handler,
    @Background private val bgHandler: Handler,
    private val systemSettings: SystemSettings
) : MirroredBrightnessController {

    private val settingsObserver = object : ContentObserver(bgHandler) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            val key = uri?.lastPathSegment ?: return
            if (key == Settings.System.SCREEN_BRIGHTNESS_MODE) {
                updateIcon()
            } else if (key == Settings.System.SHOW_AUTO_BRIGHTNESS_BUTTON) {
                updateIconVisibility()
            }
        }
    }

    private var mirrorController: BrightnessMirrorController? = null
    private var callbacksRegistered = false

    init {
        updateIconAndVisibility()
    }

    private fun updateIconAndVisibility(onlyUpdateMirror: Boolean = false) {
        bgHandler.post {
            updateIconVisibility(onlyUpdateMirror)
            updateIcon(onlyUpdateMirror)
        }
    }

    override fun setMirror(controller: BrightnessMirrorController) {
        mirrorController = controller
        updateIconAndVisibility(onlyUpdateMirror = true)
    }

    private fun updateIcon(onlyUpdateMirror: Boolean = false) {
        val resId = getIconResId()
        mainHandler.post {
            if (!onlyUpdateMirror) autoBrightnessIcon.setImageResource(resId)
            mirrorController?.let {
                it.updateAutoBrightnessIcon(resId)
            }
        }
    }

    private fun updateIconVisibility(onlyUpdateMirror: Boolean = false) {
        val visible = isButtonEnabled()
        mainHandler.post {
            if (!onlyUpdateMirror) {
                autoBrightnessIcon.visibility = if (visible) View.VISIBLE else View.GONE
            }
            mirrorController?.let {
                it.setAutoBrightnessIconVisibile(visible)
            }
        }
    }

    private fun isButtonEnabled() =
        systemSettings.getIntForUser(
            Settings.System.SHOW_AUTO_BRIGHTNESS_BUTTON,
            0, UserHandle.USER_CURRENT
        ) == 1

    private fun getIconResId() =
        if (isAutoBrightnessEnabled())
            R.drawable.auto_brightness_icon_on
        else
            R.drawable.ic_brightness_full

    fun registerCallbacks() {
        if (callbacksRegistered) return
        updateIconAndVisibility()
        systemSettings.registerContentObserverForUser(
            Settings.System.SHOW_AUTO_BRIGHTNESS_BUTTON,
            settingsObserver, UserHandle.USER_ALL
        )
        systemSettings.registerContentObserverForUser(
            Settings.System.SCREEN_BRIGHTNESS_MODE,
            settingsObserver, UserHandle.USER_ALL
        )
        autoBrightnessIcon.setOnClickListener {
            bgHandler.post {
                toggleAutoBrightness()
            }
        }
        callbacksRegistered = true
    }

    private fun toggleAutoBrightness() {
        systemSettings.putIntForUser(
            Settings.System.SCREEN_BRIGHTNESS_MODE,
            if (isAutoBrightnessEnabled())
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            else
                Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC,
            UserHandle.USER_CURRENT
        )
    }

    private fun isAutoBrightnessEnabled() =
        systemSettings.getIntForUser(
            Settings.System.SCREEN_BRIGHTNESS_MODE,
            Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
            UserHandle.USER_CURRENT
        ) == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC

    fun unregisterCallbacks() {
        if (!callbacksRegistered) return
        autoBrightnessIcon.setOnClickListener(null)
        systemSettings.unregisterContentObserver(settingsObserver)
        callbacksRegistered = false
    }

    class Factory @Inject constructor(
        @Main private val mainHandler: Handler,
        @Background private val bgHandler: Handler,
        private val systemSettings: SystemSettings
    ) {
        fun create(brightnessSliderView: View): AutoBrightnessController {
            val autoBrightnessIcon: ImageView =
                (brightnessSliderView as BrightnessSliderView).findViewById(R.id.auto_brightness_icon)
            return AutoBrightnessController(
                autoBrightnessIcon,
                mainHandler,
                bgHandler,
                systemSettings
            )
        }
    }
}
