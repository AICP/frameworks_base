/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.systemui.screenrecord

import android.content.Context
import android.media.projection.MediaProjectionConfig
import android.os.Bundle
import com.android.systemui.R

/** Dialog to select screen recording options */
class MediaProjectionPermissionDialog(
    context: Context,
    mediaProjectionConfig: MediaProjectionConfig?,
    private val onStartRecordingClicked: Runnable,
    private val onCancelClicked: Runnable,
    private val appName: String?
) :
    BaseScreenSharePermissionDialog(
        context,
        createOptionList(context, appName, mediaProjectionConfig),
        appName
    ) {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // TODO(b/270018943): Handle the case of System sharing (not recording nor casting)
        if (appName == null) {
            setDialogTitle(R.string.media_projection_entry_cast_permission_dialog_title)
            setStartButtonText(R.string.media_projection_entry_cast_permission_dialog_continue)
        } else {
            setDialogTitle(R.string.media_projection_entry_app_permission_dialog_title)
            setStartButtonText(R.string.media_projection_entry_app_permission_dialog_continue)
        }
        setStartButtonOnClickListener {
            // Note that it is important to run this callback before dismissing, so that the
            // callback can disable the dialog exit animation if it wants to.
            onStartRecordingClicked.run()
            dismiss()
        }
        setCancelButtonOnClickListener {
            onCancelClicked.run()
            dismiss()
        }
    }

    companion object {
        private fun createOptionList(
            context: Context,
            appName: String?,
            mediaProjectionConfig: MediaProjectionConfig?
        ): List<ScreenShareOption> {
            val singleAppWarningText =
                if (appName == null) {
                    R.string.media_projection_entry_cast_permission_dialog_warning_single_app
                } else {
                    R.string.media_projection_entry_app_permission_dialog_warning_single_app
                }
            val entireScreenWarningText =
                if (appName == null) {
                    R.string.media_projection_entry_cast_permission_dialog_warning_entire_screen
                } else {
                    R.string.media_projection_entry_app_permission_dialog_warning_entire_screen
                }

            val singleAppDisabledText =
                if (
                    appName != null &&
                        mediaProjectionConfig?.regionToCapture ==
                            MediaProjectionConfig.CAPTURE_REGION_FIXED_DISPLAY
                ) {
                    context.getString(
                        R.string.media_projection_entry_app_permission_dialog_single_app_disabled,
                        appName
                    )
                } else {
                    null
                }
            return listOf(
                ScreenShareOption(
                    mode = ENTIRE_SCREEN,
                    spinnerText = R.string.screen_share_permission_dialog_option_entire_screen,
                    warningText = entireScreenWarningText
                ),
                ScreenShareOption(
                    mode = SINGLE_APP,
                    spinnerText = R.string.screen_share_permission_dialog_option_single_app,
                    warningText = singleAppWarningText,
                    spinnerDisabledText = singleAppDisabledText,
                )
            )
        }
    }
}
