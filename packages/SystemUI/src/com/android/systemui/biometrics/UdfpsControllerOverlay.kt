/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.systemui.biometrics

import android.annotation.SuppressLint
import android.annotation.UiThread
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.Rect
import android.hardware.biometrics.BiometricOverlayConstants.REASON_AUTH_BP
import android.hardware.biometrics.BiometricOverlayConstants.REASON_AUTH_KEYGUARD
import android.hardware.biometrics.BiometricOverlayConstants.REASON_AUTH_OTHER
import android.hardware.biometrics.BiometricOverlayConstants.REASON_AUTH_SETTINGS
import android.hardware.biometrics.BiometricOverlayConstants.REASON_ENROLL_ENROLLING
import android.hardware.biometrics.BiometricOverlayConstants.REASON_ENROLL_FIND_SENSOR
import android.hardware.biometrics.BiometricOverlayConstants.ShowReason
import android.hardware.fingerprint.FingerprintManager
import android.hardware.fingerprint.IUdfpsOverlayControllerCallback
import android.os.Build
import android.os.RemoteException
import android.provider.Settings
import android.util.Log
import android.util.RotationUtils
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.Surface
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityManager.TouchExplorationStateChangeListener
import androidx.annotation.LayoutRes
import androidx.annotation.VisibleForTesting
import com.android.keyguard.KeyguardUpdateMonitor
import com.android.settingslib.udfps.UdfpsOverlayParams
import com.android.settingslib.udfps.UdfpsUtils
import com.android.systemui.R
import com.android.systemui.animation.ActivityLaunchAnimator
import com.android.systemui.biometrics.ui.controller.UdfpsKeyguardViewController
import com.android.systemui.bouncer.domain.interactor.AlternateBouncerInteractor
import com.android.systemui.bouncer.domain.interactor.PrimaryBouncerInteractor
import com.android.systemui.dump.DumpManager
import com.android.systemui.flags.FeatureFlags
import com.android.systemui.flags.Flags
import com.android.systemui.flags.Flags.REFACTOR_UDFPS_KEYGUARD_VIEWS
import com.android.systemui.keyguard.ui.adapter.UdfpsKeyguardViewControllerAdapter
import com.android.systemui.keyguard.ui.viewmodel.UdfpsKeyguardViewModels
import com.android.systemui.plugins.statusbar.StatusBarStateController
import com.android.systemui.shade.ShadeExpansionStateManager
import com.android.systemui.statusbar.LockscreenShadeTransitionController
import com.android.systemui.statusbar.phone.StatusBarKeyguardViewManager
import com.android.systemui.statusbar.phone.SystemUIDialogManager
import com.android.systemui.statusbar.phone.UnlockedScreenOffAnimationController
import com.android.systemui.statusbar.policy.ConfigurationController
import com.android.systemui.statusbar.policy.KeyguardStateController
import com.android.systemui.util.settings.SecureSettings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import javax.inject.Provider

private const val TAG = "UdfpsControllerOverlay"

@VisibleForTesting
const val SETTING_REMOVE_ENROLLMENT_UI = "udfps_overlay_remove_enrollment_ui"

/**
 * Keeps track of the overlay state and UI resources associated with a single FingerprintService
 * request. This state can persist across configuration changes via the [show] and [hide]
 * methods.
 */
@ExperimentalCoroutinesApi
@UiThread
class UdfpsControllerOverlay @JvmOverloads constructor(
        private val context: Context,
        fingerprintManager: FingerprintManager,
        private val inflater: LayoutInflater,
        private val windowManager: WindowManager,
        private val accessibilityManager: AccessibilityManager,
        private val statusBarStateController: StatusBarStateController,
        private val shadeExpansionStateManager: ShadeExpansionStateManager,
        private val statusBarKeyguardViewManager: StatusBarKeyguardViewManager,
        private val keyguardUpdateMonitor: KeyguardUpdateMonitor,
        private val dialogManager: SystemUIDialogManager,
        private val dumpManager: DumpManager,
        private val transitionController: LockscreenShadeTransitionController,
        private val configurationController: ConfigurationController,
        private val keyguardStateController: KeyguardStateController,
        private val unlockedScreenOffAnimationController: UnlockedScreenOffAnimationController,
        private var udfpsDisplayModeProvider: UdfpsDisplayModeProvider,
        private val secureSettings: SecureSettings,
        val requestId: Long,
        @ShowReason val requestReason: Int,
        private val controllerCallback: IUdfpsOverlayControllerCallback,
        private val onTouch: (View, MotionEvent, Boolean) -> Boolean,
        private val activityLaunchAnimator: ActivityLaunchAnimator,
        private val featureFlags: FeatureFlags,
        private val primaryBouncerInteractor: PrimaryBouncerInteractor,
        private val alternateBouncerInteractor: AlternateBouncerInteractor,
        private val isDebuggable: Boolean = Build.IS_DEBUGGABLE,
        private val udfpsUtils: UdfpsUtils,
        private val udfpsKeyguardAccessibilityDelegate: UdfpsKeyguardAccessibilityDelegate,
        private val udfpsKeyguardViewModels: Provider<UdfpsKeyguardViewModels>,
) {
    /** The view, when [isShowing], or null. */
    var overlayView: UdfpsView? = null
        private set

    private var overlayParams: UdfpsOverlayParams = UdfpsOverlayParams()
    private var sensorBounds: Rect = Rect()

    private var overlayTouchListener: TouchExplorationStateChangeListener? = null

    private val coreLayoutParams = WindowManager.LayoutParams(
        WindowManager.LayoutParams.TYPE_NAVIGATION_BAR_PANEL,
        0 /* flags set in computeLayoutParams() */,
        PixelFormat.TRANSLUCENT
    ).apply {
        title = TAG
        fitInsetsTypes = 0
        gravity = android.view.Gravity.TOP or android.view.Gravity.LEFT
        layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        flags = (Utils.FINGERPRINT_OVERLAY_LAYOUT_PARAM_FLAGS or
                WindowManager.LayoutParams.FLAG_SPLIT_TOUCH)
        privateFlags = WindowManager.LayoutParams.PRIVATE_FLAG_TRUSTED_OVERLAY
        // Avoid announcing window title.
        accessibilityTitle = " "

        if (featureFlags.isEnabled(Flags.UDFPS_NEW_TOUCH_DETECTION)) {
            inputFeatures = WindowManager.LayoutParams.INPUT_FEATURE_SPY
        }
    }

    /** If the overlay is currently showing. */
    val isShowing: Boolean
        get() = overlayView != null

    /** Opposite of [isShowing]. */
    val isHiding: Boolean
        get() = overlayView == null

    /** The animation controller if the overlay [isShowing]. */
    val animationViewController: UdfpsAnimationViewController<*>?
        get() = overlayView?.animationViewController

    private var touchExplorationEnabled = false

    private fun shouldRemoveEnrollmentUi(): Boolean {
        if (isDebuggable) {
            return Settings.Global.getInt(
                context.contentResolver,
                SETTING_REMOVE_ENROLLMENT_UI,
                0 /* def */
            ) != 0
        }
        return false
    }

    /** Show the overlay or return false and do nothing if it is already showing. */
    @SuppressLint("ClickableViewAccessibility")
    fun show(controller: UdfpsController, params: UdfpsOverlayParams): Boolean {
        if (overlayView == null) {
            overlayParams = params
            sensorBounds = Rect(params.sensorBounds)
            try {
                overlayView = (inflater.inflate(
                    R.layout.udfps_view, null, false
                ) as UdfpsView).apply {
                    overlayParams = params
                    setUdfpsDisplayModeProvider(udfpsDisplayModeProvider)
                    val animation = inflateUdfpsAnimation(this, controller)
                    if (animation != null) {
                        animation.init()
                        animationViewController = animation
                    }
                    // This view overlaps the sensor area
                    // prevent it from being selectable during a11y
                    if (requestReason.isImportantForAccessibility()) {
                        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    }

                    windowManager.addView(this, coreLayoutParams.updateDimensions(animation))
                    sensorRect = sensorBounds
                    touchExplorationEnabled = accessibilityManager.isTouchExplorationEnabled
                    overlayTouchListener = TouchExplorationStateChangeListener {
                        if (accessibilityManager.isTouchExplorationEnabled) {
                            setOnHoverListener { v, event -> onTouch(v, event, true) }
                            setOnTouchListener(null)
                            touchExplorationEnabled = true
                        } else {
                            setOnHoverListener(null)
                            setOnTouchListener { v, event -> onTouch(v, event, true) }
                            touchExplorationEnabled = false
                        }
                    }
                    accessibilityManager.addTouchExplorationStateChangeListener(
                        overlayTouchListener!!
                    )
                    overlayTouchListener?.onTouchExplorationStateChanged(true)
                    useExpandedOverlay = featureFlags.isEnabled(Flags.UDFPS_NEW_TOUCH_DETECTION)
                }
            } catch (e: RuntimeException) {
                Log.e(TAG, "showUdfpsOverlay | failed to add window", e)
            }
            return true
        }

        Log.v(TAG, "showUdfpsOverlay | the overlay is already showing")
        return false
    }

    fun inflateUdfpsAnimation(
        view: UdfpsView,
        controller: UdfpsController
    ): UdfpsAnimationViewController<*>? {
        val isEnrollment = when (requestReason) {
            REASON_ENROLL_FIND_SENSOR, REASON_ENROLL_ENROLLING -> true
            else -> false
        }

        val filteredRequestReason = if (isEnrollment && shouldRemoveEnrollmentUi()) {
            REASON_AUTH_OTHER
        } else {
            requestReason
        }

        return when (filteredRequestReason) {
            REASON_ENROLL_FIND_SENSOR,
            REASON_ENROLL_ENROLLING -> {
                // Enroll udfps UI is handled by settings, so use empty view here
                UdfpsFpmEmptyViewController(
                    view.addUdfpsView(R.layout.udfps_fpm_empty_view){
                        updateAccessibilityViewLocation(sensorBounds)
                    },
                    statusBarStateController,
                    shadeExpansionStateManager,
                    dialogManager,
                    dumpManager
                )
            }
            REASON_AUTH_KEYGUARD -> {
                if (featureFlags.isEnabled(REFACTOR_UDFPS_KEYGUARD_VIEWS)) {
                    udfpsKeyguardViewModels.get().setSensorBounds(sensorBounds)
                    UdfpsKeyguardViewController(
                        view.addUdfpsView(R.layout.udfps_keyguard_view),
                        statusBarStateController,
                        shadeExpansionStateManager,
                        dialogManager,
                        dumpManager,
                        alternateBouncerInteractor,
                        udfpsKeyguardViewModels.get(),
                    )
                } else {
                    UdfpsKeyguardViewControllerLegacy(
                        view.addUdfpsView(R.layout.udfps_keyguard_view_legacy) {
                            updateSensorLocation(sensorBounds)
                        },
                        statusBarStateController,
                        shadeExpansionStateManager,
                        statusBarKeyguardViewManager,
                        keyguardUpdateMonitor,
                        dumpManager,
                        transitionController,
                        configurationController,
                        keyguardStateController,
                        unlockedScreenOffAnimationController,
                        dialogManager,
                        controller,
                        activityLaunchAnimator,
                        featureFlags,
                        primaryBouncerInteractor,
                        alternateBouncerInteractor,
                        udfpsKeyguardAccessibilityDelegate,
                    )
                }
            }
            REASON_AUTH_BP -> {
                // note: empty controller, currently shows no visual affordance
                UdfpsBpViewController(
                    view.addUdfpsView(R.layout.udfps_bp_view),
                    statusBarStateController,
                    shadeExpansionStateManager,
                    dialogManager,
                    dumpManager
                )
            }
            REASON_AUTH_OTHER,
            REASON_AUTH_SETTINGS -> {
                UdfpsFpmEmptyViewController(
                    view.addUdfpsView(R.layout.udfps_fpm_empty_view),
                    statusBarStateController,
                    shadeExpansionStateManager,
                    dialogManager,
                    dumpManager
                )
            }
            else -> {
                Log.e(TAG, "Animation for reason $requestReason not supported yet")
                null
            }
        }
    }

    /** Hide the overlay or return false and do nothing if it is already hidden. */
    fun hide(): Boolean {
        val wasShowing = isShowing

        overlayView?.apply {
            if (isDisplayConfigured) {
                unconfigureDisplay()
            }
            windowManager.removeView(this)
            setOnTouchListener(null)
            setOnHoverListener(null)
            animationViewController = null
            overlayTouchListener?.let {
                accessibilityManager.removeTouchExplorationStateChangeListener(it)
            }
        }
        overlayView = null
        overlayTouchListener = null

        return wasShowing
    }

    /**
     * This function computes the angle of touch relative to the sensor and maps
     * the angle to a list of help messages which are announced if accessibility is enabled.
     *
     */
    fun onTouchOutsideOfSensorArea(scaledTouch: Point) {
        val theStr =
            udfpsUtils.onTouchOutsideOfSensorArea(
                touchExplorationEnabled,
                context,
                scaledTouch.x,
                scaledTouch.y,
                overlayParams
            )
        if (theStr != null) {
            animationViewController?.doAnnounceForAccessibility(theStr)
        }
    }

    /** Cancel this request. */
    fun cancel() {
        try {
            controllerCallback.onUserCanceled()
        } catch (e: RemoteException) {
            Log.e(TAG, "Remote exception", e)
        }
    }

    /** Checks if the id is relevant for this overlay. */
    fun matchesRequestId(id: Long): Boolean = requestId == -1L || requestId == id

    private fun WindowManager.LayoutParams.updateDimensions(
        animation: UdfpsAnimationViewController<*>?
    ): WindowManager.LayoutParams {
        val paddingX = animation?.paddingX ?: 0
        val paddingY = animation?.paddingY ?: 0
        if (!featureFlags.isEnabled(Flags.UDFPS_NEW_TOUCH_DETECTION) && animation != null &&
                animation.listenForTouchesOutsideView()) {
            flags = flags or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
        }

        val isEnrollment = when (requestReason) {
            REASON_ENROLL_FIND_SENSOR, REASON_ENROLL_ENROLLING -> true
            else -> false
        }

        // Use expanded overlay unless touchExploration enabled
        var rotatedBounds =
            if (featureFlags.isEnabled(Flags.UDFPS_NEW_TOUCH_DETECTION)) {
                if (accessibilityManager.isTouchExplorationEnabled && isEnrollment) {
                    Rect(overlayParams.sensorBounds)
                } else {
                    Rect(
                        0,
                        0,
                        overlayParams.naturalDisplayWidth,
                        overlayParams.naturalDisplayHeight
                    )
                }
            } else {
                Rect(overlayParams.sensorBounds)
            }

        val rot = overlayParams.rotation
        if (rot == Surface.ROTATION_90 || rot == Surface.ROTATION_270) {
            if (!shouldRotate(animation)) {
                Log.v(
                    TAG, "Skip rotating UDFPS bounds " + Surface.rotationToString(rot) +
                            " animation=$animation" +
                            " isGoingToSleep=${keyguardUpdateMonitor.isGoingToSleep}" +
                            " isOccluded=${keyguardStateController.isOccluded}"
                )
            } else {
                Log.v(TAG, "Rotate UDFPS bounds " + Surface.rotationToString(rot))
                RotationUtils.rotateBounds(
                    rotatedBounds,
                    overlayParams.naturalDisplayWidth,
                    overlayParams.naturalDisplayHeight,
                    rot
                )

                if (featureFlags.isEnabled(Flags.UDFPS_NEW_TOUCH_DETECTION)) {
                    RotationUtils.rotateBounds(
                            sensorBounds,
                            overlayParams.naturalDisplayWidth,
                            overlayParams.naturalDisplayHeight,
                            rot
                    )
                }
            }
        }

        x = rotatedBounds.left - paddingX
        y = rotatedBounds.top - paddingY
        height = rotatedBounds.height() + 2 * paddingX
        width = rotatedBounds.width() + 2 * paddingY

        return this
    }

    private fun shouldRotate(animation: UdfpsAnimationViewController<*>?): Boolean {
        if (animation !is UdfpsKeyguardViewControllerAdapter) {
            // always rotate view if we're not on the keyguard
            return true
        }

        // on the keyguard, make sure we don't rotate if we're going to sleep or not occluded
        return !(keyguardUpdateMonitor.isGoingToSleep || !keyguardStateController.isOccluded)
    }

    private inline fun <reified T : View> UdfpsView.addUdfpsView(
        @LayoutRes id: Int,
        init: T.() -> Unit = {}
    ): T {
        val subView = inflater.inflate(id, null) as T
        addView(subView)
        subView.init()
        return subView
    }
}

@ShowReason
private fun Int.isImportantForAccessibility() =
    this == REASON_ENROLL_FIND_SENSOR ||
            this == REASON_ENROLL_ENROLLING ||
            this == REASON_AUTH_BP
