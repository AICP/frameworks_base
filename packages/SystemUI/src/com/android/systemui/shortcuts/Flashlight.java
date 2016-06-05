/*
 * Copyright 2013 SlimRom
 * Copyright 2015 AICP
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

package com.android.systemui.shortcuts;

import android.app.Activity;
import android.content.Context;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraAccessException;
import android.os.Bundle;

import com.android.internal.util.aicp.Helpers;

public class Flashlight extends Activity {

    private static boolean mTorchEnabled = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onResume() {
        super.onResume();
        toggleTorch();
    }

    public void toggleTorch(){
        try {
            CameraManager cameraManager = (CameraManager)
                    this.getSystemService(Context.CAMERA_SERVICE);
            for (final String cameraId : cameraManager.getCameraIdList()) {
                CameraCharacteristics characteristics =
                    cameraManager.getCameraCharacteristics(cameraId);
                int orient = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (orient == CameraCharacteristics.LENS_FACING_BACK) {
                    cameraManager.setTorchMode(cameraId, !mTorchEnabled);
                    mTorchEnabled = !mTorchEnabled;
                }
            }
        } catch (CameraAccessException e) {
        }
        this.finish();
    }
}
