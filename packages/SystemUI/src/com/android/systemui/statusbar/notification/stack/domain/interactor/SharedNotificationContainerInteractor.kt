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

package com.android.systemui.statusbar.notification.stack.domain.interactor

import android.content.Context
import com.android.systemui.R
import com.android.systemui.common.ui.data.repository.ConfigurationRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

/** Encapsulates business-logic specifically related to the shared notification stack container. */
class SharedNotificationContainerInteractor
@Inject
constructor(
    configurationRepository: ConfigurationRepository,
    private val context: Context,
) {
    val configurationBasedDimensions: Flow<ConfigurationBasedDimensions> =
        configurationRepository.onAnyConfigurationChange
            .onStart { emit(Unit) }
            .map { _ ->
                with(context.resources) {
                    ConfigurationBasedDimensions(
                        useSplitShade = getBoolean(R.bool.config_use_split_notification_shade),
                        useLargeScreenHeader =
                            getBoolean(R.bool.config_use_large_screen_shade_header),
                        marginHorizontal =
                            getDimensionPixelSize(R.dimen.notification_panel_margin_horizontal),
                        marginBottom =
                            getDimensionPixelSize(R.dimen.notification_panel_margin_bottom),
                        marginTop = getDimensionPixelSize(R.dimen.notification_panel_margin_top),
                        marginTopLargeScreen =
                            getDimensionPixelSize(R.dimen.large_screen_shade_header_height),
                    )
                }
            }
            .distinctUntilChanged()

    data class ConfigurationBasedDimensions(
        val useSplitShade: Boolean,
        val useLargeScreenHeader: Boolean,
        val marginHorizontal: Int,
        val marginBottom: Int,
        val marginTop: Int,
        val marginTopLargeScreen: Int,
    )
}
