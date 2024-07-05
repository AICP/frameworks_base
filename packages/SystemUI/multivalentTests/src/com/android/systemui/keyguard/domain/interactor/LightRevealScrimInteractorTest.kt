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

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.keyguard.data.fakeLightRevealScrimRepository
import com.android.systemui.keyguard.data.repository.FakeLightRevealScrimRepository
import com.android.systemui.keyguard.data.repository.fakeKeyguardTransitionRepository
import com.android.systemui.keyguard.shared.model.TransitionState
import com.android.systemui.keyguard.shared.model.TransitionStep
import com.android.systemui.kosmos.testScope
import com.android.systemui.statusbar.LightRevealEffect
import com.android.systemui.statusbar.LightRevealScrim
import com.android.systemui.testKosmos
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito

@SmallTest
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class LightRevealScrimInteractorTest : SysuiTestCase() {
    val kosmos =
        testKosmos().apply {
            this.fakeLightRevealScrimRepository = Mockito.spy(FakeLightRevealScrimRepository())
        }

    private val fakeLightRevealScrimRepository = kosmos.fakeLightRevealScrimRepository

    private val fakeKeyguardTransitionRepository = kosmos.fakeKeyguardTransitionRepository
    private val testScope = kosmos.testScope

    private val underTest = kosmos.lightRevealScrimInteractor

    private val reveal1 =
        object : LightRevealEffect {
            override fun setRevealAmountOnScrim(amount: Float, scrim: LightRevealScrim) {}
        }

    private val reveal2 =
        object : LightRevealEffect {
            override fun setRevealAmountOnScrim(amount: Float, scrim: LightRevealScrim) {}
        }

    @Test
    fun lightRevealEffect_doesNotChangeDuringKeyguardTransition() =
        runTest(UnconfinedTestDispatcher()) {
            val values = mutableListOf<LightRevealEffect>()
            val job = underTest.lightRevealEffect.onEach(values::add).launchIn(this)

            fakeLightRevealScrimRepository.setRevealEffect(reveal1)

            // The reveal effect shouldn't emit anything until a keyguard transition starts.
            assertEquals(values.size, 0)

            // Once it starts, it should emit reveal1.
            fakeKeyguardTransitionRepository.sendTransitionStep(
                TransitionStep(transitionState = TransitionState.STARTED)
            )
            assertEquals(values, listOf(reveal1))

            // Until the next transition starts, reveal2 should not be emitted.
            fakeLightRevealScrimRepository.setRevealEffect(reveal2)
            fakeKeyguardTransitionRepository.sendTransitionStep(
                TransitionStep(transitionState = TransitionState.RUNNING)
            )
            fakeKeyguardTransitionRepository.sendTransitionStep(
                TransitionStep(transitionState = TransitionState.FINISHED)
            )
            assertEquals(values, listOf(reveal1))
            fakeKeyguardTransitionRepository.sendTransitionStep(
                TransitionStep(transitionState = TransitionState.STARTED)
            )
            assertEquals(values, listOf(reveal1, reveal2))

            job.cancel()
        }
}
