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

package com.android.systemui.temporarydisplay

/**
 * A superclass view state used with [TemporaryViewDisplayController].
 */
interface TemporaryViewInfo {
    /**
     * Returns the amount of time the given view state should display on the screen before it times
     * out and disappears.
     */
    fun getTimeoutMs(): Long = DEFAULT_TIMEOUT_MILLIS
}

const val DEFAULT_TIMEOUT_MILLIS = 10000L
