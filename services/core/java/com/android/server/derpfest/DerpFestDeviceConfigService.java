/*
 * Copyright (C) 2023 The PixelExperience Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.server.derpfest;

import android.content.Context;
import com.android.server.SystemService;

import com.android.internal.util.derpfest.DeviceConfigUtils;

public class DerpFestDeviceConfigService extends SystemService {

    private static final String TAG = "DerpFestDeviceConfigService";

    private final Context mContext;

    public DerpFestDeviceConfigService(Context context) {
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
