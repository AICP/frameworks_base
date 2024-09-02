/*
 * Copyright (C) 2023 The PixelExperience Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.server.aicp;

import android.content.Context;
import com.android.server.SystemService;

import com.android.internal.util.aicp.DeviceConfigUtils;

public class CustomDeviceConfigService extends SystemService {

    private static final String TAG = "CustomDeviceConfigService";

    private final Context mContext;

    public CustomDeviceConfigService(Context context) {
        super(context);
        mContext = context;
    }

    @Override
    public void onStart() {
    }

    @Override
    public void onBootPhase(int phase) {
        if (phase == PHASE_BOOT_COMPLETED) {
            DeviceConfigUtils.setDefaultProperties(null, null);
        }
    }
}
