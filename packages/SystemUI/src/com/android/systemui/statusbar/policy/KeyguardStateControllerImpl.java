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

package com.android.systemui.statusbar.policy;

import android.annotation.NonNull;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.biometrics.BiometricSourceType;
import android.os.Build;
import android.os.Trace;

import androidx.annotation.VisibleForTesting;

import com.android.internal.widget.LockPatternUtils;
import com.android.keyguard.KeyguardUpdateMonitor;
import com.android.keyguard.KeyguardUpdateMonitorCallback;
import com.android.systemui.Dumpable;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Objects;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 */
@Singleton
public class KeyguardStateControllerImpl implements KeyguardStateController, Dumpable {

    private static final boolean DEBUG_AUTH_WITH_ADB = false;
    private static final String AUTH_BROADCAST_KEY = "debug_trigger_auth";

    private final ArrayList<Callback> mCallbacks = new ArrayList<>();
    private final KeyguardUpdateMonitor mKeyguardUpdateMonitor;
    private final LockPatternUtils mLockPatternUtils;
    private final KeyguardUpdateMonitorCallback mKeyguardUpdateMonitorCallback =
            new UpdateMonitorCallback();

    private boolean mCanDismissLockScreen;
    private boolean mShowing;
    private boolean mSecure;
    private boolean mOccluded;

    private boolean mKeyguardFadingAway;
    private long mKeyguardFadingAwayDelay;
    private long mKeyguardFadingAwayDuration;
    private boolean mKeyguardGoingAway;
    private boolean mLaunchTransitionFadingAway;
    private boolean mBypassFadingAnimation;
    private boolean mTrustManaged;
    private boolean mTrusted;
    private boolean mDebugUnlocked = false;
    private boolean mFaceAuthEnabled;

    /**
     */
    @Inject
    public KeyguardStateControllerImpl(Context context,
            KeyguardUpdateMonitor keyguardUpdateMonitor, LockPatternUtils lockPatternUtils) {
        mKeyguardUpdateMonitor = keyguardUpdateMonitor;
        mLockPatternUtils = lockPatternUtils;
        mKeyguardUpdateMonitor.registerCallback(mKeyguardUpdateMonitorCallback);

        update(true /* updateAlways */);
        if (Build.IS_DEBUGGABLE && DEBUG_AUTH_WITH_ADB) {
            // Watch for interesting updates
            final IntentFilter filter = new IntentFilter();
            filter.addAction(AUTH_BROADCAST_KEY);
            context.registerReceiver(new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (DEBUG_AUTH_WITH_ADB && AUTH_BROADCAST_KEY.equals(intent.getAction())) {
                        mDebugUnlocked = !mDebugUnlocked;
                        update(true /* updateAlways */);
                    }
                }
            }, filter, null, null);
        }
    }

    @Override
    public synchronized void addCallback(@NonNull Callback callback) {
        Objects.requireNonNull(callback, "Callback must not be null. b/128895449");
        if (!mCallbacks.contains(callback)) {
            mCallbacks.add(callback);
        }
    }

    @Override
    public synchronized void removeCallback(@NonNull Callback callback) {
        Objects.requireNonNull(callback, "Callback must not be null. b/128895449");
        mCallbacks.remove(callback);
    }

    @Override
    public boolean isShowing() {
        return mShowing;
    }

    @Override
    public boolean isMethodSecure() {
        return mSecure;
    }

    @Override
    public boolean isOccluded() {
        return mOccluded;
    }

    @Override
    public boolean isTrusted() {
        return mTrusted;
    }

    @Override
    public void notifyKeyguardState(boolean showing, boolean occluded) {
        if (mShowing == showing && mOccluded == occluded) return;
        mShowing = showing;
        mOccluded = occluded;
        notifyKeyguardChanged();
    }

    private void notifyKeyguardChanged() {
        Trace.beginSection("KeyguardStateController#notifyKeyguardChanged");
        // Copy the list to allow removal during callback.
        new ArrayList<>(mCallbacks).forEach(Callback::onKeyguardShowingChanged);
        Trace.endSection();
    }

    private void notifyUnlockedChanged() {
        Trace.beginSection("KeyguardStateController#notifyUnlockedChanged");
        // Copy the list to allow removal during callback.
        new ArrayList<>(mCallbacks).forEach(Callback::onUnlockedChanged);
        Trace.endSection();
    }

    @Override
    public void notifyKeyguardFadingAway(long delay, long fadeoutDuration, boolean isBypassFading) {
        mKeyguardFadingAwayDelay = delay;
        mKeyguardFadingAwayDuration = fadeoutDuration;
        mBypassFadingAnimation = isBypassFading;
        setKeyguardFadingAway(true);
    }

    private void setKeyguardFadingAway(boolean keyguardFadingAway) {
        if (mKeyguardFadingAway != keyguardFadingAway) {
            mKeyguardFadingAway = keyguardFadingAway;
            ArrayList<Callback> callbacks = new ArrayList<>(mCallbacks);
            for (int i = 0; i < callbacks.size(); i++) {
                callbacks.get(i).onKeyguardFadingAwayChanged();
            }
        }
    }

    @Override
    public void notifyKeyguardDoneFading() {
        mKeyguardGoingAway = false;
        setKeyguardFadingAway(false);
    }

    @VisibleForTesting
    void update(boolean updateAlways) {
        Trace.beginSection("KeyguardStateController#update");
        int user = KeyguardUpdateMonitor.getCurrentUser();
        boolean secure = mLockPatternUtils.isSecure(user);
        boolean canDismissLockScreen = !secure || mKeyguardUpdateMonitor.getUserCanSkipBouncer(user)
                || (Build.IS_DEBUGGABLE && DEBUG_AUTH_WITH_ADB && mDebugUnlocked);
        boolean trustManaged = mKeyguardUpdateMonitor.getUserTrustIsManaged(user);
        boolean trusted = mKeyguardUpdateMonitor.getUserHasTrust(user);
        boolean faceAuthEnabled = mKeyguardUpdateMonitor.isFaceAuthEnabledForUser(user);
        boolean changed = secure != mSecure || canDismissLockScreen != mCanDismissLockScreen
                || trustManaged != mTrustManaged || mTrusted != trusted
                || mFaceAuthEnabled != faceAuthEnabled;
        if (changed || updateAlways) {
            mSecure = secure;
            mCanDismissLockScreen = canDismissLockScreen;
            mTrusted = trusted;
            mTrustManaged = trustManaged;
            mFaceAuthEnabled = faceAuthEnabled;
            notifyUnlockedChanged();
        }
        Trace.endSection();
    }

    @Override
    public boolean canDismissLockScreen() {
        return mCanDismissLockScreen;
    }

    @Override
    public boolean isFaceAuthEnabled() {
        return mFaceAuthEnabled;
    }

    @Override
    public boolean isKeyguardFadingAway() {
        return mKeyguardFadingAway;
    }

    @Override
    public boolean isKeyguardGoingAway() {
        return mKeyguardGoingAway;
    }

    @Override
    public boolean isBypassFadingAnimation() {
        return mBypassFadingAnimation;
    }

    @Override
    public long getKeyguardFadingAwayDelay() {
        return mKeyguardFadingAwayDelay;
    }

    @Override
    public long getKeyguardFadingAwayDuration() {
        return mKeyguardFadingAwayDuration;
    }

    @Override
    public long calculateGoingToFullShadeDelay() {
        return mKeyguardFadingAwayDelay + mKeyguardFadingAwayDuration;
    }

    @Override
    public void notifyKeyguardGoingAway(boolean keyguardGoingAway) {
        mKeyguardGoingAway = keyguardGoingAway;
    }

    @Override
    public void setLaunchTransitionFadingAway(boolean fadingAway) {
        mLaunchTransitionFadingAway = fadingAway;
    }

    @Override
    public boolean isLaunchTransitionFadingAway() {
        return mLaunchTransitionFadingAway;
    }

    /**
     * Dumps internal state for debugging.
     * @param pw Where to dump.
     */
    @Override
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("KeyguardStateController:");
        pw.println("  mSecure: " + mSecure);
        pw.println("  mCanDismissLockScreen: " + mCanDismissLockScreen);
        pw.println("  mTrustManaged: " + mTrustManaged);
        pw.println("  mTrusted: " + mTrusted);
        pw.println("  mDebugUnlocked: " + mDebugUnlocked);
        pw.println("  mFaceAuthEnabled: " + mFaceAuthEnabled);
    }

    private class UpdateMonitorCallback extends KeyguardUpdateMonitorCallback {
        @Override
        public void onUserSwitchComplete(int userId) {
            update(false /* updateAlways */);
        }

        @Override
        public void onTrustChanged(int userId) {
            update(false /* updateAlways */);
            notifyKeyguardChanged();
        }

        @Override
        public void onTrustManagedChanged(int userId) {
            update(false /* updateAlways */);
        }

        @Override
        public void onStartedWakingUp() {
            update(false /* updateAlways */);
        }

        @Override
        public void onBiometricAuthenticated(int userId, BiometricSourceType biometricSourceType,
                boolean isStrongBiometric) {
            Trace.beginSection("KeyguardUpdateMonitorCallback#onBiometricAuthenticated");
            if (mKeyguardUpdateMonitor.isUnlockingWithBiometricAllowed(isStrongBiometric)) {
                update(false /* updateAlways */);
            }
            Trace.endSection();
        }

        @Override
        public void onFaceUnlockStateChanged(boolean running, int userId) {
            update(false /* updateAlways */);
        }

        @Override
        public void onStrongAuthStateChanged(int userId) {
            update(false /* updateAlways */);
        }

        @Override
        public void onKeyguardVisibilityChanged(boolean showing) {
            update(false /* updateAlways */);
        }

        @Override
        public void onBiometricsCleared() {
            update(false /* alwaysUpdate */);
        }
    }
}
