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

package com.android.systemui.mediaprojection.taskswitcher.data.repository

import android.media.projection.MediaProjectionInfo
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.util.Log
import android.view.ContentRecordingSession
import android.view.ContentRecordingSession.RECORD_CONTENT_DISPLAY
import com.android.systemui.common.coroutine.ChannelExt.trySendWithFailureLogging
import com.android.systemui.common.coroutine.ConflatedCallbackFlow.conflatedCallbackFlow
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.mediaprojection.taskswitcher.data.model.MediaProjectionState
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch

@SysUISingleton
class MediaProjectionManagerRepository
@Inject
constructor(
    private val mediaProjectionManager: MediaProjectionManager,
    @Main private val handler: Handler,
    @Application private val applicationScope: CoroutineScope,
    private val tasksRepository: TasksRepository,
) : MediaProjectionRepository {

    override val mediaProjectionState: Flow<MediaProjectionState> =
        conflatedCallbackFlow {
                val callback =
                    object : MediaProjectionManager.Callback() {
                        override fun onStart(info: MediaProjectionInfo?) {
                            Log.d(TAG, "MediaProjectionManager.Callback#onStart")
                            trySendWithFailureLogging(MediaProjectionState.NotProjecting, TAG)
                        }

                        override fun onStop(info: MediaProjectionInfo?) {
                            Log.d(TAG, "MediaProjectionManager.Callback#onStop")
                            trySendWithFailureLogging(MediaProjectionState.NotProjecting, TAG)
                        }

                        override fun onRecordingSessionSet(
                            info: MediaProjectionInfo,
                            session: ContentRecordingSession?
                        ) {
                            Log.d(TAG, "MediaProjectionManager.Callback#onSessionStarted: $session")
                            launch { trySendWithFailureLogging(stateForSession(session), TAG) }
                        }
                    }
                mediaProjectionManager.addCallback(callback, handler)
                awaitClose { mediaProjectionManager.removeCallback(callback) }
            }
            .shareIn(scope = applicationScope, started = SharingStarted.Lazily, replay = 1)

    private suspend fun stateForSession(session: ContentRecordingSession?): MediaProjectionState {
        if (session == null) {
            return MediaProjectionState.NotProjecting
        }
        if (session.contentToRecord == RECORD_CONTENT_DISPLAY || session.tokenToRecord == null) {
            return MediaProjectionState.EntireScreen
        }
        val matchingTask =
            tasksRepository.findRunningTaskFromWindowContainerToken(
                checkNotNull(session.tokenToRecord)) ?: return MediaProjectionState.EntireScreen
        return MediaProjectionState.SingleTask(matchingTask)
    }

    companion object {
        private const val TAG = "MediaProjectionMngrRepo"
    }
}
