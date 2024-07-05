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

package android.ravenwood.example;

import android.annotation.SystemService;
import android.os.RemoteException;
import android.os.ServiceManager;

@SystemService(RedManager.SERVICE_NAME)
public class RedManager {
    public static final String SERVICE_NAME = "example_red";

    public String getInterfaceDescriptor() {
        try {
            return ServiceManager.getService(SERVICE_NAME).getInterfaceDescriptor();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }
}
