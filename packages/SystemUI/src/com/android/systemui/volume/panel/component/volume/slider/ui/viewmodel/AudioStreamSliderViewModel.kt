/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.systemui.volume.panel.component.volume.slider.ui.viewmodel

import android.content.Context
import android.media.AudioManager
import com.android.settingslib.volume.domain.interactor.AudioVolumeInteractor
import com.android.settingslib.volume.shared.model.AudioStream
import com.android.settingslib.volume.shared.model.AudioStreamModel
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.res.R
import com.android.systemui.volume.panel.component.volume.domain.interactor.VolumeSliderInteractor
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Models a particular slider state. */
class AudioStreamSliderViewModel
@AssistedInject
constructor(
    @Assisted private val audioStreamWrapper: FactoryAudioStreamWrapper,
    @Assisted private val coroutineScope: CoroutineScope,
    private val context: Context,
    private val audioVolumeInteractor: AudioVolumeInteractor,
    private val volumeSliderInteractor: VolumeSliderInteractor,
) : SliderViewModel {

    private val audioStream = audioStreamWrapper.audioStream
    private val iconsByStream =
        mapOf(
            AudioStream(AudioManager.STREAM_MUSIC) to R.drawable.ic_music_note,
            AudioStream(AudioManager.STREAM_VOICE_CALL) to R.drawable.ic_call,
            AudioStream(AudioManager.STREAM_RING) to R.drawable.ic_ring_volume,
            AudioStream(AudioManager.STREAM_NOTIFICATION) to R.drawable.ic_volume_ringer,
            AudioStream(AudioManager.STREAM_ALARM) to R.drawable.ic_volume_alarm,
        )
    private val mutedIconsByStream =
        mapOf(
            AudioStream(AudioManager.STREAM_MUSIC) to R.drawable.ic_volume_off,
            AudioStream(AudioManager.STREAM_VOICE_CALL) to R.drawable.ic_volume_off,
            AudioStream(AudioManager.STREAM_RING) to R.drawable.ic_volume_off,
            AudioStream(AudioManager.STREAM_NOTIFICATION) to R.drawable.ic_volume_off,
            AudioStream(AudioManager.STREAM_ALARM) to R.drawable.ic_volume_off,
        )
    private val labelsByStream =
        mapOf(
            AudioStream(AudioManager.STREAM_MUSIC) to R.string.stream_music,
            AudioStream(AudioManager.STREAM_VOICE_CALL) to R.string.stream_voice_call,
            AudioStream(AudioManager.STREAM_RING) to R.string.stream_ring,
            AudioStream(AudioManager.STREAM_NOTIFICATION) to R.string.stream_notification,
            AudioStream(AudioManager.STREAM_ALARM) to R.string.stream_alarm,
        )
    private val disabledTextByStream =
        mapOf(
            AudioStream(AudioManager.STREAM_NOTIFICATION) to
                R.string.stream_notification_unavailable,
        )

    private var value = 0f
    override val slider: StateFlow<SliderState> =
        combine(
                audioVolumeInteractor.getAudioStream(audioStream),
                audioVolumeInteractor.canChangeVolume(audioStream),
            ) { model, isEnabled ->
                model.toState(value, isEnabled)
            }
            .stateIn(coroutineScope, SharingStarted.Eagerly, EmptyState)

    override fun onValueChangeFinished(state: SliderState, newValue: Float) {
        val audioViewModel = state as? State
        audioViewModel ?: return
        coroutineScope.launch {
            value = newValue
            val volume =
                volumeSliderInteractor.translateValueToVolume(
                    newValue,
                    audioViewModel.audioStreamModel.volumeRange
                )
            audioVolumeInteractor.setVolume(audioStream, volume)
        }
    }

    private fun AudioStreamModel.toState(value: Float, isEnabled: Boolean): State {
        return State(
            value =
                volumeSliderInteractor.processVolumeToValue(
                    volume,
                    volumeRange,
                    value,
                    isMuted,
                ),
            valueRange = volumeSliderInteractor.displayValueRange,
            icon = getIcon(this),
            label = labelsByStream[audioStream]?.let(context::getString)
                    ?: error("No label for the stream: $audioStream"),
            disabledMessage = disabledTextByStream[audioStream]?.let(context::getString),
            isEnabled = isEnabled,
            audioStreamModel = this,
        )
    }

    private fun getIcon(model: AudioStreamModel): Icon {
        val isMutedOrNoVolume = model.isMuted || model.volume == model.minVolume
        val iconRes =
            if (isMutedOrNoVolume) {
                mutedIconsByStream
            } else {
                iconsByStream
            }[audioStream]
                ?: error("No icon for the stream: $audioStream")
        return Icon.Resource(iconRes, null)
    }

    private val AudioStreamModel.volumeRange: IntRange
        get() = minVolume..maxVolume

    private data class State(
        override val value: Float,
        override val valueRange: ClosedFloatingPointRange<Float>,
        override val icon: Icon,
        override val label: String,
        override val disabledMessage: String?,
        override val isEnabled: Boolean,
        val audioStreamModel: AudioStreamModel,
    ) : SliderState

    private data object EmptyState : SliderState {
        override val value: Float = 0f
        override val valueRange: ClosedFloatingPointRange<Float> = 0f..1f
        override val icon: Icon? = null
        override val label: String = ""
        override val disabledMessage: String? = null
        override val isEnabled: Boolean = true
    }

    @AssistedFactory
    interface Factory {

        fun create(
            audioStream: FactoryAudioStreamWrapper,
            coroutineScope: CoroutineScope,
        ): AudioStreamSliderViewModel
    }

    /**
     * AudioStream is a value class that compiles into a primitive. This fail AssistedFactory build
     * when using [AudioStream] directly because it expects another type.
     */
    class FactoryAudioStreamWrapper(val audioStream: AudioStream)
}
