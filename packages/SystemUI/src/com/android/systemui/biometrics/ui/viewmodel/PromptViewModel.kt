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
package com.android.systemui.biometrics.ui.viewmodel

import android.hardware.biometrics.BiometricPrompt
import android.util.Log
import android.view.MotionEvent
import com.android.systemui.biometrics.AuthBiometricView
import com.android.systemui.biometrics.domain.interactor.PromptSelectorInteractor
import com.android.systemui.biometrics.domain.model.BiometricModalities
import com.android.systemui.biometrics.shared.model.BiometricModality
import com.android.systemui.biometrics.shared.model.PromptKind
import com.android.systemui.statusbar.VibratorHelper
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** ViewModel for BiometricPrompt. */
class PromptViewModel
@Inject
constructor(
    private val interactor: PromptSelectorInteractor,
    private val vibrator: VibratorHelper,
) {
    /** The set of modalities available for this prompt */
    val modalities: Flow<BiometricModalities> =
        interactor.prompt.map { it?.modalities ?: BiometricModalities() }.distinctUntilChanged()

    // TODO(b/251476085): remove after icon controllers are migrated - do not keep this state
    private var _legacyState = MutableStateFlow(AuthBiometricView.STATE_AUTHENTICATING_ANIMATING_IN)
    val legacyState: StateFlow<Int> = _legacyState.asStateFlow()

    private val _isAuthenticating: MutableStateFlow<Boolean> = MutableStateFlow(false)

    /** If the user is currently authenticating (i.e. at least one biometric is scanning). */
    val isAuthenticating: Flow<Boolean> = _isAuthenticating.asStateFlow()

    private val _isAuthenticated: MutableStateFlow<PromptAuthState> =
        MutableStateFlow(PromptAuthState(false))

    /** If the user has successfully authenticated and confirmed (when explicitly required). */
    val isAuthenticated: Flow<PromptAuthState> = _isAuthenticated.asStateFlow()

    private val _isOverlayTouched: MutableStateFlow<Boolean> = MutableStateFlow(false)

    /**
     * If the API caller or the user's personal preferences require explicit confirmation after
     * successful authentication.
     */
    val isConfirmationRequired: Flow<Boolean> =
        combine(_isOverlayTouched, interactor.isConfirmationRequired) {
            isOverlayTouched,
            isConfirmationRequired ->
            !isOverlayTouched && isConfirmationRequired
        }

    /** The kind of credential the user has. */
    val credentialKind: Flow<PromptKind> = interactor.credentialKind

    /** The label to use for the cancel button. */
    val negativeButtonText: Flow<String> = interactor.prompt.map { it?.negativeButtonText ?: "" }

    private val _message: MutableStateFlow<PromptMessage> = MutableStateFlow(PromptMessage.Empty)

    /** A message to show the user, if there is an error, hint, or help to show. */
    val message: Flow<PromptMessage> = _message.asStateFlow()

    private val isRetrySupported: Flow<Boolean> = modalities.map { it.hasFace }

    private val _fingerprintStartMode = MutableStateFlow(FingerprintStartMode.Pending)

    /** Fingerprint sensor state. */
    val fingerprintStartMode: Flow<FingerprintStartMode> = _fingerprintStartMode.asStateFlow()

    private val _forceLargeSize = MutableStateFlow(false)
    private val _forceMediumSize = MutableStateFlow(false)

    /** The size of the prompt. */
    val size: Flow<PromptSize> =
        combine(
                _forceLargeSize,
                _forceMediumSize,
                modalities,
                interactor.isConfirmationRequired,
                fingerprintStartMode,
            ) { forceLarge, forceMedium, modalities, confirmationRequired, fpStartMode ->
                when {
                    forceLarge -> PromptSize.LARGE
                    forceMedium -> PromptSize.MEDIUM
                    modalities.hasFaceOnly && !confirmationRequired -> PromptSize.SMALL
                    modalities.hasFaceAndFingerprint &&
                        !confirmationRequired &&
                        fpStartMode == FingerprintStartMode.Pending -> PromptSize.SMALL
                    else -> PromptSize.MEDIUM
                }
            }
            .distinctUntilChanged()

    /** Title for the prompt. */
    val title: Flow<String> = interactor.prompt.map { it?.title ?: "" }.distinctUntilChanged()

    /** Subtitle for the prompt. */
    val subtitle: Flow<String> = interactor.prompt.map { it?.subtitle ?: "" }.distinctUntilChanged()

    /** Description for the prompt. */
    val description: Flow<String> =
        interactor.prompt.map { it?.description ?: "" }.distinctUntilChanged()

    /** If the indicator (help, error) message should be shown. */
    val isIndicatorMessageVisible: Flow<Boolean> =
        combine(
                size,
                message,
            ) { size, message ->
                size.isNotSmall && message.message.isNotBlank()
            }
            .distinctUntilChanged()

    /** If the auth is pending confirmation and the confirm button should be shown. */
    val isConfirmButtonVisible: Flow<Boolean> =
        combine(
                size,
                isAuthenticated,
            ) { size, authState ->
                size.isNotSmall && authState.isAuthenticated && authState.needsUserConfirmation
            }
            .distinctUntilChanged()

    /** If the icon can be used as a confirmation button. */
    val isIconConfirmButton: Flow<Boolean> =
        combine(size, interactor.isConfirmationRequired) { size, isConfirmationRequired ->
            size.isNotSmall && isConfirmationRequired
        }

    /** If the negative button should be shown. */
    val isNegativeButtonVisible: Flow<Boolean> =
        combine(
                size,
                isAuthenticated,
                interactor.isCredentialAllowed,
            ) { size, authState, credentialAllowed ->
                size.isNotSmall && authState.isNotAuthenticated && !credentialAllowed
            }
            .distinctUntilChanged()

    /** If the cancel button should be shown (. */
    val isCancelButtonVisible: Flow<Boolean> =
        combine(
                size,
                isAuthenticated,
                isNegativeButtonVisible,
                isConfirmButtonVisible,
            ) { size, authState, showNegativeButton, showConfirmButton ->
                size.isNotSmall &&
                    authState.isAuthenticated &&
                    !showNegativeButton &&
                    showConfirmButton
            }
            .distinctUntilChanged()

    private val _canTryAgainNow = MutableStateFlow(false)
    /**
     * If authentication can be manually restarted via the try again button or touching a
     * fingerprint sensor.
     */
    val canTryAgainNow: Flow<Boolean> =
        combine(
                _canTryAgainNow,
                size,
                isAuthenticated,
                isRetrySupported,
            ) { readyToTryAgain, size, authState, supportsRetry ->
                readyToTryAgain && size.isNotSmall && supportsRetry && authState.isNotAuthenticated
            }
            .distinctUntilChanged()

    /** If the try again button show be shown (only the button, see [canTryAgainNow]). */
    val isTryAgainButtonVisible: Flow<Boolean> =
        combine(
                canTryAgainNow,
                modalities,
            ) { tryAgainIsPossible, modalities ->
                tryAgainIsPossible && modalities.hasFaceOnly
            }
            .distinctUntilChanged()

    /** If the credential fallback button show be shown. */
    val isCredentialButtonVisible: Flow<Boolean> =
        combine(
                size,
                isAuthenticated,
                interactor.isCredentialAllowed,
            ) { size, authState, credentialAllowed ->
                size.isNotSmall && authState.isNotAuthenticated && credentialAllowed
            }
            .distinctUntilChanged()

    private var messageJob: Job? = null

    /**
     * Show a temporary error [message] associated with an optional [failedModality] and play
     * [hapticFeedback].
     *
     * The [messageAfterError] will be shown via [showAuthenticating] when [authenticateAfterError]
     * is set (or via [showHelp] when not set) after the error is dismissed.
     *
     * The error is ignored if the user has already authenticated or if [suppressIf] is true given
     * the currently showing [PromptMessage].
     */
    suspend fun showTemporaryError(
        message: String,
        messageAfterError: String,
        authenticateAfterError: Boolean,
        suppressIf: (PromptMessage) -> Boolean = { false },
        hapticFeedback: Boolean = true,
        failedModality: BiometricModality = BiometricModality.None,
    ) = coroutineScope {
        if (_isAuthenticated.value.isAuthenticated) {
            return@coroutineScope
        }

        _canTryAgainNow.value = supportsRetry(failedModality)

        if (suppressIf(_message.value)) {
            return@coroutineScope
        }

        _isAuthenticating.value = false
        _isAuthenticated.value = PromptAuthState(false)
        _forceMediumSize.value = true
        _message.value = PromptMessage.Error(message)
        _legacyState.value = AuthBiometricView.STATE_ERROR

        if (hapticFeedback) {
            vibrator.error(failedModality)
        }

        messageJob?.cancel()
        messageJob = launch {
            delay(BiometricPrompt.HIDE_DIALOG_DELAY.toLong())
            if (authenticateAfterError) {
                showAuthenticating(messageAfterError)
            } else {
                showHelp(messageAfterError)
            }
        }
    }

    /**
     * Call to ensure the fingerprint sensor has started. Either when the dialog is first shown
     * (most cases) or when it should be enabled after a first error (coex implicit flow).
     */
    fun ensureFingerprintHasStarted(isDelayed: Boolean) {
        if (_fingerprintStartMode.value == FingerprintStartMode.Pending) {
            _fingerprintStartMode.value =
                if (isDelayed) FingerprintStartMode.Delayed else FingerprintStartMode.Normal
        }
    }

    // enable retry only when face fails (fingerprint runs constantly)
    private fun supportsRetry(failedModality: BiometricModality) =
        failedModality == BiometricModality.Face

    /**
     * Show a persistent help message.
     *
     * Will be show even if the user has already authenticated.
     */
    suspend fun showHelp(message: String) {
        val alreadyAuthenticated = _isAuthenticated.value.isAuthenticated
        if (!alreadyAuthenticated) {
            _isAuthenticating.value = false
            _isAuthenticated.value = PromptAuthState(false)
        }

        _message.value =
            if (message.isNotBlank()) PromptMessage.Help(message) else PromptMessage.Empty
        _forceMediumSize.value = true
        _legacyState.value =
            if (alreadyAuthenticated && isConfirmationRequired.first()) {
                AuthBiometricView.STATE_PENDING_CONFIRMATION
            } else if (alreadyAuthenticated && !isConfirmationRequired.first()) {
                AuthBiometricView.STATE_AUTHENTICATED
            } else {
                AuthBiometricView.STATE_HELP
            }

        messageJob?.cancel()
        messageJob = null
    }

    /**
     * Show a temporary help message and transition back to a fixed message.
     *
     * Ignored if the user has already authenticated.
     */
    suspend fun showTemporaryHelp(
        message: String,
        messageAfterHelp: String = "",
    ) = coroutineScope {
        if (_isAuthenticated.value.isAuthenticated) {
            return@coroutineScope
        }

        _isAuthenticating.value = false
        _isAuthenticated.value = PromptAuthState(false)
        _message.value =
            if (message.isNotBlank()) PromptMessage.Help(message) else PromptMessage.Empty
        _forceMediumSize.value = true
        _legacyState.value = AuthBiometricView.STATE_HELP

        messageJob?.cancel()
        messageJob = launch {
            delay(BiometricPrompt.HIDE_DIALOG_DELAY.toLong())
            showAuthenticating(messageAfterHelp)
        }
    }

    /** Show the user that biometrics are actively running and set [isAuthenticating]. */
    fun showAuthenticating(message: String = "", isRetry: Boolean = false) {
        if (_isAuthenticated.value.isAuthenticated) {
            // TODO(jbolinger): convert to go/tex-apc?
            Log.w(TAG, "Cannot show authenticating after authenticated")
            return
        }

        _isAuthenticating.value = true
        _isAuthenticated.value = PromptAuthState(false)
        _message.value = if (message.isBlank()) PromptMessage.Empty else PromptMessage.Help(message)
        _legacyState.value = AuthBiometricView.STATE_AUTHENTICATING

        // reset the try again button(s) after the user attempts a retry
        if (isRetry) {
            _canTryAgainNow.value = false
        }

        messageJob?.cancel()
        messageJob = null
    }

    /**
     * Show successfully authentication, set [isAuthenticated], and dismiss the prompt after a
     * [dismissAfterDelay] or prompt for explicit confirmation (if required).
     */
    suspend fun showAuthenticated(
        modality: BiometricModality,
        dismissAfterDelay: Long,
        helpMessage: String = "",
    ) {
        if (_isAuthenticated.value.isAuthenticated) {
            // TODO(jbolinger): convert to go/tex-apc?
            Log.w(TAG, "Cannot show authenticated after authenticated")
            return
        }

        _isAuthenticating.value = false
        val needsUserConfirmation = needsExplicitConfirmation(modality)
        _isAuthenticated.value =
            PromptAuthState(true, modality, needsUserConfirmation, dismissAfterDelay)
        _message.value = PromptMessage.Empty
        _legacyState.value =
            if (needsUserConfirmation) {
                AuthBiometricView.STATE_PENDING_CONFIRMATION
            } else {
                AuthBiometricView.STATE_AUTHENTICATED
            }

        if (!needsUserConfirmation) {
            vibrator.success(modality)
        }

        messageJob?.cancel()
        messageJob = null

        if (helpMessage.isNotBlank()) {
            showHelp(helpMessage)
        }
    }

    private suspend fun needsExplicitConfirmation(modality: BiometricModality): Boolean {
        val confirmationRequired = isConfirmationRequired.first()

        // Only worry about confirmationRequired if face was used to unlock
        if (modality == BiometricModality.Face) {
            return confirmationRequired
        }
        // fingerprint only never requires confirmation
        return false
    }

    /**
     * Set the prompt's auth state to authenticated and confirmed.
     *
     * This should only be used after [showAuthenticated] when the operation requires explicit user
     * confirmation.
     */
    fun confirmAuthenticated() {
        val authState = _isAuthenticated.value
        if (authState.isNotAuthenticated) {
            Log.w(TAG, "Cannot confirm authenticated when not authenticated")
            return
        }

        _isAuthenticated.value = authState.asExplicitlyConfirmed()
        _message.value = PromptMessage.Empty
        _legacyState.value = AuthBiometricView.STATE_AUTHENTICATED

        vibrator.success(authState.authenticatedModality)

        messageJob?.cancel()
        messageJob = null
    }

    /**
     * Touch event occurred on the overlay
     *
     * Tracks whether a finger is currently down to set [_isOverlayTouched] to be used as user
     * confirmation
     */
    fun onOverlayTouch(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            _isOverlayTouched.value = true

            if (_isAuthenticated.value.needsUserConfirmation) {
                confirmAuthenticated()
            }
            return true
        } else if (event.actionMasked == MotionEvent.ACTION_UP) {
            _isOverlayTouched.value = false
        }
        return false
    }

    /**
     * Switch to the credential view.
     *
     * TODO(b/251476085): this should be decoupled from the shared panel controller
     */
    fun onSwitchToCredential() {
        _forceLargeSize.value = true
    }

    private fun VibratorHelper.success(modality: BiometricModality) =
        vibrateAuthSuccess("$TAG, modality = $modality BP::success")

    private fun VibratorHelper.error(modality: BiometricModality = BiometricModality.None) =
        vibrateAuthError("$TAG, modality = $modality BP::error")

    companion object {
        private const val TAG = "PromptViewModel"
    }
}

/** How the fingerprint sensor was started for the prompt. */
enum class FingerprintStartMode {
    /** Fingerprint sensor has not started. */
    Pending,

    /** Fingerprint sensor started immediately when prompt was displayed. */
    Normal,

    /** Fingerprint sensor started after the first failure of another passive modality. */
    Delayed;

    /** If this is [Normal] or [Delayed]. */
    val isStarted: Boolean
        get() = this == Normal || this == Delayed
}
