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
 *
 */

package com.android.systemui.keyguard.ui.viewmodel

import android.content.Context
import com.android.settingslib.Utils
import com.android.systemui.common.ui.domain.interactor.ConfigurationInteractor
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart

/** Models the UI state for the device entry icon background view. */
@ExperimentalCoroutinesApi
class DeviceEntryBackgroundViewModel
@Inject
constructor(
    val context: Context,
    val deviceEntryIconViewModel: DeviceEntryIconViewModel,
    configurationInteractor: ConfigurationInteractor,
    lockscreenToAodTransitionViewModel: LockscreenToAodTransitionViewModel,
    aodToLockscreenTransitionViewModel: AodToLockscreenTransitionViewModel,
    goneToAodTransitionViewModel: GoneToAodTransitionViewModel,
    primaryBouncerToAodTransitionViewModel: PrimaryBouncerToAodTransitionViewModel,
    occludedToAodTransitionViewModel: OccludedToAodTransitionViewModel,
    occludedToLockscreenTransitionViewModel: OccludedToLockscreenTransitionViewModel,
    dreamingToLockscreenTransitionViewModel: DreamingToLockscreenTransitionViewModel,
    alternateBouncerToAodTransitionViewModel: AlternateBouncerToAodTransitionViewModel,
    goneToLockscreenTransitionViewModel: GoneToLockscreenTransitionViewModel,
    goneToDozingTransitionViewModel: GoneToDozingTransitionViewModel,
    primaryBouncerToDozingTransitionViewModel: PrimaryBouncerToDozingTransitionViewModel,
    lockscreenToDozingTransitionViewModel: LockscreenToDozingTransitionViewModel,
    dozingToLockscreenTransitionViewModel: DozingToLockscreenTransitionViewModel,
    alternateBouncerToDozingTransitionViewModel: AlternateBouncerToDozingTransitionViewModel,
) {
    val color: Flow<Int> =
        deviceEntryIconViewModel.useBackgroundProtection.flatMapLatest { useBackground ->
            if (useBackground) {
                configurationInteractor.onAnyConfigurationChange
                    .map {
                        Utils.getColorAttrDefaultColor(
                            context,
                            com.android.internal.R.attr.colorSurface
                        )
                    }
                    .onStart {
                        emit(
                            Utils.getColorAttrDefaultColor(
                                context,
                                com.android.internal.R.attr.colorSurface
                            )
                        )
                    }
            } else {
                flowOf(0)
            }
        }
    val alpha: Flow<Float> =
        deviceEntryIconViewModel.useBackgroundProtection.flatMapLatest { useBackground ->
            if (useBackground) {
                setOf(
                        lockscreenToAodTransitionViewModel.deviceEntryBackgroundViewAlpha,
                        aodToLockscreenTransitionViewModel.deviceEntryBackgroundViewAlpha,
                        goneToAodTransitionViewModel.deviceEntryBackgroundViewAlpha,
                        primaryBouncerToAodTransitionViewModel.deviceEntryBackgroundViewAlpha,
                        occludedToAodTransitionViewModel.deviceEntryBackgroundViewAlpha,
                        occludedToLockscreenTransitionViewModel.deviceEntryBackgroundViewAlpha,
                        dreamingToLockscreenTransitionViewModel.deviceEntryBackgroundViewAlpha,
                        alternateBouncerToAodTransitionViewModel.deviceEntryBackgroundViewAlpha,
                        goneToLockscreenTransitionViewModel.deviceEntryBackgroundViewAlpha,
                        goneToDozingTransitionViewModel.deviceEntryBackgroundViewAlpha,
                        primaryBouncerToDozingTransitionViewModel.deviceEntryBackgroundViewAlpha,
                        lockscreenToDozingTransitionViewModel.deviceEntryBackgroundViewAlpha,
                        dozingToLockscreenTransitionViewModel.deviceEntryBackgroundViewAlpha,
                        alternateBouncerToDozingTransitionViewModel.deviceEntryBackgroundViewAlpha,
                    )
                    .merge()
            } else {
                flowOf(0f)
            }
        }
}
