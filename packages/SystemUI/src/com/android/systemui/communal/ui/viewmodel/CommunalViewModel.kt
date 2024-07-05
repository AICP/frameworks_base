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

import com.android.systemui.communal.domain.interactor.CommunalInteractor
import com.android.systemui.communal.domain.interactor.CommunalTutorialInteractor
import com.android.systemui.communal.domain.model.CommunalContentModel
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.log.LogBuffer
import com.android.systemui.log.core.Logger
import com.android.systemui.log.dagger.CommunalLog
import com.android.systemui.media.controls.ui.controller.MediaHierarchyManager
import com.android.systemui.media.controls.ui.view.MediaHost
import com.android.systemui.media.controls.ui.view.MediaHostState
import com.android.systemui.media.dagger.MediaModule
import com.android.systemui.shade.domain.interactor.ShadeInteractor
import com.android.systemui.util.kotlin.BooleanFlowOperators.not
import javax.inject.Inject
import javax.inject.Named
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/** The default view model used for showing the communal hub. */
@SysUISingleton
class CommunalViewModel
@Inject
constructor(
    @Application private val scope: CoroutineScope,
    private val communalInteractor: CommunalInteractor,
    tutorialInteractor: CommunalTutorialInteractor,
    shadeInteractor: ShadeInteractor,
    @Named(MediaModule.COMMUNAL_HUB) mediaHost: MediaHost,
    @CommunalLog logBuffer: LogBuffer,
) : BaseCommunalViewModel(communalInteractor, mediaHost) {

    private val logger = Logger(logBuffer, "CommunalViewModel")

    @OptIn(ExperimentalCoroutinesApi::class)
    override val communalContent: Flow<List<CommunalContentModel>> =
        tutorialInteractor.isTutorialAvailable
            .flatMapLatest { isTutorialMode ->
                if (isTutorialMode) {
                    return@flatMapLatest flowOf(communalInteractor.tutorialContent)
                }
                combine(
                    communalInteractor.ongoingContent,
                    communalInteractor.widgetContent,
                    communalInteractor.ctaTileContent,
                ) { ongoing, widgets, ctaTile,
                    ->
                    ongoing + widgets + ctaTile
                }
            }
            .onEach { models ->
                logger.d({ "Content updated: $str1" }) { str1 = models.joinToString { it.key } }
            }

    private val _isPopupOnDismissCtaShowing: MutableStateFlow<Boolean> = MutableStateFlow(false)
    override val isPopupOnDismissCtaShowing: Flow<Boolean> =
        _isPopupOnDismissCtaShowing.asStateFlow()

    /** Whether touches should be disabled in communal */
    val touchesAllowed: Flow<Boolean> = not(shadeInteractor.isAnyFullyExpanded)

    init {
        // Initialize our media host for the UMO. This only needs to happen once and must be done
        // before the MediaHierarchyManager attempts to move the UMO to the hub.
        with(mediaHost) {
            expansion = MediaHostState.EXPANDED
            expandedMatchesParentHeight = true
            showsOnlyActiveMedia = true
            falsingProtectionNeeded = false
            init(MediaHierarchyManager.LOCATION_COMMUNAL_HUB)
        }
    }

    override fun onOpenWidgetEditor(preselectedKey: String?) =
        communalInteractor.showWidgetEditor(preselectedKey)

    override fun onDismissCtaTile() {
        scope.launch {
            communalInteractor.dismissCtaTile()
            setPopupOnDismissCtaVisibility(true)
            schedulePopupHiding()
        }
    }

    override fun onHidePopupAfterDismissCta() {
        cancelDelayedPopupHiding()
        setPopupOnDismissCtaVisibility(false)
    }

    private fun setPopupOnDismissCtaVisibility(isVisible: Boolean) {
        _isPopupOnDismissCtaShowing.value = isVisible
    }

    private var delayedHidePopupJob: Job? = null

    private fun schedulePopupHiding() {
        cancelDelayedPopupHiding()
        delayedHidePopupJob =
            scope.launch {
                delay(POPUP_AUTO_HIDE_TIMEOUT_MS)
                onHidePopupAfterDismissCta()
            }
    }

    private fun cancelDelayedPopupHiding() {
        delayedHidePopupJob?.cancel()
        delayedHidePopupJob = null
    }

    companion object {
        const val POPUP_AUTO_HIDE_TIMEOUT_MS = 12000L
    }
}
