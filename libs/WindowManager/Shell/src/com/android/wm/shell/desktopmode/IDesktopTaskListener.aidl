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

package com.android.wm.shell.desktopmode;

/**
 * Allows external processes to register a listener in WMShell to get updates about desktop task
 * state.
 */
interface IDesktopTaskListener {

    /** Desktop task visibility has change. Visible if at least 1 task is visible. */
    oneway void onVisibilityChanged(int displayId, boolean visible);

    /** Desktop task stashed status has changed. */
    oneway void onStashedChanged(int displayId, boolean stashed);
}