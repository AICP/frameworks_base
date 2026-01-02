/*
 * SPDX-FileCopyrightText: DerpFest AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.statusbar.pipeline.netspeed.ui.model

import com.android.systemui.common.shared.model.Icon

sealed interface NetworkSpeedIcon {
    data class Visible(val icon: Icon): NetworkSpeedIcon
    object Hidden: NetworkSpeedIcon
}
