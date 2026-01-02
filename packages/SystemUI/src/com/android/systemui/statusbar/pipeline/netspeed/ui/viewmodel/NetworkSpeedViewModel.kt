/*
 * SPDX-FileCopyrightText: DerpFest AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.statusbar.pipeline.netspeed.ui.viewmodel

import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.statusbar.pipeline.netspeed.ui.model.NetworkSpeedIcon
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

@SysUISingleton
class NetworkSpeedViewModel
@Inject
constructor() {
    val icon: Flow<NetworkSpeedIcon> = flowOf(NetworkSpeedIcon.Hidden)
}
