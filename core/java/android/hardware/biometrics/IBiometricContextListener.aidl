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
package android.hardware.biometrics;

/**
 * A secondary communication channel from AuthController back to BiometricService for
 * events that are not associated with an autentication session. See
 * {@link IBiometricSysuiReceiver} for events associated with a session.
 *
 * @hide
 */
oneway interface IBiometricContextListener {
    // Called when doze or awake (screen on) status changes.
    // These may be called while the device is still transitioning to the new state
    // (i.e. about to become awake or enter doze)
    void onDozeChanged(boolean isDozing, boolean isAwake);
}
