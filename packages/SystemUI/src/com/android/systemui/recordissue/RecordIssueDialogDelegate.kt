/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.systemui.recordissue

import android.annotation.SuppressLint
import android.app.BroadcastOptions
import android.app.PendingIntent
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.os.UserHandle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.WindowManager
import android.widget.Button
import android.widget.PopupMenu
import android.widget.Switch
import androidx.annotation.AnyThread
import androidx.annotation.MainThread
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.flags.FeatureFlagsClassic
import com.android.systemui.flags.Flags
import com.android.systemui.flags.Flags.WM_ENABLE_PARTIAL_SCREEN_SHARING_ENTERPRISE_POLICIES
import com.android.systemui.mediaprojection.MediaProjectionMetricsLogger
import com.android.systemui.mediaprojection.SessionCreationSource
import com.android.systemui.mediaprojection.devicepolicy.ScreenCaptureDevicePolicyResolver
import com.android.systemui.mediaprojection.devicepolicy.ScreenCaptureDisabledDialog
import com.android.systemui.qs.tiles.RecordIssueTile
import com.android.systemui.res.R
import com.android.systemui.screenrecord.RecordingService
import com.android.systemui.settings.UserContextProvider
import com.android.systemui.settings.UserFileManager
import com.android.systemui.settings.UserTracker
import com.android.systemui.statusbar.phone.SystemUIDialog
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import java.util.concurrent.Executor

class RecordIssueDialogDelegate
@AssistedInject
constructor(
    private val factory: SystemUIDialog.Factory,
    private val userContextProvider: UserContextProvider,
    private val userTracker: UserTracker,
    private val flags: FeatureFlagsClassic,
    @Background private val bgExecutor: Executor,
    @Main private val mainExecutor: Executor,
    private val devicePolicyResolver: dagger.Lazy<ScreenCaptureDevicePolicyResolver>,
    private val mediaProjectionMetricsLogger: MediaProjectionMetricsLogger,
    private val userFileManager: UserFileManager,
    @Assisted private val onStarted: Runnable,
) : SystemUIDialog.Delegate {

    /** To inject dependencies and allow for easier testing */
    @AssistedFactory
    interface Factory {
        /** Create a dialog object */
        fun create(onStarted: Runnable): RecordIssueDialogDelegate
    }

    @SuppressLint("UseSwitchCompatOrMaterialCode") private lateinit var screenRecordSwitch: Switch
    private lateinit var issueTypeButton: Button

    @MainThread
    override fun beforeCreate(dialog: SystemUIDialog, savedInstanceState: Bundle?) {
        dialog.apply {
            setView(LayoutInflater.from(context).inflate(R.layout.record_issue_dialog, null))
            setTitle(context.getString(R.string.qs_record_issue_label))
            setIcon(R.drawable.qs_record_issue_icon_off)
            setNegativeButton(R.string.cancel) { _, _ -> dismiss() }
            setPositiveButton(R.string.qs_record_issue_start) { _, _ ->
                onStarted.run()
                if (screenRecordSwitch.isChecked) {
                    requestScreenCapture()
                }
                dismiss()
            }
        }
    }

    override fun createDialog(): SystemUIDialog = factory.create(this)

    @MainThread
    override fun onCreate(dialog: SystemUIDialog, savedInstanceState: Bundle?) {
        dialog.apply {
            window?.addPrivateFlags(WindowManager.LayoutParams.SYSTEM_FLAG_SHOW_FOR_ALL_USERS)
            window?.setGravity(Gravity.CENTER)

            screenRecordSwitch = requireViewById(R.id.screenrecord_switch)
            screenRecordSwitch.setOnCheckedChangeListener { _, isEnabled ->
                onScreenRecordSwitchClicked(context, isEnabled)
            }
            issueTypeButton = requireViewById(R.id.issue_type_button)
            issueTypeButton.setOnClickListener { onIssueTypeClicked(context) }
        }
    }

    @AnyThread
    private fun onScreenRecordSwitchClicked(context: Context, isEnabled: Boolean) {
        if (!isEnabled) return

        bgExecutor.execute {
            if (
                flags.isEnabled(WM_ENABLE_PARTIAL_SCREEN_SHARING_ENTERPRISE_POLICIES) &&
                    devicePolicyResolver
                        .get()
                        .isScreenCaptureCompletelyDisabled(UserHandle.of(userTracker.userId))
            ) {
                mainExecutor.execute {
                    ScreenCaptureDisabledDialog(context).show()
                    screenRecordSwitch.isChecked = false
                }
                return@execute
            }

            mediaProjectionMetricsLogger.notifyProjectionInitiated(
                userTracker.userId,
                SessionCreationSource.SYSTEM_UI_SCREEN_RECORDER
            )

            if (flags.isEnabled(Flags.WM_ENABLE_PARTIAL_SCREEN_SHARING)) {
                val prefs =
                    userFileManager.getSharedPreferences(
                        RecordIssueTile.TILE_SPEC,
                        Context.MODE_PRIVATE,
                        userTracker.userId
                    )
                if (!prefs.getBoolean(HAS_APPROVED_SCREEN_RECORDING, false)) {
                    mainExecutor.execute {
                        ScreenCapturePermissionDialogDelegate(factory, prefs).createDialog().apply {
                            setOnCancelListener { screenRecordSwitch.isChecked = false }
                            show()
                        }
                    }
                }
            }
        }
    }

    @MainThread
    private fun onIssueTypeClicked(context: Context) {
        val selectedCategory = issueTypeButton.text.toString()
        val popupMenu = PopupMenu(context, issueTypeButton)

        context.resources.getStringArray(R.array.qs_record_issue_types).forEachIndexed { i, cat ->
            popupMenu.menu.add(0, 0, i, cat).apply {
                setIcon(R.drawable.arrow_pointing_down)
                if (selectedCategory != cat) {
                    iconTintList = ColorStateList.valueOf(Color.TRANSPARENT)
                }
            }
        }
        popupMenu.apply {
            setOnMenuItemClickListener {
                issueTypeButton.text = it.title
                true
            }
            setForceShowIcon(true)
            show()
        }
    }

    private fun requestScreenCapture() =
        PendingIntent.getForegroundService(
                userContextProvider.userContext,
                RecordingService.REQUEST_CODE,
                IssueRecordingService.getStartIntent(userContextProvider.userContext),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            .send(BroadcastOptions.makeBasic().apply { isInteractive = true }.toBundle())
}
