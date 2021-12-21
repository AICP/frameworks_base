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

package com.android.systemui.keyguard.ui.binder

import android.content.res.ColorStateList
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.provider.Settings
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.android.app.tracing.coroutines.launchTraced as launch
import com.android.systemui.biometrics.UdfpsIconDrawable
import com.android.systemui.keyguard.ui.view.DeviceEntryIconView
import com.android.systemui.keyguard.ui.viewmodel.AlternateBouncerUdfpsIconViewModel
import com.android.systemui.lifecycle.repeatWhenAttached
import com.android.systemui.scene.shared.flag.SceneContainerFlag
import com.android.internal.util.aicp.PackageUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch

object AlternateBouncerUdfpsViewBinder {

    /** Updates UI for the UDFPS icon on the alternate bouncer. */
    @JvmStatic
    fun bind(applicationScope: CoroutineScope, view: DeviceEntryIconView, viewModel: AlternateBouncerUdfpsIconViewModel) {
        val fgIconView = view.iconView
        val bgView = view.bgView

        val packageInstalled = PackageUtils.isPackageInstalled(
            view.context, "org.derpfest.udfps.icons"
        )

        val shouldUseCustomUdfpsIcon: StateFlow<Boolean> = callbackFlow {
            fun readValue(): Boolean =
                Settings.System.getIntForUser(
                    view.context.contentResolver,
                    Settings.System.UDFPS_ICON,
                    0,
                    UserHandle.USER_CURRENT
                ) != 0

            val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean, uri: Uri?) {
                    trySend(readValue())
                }
            }
            view.context.contentResolver.registerContentObserver(
                Settings.System.getUriFor(Settings.System.UDFPS_ICON),
                false,
                observer,
                UserHandle.USER_CURRENT
            )
            trySend(readValue())
            awaitClose { view.context.contentResolver.unregisterContentObserver(observer) }
        }.stateIn(
            scope = applicationScope,
            started = SharingStarted.Eagerly,
            initialValue = false
        )

        view.repeatWhenAttached {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                view.alpha = 0f

                launch("$TAG#viewModel.accessibilityDelegateHint") {
                    viewModel.accessibilityDelegateHint.collect { hint ->
                        view.accessibilityHintType = hint
                        if (hint != DeviceEntryIconView.AccessibilityHintType.NONE) {
                            view.setOnClickListener { viewModel.onTapped() }
                        } else {
                            view.setOnClickListener(null)
                        }
                    }
                }

                if (SceneContainerFlag.isEnabled) {
                    view.alpha = 1f
                } else {
                    launch("$TAG#viewModel.alpha") { viewModel.alpha.collect { view.alpha = it } }
                }
            }
        }

        fgIconView.repeatWhenAttached {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.fgViewModel.collect { fgViewModel ->
                    fgIconView.setImageState(
                        view.getIconState(fgViewModel.type, fgViewModel.useAodVariant),
                        /* merge */ false,
                    )
                    fgIconView.imageTintList = ColorStateList.valueOf(fgViewModel.tint)
                    if (fgIconView.drawable.current !is UdfpsIconDrawable) {
                        fgIconView.setPadding(
                            fgViewModel.padding,
                            fgViewModel.padding,
                            fgViewModel.padding,
                            fgViewModel.padding
                        )
                    } else {
                        fgIconView.setPadding(0, 0, 0, 0)
                    }
                }
            }
        }

        bgView.visibility = View.VISIBLE
        bgView.repeatWhenAttached {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch("$TAG#viewModel.bgColor") {
                    if (!shouldUseCustomUdfpsIcon.value || !packageInstalled) {
                        viewModel.bgColor.collect { color ->
                            bgView.imageTintList = ColorStateList.valueOf(color)
                        }
                    } else {
                        viewModel.bgColor.collect { color ->
                            bgView.imageTintList = null
                        }
                    }
                }
                launch("$TAG#viewModel.bgAlpha") {
                    viewModel.bgAlpha.collect { alpha -> bgView.alpha = alpha }
                }
            }
        }
    }

    private const val TAG = "AlternateBouncerUdfpsViewBinder"
}
