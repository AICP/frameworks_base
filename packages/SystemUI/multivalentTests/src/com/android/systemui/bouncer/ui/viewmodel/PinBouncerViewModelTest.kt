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

package com.android.systemui.bouncer.ui.viewmodel

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.compose.animation.scene.SceneKey
import com.android.systemui.SysuiTestCase
import com.android.systemui.authentication.data.repository.FakeAuthenticationRepository
import com.android.systemui.authentication.data.repository.fakeAuthenticationRepository
import com.android.systemui.authentication.domain.interactor.authenticationInteractor
import com.android.systemui.authentication.shared.model.AuthenticationMethodModel
import com.android.systemui.bouncer.data.repository.fakeSimBouncerRepository
import com.android.systemui.bouncer.domain.interactor.bouncerInteractor
import com.android.systemui.bouncer.domain.interactor.simBouncerInteractor
import com.android.systemui.coroutines.collectLastValue
import com.android.systemui.deviceentry.data.repository.fakeDeviceEntryRepository
import com.android.systemui.kosmos.testScope
import com.android.systemui.res.R
import com.android.systemui.scene.domain.interactor.sceneInteractor
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@SmallTest
@RunWith(AndroidJUnit4::class)
class PinBouncerViewModelTest : SysuiTestCase() {

    private val kosmos = testKosmos()
    private val testScope = kosmos.testScope
    private val sceneInteractor by lazy { kosmos.sceneInteractor }
    private val authenticationInteractor by lazy { kosmos.authenticationInteractor }
    private val bouncerInteractor by lazy { kosmos.bouncerInteractor }
    private val bouncerViewModel by lazy { kosmos.bouncerViewModel }
    private lateinit var underTest: PinBouncerViewModel

    @Before
    fun setUp() {
        underTest =
            PinBouncerViewModel(
                applicationContext = context,
                viewModelScope = testScope.backgroundScope,
                interactor = bouncerInteractor,
                isInputEnabled = MutableStateFlow(true).asStateFlow(),
                simBouncerInteractor = kosmos.simBouncerInteractor,
                authenticationMethod = AuthenticationMethodModel.Pin,
            )

        overrideResource(R.string.keyguard_enter_your_pin, ENTER_YOUR_PIN)
        overrideResource(R.string.kg_wrong_pin, WRONG_PIN)
    }

    @Test
    fun onShown() =
        testScope.runTest {
            val message by collectLastValue(bouncerViewModel.message)
            val pin by collectLastValue(underTest.pinInput.map { it.getPin() })
            lockDeviceAndOpenPinBouncer()

            assertThat(message?.text).ignoringCase().isEqualTo(ENTER_YOUR_PIN)
            assertThat(pin).isEmpty()
            assertThat(underTest.authenticationMethod).isEqualTo(AuthenticationMethodModel.Pin)
        }

    @Test
    fun simBouncerViewModel_simAreaIsVisible() =
        testScope.runTest {
            val underTest =
                PinBouncerViewModel(
                    applicationContext = context,
                    viewModelScope = testScope.backgroundScope,
                    interactor = bouncerInteractor,
                    isInputEnabled = MutableStateFlow(true).asStateFlow(),
                    simBouncerInteractor = kosmos.simBouncerInteractor,
                    authenticationMethod = AuthenticationMethodModel.Sim,
                )

            assertThat(underTest.isSimAreaVisible).isTrue()
        }

    @Test
    fun onErrorDialogDismissed_clearsDialogMessage() =
        testScope.runTest {
            val dialogMessage by collectLastValue(underTest.errorDialogMessage)
            kosmos.fakeSimBouncerRepository.setSimVerificationErrorMessage("abc")
            assertThat(dialogMessage).isEqualTo("abc")

            underTest.onErrorDialogDismissed()

            assertThat(dialogMessage).isNull()
        }

    @Test
    fun simBouncerViewModel_autoConfirmEnabled_hintedPinLengthIsNull() =
        testScope.runTest {
            val underTest =
                PinBouncerViewModel(
                    applicationContext = context,
                    viewModelScope = testScope.backgroundScope,
                    interactor = bouncerInteractor,
                    isInputEnabled = MutableStateFlow(true).asStateFlow(),
                    simBouncerInteractor = kosmos.simBouncerInteractor,
                    authenticationMethod = AuthenticationMethodModel.Sim,
                )
            kosmos.fakeAuthenticationRepository.setAutoConfirmFeatureEnabled(true)
            val hintedPinLength by collectLastValue(underTest.hintedPinLength)

            assertThat(hintedPinLength).isNull()
        }

    @Test
    fun onPinButtonClicked() =
        testScope.runTest {
            val message by collectLastValue(bouncerViewModel.message)
            val pin by collectLastValue(underTest.pinInput.map { it.getPin() })
            lockDeviceAndOpenPinBouncer()

            underTest.onPinButtonClicked(1)

            assertThat(message?.text).isEmpty()
            assertThat(pin).containsExactly(1)
        }

    @Test
    fun onBackspaceButtonClicked() =
        testScope.runTest {
            val message by collectLastValue(bouncerViewModel.message)
            val pin by collectLastValue(underTest.pinInput.map { it.getPin() })
            lockDeviceAndOpenPinBouncer()

            underTest.onPinButtonClicked(1)
            assertThat(pin).hasSize(1)

            underTest.onBackspaceButtonClicked()

            assertThat(message?.text).isEmpty()
            assertThat(pin).isEmpty()
        }

    @Test
    fun onPinEdit() =
        testScope.runTest {
            val pin by collectLastValue(underTest.pinInput.map { it.getPin() })
            lockDeviceAndOpenPinBouncer()

            underTest.onPinButtonClicked(1)
            underTest.onPinButtonClicked(2)
            underTest.onPinButtonClicked(3)
            underTest.onBackspaceButtonClicked()
            underTest.onBackspaceButtonClicked()
            underTest.onPinButtonClicked(4)
            underTest.onPinButtonClicked(5)

            assertThat(pin).containsExactly(1, 4, 5).inOrder()
        }

    @Test
    fun onBackspaceButtonLongPressed() =
        testScope.runTest {
            val currentScene by collectLastValue(sceneInteractor.currentScene)
            val message by collectLastValue(bouncerViewModel.message)
            val pin by collectLastValue(underTest.pinInput.map { it.getPin() })
            lockDeviceAndOpenPinBouncer()

            underTest.onPinButtonClicked(1)
            underTest.onPinButtonClicked(2)
            underTest.onPinButtonClicked(3)
            underTest.onPinButtonClicked(4)
            runCurrent()

            underTest.onBackspaceButtonLongPressed()

            assertThat(message?.text).isEmpty()
            assertThat(pin).isEmpty()
            assertThat(currentScene).isEqualTo(Scenes.Bouncer)
        }

    @Test
    fun onAuthenticateButtonClicked_whenCorrect() =
        testScope.runTest {
            val authResult by collectLastValue(authenticationInteractor.onAuthenticationResult)
            lockDeviceAndOpenPinBouncer()

            FakeAuthenticationRepository.DEFAULT_PIN.forEach(underTest::onPinButtonClicked)

            underTest.onAuthenticateButtonClicked()

            assertThat(authResult).isTrue()
        }

    @Test
    fun onAuthenticateButtonClicked_whenWrong() =
        testScope.runTest {
            val currentScene by collectLastValue(sceneInteractor.currentScene)
            val message by collectLastValue(bouncerViewModel.message)
            val pin by collectLastValue(underTest.pinInput.map { it.getPin() })
            lockDeviceAndOpenPinBouncer()

            underTest.onPinButtonClicked(1)
            underTest.onPinButtonClicked(2)
            underTest.onPinButtonClicked(3)
            underTest.onPinButtonClicked(4)
            underTest.onPinButtonClicked(5) // PIN is now wrong!

            underTest.onAuthenticateButtonClicked()

            assertThat(pin).isEmpty()
            assertThat(message?.text).ignoringCase().isEqualTo(WRONG_PIN)
            assertThat(currentScene).isEqualTo(Scenes.Bouncer)
        }

    @Test
    fun onAuthenticateButtonClicked_correctAfterWrong() =
        testScope.runTest {
            val authResult by collectLastValue(authenticationInteractor.onAuthenticationResult)
            val message by collectLastValue(bouncerViewModel.message)
            val pin by collectLastValue(underTest.pinInput.map { it.getPin() })
            lockDeviceAndOpenPinBouncer()

            underTest.onPinButtonClicked(1)
            underTest.onPinButtonClicked(2)
            underTest.onPinButtonClicked(3)
            underTest.onPinButtonClicked(4)
            underTest.onPinButtonClicked(5) // PIN is now wrong!
            underTest.onAuthenticateButtonClicked()
            assertThat(message?.text).ignoringCase().isEqualTo(WRONG_PIN)
            assertThat(pin).isEmpty()
            assertThat(authResult).isFalse()

            // Enter the correct PIN:
            FakeAuthenticationRepository.DEFAULT_PIN.forEach(underTest::onPinButtonClicked)
            assertThat(message?.text).isEmpty()

            underTest.onAuthenticateButtonClicked()

            assertThat(authResult).isTrue()
        }

    @Test
    fun onAutoConfirm_whenCorrect() =
        testScope.runTest {
            kosmos.fakeAuthenticationRepository.setAutoConfirmFeatureEnabled(true)
            val authResult by collectLastValue(authenticationInteractor.onAuthenticationResult)
            lockDeviceAndOpenPinBouncer()

            FakeAuthenticationRepository.DEFAULT_PIN.forEach(underTest::onPinButtonClicked)

            assertThat(authResult).isTrue()
        }

    @Test
    fun onAutoConfirm_whenWrong() =
        testScope.runTest {
            val currentScene by collectLastValue(sceneInteractor.currentScene)
            val message by collectLastValue(bouncerViewModel.message)
            val pin by collectLastValue(underTest.pinInput.map { it.getPin() })
            kosmos.fakeAuthenticationRepository.setAutoConfirmFeatureEnabled(true)
            lockDeviceAndOpenPinBouncer()

            FakeAuthenticationRepository.DEFAULT_PIN.dropLast(1).forEach { digit ->
                underTest.onPinButtonClicked(digit)
            }
            underTest.onPinButtonClicked(
                FakeAuthenticationRepository.DEFAULT_PIN.last() + 1
            ) // PIN is now wrong!

            assertThat(pin).isEmpty()
            assertThat(message?.text).ignoringCase().isEqualTo(WRONG_PIN)
            assertThat(currentScene).isEqualTo(Scenes.Bouncer)
        }

    @Test
    fun onShown_againAfterSceneChange_resetsPin() =
        testScope.runTest {
            val pin by collectLastValue(underTest.pinInput.map { it.getPin() })
            lockDeviceAndOpenPinBouncer()

            // The user types a PIN.
            FakeAuthenticationRepository.DEFAULT_PIN.forEach(underTest::onPinButtonClicked)
            assertThat(pin).isNotEmpty()

            // The user doesn't confirm the PIN, but navigates back to the lockscreen instead.
            switchToScene(Scenes.Lockscreen)

            // The user navigates to the bouncer again.
            switchToScene(Scenes.Bouncer)

            // Ensure the previously-entered PIN is not shown.
            assertThat(pin).isEmpty()
        }

    @Test
    fun backspaceButtonAppearance_withoutAutoConfirm_alwaysShown() =
        testScope.runTest {
            val backspaceButtonAppearance by collectLastValue(underTest.backspaceButtonAppearance)

            kosmos.fakeAuthenticationRepository.setAuthenticationMethod(
                AuthenticationMethodModel.Pin
            )

            assertThat(backspaceButtonAppearance).isEqualTo(ActionButtonAppearance.Shown)
        }

    @Test
    fun backspaceButtonAppearance_withAutoConfirmButNoInput_isHidden() =
        testScope.runTest {
            val backspaceButtonAppearance by collectLastValue(underTest.backspaceButtonAppearance)
            kosmos.fakeAuthenticationRepository.setAuthenticationMethod(
                AuthenticationMethodModel.Pin
            )
            kosmos.fakeAuthenticationRepository.setAutoConfirmFeatureEnabled(true)

            assertThat(backspaceButtonAppearance).isEqualTo(ActionButtonAppearance.Hidden)
        }

    @Test
    fun backspaceButtonAppearance_withAutoConfirmAndInput_isShownQuiet() =
        testScope.runTest {
            val backspaceButtonAppearance by collectLastValue(underTest.backspaceButtonAppearance)
            kosmos.fakeAuthenticationRepository.setAuthenticationMethod(
                AuthenticationMethodModel.Pin
            )
            kosmos.fakeAuthenticationRepository.setAutoConfirmFeatureEnabled(true)

            underTest.onPinButtonClicked(1)

            assertThat(backspaceButtonAppearance).isEqualTo(ActionButtonAppearance.Subtle)
        }

    @Test
    fun confirmButtonAppearance_withoutAutoConfirm_alwaysShown() =
        testScope.runTest {
            val confirmButtonAppearance by collectLastValue(underTest.confirmButtonAppearance)

            kosmos.fakeAuthenticationRepository.setAuthenticationMethod(
                AuthenticationMethodModel.Pin
            )

            assertThat(confirmButtonAppearance).isEqualTo(ActionButtonAppearance.Shown)
        }

    @Test
    fun confirmButtonAppearance_withAutoConfirm_isHidden() =
        testScope.runTest {
            val confirmButtonAppearance by collectLastValue(underTest.confirmButtonAppearance)
            kosmos.fakeAuthenticationRepository.setAuthenticationMethod(
                AuthenticationMethodModel.Pin
            )
            kosmos.fakeAuthenticationRepository.setAutoConfirmFeatureEnabled(true)

            assertThat(confirmButtonAppearance).isEqualTo(ActionButtonAppearance.Hidden)
        }

    @Test
    fun isDigitButtonAnimationEnabled() =
        testScope.runTest {
            val isAnimationEnabled by collectLastValue(underTest.isDigitButtonAnimationEnabled)

            kosmos.fakeAuthenticationRepository.setPinEnhancedPrivacyEnabled(true)
            assertThat(isAnimationEnabled).isFalse()

            kosmos.fakeAuthenticationRepository.setPinEnhancedPrivacyEnabled(false)
            assertThat(isAnimationEnabled).isTrue()
        }

    private fun TestScope.switchToScene(toScene: SceneKey) {
        val currentScene by collectLastValue(sceneInteractor.currentScene)
        val bouncerShown = currentScene != Scenes.Bouncer && toScene == Scenes.Bouncer
        val bouncerHidden = currentScene == Scenes.Bouncer && toScene != Scenes.Bouncer
        sceneInteractor.changeScene(toScene, "reason")
        if (bouncerShown) underTest.onShown()
        if (bouncerHidden) underTest.onHidden()
        runCurrent()

        assertThat(currentScene).isEqualTo(toScene)
    }

    private fun TestScope.lockDeviceAndOpenPinBouncer() {
        kosmos.fakeAuthenticationRepository.setAuthenticationMethod(AuthenticationMethodModel.Pin)
        kosmos.fakeDeviceEntryRepository.setUnlocked(false)
        switchToScene(Scenes.Bouncer)
    }

    companion object {
        private const val ENTER_YOUR_PIN = "Enter your pin"
        private const val WRONG_PIN = "Wrong pin"
    }
}
