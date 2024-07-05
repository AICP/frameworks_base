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

import com.android.internal.logging.metricsLogger
import com.android.systemui.classifier.falsingManager
import com.android.systemui.common.ui.configurationState
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.Kosmos.Fixture
import com.android.systemui.kosmos.testDispatcher
import com.android.systemui.statusbar.notification.collection.render.silentHeaderController
import com.android.systemui.statusbar.notification.icon.ui.viewbinder.notificationIconContainerShelfViewBinder
import com.android.systemui.statusbar.notification.notificationActivityStarter
import com.android.systemui.statusbar.notification.stack.displaySwitchNotificationsHiderTracker
import com.android.systemui.statusbar.notification.stack.ui.view.notificationStatsLogger
import com.android.systemui.statusbar.notification.stack.ui.viewmodel.notificationListViewModel
import com.android.systemui.statusbar.phone.notificationIconAreaController
import java.util.Optional

val Kosmos.notificationListViewBinder by Fixture {
    NotificationListViewBinder(
        viewModel = notificationListViewModel,
        backgroundDispatcher = testDispatcher,
        configuration = configurationState,
        falsingManager = falsingManager,
        iconAreaController = notificationIconAreaController,
        loggerOptional = Optional.of(notificationStatsLogger),
        metricsLogger = metricsLogger,
        hiderTracker = displaySwitchNotificationsHiderTracker,
        nicBinder = notificationIconContainerShelfViewBinder,
        notificationActivityStarter = { notificationActivityStarter },
        silentHeaderController = silentHeaderController,
    )
}
