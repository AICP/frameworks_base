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

package com.android.systemui.statusbar.notification.stack.ui.viewmodel

import com.android.compose.animation.scene.ObservableTransitionState
import com.android.systemui.common.shared.model.NotificationContainerBounds
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dump.DumpManager
import com.android.systemui.scene.domain.interactor.SceneInteractor
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.shade.domain.interactor.ShadeInteractor
import com.android.systemui.statusbar.notification.stack.domain.interactor.NotificationStackAppearanceInteractor
import com.android.systemui.util.kotlin.FlowDumperImpl
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/** ViewModel which represents the state of the NSSL/Controller in the world of flexiglass */
@SysUISingleton
class NotificationStackAppearanceViewModel
@Inject
constructor(
    dumpManager: DumpManager,
    stackAppearanceInteractor: NotificationStackAppearanceInteractor,
    shadeInteractor: ShadeInteractor,
    sceneInteractor: SceneInteractor,
) : FlowDumperImpl(dumpManager) {
    /**
     * The expansion fraction of the notification stack. It should go from 0 to 1 when transitioning
     * from Gone to Shade scenes, and remain at 1 when in Lockscreen or Shade scenes and while
     * transitioning from Shade to QuickSettings scenes.
     */
    val expandFraction: Flow<Float> =
        combine(
                shadeInteractor.shadeExpansion,
                sceneInteractor.transitionState,
            ) { shadeExpansion, transitionState ->
                when (transitionState) {
                    is ObservableTransitionState.Idle -> {
                        if (transitionState.scene == Scenes.Lockscreen) {
                            1f
                        } else {
                            shadeExpansion
                        }
                    }
                    is ObservableTransitionState.Transition -> {
                        if (
                            (transitionState.fromScene == Scenes.Shade &&
                                transitionState.toScene == Scenes.QuickSettings) ||
                                (transitionState.fromScene == Scenes.QuickSettings &&
                                    transitionState.toScene == Scenes.Shade)
                        ) {
                            1f
                        } else {
                            shadeExpansion
                        }
                    }
                }
            }
            .distinctUntilChanged()
            .dumpWhileCollecting("expandFraction")

    /** The bounds of the notification stack in the current scene. */
    val stackBounds: Flow<NotificationContainerBounds> =
        stackAppearanceInteractor.stackBounds.dumpValue("stackBounds")

    /** The y-coordinate in px of top of the contents of the notification stack. */
    val contentTop: StateFlow<Float> = stackAppearanceInteractor.contentTop.dumpValue("contentTop")
}
