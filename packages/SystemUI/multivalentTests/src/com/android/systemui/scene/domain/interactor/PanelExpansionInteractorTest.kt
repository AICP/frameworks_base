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

@file:OptIn(ExperimentalCoroutinesApi::class)

package com.android.systemui.scene.domain.interactor

import android.platform.test.annotations.DisableFlags
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.compose.animation.scene.ObservableTransitionState
import com.android.compose.animation.scene.SceneKey
import com.android.systemui.Flags.FLAG_SCENE_CONTAINER
import com.android.systemui.SysuiTestCase
import com.android.systemui.coroutines.collectLastValue
import com.android.systemui.deviceentry.data.repository.fakeDeviceEntryRepository
import com.android.systemui.deviceentry.domain.interactor.deviceUnlockedInteractor
import com.android.systemui.flags.EnableSceneContainer
import com.android.systemui.kosmos.testScope
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.scene.shared.model.fakeSceneDataSource
import com.android.systemui.shade.data.repository.fakeShadeRepository
import com.android.systemui.statusbar.notification.stack.ui.viewmodel.panelExpansionInteractor
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class PanelExpansionInteractorTest : SysuiTestCase() {

    private val kosmos = testKosmos()
    private val testScope = kosmos.testScope
    private val deviceEntryRepository = kosmos.fakeDeviceEntryRepository
    private val deviceUnlockedInteractor = kosmos.deviceUnlockedInteractor
    private val sceneInteractor = kosmos.sceneInteractor
    private val transitionState =
        MutableStateFlow<ObservableTransitionState>(
            ObservableTransitionState.Idle(Scenes.Lockscreen)
        )
    private val fakeSceneDataSource = kosmos.fakeSceneDataSource
    private val fakeShadeRepository = kosmos.fakeShadeRepository

    private lateinit var underTest: PanelExpansionInteractor

    @Before
    fun setUp() {
        sceneInteractor.setTransitionState(transitionState)
    }

    @Test
    @EnableSceneContainer
    fun legacyPanelExpansion_whenIdle_whenLocked() =
        testScope.runTest {
            underTest = kosmos.panelExpansionInteractor
            setUnlocked(false)
            val panelExpansion by collectLastValue(underTest.legacyPanelExpansion)

            changeScene(Scenes.Lockscreen) { assertThat(panelExpansion).isEqualTo(1f) }
            assertThat(panelExpansion).isEqualTo(1f)

            changeScene(Scenes.Bouncer) { assertThat(panelExpansion).isEqualTo(1f) }
            assertThat(panelExpansion).isEqualTo(1f)

            changeScene(Scenes.Shade) { assertThat(panelExpansion).isEqualTo(1f) }
            assertThat(panelExpansion).isEqualTo(1f)

            changeScene(Scenes.QuickSettings) { assertThat(panelExpansion).isEqualTo(1f) }
            assertThat(panelExpansion).isEqualTo(1f)

            changeScene(Scenes.Communal) { assertThat(panelExpansion).isEqualTo(1f) }
            assertThat(panelExpansion).isEqualTo(1f)
        }

    @Test
    @EnableSceneContainer
    fun legacyPanelExpansion_whenIdle_whenUnlocked() =
        testScope.runTest {
            underTest = kosmos.panelExpansionInteractor
            setUnlocked(true)
            val panelExpansion by collectLastValue(underTest.legacyPanelExpansion)

            changeScene(Scenes.Gone) { assertThat(panelExpansion).isEqualTo(0f) }
            assertThat(panelExpansion).isEqualTo(0f)

            changeScene(Scenes.Shade) { progress -> assertThat(panelExpansion).isEqualTo(progress) }
            assertThat(panelExpansion).isEqualTo(1f)

            changeScene(Scenes.QuickSettings) {
                // Shade's already expanded, so moving to QS should also be 1f.
                assertThat(panelExpansion).isEqualTo(1f)
            }
            assertThat(panelExpansion).isEqualTo(1f)

            changeScene(Scenes.Communal) { assertThat(panelExpansion).isEqualTo(1f) }
            assertThat(panelExpansion).isEqualTo(1f)
        }

    @Test
    @DisableFlags(FLAG_SCENE_CONTAINER)
    fun legacyPanelExpansion_whenInLegacyMode() =
        testScope.runTest {
            underTest = kosmos.panelExpansionInteractor
            val leet = 0.1337f
            fakeShadeRepository.setLegacyShadeExpansion(leet)
            setUnlocked(false)
            val panelExpansion by collectLastValue(underTest.legacyPanelExpansion)

            changeScene(Scenes.Lockscreen)
            assertThat(panelExpansion).isEqualTo(leet)

            changeScene(Scenes.Bouncer)
            assertThat(panelExpansion).isEqualTo(leet)

            changeScene(Scenes.Shade)
            assertThat(panelExpansion).isEqualTo(leet)

            changeScene(Scenes.QuickSettings)
            assertThat(panelExpansion).isEqualTo(leet)

            changeScene(Scenes.Communal)
            assertThat(panelExpansion).isEqualTo(leet)
        }

    private fun TestScope.setUnlocked(isUnlocked: Boolean) {
        val isDeviceUnlocked by collectLastValue(deviceUnlockedInteractor.isDeviceUnlocked)
        deviceEntryRepository.setUnlocked(isUnlocked)
        runCurrent()

        assertThat(isDeviceUnlocked).isEqualTo(isUnlocked)
    }

    private fun TestScope.changeScene(
        toScene: SceneKey,
        assertDuringProgress: ((progress: Float) -> Unit) = {},
    ) {
        val currentScene by collectLastValue(sceneInteractor.currentScene)
        val progressFlow = MutableStateFlow(0f)
        transitionState.value =
            ObservableTransitionState.Transition(
                fromScene = checkNotNull(currentScene),
                toScene = toScene,
                progress = progressFlow,
                isInitiatedByUserInput = true,
                isUserInputOngoing = flowOf(true),
            )
        runCurrent()
        assertDuringProgress(progressFlow.value)

        progressFlow.value = 0.2f
        runCurrent()
        assertDuringProgress(progressFlow.value)

        progressFlow.value = 0.6f
        runCurrent()
        assertDuringProgress(progressFlow.value)

        progressFlow.value = 1f
        runCurrent()
        assertDuringProgress(progressFlow.value)

        transitionState.value = ObservableTransitionState.Idle(toScene)
        fakeSceneDataSource.changeScene(toScene)
        runCurrent()
        assertDuringProgress(progressFlow.value)

        assertThat(currentScene).isEqualTo(toScene)
    }
}
