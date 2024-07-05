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

package com.android.systemui.bouncer.domain.interactor

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.authentication.data.repository.FakeAuthenticationRepository
import com.android.systemui.authentication.data.repository.fakeAuthenticationRepository
import com.android.systemui.authentication.domain.interactor.AuthenticationResult
import com.android.systemui.authentication.domain.interactor.authenticationInteractor
import com.android.systemui.authentication.shared.model.AuthenticationMethodModel
import com.android.systemui.authentication.shared.model.AuthenticationPatternCoordinate
import com.android.systemui.coroutines.collectLastValue
import com.android.systemui.coroutines.collectValues
import com.android.systemui.deviceentry.domain.interactor.deviceEntryFaceAuthInteractor
import com.android.systemui.keyguard.data.repository.fakeDeviceEntryFaceAuthRepository
import com.android.systemui.kosmos.testScope
import com.android.systemui.power.data.repository.fakePowerRepository
import com.android.systemui.res.R
import com.android.systemui.scene.shared.flag.fakeSceneContainerFlags
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.MockitoAnnotations

@OptIn(ExperimentalCoroutinesApi::class)
@SmallTest
@RunWith(AndroidJUnit4::class)
class BouncerInteractorTest : SysuiTestCase() {

    private val kosmos = testKosmos().apply { fakeSceneContainerFlags.enabled = true }
    private val testScope = kosmos.testScope
    private val authenticationInteractor = kosmos.authenticationInteractor

    private lateinit var underTest: BouncerInteractor

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)

        overrideResource(R.string.keyguard_enter_your_pin, MESSAGE_ENTER_YOUR_PIN)
        overrideResource(R.string.keyguard_enter_your_password, MESSAGE_ENTER_YOUR_PASSWORD)
        overrideResource(R.string.keyguard_enter_your_pattern, MESSAGE_ENTER_YOUR_PATTERN)
        overrideResource(R.string.kg_wrong_pin, MESSAGE_WRONG_PIN)
        overrideResource(R.string.kg_wrong_password, MESSAGE_WRONG_PASSWORD)
        overrideResource(R.string.kg_wrong_pattern, MESSAGE_WRONG_PATTERN)

        underTest = kosmos.bouncerInteractor
    }

    @Test
    fun pinAuthMethod() =
        testScope.runTest {
            val message by collectLastValue(underTest.message)

            kosmos.fakeAuthenticationRepository.setAuthenticationMethod(
                AuthenticationMethodModel.Pin
            )
            runCurrent()
            underTest.clearMessage()
            assertThat(message).isNull()

            underTest.resetMessage()
            assertThat(message).isEqualTo(MESSAGE_ENTER_YOUR_PIN)

            // Wrong input.
            assertThat(underTest.authenticate(listOf(9, 8, 7)))
                .isEqualTo(AuthenticationResult.FAILED)
            assertThat(message).isEqualTo(MESSAGE_WRONG_PIN)

            underTest.resetMessage()
            assertThat(message).isEqualTo(MESSAGE_ENTER_YOUR_PIN)

            // Correct input.
            assertThat(underTest.authenticate(FakeAuthenticationRepository.DEFAULT_PIN))
                .isEqualTo(AuthenticationResult.SUCCEEDED)
        }

    @Test
    fun pinAuthMethod_sim_skipsAuthentication() =
        testScope.runTest {
            kosmos.fakeAuthenticationRepository.setAuthenticationMethod(
                AuthenticationMethodModel.Sim
            )
            runCurrent()

            // We rely on TelephonyManager to authenticate the sim card.
            // Additionally, authenticating the sim card does not unlock the device.
            // Thus, when auth method is sim, we expect to skip here.
            assertThat(underTest.authenticate(FakeAuthenticationRepository.DEFAULT_PIN))
                .isEqualTo(AuthenticationResult.SKIPPED)
        }

    @Test
    fun pinAuthMethod_tryAutoConfirm_withAutoConfirmPin() =
        testScope.runTest {
            val isAutoConfirmEnabled by collectLastValue(underTest.isAutoConfirmEnabled)

            kosmos.fakeAuthenticationRepository.setAuthenticationMethod(
                AuthenticationMethodModel.Pin
            )
            runCurrent()
            kosmos.fakeAuthenticationRepository.setAutoConfirmFeatureEnabled(true)
            assertThat(isAutoConfirmEnabled).isTrue()

            // Incomplete input.
            assertThat(underTest.authenticate(listOf(1, 2), tryAutoConfirm = true))
                .isEqualTo(AuthenticationResult.SKIPPED)

            // Wrong 6-digit pin
            assertThat(underTest.authenticate(listOf(1, 2, 3, 5, 5, 6), tryAutoConfirm = true))
                .isEqualTo(AuthenticationResult.FAILED)

            // Correct input.
            assertThat(
                    underTest.authenticate(
                        FakeAuthenticationRepository.DEFAULT_PIN,
                        tryAutoConfirm = true
                    )
                )
                .isEqualTo(AuthenticationResult.SUCCEEDED)
        }

    @Test
    fun pinAuthMethod_tryAutoConfirm_withoutAutoConfirmPin() =
        testScope.runTest {
            val message by collectLastValue(underTest.message)

            kosmos.fakeAuthenticationRepository.setAuthenticationMethod(
                AuthenticationMethodModel.Pin
            )
            runCurrent()

            // Incomplete input.
            assertThat(underTest.authenticate(listOf(1, 2), tryAutoConfirm = true))
                .isEqualTo(AuthenticationResult.SKIPPED)
            assertThat(message).isNull()

            // Correct input.
            assertThat(
                    underTest.authenticate(
                        FakeAuthenticationRepository.DEFAULT_PIN,
                        tryAutoConfirm = true
                    )
                )
                .isEqualTo(AuthenticationResult.SKIPPED)
            assertThat(message).isNull()
        }

    @Test
    fun passwordAuthMethod() =
        testScope.runTest {
            val message by collectLastValue(underTest.message)
            kosmos.fakeAuthenticationRepository.setAuthenticationMethod(
                AuthenticationMethodModel.Password
            )
            runCurrent()

            underTest.resetMessage()
            assertThat(message).isEqualTo(MESSAGE_ENTER_YOUR_PASSWORD)

            // Wrong input.
            assertThat(underTest.authenticate("alohamora".toList()))
                .isEqualTo(AuthenticationResult.FAILED)
            assertThat(message).isEqualTo(MESSAGE_WRONG_PASSWORD)

            underTest.resetMessage()
            assertThat(message).isEqualTo(MESSAGE_ENTER_YOUR_PASSWORD)

            // Too short input.
            assertThat(
                    underTest.authenticate(
                        buildList {
                            repeat(kosmos.fakeAuthenticationRepository.minPasswordLength - 1) { time
                                ->
                                add("$time")
                            }
                        }
                    )
                )
                .isEqualTo(AuthenticationResult.SKIPPED)
            assertThat(message).isEqualTo(MESSAGE_WRONG_PASSWORD)

            // Correct input.
            assertThat(underTest.authenticate("password".toList()))
                .isEqualTo(AuthenticationResult.SUCCEEDED)
        }

    @Test
    fun patternAuthMethod() =
        testScope.runTest {
            val message by collectLastValue(underTest.message)
            kosmos.fakeAuthenticationRepository.setAuthenticationMethod(
                AuthenticationMethodModel.Pattern
            )
            runCurrent()
            underTest.resetMessage()
            assertThat(message).isEqualTo(MESSAGE_ENTER_YOUR_PATTERN)

            // Wrong input.
            val wrongPattern =
                listOf(
                    AuthenticationPatternCoordinate(1, 2),
                    AuthenticationPatternCoordinate(1, 1),
                    AuthenticationPatternCoordinate(0, 0),
                    AuthenticationPatternCoordinate(0, 1),
                )
            assertThat(wrongPattern).isNotEqualTo(FakeAuthenticationRepository.PATTERN)
            assertThat(wrongPattern.size)
                .isAtLeast(kosmos.fakeAuthenticationRepository.minPatternLength)
            assertThat(underTest.authenticate(wrongPattern)).isEqualTo(AuthenticationResult.FAILED)
            assertThat(message).isEqualTo(MESSAGE_WRONG_PATTERN)

            underTest.resetMessage()
            assertThat(message).isEqualTo(MESSAGE_ENTER_YOUR_PATTERN)

            // Too short input.
            val tooShortPattern =
                FakeAuthenticationRepository.PATTERN.subList(
                    0,
                    kosmos.fakeAuthenticationRepository.minPatternLength - 1
                )
            assertThat(underTest.authenticate(tooShortPattern))
                .isEqualTo(AuthenticationResult.SKIPPED)
            assertThat(message).isEqualTo(MESSAGE_WRONG_PATTERN)

            underTest.resetMessage()
            assertThat(message).isEqualTo(MESSAGE_ENTER_YOUR_PATTERN)

            // Correct input.
            assertThat(underTest.authenticate(FakeAuthenticationRepository.PATTERN))
                .isEqualTo(AuthenticationResult.SUCCEEDED)
        }

    @Test
    fun lockoutStarted() =
        testScope.runTest {
            val lockoutStartedEvents by collectValues(underTest.onLockoutStarted)
            val message by collectLastValue(underTest.message)

            kosmos.fakeAuthenticationRepository.setAuthenticationMethod(
                AuthenticationMethodModel.Pin
            )
            assertThat(lockoutStartedEvents).isEmpty()

            // Try the wrong PIN repeatedly, until lockout is triggered:
            repeat(FakeAuthenticationRepository.MAX_FAILED_AUTH_TRIES_BEFORE_LOCKOUT) { times ->
                // Wrong PIN.
                assertThat(underTest.authenticate(listOf(6, 7, 8, 9)))
                    .isEqualTo(AuthenticationResult.FAILED)
                if (times < FakeAuthenticationRepository.MAX_FAILED_AUTH_TRIES_BEFORE_LOCKOUT - 1) {
                    assertThat(lockoutStartedEvents).isEmpty()
                    assertThat(message).isNotEmpty()
                }
            }
            assertThat(authenticationInteractor.lockoutEndTimestamp).isNotNull()
            assertThat(lockoutStartedEvents.size).isEqualTo(1)
            assertThat(message).isNull()

            // Advance the time to finish the lockout:
            advanceTimeBy(FakeAuthenticationRepository.LOCKOUT_DURATION_SECONDS.seconds)
            assertThat(authenticationInteractor.lockoutEndTimestamp).isNull()
            assertThat(message).isNull()
            assertThat(lockoutStartedEvents.size).isEqualTo(1)

            // Trigger lockout again:
            repeat(FakeAuthenticationRepository.MAX_FAILED_AUTH_TRIES_BEFORE_LOCKOUT) {
                // Wrong PIN.
                underTest.authenticate(listOf(6, 7, 8, 9))
            }
            assertThat(lockoutStartedEvents.size).isEqualTo(2)
        }

    @Test
    fun imeHiddenEvent_isTriggered() =
        testScope.runTest {
            val imeHiddenEvent by collectLastValue(underTest.onImeHiddenByUser)
            runCurrent()

            underTest.onImeHiddenByUser()
            runCurrent()

            assertThat(imeHiddenEvent).isNotNull()
        }

    @Test
    fun intentionalUserInputEvent_registersTouchEvent() =
        testScope.runTest {
            assertThat(kosmos.fakePowerRepository.userTouchRegistered).isFalse()
            underTest.onIntentionalUserInput()
            assertThat(kosmos.fakePowerRepository.userTouchRegistered).isTrue()
        }

    @Test
    fun intentionalUserInputEvent_notifiesFaceAuthInteractor() =
        testScope.runTest {
            val isFaceAuthRunning by
                collectLastValue(kosmos.fakeDeviceEntryFaceAuthRepository.isAuthRunning)
            kosmos.deviceEntryFaceAuthInteractor.onDeviceLifted()
            runCurrent()
            assertThat(isFaceAuthRunning).isTrue()

            underTest.onIntentionalUserInput()
            runCurrent()

            assertThat(isFaceAuthRunning).isFalse()
        }

    companion object {
        private const val MESSAGE_ENTER_YOUR_PIN = "Enter your PIN"
        private const val MESSAGE_ENTER_YOUR_PASSWORD = "Enter your password"
        private const val MESSAGE_ENTER_YOUR_PATTERN = "Enter your pattern"
        private const val MESSAGE_WRONG_PIN = "Wrong PIN"
        private const val MESSAGE_WRONG_PASSWORD = "Wrong password"
        private const val MESSAGE_WRONG_PATTERN = "Wrong pattern"
    }
}
