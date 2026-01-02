package com.android.systemui.biometrics.ui.viewmodel

import android.content.Context
import android.graphics.drawable.Drawable
import android.hardware.biometrics.Flags
import android.hardware.biometrics.PromptContentView
import android.text.InputType
import com.android.internal.widget.LockPatternView
import com.android.systemui.biometrics.Utils
import com.android.systemui.biometrics.domain.interactor.CredentialStatus
import com.android.systemui.biometrics.domain.interactor.PromptCredentialInteractor
import com.android.systemui.biometrics.domain.interactor.PromptSelectorInteractor
import com.android.systemui.biometrics.domain.model.BiometricPromptRequest
import com.android.systemui.biometrics.shared.model.BiometricUserInfo
import com.android.systemui.biometrics.shared.model.FallbackOptionModel
import com.android.systemui.biometrics.shared.model.PromptKind
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.res.R
import com.android.systemui.shade.domain.interactor.ShadeInteractor
import javax.inject.Inject
import kotlin.reflect.KClass
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map

/** View-model for all CredentialViews within BiometricPrompt. */
class CredentialViewModel
@Inject
constructor(
    @Application private val applicationContext: Context,
    private val promptCredentialInteractor: PromptCredentialInteractor,
    shadeInteractor: ShadeInteractor,
    private val promptSelectorInteractor: PromptSelectorInteractor,
) {
    /** Whether credential is allowed in the prompt */
    val isCredentialAllowed: Flow<Boolean> =
        promptCredentialInteractor.prompt.map { it?.credentialAllowed == true }

    /** Top level information about the prompt. */
    val header: Flow<CredentialHeaderViewModel> =
        combine(
            promptCredentialInteractor.prompt.filterIsInstance<BiometricPromptRequest.Credential>(),
            promptCredentialInteractor.showTitleOnly,
            isCredentialAllowed,
            promptCredentialInteractor.credentialKind,
        ) { request, showTitleOnly, credentialAllowed, credentialKind ->
            if (credentialAllowed) {
                BiometricPromptHeaderViewModelImpl(
                    request,
                    user = request.userInfo,
                    title = request.title,
                    subtitle = if (showTitleOnly) "" else request.subtitle,
                    contentView = if (!showTitleOnly) request.contentView else null,
                    description = if (request.contentView != null) "" else request.description,
                    icon = applicationContext.asLockIcon(request.userInfo.deviceCredentialOwnerId),
                    showEmergencyCallButton = request.showEmergencyCallButton,
                )
            } else {
                BiometricPromptHeaderViewModelImpl(
                    request,
                    user = request.userInfo,
                    title = applicationContext.asResetTitle(credentialKind),
                    description = applicationContext.asResetSubtitle(credentialKind),
                    icon =
                        applicationContext.asLockIcon(
                            request.userInfo.deviceCredentialOwnerId
                        ), // TODO: Need custom icon
                    showEmergencyCallButton = request.showEmergencyCallButton,
                )
            }
        }

    /** Whether the shade is being interacted with */
    val isShadeInteracted = shadeInteractor.isUserInteracting

    /** Input flags for text based credential views */
    val inputFlags: Flow<Int?> =
        promptCredentialInteractor.prompt.map {
            when (it) {
                is BiometricPromptRequest.Credential.Pin ->
                    InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
                else -> null
            }
        }

    /** Input box accessibility description for text based credential views */
    val inputBoxContentDescription: Flow<Int?> =
        promptCredentialInteractor.prompt.map {
            when (it) {
                is BiometricPromptRequest.Credential.Pin -> R.string.keyguard_accessibility_pin_area
                is BiometricPromptRequest.Credential.Password ->
                    R.string.keyguard_accessibility_password
                else -> null
            }
        }

    /** If stealth mode is active (hide user credential input). */
    val stealthMode: Flow<Boolean> =
        promptCredentialInteractor.prompt.map {
            when (it) {
                is BiometricPromptRequest.Credential.Pattern -> it.stealthMode
                else -> false
            }
        }

    private val _animateContents: MutableStateFlow<Boolean> = MutableStateFlow(true)
    /** If this view should be animated on transitions. */
    val animateContents = _animateContents.asStateFlow()

    /** Error messages to show the user. */
    val errorMessage: Flow<String> =
        combine(promptCredentialInteractor.verificationError, promptCredentialInteractor.prompt) {
            error,
            p ->
            when (error) {
                is CredentialStatus.Fail.Error ->
                    error.error ?: applicationContext.asBadCredentialErrorMessage(p)
                is CredentialStatus.Fail.Throttled -> error.error
                null -> ""
            }
        }

    private val _validatedAttestation: MutableSharedFlow<ByteArray?> = MutableSharedFlow()
    /** Results of [checkPatternCredential]. A non-null attestation is supplied on success. */
    val validatedAttestation: Flow<ByteArray?> = _validatedAttestation.asSharedFlow()

    private val _remainingAttempts: MutableStateFlow<RemainingAttempts> =
        MutableStateFlow(RemainingAttempts())
    /** If set, the number of remaining attempts before the user must stop. */
    val remainingAttempts: Flow<RemainingAttempts> = _remainingAttempts.asStateFlow()

    private val biometricsRequested: Flow<Boolean> =
        promptCredentialInteractor.prompt.map { it?.biometricsRequested == true }

    /** List of fallback options set by prompt caller */
    val fallbackOptions: Flow<List<FallbackOptionModel>> =
        promptCredentialInteractor.fallbackOptions

    /** Whether the fallback button should show on the credential screen */
    val showFallbackButton: Flow<Boolean> =
        combine(biometricsRequested, fallbackOptions) { biometricsRequested, fallbackOptions ->
            Flags.bpFallbackOptions() && !biometricsRequested && fallbackOptions.isNotEmpty()
        }

    /** Enable transition animations. */
    fun setAnimateContents(animate: Boolean) {
        _animateContents.value = animate
    }

    /** Show an error message to inform the user the pattern is too short to attempt validation. */
    fun showPatternTooShortError() {
        promptCredentialInteractor.setVerificationError(
            CredentialStatus.Fail.Error(
                applicationContext.asBadCredentialErrorMessage(
                    BiometricPromptRequest.Credential.Pattern::class
                )
            )
        )
    }

    /** Reset the error message to an empty string. */
    fun resetErrorMessage() {
        promptCredentialInteractor.resetVerificationError()
    }

    /** Switch to the fallback view. */
    fun onSwitchToFallbackScreen() {
        promptSelectorInteractor.onSwitchToFallback()
    }

    /** Switch to the auth view. */
    fun onSwichToAuthScreen() {
        promptSelectorInteractor.onSwitchToAuth()
    }

    /** Check a PIN or password and update [validatedAttestation] or [remainingAttempts]. */
    suspend fun checkCredential(text: CharSequence, header: CredentialHeaderViewModel) =
        checkCredential(promptCredentialInteractor.checkCredential(header.asRequest(), text = text))

    /** Check a pattern and update [validatedAttestation] or [remainingAttempts]. */
    suspend fun checkCredential(
        pattern: List<LockPatternView.Cell>,
        patternSize: Byte,
        header: CredentialHeaderViewModel,
    ) =
        checkCredential(
            promptCredentialInteractor.checkCredential(header.asRequest(), pattern = pattern,
            patternSize = patternSize)
        )

    private suspend fun checkCredential(result: CredentialStatus) {
        when (result) {
            is CredentialStatus.Success.Verified -> {
                _validatedAttestation.emit(result.hat)
                _remainingAttempts.value = RemainingAttempts()
            }
            is CredentialStatus.Fail.Error -> {
                _validatedAttestation.emit(null)
                _remainingAttempts.value =
                    RemainingAttempts(result.remainingAttempts, result.urgentMessage ?: "")
            }
            is CredentialStatus.Fail.Throttled -> {
                // required for completeness, but a throttled error cannot be the final result
                _validatedAttestation.emit(null)
                _remainingAttempts.value = RemainingAttempts()
            }
        }
    }

    fun doEmergencyCall(context: Context) {
        val intent =
            context
                .getSystemService(android.telecom.TelecomManager::class.java)!!
                .createLaunchEmergencyDialerIntent(null)
                .setFlags(
                    android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                        android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
                )
        context.startActivity(intent)
    }
}

private fun Context.asBadCredentialErrorMessage(prompt: BiometricPromptRequest?): String =
    asBadCredentialErrorMessage(
        if (prompt != null) prompt::class else BiometricPromptRequest.Credential.Password::class
    )

private fun <T : BiometricPromptRequest> Context.asBadCredentialErrorMessage(
    clazz: KClass<T>
): String =
    getString(
        when (clazz) {
            BiometricPromptRequest.Credential.Pin::class -> R.string.biometric_dialog_wrong_pin
            BiometricPromptRequest.Credential.Password::class ->
                R.string.biometric_dialog_wrong_password
            BiometricPromptRequest.Credential.Pattern::class ->
                R.string.biometric_dialog_wrong_pattern
            else -> R.string.biometric_dialog_wrong_password
        }
    )

private fun Context.asLockIcon(userId: Int): Drawable {
    val id =
        if (Utils.isManagedProfile(this, userId)) {
            R.drawable.auth_dialog_enterprise
        } else {
            R.drawable.auth_dialog_lock
        }
    return resources.getDrawable(id, theme)
}

private fun Context.asResetTitle(credentialKind: PromptKind): String =
    getString(
        when (credentialKind) {
            PromptKind.Pin -> R.string.biometric_dialog_enter_pin
            PromptKind.Pattern -> R.string.biometric_dialog_enter_pattern
            PromptKind.Password -> R.string.biometric_dialog_enter_password
            else -> 0 // credentialKind should always be a credential
        }
    )

private fun Context.asResetSubtitle(credentialKind: PromptKind): String =
    getString(
        when (credentialKind) {
            PromptKind.Pin -> R.string.biometric_dialog_recovery_pin
            PromptKind.Pattern -> R.string.biometric_dialog_recovery_pattern
            PromptKind.Password -> R.string.biometric_dialog_recovery_password
            else -> 0 // credentialKind should always be a credential
        }
    )

private class BiometricPromptHeaderViewModelImpl(
    val request: BiometricPromptRequest.Credential,
    override val user: BiometricUserInfo,
    override val title: String,
    override val subtitle: String = "",
    override val description: String = "",
    override val contentView: PromptContentView? = null,
    override val icon: Drawable,
    override val showEmergencyCallButton: Boolean,
) : CredentialHeaderViewModel

private fun CredentialHeaderViewModel.asRequest(): BiometricPromptRequest.Credential =
    (this as BiometricPromptHeaderViewModelImpl).request
