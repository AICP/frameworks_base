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
 * limitations under the License
 */

package com.android.systemui.keyguard.ui.viewmodel

import android.content.Context
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.keyguard.domain.interactor.KeyguardClockInteractor
import com.android.systemui.keyguard.shared.model.SettingsClockSize
import com.android.systemui.plugins.clocks.ClockController
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** View model for the small clock view, large clock view. */
class KeyguardPreviewClockViewModel
@Inject
constructor(
    @Application private val context: Context,
    interactor: KeyguardClockInteractor,
    @Application private val applicationScope: CoroutineScope,
) {

    var shouldHighlightSelectedAffordance: Boolean = false
    val isLargeClockVisible: Flow<Boolean> =
        interactor.selectedClockSize.map { it == SettingsClockSize.DYNAMIC }

    val isSmallClockVisible: Flow<Boolean> =
        interactor.selectedClockSize.map { it == SettingsClockSize.SMALL }

    var lastClockPair: Pair<ClockController, ClockController>? = null

    val previewClockPair: StateFlow<Pair<ClockController, ClockController>> =
        interactor.previewClockPair

    val selectedClockSize: StateFlow<SettingsClockSize?> =
        interactor.selectedClockSize.stateIn(
            scope = applicationScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = null
        )
}
