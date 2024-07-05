/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.systemui.keyguard.domain.interactor

import android.app.StatusBarManager
import androidx.test.filters.SmallTest
import com.android.compose.animation.scene.ObservableTransitionState
import com.android.keyguard.KeyguardSecurityModel
import com.android.keyguard.KeyguardSecurityModel.SecurityMode.PIN
import com.android.systemui.Flags.FLAG_COMMUNAL_HUB
import com.android.systemui.Flags.FLAG_KEYGUARD_WM_STATE_REFACTOR
import com.android.systemui.SysuiTestCase
import com.android.systemui.bouncer.data.repository.fakeKeyguardBouncerRepository
import com.android.systemui.communal.domain.interactor.communalInteractor
import com.android.systemui.communal.shared.model.CommunalScenes
import com.android.systemui.flags.FakeFeatureFlags
import com.android.systemui.keyguard.data.repository.FakeKeyguardTransitionRepository
import com.android.systemui.keyguard.data.repository.fakeCommandQueue
import com.android.systemui.keyguard.data.repository.fakeKeyguardRepository
import com.android.systemui.keyguard.data.repository.fakeKeyguardTransitionRepository
import com.android.systemui.keyguard.shared.model.BiometricUnlockModel
import com.android.systemui.keyguard.shared.model.DozeStateModel
import com.android.systemui.keyguard.shared.model.DozeTransitionModel
import com.android.systemui.keyguard.shared.model.KeyguardState
import com.android.systemui.keyguard.shared.model.StatusBarState
import com.android.systemui.keyguard.shared.model.TransitionState
import com.android.systemui.keyguard.shared.model.TransitionStep
import com.android.systemui.keyguard.util.KeyguardTransitionRepositorySpySubject.Companion.assertThat
import com.android.systemui.kosmos.testDispatcher
import com.android.systemui.kosmos.testScope
import com.android.systemui.power.domain.interactor.PowerInteractor.Companion.setAsleepForTest
import com.android.systemui.power.domain.interactor.PowerInteractor.Companion.setAwakeForTest
import com.android.systemui.power.domain.interactor.powerInteractor
import com.android.systemui.shade.data.repository.fakeShadeRepository
import com.android.systemui.statusbar.commandQueue
import com.android.systemui.testKosmos
import com.android.systemui.user.domain.interactor.SelectedUserInteractor
import com.android.systemui.util.mockito.any
import com.android.systemui.util.mockito.whenever
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mock
import org.mockito.Mockito.clearInvocations
import org.mockito.Mockito.never
import org.mockito.Mockito.reset
import org.mockito.Mockito.spy
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations

/**
 * Class for testing user journeys through the interactors. They will all be activated during setup,
 * to ensure the expected transitions are still triggered.
 */
@ExperimentalCoroutinesApi
@SmallTest
@RunWith(JUnit4::class)
class KeyguardTransitionScenariosTest : SysuiTestCase() {
    private val kosmos =
        testKosmos().apply {
            fakeKeyguardTransitionRepository = spy(FakeKeyguardTransitionRepository())
            this.commandQueue = fakeCommandQueue
        }
    private val testScope = kosmos.testScope

    private val keyguardRepository = kosmos.fakeKeyguardRepository
    private val bouncerRepository = kosmos.fakeKeyguardBouncerRepository
    private var commandQueue = kosmos.fakeCommandQueue
    private val shadeRepository = kosmos.fakeShadeRepository
    private val transitionRepository = kosmos.fakeKeyguardTransitionRepository
    private val transitionInteractor = kosmos.keyguardTransitionInteractor
    private lateinit var featureFlags: FakeFeatureFlags

    // Used to verify transition requests for test output
    @Mock private lateinit var keyguardSecurityModel: KeyguardSecurityModel
    @Mock private lateinit var mSelectedUserInteractor: SelectedUserInteractor

    private val fromLockscreenTransitionInteractor = kosmos.fromLockscreenTransitionInteractor
    private lateinit var fromDreamingTransitionInteractor: FromDreamingTransitionInteractor
    private lateinit var fromDozingTransitionInteractor: FromDozingTransitionInteractor
    private lateinit var fromOccludedTransitionInteractor: FromOccludedTransitionInteractor
    private lateinit var fromGoneTransitionInteractor: FromGoneTransitionInteractor
    private lateinit var fromAodTransitionInteractor: FromAodTransitionInteractor
    private lateinit var fromAlternateBouncerTransitionInteractor:
        FromAlternateBouncerTransitionInteractor
    private val fromPrimaryBouncerTransitionInteractor =
        kosmos.fromPrimaryBouncerTransitionInteractor
    private lateinit var fromDreamingLockscreenHostedTransitionInteractor:
        FromDreamingLockscreenHostedTransitionInteractor
    private lateinit var fromGlanceableHubTransitionInteractor:
        FromGlanceableHubTransitionInteractor

    private val powerInteractor = kosmos.powerInteractor
    private val keyguardInteractor = kosmos.keyguardInteractor
    private val communalInteractor = kosmos.communalInteractor

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)

        whenever(keyguardSecurityModel.getSecurityMode(anyInt())).thenReturn(PIN)

        mSetFlagsRule.enableFlags(FLAG_COMMUNAL_HUB)
        featureFlags = FakeFeatureFlags()

        val glanceableHubTransitions =
            GlanceableHubTransitions(
                bgDispatcher = kosmos.testDispatcher,
                transitionInteractor = transitionInteractor,
                transitionRepository = transitionRepository,
                communalInteractor = communalInteractor
            )

        fromLockscreenTransitionInteractor.start()
        fromPrimaryBouncerTransitionInteractor.start()

        fromDreamingTransitionInteractor =
            FromDreamingTransitionInteractor(
                    scope = testScope,
                    bgDispatcher = kosmos.testDispatcher,
                    mainDispatcher = kosmos.testDispatcher,
                    keyguardInteractor = keyguardInteractor,
                    transitionRepository = transitionRepository,
                    transitionInteractor = transitionInteractor,
                    glanceableHubTransitions = glanceableHubTransitions,
                )
                .apply { start() }

        fromDreamingLockscreenHostedTransitionInteractor =
            FromDreamingLockscreenHostedTransitionInteractor(
                    scope = testScope,
                    bgDispatcher = kosmos.testDispatcher,
                    mainDispatcher = kosmos.testDispatcher,
                    keyguardInteractor = keyguardInteractor,
                    transitionRepository = transitionRepository,
                    transitionInteractor = transitionInteractor,
                )
                .apply { start() }

        fromAodTransitionInteractor =
            FromAodTransitionInteractor(
                    scope = testScope,
                    bgDispatcher = kosmos.testDispatcher,
                    mainDispatcher = kosmos.testDispatcher,
                    keyguardInteractor = keyguardInteractor,
                    transitionRepository = transitionRepository,
                    transitionInteractor = transitionInteractor,
                    powerInteractor = powerInteractor,
                )
                .apply { start() }

        fromGoneTransitionInteractor =
            FromGoneTransitionInteractor(
                    scope = testScope,
                    bgDispatcher = kosmos.testDispatcher,
                    mainDispatcher = kosmos.testDispatcher,
                    keyguardInteractor = keyguardInteractor,
                    transitionRepository = transitionRepository,
                    transitionInteractor = transitionInteractor,
                    powerInteractor = powerInteractor,
                    communalInteractor = communalInteractor,
                )
                .apply { start() }

        fromDozingTransitionInteractor =
            FromDozingTransitionInteractor(
                    scope = testScope,
                    bgDispatcher = kosmos.testDispatcher,
                    mainDispatcher = kosmos.testDispatcher,
                    keyguardInteractor = keyguardInteractor,
                    transitionRepository = transitionRepository,
                    transitionInteractor = transitionInteractor,
                    powerInteractor = powerInteractor,
                    communalInteractor = communalInteractor,
                )
                .apply { start() }

        fromOccludedTransitionInteractor =
            FromOccludedTransitionInteractor(
                    scope = testScope,
                    bgDispatcher = kosmos.testDispatcher,
                    mainDispatcher = kosmos.testDispatcher,
                    keyguardInteractor = keyguardInteractor,
                    transitionRepository = transitionRepository,
                    transitionInteractor = transitionInteractor,
                    powerInteractor = powerInteractor,
                    communalInteractor = communalInteractor,
                )
                .apply { start() }

        fromAlternateBouncerTransitionInteractor =
            FromAlternateBouncerTransitionInteractor(
                    scope = testScope,
                    bgDispatcher = kosmos.testDispatcher,
                    mainDispatcher = kosmos.testDispatcher,
                    keyguardInteractor = keyguardInteractor,
                    transitionRepository = transitionRepository,
                    transitionInteractor = transitionInteractor,
                    communalInteractor = communalInteractor,
                    powerInteractor = powerInteractor,
                )
                .apply { start() }

        fromGlanceableHubTransitionInteractor =
            FromGlanceableHubTransitionInteractor(
                    scope = testScope,
                    bgDispatcher = kosmos.testDispatcher,
                    mainDispatcher = kosmos.testDispatcher,
                    glanceableHubTransitions = glanceableHubTransitions,
                    keyguardInteractor = keyguardInteractor,
                    transitionRepository = transitionRepository,
                    transitionInteractor = transitionInteractor,
                    powerInteractor = powerInteractor,
                )
                .apply { start() }

        mSetFlagsRule.disableFlags(
            FLAG_KEYGUARD_WM_STATE_REFACTOR,
        )
    }

    @Test
    fun lockscreenToPrimaryBouncerViaBouncerShowingCall() =
        testScope.runTest {
            // GIVEN a prior transition has run to LOCKSCREEN
            runTransitionAndSetWakefulness(KeyguardState.OFF, KeyguardState.LOCKSCREEN)

            // WHEN the primary bouncer is set to show
            bouncerRepository.setPrimaryShow(true)
            runCurrent()

            assertThat(transitionRepository)
                .startedTransition(
                    to = KeyguardState.PRIMARY_BOUNCER,
                    from = KeyguardState.LOCKSCREEN,
                    ownerName = "FromLockscreenTransitionInteractor",
                    animatorAssertion = { it.isNotNull() }
                )

            coroutineContext.cancelChildren()
        }

    @Test
    fun occludedToDozing() =
        testScope.runTest {
            // GIVEN a device with AOD not available
            keyguardRepository.setAodAvailable(false)
            runCurrent()

            // GIVEN a prior transition has run to OCCLUDED
            runTransitionAndSetWakefulness(KeyguardState.LOCKSCREEN, KeyguardState.OCCLUDED)

            // WHEN the device begins to sleep
            powerInteractor.setAsleepForTest()
            runCurrent()

            assertThat(transitionRepository)
                .startedTransition(
                    to = KeyguardState.DOZING,
                    from = KeyguardState.OCCLUDED,
                    ownerName = "FromOccludedTransitionInteractor",
                    animatorAssertion = { it.isNotNull() }
                )

            coroutineContext.cancelChildren()
        }

    @Test
    fun occludedToAod() =
        testScope.runTest {
            // GIVEN a device with AOD available
            keyguardRepository.setAodAvailable(true)
            runCurrent()

            // GIVEN a prior transition has run to OCCLUDED
            runTransitionAndSetWakefulness(KeyguardState.LOCKSCREEN, KeyguardState.OCCLUDED)

            // WHEN the device begins to sleep
            powerInteractor.setAsleepForTest()
            runCurrent()

            assertThat(transitionRepository)
                .startedTransition(
                    to = KeyguardState.AOD,
                    from = KeyguardState.OCCLUDED,
                    ownerName = "FromOccludedTransitionInteractor",
                    animatorAssertion = { it.isNotNull() }
                )

            coroutineContext.cancelChildren()
        }

    @Test
    fun lockscreenToDreaming() =
        testScope.runTest {
            // GIVEN a device that is not dreaming or dozing
            keyguardRepository.setDreamingWithOverlay(false)
            keyguardRepository.setDozeTransitionModel(
                DozeTransitionModel(from = DozeStateModel.DOZE, to = DozeStateModel.FINISH)
            )
            runCurrent()

            // GIVEN a prior transition has run to LOCKSCREEN
            runTransitionAndSetWakefulness(KeyguardState.GONE, KeyguardState.LOCKSCREEN)

            // WHEN the device begins to dream
            keyguardRepository.setDreamingWithOverlay(true)
            advanceTimeBy(100L)

            assertThat(transitionRepository)
                .startedTransition(
                    to = KeyguardState.DREAMING,
                    from = KeyguardState.LOCKSCREEN,
                    ownerName = "FromLockscreenTransitionInteractor",
                    animatorAssertion = { it.isNotNull() }
                )

            coroutineContext.cancelChildren()
        }

    @Test
    fun lockscreenToDreamingLockscreenHosted() =
        testScope.runTest {
            // GIVEN a device that is not dreaming or dozing
            keyguardRepository.setDreamingWithOverlay(false)
            keyguardRepository.setDozeTransitionModel(
                DozeTransitionModel(from = DozeStateModel.DOZE, to = DozeStateModel.FINISH)
            )
            runCurrent()

            // GIVEN a prior transition has run to LOCKSCREEN
            runTransitionAndSetWakefulness(KeyguardState.GONE, KeyguardState.LOCKSCREEN)

            // WHEN the device begins to dream and the dream is lockscreen hosted
            keyguardRepository.setDreamingWithOverlay(true)
            keyguardRepository.setIsActiveDreamLockscreenHosted(true)
            advanceTimeBy(100L)

            assertThat(transitionRepository)
                .startedTransition(
                    to = KeyguardState.DREAMING_LOCKSCREEN_HOSTED,
                    from = KeyguardState.LOCKSCREEN,
                    ownerName = "FromLockscreenTransitionInteractor",
                    animatorAssertion = { it.isNotNull() }
                )

            coroutineContext.cancelChildren()
        }

    @Test
    fun lockscreenToDozing() =
        testScope.runTest {
            // GIVEN a device with AOD not available
            keyguardRepository.setAodAvailable(false)
            runCurrent()

            // GIVEN a prior transition has run to LOCKSCREEN
            runTransitionAndSetWakefulness(KeyguardState.GONE, KeyguardState.LOCKSCREEN)

            // WHEN the device begins to sleep
            powerInteractor.setAsleepForTest()
            runCurrent()

            assertThat(transitionRepository)
                .startedTransition(
                    to = KeyguardState.DOZING,
                    from = KeyguardState.LOCKSCREEN,
                    ownerName = "FromLockscreenTransitionInteractor",
                    animatorAssertion = { it.isNotNull() }
                )

            coroutineContext.cancelChildren()
        }

    @Test
    fun lockscreenToAod() =
        testScope.runTest {
            // GIVEN a device with AOD available
            keyguardRepository.setAodAvailable(true)
            runCurrent()

            // GIVEN a prior transition has run to LOCKSCREEN
            runTransitionAndSetWakefulness(KeyguardState.GONE, KeyguardState.LOCKSCREEN)

            // WHEN the device begins to sleep
            powerInteractor.setAsleepForTest()
            runCurrent()

            assertThat(transitionRepository)
                .startedTransition(
                    to = KeyguardState.AOD,
                    from = KeyguardState.LOCKSCREEN,
                    ownerName = "FromLockscreenTransitionInteractor",
                    animatorAssertion = { it.isNotNull() }
                )

            coroutineContext.cancelChildren()
        }

    @Test
    fun dreamingLockscreenHostedToLockscreen() =
        testScope.runTest {
            // GIVEN a device dreaming with the lockscreen hosted dream and not dozing
            keyguardRepository.setIsActiveDreamLockscreenHosted(true)
            keyguardRepository.setDozeTransitionModel(
                DozeTransitionModel(from = DozeStateModel.DOZE, to = DozeStateModel.FINISH)
            )
            runCurrent()

            // GIVEN a prior transition has run to DREAMING_LOCKSCREEN_HOSTED
            runTransitionAndSetWakefulness(
                KeyguardState.GONE,
                KeyguardState.DREAMING_LOCKSCREEN_HOSTED
            )

            // WHEN the lockscreen hosted dream stops
            keyguardRepository.setIsActiveDreamLockscreenHosted(false)
            advanceTimeBy(100L)

            assertThat(transitionRepository)
                .startedTransition(
                    to = KeyguardState.LOCKSCREEN,
                    from = KeyguardState.DREAMING_LOCKSCREEN_HOSTED,
                    ownerName = "FromDreamingLockscreenHostedTransitionInteractor",
                    animatorAssertion = { it.isNotNull() }
                )

            coroutineContext.cancelChildren()
        }

    @Test
    fun dreamingLockscreenHostedToGone() =
        testScope.runTest {
            // GIVEN a prior transition has run to DREAMING_LOCKSCREEN_HOSTED
            runTransitionAndSetWakefulness(
                KeyguardState.GONE,
                KeyguardState.DREAMING_LOCKSCREEN_HOSTED
            )

            // WHEN biometrics succeeds with wake and unlock from dream mode
            keyguardRepository.setBiometricUnlockState(
                BiometricUnlockModel.WAKE_AND_UNLOCK_FROM_DREAM
            )
            runCurrent()

            assertThat(transitionRepository)
                .startedTransition(
                    to = KeyguardState.GONE,
                    from = KeyguardState.DREAMING_LOCKSCREEN_HOSTED,
                    ownerName = "FromDreamingLockscreenHostedTransitionInteractor",
                    animatorAssertion = { it.isNotNull() }
                )

            coroutineContext.cancelChildren()
        }

    @Test
    fun dreamingLockscreenHostedToPrimaryBouncer() =
        testScope.runTest {
            // GIVEN a device dreaming with lockscreen hosted dream and not dozing
            keyguardRepository.setIsActiveDreamLockscreenHosted(true)
            runCurrent()

            // GIVEN a prior transition has run to DREAMING_LOCKSCREEN_HOSTED
            runTransitionAndSetWakefulness(
                KeyguardState.GONE,
                KeyguardState.DREAMING_LOCKSCREEN_HOSTED
            )

            // WHEN the primary bouncer is set to show
            bouncerRepository.setPrimaryShow(true)
            runCurrent()

            assertThat(transitionRepository)
                .startedTransition(
                    to = KeyguardState.PRIMARY_BOUNCER,
                    from = KeyguardState.DREAMING_LOCKSCREEN_HOSTED,
                    ownerName = "FromDreamingLockscreenHostedTransitionInteractor",
                    animatorAssertion = { it.isNotNull() }
                )

            coroutineContext.cancelChildren()
        }

    @Test
    fun dreamingLockscreenHostedToDozing() =
        testScope.runTest {
            // GIVEN a device is dreaming with lockscreen hosted dream
            keyguardRepository.setIsActiveDreamLockscreenHosted(true)
            runCurrent()

            // GIVEN a prior transition has run to DREAMING_LOCKSCREEN_HOSTED
            runTransitionAndSetWakefulness(
                KeyguardState.GONE,
                KeyguardState.DREAMING_LOCKSCREEN_HOSTED
            )

            // WHEN the device begins to sleep
            keyguardRepository.setIsActiveDreamLockscreenHosted(false)
            keyguardRepository.setDozeTransitionModel(
                DozeTransitionModel(from = DozeStateModel.INITIALIZED, to = DozeStateModel.DOZE)
            )
            runCurrent()

            assertThat(transitionRepository)
                .startedTransition(
                    to = KeyguardState.DOZING,
                    from = KeyguardState.DREAMING_LOCKSCREEN_HOSTED,
                    ownerName = "FromDreamingLockscreenHostedTransitionInteractor",
                    animatorAssertion = { it.isNotNull() }
                )

            coroutineContext.cancelChildren()
        }

    @Test
    fun dreamingLockscreenHostedToOccluded() =
        testScope.runTest {
            // GIVEN device is dreaming with lockscreen hosted dream and not occluded
            keyguardRepository.setIsActiveDreamLockscreenHosted(true)
            keyguardRepository.setKeyguardOccluded(false)
            runCurrent()

            // GIVEN a prior transition has run to DREAMING_LOCKSCREEN_HOSTED
            runTransitionAndSetWakefulness(
                KeyguardState.GONE,
                KeyguardState.DREAMING_LOCKSCREEN_HOSTED
            )

            // WHEN the keyguard is occluded and the lockscreen hosted dream stops
            keyguardRepository.setIsActiveDreamLockscreenHosted(false)
            keyguardRepository.setKeyguardOccluded(true)
            runCurrent()

            assertThat(transitionRepository)
                .startedTransition(
                    to = KeyguardState.OCCLUDED,
                    from = KeyguardState.DREAMING_LOCKSCREEN_HOSTED,
                    ownerName = "FromDreamingLockscreenHostedTransitionInteractor",
                    animatorAssertion = { it.isNotNull() }
                )

            coroutineContext.cancelChildren()
        }

    @Test
    fun dozingToLockscreen() =
        testScope.runTest {
            // GIVEN a prior transition has run to DOZING
            runTransitionAndSetWakefulness(KeyguardState.LOCKSCREEN, KeyguardState.DOZING)
            runCurrent()

            // WHEN the device begins to wake
            keyguardRepository.setKeyguardShowing(true)
            powerInteractor.setAwakeForTest()
            advanceTimeBy(60L)

            assertThat(transitionRepository)
                .startedTransition(
                    to = KeyguardState.LOCKSCREEN,
                    from = KeyguardState.DOZING,
                    ownerName = "FromDozingTransitionInteractor",
                    animatorAssertion = { it.isNotNull() }
                )

            coroutineContext.cancelChildren()
        }

    @Test
    fun dozingToLockscreenCannotBeInterruptedByDreaming() =
        testScope.runTest {
            transitionRepository.sendTransitionSteps(
                KeyguardState.LOCKSCREEN,
                KeyguardState.DOZING,
                testScheduler
            )
            // GIVEN a prior transition has started to LOCKSCREEN
            transitionRepository.sendTransitionStep(
                TransitionStep(
                    from = KeyguardState.DOZING,
                    to = KeyguardState.LOCKSCREEN,
                    value = 0f,
                    transitionState = TransitionState.STARTED,
                    ownerName = "KeyguardTransitionScenariosTest",
                )
            )
            runCurrent()
            transitionRepository.sendTransitionStep(
                TransitionStep(
                    from = KeyguardState.DOZING,
                    to = KeyguardState.LOCKSCREEN,
                    value = 0.5f,
                    transitionState = TransitionState.RUNNING,
                    ownerName = "KeyguardTransitionScenariosTest",
                )
            )
            runCurrent()
            reset(transitionRepository)

            // WHEN a signal comes that dreaming is enabled
            keyguardRepository.setDreamingWithOverlay(true)
            advanceTimeBy(100L)

            // THEN the transition is ignored
            verify(transitionRepository, never()).startTransition(any())

            coroutineContext.cancelChildren()
        }

    @Test
    fun dozingToGoneWithUnlock() =
        testScope.runTest {
            // GIVEN a prior transition has run to DOZING
            runTransitionAndSetWakefulness(KeyguardState.LOCKSCREEN, KeyguardState.DOZING)
            runCurrent()

            // WHEN biometrics succeeds with wake and unlock mode
            powerInteractor.setAwakeForTest()
            keyguardRepository.setBiometricUnlockState(BiometricUnlockModel.WAKE_AND_UNLOCK)
            advanceTimeBy(60L)

            assertThat(transitionRepository)
                .startedTransition(
                    to = KeyguardState.GONE,
                    from = KeyguardState.DOZING,
                    ownerName = "FromDozingTransitionInteractor",
                    animatorAssertion = { it.isNotNull() }
                )

            coroutineContext.cancelChildren()
        }

    @Test
    fun dozingToPrimaryBouncer() =
        testScope.runTest {
            // GIVEN a prior transition has run to DOZING
            runTransitionAndSetWakefulness(KeyguardState.LOCKSCREEN, KeyguardState.DOZING)
            runCurrent()

            // WHEN awaked by a request to show the primary bouncer, as can happen if SPFS is
            // touched after boot
            powerInteractor.setAwakeForTest()
            bouncerRepository.setPrimaryShow(true)
            advanceTimeBy(60L)

            assertThat(transitionRepository)
                .startedTransition(
                    to = KeyguardState.PRIMARY_BOUNCER,
                    from = KeyguardState.DOZING,
                    ownerName = "FromDozingTransitionInteractor",
                    animatorAssertion = { it.isNotNull() }
                )

            coroutineContext.cancelChildren()
        }

    /** This handles security method NONE and screen off with lock timeout */
    @Test
    fun dozingToGoneWithKeyguardNotShowing() =
        testScope.runTest {
            // GIVEN a prior transition has run to DOZING
            runTransitionAndSetWakefulness(KeyguardState.LOCKSCREEN, KeyguardState.DOZING)
            runCurrent()

            // WHEN the device wakes up without a keyguard
            keyguardRepository.setKeyguardShowing(false)
            keyguardRepository.setKeyguardDismissible(true)
            powerInteractor.setAwakeForTest()
            advanceTimeBy(60L)

            assertThat(transitionRepository)
                .startedTransition(
                    to = KeyguardState.GONE,
                    from = KeyguardState.DOZING,
                    ownerName = "FromDozingTransitionInteractor",
                    animatorAssertion = { it.isNotNull() }
                )

            coroutineContext.cancelChildren()
        }

    @Test
    fun dozingToGlanceableHub() =
        testScope.runTest {
            // GIVEN a prior transition has run to DOZING
            runTransitionAndSetWakefulness(KeyguardState.GLANCEABLE_HUB, KeyguardState.DOZING)
            runCurrent()

            // GIVEN the device is idle on the glanceable hub
            val idleTransitionState =
                MutableStateFlow<ObservableTransitionState>(
                    ObservableTransitionState.Idle(CommunalScenes.Communal)
                )
            communalInteractor.setTransitionState(idleTransitionState)
            runCurrent()

            // WHEN the device begins to wake
            keyguardRepository.setKeyguardShowing(true)
            powerInteractor.setAwakeForTest()
            advanceTimeBy(60L)

            assertThat(transitionRepository)
                .startedTransition(
                    to = KeyguardState.GLANCEABLE_HUB,
                    from = KeyguardState.DOZING,
                    ownerName = FromDozingTransitionInteractor::class.simpleName,
                    animatorAssertion = { it.isNotNull() }
                )

            coroutineContext.cancelChildren()
        }

    @Test
    fun goneToDozing() =
        testScope.runTest {
            // GIVEN a device with AOD not available
            keyguardRepository.setAodAvailable(false)
            runCurrent()

            // GIVEN a prior transition has run to GONE
            runTransitionAndSetWakefulness(KeyguardState.LOCKSCREEN, KeyguardState.GONE)

            // WHEN the device begins to sleep
            powerInteractor.setAsleepForTest()
            runCurrent()

            assertThat(transitionRepository)
                .startedTransition(
                    to = KeyguardState.DOZING,
                    from = KeyguardState.GONE,
                    ownerName = "FromGoneTransitionInteractor",
                    animatorAssertion = { it.isNotNull() }
                )

            coroutineContext.cancelChildren()
        }

    @Test
    fun goneToAod() =
        testScope.runTest {
            // GIVEN a device with AOD available
            keyguardRepository.setAodAvailable(true)
            runCurrent()

            // GIVEN a prior transition has run to GONE
            runTransitionAndSetWakefulness(KeyguardState.LOCKSCREEN, KeyguardState.GONE)

            // WHEN the device begins to sleep
            powerInteractor.setAsleepForTest()
            runCurrent()

            assertThat(transitionRepository)
                .startedTransition(
                    to = KeyguardState.AOD,
                    from = KeyguardState.GONE,
                    ownerName = "FromGoneTransitionInteractor",
                    animatorAssertion = { it.isNotNull() }
                )

            coroutineContext.cancelChildren()
        }

    @Test
    fun goneToLockscreen() =
        testScope.runTest {
            // GIVEN a prior transition has run to GONE
            runTransitionAndSetWakefulness(KeyguardState.LOCKSCREEN, KeyguardState.GONE)

            // WHEN the keyguard starts to show
            keyguardRepository.setKeyguardShowing(true)
            runCurrent()

            assertThat(transitionRepository)
                .startedTransition(
                    to = KeyguardState.LOCKSCREEN,
                    from = KeyguardState.GONE,
                    ownerName = "FromGoneTransitionInteractor",
                    animatorAssertion = { it.isNotNull() }
                )

            coroutineContext.cancelChildren()
        }

    @Test
    fun goneToDreaming() =
        testScope.runTest {
            // GIVEN a device that is not dreaming or dozing
            keyguardRepository.setDreamingWithOverlay(false)
            keyguardRepository.setDozeTransitionModel(
                DozeTransitionModel(from = DozeStateModel.DOZE, to = DozeStateModel.FINISH)
            )
            runCurrent()

            // GIVEN a prior transition has run to GONE
            runTransitionAndSetWakefulness(KeyguardState.LOCKSCREEN, KeyguardState.GONE)

            // WHEN the device begins to dream
            keyguardRepository.setDreamingWithOverlay(true)
            advanceTimeBy(100L)

            assertThat(transitionRepository)
                .startedTransition(
                    to = KeyguardState.DREAMING,
                    from = KeyguardState.GONE,
                    ownerName = "FromGoneTransitionInteractor",
                    animatorAssertion = { it.isNotNull() }
                )

            coroutineContext.cancelChildren()
        }

    @Test
    fun goneToDreamingLockscreenHosted() =
        testScope.runTest {
            // GIVEN a device that is not dreaming or dozing
            keyguardRepository.setDreamingWithOverlay(false)
            keyguardRepository.setDozeTransitionModel(
                DozeTransitionModel(from = DozeStateModel.DOZE, to = DozeStateModel.FINISH)
            )
            runCurrent()

            // GIVEN a prior transition has run to GONE
            runTransitionAndSetWakefulness(KeyguardState.LOCKSCREEN, KeyguardState.GONE)

            // WHEN the device begins to dream with the lockscreen hosted dream
            keyguardRepository.setDreamingWithOverlay(true)
            keyguardRepository.setIsActiveDreamLockscreenHosted(true)
            advanceTimeBy(100L)

            assertThat(transitionRepository)
                .startedTransition(
                    to = KeyguardState.DREAMING_LOCKSCREEN_HOSTED,
                    from = KeyguardState.GONE,
                    ownerName = "FromGoneTransitionInteractor",
                    animatorAssertion = { it.isNotNull() }
                )

            coroutineContext.cancelChildren()
        }

    @Test
    fun goneToGlanceableHub() =
        testScope.runTest {
            // GIVEN a prior transition has run to GONE
            runTransitionAndSetWakefulness(KeyguardState.LOCKSCREEN, KeyguardState.GONE)

            // GIVEN the device is idle on the glanceable hub
            val idleTransitionState =
                MutableStateFlow<ObservableTransitionState>(
                    ObservableTransitionState.Idle(CommunalScenes.Communal)
                )
            communalInteractor.setTransitionState(idleTransitionState)
            runCurrent()

            // WHEN the keyguard starts to show
            keyguardRepository.setKeyguardShowing(true)
            runCurrent()

            assertThat(transitionRepository)
                .startedTransition(
                    to = KeyguardState.GLANCEABLE_HUB,
                    from = KeyguardState.GONE,
                    ownerName = FromGoneTransitionInteractor::class.simpleName,
                    animatorAssertion = { it.isNotNull() }
                )

            coroutineContext.cancelChildren()
        }

    @Test
    fun alternateBouncerToPrimaryBouncer() =
        testScope.runTest {
            // GIVEN a prior transition has run to ALTERNATE_BOUNCER
            runTransitionAndSetWakefulness(
                KeyguardState.LOCKSCREEN,
                KeyguardState.ALTERNATE_BOUNCER
            )

            // WHEN the alternateBouncer stops showing and then the primary bouncer shows
            bouncerRepository.setPrimaryShow(true)
            runCurrent()

            assertThat(transitionRepository)
                .startedTransition(
                    to = KeyguardState.PRIMARY_BOUNCER,
                    from = KeyguardState.ALTERNATE_BOUNCER,
                    ownerName = "FromAlternateBouncerTransitionInteractor",
                    animatorAssertion = { it.isNotNull() }
                )

            coroutineContext.cancelChildren()
        }

    @Test
    fun alternateBouncerToAod() =
        testScope.runTest {
            // GIVEN a prior transition has run to ALTERNATE_BOUNCER
            bouncerRepository.setAlternateVisible(true)
            runTransitionAndSetWakefulness(
                KeyguardState.LOCKSCREEN,
                KeyguardState.ALTERNATE_BOUNCER
            )

            // GIVEN the primary bouncer isn't showing, aod available and starting to sleep
            bouncerRepository.setPrimaryShow(false)
            keyguardRepository.setAodAvailable(true)
            powerInteractor.setAsleepForTest()

            // WHEN the alternateBouncer stops showing
            bouncerRepository.setAlternateVisible(false)
            advanceTimeBy(200L)

            assertThat(transitionRepository)
                .startedTransition(
                    to = KeyguardState.AOD,
                    from = KeyguardState.ALTERNATE_BOUNCER,
                    ownerName = "FromAlternateBouncerTransitionInteractor",
                    animatorAssertion = { it.isNotNull() }
                )

            coroutineContext.cancelChildren()
        }

    @Test
    fun alternateBouncerToDozing() =
        testScope.runTest {
            // GIVEN a prior transition has run to ALTERNATE_BOUNCER
            bouncerRepository.setAlternateVisible(true)
            runTransitionAndSetWakefulness(
                KeyguardState.LOCKSCREEN,
                KeyguardState.ALTERNATE_BOUNCER
            )

            // GIVEN the primary bouncer isn't showing, aod not available and starting to sleep
            // to sleep
            bouncerRepository.setPrimaryShow(false)
            keyguardRepository.setAodAvailable(false)
            powerInteractor.setAsleepForTest()

            // WHEN the alternateBouncer stops showing
            bouncerRepository.setAlternateVisible(false)
            advanceTimeBy(200L)

            assertThat(transitionRepository)
                .startedTransition(
                    to = KeyguardState.DOZING,
                    from = KeyguardState.ALTERNATE_BOUNCER,
                    ownerName = "FromAlternateBouncerTransitionInteractor",
                    animatorAssertion = { it.isNotNull() }
                )

            coroutineContext.cancelChildren()
        }

    @Test
    fun alternateBouncerToLockscreen() =
        testScope.runTest {
            // GIVEN a prior transition has run to ALTERNATE_BOUNCER
            bouncerRepository.setAlternateVisible(true)
            runTransitionAndSetWakefulness(
                KeyguardState.LOCKSCREEN,
                KeyguardState.ALTERNATE_BOUNCER
            )

            // GIVEN the primary bouncer isn't showing and device not sleeping
            bouncerRepository.setPrimaryShow(false)

            // WHEN the alternateBouncer stops showing
            bouncerRepository.setAlternateVisible(false)
            advanceTimeBy(200L)

            assertThat(transitionRepository)
                .startedTransition(
                    ownerName = "FromAlternateBouncerTransitionInteractor",
                    from = KeyguardState.ALTERNATE_BOUNCER,
                    to = KeyguardState.LOCKSCREEN,
                    animatorAssertion = { it.isNotNull() },
                )

            coroutineContext.cancelChildren()
        }

    @Test
    fun alternateBouncerToGlanceableHub() =
        testScope.runTest {
            // GIVEN a prior transition has run to ALTERNATE_BOUNCER
            bouncerRepository.setAlternateVisible(true)
            runTransitionAndSetWakefulness(
                KeyguardState.LOCKSCREEN,
                KeyguardState.ALTERNATE_BOUNCER
            )

            // GIVEN the primary bouncer isn't showing and device not sleeping
            bouncerRepository.setPrimaryShow(false)

            // GIVEN the device is idle on the glanceable hub
            val idleTransitionState =
                MutableStateFlow<ObservableTransitionState>(
                    ObservableTransitionState.Idle(CommunalScenes.Communal)
                )
            communalInteractor.setTransitionState(idleTransitionState)
            runCurrent()

            // WHEN the alternateBouncer stops showing
            bouncerRepository.setAlternateVisible(false)
            advanceTimeBy(200L)

            // THEN a transition to LOCKSCREEN should occur
            assertThat(transitionRepository)
                .startedTransition(
                    ownerName = FromAlternateBouncerTransitionInteractor::class.simpleName,
                    from = KeyguardState.ALTERNATE_BOUNCER,
                    to = KeyguardState.GLANCEABLE_HUB,
                    animatorAssertion = { it.isNotNull() },
                )

            coroutineContext.cancelChildren()
        }

    @Test
    fun primaryBouncerToAod() =
        testScope.runTest {
            // GIVEN a prior transition has run to PRIMARY_BOUNCER
            bouncerRepository.setPrimaryShow(true)
            runTransitionAndSetWakefulness(KeyguardState.LOCKSCREEN, KeyguardState.PRIMARY_BOUNCER)

            // GIVEN aod available and starting to sleep
            keyguardRepository.setAodAvailable(true)
            powerInteractor.setAsleepForTest()

            // WHEN the primaryBouncer stops showing
            bouncerRepository.setPrimaryShow(false)
            runCurrent()

            // THEN a transition to AOD should occur
            assertThat(transitionRepository)
                .startedTransition(
                    ownerName = "FromPrimaryBouncerTransitionInteractor",
                    from = KeyguardState.PRIMARY_BOUNCER,
                    to = KeyguardState.AOD,
                    animatorAssertion = { it.isNotNull() },
                )

            coroutineContext.cancelChildren()
        }

    @Test
    fun primaryBouncerToDozing() =
        testScope.runTest {
            // GIVEN a prior transition has run to PRIMARY_BOUNCER
            bouncerRepository.setPrimaryShow(true)
            runTransitionAndSetWakefulness(KeyguardState.LOCKSCREEN, KeyguardState.PRIMARY_BOUNCER)

            // GIVEN aod not available and starting to sleep to sleep
            keyguardRepository.setAodAvailable(false)
            powerInteractor.setAsleepForTest()

            // WHEN the primaryBouncer stops showing
            bouncerRepository.setPrimaryShow(false)
            runCurrent()

            // THEN a transition to DOZING should occur
            assertThat(transitionRepository)
                .startedTransition(
                    ownerName = "FromPrimaryBouncerTransitionInteractor",
                    from = KeyguardState.PRIMARY_BOUNCER,
                    to = KeyguardState.DOZING,
                    animatorAssertion = { it.isNotNull() },
                )

            coroutineContext.cancelChildren()
        }

    @Test
    fun primaryBouncerToLockscreen() =
        testScope.runTest {
            // GIVEN a prior transition has run to PRIMARY_BOUNCER
            bouncerRepository.setPrimaryShow(true)
            runTransitionAndSetWakefulness(KeyguardState.LOCKSCREEN, KeyguardState.PRIMARY_BOUNCER)

            // WHEN the primaryBouncer stops showing
            bouncerRepository.setPrimaryShow(false)
            runCurrent()

            // THEN a transition to LOCKSCREEN should occur
            assertThat(transitionRepository)
                .startedTransition(
                    ownerName = "FromPrimaryBouncerTransitionInteractor",
                    from = KeyguardState.PRIMARY_BOUNCER,
                    to = KeyguardState.LOCKSCREEN,
                    animatorAssertion = { it.isNotNull() },
                )

            coroutineContext.cancelChildren()
        }

    @Test
    fun primaryBouncerToGlanceableHub() =
        testScope.runTest {
            // GIVEN a prior transition has run to PRIMARY_BOUNCER
            bouncerRepository.setPrimaryShow(true)
            runTransitionAndSetWakefulness(KeyguardState.LOCKSCREEN, KeyguardState.PRIMARY_BOUNCER)

            // GIVEN the device is idle on the glanceable hub
            val idleTransitionState =
                MutableStateFlow<ObservableTransitionState>(
                    ObservableTransitionState.Idle(CommunalScenes.Communal)
                )
            communalInteractor.setTransitionState(idleTransitionState)
            runCurrent()

            // WHEN the primaryBouncer stops showing
            bouncerRepository.setPrimaryShow(false)
            runCurrent()

            // THEN a transition to LOCKSCREEN should occur
            assertThat(transitionRepository)
                .startedTransition(
                    ownerName = FromPrimaryBouncerTransitionInteractor::class.simpleName,
                    from = KeyguardState.PRIMARY_BOUNCER,
                    to = KeyguardState.GLANCEABLE_HUB,
                    animatorAssertion = { it.isNotNull() },
                )

            coroutineContext.cancelChildren()
        }

    @Test
    fun primaryBouncerToGlanceableHubWhileDreaming() =
        testScope.runTest {
            // GIVEN a prior transition has run to PRIMARY_BOUNCER
            bouncerRepository.setPrimaryShow(true)
            runTransitionAndSetWakefulness(KeyguardState.LOCKSCREEN, KeyguardState.PRIMARY_BOUNCER)

            // GIVEN that we are dreaming and occluded
            keyguardRepository.setDreaming(true)
            keyguardRepository.setKeyguardOccluded(true)

            // GIVEN the device is idle on the glanceable hub
            val idleTransitionState =
                MutableStateFlow<ObservableTransitionState>(
                    ObservableTransitionState.Idle(CommunalScenes.Communal)
                )
            communalInteractor.setTransitionState(idleTransitionState)
            runCurrent()

            // WHEN the primaryBouncer stops showing
            bouncerRepository.setPrimaryShow(false)
            runCurrent()

            // THEN a transition to LOCKSCREEN should occur
            assertThat(transitionRepository)
                .startedTransition(
                    ownerName = FromPrimaryBouncerTransitionInteractor::class.simpleName,
                    from = KeyguardState.PRIMARY_BOUNCER,
                    to = KeyguardState.GLANCEABLE_HUB,
                    animatorAssertion = { it.isNotNull() },
                )

            coroutineContext.cancelChildren()
        }

    @Test
    fun primaryBouncerToDreamingLockscreenHosted() =
        testScope.runTest {
            // GIVEN device dreaming with the lockscreen hosted dream and not dozing
            keyguardRepository.setIsActiveDreamLockscreenHosted(true)

            // GIVEN a prior transition has run to PRIMARY_BOUNCER
            bouncerRepository.setPrimaryShow(true)
            runTransitionAndSetWakefulness(
                KeyguardState.DREAMING_LOCKSCREEN_HOSTED,
                KeyguardState.PRIMARY_BOUNCER
            )

            // WHEN the primary bouncer stops showing and lockscreen hosted dream still active
            bouncerRepository.setPrimaryShow(false)
            runCurrent()

            // THEN a transition back to DREAMING_LOCKSCREEN_HOSTED should occur
            assertThat(transitionRepository)
                .startedTransition(
                    ownerName = "FromPrimaryBouncerTransitionInteractor",
                    from = KeyguardState.PRIMARY_BOUNCER,
                    to = KeyguardState.DREAMING_LOCKSCREEN_HOSTED,
                    animatorAssertion = { it.isNotNull() },
                )

            coroutineContext.cancelChildren()
        }

    @Test
    fun occludedToGone() =
        testScope.runTest {
            // GIVEN a device on lockscreen
            keyguardRepository.setKeyguardShowing(true)
            runCurrent()

            // GIVEN a prior transition has run to OCCLUDED
            runTransitionAndSetWakefulness(KeyguardState.LOCKSCREEN, KeyguardState.OCCLUDED)
            keyguardRepository.setKeyguardOccluded(true)
            runCurrent()

            // WHEN keyguard goes away
            keyguardRepository.setKeyguardShowing(false)
            // AND occlusion ends
            keyguardRepository.setKeyguardOccluded(false)
            runCurrent()

            // THEN a transition to GONE should occur
            assertThat(transitionRepository)
                .startedTransition(
                    ownerName = "FromOccludedTransitionInteractor",
                    from = KeyguardState.OCCLUDED,
                    to = KeyguardState.GONE,
                    animatorAssertion = { it.isNotNull() },
                )

            coroutineContext.cancelChildren()
        }

    @Test
    fun occludedToLockscreen() =
        testScope.runTest {
            // GIVEN a device on lockscreen
            keyguardRepository.setKeyguardShowing(true)
            runCurrent()

            // GIVEN a prior transition has run to OCCLUDED
            runTransitionAndSetWakefulness(KeyguardState.LOCKSCREEN, KeyguardState.OCCLUDED)
            keyguardRepository.setKeyguardOccluded(true)
            runCurrent()

            // WHEN occlusion ends
            keyguardRepository.setKeyguardOccluded(false)
            runCurrent()

            // THEN a transition to LOCKSCREEN should occur
            assertThat(transitionRepository)
                .startedTransition(
                    ownerName = "FromOccludedTransitionInteractor",
                    from = KeyguardState.OCCLUDED,
                    to = KeyguardState.LOCKSCREEN,
                    animatorAssertion = { it.isNotNull() },
                )

            coroutineContext.cancelChildren()
        }

    @Test
    fun occludedToGlanceableHub() =
        testScope.runTest {
            // GIVEN a device on lockscreen
            keyguardRepository.setKeyguardShowing(true)
            runCurrent()

            // GIVEN the device is idle on the glanceable hub
            val idleTransitionState =
                MutableStateFlow<ObservableTransitionState>(
                    ObservableTransitionState.Idle(CommunalScenes.Communal)
                )
            communalInteractor.setTransitionState(idleTransitionState)
            runCurrent()

            // GIVEN a prior transition has run to OCCLUDED
            runTransitionAndSetWakefulness(KeyguardState.GLANCEABLE_HUB, KeyguardState.OCCLUDED)
            keyguardRepository.setKeyguardOccluded(true)
            runCurrent()

            // WHEN occlusion ends
            keyguardRepository.setKeyguardOccluded(false)
            runCurrent()

            // THEN a transition to GLANCEABLE_HUB should occur
            assertThat(transitionRepository)
                .startedTransition(
                    ownerName = FromOccludedTransitionInteractor::class.simpleName,
                    from = KeyguardState.OCCLUDED,
                    to = KeyguardState.GLANCEABLE_HUB,
                    animatorAssertion = { it.isNotNull() },
                )

            coroutineContext.cancelChildren()
        }

    @Test
    fun occludedToAlternateBouncer() =
        testScope.runTest {
            // GIVEN a prior transition has run to OCCLUDED
            runTransitionAndSetWakefulness(KeyguardState.LOCKSCREEN, KeyguardState.OCCLUDED)
            keyguardRepository.setKeyguardOccluded(true)
            runCurrent()

            // WHEN alternate bouncer shows
            bouncerRepository.setAlternateVisible(true)
            runCurrent()

            // THEN a transition to AlternateBouncer should occur
            assertThat(transitionRepository)
                .startedTransition(
                    ownerName = "FromOccludedTransitionInteractor",
                    from = KeyguardState.OCCLUDED,
                    to = KeyguardState.ALTERNATE_BOUNCER,
                    animatorAssertion = { it.isNotNull() },
                )

            coroutineContext.cancelChildren()
        }

    @Test
    fun occludedToPrimaryBouncer() =
        testScope.runTest {
            // GIVEN a prior transition has run to OCCLUDED
            runTransitionAndSetWakefulness(KeyguardState.LOCKSCREEN, KeyguardState.OCCLUDED)
            keyguardRepository.setKeyguardOccluded(true)
            runCurrent()

            // WHEN primary bouncer shows
            bouncerRepository.setPrimaryShow(true)
            runCurrent()

            // THEN a transition to AlternateBouncer should occur
            assertThat(transitionRepository)
                .startedTransition(
                    ownerName = "FromOccludedTransitionInteractor",
                    from = KeyguardState.OCCLUDED,
                    to = KeyguardState.PRIMARY_BOUNCER,
                    animatorAssertion = { it.isNotNull() },
                )

            coroutineContext.cancelChildren()
        }

    @Test
    fun primaryBouncerToOccluded() =
        testScope.runTest {
            // GIVEN a prior transition has run to PRIMARY_BOUNCER
            runTransitionAndSetWakefulness(KeyguardState.LOCKSCREEN, KeyguardState.PRIMARY_BOUNCER)
            bouncerRepository.setPrimaryShow(true)
            runCurrent()

            // WHEN the keyguard is occluded and primary bouncer stops showing
            keyguardRepository.setKeyguardOccluded(true)
            bouncerRepository.setPrimaryShow(false)
            runCurrent()

            // THEN a transition to OCCLUDED should occur
            assertThat(transitionRepository)
                .startedTransition(
                    ownerName = "FromPrimaryBouncerTransitionInteractor",
                    from = KeyguardState.PRIMARY_BOUNCER,
                    to = KeyguardState.OCCLUDED,
                    animatorAssertion = { it.isNotNull() },
                )

            coroutineContext.cancelChildren()
        }

    @Test
    fun dozingToOccluded() =
        testScope.runTest {
            // GIVEN a prior transition has run to DOZING
            runTransitionAndSetWakefulness(KeyguardState.LOCKSCREEN, KeyguardState.DOZING)
            runCurrent()

            // WHEN the keyguard is occluded and device wakes up
            keyguardRepository.setKeyguardOccluded(true)
            keyguardRepository.setKeyguardShowing(true)
            powerInteractor.setAwakeForTest()
            advanceTimeBy(60L)

            // THEN a transition to OCCLUDED should occur
            assertThat(transitionRepository)
                .startedTransition(
                    ownerName = "FromDozingTransitionInteractor",
                    from = KeyguardState.DOZING,
                    to = KeyguardState.OCCLUDED,
                    animatorAssertion = { it.isNotNull() },
                )

            coroutineContext.cancelChildren()
        }

    @Test
    fun dreamingToOccluded() =
        testScope.runTest {
            // GIVEN a prior transition has run to DREAMING
            keyguardRepository.setDreaming(true)
            runTransitionAndSetWakefulness(KeyguardState.LOCKSCREEN, KeyguardState.DREAMING)
            runCurrent()

            // WHEN the keyguard is occluded and device wakes up and is no longer dreaming
            keyguardRepository.setDreaming(false)
            keyguardRepository.setKeyguardOccluded(true)
            powerInteractor.setAwakeForTest()
            runCurrent()

            // THEN a transition to OCCLUDED should occur
            assertThat(transitionRepository)
                .startedTransition(
                    ownerName = "FromDreamingTransitionInteractor",
                    from = KeyguardState.DREAMING,
                    to = KeyguardState.OCCLUDED,
                    animatorAssertion = { it.isNotNull() },
                )

            coroutineContext.cancelChildren()
        }

    @Test
    fun dreamingToAod() =
        testScope.runTest {
            // GIVEN a prior transition has run to DREAMING
            keyguardRepository.setDreaming(true)
            runTransitionAndSetWakefulness(KeyguardState.LOCKSCREEN, KeyguardState.DREAMING)
            runCurrent()

            // WHEN the device starts DOZE_AOD
            keyguardRepository.setDozeTransitionModel(
                DozeTransitionModel(
                    from = DozeStateModel.INITIALIZED,
                    to = DozeStateModel.DOZE_AOD,
                )
            )
            runCurrent()

            // THEN a transition to AOD should occur
            assertThat(transitionRepository)
                .startedTransition(
                    ownerName = "FromDreamingTransitionInteractor",
                    from = KeyguardState.DREAMING,
                    to = KeyguardState.AOD,
                    animatorAssertion = { it.isNotNull() },
                )

            coroutineContext.cancelChildren()
        }

    @Test
    fun dreamingToGlanceableHub() =
        testScope.runTest {
            // GIVEN a prior transition has run to DREAMING
            keyguardRepository.setDreaming(true)
            runTransitionAndSetWakefulness(KeyguardState.LOCKSCREEN, KeyguardState.DREAMING)
            runCurrent()

            // WHEN a transition to the glanceable hub starts
            val currentScene = CommunalScenes.Blank
            val targetScene = CommunalScenes.Communal

            val progress = MutableStateFlow(0f)
            val transitionState =
                MutableStateFlow<ObservableTransitionState>(
                    ObservableTransitionState.Transition(
                        fromScene = currentScene,
                        toScene = targetScene,
                        progress = progress,
                        isInitiatedByUserInput = false,
                        isUserInputOngoing = flowOf(false),
                    )
                )
            communalInteractor.setTransitionState(transitionState)
            progress.value = .1f
            runCurrent()

            assertThat(transitionRepository)
                .startedTransition(
                    ownerName = FromDreamingTransitionInteractor::class.simpleName,
                    from = KeyguardState.DREAMING,
                    to = KeyguardState.GLANCEABLE_HUB,
                    animatorAssertion = { it.isNull() }, // transition should be manually animated
                )

            coroutineContext.cancelChildren()
        }

    @Test
    fun lockscreenToOccluded() =
        testScope.runTest {
            // GIVEN a prior transition has run to LOCKSCREEN
            runTransitionAndSetWakefulness(KeyguardState.GONE, KeyguardState.LOCKSCREEN)
            runCurrent()

            // WHEN the keyguard is occluded
            keyguardRepository.setKeyguardOccluded(true)
            runCurrent()

            // THEN a transition to OCCLUDED should occur
            assertThat(transitionRepository)
                .startedTransition(
                    ownerName = "FromLockscreenTransitionInteractor",
                    from = KeyguardState.LOCKSCREEN,
                    to = KeyguardState.OCCLUDED,
                    animatorAssertion = { it.isNotNull() },
                )

            coroutineContext.cancelChildren()
        }

    @Test
    fun aodToOccluded() =
        testScope.runTest {
            // GIVEN a prior transition has run to AOD
            runTransitionAndSetWakefulness(KeyguardState.LOCKSCREEN, KeyguardState.AOD)
            runCurrent()

            // WHEN the keyguard is occluded
            keyguardRepository.setKeyguardOccluded(true)
            runCurrent()

            // THEN a transition to OCCLUDED should occur
            assertThat(transitionRepository)
                .startedTransition(
                    ownerName = "FromAodTransitionInteractor",
                    from = KeyguardState.AOD,
                    to = KeyguardState.OCCLUDED,
                    animatorAssertion = { it.isNotNull() },
                )

            coroutineContext.cancelChildren()
        }

    @Test
    fun aodToPrimaryBouncer() =
        testScope.runTest {
            // GIVEN a prior transition has run to AOD
            runTransitionAndSetWakefulness(KeyguardState.LOCKSCREEN, KeyguardState.AOD)
            runCurrent()

            // WHEN the primary bouncer is set to show
            bouncerRepository.setPrimaryShow(true)
            runCurrent()

            // THEN a transition to OCCLUDED should occur
            assertThat(transitionRepository)
                .startedTransition(
                    ownerName = "FromAodTransitionInteractor",
                    from = KeyguardState.AOD,
                    to = KeyguardState.PRIMARY_BOUNCER,
                    animatorAssertion = { it.isNotNull() },
                )

            coroutineContext.cancelChildren()
        }

    @Test
    fun lockscreenToOccluded_fromCameraGesture() =
        testScope.runTest {
            // GIVEN a prior transition has run to LOCKSCREEN
            runTransitionAndSetWakefulness(KeyguardState.AOD, KeyguardState.LOCKSCREEN)
            runCurrent()

            // WHEN the device begins to sleep (first power button press)...
            powerInteractor.setAsleepForTest()
            runCurrent()
            reset(transitionRepository)

            // ...AND WHEN the camera gesture is detected quickly afterwards
            commandQueue.doForEachCallback {
                it.onCameraLaunchGestureDetected(
                    StatusBarManager.CAMERA_LAUNCH_SOURCE_POWER_DOUBLE_TAP
                )
            }
            runCurrent()

            // THEN a transition from LOCKSCREEN => OCCLUDED should occur
            assertThat(transitionRepository)
                .startedTransition(
                    ownerName = "FromLockscreenTransitionInteractor",
                    from = KeyguardState.LOCKSCREEN,
                    to = KeyguardState.OCCLUDED,
                    animatorAssertion = { it.isNotNull() },
                )

            coroutineContext.cancelChildren()
        }

    @Test
    fun lockscreenToPrimaryBouncerDragging() =
        testScope.runTest {
            // GIVEN a prior transition has run to LOCKSCREEN
            runTransitionAndSetWakefulness(KeyguardState.AOD, KeyguardState.LOCKSCREEN)
            runCurrent()

            // GIVEN the keyguard is showing locked
            keyguardRepository.setStatusBarState(StatusBarState.KEYGUARD)
            runCurrent()
            shadeRepository.setLegacyShadeTracking(true)
            shadeRepository.setLegacyShadeExpansion(.9f)
            runCurrent()

            // THEN a transition from LOCKSCREEN => PRIMARY_BOUNCER should occur
            assertThat(transitionRepository)
                .startedTransition(
                    ownerName = "FromLockscreenTransitionInteractor",
                    from = KeyguardState.LOCKSCREEN,
                    to = KeyguardState.PRIMARY_BOUNCER,
                    animatorAssertion = { it.isNull() }, // dragging should be manually animated
                )

            // WHEN the user stops dragging and shade is back to expanded
            clearInvocations(transitionRepository)
            runTransitionAndSetWakefulness(KeyguardState.LOCKSCREEN, KeyguardState.PRIMARY_BOUNCER)
            shadeRepository.setLegacyShadeTracking(false)
            shadeRepository.setLegacyShadeExpansion(1f)
            runCurrent()

            // THEN a transition from LOCKSCREEN => PRIMARY_BOUNCER should occur
            assertThat(transitionRepository)
                .startedTransition(
                    from = KeyguardState.PRIMARY_BOUNCER,
                    to = KeyguardState.LOCKSCREEN,
                    animatorAssertion = { it.isNotNull() },
                )

            coroutineContext.cancelChildren()
        }

    @Test
    fun lockscreenToGlanceableHub() =
        testScope.runTest {
            // GIVEN a prior transition has run to LOCKSCREEN
            runTransitionAndSetWakefulness(KeyguardState.AOD, KeyguardState.LOCKSCREEN)
            runCurrent()

            // WHEN a glanceable hub transition starts
            val currentScene = CommunalScenes.Blank
            val targetScene = CommunalScenes.Communal

            val progress = MutableStateFlow(0f)
            val transitionState =
                MutableStateFlow<ObservableTransitionState>(
                    ObservableTransitionState.Transition(
                        fromScene = currentScene,
                        toScene = targetScene,
                        progress = progress,
                        isInitiatedByUserInput = false,
                        isUserInputOngoing = flowOf(false),
                    )
                )
            communalInteractor.setTransitionState(transitionState)
            progress.value = .1f
            runCurrent()

            // THEN a transition from LOCKSCREEN => GLANCEABLE_HUB should occur
            assertThat(transitionRepository)
                .startedTransition(
                    ownerName = FromLockscreenTransitionInteractor::class.simpleName,
                    from = KeyguardState.LOCKSCREEN,
                    to = KeyguardState.GLANCEABLE_HUB,
                    animatorAssertion = { it.isNull() }, // transition should be manually animated
                )

            // WHEN the user stops dragging and the glanceable hub opening is cancelled
            clearInvocations(transitionRepository)
            runTransitionAndSetWakefulness(KeyguardState.LOCKSCREEN, KeyguardState.GLANCEABLE_HUB)
            val idleTransitionState =
                MutableStateFlow<ObservableTransitionState>(
                    ObservableTransitionState.Idle(currentScene)
                )
            communalInteractor.setTransitionState(idleTransitionState)
            runCurrent()

            // THEN a transition from LOCKSCREEN => GLANCEABLE_HUB should occur
            assertThat(transitionRepository)
                .startedTransition(
                    ownerName = FromLockscreenTransitionInteractor::class.simpleName,
                    from = KeyguardState.GLANCEABLE_HUB,
                    to = KeyguardState.LOCKSCREEN,
                )

            coroutineContext.cancelChildren()
        }

    @Test
    fun glanceableHubToLockscreen() =
        testScope.runTest {
            // GIVEN a prior transition has run to GLANCEABLE_HUB
            runTransitionAndSetWakefulness(KeyguardState.LOCKSCREEN, KeyguardState.GLANCEABLE_HUB)
            runCurrent()

            // WHEN a transition away from glanceable hub starts
            val currentScene = CommunalScenes.Communal
            val targetScene = CommunalScenes.Blank

            val progress = MutableStateFlow(0f)
            val transitionState =
                MutableStateFlow<ObservableTransitionState>(
                    ObservableTransitionState.Transition(
                        fromScene = currentScene,
                        toScene = targetScene,
                        progress = progress,
                        isInitiatedByUserInput = false,
                        isUserInputOngoing = flowOf(false),
                    )
                )
            communalInteractor.setTransitionState(transitionState)
            progress.value = .1f
            runCurrent()

            assertThat(transitionRepository)
                .startedTransition(
                    ownerName = FromGlanceableHubTransitionInteractor::class.simpleName,
                    from = KeyguardState.GLANCEABLE_HUB,
                    to = KeyguardState.LOCKSCREEN,
                    animatorAssertion = { it.isNull() }, // transition should be manually animated
                )

            // WHEN the user stops dragging and the glanceable hub closing is cancelled
            clearInvocations(transitionRepository)
            runTransitionAndSetWakefulness(KeyguardState.GLANCEABLE_HUB, KeyguardState.LOCKSCREEN)
            val idleTransitionState =
                MutableStateFlow<ObservableTransitionState>(
                    ObservableTransitionState.Idle(currentScene)
                )
            communalInteractor.setTransitionState(idleTransitionState)
            runCurrent()

            assertThat(transitionRepository)
                .startedTransition(
                    from = KeyguardState.LOCKSCREEN,
                    to = KeyguardState.GLANCEABLE_HUB,
                )

            coroutineContext.cancelChildren()
        }

    @Test
    fun glanceableHubToDozing() =
        testScope.runTest {
            // GIVEN a prior transition has run to GLANCEABLE_HUB
            runTransitionAndSetWakefulness(KeyguardState.LOCKSCREEN, KeyguardState.GLANCEABLE_HUB)

            // WHEN the device begins to sleep
            powerInteractor.setAsleepForTest()
            runCurrent()

            assertThat(transitionRepository)
                .startedTransition(
                    ownerName = FromGlanceableHubTransitionInteractor::class.simpleName,
                    from = KeyguardState.GLANCEABLE_HUB,
                    to = KeyguardState.DOZING,
                    animatorAssertion = { it.isNotNull() },
                )

            coroutineContext.cancelChildren()
        }

    @Test
    fun glanceableHubToPrimaryBouncer() =
        testScope.runTest {
            // GIVEN a prior transition has run to ALTERNATE_BOUNCER
            runTransitionAndSetWakefulness(KeyguardState.LOCKSCREEN, KeyguardState.GLANCEABLE_HUB)

            // WHEN the primary bouncer shows
            bouncerRepository.setPrimaryShow(true)
            runCurrent()

            assertThat(transitionRepository)
                .startedTransition(
                    ownerName = FromGlanceableHubTransitionInteractor::class.simpleName,
                    from = KeyguardState.GLANCEABLE_HUB,
                    to = KeyguardState.PRIMARY_BOUNCER,
                    animatorAssertion = { it.isNotNull() },
                )

            coroutineContext.cancelChildren()
        }

    @Test
    fun glanceableHubToAlternateBouncer() =
        testScope.runTest {
            // GIVEN a prior transition has run to ALTERNATE_BOUNCER
            runTransitionAndSetWakefulness(KeyguardState.LOCKSCREEN, KeyguardState.GLANCEABLE_HUB)

            // WHEN the primary bouncer shows
            bouncerRepository.setAlternateVisible(true)
            runCurrent()

            assertThat(transitionRepository)
                .startedTransition(
                    ownerName = FromGlanceableHubTransitionInteractor::class.simpleName,
                    from = KeyguardState.GLANCEABLE_HUB,
                    to = KeyguardState.ALTERNATE_BOUNCER,
                    animatorAssertion = { it.isNotNull() },
                )

            coroutineContext.cancelChildren()
        }

    @Test
    fun glanceableHubToOccluded() =
        testScope.runTest {
            // GIVEN a prior transition has run to GLANCEABLE_HUB
            runTransitionAndSetWakefulness(KeyguardState.GONE, KeyguardState.GLANCEABLE_HUB)
            runCurrent()

            // GIVEN the device is idle on the glanceable hub
            val idleTransitionState =
                MutableStateFlow<ObservableTransitionState>(
                    ObservableTransitionState.Idle(CommunalScenes.Communal)
                )
            communalInteractor.setTransitionState(idleTransitionState)
            runCurrent()

            // WHEN the keyguard is occluded
            keyguardRepository.setKeyguardOccluded(true)
            runCurrent()

            assertThat(transitionRepository)
                .startedTransition(
                    ownerName = FromGlanceableHubTransitionInteractor::class.simpleName,
                    from = KeyguardState.GLANCEABLE_HUB,
                    to = KeyguardState.OCCLUDED,
                    animatorAssertion = { it.isNotNull() },
                )

            coroutineContext.cancelChildren()
        }

    @Test
    fun glanceableHubToGone() =
        testScope.runTest {
            // GIVEN a prior transition has run to GLANCEABLE_HUB
            runTransitionAndSetWakefulness(KeyguardState.LOCKSCREEN, KeyguardState.GLANCEABLE_HUB)

            // WHEN keyguard goes away
            keyguardRepository.setKeyguardGoingAway(true)
            runCurrent()

            assertThat(transitionRepository)
                .startedTransition(
                    ownerName = FromGlanceableHubTransitionInteractor::class.simpleName,
                    from = KeyguardState.GLANCEABLE_HUB,
                    to = KeyguardState.GONE,
                    animatorAssertion = { it.isNotNull() },
                )

            coroutineContext.cancelChildren()
        }

    @Test
    fun glanceableHubToDreaming() =
        testScope.runTest {
            // GIVEN that we are dreaming and not dozing
            keyguardRepository.setDreaming(true)
            keyguardRepository.setDozeTransitionModel(
                DozeTransitionModel(from = DozeStateModel.DOZE, to = DozeStateModel.FINISH)
            )
            runCurrent()

            // GIVEN a prior transition has run to GLANCEABLE_HUB
            runTransitionAndSetWakefulness(KeyguardState.DREAMING, KeyguardState.GLANCEABLE_HUB)
            runCurrent()

            // WHEN a transition away from glanceable hub starts
            val currentScene = CommunalScenes.Communal
            val targetScene = CommunalScenes.Blank

            val transitionState =
                MutableStateFlow<ObservableTransitionState>(
                    ObservableTransitionState.Transition(
                        fromScene = currentScene,
                        toScene = targetScene,
                        progress = flowOf(0f, 0.1f),
                        isInitiatedByUserInput = false,
                        isUserInputOngoing = flowOf(false),
                    )
                )
            communalInteractor.setTransitionState(transitionState)
            runCurrent()

            assertThat(transitionRepository)
                .startedTransition(
                    ownerName = FromGlanceableHubTransitionInteractor::class.simpleName,
                    from = KeyguardState.GLANCEABLE_HUB,
                    to = KeyguardState.DREAMING,
                    animatorAssertion = { it.isNull() }, // transition should be manually animated
                )

            coroutineContext.cancelChildren()
        }

    private suspend fun TestScope.runTransitionAndSetWakefulness(
        from: KeyguardState,
        to: KeyguardState
    ) {
        transitionRepository.sendTransitionStep(
            TransitionStep(
                from = from,
                to = to,
                value = 0f,
                transitionState = TransitionState.STARTED,
            )
        )
        runCurrent()
        transitionRepository.sendTransitionStep(
            TransitionStep(
                from = from,
                to = to,
                value = 0.5f,
                transitionState = TransitionState.RUNNING,
            )
        )
        runCurrent()
        transitionRepository.sendTransitionStep(
            TransitionStep(
                from = from,
                to = to,
                value = 1f,
                transitionState = TransitionState.FINISHED,
            )
        )
        runCurrent()
        reset(transitionRepository)

        if (KeyguardState.deviceIsAwakeInState(to)) {
            powerInteractor.setAwakeForTest()
        } else {
            powerInteractor.setAsleepForTest()
        }
    }
}
