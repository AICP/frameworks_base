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

package com.android.systemui.statusbar.notification.stack.data.repository

import com.android.systemui.common.shared.model.NotificationContainerBounds
import com.android.systemui.dagger.SysUISingleton
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow

/** A repository which holds state about and controlling the appearance of the notification stack */
@SysUISingleton
class NotificationStackAppearanceRepository @Inject constructor() {
    /** The bounds of the notification stack in the current scene. */
    val stackBounds = MutableStateFlow(NotificationContainerBounds())

    /**
     * The height in px of the contents of notification stack. Depending on the number of
     * notifications, this can exceed the space available on screen to show notifications, at which
     * point the notification stack should become scrollable.
     */
    val intrinsicContentHeight = MutableStateFlow(0f)

    /**
     * The y-coordinate in px of top of the contents of the notification stack. This value can be
     * negative, if the stack is scrolled such that its top extends beyond the top edge of the
     * screen.
     */
    val contentTop = MutableStateFlow(0f)

    /**
     * Whether the notification stack is scrolled to the top; i.e., it cannot be scrolled down any
     * further.
     */
    val scrolledToTop = MutableStateFlow(true)

    /**
     * The amount in px that the notification stack should scroll due to internal expansion. This
     * should only happen when a notification expansion hits the bottom of the screen, so it is
     * necessary to scroll up to keep expanding the notification.
     */
    val syntheticScroll = MutableStateFlow(0f)
}
