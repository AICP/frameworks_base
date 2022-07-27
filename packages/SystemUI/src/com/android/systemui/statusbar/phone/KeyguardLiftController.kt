/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.systemui.statusbar.phone

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.TriggerEvent
import android.hardware.TriggerEventListener
import android.os.PowerManager
import android.os.SystemClock
import com.android.keyguard.ActiveUnlockConfig
import com.android.keyguard.FaceAuthApiRequestReason
import com.android.keyguard.KeyguardUpdateMonitor
import com.android.keyguard.KeyguardUpdateMonitorCallback
import com.android.systemui.CoreStartable
import com.android.systemui.Dumpable
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dump.DumpManager
import com.android.systemui.plugins.statusbar.StatusBarStateController
import com.android.systemui.util.Assert
import com.android.systemui.util.sensors.AsyncSensorManager
import java.io.PrintWriter
import javax.inject.Inject

@SysUISingleton
class KeyguardLiftController @Inject constructor(
    private val context: Context,
    private val statusBarStateController: StatusBarStateController,
    private val asyncSensorManager: AsyncSensorManager,
    private val keyguardUpdateMonitor: KeyguardUpdateMonitor,
    private val dumpManager: DumpManager
) : Dumpable, CoreStartable(context) {

    private val pickupSensor = asyncSensorManager.getDefaultSensor(Sensor.TYPE_PICK_UP_GESTURE)
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val hasFaceFeature = context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_FACE)
    private val hasDozePickup = context.resources.getBoolean(com.android.internal.R.bool.config_dozePulsePickup)
    private var isPickupWake = false
    private var isListening = false
    private var bouncerVisible = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_SCREEN_OFF)
                isPickupWake = false
        }
    }

    override fun start() {
        if (hasFaceFeature || hasDozePickup) {
            init()
        }
    }

    private fun init() {
        dumpManager.registerDumpable(javaClass.name, this)
        statusBarStateController.addCallback(statusBarStateListener)
        keyguardUpdateMonitor.registerCallback(keyguardUpdateMonitorCallback)
        context.registerReceiver(receiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
        updateListeningState()
    }

    private val listener: TriggerEventListener = object : TriggerEventListener() {
        override fun onTrigger(event: TriggerEvent?) {
            Assert.isMainThread()
            val ev = event?.values?.get(0)
            if (ev == 2f) {
                powerManager.goToSleep(SystemClock.uptimeMillis())
            } else if (ev == 1f && isFaceEnabled()) {
                keyguardUpdateMonitor.requestFaceAuth(
                    true,
                    FaceAuthApiRequestReason.PICK_UP_GESTURE_TRIGGERED
                )
                keyguardUpdateMonitor.requestActiveUnlock(
                    ActiveUnlockConfig.ACTIVE_UNLOCK_REQUEST_ORIGIN.WAKE,
                    "KeyguardLiftController")
            }
            // Not listening anymore since trigger events unregister themselves
            isListening = false
            isPickupWake = false
            updateListeningState()
        }
    }

    private val keyguardUpdateMonitorCallback = object : KeyguardUpdateMonitorCallback() {
        override fun onKeyguardBouncerFullyShowingChanged(bouncer: Boolean) {
            bouncerVisible = bouncer
            updateListeningState()
        }

        override fun onKeyguardVisibilityChanged(showing: Boolean) {
            updateListeningState()
        }
    }

    private val statusBarStateListener = object : StatusBarStateController.StateListener {
        override fun onDozingChanged(isDozing: Boolean) {
            updateListeningState()
        }
    }

    override fun dump(pw: PrintWriter, args: Array<out String>) {
        pw.println("KeyguardLiftController:")
        pw.println("  pickupSensor: $pickupSensor")
        pw.println("  isPickupWake: $isPickupWake")
        pw.println("  isListening: $isListening")
        pw.println("  bouncerVisible: $bouncerVisible")
    }

    private fun updateListeningState() {
        if (pickupSensor == null) {
            return
        }
        val onKeyguard = keyguardUpdateMonitor.isKeyguardVisible &&
                !statusBarStateController.isDozing

        val shouldListen = (onKeyguard || bouncerVisible) && (isFaceEnabled() || isPickupWake)
        if (shouldListen != isListening) {
            isListening = shouldListen

            if (shouldListen) {
                asyncSensorManager.requestTriggerSensor(listener, pickupSensor)
            } else {
                asyncSensorManager.cancelTriggerSensor(listener, pickupSensor)
            }
        }
    }

    private fun isFaceEnabled(): Boolean {
        if (!hasFaceFeature) {
            return false
        }
        val userId = KeyguardUpdateMonitor.getCurrentUser()
        return keyguardUpdateMonitor.isFaceAuthEnabledForUser(userId);
    }

    public fun setPickupWake(state: Boolean) {
        isPickupWake = state
        updateListeningState()
    }
}
