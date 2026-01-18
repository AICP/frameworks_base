/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.systemui.qs.tiles.impl.wifi.ui.mapper

import android.content.Context
import android.content.res.Resources
import android.widget.Switch
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.qs.tiles.base.shared.model.QSTileConfig
import com.android.systemui.qs.tiles.base.shared.model.QSTileState
import com.android.systemui.qs.tiles.base.ui.model.QSTileDataToStateMapper
import com.android.systemui.qs.tiles.impl.wifi.domain.model.WifiTileModel
import com.android.systemui.res.R
import com.android.systemui.shade.ShadeDisplayAware
import javax.inject.Inject

/** Maps [WifiTileModel] to [QSTileState]. */
class WifiTileMapper
@Inject
constructor(
    @ShadeDisplayAware private val resources: Resources,
    private val theme: Resources.Theme,
    @ShadeDisplayAware private val context: Context,
) : QSTileDataToStateMapper<WifiTileModel> {

    override fun map(config: QSTileConfig, data: WifiTileModel): QSTileState =
        QSTileState.build(resources, theme, config.uiConfig) {
            label = resources.getString(R.string.quick_settings_wifi_label)
            expandedAccessibilityClass = Switch::class

            secondaryLabel =
                if (data.secondaryLabel != null) {
                    data.secondaryLabel
                } else {
                    null
                }

            stateDescription = secondaryLabel
            contentDescription = "$label,$secondaryLabel"

            icon =
                Icon.Loaded(
                    resources.getDrawable(data.icon.resId, theme),
                    contentDescription = null,
                    data.icon.resId,
                )

            activationState =
                if (data is WifiTileModel.Active) QSTileState.ActivationState.ACTIVE
                else QSTileState.ActivationState.INACTIVE

            supportedActions =
                setOf(
                    QSTileState.UserAction.CLICK,
                    QSTileState.UserAction.LONG_CLICK,
                    QSTileState.UserAction.TOGGLE_CLICK,
                )
        }
}
