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

@file:OptIn(ExperimentalCoroutinesApi::class)

package com.android.systemui.scene.domain.interactor

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.compose.animation.scene.ObservableTransitionState
import com.android.systemui.SysuiTestCase
import com.android.systemui.coroutines.collectLastValue
import com.android.systemui.deviceentry.data.repository.fakeDeviceEntryRepository
import com.android.systemui.kosmos.testScope
import com.android.systemui.scene.data.repository.sceneContainerRepository
import com.android.systemui.scene.sceneContainerConfig
import com.android.systemui.scene.sceneKeys
import com.android.systemui.scene.shared.flag.fakeSceneContainerFlags
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.scene.shared.model.fakeSceneDataSource
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class SceneInteractorTest : SysuiTestCase() {

    private val kosmos = testKosmos()
    private val testScope = kosmos.testScope
    private val fakeSceneDataSource = kosmos.fakeSceneDataSource

    private lateinit var underTest: SceneInteractor

    @Before
    fun setUp() {
        kosmos.fakeSceneContainerFlags.enabled = true
        underTest = kosmos.sceneInteractor
    }

    @Test
    fun allSceneKeys() {
        assertThat(underTest.allSceneKeys()).isEqualTo(kosmos.sceneKeys)
    }

    @Test
    fun changeScene() =
        testScope.runTest {
            val currentScene by collectLastValue(underTest.currentScene)
            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)

            underTest.changeScene(Scenes.Shade, "reason")
            assertThat(currentScene).isEqualTo(Scenes.Shade)
        }

    @Test
    fun changeScene_toGoneWhenUnl_doesNotThrow() =
        testScope.runTest {
            val currentScene by collectLastValue(underTest.currentScene)
            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)

            kosmos.fakeDeviceEntryRepository.setUnlocked(true)
            runCurrent()

            underTest.changeScene(Scenes.Gone, "reason")
            assertThat(currentScene).isEqualTo(Scenes.Gone)
        }

    @Test(expected = IllegalStateException::class)
    fun changeScene_toGoneWhenStillLocked_throws() =
        testScope.runTest {
            kosmos.fakeDeviceEntryRepository.setUnlocked(false)

            underTest.changeScene(Scenes.Gone, "reason")
        }

    @Test
    fun sceneChanged_inDataSource() =
        testScope.runTest {
            val currentScene by collectLastValue(underTest.currentScene)
            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)

            fakeSceneDataSource.changeScene(Scenes.Shade)

            assertThat(currentScene).isEqualTo(Scenes.Shade)
        }

    @Test
    fun transitionState() =
        testScope.runTest {
            val underTest = kosmos.sceneContainerRepository
            val transitionState =
                MutableStateFlow<ObservableTransitionState>(
                    ObservableTransitionState.Idle(Scenes.Lockscreen)
                )
            underTest.setTransitionState(transitionState)
            val reflectedTransitionState by collectLastValue(underTest.transitionState)
            assertThat(reflectedTransitionState).isEqualTo(transitionState.value)

            val progress = MutableStateFlow(1f)
            transitionState.value =
                ObservableTransitionState.Transition(
                    fromScene = Scenes.Lockscreen,
                    toScene = Scenes.Shade,
                    progress = progress,
                    isInitiatedByUserInput = false,
                    isUserInputOngoing = flowOf(false),
                )
            assertThat(reflectedTransitionState).isEqualTo(transitionState.value)

            progress.value = 0.1f
            assertThat(reflectedTransitionState).isEqualTo(transitionState.value)

            progress.value = 0.9f
            assertThat(reflectedTransitionState).isEqualTo(transitionState.value)

            underTest.setTransitionState(null)
            assertThat(reflectedTransitionState)
                .isEqualTo(
                    ObservableTransitionState.Idle(kosmos.sceneContainerConfig.initialSceneKey)
                )
        }

    @Test
    fun transitioningTo() =
        testScope.runTest {
            val transitionState =
                MutableStateFlow<ObservableTransitionState>(
                    ObservableTransitionState.Idle(underTest.currentScene.value)
                )
            underTest.setTransitionState(transitionState)

            val transitionTo by collectLastValue(underTest.transitioningTo)
            assertThat(transitionTo).isNull()

            underTest.changeScene(Scenes.Shade, "reason")
            assertThat(transitionTo).isNull()

            val progress = MutableStateFlow(0f)
            transitionState.value =
                ObservableTransitionState.Transition(
                    fromScene = underTest.currentScene.value,
                    toScene = Scenes.Shade,
                    progress = progress,
                    isInitiatedByUserInput = false,
                    isUserInputOngoing = flowOf(false),
                )
            assertThat(transitionTo).isEqualTo(Scenes.Shade)

            progress.value = 0.5f
            assertThat(transitionTo).isEqualTo(Scenes.Shade)

            progress.value = 1f
            assertThat(transitionTo).isEqualTo(Scenes.Shade)

            transitionState.value = ObservableTransitionState.Idle(Scenes.Shade)
            assertThat(transitionTo).isNull()
        }

    @Test
    fun isTransitionUserInputOngoing_idle_false() =
        testScope.runTest {
            val transitionState =
                MutableStateFlow<ObservableTransitionState>(
                    ObservableTransitionState.Idle(Scenes.Shade)
                )
            val isTransitionUserInputOngoing by
                collectLastValue(underTest.isTransitionUserInputOngoing)
            underTest.setTransitionState(transitionState)

            assertThat(isTransitionUserInputOngoing).isFalse()
        }

    @Test
    fun isTransitionUserInputOngoing_transition_true() =
        testScope.runTest {
            val transitionState =
                MutableStateFlow<ObservableTransitionState>(
                    ObservableTransitionState.Transition(
                        fromScene = Scenes.Shade,
                        toScene = Scenes.Lockscreen,
                        progress = flowOf(0.5f),
                        isInitiatedByUserInput = true,
                        isUserInputOngoing = flowOf(true),
                    )
                )
            val isTransitionUserInputOngoing by
                collectLastValue(underTest.isTransitionUserInputOngoing)
            underTest.setTransitionState(transitionState)

            assertThat(isTransitionUserInputOngoing).isTrue()
        }

    @Test
    fun isTransitionUserInputOngoing_updateMidTransition_false() =
        testScope.runTest {
            val transitionState =
                MutableStateFlow<ObservableTransitionState>(
                    ObservableTransitionState.Transition(
                        fromScene = Scenes.Shade,
                        toScene = Scenes.Lockscreen,
                        progress = flowOf(0.5f),
                        isInitiatedByUserInput = true,
                        isUserInputOngoing = flowOf(true),
                    )
                )
            val isTransitionUserInputOngoing by
                collectLastValue(underTest.isTransitionUserInputOngoing)
            underTest.setTransitionState(transitionState)

            assertThat(isTransitionUserInputOngoing).isTrue()

            transitionState.value =
                ObservableTransitionState.Transition(
                    fromScene = Scenes.Shade,
                    toScene = Scenes.Lockscreen,
                    progress = flowOf(0.6f),
                    isInitiatedByUserInput = true,
                    isUserInputOngoing = flowOf(false),
                )

            assertThat(isTransitionUserInputOngoing).isFalse()
        }

    @Test
    fun isTransitionUserInputOngoing_updateOnIdle_false() =
        testScope.runTest {
            val transitionState =
                MutableStateFlow<ObservableTransitionState>(
                    ObservableTransitionState.Transition(
                        fromScene = Scenes.Shade,
                        toScene = Scenes.Lockscreen,
                        progress = flowOf(0.5f),
                        isInitiatedByUserInput = true,
                        isUserInputOngoing = flowOf(true),
                    )
                )
            val isTransitionUserInputOngoing by
                collectLastValue(underTest.isTransitionUserInputOngoing)
            underTest.setTransitionState(transitionState)

            assertThat(isTransitionUserInputOngoing).isTrue()

            transitionState.value = ObservableTransitionState.Idle(scene = Scenes.Lockscreen)

            assertThat(isTransitionUserInputOngoing).isFalse()
        }

    @Test
    fun isVisible() =
        testScope.runTest {
            val isVisible by collectLastValue(underTest.isVisible)
            assertThat(isVisible).isTrue()

            underTest.setVisible(false, "reason")
            assertThat(isVisible).isFalse()

            underTest.setVisible(true, "reason")
            assertThat(isVisible).isTrue()
        }

    @Test
    fun isVisible_duringRemoteUserInteraction_forcedVisible() =
        testScope.runTest {
            underTest.setVisible(false, "reason")
            val isVisible by collectLastValue(underTest.isVisible)
            assertThat(isVisible).isFalse()
            underTest.onRemoteUserInteractionStarted("reason")
            assertThat(isVisible).isTrue()

            underTest.onUserInteractionFinished()

            assertThat(isVisible).isFalse()
        }
}
