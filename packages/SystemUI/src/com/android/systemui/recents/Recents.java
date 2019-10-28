/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.systemui.recents;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.provider.Settings;

import com.android.systemui.Dependency;
import com.android.systemui.SystemUI;
import com.android.systemui.statusbar.CommandQueue;
import com.android.systemui.slimrecent.RecentController;
import com.android.systemui.tuner.TunerService;

import java.io.FileDescriptor;
import java.io.PrintWriter;

/**
 * A proxy to a Recents implementation.
 */
public class Recents extends SystemUI implements CommandQueue.Callbacks, TunerService.Tunable {

    private static final String USE_SLIM_RECENTS = "system:" + Settings.System.USE_SLIM_RECENTS;

    private RecentsImplementation mImpl;
    private final CommandQueue mCommandQueue;

    private RecentsImplementation mDefaultImpl;
    private RecentController mSlimImpl;

    private boolean mStarted = false;
    private boolean mBootCompleted = false;

    private boolean mUseSlimRecents = false;

    public Recents(Context context, RecentsImplementation impl, CommandQueue commandQueue) {
        super(context);
        mDefaultImpl = impl;
        mCommandQueue = commandQueue;
        mImpl = getCurrentImpl();
    }

    @Override
    public void start() {
        mStarted = true;
        mCommandQueue.addCallback(this);
        mImpl.onStart(mContext);
        Dependency.get(TunerService.class).addTunable(this, USE_SLIM_RECENTS);
    }

    @Override
    public void onBootCompleted() {
        mBootCompleted = true;
        mImpl.onBootCompleted();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        mImpl.onConfigurationChanged(newConfig);
    }

    @Override
    public void appTransitionFinished(int displayId) {
        if (mContext.getDisplayId() == displayId) {
            mImpl.onAppTransitionFinished();
        }
    }

    public void growRecents() {
        mImpl.growRecents();
    }

    @Override
    public void showRecentApps(boolean triggeredFromAltTab) {
        // Ensure the device has been provisioned before allowing the user to interact with
        // recents
        if (!isUserSetup()) {
            return;
        }

        mImpl.showRecentApps(triggeredFromAltTab);
    }

    @Override
    public void hideRecentApps(boolean triggeredFromAltTab, boolean triggeredFromHomeKey) {
        // Ensure the device has been provisioned before allowing the user to interact with
        // recents
        if (!isUserSetup()) {
            return;
        }

        mImpl.hideRecentApps(triggeredFromAltTab, triggeredFromHomeKey);
    }

    @Override
    public void toggleRecentApps() {
        // Ensure the device has been provisioned before allowing the user to interact with
        // recents
        if (!isUserSetup()) {
            return;
        }

        mImpl.toggleRecentApps();
    }

    @Override
    public void preloadRecentApps() {
        // Ensure the device has been provisioned before allowing the user to interact with
        // recents
        if (!isUserSetup()) {
            return;
        }

        mImpl.preloadRecentApps();
    }

    @Override
    public void cancelPreloadRecentApps() {
        // Ensure the device has been provisioned before allowing the user to interact with
        // recents
        if (!isUserSetup()) {
            return;
        }

        mImpl.cancelPreloadRecentApps();
    }

    public boolean splitPrimaryTask(int stackCreateMode, Rect initialBounds,
            int metricsDockAction) {
        // Ensure the device has been provisioned before allowing the user to interact with
        // recents
        if (!isUserSetup()) {
            return false;
        }

        return mImpl.splitPrimaryTask(stackCreateMode, initialBounds, metricsDockAction);
    }

    /**
     * @return whether this device is provisioned and the current user is set up.
     */
    private boolean isUserSetup() {
        ContentResolver cr = mContext.getContentResolver();
        return (Settings.Global.getInt(cr, Settings.Global.DEVICE_PROVISIONED, 0) != 0) &&
                (Settings.Secure.getInt(cr, Settings.Secure.USER_SETUP_COMPLETE, 0) != 0);
    }

    @Override
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        mImpl.dump(pw);
    }

    private RecentsImplementation getCurrentImpl() {
        RecentsImplementation newImpl;
        boolean newCreated = false;
        if (mUseSlimRecents) {
            if (mSlimImpl == null) {
                newCreated = true;
                mSlimImpl = new RecentController();
            }
            newImpl = mSlimImpl;
        } else {
            newImpl = mDefaultImpl;
        }
        if (newCreated) {
            if (mStarted) {
                newImpl.onStart(mContext);
            }
            if (mBootCompleted) {
                newImpl.onBootCompleted();
            }
        }
        return newImpl;
    }

    @Override
    public void onTuningChanged(String key, String newValue) {
        switch (key) {
            case USE_SLIM_RECENTS:
                mUseSlimRecents = "1".equals(newValue);
                mImpl = getCurrentImpl();
                break;
         }
     }
}
