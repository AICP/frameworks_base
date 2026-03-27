/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.server.wm;

import static android.app.WindowConfiguration.ACTIVITY_TYPE_HOME;
import static android.app.WindowConfiguration.ACTIVITY_TYPE_STANDARD;
import static android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM;
import static android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN;
import static android.view.Display.DEFAULT_DISPLAY;
import static android.view.WindowManager.LayoutParams.INVALID_WINDOW_TYPE;
import static android.view.WindowManager.LayoutParams.TYPE_APPLICATION;
import static android.view.WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG;
import static android.view.WindowManager.LayoutParams.TYPE_INPUT_METHOD;
import static android.view.WindowManager.LayoutParams.TYPE_INPUT_METHOD_DIALOG;
import static android.view.WindowManager.LayoutParams.TYPE_TOAST;
import static android.window.DisplayAreaOrganizer.FEATURE_VENDOR_FIRST;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.never;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.spyOn;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.UserHandle;
import android.platform.test.annotations.Presubmit;
import android.view.IWindowSessionCallback;
import android.view.InsetsSourceControl;
import android.view.InsetsState;
import android.view.InsetsVisibilities;
import android.view.View;
import android.view.WindowManager;
import android.view.WindowManagerGlobal;

import androidx.test.filters.SmallTest;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;

/**
 * Build/Install/Run:
 *  atest WmTests:WindowManagerServiceTests
 */
@SmallTest
@Presubmit
@RunWith(WindowTestRunner.class)
public class WindowManagerServiceTests extends WindowTestsBase {
    @Rule
    public ExpectedException mExpectedException = ExpectedException.none();

    private boolean isAutomotive() {
        return getInstrumentation().getTargetContext().getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_AUTOMOTIVE);
    }

    @Test
    public void testAddWindowToken() {
        IBinder token = mock(IBinder.class);
        mWm.addWindowToken(token, TYPE_TOAST, mDisplayContent.getDisplayId(), null /* options */);

        WindowToken windowToken = mWm.mRoot.getWindowToken(token);
        assertFalse(windowToken.mRoundedCornerOverlay);
        assertFalse(windowToken.isFromClient());
    }

    @Test
    public void testTaskFocusChange_rootTaskNotHomeType_focusChanges() throws RemoteException {
        DisplayContent display = createNewDisplay();
        // Current focused window
        Task focusedRootTask = createTask(
                display, WINDOWING_MODE_FREEFORM, ACTIVITY_TYPE_STANDARD);
        Task focusedTask = createTaskInRootTask(focusedRootTask, 0 /* userId */);
        WindowState focusedWindow = createAppWindow(focusedTask, TYPE_APPLICATION, "App Window");
        mDisplayContent.mCurrentFocus = focusedWindow;
        // Tapped task
        Task tappedRootTask = createTask(
                display, WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_STANDARD);
        Task tappedTask = createTaskInRootTask(tappedRootTask, 0 /* userId */);
        spyOn(mWm.mAtmService);

        mWm.handleTaskFocusChange(tappedTask, null /* window */);

        verify(mWm.mAtmService).setFocusedTask(tappedTask.mTaskId, null);
    }

    @Test
    public void testTaskFocusChange_rootTaskHomeTypeWithSameTaskDisplayArea_focusDoesNotChange()
            throws RemoteException {
        DisplayContent display = createNewDisplay();
        // Current focused window
        Task focusedRootTask = createTask(
                display, WINDOWING_MODE_FREEFORM, ACTIVITY_TYPE_STANDARD);
        Task focusedTask = createTaskInRootTask(focusedRootTask, 0 /* userId */);
        WindowState focusedWindow = createAppWindow(focusedTask, TYPE_APPLICATION, "App Window");
        mDisplayContent.mCurrentFocus = focusedWindow;
        // Tapped home task
        Task tappedRootTask = createTask(
                display, WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_HOME);
        Task tappedTask = createTaskInRootTask(tappedRootTask, 0 /* userId */);
        spyOn(mWm.mAtmService);

        mWm.handleTaskFocusChange(tappedTask, null /* window */);

        verify(mWm.mAtmService, never()).setFocusedTask(tappedTask.mTaskId, null);
    }

    @Test
    public void testTaskFocusChange_rootTaskHomeTypeWithDifferentTaskDisplayArea_focusChanges()
            throws RemoteException {
        final DisplayContent display = createNewDisplay();
        final TaskDisplayArea secondTda = createTaskDisplayArea(
                display, mWm, "Tapped TDA", FEATURE_VENDOR_FIRST);
        // Current focused window
        Task focusedRootTask = createTask(
                display, WINDOWING_MODE_FREEFORM, ACTIVITY_TYPE_STANDARD);
        Task focusedTask = createTaskInRootTask(focusedRootTask, 0 /* userId */);
        WindowState focusedWindow = createAppWindow(focusedTask, TYPE_APPLICATION, "App Window");
        mDisplayContent.mCurrentFocus = focusedWindow;
        // Tapped home task on another task display area
        Task tappedRootTask = createTask(secondTda, WINDOWING_MODE_FULLSCREEN,
                ACTIVITY_TYPE_STANDARD);
        Task tappedTask = createTaskInRootTask(tappedRootTask, 0 /* userId */);
        spyOn(mWm.mAtmService);

        mWm.handleTaskFocusChange(tappedTask, null /* window */);

        verify(mWm.mAtmService).setFocusedTask(tappedTask.mTaskId, null);
    }

    @Test
    public void testMoveWindowTokenToDisplay_NullToken_DoNothing() {
        mWm.moveWindowTokenToDisplay(null, mDisplayContent.getDisplayId());

        verify(mDisplayContent, never()).reParentWindowToken(any());
    }

    @Test
    public void testMoveWindowTokenToDisplay_SameDisplay_DoNothing() {
        final WindowToken windowToken = createTestWindowToken(TYPE_INPUT_METHOD_DIALOG,
                mDisplayContent);

        mWm.moveWindowTokenToDisplay(windowToken.token, mDisplayContent.getDisplayId());

        verify(mDisplayContent, never()).reParentWindowToken(any());
    }

    @Test
    public void testMoveWindowTokenToDisplay_DifferentDisplay_DoMoveDisplay() {
        final WindowToken windowToken = createTestWindowToken(TYPE_INPUT_METHOD_DIALOG,
                mDisplayContent);

        mWm.moveWindowTokenToDisplay(windowToken.token, DEFAULT_DISPLAY);

        assertThat(windowToken.getDisplayContent()).isEqualTo(mDefaultDisplay);
    }

    @Test
    public void testAttachWindowContextToWindowToken_InvalidToken_EarlyReturn() {
        spyOn(mWm.mWindowContextListenerController);

        mWm.attachWindowContextToWindowToken(new Binder(), new Binder());

        verify(mWm.mWindowContextListenerController, never()).getWindowType(any());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAttachWindowContextToWindowToken_InvalidWindowType_ThrowException() {
        spyOn(mWm.mWindowContextListenerController);

        final WindowToken windowToken = createTestWindowToken(TYPE_INPUT_METHOD, mDefaultDisplay);
        doReturn(INVALID_WINDOW_TYPE).when(mWm.mWindowContextListenerController)
                .getWindowType(any());

        mWm.attachWindowContextToWindowToken(new Binder(), windowToken.token);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAttachWindowContextToWindowToken_DifferentWindowType_ThrowException() {
        spyOn(mWm.mWindowContextListenerController);

        final WindowToken windowToken = createTestWindowToken(TYPE_INPUT_METHOD, mDefaultDisplay);
        doReturn(TYPE_APPLICATION).when(mWm.mWindowContextListenerController)
                .getWindowType(any());

        mWm.attachWindowContextToWindowToken(new Binder(), windowToken.token);
    }

    @Test
    public void testAttachWindowContextToWindowToken_CallerNotValid_EarlyReturn() {
        spyOn(mWm.mWindowContextListenerController);

        final WindowToken windowToken = createTestWindowToken(TYPE_INPUT_METHOD, mDefaultDisplay);
        doReturn(TYPE_INPUT_METHOD).when(mWm.mWindowContextListenerController)
                .getWindowType(any());
        doReturn(false).when(mWm.mWindowContextListenerController)
                .assertCallerCanModifyListener(any(), anyBoolean(), anyInt());

        mWm.attachWindowContextToWindowToken(new Binder(), windowToken.token);

        verify(mWm.mWindowContextListenerController, never()).registerWindowContainerListener(
                any(), any(), anyInt(), anyInt(), any());
    }

    @Test
    public void testAttachWindowContextToWindowToken_CallerValid_DoRegister() {
        spyOn(mWm.mWindowContextListenerController);

        final WindowToken windowToken = createTestWindowToken(TYPE_INPUT_METHOD, mDefaultDisplay);
        doReturn(TYPE_INPUT_METHOD).when(mWm.mWindowContextListenerController)
                .getWindowType(any());
        doReturn(true).when(mWm.mWindowContextListenerController)
                .assertCallerCanModifyListener(any(), anyBoolean(), anyInt());

        final IBinder clientToken = new Binder();
        mWm.attachWindowContextToWindowToken(clientToken, windowToken.token);

        verify(mWm.mWindowContextListenerController).registerWindowContainerListener(
                eq(clientToken), eq(windowToken), anyInt(), eq(TYPE_INPUT_METHOD),
                eq(windowToken.mOptions));
    }

    @Test
    public void testAddWindowWithSubWindowTypeByWindowContext() {
        spyOn(mWm.mWindowContextListenerController);

        final WindowToken windowToken = createTestWindowToken(TYPE_INPUT_METHOD, mDefaultDisplay);
        final Session session = new Session(mWm, new IWindowSessionCallback.Stub() {
            @Override
            public void onAnimatorScaleChanged(float v) throws RemoteException {}
        });
        final WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                TYPE_APPLICATION_ATTACHED_DIALOG);
        params.token = windowToken.token;
        final IBinder windowContextToken = new Binder();
        params.setWindowContextToken(windowContextToken);
        doReturn(true).when(mWm.mWindowContextListenerController)
                .hasListener(eq(windowContextToken));
        doReturn(TYPE_INPUT_METHOD).when(mWm.mWindowContextListenerController)
                .getWindowType(eq(windowContextToken));

        mWm.addWindow(session, new TestIWindow(), params, View.VISIBLE, DEFAULT_DISPLAY,
                UserHandle.USER_SYSTEM, new InsetsVisibilities(), null, new InsetsState(),
                new InsetsSourceControl[0]);

        verify(mWm.mWindowContextListenerController, never()).registerWindowContainerListener(any(),
                any(), anyInt(), anyInt(), any());
    }

    @Test
    public void testRelayout_addTrustedOverlay_permissionDenied() {
        testRelayoutFlagChanges(
                false, /* firstRelayout */
                0, /* startFlags */
                0, /* startPrivateFlags */
                0, /* newFlags */
                PRIVATE_FLAG_TRUSTED_OVERLAY, /* newPrivateFlags */
                0, /* expectedChangedFlags */
                0, /* expectedChangedPrivateFlags */
                0, /* expectedFlagsValue */
                0, /* expectedPrivateFlagsValue */
                false, /* internalSystemWindowGranted */
                true /* manageActivityTasksGranted */);
    }

    @Test
    public void testRelayout_addTrustedOverlay_permissionGranted() {
        testRelayoutFlagChanges(
                false, /* firstRelayout */
                0, /* startFlags */
                0, /* startPrivateFlags */
                0, /* newFlags */
                PRIVATE_FLAG_TRUSTED_OVERLAY, /* newPrivateFlags */
                0, /* expectedChangedFlags */
                PRIVATE_FLAG_TRUSTED_OVERLAY, /* expectedChangedPrivateFlags */
                0, /* expectedFlagsValue */
                PRIVATE_FLAG_TRUSTED_OVERLAY /* expectedPrivateFlagsValue */,
                true, /* internalSystemWindowGranted */
                true /* manageActivityTasksGranted */);
    }

    @Test
    public void testRelayout_addRoundedCornersOverlay_permissionDenied() {
        testRelayoutFlagChanges(
                false, /* firstRelayout */
                0, /* startFlags */
                0, /* startPrivateFlags */
                0, /* newFlags */
                PRIVATE_FLAG_IS_ROUNDED_CORNERS_OVERLAY, /* newPrivateFlags */
                0, /* expectedChangedFlags */
                0, /* expectedChangedPrivateFlags */
                0, /* expectedFlagsValue */
                0, /* expectedPrivateFlagsValue */
                false, /* internalSystemWindowGranted */
                true /* manageActivityTasksGranted */);
    }

    @Test
    public void testRelayout_addRoundedCornersOverlay_permissionGranted() {
        testRelayoutFlagChanges(
                false, /* firstRelayout */
                0, /* startFlags */
                0, /* startPrivateFlags */
                0, /* newFlags */
                PRIVATE_FLAG_IS_ROUNDED_CORNERS_OVERLAY, /* newPrivateFlags */
                0, /* expectedChangedFlags */
                PRIVATE_FLAG_IS_ROUNDED_CORNERS_OVERLAY, /* expectedChangedPrivateFlags */
                0, /* expectedFlagsValue */
                PRIVATE_FLAG_IS_ROUNDED_CORNERS_OVERLAY /* expectedPrivateFlagsValue */,
                true, /* internalSystemWindowGranted */
                true /* manageActivityTasksGranted */);
    }

    @Test
    public void testRelayout_addInterceptGlobalDragAndDrop_permissionDenied() {
        testRelayoutFlagChanges(
                false, /* firstRelayout */
                0, /* startFlags */
                0, /* startPrivateFlags */
                0, /* newFlags */
                PRIVATE_FLAG_INTERCEPT_GLOBAL_DRAG_AND_DROP, /* newPrivateFlags */
                0, /* expectedChangedFlags */
                0, /* expectedChangedPrivateFlags */
                0, /* expectedFlagsValue */
                0, /* expectedPrivateFlagsValue */
                true, /* internalSystemWindowGranted */
                false /* manageActivityTasksGranted */);
    }

    @Test
    public void testRelayout_addInterceptGlobalDragAndDrop_permissionGranted() {
        testRelayoutFlagChanges(
                false, /* firstRelayout */
                0, /* startFlags */
                0, /* startPrivateFlags */
                0, /* newFlags */
                PRIVATE_FLAG_INTERCEPT_GLOBAL_DRAG_AND_DROP, /* newPrivateFlags */
                0, /* expectedChangedFlags */
                PRIVATE_FLAG_INTERCEPT_GLOBAL_DRAG_AND_DROP, /* expectedChangedPrivateFlags */
                0, /* expectedFlagsValue */
                PRIVATE_FLAG_INTERCEPT_GLOBAL_DRAG_AND_DROP /* expectedPrivateFlagsValue */,
                true, /* internalSystemWindowGranted */
                true /* manageActivityTasksGranted */);
    }

    private void testRelayoutFlagChanges(boolean firstRelayout, int startFlags,
            int startPrivateFlags, int newFlags, int newPrivateFlags, int expectedChangedFlags,
            int expectedChangedPrivateFlags, int expectedFlagsValue,
            int expectedPrivateFlagsValue) {
            testRelayoutFlagChanges(firstRelayout, startFlags, startPrivateFlags, newFlags,
                    newPrivateFlags, expectedChangedFlags, expectedChangedPrivateFlags,
                    expectedFlagsValue, expectedPrivateFlagsValue,
                    true /* internalSystemWindowGranted */,
                    true /* manageActivityTasksGranted */);
    }

    // Helper method to test relayout of a window, either for the initial layout, or a subsequent
    // one, and makes sure that the flags and private flags changes and final values are properly
    // reported to mDwpcHelper.keepActivityOnWindowFlagsChanged.
    private void testRelayoutFlagChanges(boolean firstRelayout, int startFlags,
            int startPrivateFlags, int newFlags, int newPrivateFlags, int expectedChangedFlags,
            int expectedChangedPrivateFlags, int expectedFlagsValue,
            int expectedPrivateFlagsValue, boolean internalSystemWindowGranted,
            boolean manageActivityTasksGranted) {
        final WindowState win = newWindowBuilder("appWin", TYPE_BASE_APPLICATION).build();
        win.mRelayoutCalled = !firstRelayout;
        mWm.mWindowMap.put(win.mClient.asBinder(), win);
        spyOn(mDisplayContent.mDwpcHelper);
        when(mDisplayContent.mDwpcHelper.hasController()).thenReturn(true);

        doReturn(internalSystemWindowGranted ? PackageManager.PERMISSION_GRANTED
                : PackageManager.PERMISSION_DENIED).when(mWm.mContext).checkPermission(
                eq(android.Manifest.permission.INTERNAL_SYSTEM_WINDOW), anyInt(), anyInt());
        doReturn(manageActivityTasksGranted ? PackageManager.PERMISSION_GRANTED
                : PackageManager.PERMISSION_DENIED).when(mWm.mContext).checkPermission(
                eq(android.Manifest.permission.MANAGE_ACTIVITY_TASKS), anyInt(), anyInt());

        win.mAttrs.flags = startFlags;
        win.mAttrs.privateFlags = startPrivateFlags;

        LayoutParams newParams = new LayoutParams();
        newParams.copyFrom(win.mAttrs);
        newParams.flags = newFlags;
        newParams.privateFlags = newPrivateFlags;

        int seq = 1;
        if (!firstRelayout) {
            win.mRelayoutSeq = 1;
            seq = 2;
        }
        mWm.relayoutWindow(win.mSession, win.mClient, newParams, 100, 200, View.VISIBLE, 0, seq,
                0, new WindowRelayoutResult(), new SurfaceControl());

        ArgumentCaptor<Integer> changedFlags = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> changedPrivateFlags = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> flagsValue = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> privateFlagsValue = ArgumentCaptor.forClass(Integer.class);

        if (!firstRelayout && expectedChangedFlags == 0 && expectedChangedPrivateFlags == 0) {
            verify(mDisplayContent.mDwpcHelper, never()).keepActivityOnWindowFlagsChanged(
                    any(ActivityInfo.class), anyInt(), anyInt(), anyInt(), anyInt());
            return;
        }

        verify(mDisplayContent.mDwpcHelper).keepActivityOnWindowFlagsChanged(
                any(ActivityInfo.class), changedFlags.capture(), changedPrivateFlags.capture(),
                flagsValue.capture(), privateFlagsValue.capture());

        assertThat(changedFlags.getValue()).isEqualTo(expectedChangedFlags);
        assertThat(changedPrivateFlags.getValue()).isEqualTo(expectedChangedPrivateFlags);
        assertThat(flagsValue.getValue()).isEqualTo(expectedFlagsValue);
        assertThat(privateFlagsValue.getValue()).isEqualTo(expectedPrivateFlagsValue);
    }

    @Test
    public void testAddToastWindow_singleWindowPerToken() {
        final IBinder token = new Binder();
        mWm.addWindowToken(token, TYPE_TOAST, DEFAULT_DISPLAY, null /* options */);

        final int uid1 = 1234;
        final int pid1 = 1234;
        final Session session1 = createTestSession(mAtm, pid1, uid1);
        final WindowManager.LayoutParams params1 = new WindowManager.LayoutParams(TYPE_TOAST);
        params1.token = token;
        params1.packageName = "test1";

        final ApplicationInfo appInfo1 = new ApplicationInfo();
        appInfo1.targetSdkVersion = android.os.Build.VERSION_CODES.O;
        doReturn(appInfo1)
                .when(mWm.mPmInternal)
                .getApplicationInfo(eq("test1"), anyLong(), anyInt(), anyInt());
        doReturn(true).when(mWm.mPmInternal).isSameApp(eq("test1"), eq(uid1), anyInt());

        final IWindow client1 = new TestIWindow();
        final int res1 =
                mWm.addWindow(
                        session1,
                        client1,
                        params1,
                        View.VISIBLE,
                        DEFAULT_DISPLAY,
                        0 /* requestUserId */,
                        WindowInsets.Type.defaultVisible(),
                        null,
                        new InsetsState(),
                        new InsetsSourceControl.Array(),
                        new Rect(),
                        new float[1]);
        assertThat(res1).isAtLeast(WindowManagerGlobal.ADD_OKAY);

        // Add second window with same token but different UID to bypass the canAddToastWindowForUid
        // check
        final int uid2 = 1235;
        final int pid2 = 1235;
        final Session session2 = createTestSession(mAtm, pid2, uid2);
        final WindowManager.LayoutParams params2 = new WindowManager.LayoutParams(TYPE_TOAST);
        params2.token = token;
        params2.packageName = "test2";

        final ApplicationInfo appInfo2 = new ApplicationInfo();
        appInfo2.targetSdkVersion = android.os.Build.VERSION_CODES.O;
        doReturn(appInfo2)
                .when(mWm.mPmInternal)
                .getApplicationInfo(eq("test2"), anyLong(), anyInt(), anyInt());
        doReturn(true).when(mWm.mPmInternal).isSameApp(eq("test2"), eq(uid2), anyInt());

        final IWindow client2 = new TestIWindow();
        final int res2 =
                mWm.addWindow(
                        session2,
                        client2,
                        params2,
                        View.VISIBLE,
                        DEFAULT_DISPLAY,
                        0 /* requestUserId */,
                        WindowInsets.Type.defaultVisible(),
                        null,
                        new InsetsState(),
                        new InsetsSourceControl.Array(),
                        new Rect(),
                        new float[1]);
        assertThat(res2).isEqualTo(WindowManagerGlobal.ADD_BAD_APP_TOKEN);
    }
}
