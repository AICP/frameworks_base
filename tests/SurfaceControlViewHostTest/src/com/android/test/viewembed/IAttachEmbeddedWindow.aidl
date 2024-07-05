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

package com.android.test.viewembed;

import android.os.IBinder;
import com.android.test.viewembed.IAttachEmbeddedWindowCallback;
import android.view.WindowManager.LayoutParams;
import android.view.SurfaceControl;
import android.window.InputTransferToken;

interface IAttachEmbeddedWindow {
    void attachEmbedded(IBinder hostToken, int width, int height, in IAttachEmbeddedWindowCallback callback);
    void relayout(in LayoutParams lp);
    oneway void attachEmbeddedSurfaceControl(in SurfaceControl parentSurfaceControl, int displayId,
            in InputTransferToken inputTransferToken);
    oneway void tearDownEmbeddedSurfaceControl();
}