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

package com.android.systemui.shade.ui.viewmodel

import com.android.systemui.authentication.domain.interactor.AuthenticationInteractor
import com.android.systemui.bouncer.domain.interactor.BouncerInteractor
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.scene.shared.model.SceneKey
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** Models UI state and handles user input for the shade scene. */
@SysUISingleton
class ShadeSceneViewModel
@Inject
constructor(
    @Application private val applicationScope: CoroutineScope,
    authenticationInteractor: AuthenticationInteractor,
    private val bouncerInteractor: BouncerInteractor,
) {
    /** The key of the scene we should switch to when swiping up. */
    val upDestinationSceneKey: StateFlow<SceneKey> =
        combine(
                authenticationInteractor.isUnlocked,
                authenticationInteractor.canSwipeToDismiss,
            ) { isUnlocked, canSwipeToDismiss ->
                upDestinationSceneKey(
                    isUnlocked = isUnlocked,
                    canSwipeToDismiss = canSwipeToDismiss,
                )
            }
            .stateIn(
                scope = applicationScope,
                started = SharingStarted.WhileSubscribed(),
                initialValue =
                    upDestinationSceneKey(
                        isUnlocked = authenticationInteractor.isUnlocked.value,
                        canSwipeToDismiss = authenticationInteractor.canSwipeToDismiss.value,
                    ),
            )

    /** Notifies that some content in the shade was clicked. */
    fun onContentClicked() {
        bouncerInteractor.showOrUnlockDevice()
    }

    private fun upDestinationSceneKey(
        isUnlocked: Boolean,
        canSwipeToDismiss: Boolean,
    ): SceneKey {
        return when {
            canSwipeToDismiss -> SceneKey.Lockscreen
            isUnlocked -> SceneKey.Gone
            else -> SceneKey.Lockscreen
        }
    }
}
