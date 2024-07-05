/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.systemui.volume.panel.ui.layout

import com.android.systemui.volume.panel.ui.viewmodel.ComponentState
import com.android.systemui.volume.panel.ui.viewmodel.VolumePanelState

/**
 * Lays out components to [ComponentsLayout], that UI uses to render the Volume Panel.
 *
 * Vertical layout shows the list from top to bottom:
 * ```
 * -----
 * | 1 |
 * | 2 |
 * | 3 |
 * | 4 |
 * -----
 * ```
 *
 * Horizontal layout shows the list in a grid from, filling the columns first:
 * ```
 * ----------
 * | 1 || 3 |
 * | 2 || 4 |
 * ----------
 * ```
 */
interface ComponentsLayoutManager {

    fun layout(
        volumePanelState: VolumePanelState,
        components: Collection<ComponentState>,
    ): ComponentsLayout
}
