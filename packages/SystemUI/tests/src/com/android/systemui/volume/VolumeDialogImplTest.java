/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.systemui.volume;

import static android.media.AudioManager.RINGER_MODE_NORMAL;
import static android.media.AudioManager.RINGER_MODE_SILENT;
import static android.media.AudioManager.RINGER_MODE_VIBRATE;

import static com.android.systemui.flags.Flags.ONE_WAY_HAPTICS_API_MIGRATION;
import static com.android.systemui.volume.Events.DISMISS_REASON_UNKNOWN;
import static com.android.systemui.volume.Events.SHOW_REASON_UNKNOWN;
import static com.android.systemui.volume.VolumeDialogControllerImpl.STREAMS;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotSame;
import static junit.framework.Assert.assertTrue;

import static org.junit.Assume.assumeNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.KeyguardManager;
import android.content.res.Configuration;
import android.media.AudioManager;
import android.os.SystemClock;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;
import android.util.Log;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityManager;

import androidx.test.filters.SmallTest;

import com.android.internal.jank.InteractionJankMonitor;
import com.android.systemui.Prefs;
import com.android.systemui.R;
import com.android.systemui.SysuiTestCase;
import com.android.systemui.animation.AnimatorTestRule;
import com.android.systemui.dump.DumpManager;
import com.android.systemui.flags.FakeFeatureFlags;
import com.android.systemui.media.dialog.MediaOutputDialogFactory;
import com.android.systemui.plugins.ActivityStarter;
import com.android.systemui.plugins.VolumeDialogController;
import com.android.systemui.plugins.VolumeDialogController.State;
import com.android.systemui.statusbar.policy.AccessibilityManagerWrapper;
import com.android.systemui.statusbar.policy.ConfigurationController;
import com.android.systemui.statusbar.policy.DevicePostureController;
import com.android.systemui.statusbar.policy.DeviceProvisionedController;
import com.android.systemui.statusbar.policy.FakeConfigurationController;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.function.Predicate;

@SmallTest
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper(setAsMainLooper = true)
public class VolumeDialogImplTest extends SysuiTestCase {
    VolumeDialogImpl mDialog;
    View mActiveRinger;
    View mDrawerContainer;
    View mDrawerVibrate;
    View mDrawerMute;
    View mDrawerNormal;
    CaptionsToggleImageButton mODICaptionsIcon;

    private TestableLooper mTestableLooper;
    private ConfigurationController mConfigurationController;
    private int mOriginalOrientation;

    private static final String TAG = "VolumeDialogImplTest";

    @Mock
    VolumeDialogController mVolumeDialogController;
    @Mock
    KeyguardManager mKeyguard;
    @Mock
    AccessibilityManagerWrapper mAccessibilityMgr;
    @Mock
    DeviceProvisionedController mDeviceProvisionedController;
    @Mock
    MediaOutputDialogFactory mMediaOutputDialogFactory;
    @Mock
    VolumePanelFactory mVolumePanelFactory;
    @Mock
    ActivityStarter mActivityStarter;
    @Mock
    InteractionJankMonitor mInteractionJankMonitor;
    @Mock
    private DumpManager mDumpManager;
    @Mock CsdWarningDialog mCsdWarningDialog;
    @Mock
    DevicePostureController mPostureController;

    private final CsdWarningDialog.Factory mCsdWarningDialogFactory =
            new CsdWarningDialog.Factory() {
        @Override
        public CsdWarningDialog create(int warningType, Runnable onCleanup) {
            return mCsdWarningDialog;
        }
    };

    private FakeFeatureFlags mFeatureFlags;
    private int mLongestHideShowAnimationDuration = 250;

    @Rule
    public final AnimatorTestRule mAnimatorTestRule = new AnimatorTestRule();

    @Before
    public void setup() throws Exception {
        MockitoAnnotations.initMocks(this);

        getContext().addMockSystemService(KeyguardManager.class, mKeyguard);

        mTestableLooper = TestableLooper.get(this);
        allowTestableLooperAsMainThread();

        when(mPostureController.getDevicePosture())
                .thenReturn(DevicePostureController.DEVICE_POSTURE_CLOSED);

        int hideDialogDuration = mContext.getResources()
                .getInteger(R.integer.config_dialogHideAnimationDurationMs);
        int showDialogDuration = mContext.getResources()
                .getInteger(R.integer.config_dialogShowAnimationDurationMs);

        mLongestHideShowAnimationDuration = Math.max(hideDialogDuration, showDialogDuration);

        mOriginalOrientation = mContext.getResources().getConfiguration().orientation;

        mConfigurationController = new FakeConfigurationController();

        mFeatureFlags = new FakeFeatureFlags();

        mDialog = new VolumeDialogImpl(
                getContext(),
                mVolumeDialogController,
                mAccessibilityMgr,
                mDeviceProvisionedController,
                mConfigurationController,
                mMediaOutputDialogFactory,
                mVolumePanelFactory,
                mActivityStarter,
                mInteractionJankMonitor,
                false,
                mCsdWarningDialogFactory,
                mPostureController,
                mTestableLooper.getLooper(),
                mDumpManager,
                mFeatureFlags);
        mDialog.init(0, null);
        State state = createShellState();
        mDialog.onStateChangedH(state);

        mActiveRinger = mDialog.getDialogView().findViewById(
                R.id.volume_new_ringer_active_icon_container);
        mDrawerContainer = mDialog.getDialogView().findViewById(R.id.volume_drawer_container);
        mDrawerVibrate = mDrawerContainer.findViewById(R.id.volume_drawer_vibrate);
        mDrawerMute = mDrawerContainer.findViewById(R.id.volume_drawer_mute);
        mDrawerNormal = mDrawerContainer.findViewById(R.id.volume_drawer_normal);
        mODICaptionsIcon = mDialog.getDialogView().findViewById(R.id.odi_captions_icon);

        Prefs.putInt(mContext,
                Prefs.Key.SEEN_RINGER_GUIDANCE_COUNT,
                VolumePrefs.SHOW_RINGER_TOAST_COUNT + 1);

        Prefs.putBoolean(mContext, Prefs.Key.HAS_SEEN_ODI_CAPTIONS_TOOLTIP, false);
    }

    private State createShellState() {
        State state = new VolumeDialogController.State();
        for (int i = AudioManager.STREAM_VOICE_CALL; i <= AudioManager.STREAM_ACCESSIBILITY; i++) {
            VolumeDialogController.StreamState ss = new VolumeDialogController.StreamState();
            ss.name = STREAMS.get(i);
            ss.level = 1;
            state.states.append(i, ss);
        }
        return state;
    }

    private void navigateViews(View view, Predicate<View> condition) {
        if (view instanceof ViewGroup) {
            ViewGroup viewGroup = (ViewGroup) view;
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                navigateViews(viewGroup.getChildAt(i), condition);
            }
        } else {
            String resourceName = null;
            try {
                resourceName = getContext().getResources().getResourceName(view.getId());
            } catch (Exception e) {}
            assertTrue("View " + resourceName != null ? resourceName : view.getId()
                    + " failed test", condition.test(view));
        }
    }

    @Test
    public void testComputeTimeout() {
        Mockito.reset(mAccessibilityMgr);
        mDialog.rescheduleTimeoutH();
        verify(mAccessibilityMgr).getRecommendedTimeoutMillis(
                VolumeDialogImpl.DIALOG_TIMEOUT_MILLIS,
                AccessibilityManager.FLAG_CONTENT_CONTROLS);
    }

    @Test
    public void testComputeTimeout_tooltip() {
        Mockito.reset(mAccessibilityMgr);
        mDialog.showCaptionsTooltip();
        verify(mAccessibilityMgr).getRecommendedTimeoutMillis(
                VolumeDialogImpl.DIALOG_ODI_CAPTIONS_TOOLTIP_TIMEOUT_MILLIS,
                AccessibilityManager.FLAG_CONTENT_CONTROLS
                | AccessibilityManager.FLAG_CONTENT_TEXT);
    }

    @Test
    public void testComputeTimeout_withHovering() {
        Mockito.reset(mAccessibilityMgr);
        View dialog = mDialog.getDialogView();
        long uptimeMillis = SystemClock.uptimeMillis();
        MotionEvent event = MotionEvent.obtain(uptimeMillis, uptimeMillis,
                MotionEvent.ACTION_HOVER_ENTER, 0, 0, 0);
        event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        dialog.dispatchGenericMotionEvent(event);
        event.recycle();
        verify(mAccessibilityMgr).getRecommendedTimeoutMillis(
                VolumeDialogImpl.DIALOG_HOVERING_TIMEOUT_MILLIS,
                AccessibilityManager.FLAG_CONTENT_CONTROLS);
    }

    @Test
    public void testComputeTimeout_withSafetyWarningOn() {
        Mockito.reset(mAccessibilityMgr);
        ArgumentCaptor<VolumeDialogController.Callbacks> controllerCallbackCapture =
                ArgumentCaptor.forClass(VolumeDialogController.Callbacks.class);
        verify(mVolumeDialogController).addCallback(controllerCallbackCapture.capture(), any());
        VolumeDialogController.Callbacks callbacks = controllerCallbackCapture.getValue();

        callbacks.onShowSafetyWarning(AudioManager.FLAG_SHOW_UI);
        verify(mAccessibilityMgr).getRecommendedTimeoutMillis(
                VolumeDialogImpl.DIALOG_SAFETYWARNING_TIMEOUT_MILLIS,
                AccessibilityManager.FLAG_CONTENT_TEXT
                        | AccessibilityManager.FLAG_CONTENT_CONTROLS);
    }

    @Test
    public void testComputeTimeout_standard() {
        Mockito.reset(mAccessibilityMgr);
        mDialog.tryToRemoveCaptionsTooltip();
        mDialog.rescheduleTimeoutH();
        verify(mAccessibilityMgr).getRecommendedTimeoutMillis(
                VolumeDialogImpl.DIALOG_TIMEOUT_MILLIS,
                AccessibilityManager.FLAG_CONTENT_CONTROLS);
    }

    @Test
    public void testVibrateOnRingerChangedToVibrate() {
        mFeatureFlags.set(ONE_WAY_HAPTICS_API_MIGRATION, false);
        final State initialSilentState = new State();
        initialSilentState.ringerModeInternal = AudioManager.RINGER_MODE_SILENT;

        final State vibrateState = new State();
        vibrateState.ringerModeInternal = AudioManager.RINGER_MODE_VIBRATE;

        // change ringer to silent
        mDialog.onStateChangedH(initialSilentState);

        // expected: shouldn't call vibrate yet
        verify(mVolumeDialogController, never()).vibrate(any());

        // changed ringer to vibrate
        mDialog.onStateChangedH(vibrateState);

        // expected: vibrate device
        verify(mVolumeDialogController).vibrate(any());
    }

    @Test
    public void testControllerDoesNotVibrateOnRingerChangedToVibrate_OnewayAPI_On() {
        mFeatureFlags.set(ONE_WAY_HAPTICS_API_MIGRATION, true);
        final State initialSilentState = new State();
        initialSilentState.ringerModeInternal = AudioManager.RINGER_MODE_SILENT;

        final State vibrateState = new State();
        vibrateState.ringerModeInternal = AudioManager.RINGER_MODE_VIBRATE;

        // change ringer to silent
        mDialog.onStateChangedH(initialSilentState);

        // expected: shouldn't call vibrate yet
        verify(mVolumeDialogController, never()).vibrate(any());

        // changed ringer to vibrate
        mDialog.onStateChangedH(vibrateState);

        // expected: vibrate method of controller is not used
        verify(mVolumeDialogController, never()).vibrate(any());
    }

    @Test
    public void testNoVibrateOnRingerInitialization() {
        mFeatureFlags.set(ONE_WAY_HAPTICS_API_MIGRATION, false);
        final State initialUnsetState = new State();
        initialUnsetState.ringerModeInternal = -1;

        // ringer not initialized yet:
        mDialog.onStateChangedH(initialUnsetState);

        final State vibrateState = new State();
        vibrateState.ringerModeInternal = AudioManager.RINGER_MODE_VIBRATE;

        // changed ringer to vibrate
        mDialog.onStateChangedH(vibrateState);

        // shouldn't call vibrate
        verify(mVolumeDialogController, never()).vibrate(any());
    }

    @Test
    public void testControllerDoesNotVibrateOnRingerInitialization_OnewayAPI_On() {
        mFeatureFlags.set(ONE_WAY_HAPTICS_API_MIGRATION, true);
        final State initialUnsetState = new State();
        initialUnsetState.ringerModeInternal = -1;

        // ringer not initialized yet:
        mDialog.onStateChangedH(initialUnsetState);

        final State vibrateState = new State();
        vibrateState.ringerModeInternal = AudioManager.RINGER_MODE_VIBRATE;

        // changed ringer to vibrate
        mDialog.onStateChangedH(vibrateState);

        // shouldn't call vibrate on the controller either
        verify(mVolumeDialogController, never()).vibrate(any());
    }

    @Test
    public void testSelectVibrateFromDrawer() {
        mFeatureFlags.set(ONE_WAY_HAPTICS_API_MIGRATION, false);
        final State initialUnsetState = new State();
        initialUnsetState.ringerModeInternal = AudioManager.RINGER_MODE_NORMAL;
        mDialog.onStateChangedH(initialUnsetState);

        mActiveRinger.performClick();
        mDrawerVibrate.performClick();

        // Make sure we've actually changed the ringer mode.
        verify(mVolumeDialogController, times(1)).setRingerMode(
                AudioManager.RINGER_MODE_VIBRATE, false);
    }

    @Test
    public void testSelectVibrateFromDrawer_OnewayAPI_On() {
        mFeatureFlags.set(ONE_WAY_HAPTICS_API_MIGRATION, true);
        final State initialUnsetState = new State();
        initialUnsetState.ringerModeInternal = RINGER_MODE_NORMAL;
        mDialog.onStateChangedH(initialUnsetState);

        mActiveRinger.performClick();
        mDrawerVibrate.performClick();

        // Make sure we've actually changed the ringer mode.
        verify(mVolumeDialogController, times(1)).setRingerMode(
                AudioManager.RINGER_MODE_VIBRATE, false);
    }

    @Test
    public void testSelectMuteFromDrawer() {
        mFeatureFlags.set(ONE_WAY_HAPTICS_API_MIGRATION, false);
        final State initialUnsetState = new State();
        initialUnsetState.ringerModeInternal = AudioManager.RINGER_MODE_NORMAL;
        mDialog.onStateChangedH(initialUnsetState);

        mActiveRinger.performClick();
        mDrawerMute.performClick();

        // Make sure we've actually changed the ringer mode.
        verify(mVolumeDialogController, times(1)).setRingerMode(
                AudioManager.RINGER_MODE_SILENT, false);
    }

    @Test
    public void testSelectMuteFromDrawer_OnewayAPI_On() {
        mFeatureFlags.set(ONE_WAY_HAPTICS_API_MIGRATION, true);
        final State initialUnsetState = new State();
        initialUnsetState.ringerModeInternal = RINGER_MODE_NORMAL;
        mDialog.onStateChangedH(initialUnsetState);

        mActiveRinger.performClick();
        mDrawerMute.performClick();

        // Make sure we've actually changed the ringer mode.
        verify(mVolumeDialogController, times(1)).setRingerMode(
                AudioManager.RINGER_MODE_SILENT, false);
    }

    @Test
    public void testSelectNormalFromDrawer() {
        mFeatureFlags.set(ONE_WAY_HAPTICS_API_MIGRATION, false);
        final State initialUnsetState = new State();
        initialUnsetState.ringerModeInternal = AudioManager.RINGER_MODE_VIBRATE;
        mDialog.onStateChangedH(initialUnsetState);

        mActiveRinger.performClick();
        mDrawerNormal.performClick();

        // Make sure we've actually changed the ringer mode.
        verify(mVolumeDialogController, times(1)).setRingerMode(
                AudioManager.RINGER_MODE_NORMAL, false);
    }

    @Test
    public void testSelectNormalFromDrawer_OnewayAPI_On() {
        mFeatureFlags.set(ONE_WAY_HAPTICS_API_MIGRATION, true);
        final State initialUnsetState = new State();
        initialUnsetState.ringerModeInternal = AudioManager.RINGER_MODE_VIBRATE;
        mDialog.onStateChangedH(initialUnsetState);

        mActiveRinger.performClick();
        mDrawerNormal.performClick();

        // Make sure we've actually changed the ringer mode.
        verify(mVolumeDialogController, times(1)).setRingerMode(
                RINGER_MODE_NORMAL, false);
    }

    /**
     * Ideally we would look at the ringer ImageView and check its assigned drawable id, but that
     * API does not exist. So we do the next best thing; we check the cached icon id.
     */
    @Test
    public void notificationVolumeSeparated_theRingerIconChangesToSpeakerIcon() {
        // already separated. assert icon is new based on res id
        assertEquals(mDialog.mVolumeRingerIconDrawableId,
                R.drawable.ic_speaker_on);
        assertEquals(mDialog.mVolumeRingerMuteIconDrawableId,
                R.drawable.ic_speaker_mute);
    }

    @Test
    public void testDialogDismissAnimation_notifyVisibleIsNotCalledBeforeAnimation() {
        mDialog.dismissH(DISMISS_REASON_UNKNOWN);
        // notifyVisible(false) should not be called immediately but only after the dismiss
        // animation has ended.
        verify(mVolumeDialogController, times(0)).notifyVisible(false);
        mDialog.getDialogView().animate().cancel();
    }

    @Test
    public void showCsdWarning_dialogShown() {
        mDialog.showCsdWarningH(AudioManager.CSD_WARNING_DOSE_REACHED_1X,
                CsdWarningDialog.NO_ACTION_TIMEOUT_MS);

        verify(mCsdWarningDialog).show();
    }

    @Test
    public void ifPortraitHalfOpen_drawVerticallyTop() {
        mDialog.onPostureChanged(DevicePostureController.DEVICE_POSTURE_HALF_OPENED);
        mTestableLooper.processAllMessages(); // let dismiss() finish

        setOrientation(Configuration.ORIENTATION_PORTRAIT);

        // Call show() to trigger layout updates before verifying position
        mDialog.show(SHOW_REASON_UNKNOWN);
        mTestableLooper.processAllMessages(); // let show() finish before assessing its side-effect

        int gravity = mDialog.getWindowGravity();
        assertEquals(Gravity.TOP, gravity & Gravity.VERTICAL_GRAVITY_MASK);
    }

    @Test
    public void ifPortraitAndOpen_drawCenterVertically() {
        mDialog.onPostureChanged(DevicePostureController.DEVICE_POSTURE_OPENED);
        mTestableLooper.processAllMessages(); // let dismiss() finish

        setOrientation(Configuration.ORIENTATION_PORTRAIT);

        mDialog.show(SHOW_REASON_UNKNOWN);
        mTestableLooper.processAllMessages(); // let show() finish before assessing its side-effect

        int gravity = mDialog.getWindowGravity();
        assertEquals(Gravity.CENTER_VERTICAL, gravity & Gravity.VERTICAL_GRAVITY_MASK);
    }

    @Test
    public void ifLandscapeAndHalfOpen_drawCenterVertically() {
        mDialog.onPostureChanged(DevicePostureController.DEVICE_POSTURE_HALF_OPENED);
        mTestableLooper.processAllMessages(); // let dismiss() finish

        setOrientation(Configuration.ORIENTATION_LANDSCAPE);

        mDialog.show(SHOW_REASON_UNKNOWN);
        mTestableLooper.processAllMessages(); // let show() finish before assessing its side-effect

        int gravity = mDialog.getWindowGravity();
        assertEquals(Gravity.CENTER_VERTICAL, gravity & Gravity.VERTICAL_GRAVITY_MASK);
    }

    @Test
    public void dialogInit_addsPostureControllerCallback() {
        // init is already called in setup
        verify(mPostureController).addCallback(any());
    }

    @Test
    public void dialogDestroy_removesPostureControllerCallback() {
        verify(mPostureController, never()).removeCallback(any());
        mDialog.destroy();
        verify(mPostureController).removeCallback(any());
    }

    private void setOrientation(int orientation) {
        Configuration config = new Configuration();
        config.orientation = orientation;
        if (mConfigurationController != null) {
            mConfigurationController.onConfigurationChanged(config);
        }
    }

    private enum RingerDrawerState {INIT, OPEN, CLOSE}

    @Test
    public void ringerModeNormal_ringerContainerDescribesItsState() {
        assertRingerContainerDescribesItsState(RINGER_MODE_NORMAL, RingerDrawerState.INIT);
    }

    @Test
    public void ringerModeSilent_ringerContainerDescribesItsState() {
        assertRingerContainerDescribesItsState(RINGER_MODE_SILENT, RingerDrawerState.INIT);
    }

    @Test
    public void ringerModeVibrate_ringerContainerDescribesItsState() {
        assertRingerContainerDescribesItsState(RINGER_MODE_VIBRATE, RingerDrawerState.INIT);
    }

    @Test
    public void ringerModeNormal_openDrawer_ringerContainerDescribesItsState() {
        assertRingerContainerDescribesItsState(RINGER_MODE_NORMAL, RingerDrawerState.OPEN);
    }

    @Test
    public void ringerModeSilent_openDrawer_ringerContainerDescribesItsState() {
        assertRingerContainerDescribesItsState(RINGER_MODE_SILENT, RingerDrawerState.OPEN);
    }

    @Test
    public void ringerModeVibrate_openDrawer_ringerContainerDescribesItsState() {
        assertRingerContainerDescribesItsState(RINGER_MODE_VIBRATE, RingerDrawerState.OPEN);
    }

    @Test
    public void ringerModeNormal_closeDrawer_ringerContainerDescribesItsState() {
        assertRingerContainerDescribesItsState(RINGER_MODE_NORMAL, RingerDrawerState.CLOSE);
    }

    @Test
    public void ringerModeSilent_closeDrawer_ringerContainerDescribesItsState() {
        assertRingerContainerDescribesItsState(RINGER_MODE_SILENT, RingerDrawerState.CLOSE);
    }

    @Test
    public void ringerModeVibrate_closeDrawer_ringerContainerDescribesItsState() {
        assertRingerContainerDescribesItsState(RINGER_MODE_VIBRATE, RingerDrawerState.CLOSE);
    }

    @Test
    public void testOnCaptionEnabledStateChanged_checkBeforeSwitchTrue_setCaptionsEnabledState() {
        ArgumentCaptor<VolumeDialogController.Callbacks> controllerCallbackCapture =
                ArgumentCaptor.forClass(VolumeDialogController.Callbacks.class);
        verify(mVolumeDialogController).addCallback(controllerCallbackCapture.capture(), any());
        VolumeDialogController.Callbacks callbacks = controllerCallbackCapture.getValue();

        callbacks.onCaptionEnabledStateChanged(true, true);
        verify(mVolumeDialogController).setCaptionsEnabledState(eq(false));
    }

    @Test
    public void testOnCaptionEnabledStateChanged_checkBeforeSwitchFalse_getCaptionsEnabledTrue() {
        ArgumentCaptor<VolumeDialogController.Callbacks> controllerCallbackCapture =
                ArgumentCaptor.forClass(VolumeDialogController.Callbacks.class);
        verify(mVolumeDialogController).addCallback(controllerCallbackCapture.capture(), any());
        VolumeDialogController.Callbacks callbacks = controllerCallbackCapture.getValue();

        callbacks.onCaptionEnabledStateChanged(true, false);
        assertTrue(mODICaptionsIcon.getCaptionsEnabled());
    }

    /**
     * The content description should include ringer state, and the correct one.
     */
    private void assertRingerContainerDescribesItsState(int ringerMode,
            RingerDrawerState drawerState) {
        State state = createShellState();
        state.ringerModeInternal = ringerMode;
        mFeatureFlags.set(ONE_WAY_HAPTICS_API_MIGRATION, true);
        mDialog.onStateChangedH(state);

        mDialog.show(SHOW_REASON_UNKNOWN);

        if (drawerState != RingerDrawerState.INIT) {
            // in both cases we first open the drawer
            mDialog.toggleRingerDrawer(true);

            if (drawerState == RingerDrawerState.CLOSE) {
                mDialog.toggleRingerDrawer(false);
            }
        }

        String ringerContainerDescription = mDialog.getSelectedRingerContainerDescription();
        assumeNotNull(ringerContainerDescription);

        String ringerDescription = mContext.getString(
                mDialog.getStringDescriptionResourceForRingerMode(ringerMode));

        if (drawerState == RingerDrawerState.OPEN) {
            assertEquals(ringerDescription, ringerContainerDescription);
        } else {
            assertNotSame(ringerDescription, ringerContainerDescription);
            assertTrue(ringerContainerDescription.startsWith(ringerDescription));
        }
    }

    @After
    public void teardown() {
        // Detailed logs to track down timeout issues in b/299491332
        Log.d(TAG, "teardown: entered");
        setOrientation(mOriginalOrientation);
        Log.d(TAG, "teardown: after setOrientation");
        mAnimatorTestRule.advanceTimeBy(mLongestHideShowAnimationDuration);
        Log.d(TAG, "teardown: after advanceTimeBy");
        mTestableLooper.moveTimeForward(mLongestHideShowAnimationDuration);
        Log.d(TAG, "teardown: after moveTimeForward");
        mTestableLooper.processAllMessages();
        Log.d(TAG, "teardown: after processAllMessages");
        reset(mPostureController);
        Log.d(TAG, "teardown: after reset");
        cleanUp(mDialog);
        Log.d(TAG, "teardown: after cleanUp");
    }

    private void cleanUp(VolumeDialogImpl dialog) {
        if (dialog != null) {
            dialog.clearInternalHandlerAfterTest();
        }
    }

/*
    @Test
    public void testContentDescriptions() {
        mDialog.show(SHOW_REASON_UNKNOWN);
        ViewGroup dialog = mDialog.getDialogView();

        navigateViews(dialog, view -> {
            if (view instanceof ImageView) {
                return !TextUtils.isEmpty(view.getContentDescription());
            } else {
                return true;
            }
        });

        mDialog.dismiss(DISMISS_REASON_UNKNOWN);
    }

    @Test
    public void testNoDuplicationOfParentState() {
        mDialog.show(SHOW_REASON_UNKNOWN);
        ViewGroup dialog = mDialog.getDialogView();

        navigateViews(dialog, view -> !view.isDuplicateParentStateEnabled());

        mDialog.dismiss(DISMISS_REASON_UNKNOWN);
    }

    @Test
    public void testNoClickableViewGroups() {
        mDialog.show(SHOW_REASON_UNKNOWN);
        ViewGroup dialog = mDialog.getDialogView();

        navigateViews(dialog, view -> {
            if (view instanceof ViewGroup) {
                return !view.isClickable();
            } else {
                return true;
            }
        });

        mDialog.dismiss(DISMISS_REASON_UNKNOWN);
    }

    @Test
    public void testTristateToggle_withVibrator() {
        when(mController.hasVibrator()).thenReturn(true);

        State state = createShellState();
        state.ringerModeInternal = RINGER_MODE_NORMAL;
        mDialog.onStateChangedH(state);

        mDialog.show(SHOW_REASON_UNKNOWN);
        ViewGroup dialog = mDialog.getDialogView();

        // click once, verify updates to vibrate
        dialog.findViewById(R.id.ringer_icon).performClick();
        verify(mController, times(1)).setRingerMode(RINGER_MODE_VIBRATE, false);

        // fake the update back to the dialog with the new ringer mode
        state = createShellState();
        state.ringerModeInternal = RINGER_MODE_VIBRATE;
        mDialog.onStateChangedH(state);

        // click once, verify updates to silent
        dialog.findViewById(R.id.ringer_icon).performClick();
        verify(mController, times(1)).setRingerMode(RINGER_MODE_SILENT, false);
        verify(mController, times(1)).setStreamVolume(STREAM_RING, 0);

        // fake the update back to the dialog with the new ringer mode
        state = createShellState();
        state.states.get(STREAM_RING).level = 0;
        state.ringerModeInternal = RINGER_MODE_SILENT;
        mDialog.onStateChangedH(state);

        // click once, verify updates to normal
        dialog.findViewById(R.id.ringer_icon).performClick();
        verify(mController, times(1)).setRingerMode(RINGER_MODE_NORMAL, false);
        verify(mController, times(1)).setStreamVolume(STREAM_RING, 0);
    }

    @Test
    public void testTristateToggle_withoutVibrator() {
        when(mController.hasVibrator()).thenReturn(false);

        State state = createShellState();
        state.ringerModeInternal = RINGER_MODE_NORMAL;
        mDialog.onStateChangedH(state);

        mDialog.show(SHOW_REASON_UNKNOWN);
        ViewGroup dialog = mDialog.getDialogView();

        // click once, verify updates to silent
        dialog.findViewById(R.id.ringer_icon).performClick();
        verify(mController, times(1)).setRingerMode(RINGER_MODE_SILENT, false);
        verify(mController, times(1)).setStreamVolume(STREAM_RING, 0);

        // fake the update back to the dialog with the new ringer mode
        state = createShellState();
        state.states.get(STREAM_RING).level = 0;
        state.ringerModeInternal = RINGER_MODE_SILENT;
        mDialog.onStateChangedH(state);

        // click once, verify updates to normal
        dialog.findViewById(R.id.ringer_icon).performClick();
        verify(mController, times(1)).setRingerMode(RINGER_MODE_NORMAL, false);
        verify(mController, times(1)).setStreamVolume(STREAM_RING, 0);
    }
    */
}
