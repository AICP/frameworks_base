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

package com.android.server.inputmethod;

import static android.inputmethodservice.InputMethodService.IME_ACTIVE;
import static android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE;

import static com.android.internal.inputmethod.SoftInputShowHideReason.HIDE_SOFT_INPUT;
import static com.android.internal.inputmethod.SoftInputShowHideReason.HIDE_SWITCH_USER;
import static com.android.internal.inputmethod.SoftInputShowHideReason.SHOW_SOFT_INPUT;
import static com.android.server.inputmethod.ImeVisibilityStateComputer.STATE_HIDE_IME;
import static com.android.server.inputmethod.ImeVisibilityStateComputer.STATE_HIDE_IME_EXPLICIT;
import static com.android.server.inputmethod.ImeVisibilityStateComputer.STATE_HIDE_IME_NOT_ALWAYS;
import static com.android.server.inputmethod.ImeVisibilityStateComputer.STATE_INVALID;
import static com.android.server.inputmethod.ImeVisibilityStateComputer.STATE_SHOW_IME;
import static com.android.server.inputmethod.ImeVisibilityStateComputer.STATE_SHOW_IME_IMPLICIT;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import android.os.Binder;
import android.os.IBinder;
import android.os.RemoteException;
import android.view.Display;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.internal.inputmethod.InputBindResult;
import com.android.internal.inputmethod.StartInputFlags;
import com.android.internal.inputmethod.StartInputReason;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test the behavior of {@link DefaultImeVisibilityApplier} when performing or applying the IME
 * visibility state.
 *
 * Build/Install/Run:
 * atest FrameworksInputMethodSystemServerTests:DefaultImeVisibilityApplierTest
 */
@RunWith(AndroidJUnit4.class)
public class DefaultImeVisibilityApplierTest extends InputMethodManagerServiceTestBase {
    private DefaultImeVisibilityApplier mVisibilityApplier;

    @Before
    public void setUp() throws RemoteException {
        super.setUp();
        mVisibilityApplier =
                (DefaultImeVisibilityApplier) mInputMethodManagerService.getVisibilityApplier();
        mInputMethodManagerService.setAttachedClientForTesting(
                mock(InputMethodManagerService.ClientState.class));
    }

    @Test
    public void testPerformShowIme() throws Exception {
        synchronized (ImfLock.class) {
            mVisibilityApplier.performShowIme(new Binder() /* showInputToken */,
                    null /* statsToken */, 0 /* showFlags */, null, SHOW_SOFT_INPUT);
        }
        verifyShowSoftInput(false, true, 0 /* showFlags */);
    }

    @Test
    public void testPerformHideIme() throws Exception {
        synchronized (ImfLock.class) {
            mVisibilityApplier.performHideIme(new Binder() /* hideInputToken */,
                    null /* statsToken */, null, HIDE_SOFT_INPUT);
        }
        verifyHideSoftInput(false, true);
    }

    @Test
    public void testApplyImeVisibility_throwForInvalidState() {
        assertThrows(IllegalArgumentException.class,
                () -> mVisibilityApplier.applyImeVisibility(mWindowToken, null, STATE_INVALID));
    }

    @Test
    public void testApplyImeVisibility_showIme() {
        mVisibilityApplier.applyImeVisibility(mWindowToken, null, STATE_SHOW_IME);
        verify(mMockWindowManagerInternal).showImePostLayout(eq(mWindowToken), any());
    }

    @Test
    public void testApplyImeVisibility_hideIme() {
        mVisibilityApplier.applyImeVisibility(mWindowToken, null, STATE_HIDE_IME);
        verify(mMockWindowManagerInternal).hideIme(eq(mWindowToken), anyInt(), any());
    }

    @Test
    public void testApplyImeVisibility_hideImeExplicit() throws Exception {
        mInputMethodManagerService.mImeWindowVis = IME_ACTIVE;
        mVisibilityApplier.applyImeVisibility(mWindowToken, null, STATE_HIDE_IME_EXPLICIT);
        verifyHideSoftInput(true, true);
    }

    @Test
    public void testApplyImeVisibility_hideNotAlways() throws Exception {
        mInputMethodManagerService.mImeWindowVis = IME_ACTIVE;
        mVisibilityApplier.applyImeVisibility(mWindowToken, null, STATE_HIDE_IME_NOT_ALWAYS);
        verifyHideSoftInput(true, true);
    }

    @Test
    public void testApplyImeVisibility_showImeImplicit() throws Exception {
        mVisibilityApplier.applyImeVisibility(mWindowToken, null, STATE_SHOW_IME_IMPLICIT);
        verifyShowSoftInput(true, true, 0 /* showFlags */);
    }

    @Test
    public void testApplyImeVisibility_hideImeFromTargetOnSecondaryDisplay() {
        // Init a IME target client on the secondary display to show IME.
        mInputMethodManagerService.addClient(mMockInputMethodClient, mMockRemoteInputConnection,
                10 /* selfReportedDisplayId */);
        mInputMethodManagerService.setAttachedClientForTesting(null);
        startInputOrWindowGainedFocus(mWindowToken, SOFT_INPUT_STATE_ALWAYS_VISIBLE);

        synchronized (ImfLock.class) {
            final int displayIdToShowIme = mInputMethodManagerService.getDisplayIdToShowImeLocked();
            // Verify hideIme will apply the expected displayId when the default IME
            // visibility applier app STATE_HIDE_IME.
            mVisibilityApplier.applyImeVisibility(mWindowToken, null, STATE_HIDE_IME);
            verify(mInputMethodManagerService.mWindowManagerInternal).hideIme(
                    eq(mWindowToken), eq(displayIdToShowIme), eq(null));
        }
    }

    @Test
    public void testShowImeScreenshot() {
        synchronized (ImfLock.class) {
            mVisibilityApplier.showImeScreenshot(mWindowToken, Display.DEFAULT_DISPLAY,
                    null /* statsToken */);
        }

        verify(mMockImeTargetVisibilityPolicy).showImeScreenshot(eq(mWindowToken),
                eq(Display.DEFAULT_DISPLAY));
    }

    @Test
    public void testRemoveImeScreenshot() {
        synchronized (ImfLock.class) {
            mVisibilityApplier.removeImeScreenshot(Display.DEFAULT_DISPLAY);
        }

        verify(mMockImeTargetVisibilityPolicy).removeImeScreenshot(eq(Display.DEFAULT_DISPLAY));
    }

    @Test
    public void testApplyImeVisibility_hideImeWhenUnbinding() {
        mInputMethodManagerService.setAttachedClientForTesting(null);
        startInputOrWindowGainedFocus(mWindowToken, SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        ExtendedMockito.spyOn(mVisibilityApplier);

        synchronized (ImfLock.class) {
            // Simulate the system hides the IME when switching IME services in different users.
            // (e.g. unbinding the IME from the current user to the profile user)
            final int displayIdToShowIme = mInputMethodManagerService.getDisplayIdToShowImeLocked();
            mInputMethodManagerService.hideCurrentInputLocked(mWindowToken, null, 0, null,
                    HIDE_SWITCH_USER);
            mInputMethodManagerService.onUnbindCurrentMethodByReset();

            // Expects applyImeVisibility() -> hideIme() will be called to notify WM for syncing
            // the IME hidden state.
            verify(mVisibilityApplier).applyImeVisibility(eq(mWindowToken), any(),
                    eq(STATE_HIDE_IME));
            verify(mInputMethodManagerService.mWindowManagerInternal).hideIme(
                    eq(mWindowToken), eq(displayIdToShowIme), eq(null));
        }
    }

    private InputBindResult startInputOrWindowGainedFocus(IBinder windowToken, int softInputMode) {
        return mInputMethodManagerService.startInputOrWindowGainedFocus(
                StartInputReason.WINDOW_FOCUS_GAIN /* startInputReason */,
                mMockInputMethodClient /* client */,
                windowToken /* windowToken */,
                StartInputFlags.VIEW_HAS_FOCUS | StartInputFlags.IS_TEXT_EDITOR,
                softInputMode /* softInputMode */,
                0 /* windowFlags */,
                mEditorInfo /* editorInfo */,
                mMockRemoteInputConnection /* inputConnection */,
                mMockRemoteAccessibilityInputConnection /* remoteAccessibilityInputConnection */,
                mTargetSdkVersion /* unverifiedTargetSdkVersion */,
                mCallingUserId /* userId */,
                mMockImeOnBackInvokedDispatcher /* imeDispatcher */);
    }
}
