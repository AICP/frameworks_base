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

package com.android.systemui.statusbar.notification.stack.ui.viewmodel

import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.flags.FeatureFlags
import com.android.systemui.flags.Flags
import com.android.systemui.statusbar.notification.shelf.ui.viewmodel.NotificationShelfViewModel
import dagger.Module
import dagger.Provides
import java.util.Optional
import javax.inject.Provider

/** ViewModel for the list of notifications. */
class NotificationListViewModel(
    val shelf: NotificationShelfViewModel,
)

@Module
object NotificationListViewModelModule {
    @JvmStatic
    @Provides
    @SysUISingleton
    fun maybeProvideViewModel(
        featureFlags: FeatureFlags,
        shelfViewModel: Provider<NotificationShelfViewModel>,
    ): Optional<NotificationListViewModel> =
        if (featureFlags.isEnabled(Flags.NOTIFICATION_SHELF_REFACTOR)) {
            Optional.of(NotificationListViewModel(shelfViewModel.get()))
        } else {
            Optional.empty()
        }
}
