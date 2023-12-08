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
package com.android.keyguard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Resources
import android.text.format.DateFormat
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.view.View.OnAttachStateChangeListener
import android.view.ViewTreeObserver
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import android.widget.FrameLayout
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.android.systemui.Dependency
import com.android.systemui.R
import com.android.systemui.broadcast.BroadcastDispatcher
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.flags.FeatureFlags
import com.android.systemui.flags.Flags.DOZING_MIGRATION_1
import com.android.systemui.flags.Flags.REGION_SAMPLING
import com.android.systemui.keyguard.domain.interactor.KeyguardInteractor
import com.android.systemui.keyguard.domain.interactor.KeyguardTransitionInteractor
import com.android.systemui.keyguard.shared.model.TransitionState
import com.android.systemui.lifecycle.repeatWhenAttached
import com.android.systemui.log.LogBuffer
import com.android.systemui.log.core.LogLevel.DEBUG
import com.android.systemui.log.dagger.KeyguardLargeClockLog
import com.android.systemui.log.dagger.KeyguardSmallClockLog
import com.android.systemui.omni.OmniSettingsService
import com.android.systemui.plugins.ClockController
import com.android.systemui.plugins.ClockFaceController
import com.android.systemui.plugins.ClockTickRate
import com.android.systemui.plugins.WeatherData
import com.android.systemui.shared.regionsampling.RegionSampler
import com.android.systemui.statusbar.policy.BatteryController
import com.android.systemui.statusbar.policy.BatteryController.BatteryStateChangeCallback
import com.android.systemui.statusbar.policy.ConfigurationController
import com.android.systemui.util.concurrency.DelayableExecutor
import org.omnirom.omnilib.utils.OmniSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.Executor
import javax.inject.Inject

/**
 * Controller for a Clock provided by the registry and used on the keyguard. Instantiated by
 * [KeyguardClockSwitchController]. Functionality is forked from [AnimatableClockController].
 */
open class ClockEventController
@Inject
constructor(
    private val keyguardInteractor: KeyguardInteractor,
    private val keyguardTransitionInteractor: KeyguardTransitionInteractor,
    private val broadcastDispatcher: BroadcastDispatcher,
    private val batteryController: BatteryController,
    private val keyguardUpdateMonitor: KeyguardUpdateMonitor,
    private val configurationController: ConfigurationController,
    @Main private val resources: Resources,
    private val context: Context,
    @Main private val mainExecutor: DelayableExecutor,
    @Background private val bgExecutor: Executor,
    @KeyguardSmallClockLog private val smallLogBuffer: LogBuffer?,
    @KeyguardLargeClockLog private val largeLogBuffer: LogBuffer?,
    private val featureFlags: FeatureFlags
) {
    var clock: ClockController? = null
        set(value) {
            smallClockOnAttachStateChangeListener?.let {
                field?.smallClock?.view?.removeOnAttachStateChangeListener(it)
                smallClockFrame?.viewTreeObserver
                        ?.removeOnGlobalLayoutListener(onGlobalLayoutListener)
            }
            largeClockOnAttachStateChangeListener?.let {
                field?.largeClock?.view?.removeOnAttachStateChangeListener(it)
            }

            field = value
            if (value != null) {
                smallLogBuffer?.log(TAG, DEBUG, {}, { "New Clock" })
                value.smallClock.messageBuffer = smallLogBuffer
                largeLogBuffer?.log(TAG, DEBUG, {}, { "New Clock" })
                value.largeClock.messageBuffer = largeLogBuffer

                value.initialize(resources, dozeAmount, 0f)

                if (!regionSamplingEnabled) {
                    updateColors()
                } else {
                    clock?.let {
                        smallRegionSampler = createRegionSampler(
                                it.smallClock.view,
                                mainExecutor,
                                bgExecutor,
                                regionSamplingEnabled,
                                isLockscreen = true,
                                ::updateColors
                        )?.apply { startRegionSampler() }

                        largeRegionSampler = createRegionSampler(
                                it.largeClock.view,
                                mainExecutor,
                                bgExecutor,
                                regionSamplingEnabled,
                                isLockscreen = true,
                                ::updateColors
                        )?.apply { startRegionSampler() }

                        updateColors()
                    }
                }
                updateFontSizes()
                updateTimeListeners()
                cachedWeatherData?.let {
                    if (WeatherData.DEBUG) {
                        Log.i(TAG, "Pushing cached weather data to new clock: $it")
                    }
                    value.events.onWeatherDataChanged(it)
                }

                smallClockOnAttachStateChangeListener =
                    object : OnAttachStateChangeListener {
                        var pastVisibility: Int? = null
                        override fun onViewAttachedToWindow(view: View) {
                            value.events.onTimeFormatChanged(DateFormat.is24HourFormat(context))
                            if (view != null) {
                                smallClockFrame = view.parent as FrameLayout
                                smallClockFrame?.let {frame ->
                                    pastVisibility = frame.visibility
                                    onGlobalLayoutListener = OnGlobalLayoutListener {
                                        val currentVisibility = frame.visibility
                                        if (pastVisibility != currentVisibility) {
                                            pastVisibility = currentVisibility
                                            // when small clock  visible,
                                            // recalculate bounds and sample
                                            if (currentVisibility == View.VISIBLE) {
                                                smallRegionSampler?.stopRegionSampler()
                                                smallRegionSampler?.startRegionSampler()
                                            }
                                        }
                                    }
                                    frame.viewTreeObserver
                                            .addOnGlobalLayoutListener(onGlobalLayoutListener)
                                }
                            }
                        }

                        override fun onViewDetachedFromWindow(p0: View) {
                            smallClockFrame?.viewTreeObserver
                                    ?.removeOnGlobalLayoutListener(onGlobalLayoutListener)
                        }
                }
                value.smallClock.view
                        .addOnAttachStateChangeListener(smallClockOnAttachStateChangeListener)

                largeClockOnAttachStateChangeListener =
                    object : OnAttachStateChangeListener {
                        override fun onViewAttachedToWindow(p0: View) {
                            value.events.onTimeFormatChanged(DateFormat.is24HourFormat(context))
                        }
                        override fun onViewDetachedFromWindow(p0: View) {
                        }
                }
                value.largeClock.view
                        .addOnAttachStateChangeListener(largeClockOnAttachStateChangeListener)
            }
        }

    @VisibleForTesting
    var smallClockOnAttachStateChangeListener: OnAttachStateChangeListener? = null
    @VisibleForTesting
    var largeClockOnAttachStateChangeListener: OnAttachStateChangeListener? = null
    private var smallClockFrame: FrameLayout? = null
    private var onGlobalLayoutListener: OnGlobalLayoutListener? = null

    private var isDozing = false
        private set

    private var isCharging = false
    private var dozeAmount = 0f
    private var isKeyguardVisible = false
    private var isRegistered = false
    private var disposableHandle: DisposableHandle? = null
    private val regionSamplingEnabled = featureFlags.isEnabled(REGION_SAMPLING)


    private fun updateColors() {
        if (regionSamplingEnabled) {
            clock?.let { clock ->
                smallRegionSampler?.let {
                    smallClockIsDark = it.currentRegionDarkness().isDark
                    clock.smallClock.events.onRegionDarknessChanged(smallClockIsDark)
                }

                largeRegionSampler?.let {
                    largeClockIsDark = it.currentRegionDarkness().isDark
                    clock.largeClock.events.onRegionDarknessChanged(largeClockIsDark)
                }
            }
            return
        }

        val isLightTheme = TypedValue()
        context.theme.resolveAttribute(android.R.attr.isLightTheme, isLightTheme, true)
        smallClockIsDark = isLightTheme.data == 0
        largeClockIsDark = isLightTheme.data == 0

        clock?.run {
            smallClock.events.onRegionDarknessChanged(smallClockIsDark)
            largeClock.events.onRegionDarknessChanged(largeClockIsDark)
        }
    }
    protected open fun createRegionSampler(
        sampledView: View,
        mainExecutor: Executor?,
        bgExecutor: Executor?,
        regionSamplingEnabled: Boolean,
        isLockscreen: Boolean,
        updateColors: () -> Unit
    ): RegionSampler? {
        return RegionSampler(
            sampledView,
            mainExecutor,
            bgExecutor,
            regionSamplingEnabled,
            isLockscreen,
        ) { updateColors() }
    }

    var smallRegionSampler: RegionSampler? = null
        private set
    var largeRegionSampler: RegionSampler? = null
        private set
    var smallTimeListener: TimeListener? = null
    var largeTimeListener: TimeListener? = null
    val shouldTimeListenerRun: Boolean
        get() = isKeyguardVisible && dozeAmount < DOZE_TICKRATE_THRESHOLD
    private var cachedWeatherData: WeatherData? = null

    private var smallClockIsDark = true
    private var largeClockIsDark = true

    private val configListener =
        object : ConfigurationController.ConfigurationListener {
            override fun onThemeChanged() {
                clock?.run { events.onColorPaletteChanged(resources) }
                updateColors()
            }

            override fun onDensityOrFontScaleChanged() {
                updateFontSizes()
            }
        }

    private val batteryCallback =
        object : BatteryStateChangeCallback {
            override fun onBatteryLevelChanged(level: Int, pluggedIn: Boolean, charging: Boolean) {
                if (isKeyguardVisible && !isCharging && charging) {
                    clock?.run {
                        smallClock.animations.charge()
                        largeClock.animations.charge()
                    }
                }
                isCharging = charging
            }
        }

    private val localeBroadcastReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                clock?.run { events.onLocaleChanged(Locale.getDefault()) }
            }
        }

    private val keyguardUpdateMonitorCallback =
        object : KeyguardUpdateMonitorCallback() {
            override fun onKeyguardVisibilityChanged(visible: Boolean) {
                isKeyguardVisible = visible
                if (!featureFlags.isEnabled(DOZING_MIGRATION_1)) {
                    if (!isKeyguardVisible) {
                        clock?.run {
                            smallClock.animations.doze(if (isDozing) 1f else 0f)
                            largeClock.animations.doze(if (isDozing) 1f else 0f)
                        }
                    }
                }

                smallTimeListener?.update(shouldTimeListenerRun)
                largeTimeListener?.update(shouldTimeListenerRun)
            }

            override fun onTimeFormatChanged(timeFormat: String?) {
                clock?.run { events.onTimeFormatChanged(DateFormat.is24HourFormat(context)) }
            }

            override fun onTimeZoneChanged(timeZone: TimeZone) {
                clock?.run { events.onTimeZoneChanged(timeZone) }
            }

            override fun onUserSwitchComplete(userId: Int) {
                clock?.run { events.onTimeFormatChanged(DateFormat.is24HourFormat(context)) }
            }

            override fun onWeatherDataChanged(data: WeatherData) {
                cachedWeatherData = data
                clock?.run { events.onWeatherDataChanged(data) }
            }
        }

    private val settingsListener = object : OmniSettingsService.OmniSettingsObserver {
        override fun onIntSettingChanged(key: String, newValue: Int) {
            clock?.events?.onColorPaletteChanged(resources)
            updateColors()
        }
    }

    fun registerListeners(parent: View) {
        if (isRegistered) {
            return
        }
        isRegistered = true
        broadcastDispatcher.registerReceiver(
            localeBroadcastReceiver,
            IntentFilter(Intent.ACTION_LOCALE_CHANGED)
        )
        configurationController.addCallback(configListener)
        batteryController.addCallback(batteryCallback)
        keyguardUpdateMonitor.registerCallback(keyguardUpdateMonitorCallback)
        disposableHandle =
            parent.repeatWhenAttached {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    listenForDozing(this)
                    if (featureFlags.isEnabled(DOZING_MIGRATION_1)) {
                        listenForDozeAmountTransition(this)
                        listenForAnyStateToAodTransition(this)
                    } else {
                        listenForDozeAmount(this)
                    }
                }
            }
        smallTimeListener?.update(shouldTimeListenerRun)
        largeTimeListener?.update(shouldTimeListenerRun)
        Dependency.get(OmniSettingsService::class.java).addIntObserver(settingsListener, OmniSettings.OMNI_LOCKSCREEN_CLOCK_COLORED)
    }

    fun unregisterListeners() {
        if (!isRegistered) {
            return
        }
        isRegistered = false

        disposableHandle?.dispose()
        broadcastDispatcher.unregisterReceiver(localeBroadcastReceiver)
        configurationController.removeCallback(configListener)
        batteryController.removeCallback(batteryCallback)
        keyguardUpdateMonitor.removeCallback(keyguardUpdateMonitorCallback)
        smallRegionSampler?.stopRegionSampler()
        largeRegionSampler?.stopRegionSampler()
        smallTimeListener?.stop()
        largeTimeListener?.stop()
        Dependency.get(OmniSettingsService::class.java).removeObserver(settingsListener)
        clock?.smallClock?.view
                ?.removeOnAttachStateChangeListener(smallClockOnAttachStateChangeListener)
        smallClockFrame?.viewTreeObserver
                ?.removeOnGlobalLayoutListener(onGlobalLayoutListener)
        clock?.largeClock?.view
                ?.removeOnAttachStateChangeListener(largeClockOnAttachStateChangeListener)
    }

    private fun updateTimeListeners() {
        smallTimeListener?.stop()
        largeTimeListener?.stop()

        smallTimeListener = null
        largeTimeListener = null

        clock?.let {
            smallTimeListener = TimeListener(it.smallClock, mainExecutor).apply {
                update(shouldTimeListenerRun)
            }
            largeTimeListener = TimeListener(it.largeClock, mainExecutor).apply {
                update(shouldTimeListenerRun)
            }
        }
    }

    private fun updateFontSizes() {
        clock?.run {
            smallClock.events.onFontSettingChanged(
                resources.getDimensionPixelSize(R.dimen.small_clock_text_size).toFloat()
            )
            largeClock.events.onFontSettingChanged(
                resources.getDimensionPixelSize(R.dimen.large_clock_text_size).toFloat()
            )
        }
    }

    private fun handleDoze(doze: Float) {
        dozeAmount = doze
        clock?.run {
            smallClock.animations.doze(dozeAmount)
            largeClock.animations.doze(dozeAmount)
        }
        smallTimeListener?.update(doze < DOZE_TICKRATE_THRESHOLD)
        largeTimeListener?.update(doze < DOZE_TICKRATE_THRESHOLD)
    }

    @VisibleForTesting
    internal fun listenForDozeAmount(scope: CoroutineScope): Job {
        return scope.launch { keyguardInteractor.dozeAmount.collect { handleDoze(it) } }
    }

    @VisibleForTesting
    internal fun listenForDozeAmountTransition(scope: CoroutineScope): Job {
        return scope.launch {
            keyguardTransitionInteractor.dozeAmountTransition.collect { handleDoze(it.value) }
        }
    }

    /**
     * When keyguard is displayed again after being gone, the clock must be reset to full dozing.
     */
    @VisibleForTesting
    internal fun listenForAnyStateToAodTransition(scope: CoroutineScope): Job {
        return scope.launch {
            keyguardTransitionInteractor.anyStateToAodTransition
                .filter { it.transitionState == TransitionState.FINISHED }
                .collect { handleDoze(1f) }
        }
    }

    @VisibleForTesting
    internal fun listenForDozing(scope: CoroutineScope): Job {
        return scope.launch {
            combine(
                    keyguardInteractor.dozeAmount,
                    keyguardInteractor.isDozing,
                ) { localDozeAmount, localIsDozing ->
                    localDozeAmount > dozeAmount || localIsDozing
                }
                .collect { localIsDozing -> isDozing = localIsDozing }
        }
    }

    class TimeListener(val clockFace: ClockFaceController, val executor: DelayableExecutor) {
        val predrawListener =
            ViewTreeObserver.OnPreDrawListener {
                clockFace.events.onTimeTick()
                true
            }

        val secondsRunnable =
            object : Runnable {
                override fun run() {
                    if (!isRunning) {
                        return
                    }

                    executor.executeDelayed(this, 990)
                    clockFace.events.onTimeTick()
                }
            }

        var isRunning: Boolean = false
            private set

        fun start() {
            if (isRunning) {
                return
            }

            isRunning = true
            when (clockFace.config.tickRate) {
                ClockTickRate.PER_MINUTE -> {
                    /* Handled by KeyguardClockSwitchController */
                }
                ClockTickRate.PER_SECOND -> executor.execute(secondsRunnable)
                ClockTickRate.PER_FRAME -> {
                    clockFace.view.viewTreeObserver.addOnPreDrawListener(predrawListener)
                    clockFace.view.invalidate()
                }
            }
        }

        fun stop() {
            if (!isRunning) {
                return
            }

            isRunning = false
            clockFace.view.viewTreeObserver.removeOnPreDrawListener(predrawListener)
        }

        fun update(shouldRun: Boolean) = if (shouldRun) start() else stop()
    }

    companion object {
        private val TAG = ClockEventController::class.simpleName!!
        private val DOZE_TICKRATE_THRESHOLD = 0.99f
    }
}
