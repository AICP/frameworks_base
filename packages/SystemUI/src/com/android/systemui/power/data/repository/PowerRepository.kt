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
 *
 */

package com.android.systemui.power.data.repository

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import com.android.systemui.broadcast.BroadcastDispatcher
import com.android.systemui.common.coroutine.ChannelExt.trySendWithFailureLogging
import com.android.systemui.common.coroutine.ConflatedCallbackFlow.conflatedCallbackFlow
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.util.time.SystemClock
import javax.inject.Inject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow

/** Defines interface for classes that act as source of truth for power-related data. */
interface PowerRepository {
    /** Whether the device is interactive. Starts with the current state. */
    val isInteractive: Flow<Boolean>

    /** Wakes up the device. */
    fun wakeUp(why: String, @PowerManager.WakeReason wakeReason: Int)
}

@SysUISingleton
class PowerRepositoryImpl
@Inject
constructor(
    private val manager: PowerManager,
    @Application private val applicationContext: Context,
    private val systemClock: SystemClock,
    dispatcher: BroadcastDispatcher,
) : PowerRepository {

    override val isInteractive: Flow<Boolean> = conflatedCallbackFlow {
        fun send() {
            trySendWithFailureLogging(manager.isInteractive, TAG)
        }

        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    send()
                }
            }

        dispatcher.registerReceiver(
            receiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
            },
        )
        send()

        awaitClose { dispatcher.unregisterReceiver(receiver) }
    }

    override fun wakeUp(why: String, wakeReason: Int) {
        manager.wakeUp(
            systemClock.uptimeMillis(),
            wakeReason,
            "${applicationContext.packageName}:$why",
        )
    }

    companion object {
        private const val TAG = "PowerRepository"
    }
}
