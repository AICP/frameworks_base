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

package com.android.systemui.statusbar.events

import android.annotation.IntRange
import android.content.Context
import android.provider.DeviceConfig
import android.provider.DeviceConfig.NAMESPACE_PRIVACY
import com.android.internal.annotations.VisibleForTesting
import com.android.systemui.Flags
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.display.domain.interactor.ConnectedDisplayInteractor
import com.android.systemui.log.LogBuffer
import com.android.systemui.log.core.LogLevel
import com.android.systemui.privacy.PrivacyChipBuilder
import com.android.systemui.privacy.PrivacyItem
import com.android.systemui.privacy.PrivacyItemController
import com.android.systemui.privacy.PrivacyType
import com.android.systemui.res.R
import com.android.systemui.statusbar.featurepods.vc.domain.interactor.AvControlsChipInteractor
import com.android.systemui.statusbar.pipeline.battery.domain.interactor.BatteryInteractor
import com.android.systemui.statusbar.policy.BatteryController
import com.android.systemui.util.time.SystemClock
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Listens for system events (battery, privacy, connectivity) and allows listeners to show status
 * bar animations when they happen
 */
@SysUISingleton
class SystemEventCoordinator
@Inject
constructor(
    private val systemClock: SystemClock,
    private val batteryController: BatteryController,
    private val batteryInteractor: BatteryInteractor,
    private val privacyController: PrivacyItemController,
    private val avControlsChipInteractor: AvControlsChipInteractor,
    private val context: Context,
    @Application private val appScope: CoroutineScope,
    connectedDisplayInteractor: ConnectedDisplayInteractor,
    @SystemEventCoordinatorLog private val logBuffer: LogBuffer,
) {
    private val onDisplayConnectedFlow = connectedDisplayInteractor.connectedDisplayAddition
    private val defaultCameraPackageName =
        context.resources.getString(R.string.config_cameraGesturePackage)

    private var connectedDisplayCollectionJob: Job? = null
    private lateinit var scheduler: SystemStatusAnimationScheduler

    fun startObserving() {
        batteryController.addCallback(batteryStateListener)
        privacyController.addCallback(privacyStateListener)
        startConnectedDisplayCollection()
    }

    fun stopObserving() {
        batteryController.removeCallback(batteryStateListener)
        privacyController.removeCallback(privacyStateListener)
        connectedDisplayCollectionJob?.cancel()
    }

    fun attachScheduler(s: SystemStatusAnimationScheduler) {
        this.scheduler = s
    }

    fun notifyPluggedIn(@IntRange(from = 0, to = 100) batteryLevel: Int) {
        scheduler.onStatusEvent(
            BatteryEvent(
                batteryLevel,
                batteryInteractor.batteryIconStyle.value,
                batteryInteractor.showPercentNextToIcon.value,
            )
        )
    }

    fun notifyPrivacyItemsEmpty() {
        scheduler.removePersistentDot()
    }

    fun notifyPrivacyItemsChanged(showAnimation: Boolean = true) {
        // Disabling animation in case that the privacy indicator is implemented as a status bar
        // chip
        val shouldShowAnimation = showAnimation && !avControlsChipInteractor.isEnabled.value
        val event = PrivacyEvent(shouldShowAnimation)
        event.privacyItems = privacyStateListener.currentPrivacyItems
        event.contentDescription = run {
            val items = PrivacyChipBuilder(context, event.privacyItems).joinTypes()
            context.getString(R.string.ongoing_privacy_chip_content_multiple_apps, items)
        }
        scheduler.onStatusEvent(event)
    }

    private fun startConnectedDisplayCollection() {
        val connectedDisplayEvent =
            ConnectedDisplayEvent().apply {
                contentDescription = context.getString(R.string.connected_display_icon_desc)
            }
        connectedDisplayCollectionJob =
            onDisplayConnectedFlow
                .onEach { scheduler.onStatusEvent(connectedDisplayEvent) }
                .launchIn(appScope)
    }

    private val batteryStateListener =
        object : BatteryController.BatteryStateChangeCallback {
            private var plugged = false
            private var stateKnown = false

            override fun onBatteryLevelChanged(level: Int, pluggedIn: Boolean, charging: Boolean) {
                if (!stateKnown) {
                    stateKnown = true
                    plugged = pluggedIn
                    notifyListeners(level)
                    return
                }

                if (plugged != pluggedIn) {
                    plugged = pluggedIn
                    notifyListeners(level)
                }
            }

            private fun notifyListeners(@IntRange(from = 0, to = 100) batteryLevel: Int) {
                // We only care about the plugged in status
                if (plugged) notifyPluggedIn(batteryLevel)
            }
        }

    private val privacyStateListener =
        object : PrivacyItemController.Callback {
            var currentPrivacyItems = listOf<PrivacyItem>()
            var previousPrivacyItems = listOf<PrivacyItem>()
            var timeLastEmpty = systemClock.elapsedRealtime()

            override fun onPrivacyItemsChanged(privacyItems: List<PrivacyItem>) {
                if (uniqueItemsMatch(privacyItems, currentPrivacyItems)) {
                    return
                } else if (privacyItems.isEmpty()) {
                    previousPrivacyItems = currentPrivacyItems
                    timeLastEmpty = systemClock.elapsedRealtime()
                }

                currentPrivacyItems = privacyItems
                notifyListeners()
            }

            private fun notifyListeners() {
                if (currentPrivacyItems.isEmpty()) {
                    notifyPrivacyItemsEmpty()
                } else {
                    val showAnimation =
                        isChipAnimationEnabled() &&
                            !isExemptFromChipAnimation(currentPrivacyItems) &&
                            (!uniqueItemsMatch(currentPrivacyItems, previousPrivacyItems) ||
                                systemClock.elapsedRealtime() - timeLastEmpty >= DEBOUNCE_TIME)
                    notifyPrivacyItemsChanged(showAnimation)
                }
            }

            // Return true if the lists contain the same permission groups, used by the same UIDs
            private fun uniqueItemsMatch(one: List<PrivacyItem>, two: List<PrivacyItem>): Boolean {
                return one.map { it.application.uid to it.privacyType.permGroupName }.toSet() ==
                    two.map { it.application.uid to it.privacyType.permGroupName }.toSet()
            }

            // Returns true if the privacy items are exempt from the chip animation.
            private fun isExemptFromChipAnimation(items: List<PrivacyItem>): Boolean {
                if (!Flags.statusBarPrivacyChipAnimationExemption()) {
                    return containsOnlyLocation(items)
                }

                // Camera and microphone requests by the default camera app are exempt from the
                // chip animation. Filter those out.
                val nonExemptItems =
                    items.filterNot {
                        val shouldFilter = isCameraOrMicrophoneRequest(it) &&
                            it.application.packageName == defaultCameraPackageName
                        if (shouldFilter) {
                            logBuffer.log(
                                TAG,
                                LogLevel.DEBUG,
                                {
                                    str1 = it.application.packageName
                                    str2 = it.privacyType.permGroupName
                                },
                                {
                                    "Privacy item from default camera ($str1) is exempt from " +
                                    "chip animation. Permission group=$str2"
                                },
                            )
                        }

                        shouldFilter
                    }

                // If the remaining items are only location, the chip animation is also exempt
                return containsOnlyLocation(nonExemptItems)
            }

            // Return true if the only privacy item is location
            private fun containsOnlyLocation(items: List<PrivacyItem>): Boolean {
                return items
                    .filterNot {
                        it.privacyType.permGroupName == android.Manifest.permission_group.LOCATION
                    }
                    .isEmpty()
            }

            private fun isCameraOrMicrophoneRequest(item: PrivacyItem): Boolean {
                return item.privacyType.let {
                    it == PrivacyType.TYPE_CAMERA || it == PrivacyType.TYPE_MICROPHONE
                 }
            }

            private fun isChipAnimationEnabled(): Boolean {
                val defaultValue =
                    context.resources.getBoolean(R.bool.config_enablePrivacyChipAnimation)
                return DeviceConfig.getBoolean(
                    NAMESPACE_PRIVACY,
                    CHIP_ANIMATION_ENABLED,
                    defaultValue,
                )
            }
        }

        @VisibleForTesting
        fun getPrivacyStateListener() = privacyStateListener
}

private const val DEBOUNCE_TIME = 3000L
private const val CHIP_ANIMATION_ENABLED = "privacy_chip_animation_enabled"
private const val TAG = "SystemEventCoordinator"
