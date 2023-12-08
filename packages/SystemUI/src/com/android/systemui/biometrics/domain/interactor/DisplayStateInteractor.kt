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

package com.android.systemui.biometrics.domain.interactor

import android.content.Context
import android.content.res.Configuration
import com.android.systemui.biometrics.data.repository.DisplayStateRepository
import com.android.systemui.biometrics.shared.model.DisplayRotation
import com.android.systemui.common.coroutine.ChannelExt.trySendWithFailureLogging
import com.android.systemui.common.coroutine.ConflatedCallbackFlow.conflatedCallbackFlow
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.unfold.compat.ScreenSizeFoldProvider
import com.android.systemui.unfold.updates.FoldProvider
import java.util.concurrent.Executor
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/** Aggregates display state information. */
interface DisplayStateInteractor {

    /** Whether the device is currently in rear display mode. */
    val isInRearDisplayMode: StateFlow<Boolean>

    /** Whether the device is currently folded. */
    val isFolded: Flow<Boolean>

    /** Current rotation of the display */
    val currentRotation: StateFlow<DisplayRotation>

    /** Called on configuration changes, used to keep the display state in sync */
    fun onConfigurationChanged(newConfig: Configuration)
}

/** Encapsulates logic for interacting with the display state. */
class DisplayStateInteractorImpl
@Inject
constructor(
    @Application applicationScope: CoroutineScope,
    @Application context: Context,
    @Main mainExecutor: Executor,
    displayStateRepository: DisplayStateRepository,
) : DisplayStateInteractor {
    private var screenSizeFoldProvider: ScreenSizeFoldProvider = ScreenSizeFoldProvider(context)

    fun setScreenSizeFoldProvider(foldProvider: ScreenSizeFoldProvider) {
        screenSizeFoldProvider = foldProvider
    }

    override val isFolded: Flow<Boolean> =
        conflatedCallbackFlow {
                val sendFoldStateUpdate = { state: Boolean ->
                    trySendWithFailureLogging(
                        state,
                        TAG,
                        "Error sending fold state update to $state"
                    )
                }

                val callback =
                    object : FoldProvider.FoldCallback {
                        override fun onFoldUpdated(isFolded: Boolean) {
                            sendFoldStateUpdate(isFolded)
                        }
                    }

                sendFoldStateUpdate(false)
                screenSizeFoldProvider.registerCallback(callback, mainExecutor)
                awaitClose { screenSizeFoldProvider.unregisterCallback(callback) }
            }
            .stateIn(
                applicationScope,
                started = SharingStarted.Eagerly,
                initialValue = false,
            )

    override val isInRearDisplayMode: StateFlow<Boolean> =
        displayStateRepository.isInRearDisplayMode

    override val currentRotation: StateFlow<DisplayRotation> =
        displayStateRepository.currentRotation

    override fun onConfigurationChanged(newConfig: Configuration) {
        screenSizeFoldProvider.onConfigurationChange(newConfig)
    }

    companion object {
        private const val TAG = "DisplayStateInteractor"
    }
}
