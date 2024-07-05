/*
 * Copyright 2023 The Android Open Source Project
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
package com.android.server.audio;

import static android.media.audio.Flags.autoPublicVolumeApiHardening;

import android.Manifest;
import android.annotation.NonNull;
import android.app.ActivityManager;
import android.app.AppOpsManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Binder;
import android.os.UserHandle;
import android.text.TextUtils;
import android.util.Slog;

import com.android.server.utils.EventLogger;

import java.io.PrintWriter;

/**
 * Class to encapsulate all audio API hardening operations
 */
public class HardeningEnforcer {

    private static final String TAG = "AS.HardeningEnforcer";
    private static final boolean DEBUG = false;
    private static final int LOG_NB_EVENTS = 20;

    final Context mContext;
    final AppOpsManager mAppOps;
    final boolean mIsAutomotive;

    final ActivityManager mActivityManager;
    final PackageManager mPackageManager;

    final EventLogger mEventLogger = new EventLogger(LOG_NB_EVENTS,
            "Hardening enforcement");

    /**
     * Matches calls from {@link AudioManager#setStreamVolume(int, int, int)}
     */
    public static final int METHOD_AUDIO_MANAGER_SET_STREAM_VOLUME = 100;
    /**
     * Matches calls from {@link AudioManager#adjustVolume(int, int)}
     */
    public static final int METHOD_AUDIO_MANAGER_ADJUST_VOLUME = 101;
    /**
     * Matches calls from {@link AudioManager#adjustSuggestedStreamVolume(int, int, int)}
     */
    public static final int METHOD_AUDIO_MANAGER_ADJUST_SUGGESTED_STREAM_VOLUME = 102;
    /**
     * Matches calls from {@link AudioManager#adjustStreamVolume(int, int, int)}
     */
    public static final int METHOD_AUDIO_MANAGER_ADJUST_STREAM_VOLUME = 103;
    /**
     * Matches calls from {@link AudioManager#setRingerMode(int)}
     */
    public static final int METHOD_AUDIO_MANAGER_SET_RINGER_MODE = 200;
    /**
     * Matches calls from {@link AudioManager#requestAudioFocus(AudioFocusRequest)}
     * and legacy variants
     */
    public static final int METHOD_AUDIO_MANAGER_REQUEST_AUDIO_FOCUS = 300;

    public HardeningEnforcer(Context ctxt, boolean isAutomotive, AppOpsManager appOps,
            PackageManager pm) {
        mContext = ctxt;
        mIsAutomotive = isAutomotive;
        mAppOps = appOps;
        mActivityManager = ctxt.getSystemService(ActivityManager.class);
        mPackageManager = pm;
    }

    protected void dump(PrintWriter pw) {
        // log
        mEventLogger.dump(pw);
    }

    /**
     * Checks whether the call in the current thread should be allowed or blocked
     * @param volumeMethod name of the method to check, for logging purposes
     * @return false if the method call is allowed, true if it should be a no-op
     */
    protected boolean blockVolumeMethod(int volumeMethod) {
        // for Auto, volume methods require MODIFY_AUDIO_SETTINGS_PRIVILEGED
        if (mIsAutomotive) {
            if (!autoPublicVolumeApiHardening()) {
                // automotive hardening flag disabled, no blocking on auto
                return false;
            }
            if (mContext.checkCallingOrSelfPermission(
                    Manifest.permission.MODIFY_AUDIO_SETTINGS_PRIVILEGED)
                    == PackageManager.PERMISSION_GRANTED) {
                return false;
            }
            if (Binder.getCallingUid() < UserHandle.AID_APP_START) {
                return false;
            }
            // TODO metrics?
            // TODO log for audio dumpsys?
            Slog.e(TAG, "Preventing volume method " + volumeMethod + " for "
                    + getPackNameForUid(Binder.getCallingUid()));
            return true;
        }
        // not blocking
        return false;
    }

    /**
     * Checks whether the call in the current thread should be allowed or blocked
     * @param focusMethod name of the method to check, for logging purposes
     * @param clientId id of the requester
     * @param durationHint focus type being requested
     * @return false if the method call is allowed, true if it should be a no-op
     */
    protected boolean blockFocusMethod(int callingUid, int focusMethod, @NonNull String clientId,
            int durationHint, @NonNull String packageName) {
        if (packageName.isEmpty()) {
            packageName = getPackNameForUid(callingUid);
        }

        if (checkAppOp(AppOpsManager.OP_TAKE_AUDIO_FOCUS, callingUid, packageName)) {
            if (DEBUG) {
                Slog.i(TAG, "blockFocusMethod pack:" + packageName + " NOT blocking");
            }
            return false;
        }

        String errorMssg = "Focus request DENIED for uid:" + callingUid
                + " clientId:" + clientId + " req:" + durationHint
                + " procState:" + mActivityManager.getUidProcessState(callingUid);

        // TODO metrics
        mEventLogger.enqueueAndSlog(errorMssg, EventLogger.Event.ALOGI, TAG);

        return true;
    }

    private String getPackNameForUid(int uid) {
        final long token = Binder.clearCallingIdentity();
        try {
            final String[] names = mPackageManager.getPackagesForUid(uid);
            if (names == null
                    || names.length == 0
                    || TextUtils.isEmpty(names[0])) {
                return "[" + uid + "]";
            }
            return names[0];
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    /**
     * Checks the given op without throwing
     * @param op the appOp code
     * @param uid the calling uid
     * @param packageName the package name of the caller
     * @return return false if the operation is not allowed
     */
    private boolean checkAppOp(int op, int uid, @NonNull String packageName) {
        if (mAppOps.checkOpNoThrow(op, uid, packageName) != AppOpsManager.MODE_ALLOWED) {
            return false;
        }
        return true;
    }
}
