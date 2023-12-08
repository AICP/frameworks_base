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

@file:OptIn(InternalNoteTaskApi::class)

package com.android.systemui.notetask

import android.app.Activity
import android.app.Service
import android.app.role.RoleManager
import com.android.systemui.flags.FeatureFlags
import com.android.systemui.flags.Flags
import com.android.systemui.notetask.quickaffordance.NoteTaskQuickAffordanceModule
import com.android.systemui.notetask.shortcut.CreateNoteTaskShortcutActivity
import com.android.systemui.notetask.shortcut.LaunchNoteTaskActivity
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.multibindings.ClassKey
import dagger.multibindings.IntoMap

/** Compose all dependencies required by Note Task feature. */
@Module(includes = [NoteTaskQuickAffordanceModule::class])
interface NoteTaskModule {

    @[Binds IntoMap ClassKey(NoteTaskControllerUpdateService::class)]
    fun NoteTaskControllerUpdateService.bindNoteTaskControllerUpdateService(): Service

    @[Binds IntoMap ClassKey(NoteTaskBubblesController.NoteTaskBubblesService::class)]
    fun NoteTaskBubblesController.NoteTaskBubblesService.bindNoteTaskBubblesService(): Service

    @[Binds IntoMap ClassKey(LaunchNoteTaskActivity::class)]
    fun LaunchNoteTaskActivity.bindNoteTaskLauncherActivity(): Activity

    @[Binds IntoMap ClassKey(LaunchNotesRoleSettingsTrampolineActivity::class)]
    fun LaunchNotesRoleSettingsTrampolineActivity.bindLaunchNotesRoleSettingsTrampolineActivity():
        Activity

    @[Binds IntoMap ClassKey(CreateNoteTaskShortcutActivity::class)]
    fun CreateNoteTaskShortcutActivity.bindNoteTaskShortcutActivity(): Activity

    companion object {

        @[Provides NoteTaskEnabledKey]
        fun provideIsNoteTaskEnabled(
            featureFlags: FeatureFlags,
            roleManager: RoleManager,
        ): Boolean {
            val isRoleAvailable = roleManager.isRoleAvailable(RoleManager.ROLE_NOTES)
            val isFeatureEnabled = featureFlags.isEnabled(Flags.NOTE_TASKS)
            return isRoleAvailable && isFeatureEnabled
        }
    }
}
