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

package com.android.server.trust;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.any;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.anyBoolean;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.anyInt;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.argThat;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doAnswer;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.eq;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.mock;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.when;

import static com.google.common.truth.Truth.assertThat;

import android.Manifest;
import android.annotation.Nullable;
import android.app.ActivityManager;
import android.app.IActivityManager;
import android.app.admin.DevicePolicyManager;
import android.app.trust.ITrustListener;
import android.app.trust.ITrustManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.content.pm.UserInfo;
import android.hardware.biometrics.BiometricManager;
import android.hardware.biometrics.SensorProperties;
import android.hardware.face.FaceManager;
import android.hardware.face.FaceSensorProperties;
import android.hardware.fingerprint.FingerprintManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.UserHandle;
import android.os.UserManager;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.provider.Settings;
import android.security.Authorization;
import android.security.authorization.IKeystoreAuthorization;
import android.service.trust.TrustAgentService;
import android.testing.TestableContext;
import android.view.IWindowManager;
import android.view.WindowManagerGlobal;

import androidx.test.core.app.ApplicationProvider;

import com.android.internal.widget.LockPatternUtils;
import com.android.internal.widget.LockPatternUtils.StrongAuthTracker;
import com.android.modules.utils.testing.ExtendedMockitoRule;
import com.android.server.LocalServices;
import com.android.server.SystemService;
import com.android.server.SystemServiceManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class TrustManagerServiceTest {

    @Rule
    public final ExtendedMockitoRule mExtendedMockitoRule = new ExtendedMockitoRule.Builder(this)
            .spyStatic(ActivityManager.class)
            .spyStatic(Authorization.class)
            .mockStatic(ServiceManager.class)
            .mockStatic(WindowManagerGlobal.class)
            .build();

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Rule
    public final MockContext mMockContext = new MockContext(
            ApplicationProvider.getApplicationContext());

    private static final String URI_SCHEME_PACKAGE = "package";
    private static final int TEST_USER_ID = 50;
    private static final UserInfo TEST_USER =
            new UserInfo(TEST_USER_ID, "user", UserInfo.FLAG_FULL);
    private static final int PARENT_USER_ID = 60;
    private static final int PROFILE_USER_ID = 70;
    private static final long[] PARENT_BIOMETRIC_SIDS = new long[] { 600L, 601L };
    private static final long[] PROFILE_BIOMETRIC_SIDS = new long[] { 700L, 701L };

    private final ArrayList<ResolveInfo> mTrustAgentResolveInfoList = new ArrayList<>();
    private final ArrayList<ComponentName> mKnownTrustAgents = new ArrayList<>();
    private final ArrayList<ComponentName> mEnabledTrustAgents = new ArrayList<>();

    private @Mock ActivityManager mActivityManager;
    private @Mock BiometricManager mBiometricManager;
    private @Mock DevicePolicyManager mDevicePolicyManager;
    private @Mock FaceManager mFaceManager;
    private @Mock FingerprintManager mFingerprintManager;
    private @Mock IKeystoreAuthorization mKeystoreAuthorization;
    private @Mock LockPatternUtils mLockPatternUtils;
    private @Mock PackageManager mPackageManager;
    private @Mock UserManager mUserManager;
    private @Mock IWindowManager mWindowManager;

    private HandlerThread mHandlerThread;
    private TrustManagerService.Injector mInjector;
    private TrustManagerService mService;
    private ITrustManager mTrustManager;

    @Before
    public void setUp() throws Exception {
        when(mActivityManager.isUserRunning(TEST_USER_ID)).thenReturn(true);
        doReturn(mock(IActivityManager.class)).when(() -> ActivityManager.getService());

        when(mFaceManager.getSensorProperties()).thenReturn(List.of());
        when(mFingerprintManager.getSensorProperties()).thenReturn(List.of());

        doReturn(mKeystoreAuthorization).when(() -> Authorization.getService());

        when(mLockPatternUtils.getDevicePolicyManager()).thenReturn(mDevicePolicyManager);
        when(mLockPatternUtils.isSecure(TEST_USER_ID)).thenReturn(true);
        when(mLockPatternUtils.getKnownTrustAgents(TEST_USER_ID)).thenReturn(mKnownTrustAgents);
        when(mLockPatternUtils.getEnabledTrustAgents(TEST_USER_ID)).thenReturn(mEnabledTrustAgents);
        doAnswer(invocation -> {
            mKnownTrustAgents.clear();
            mKnownTrustAgents.addAll((Collection<ComponentName>) invocation.getArgument(0));
            return null;
        }).when(mLockPatternUtils).setKnownTrustAgents(any(), eq(TEST_USER_ID));
        doAnswer(invocation -> {
            mEnabledTrustAgents.clear();
            mEnabledTrustAgents.addAll((Collection<ComponentName>) invocation.getArgument(0));
            return null;
        }).when(mLockPatternUtils).setEnabledTrustAgents(any(), eq(TEST_USER_ID));

        ArgumentMatcher<Intent> trustAgentIntentMatcher = new ArgumentMatcher<Intent>() {
            @Override
            public boolean matches(Intent argument) {
                return TrustAgentService.SERVICE_INTERFACE.equals(argument.getAction());
            }
        };
        when(mPackageManager.queryIntentServicesAsUser(argThat(trustAgentIntentMatcher),
                anyInt(), anyInt())).thenReturn(mTrustAgentResolveInfoList);
        when(mPackageManager.checkPermission(any(), any())).thenReturn(
                PackageManager.PERMISSION_GRANTED);

        when(mUserManager.getAliveUsers()).thenReturn(List.of(TEST_USER));
        when(mUserManager.getEnabledProfileIds(TEST_USER_ID)).thenReturn(new int[0]);
        when(mUserManager.getUserInfo(TEST_USER_ID)).thenReturn(TEST_USER);

        when(mWindowManager.isKeyguardLocked()).thenReturn(true);

        mMockContext.addMockSystemService(ActivityManager.class, mActivityManager);
        mMockContext.addMockSystemService(BiometricManager.class, mBiometricManager);
        mMockContext.addMockSystemService(FaceManager.class, mFaceManager);
        mMockContext.addMockSystemService(FingerprintManager.class, mFingerprintManager);
        mMockContext.setMockPackageManager(mPackageManager);
        mMockContext.addMockSystemService(UserManager.class, mUserManager);
        doReturn(mWindowManager).when(() -> WindowManagerGlobal.getWindowManagerService());
        LocalServices.addService(SystemServiceManager.class, mock(SystemServiceManager.class));

        grantPermission(Manifest.permission.ACCESS_KEYGUARD_SECURE_STORAGE);
        grantPermission(Manifest.permission.TRUST_LISTENER);

        mHandlerThread = new HandlerThread("handler");
        mHandlerThread.start();
        mInjector = new TrustManagerService.Injector(mLockPatternUtils, mHandlerThread.getLooper());
        mService = new TrustManagerService(mMockContext, mInjector);

        // Get the ITrustManager from the new TrustManagerService.
        mService.onStart();
        ArgumentCaptor<IBinder> binderArgumentCaptor = ArgumentCaptor.forClass(IBinder.class);
        verify(() -> ServiceManager.addService(eq(Context.TRUST_SERVICE),
                    binderArgumentCaptor.capture(), anyBoolean(), anyInt()));
        mTrustManager = ITrustManager.Stub.asInterface(binderArgumentCaptor.getValue());
    }

    @After
    public void tearDown() {
        LocalServices.removeServiceForTest(SystemServiceManager.class);
        mHandlerThread.quit();
    }

    @Test
    public void firstBootCompleted_systemTrustAgentsEnabled() {
        ComponentName systemTrustAgent1 = ComponentName.unflattenFromString(
                "com.android/.SystemTrustAgent");
        ComponentName systemTrustAgent2 = ComponentName.unflattenFromString(
                "com.android/.AnotherSystemTrustAgent");
        ComponentName userTrustAgent1 = ComponentName.unflattenFromString(
                "com.user/.UserTrustAgent");
        ComponentName userTrustAgent2 = ComponentName.unflattenFromString(
                "com.user/.AnotherUserTrustAgent");
        addTrustAgent(systemTrustAgent1, /* isSystemApp= */ true);
        addTrustAgent(systemTrustAgent2, /* isSystemApp= */ true);
        addTrustAgent(userTrustAgent1, /* isSystemApp= */ false);
        addTrustAgent(userTrustAgent2, /* isSystemApp= */ false);

        bootService();

        assertThat(mEnabledTrustAgents).containsExactly(systemTrustAgent1, systemTrustAgent2);
        assertThat(mKnownTrustAgents).containsExactly(systemTrustAgent1, systemTrustAgent2,
                    userTrustAgent1, userTrustAgent2);
    }

    @Test
    public void firstBootCompleted_defaultTrustAgentEnabled() {
        ComponentName systemTrustAgent = ComponentName.unflattenFromString(
                "com.android/.SystemTrustAgent");
        ComponentName defaultTrustAgent = ComponentName.unflattenFromString(
                "com.user/.DefaultTrustAgent");
        addTrustAgent(systemTrustAgent, /* isSystemApp= */ true);
        addTrustAgent(defaultTrustAgent, /* isSystemApp= */ false);
        mMockContext.getOrCreateTestableResources().addOverride(
                com.android.internal.R.string.config_defaultTrustAgent,
                defaultTrustAgent.flattenToString());

        bootService();

        assertThat(mEnabledTrustAgents).containsExactly(defaultTrustAgent);
        assertThat(mKnownTrustAgents).containsExactly(systemTrustAgent, defaultTrustAgent);
    }

    @Test
    public void serviceBooted_knownAgentsNotSet_enabledAgentsNotUpdated() {
        ComponentName trustAgent1 = ComponentName.unflattenFromString(
                "com.android/.SystemTrustAgent");
        ComponentName trustAgent2 = ComponentName.unflattenFromString(
                "com.android/.AnotherSystemTrustAgent");
        mEnabledTrustAgents.add(trustAgent1);
        Settings.Secure.putIntForUser(mMockContext.getContentResolver(),
                Settings.Secure.TRUST_AGENTS_INITIALIZED, 1, TEST_USER_ID);
        addTrustAgent(trustAgent1, /* isSystemApp= */ true);
        addTrustAgent(trustAgent2, /* isSystemApp= */ true);

        bootService();

        assertThat(mEnabledTrustAgents).containsExactly(trustAgent1);
        assertThat(mKnownTrustAgents).containsExactly(trustAgent1, trustAgent2);
    }

    @Test
    public void serviceBooted_knownAgentsSet_enabledAgentsUpdated() {
        ComponentName trustAgent1 = ComponentName.unflattenFromString(
                "com.android/.SystemTrustAgent");
        ComponentName trustAgent2 = ComponentName.unflattenFromString(
                "com.android/.AnotherSystemTrustAgent");
        Settings.Secure.putIntForUser(mMockContext.getContentResolver(),
                Settings.Secure.TRUST_AGENTS_INITIALIZED, 1, TEST_USER_ID);
        Settings.Secure.putIntForUser(mMockContext.getContentResolver(),
                Settings.Secure.KNOWN_TRUST_AGENTS_INITIALIZED, 1, TEST_USER_ID);
        addTrustAgent(trustAgent1, /* isSystemApp= */ true);
        addTrustAgent(trustAgent2, /* isSystemApp= */ true);

        bootService();

        assertThat(mEnabledTrustAgents).containsExactly(trustAgent1, trustAgent2);
        assertThat(mKnownTrustAgents).containsExactly(trustAgent1, trustAgent2);
    }

    @Test
    public void newSystemTrustAgent_setToEnabledAndKnown() {
        bootService();
        ComponentName newAgentComponentName = ComponentName.unflattenFromString(
                "com.android/.SystemTrustAgent");
        addTrustAgent(newAgentComponentName, /* isSystemApp= */ true);

        notifyPackageChanged(newAgentComponentName);

        assertThat(mEnabledTrustAgents).containsExactly(newAgentComponentName);
        assertThat(mKnownTrustAgents).containsExactly(newAgentComponentName);
    }

    @Test
    public void newSystemTrustAgent_notEnabledWhenDefaultAgentIsSet() {
        ComponentName defaultTrustAgent = ComponentName.unflattenFromString(
                "com.user/.DefaultTrustAgent");
        addTrustAgent(defaultTrustAgent, /* isSystemApp= */ false);
        mMockContext.getOrCreateTestableResources().addOverride(
                com.android.internal.R.string.config_defaultTrustAgent,
                defaultTrustAgent.flattenToString());
        bootService();
        ComponentName newAgentComponentName = ComponentName.unflattenFromString(
                "com.android/.SystemTrustAgent");
        addTrustAgent(newAgentComponentName, /* isSystemApp= */ true);

        notifyPackageChanged(newAgentComponentName);

        assertThat(mEnabledTrustAgents).containsExactly(defaultTrustAgent);
        assertThat(mKnownTrustAgents).containsExactly(defaultTrustAgent, newAgentComponentName);
    }

    @Test
    public void newNonSystemTrustAgent_notEnabledButMarkedAsKnown() {
        bootService();
        ComponentName newAgentComponentName = ComponentName.unflattenFromString(
                "com.user/.UserTrustAgent");
        addTrustAgent(newAgentComponentName, /* isSystemApp= */ false);

        notifyPackageChanged(newAgentComponentName);

        assertThat(mEnabledTrustAgents).isEmpty();
        assertThat(mKnownTrustAgents).containsExactly(newAgentComponentName);
    }

    @Test
    public void existingTrustAgentChanged_notEnabled() {
        ComponentName systemTrustAgent1 = ComponentName.unflattenFromString(
                "com.android/.SystemTrustAgent");
        ComponentName systemTrustAgent2 = ComponentName.unflattenFromString(
                "com.android/.AnotherSystemTrustAgent");
        addTrustAgent(systemTrustAgent1, /* isSystemApp= */ true);
        addTrustAgent(systemTrustAgent2, /* isSystemApp= */ true);
        bootService();
        // Simulate user turning off systemTrustAgent2
        mLockPatternUtils.setEnabledTrustAgents(List.of(systemTrustAgent1), TEST_USER_ID);

        notifyPackageChanged(systemTrustAgent2);

        assertThat(mEnabledTrustAgents).containsExactly(systemTrustAgent1);
    }

    @Test
    public void reportEnabledTrustAgentsChangedInformsListener() throws RemoteException {
        final ITrustListener trustListener = mock(ITrustListener.class);
        mTrustManager.registerTrustListener(trustListener);
        mService.waitForIdle();
        mTrustManager.reportEnabledTrustAgentsChanged(TEST_USER_ID);
        mService.waitForIdle();
        verify(trustListener).onEnabledTrustAgentsChanged(TEST_USER_ID);
    }

    // Tests that when the device is locked for a managed profile with a *unified* challenge, the
    // device locked notification that is sent to Keystore contains the biometric SIDs of the parent
    // user, not the profile.  This matches the authentication that is needed to unlock the device
    // for the profile again.
    @Test
    @RequiresFlagsEnabled(android.security.Flags.FLAG_FIX_UNLOCKED_DEVICE_REQUIRED_KEYS_V2)
    public void testLockDeviceForManagedProfileWithUnifiedChallenge_usesParentBiometricSids()
            throws Exception {
        setupMocksForProfile(/* unifiedChallenge= */ true);

        when(mWindowManager.isKeyguardLocked()).thenReturn(false);
        mTrustManager.reportKeyguardShowingChanged();
        verify(mKeystoreAuthorization).onDeviceUnlocked(PARENT_USER_ID, null);
        verify(mKeystoreAuthorization).onDeviceUnlocked(PROFILE_USER_ID, null);

        when(mWindowManager.isKeyguardLocked()).thenReturn(true);
        mTrustManager.reportKeyguardShowingChanged();
        verify(mKeystoreAuthorization)
                .onDeviceLocked(eq(PARENT_USER_ID), eq(PARENT_BIOMETRIC_SIDS), eq(false));
        verify(mKeystoreAuthorization)
                .onDeviceLocked(eq(PROFILE_USER_ID), eq(PARENT_BIOMETRIC_SIDS), eq(false));
    }

    // Tests that when the device is locked for a managed profile with a *separate* challenge, the
    // device locked notification that is sent to Keystore contains the biometric SIDs of the
    // profile itself.  This matches the authentication that is needed to unlock the device for the
    // profile again.
    @Test
    public void testLockDeviceForManagedProfileWithSeparateChallenge_usesProfileBiometricSids()
            throws Exception {
        setupMocksForProfile(/* unifiedChallenge= */ false);

        mTrustManager.setDeviceLockedForUser(PROFILE_USER_ID, false);
        verify(mKeystoreAuthorization).onDeviceUnlocked(PROFILE_USER_ID, null);

        mTrustManager.setDeviceLockedForUser(PROFILE_USER_ID, true);
        verify(mKeystoreAuthorization)
                .onDeviceLocked(eq(PROFILE_USER_ID), eq(PROFILE_BIOMETRIC_SIDS), eq(false));
    }

    @Test
    @RequiresFlagsEnabled(android.security.Flags.FLAG_FIX_UNLOCKED_DEVICE_REQUIRED_KEYS_V2)
    public void testKeystoreWeakUnlockEnabled_whenWeakFingerprintIsSetupAndAllowed()
            throws Exception {
        setupStrongAuthTrackerToAllowEverything();
        setupFingerprint(SensorProperties.STRENGTH_WEAK);
        verifyWeakUnlockEnabled();
    }

    @Test
    @RequiresFlagsEnabled(android.security.Flags.FLAG_FIX_UNLOCKED_DEVICE_REQUIRED_KEYS_V2)
    public void testKeystoreWeakUnlockEnabled_whenWeakFaceIsSetupAndAllowed() throws Exception {
        setupStrongAuthTrackerToAllowEverything();
        setupFace(SensorProperties.STRENGTH_WEAK);
        verifyWeakUnlockEnabled();
    }

    @Test
    @RequiresFlagsEnabled(android.security.Flags.FLAG_FIX_UNLOCKED_DEVICE_REQUIRED_KEYS_V2)
    public void testKeystoreWeakUnlockEnabled_whenConvenienceFingerprintIsSetupAndAllowed()
            throws Exception {
        setupStrongAuthTrackerToAllowEverything();
        setupFingerprint(SensorProperties.STRENGTH_CONVENIENCE);
        verifyWeakUnlockEnabled();
    }

    @Test
    @RequiresFlagsEnabled(android.security.Flags.FLAG_FIX_UNLOCKED_DEVICE_REQUIRED_KEYS_V2)
    public void testKeystoreWeakUnlockEnabled_whenConvenienceFaceIsSetupAndAllowed()
            throws Exception {
        setupStrongAuthTrackerToAllowEverything();
        setupFace(SensorProperties.STRENGTH_CONVENIENCE);
        verifyWeakUnlockEnabled();
    }

    @Test
    @RequiresFlagsEnabled(android.security.Flags.FLAG_FIX_UNLOCKED_DEVICE_REQUIRED_KEYS_V2)
    public void testKeystoreWeakUnlockDisabled_whenStrongAuthRequired() throws Exception {
        setupStrongAuthTracker(StrongAuthTracker.STRONG_AUTH_REQUIRED_AFTER_USER_LOCKDOWN, true);
        setupFace(SensorProperties.STRENGTH_WEAK);
        verifyWeakUnlockDisabled();
    }

    @Test
    @RequiresFlagsEnabled(android.security.Flags.FLAG_FIX_UNLOCKED_DEVICE_REQUIRED_KEYS_V2)
    public void testKeystoreWeakUnlockDisabled_whenNonStrongBiometricNotAllowed() throws Exception {
        setupStrongAuthTracker(StrongAuthTracker.STRONG_AUTH_NOT_REQUIRED,
                /* isNonStrongBiometricAllowed= */ false);
        setupFace(SensorProperties.STRENGTH_WEAK);
        verifyWeakUnlockDisabled();
    }

    @Test
    @RequiresFlagsEnabled(android.security.Flags.FLAG_FIX_UNLOCKED_DEVICE_REQUIRED_KEYS_V2)
    public void testKeystoreWeakUnlockDisabled_whenWeakFingerprintSensorIsPresentButNotEnrolled()
            throws Exception {
        setupStrongAuthTrackerToAllowEverything();
        setupFingerprint(SensorProperties.STRENGTH_WEAK, /* enrolled= */ false);
        verifyWeakUnlockDisabled();
    }

    @Test
    @RequiresFlagsEnabled(android.security.Flags.FLAG_FIX_UNLOCKED_DEVICE_REQUIRED_KEYS_V2)
    public void testKeystoreWeakUnlockDisabled_whenWeakFaceSensorIsPresentButNotEnrolled()
            throws Exception {
        setupStrongAuthTrackerToAllowEverything();
        setupFace(SensorProperties.STRENGTH_WEAK, /* enrolled= */ false);
        verifyWeakUnlockDisabled();
    }

    @Test
    @RequiresFlagsEnabled(android.security.Flags.FLAG_FIX_UNLOCKED_DEVICE_REQUIRED_KEYS_V2)
    public void
            testKeystoreWeakUnlockDisabled_whenWeakFingerprintIsSetupButForbiddenByDevicePolicy()
            throws Exception {
        setupStrongAuthTrackerToAllowEverything();
        setupFingerprint(SensorProperties.STRENGTH_WEAK);
        when(mDevicePolicyManager.getKeyguardDisabledFeatures(null, TEST_USER_ID))
                .thenReturn(DevicePolicyManager.KEYGUARD_DISABLE_FINGERPRINT);
        verifyWeakUnlockDisabled();
    }

    @Test
    @RequiresFlagsEnabled(android.security.Flags.FLAG_FIX_UNLOCKED_DEVICE_REQUIRED_KEYS_V2)
    public void testKeystoreWeakUnlockDisabled_whenWeakFaceIsSetupButForbiddenByDevicePolicy()
            throws Exception {
        setupStrongAuthTrackerToAllowEverything();
        setupFace(SensorProperties.STRENGTH_WEAK);
        when(mDevicePolicyManager.getKeyguardDisabledFeatures(null, TEST_USER_ID))
                .thenReturn(DevicePolicyManager.KEYGUARD_DISABLE_FACE);
        verifyWeakUnlockDisabled();
    }

    @Test
    @RequiresFlagsEnabled(android.security.Flags.FLAG_FIX_UNLOCKED_DEVICE_REQUIRED_KEYS_V2)
    public void testKeystoreWeakUnlockDisabled_whenOnlyStrongFingerprintIsSetup() throws Exception {
        setupStrongAuthTrackerToAllowEverything();
        setupFingerprint(SensorProperties.STRENGTH_STRONG);
        verifyWeakUnlockDisabled();
    }

    @Test
    @RequiresFlagsEnabled(android.security.Flags.FLAG_FIX_UNLOCKED_DEVICE_REQUIRED_KEYS_V2)
    public void testKeystoreWeakUnlockDisabled_whenOnlyStrongFaceIsSetup() throws Exception {
        setupStrongAuthTrackerToAllowEverything();
        setupFace(SensorProperties.STRENGTH_STRONG);
        verifyWeakUnlockDisabled();
    }

    @Test
    @RequiresFlagsEnabled(android.security.Flags.FLAG_FIX_UNLOCKED_DEVICE_REQUIRED_KEYS_V2)
    public void testKeystoreWeakUnlockDisabled_whenNoBiometricsAreSetup() throws Exception {
        setupStrongAuthTrackerToAllowEverything();
        verifyWeakUnlockDisabled();
    }

    private void setupStrongAuthTrackerToAllowEverything() throws Exception {
        setupStrongAuthTracker(StrongAuthTracker.STRONG_AUTH_NOT_REQUIRED, true);
    }

    private void setupStrongAuthTracker(int strongAuthFlags, boolean isNonStrongBiometricAllowed)
            throws Exception {
        bootService();
        mService.onUserSwitching(null, new SystemService.TargetUser(TEST_USER));

        ArgumentCaptor<StrongAuthTracker> strongAuthTracker =
                ArgumentCaptor.forClass(StrongAuthTracker.class);
        verify(mLockPatternUtils).registerStrongAuthTracker(strongAuthTracker.capture());
        strongAuthTracker.getValue().getStub().onStrongAuthRequiredChanged(
                strongAuthFlags, TEST_USER_ID);
        strongAuthTracker.getValue().getStub().onIsNonStrongBiometricAllowedChanged(
                isNonStrongBiometricAllowed, TEST_USER_ID);
        mService.waitForIdle();
    }

    private void setupFingerprint(int strength) {
        setupFingerprint(strength, /* enrolled= */ true);
    }

    private void setupFingerprint(int strength, boolean enrolled) {
        int sensorId = 100;
        List<SensorProperties.ComponentInfo> componentInfo = List.of();
        SensorProperties sensor = new SensorProperties(sensorId, strength, componentInfo);
        when(mFingerprintManager.getSensorProperties()).thenReturn(List.of(sensor));
        when(mFingerprintManager.hasEnrolledTemplates(TEST_USER_ID)).thenReturn(enrolled);
    }

    private void setupFace(int strength) {
        setupFace(strength, /* enrolled= */ true);
    }

    private void setupFace(int strength, boolean enrolled) {
        int sensorId = 100;
        List<SensorProperties.ComponentInfo> componentInfo = List.of();
        FaceSensorProperties sensor = new FaceSensorProperties(
                sensorId, strength, componentInfo, FaceSensorProperties.TYPE_RGB);
        when(mFaceManager.getSensorProperties()).thenReturn(List.of(sensor));
        when(mFaceManager.hasEnrolledTemplates(TEST_USER_ID)).thenReturn(enrolled);
    }

    private void verifyWeakUnlockEnabled() throws Exception {
        verifyWeakUnlockValue(true);
    }

    private void verifyWeakUnlockDisabled() throws Exception {
        verifyWeakUnlockValue(false);
    }

    // Simulates a device unlock and a device lock, then verifies that the expected
    // weakUnlockEnabled flag was passed to Keystore's onDeviceLocked method.
    private void verifyWeakUnlockValue(boolean expectedWeakUnlockEnabled) throws Exception {
        when(mWindowManager.isKeyguardLocked()).thenReturn(false);
        mTrustManager.reportKeyguardShowingChanged();
        verify(mKeystoreAuthorization).onDeviceUnlocked(TEST_USER_ID, null);

        when(mWindowManager.isKeyguardLocked()).thenReturn(true);
        mTrustManager.reportKeyguardShowingChanged();
        verify(mKeystoreAuthorization).onDeviceLocked(eq(TEST_USER_ID), any(),
                eq(expectedWeakUnlockEnabled));
    }

    private void setupMocksForProfile(boolean unifiedChallenge) {
        UserInfo parent = new UserInfo(PARENT_USER_ID, "parent", UserInfo.FLAG_FULL);
        UserInfo profile = new UserInfo(PROFILE_USER_ID, "profile", UserInfo.FLAG_MANAGED_PROFILE);
        when(mUserManager.getAliveUsers()).thenReturn(List.of(parent, profile));
        when(mUserManager.getUserInfo(PARENT_USER_ID)).thenReturn(parent);
        when(mUserManager.getUserInfo(PROFILE_USER_ID)).thenReturn(profile);
        when(mUserManager.getProfileParent(PROFILE_USER_ID)).thenReturn(parent);
        when(mUserManager.getEnabledProfileIds(PARENT_USER_ID))
                .thenReturn(new int[] { PROFILE_USER_ID });

        when(mLockPatternUtils.isSecure(anyInt())).thenReturn(true);
        when(mLockPatternUtils.isProfileWithUnifiedChallenge(PROFILE_USER_ID))
                .thenReturn(unifiedChallenge);
        when(mLockPatternUtils.isManagedProfileWithUnifiedChallenge(PROFILE_USER_ID))
                .thenReturn(unifiedChallenge);
        when(mLockPatternUtils.isSeparateProfileChallengeEnabled(PROFILE_USER_ID))
                .thenReturn(!unifiedChallenge);

        when(mBiometricManager.getAuthenticatorIds(PARENT_USER_ID))
                .thenReturn(PARENT_BIOMETRIC_SIDS);
        when(mBiometricManager.getAuthenticatorIds(PROFILE_USER_ID))
                .thenReturn(PROFILE_BIOMETRIC_SIDS);

        bootService();
        mService.onUserSwitching(null, new SystemService.TargetUser(parent));
    }

    private void addTrustAgent(ComponentName agentComponentName, boolean isSystemApp) {
        ApplicationInfo applicationInfo = new ApplicationInfo();
        if (isSystemApp) {
            applicationInfo.flags = ApplicationInfo.FLAG_SYSTEM;
        }

        ServiceInfo serviceInfo = new ServiceInfo();
        serviceInfo.packageName = agentComponentName.getPackageName();
        serviceInfo.name = agentComponentName.getClassName();
        serviceInfo.applicationInfo = applicationInfo;

        ResolveInfo resolveInfo = new ResolveInfo();
        resolveInfo.serviceInfo = serviceInfo;
        mTrustAgentResolveInfoList.add(resolveInfo);
    }

    private void bootService() {
        mService.onBootPhase(SystemService.PHASE_SYSTEM_SERVICES_READY);
        mService.onBootPhase(SystemService.PHASE_THIRD_PARTY_APPS_CAN_START);
        mService.onBootPhase(SystemService.PHASE_BOOT_COMPLETED);
        mMockContext.sendUserStartedBroadcast();
    }

    private void grantPermission(String permission) {
        mMockContext.getTestablePermissions().setPermission(
                permission, PackageManager.PERMISSION_GRANTED);
    }

    private void notifyPackageChanged(ComponentName changedComponent) {
        mService.mPackageMonitor.onPackageChanged(
                changedComponent.getPackageName(),
                UserHandle.of(TEST_USER_ID).getUid(1234),
                new String[] { changedComponent.getClassName() });
    }

    /** A mock Context that allows the test process to send protected broadcasts. */
    private static final class MockContext extends TestableContext {

        private final ArrayList<BroadcastReceiver> mUserStartedBroadcastReceivers =
                new ArrayList<>();

        MockContext(Context base) {
            super(base);
        }

        @Override
        @Nullable
        public Intent registerReceiverAsUser(BroadcastReceiver receiver,
                UserHandle user, IntentFilter filter, @Nullable String broadcastPermission,
                @Nullable Handler scheduler) {

            if (filter.hasAction(Intent.ACTION_USER_STARTED)) {
                mUserStartedBroadcastReceivers.add(receiver);
            }
            return super.registerReceiverAsUser(receiver, user, filter, broadcastPermission,
                    scheduler);
        }

        @Override
        public void sendBroadcastAsUser(Intent intent, UserHandle user,
                @Nullable String receiverPermission, @Nullable Bundle options) {
        }

        void sendUserStartedBroadcast() {
            Intent intent = new Intent(Intent.ACTION_USER_STARTED)
                    .putExtra(Intent.EXTRA_USER_HANDLE, TEST_USER_ID);
            for (BroadcastReceiver receiver : mUserStartedBroadcastReceivers) {
                receiver.onReceive(this, intent);
            }
        }
    }
}
