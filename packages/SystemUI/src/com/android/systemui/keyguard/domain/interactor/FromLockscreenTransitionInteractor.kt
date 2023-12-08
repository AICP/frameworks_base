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
 * limitations under the License
 */

package com.android.systemui.keyguard.domain.interactor

import android.animation.ValueAnimator
import com.android.app.animation.Interpolators
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.flags.FeatureFlags
import com.android.systemui.flags.Flags
import com.android.systemui.keyguard.data.repository.KeyguardTransitionRepository
import com.android.systemui.keyguard.shared.model.KeyguardState
import com.android.systemui.keyguard.shared.model.KeyguardSurfaceBehindModel
import com.android.systemui.keyguard.shared.model.StatusBarState.KEYGUARD
import com.android.systemui.keyguard.shared.model.TransitionInfo
import com.android.systemui.keyguard.shared.model.TransitionState
import com.android.systemui.keyguard.shared.model.WakefulnessState
import com.android.systemui.shade.data.repository.ShadeRepository
import com.android.systemui.util.kotlin.Utils.Companion.toQuad
import com.android.systemui.util.kotlin.Utils.Companion.toTriple
import com.android.systemui.util.kotlin.sample
import java.util.UUID
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch

@SysUISingleton
class FromLockscreenTransitionInteractor
@Inject
constructor(
    override val transitionRepository: KeyguardTransitionRepository,
    override val transitionInteractor: KeyguardTransitionInteractor,
    @Application private val scope: CoroutineScope,
    private val keyguardInteractor: KeyguardInteractor,
    private val flags: FeatureFlags,
    private val shadeRepository: ShadeRepository,
) :
    TransitionInteractor(
        fromState = KeyguardState.LOCKSCREEN,
    ) {

    override fun start() {
        listenForLockscreenToGone()
        listenForLockscreenToGoneDragging()
        listenForLockscreenToOccluded()
        listenForLockscreenToCamera()
        listenForLockscreenToAodOrDozing()
        listenForLockscreenToPrimaryBouncer()
        listenForLockscreenToDreaming()
        listenForLockscreenToPrimaryBouncerDragging()
        listenForLockscreenToAlternateBouncer()
    }

    /**
     * Whether we want the surface behind the keyguard visible for the transition from LOCKSCREEN,
     * or null if we don't care and should just use a reasonable default.
     *
     * [KeyguardSurfaceBehindInteractor] will switch to this flow whenever a transition from
     * LOCKSCREEN is running.
     */
    val surfaceBehindVisibility: Flow<Boolean?> =
        transitionInteractor.startedKeyguardTransitionStep
            .map { startedStep ->
                if (startedStep.to != KeyguardState.GONE) {
                    // LOCKSCREEN to anything but GONE does not require any special surface
                    // visibility handling.
                    return@map null
                }

                true // TODO(b/278086361): Implement continuous swipe to unlock.
            }
            .onStart {
                // Default to null ("don't care, use a reasonable default").
                emit(null)
            }
            .distinctUntilChanged()

    /**
     * The surface behind view params to use for the transition from LOCKSCREEN, or null if we don't
     * care and should use a reasonable default.
     */
    val surfaceBehindModel: Flow<KeyguardSurfaceBehindModel?> =
        combine(
                transitionInteractor.startedKeyguardTransitionStep,
                transitionInteractor.transitionStepsFromState(KeyguardState.LOCKSCREEN)
            ) { startedStep, fromLockscreenStep ->
                if (startedStep.to != KeyguardState.GONE) {
                    // Only LOCKSCREEN -> GONE has specific surface params (for the unlock
                    // animation).
                    return@combine null
                } else if (fromLockscreenStep.value > 0.5f) {
                    // Start the animation once we're 50% transitioned to GONE.
                    KeyguardSurfaceBehindModel(
                        animateFromAlpha = 0f,
                        alpha = 1f,
                        animateFromTranslationY = 500f,
                        translationY = 0f
                    )
                } else {
                    KeyguardSurfaceBehindModel(
                        alpha = 0f,
                    )
                }
            }
            .onStart {
                // Default to null ("don't care, use a reasonable default").
                emit(null)
            }
            .distinctUntilChanged()

    private fun listenForLockscreenToDreaming() {
        val invalidFromStates = setOf(KeyguardState.AOD, KeyguardState.DOZING)
        scope.launch {
            keyguardInteractor.isAbleToDream
                .sample(
                    combine(
                        transitionInteractor.startedKeyguardTransitionStep,
                        transitionInteractor.finishedKeyguardState,
                        keyguardInteractor.isActiveDreamLockscreenHosted,
                        ::Triple
                    ),
                    ::toQuad
                )
                .collect {
                    (
                        isAbleToDream,
                        lastStartedTransition,
                        finishedKeyguardState,
                        isActiveDreamLockscreenHosted) ->
                    val isOnLockscreen = finishedKeyguardState == KeyguardState.LOCKSCREEN
                    val isTransitionInterruptible =
                        lastStartedTransition.to == KeyguardState.LOCKSCREEN &&
                            !invalidFromStates.contains(lastStartedTransition.from)
                    if (isAbleToDream && (isOnLockscreen || isTransitionInterruptible)) {
                        if (isActiveDreamLockscreenHosted) {
                            startTransitionTo(KeyguardState.DREAMING_LOCKSCREEN_HOSTED)
                        } else {
                            startTransitionTo(KeyguardState.DREAMING)
                        }
                    }
                }
        }
    }

    private fun listenForLockscreenToPrimaryBouncer() {
        scope.launch {
            keyguardInteractor.primaryBouncerShowing
                .sample(transitionInteractor.startedKeyguardTransitionStep, ::Pair)
                .collect { pair ->
                    val (isBouncerShowing, lastStartedTransitionStep) = pair
                    if (
                        isBouncerShowing && lastStartedTransitionStep.to == KeyguardState.LOCKSCREEN
                    ) {
                        startTransitionTo(KeyguardState.PRIMARY_BOUNCER)
                    }
                }
        }
    }

    private fun listenForLockscreenToAlternateBouncer() {
        scope.launch {
            keyguardInteractor.alternateBouncerShowing
                .sample(transitionInteractor.startedKeyguardTransitionStep, ::Pair)
                .collect { pair ->
                    val (isAlternateBouncerShowing, lastStartedTransitionStep) = pair
                    if (
                        isAlternateBouncerShowing &&
                            lastStartedTransitionStep.to == KeyguardState.LOCKSCREEN
                    ) {
                        startTransitionTo(KeyguardState.ALTERNATE_BOUNCER)
                    }
                }
        }
    }

    /* Starts transitions when manually dragging up the bouncer from the lockscreen. */
    private fun listenForLockscreenToPrimaryBouncerDragging() {
        var transitionId: UUID? = null
        scope.launch {
            shadeRepository.shadeModel
                .sample(
                    combine(
                        transitionInteractor.startedKeyguardTransitionStep,
                        keyguardInteractor.statusBarState,
                        keyguardInteractor.isKeyguardUnlocked,
                        ::Triple
                    ),
                    ::toQuad
                )
                .collect { (shadeModel, keyguardState, statusBarState, isKeyguardUnlocked) ->
                    val id = transitionId
                    if (id != null) {
                        if (keyguardState.to == KeyguardState.PRIMARY_BOUNCER) {
                            // An existing `id` means a transition is started, and calls to
                            // `updateTransition` will control it until FINISHED or CANCELED
                            var nextState =
                                if (shadeModel.expansionAmount == 0f) {
                                    TransitionState.FINISHED
                                } else if (shadeModel.expansionAmount == 1f) {
                                    TransitionState.CANCELED
                                } else {
                                    TransitionState.RUNNING
                                }
                            transitionRepository.updateTransition(
                                id,
                                1f - shadeModel.expansionAmount,
                                nextState,
                            )

                            if (
                                nextState == TransitionState.CANCELED ||
                                    nextState == TransitionState.FINISHED
                            ) {
                                transitionId = null
                            }

                            // If canceled, just put the state back
                            // TODO(b/278086361): This logic should happen in
                            //  FromPrimaryBouncerInteractor.
                            if (nextState == TransitionState.CANCELED) {
                                transitionRepository.startTransition(
                                    TransitionInfo(
                                        ownerName = name,
                                        from = KeyguardState.PRIMARY_BOUNCER,
                                        to = KeyguardState.LOCKSCREEN,
                                        animator =
                                            getDefaultAnimatorForTransitionsToState(
                                                    KeyguardState.LOCKSCREEN
                                                )
                                                .apply { duration = 0 }
                                    )
                                )
                            }
                        }
                    } else {
                        // TODO (b/251849525): Remove statusbarstate check when that state is
                        // integrated into KeyguardTransitionRepository
                        if (
                            keyguardState.to == KeyguardState.LOCKSCREEN &&
                                shadeModel.isUserDragging &&
                                !isKeyguardUnlocked &&
                                statusBarState == KEYGUARD
                        ) {
                            transitionId = startTransitionTo(KeyguardState.PRIMARY_BOUNCER)
                        }
                    }
                }
        }
    }

    fun dismissKeyguard() {
        startTransitionTo(KeyguardState.GONE)
    }

    private fun listenForLockscreenToGone() {
        if (flags.isEnabled(Flags.KEYGUARD_WM_STATE_REFACTOR)) {
            return
        }

        scope.launch {
            keyguardInteractor.isKeyguardGoingAway
                .sample(transitionInteractor.startedKeyguardTransitionStep, ::Pair)
                .collect { pair ->
                    val (isKeyguardGoingAway, lastStartedStep) = pair
                    if (isKeyguardGoingAway && lastStartedStep.to == KeyguardState.LOCKSCREEN) {
                        startTransitionTo(KeyguardState.GONE)
                    }
                }
        }
    }

    private fun listenForLockscreenToGoneDragging() {
        if (flags.isEnabled(Flags.KEYGUARD_WM_STATE_REFACTOR)) {
            return
        }

        scope.launch {
            keyguardInteractor.isKeyguardGoingAway
                .sample(transitionInteractor.startedKeyguardTransitionStep, ::Pair)
                .collect { pair ->
                    val (isKeyguardGoingAway, lastStartedStep) = pair
                    if (isKeyguardGoingAway && lastStartedStep.to == KeyguardState.LOCKSCREEN) {
                        startTransitionTo(KeyguardState.GONE)
                    }
                }
        }
    }

    private fun listenForLockscreenToOccluded() {
        scope.launch {
            keyguardInteractor.isKeyguardOccluded
                .sample(
                    combine(
                        transitionInteractor.finishedKeyguardState,
                        keyguardInteractor.isDreaming,
                        ::Pair
                    ),
                    ::toTriple
                )
                .collect { (isOccluded, keyguardState, isDreaming) ->
                    if (isOccluded && !isDreaming && keyguardState == KeyguardState.LOCKSCREEN) {
                        startTransitionTo(KeyguardState.OCCLUDED)
                    }
                }
        }
    }

    /** This signal may come in before the occlusion signal, and can provide a custom transition */
    private fun listenForLockscreenToCamera() {
        scope.launch {
            keyguardInteractor.onCameraLaunchDetected
                .sample(transitionInteractor.startedKeyguardTransitionStep, ::Pair)
                .collect { (_, lastStartedStep) ->
                    // DREAMING/AOD/OFF may trigger on the first power button push, so include this
                    // state in order to cancel and correct the transition
                    if (
                        lastStartedStep.to == KeyguardState.LOCKSCREEN ||
                            lastStartedStep.to == KeyguardState.DREAMING ||
                            lastStartedStep.to == KeyguardState.DOZING ||
                            lastStartedStep.to == KeyguardState.AOD ||
                            lastStartedStep.to == KeyguardState.OFF
                    ) {
                        startTransitionTo(KeyguardState.OCCLUDED)
                    }
                }
        }
    }

    private fun listenForLockscreenToAodOrDozing() {
        scope.launch {
            keyguardInteractor.wakefulnessModel
                .sample(
                    combine(
                        transitionInteractor.startedKeyguardTransitionStep,
                        keyguardInteractor.isAodAvailable,
                        ::Pair
                    ),
                    ::toTriple
                )
                .collect { (wakefulnessState, lastStartedStep, isAodAvailable) ->
                    if (
                        lastStartedStep.to == KeyguardState.LOCKSCREEN &&
                            wakefulnessState.state == WakefulnessState.STARTING_TO_SLEEP
                    ) {
                        startTransitionTo(
                            if (isAodAvailable) KeyguardState.AOD else KeyguardState.DOZING
                        )
                    }
                }
        }
    }

    override fun getDefaultAnimatorForTransitionsToState(toState: KeyguardState): ValueAnimator {
        return ValueAnimator().apply {
            interpolator = Interpolators.LINEAR
            duration =
                when (toState) {
                    KeyguardState.DREAMING -> TO_DREAMING_DURATION
                    KeyguardState.OCCLUDED -> TO_OCCLUDED_DURATION
                    else -> DEFAULT_DURATION
                }.inWholeMilliseconds
        }
    }

    companion object {
        private val DEFAULT_DURATION = 400.milliseconds
        val TO_DREAMING_DURATION = 933.milliseconds
        val TO_OCCLUDED_DURATION = 450.milliseconds
    }
}
