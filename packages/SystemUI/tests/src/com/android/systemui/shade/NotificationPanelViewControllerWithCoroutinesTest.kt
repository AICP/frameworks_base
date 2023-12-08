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

package com.android.systemui.shade

import android.os.VibrationEffect
import android.testing.AndroidTestingRunner
import android.testing.TestableLooper
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewStub
import androidx.test.filters.SmallTest
import com.android.internal.util.CollectionUtils
import com.android.keyguard.KeyguardClockSwitch.LARGE
import com.android.systemui.R
import com.android.systemui.flags.Flags.ONE_WAY_HAPTICS_API_MIGRATION
import com.android.systemui.statusbar.StatusBarState.KEYGUARD
import com.android.systemui.statusbar.StatusBarState.SHADE
import com.android.systemui.statusbar.StatusBarState.SHADE_LOCKED
import com.android.systemui.statusbar.VibratorHelper
import com.android.systemui.util.mockito.any
import com.android.systemui.util.mockito.eq
import com.android.systemui.util.mockito.whenever
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Captor
import org.mockito.Mockito.atLeastOnce
import org.mockito.Mockito.clearInvocations
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify

@RunWith(AndroidTestingRunner::class)
@TestableLooper.RunWithLooper(setAsMainLooper = true)
@SmallTest
class NotificationPanelViewControllerWithCoroutinesTest :
    NotificationPanelViewControllerBaseTest() {

    @Captor private lateinit var viewCaptor: ArgumentCaptor<View>

    override fun getMainDispatcher() = Dispatchers.Main.immediate

    private val ADDITIONAL_TAP_REQUIRED_VIBRATION_EFFECT =
        VibrationEffect.get(VibrationEffect.EFFECT_STRENGTH_MEDIUM, false)

    @Test
    fun testDisableUserSwitcherAfterEnabling_returnsViewStubToTheViewHierarchy() = runTest {
        launch(Dispatchers.Main.immediate) { givenViewAttached() }
        advanceUntilIdle()

        whenever(mResources.getBoolean(com.android.internal.R.bool.config_keyguardUserSwitcher))
            .thenReturn(true)
        updateMultiUserSetting(true)
        clearInvocations(mView)

        updateMultiUserSetting(false)

        verify(mView, atLeastOnce()).addView(viewCaptor.capture(), anyInt())
        val userSwitcherStub =
            CollectionUtils.find(
                viewCaptor.getAllValues(),
                { view -> view.getId() == R.id.keyguard_user_switcher_stub }
            )
        assertThat(userSwitcherStub).isNotNull()
        assertThat(userSwitcherStub).isInstanceOf(ViewStub::class.java)
    }

    @Test
    fun testChangeSmallestScreenWidthAndUserSwitchEnabled_inflatesUserSwitchView() = runTest {
        launch(Dispatchers.Main.immediate) { givenViewAttached() }
        advanceUntilIdle()

        whenever(mView.findViewById<View>(R.id.keyguard_user_switcher_view)).thenReturn(null)
        updateSmallestScreenWidth(300)
        whenever(mResources.getBoolean(com.android.internal.R.bool.config_keyguardUserSwitcher))
            .thenReturn(true)
        whenever(mResources.getBoolean(R.bool.qs_show_user_switcher_for_single_user))
            .thenReturn(false)
        whenever(mUserManager.isUserSwitcherEnabled(false)).thenReturn(true)

        updateSmallestScreenWidth(800)

        verify(mUserSwitcherStubView).inflate()
    }

    @Test
    fun testFinishInflate_userSwitcherDisabled_doNotInflateUserSwitchView_initClock() = runTest {
        launch(Dispatchers.Main.immediate) { givenViewAttached() }
        advanceUntilIdle()

        whenever(mResources.getBoolean(com.android.internal.R.bool.config_keyguardUserSwitcher))
            .thenReturn(true)
        whenever(mResources.getBoolean(R.bool.qs_show_user_switcher_for_single_user))
            .thenReturn(false)
        whenever(mUserManager.isUserSwitcherEnabled(false /* showEvenIfNotActionable */))
            .thenReturn(false)

        mNotificationPanelViewController.onFinishInflate()

        verify(mUserSwitcherStubView, never()).inflate()
        verify(mKeyguardStatusViewController, times(3)).displayClock(LARGE, /* animate */ true)

        coroutineContext.cancelChildren()
    }

    @Test
    fun testReInflateViews_userSwitcherDisabled_doNotInflateUserSwitchView() = runTest {
        launch(Dispatchers.Main.immediate) { givenViewAttached() }
        advanceUntilIdle()

        whenever(mResources.getBoolean(com.android.internal.R.bool.config_keyguardUserSwitcher))
            .thenReturn(true)
        whenever(mResources.getBoolean(R.bool.qs_show_user_switcher_for_single_user))
            .thenReturn(false)
        whenever(mUserManager.isUserSwitcherEnabled(false /* showEvenIfNotActionable */))
            .thenReturn(false)

        mNotificationPanelViewController.reInflateViews()

        verify(mUserSwitcherStubView, never()).inflate()

        coroutineContext.cancelChildren()
    }

    @Test
    fun testDoubleTapRequired_Keyguard() = runTest {
        launch(Dispatchers.Main.immediate) {
            val listener = getFalsingTapListener()
            mStatusBarStateController.setState(KEYGUARD)

            listener.onAdditionalTapRequired()

            verify(mKeyguardIndicationController).showTransientIndication(anyInt())
        }
        advanceUntilIdle()
    }

    @Test
    fun doubleTapRequired_onKeyguard_oneWayHapticsDisabled_usesOldVibrate() = runTest {
        launch(Dispatchers.Main.immediate) {
            whenever(mFeatureFlags.isEnabled(ONE_WAY_HAPTICS_API_MIGRATION)).thenReturn(false)
            val listener = getFalsingTapListener()
            mStatusBarStateController.setState(KEYGUARD)

            listener.onAdditionalTapRequired()
            val packageName = mView.context.packageName
            verify(mKeyguardIndicationController).showTransientIndication(anyInt())
            verify(mVibratorHelper)
                .vibrate(
                    any(),
                    eq(packageName),
                    eq(ADDITIONAL_TAP_REQUIRED_VIBRATION_EFFECT),
                    eq("falsing-additional-tap-required"),
                    eq(VibratorHelper.TOUCH_VIBRATION_ATTRIBUTES)
                )
        }
        advanceUntilIdle()
    }

    @Test
    fun doubleTapRequired_onKeyguard_oneWayHapticsEnabled_usesPerformHapticFeedback() = runTest {
        launch(Dispatchers.Main.immediate) {
            whenever(mFeatureFlags.isEnabled(ONE_WAY_HAPTICS_API_MIGRATION)).thenReturn(true)
            val listener = getFalsingTapListener()
            mStatusBarStateController.setState(KEYGUARD)

            listener.onAdditionalTapRequired()
            verify(mKeyguardIndicationController).showTransientIndication(anyInt())
            verify(mVibratorHelper)
                .performHapticFeedback(eq(mView), eq(HapticFeedbackConstants.REJECT))
        }
        advanceUntilIdle()
    }

    @Test
    fun testDoubleTapRequired_ShadeLocked() = runTest {
        launch(Dispatchers.Main.immediate) {
            val listener = getFalsingTapListener()
            mStatusBarStateController.setState(SHADE_LOCKED)

            listener.onAdditionalTapRequired()

            verify(mTapAgainViewController).show()
        }
        advanceUntilIdle()
    }

    @Test
    fun doubleTapRequired_shadeLocked_oneWayHapticsDisabled_usesOldVibrate() = runTest {
        launch(Dispatchers.Main.immediate) {
            whenever(mFeatureFlags.isEnabled(ONE_WAY_HAPTICS_API_MIGRATION)).thenReturn(false)
            val listener = getFalsingTapListener()
            val packageName = mView.context.packageName
            mStatusBarStateController.setState(SHADE_LOCKED)

            listener.onAdditionalTapRequired()
            verify(mVibratorHelper)
                .vibrate(
                    any(),
                    eq(packageName),
                    eq(ADDITIONAL_TAP_REQUIRED_VIBRATION_EFFECT),
                    eq("falsing-additional-tap-required"),
                    eq(VibratorHelper.TOUCH_VIBRATION_ATTRIBUTES)
                )

            verify(mTapAgainViewController).show()
        }
        advanceUntilIdle()
    }

    @Test
    fun doubleTapRequired_shadeLocked_oneWayHapticsEnabled_usesPerformHapticFeedback() = runTest {
        launch(Dispatchers.Main.immediate) {
            whenever(mFeatureFlags.isEnabled(ONE_WAY_HAPTICS_API_MIGRATION)).thenReturn(true)
            val listener = getFalsingTapListener()
            mStatusBarStateController.setState(SHADE_LOCKED)

            listener.onAdditionalTapRequired()
            verify(mVibratorHelper)
                .performHapticFeedback(eq(mView), eq(HapticFeedbackConstants.REJECT))

            verify(mTapAgainViewController).show()
        }
        advanceUntilIdle()
    }

    @Test
    fun testOnAttachRefreshStatusBarState() = runTest {
        launch(Dispatchers.Main.immediate) {
            mStatusBarStateController.setState(KEYGUARD)
            whenever(mKeyguardStateController.isKeyguardFadingAway()).thenReturn(false)
            mOnAttachStateChangeListeners.forEach { it.onViewAttachedToWindow(mView) }
            verify(mKeyguardStatusViewController)
                .setKeyguardStatusViewVisibility(
                    KEYGUARD /*statusBarState*/,
                    false /*keyguardFadingAway*/,
                    false /*goingToFullShade*/,
                    SHADE /*oldStatusBarState*/
                )
        }
        advanceUntilIdle()
    }
}
