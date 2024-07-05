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

package com.android.systemui.volume.panel.dagger

import com.android.systemui.volume.panel.component.anc.AncModule
import com.android.systemui.volume.panel.component.bottombar.BottomBarModule
import com.android.systemui.volume.panel.component.captioning.CaptioningModule
import com.android.systemui.volume.panel.component.mediaoutput.MediaOutputModule
import com.android.systemui.volume.panel.component.volume.VolumeSlidersModule
import com.android.systemui.volume.panel.dagger.factory.VolumePanelComponentFactory
import com.android.systemui.volume.panel.dagger.scope.VolumePanelScope
import com.android.systemui.volume.panel.domain.DomainModule
import com.android.systemui.volume.panel.domain.interactor.ComponentsInteractor
import com.android.systemui.volume.panel.ui.UiModule
import com.android.systemui.volume.panel.ui.composable.ComponentsFactory
import com.android.systemui.volume.panel.ui.layout.ComponentsLayoutManager
import com.android.systemui.volume.panel.ui.viewmodel.VolumePanelViewModel
import dagger.BindsInstance
import dagger.Subcomponent
import kotlinx.coroutines.CoroutineScope

/**
 * Core Volume Panel dagger component. It's managed by [VolumePanelViewModel] and lives alongside
 * it.
 */
@VolumePanelScope
@Subcomponent(
    modules =
        [
            // Volume Panel infra modules
            CoroutineModule::class,
            DefaultMultibindsModule::class,
            DomainModule::class,
            UiModule::class,
            // Components modules
            BottomBarModule::class,
            AncModule::class,
            VolumeSlidersModule::class,
            CaptioningModule::class,
            MediaOutputModule::class,
        ]
)
interface VolumePanelComponent {

    fun coroutineScope(): CoroutineScope

    fun componentsInteractor(): ComponentsInteractor

    fun componentsFactory(): ComponentsFactory

    fun componentsLayoutManager(): ComponentsLayoutManager

    @Subcomponent.Factory
    interface Factory : VolumePanelComponentFactory {

        override fun create(@BindsInstance viewModel: VolumePanelViewModel): VolumePanelComponent
    }
}
