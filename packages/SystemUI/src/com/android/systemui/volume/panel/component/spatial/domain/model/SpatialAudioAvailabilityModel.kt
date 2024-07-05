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

package com.android.systemui.volume.panel.component.spatial.domain.model

/** Models spatial audio and head tracking availability. */
interface SpatialAudioAvailabilityModel {

    /** Spatial audio is unavailable. */
    data object Unavailable : SpatialAudioAvailabilityModel

    /** Spatial audio is available. */
    interface SpatialAudio : SpatialAudioAvailabilityModel {
        companion object : SpatialAudio
    }

    /** Head tracking is available. This also means that [SpatialAudio] is available. */
    data object HeadTracking : SpatialAudio
}
