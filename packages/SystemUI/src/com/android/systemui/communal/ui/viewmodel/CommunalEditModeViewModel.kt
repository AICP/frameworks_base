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

package com.android.systemui.communal.ui.viewmodel

import com.android.internal.logging.UiEventLogger
import com.android.systemui.communal.domain.interactor.CommunalInteractor
import com.android.systemui.communal.domain.interactor.CommunalSettingsInteractor
import com.android.systemui.communal.domain.model.CommunalContentModel
import com.android.systemui.communal.shared.log.CommunalUiEvent
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.log.LogBuffer
import com.android.systemui.log.core.Logger
import com.android.systemui.log.dagger.CommunalLog
import com.android.systemui.media.controls.ui.view.MediaHost
import com.android.systemui.media.dagger.MediaModule
import javax.inject.Inject
import javax.inject.Named
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

/** The view model for communal hub in edit mode. */
@SysUISingleton
class CommunalEditModeViewModel
@Inject
constructor(
    private val communalInteractor: CommunalInteractor,
    private val communalSettingsInteractor: CommunalSettingsInteractor,
    @Named(MediaModule.COMMUNAL_HUB) mediaHost: MediaHost,
    private val uiEventLogger: UiEventLogger,
    @CommunalLog logBuffer: LogBuffer,
) : BaseCommunalViewModel(communalInteractor, mediaHost) {

    private val logger = Logger(logBuffer, "CommunalEditModeViewModel")

    override val isEditMode = true

    // Only widgets are editable. The CTA tile comes last in the list and remains visible.
    override val communalContent: Flow<List<CommunalContentModel>> =
        communalInteractor.widgetContent
            .map { widgets -> widgets + listOf(CommunalContentModel.CtaTileInEditMode()) }
            .onEach { models ->
                logger.d({ "Content updated: $str1" }) { str1 = models.joinToString { it.key } }
            }

    private val _reorderingWidgets = MutableStateFlow(false)

    override val reorderingWidgets: StateFlow<Boolean>
        get() = _reorderingWidgets

    override fun onDeleteWidget(id: Int) = communalInteractor.deleteWidget(id)

    override fun onReorderWidgets(widgetIdToPriorityMap: Map<Int, Int>) =
        communalInteractor.updateWidgetOrder(widgetIdToPriorityMap)

    override fun onReorderWidgetStart() {
        // Clear selection status
        setSelectedKey(null)
        _reorderingWidgets.value = true
        uiEventLogger.log(CommunalUiEvent.COMMUNAL_HUB_REORDER_WIDGET_START)
    }

    override fun onReorderWidgetEnd() {
        _reorderingWidgets.value = false
        uiEventLogger.log(CommunalUiEvent.COMMUNAL_HUB_REORDER_WIDGET_FINISH)
    }

    override fun onReorderWidgetCancel() {
        _reorderingWidgets.value = false
        uiEventLogger.log(CommunalUiEvent.COMMUNAL_HUB_REORDER_WIDGET_CANCEL)
    }

    /** Returns the widget categories to show on communal hub. */
    val getCommunalWidgetCategories: Int
        get() = communalSettingsInteractor.communalWidgetCategories.value

    /** Sets whether edit mode is currently open */
    fun setEditModeOpen(isOpen: Boolean) = communalInteractor.setEditModeOpen(isOpen)
}
