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

package com.android.systemui.statusbar.notification.stack.ui.viewbinder

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.view.View
import android.view.WindowInsets
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.keyguard.ui.viewmodel.BurnInParameters
import com.android.systemui.keyguard.ui.viewmodel.ViewStateAccessor
import com.android.systemui.lifecycle.repeatWhenAttached
import com.android.systemui.scene.shared.flag.SceneContainerFlags
import com.android.systemui.statusbar.notification.footer.shared.FooterViewRefactor
import com.android.systemui.statusbar.notification.stack.NotificationStackScrollLayoutController
import com.android.systemui.statusbar.notification.stack.NotificationStackSizeCalculator
import com.android.systemui.statusbar.notification.stack.shared.flexiNotifsEnabled
import com.android.systemui.statusbar.notification.stack.ui.view.SharedNotificationContainer
import com.android.systemui.statusbar.notification.stack.ui.viewmodel.SharedNotificationContainerViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Binds the shared notification container to its view-model. */
object SharedNotificationContainerBinder {

    @JvmStatic
    fun bind(
        view: SharedNotificationContainer,
        viewModel: SharedNotificationContainerViewModel,
        sceneContainerFlags: SceneContainerFlags,
        controller: NotificationStackScrollLayoutController,
        notificationStackSizeCalculator: NotificationStackSizeCalculator,
        @Main mainImmediateDispatcher: CoroutineDispatcher,
    ): DisposableHandle {
        val disposableHandle =
            view.repeatWhenAttached {
                repeatOnLifecycle(Lifecycle.State.CREATED) {
                    launch {
                        viewModel.configurationBasedDimensions.collect {
                            view.updateConstraints(
                                useSplitShade = it.useSplitShade,
                                marginStart = it.marginStart,
                                marginTop = it.marginTop,
                                marginEnd = it.marginEnd,
                                marginBottom = it.marginBottom,
                            )

                            controller.setOverExpansion(0f)
                            controller.setOverScrollAmount(0)
                            if (!FooterViewRefactor.isEnabled) {
                                controller.updateFooter()
                            }
                        }
                    }
                }
            }

        // Required to capture keyguard media changes and ensure the notification count is correct
        val layoutChangeListener =
            object : View.OnLayoutChangeListener {
                override fun onLayoutChange(
                    view: View,
                    left: Int,
                    top: Int,
                    right: Int,
                    bottom: Int,
                    oldLeft: Int,
                    oldTop: Int,
                    oldRight: Int,
                    oldBottom: Int
                ) {
                    viewModel.notificationStackChanged()
                }
            }

        val burnInParams = MutableStateFlow(BurnInParameters())
        val viewState =
            ViewStateAccessor(
                alpha = { controller.getAlpha() },
            )

        /*
         * For animation sensitive coroutines, immediately run just like applicationScope does
         * instead of doing a post() to the main thread. This extra delay can cause visible jitter.
         */
        val disposableHandleMainImmediate =
            view.repeatWhenAttached(mainImmediateDispatcher) {
                repeatOnLifecycle(Lifecycle.State.CREATED) {
                    if (!sceneContainerFlags.flexiNotifsEnabled()) {
                        launch {
                            // Only temporarily needed, until flexi notifs go live
                            viewModel.shadeCollapseFadeIn.collect { fadeIn ->
                                if (fadeIn) {
                                    android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
                                        duration = 250
                                        addUpdateListener { animation ->
                                            controller.setMaxAlphaForExpansion(
                                                animation.getAnimatedFraction()
                                            )
                                        }
                                        addListener(
                                            object : AnimatorListenerAdapter() {
                                                override fun onAnimationEnd(animation: Animator) {
                                                    viewModel.setShadeCollapseFadeInComplete(true)
                                                }
                                            }
                                        )
                                        start()
                                    }
                                }
                            }
                        }
                    }

                    launch {
                        viewModel
                            .getMaxNotifications { space, extraShelfSpace ->
                                val shelfHeight = controller.getShelfHeight().toFloat()
                                notificationStackSizeCalculator.computeMaxKeyguardNotifications(
                                    controller.getView(),
                                    space,
                                    if (extraShelfSpace) shelfHeight else 0f,
                                    shelfHeight,
                                )
                            }
                            .collect { controller.setMaxDisplayedNotifications(it) }
                    }

                    if (!sceneContainerFlags.flexiNotifsEnabled()) {
                        launch {
                            viewModel.bounds.collect {
                                val animate =
                                    it.isAnimated || controller.isAddOrRemoveAnimationPending
                                controller.updateTopPadding(it.top, animate)
                            }
                        }
                    }

                    launch {
                        burnInParams
                            .flatMapLatest { params -> viewModel.translationY(params) }
                            .collect { y -> controller.setTranslationY(y) }
                    }

                    launch { viewModel.translationX.collect { x -> controller.translationX = x } }

                    if (!sceneContainerFlags.isEnabled()) {
                        launch {
                            viewModel.expansionAlpha(viewState).collect {
                                controller.setMaxAlphaForExpansion(it)
                            }
                        }
                    }
                    launch {
                        viewModel.glanceableHubAlpha.collect {
                            controller.setMaxAlphaForGlanceableHub(it)
                        }
                    }
                }
            }

        controller.setOnHeightChangedRunnable(Runnable { viewModel.notificationStackChanged() })

        view.setOnApplyWindowInsetsListener { v: View, insets: WindowInsets ->
            val insetTypes = WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
            burnInParams.update { current ->
                current.copy(topInset = insets.getInsetsIgnoringVisibility(insetTypes).top)
            }
            insets
        }
        view.addOnLayoutChangeListener(layoutChangeListener)

        return object : DisposableHandle {
            override fun dispose() {
                disposableHandle.dispose()
                disposableHandleMainImmediate.dispose()
                controller.setOnHeightChangedRunnable(null)
                view.setOnApplyWindowInsetsListener(null)
                view.removeOnLayoutChangeListener(layoutChangeListener)
            }
        }
    }
}
