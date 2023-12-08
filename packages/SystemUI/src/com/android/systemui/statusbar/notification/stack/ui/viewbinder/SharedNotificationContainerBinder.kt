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

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.android.systemui.lifecycle.repeatWhenAttached
import com.android.systemui.statusbar.notification.stack.ui.view.SharedNotificationContainer
import com.android.systemui.statusbar.notification.stack.ui.viewmodel.SharedNotificationContainerViewModel
import kotlinx.coroutines.launch

/** Binds the shared notification container to its view-model. */
object SharedNotificationContainerBinder {

    @JvmStatic
    fun bind(
        view: SharedNotificationContainer,
        viewModel: SharedNotificationContainerViewModel,
    ) {
        view.repeatWhenAttached {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.configurationBasedDimensions.collect {
                        view.updateConstraints(
                            useSplitShade = it.useSplitShade,
                            marginStart = it.marginStart,
                            marginTop = it.marginTop,
                            marginEnd = it.marginEnd,
                            marginBottom = it.marginBottom,
                        )
                    }
                }
            }
        }
    }
}
