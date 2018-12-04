/*
 * Copyright (C) 2015 The CyanogenMod Project
 * Copyright (C) 2018 AICP
 * This code has been modified.  Portions copyright (C) 2010, T-Mobile USA, Inc.
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

package com.android.internal.util.aicp.content;
import android.Manifest;

/**
 * AICP specific intent definition class.
 */
public class Intent {

    /**
     * Broadcast action: lid state changed
     * @hide
     */
    public static final String ACTION_LID_STATE_CHANGED =
            "aicp.intent.action.LID_STATE_CHANGED";

    /**
     * This field is part of the intent {@link #ACTION_LID_STATE_CHANGED}.
     * Intent extra field for the state of lid/cover
     * @hide
     */
    public static final String EXTRA_LID_STATE =
            "aicp.intent.extra.LID_STATE";

}
