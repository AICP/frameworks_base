/*
 * Copyright (C) 2019 The Android Open Source Project
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

import static android.app.WindowConfiguration.ACTIVITY_TYPE_STANDARD;
import static android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM;
import static android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN;
import static android.app.WindowConfiguration.WINDOWING_MODE_UNDEFINED;
import static android.view.WindowManager.TRANSIT_OLD_TASK_CHANGE_WINDOWING_MODE;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.os.IBinder;
import android.platform.test.annotations.Presubmit;
import android.view.Display;
import android.view.IRemoteAnimationFinishedCallback;
import android.view.IRemoteAnimationRunner;
import android.view.RemoteAnimationAdapter;
import android.view.RemoteAnimationDefinition;
import android.view.RemoteAnimationTarget;
import android.view.WindowManager;

import androidx.test.filters.SmallTest;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests for change transitions
 *
 * Build/Install/Run:
 *  atest WmTests:AppChangeTransitionTests
 */
@SmallTest
@Presubmit
@RunWith(WindowTestRunner.class)
public class AppChangeTransitionTests extends WindowTestsBase {

    private Task mTask;
    private ActivityRecord mActivity;

    public void setUpOnDisplay(DisplayContent dc) {
        mActivity = createActivityRecord(dc, WINDOWING_MODE_UNDEFINED, ACTIVITY_TYPE_STANDARD);
        mTask = mActivity.getTask();

        // Set a remote animator with snapshot disabled. Snapshots don't work in wmtests.
        RemoteAnimationDefinition definition = new RemoteAnimationDefinition();
        RemoteAnimationAdapter adapter =
                new RemoteAnimationAdapter(new TestRemoteAnimationRunner(), 10, 1, false);
        definition.addRemoteAnimation(TRANSIT_OLD_TASK_CHANGE_WINDOWING_MODE, adapter);
        dc.registerRemoteAnimations(definition);
    }

    class TestRemoteAnimationRunner implements IRemoteAnimationRunner {
        @Override
        public void onAnimationStart(@WindowManager.TransitionOldType int transit,
                RemoteAnimationTarget[] apps,
                RemoteAnimationTarget[] wallpapers,
                RemoteAnimationTarget[] nonApps,
                IRemoteAnimationFinishedCallback finishedCallback) {
            for (RemoteAnimationTarget target : apps) {
                assertNotNull(target.startBounds);
            }
            try {
                finishedCallback.onAnimationFinished();
            } catch (Exception e) {
                throw new RuntimeException("Something went wrong");
            }
        }

        @Override
        public void onAnimationCancelled(boolean isKeyguardOccluded) {
        }

        @Override
        public IBinder asBinder() {
            return null;
        }
    }

    @Test
    public void testModeChangeRemoteAnimatorNoSnapshot() {
        // setup currently defaults to no snapshot.
        setUpOnDisplay(mDisplayContent);

        mTask.setWindowingMode(WINDOWING_MODE_FREEFORM);
        assertEquals(1, mDisplayContent.mChangingContainers.size());

        // Verify we are in a change transition, but without a snapshot.
        // Though, the test will actually have crashed by now if a snapshot is attempted.
        assertNull(mTask.mSurfaceFreezer.mSnapshot);
        assertTrue(mTask.isInChangeTransition());

        waitUntilHandlersIdle();
        mActivity.removeImmediately();
    }

    @Test
    public void testCancelPendingChangeOnRemove() {
        // setup currently defaults to no snapshot.
        setUpOnDisplay(mDisplayContent);

        mTask.setWindowingMode(WINDOWING_MODE_FREEFORM);
        assertEquals(1, mDisplayContent.mChangingContainers.size());
        assertTrue(mTask.isInChangeTransition());

        // Removing the app-token from the display should clean-up the
        // the change leash.
        mDisplayContent.removeAppToken(mActivity.token);
        assertEquals(0, mDisplayContent.mChangingContainers.size());
        assertFalse(mTask.isInChangeTransition());

        waitUntilHandlersIdle();
        mActivity.removeImmediately();
    }

    @Test
    public void testNoChangeOnOldDisplayWhenMoveDisplay() {
        mDisplayContent.getDefaultTaskDisplayArea().setWindowingMode(WINDOWING_MODE_FULLSCREEN);
        final DisplayContent dc1 = createNewDisplay(Display.STATE_ON);
        dc1.getDefaultTaskDisplayArea().setWindowingMode(WINDOWING_MODE_FREEFORM);
        setUpOnDisplay(dc1);

        assertEquals(WINDOWING_MODE_FREEFORM, mTask.getWindowingMode());

        // Reparenting to a display with different windowing mode may trigger
        // a change transition internally, but it should be cleaned-up once
        // the display change is complete.
        mTask.reparent(mDisplayContent.getDefaultTaskDisplayArea(), true);

        assertEquals(WINDOWING_MODE_FULLSCREEN, mTask.getWindowingMode());

        // Make sure the change transition is not the old display
        assertFalse(dc1.mChangingContainers.contains(mTask));

        waitUntilHandlersIdle();
        mActivity.removeImmediately();
    }

    @Test
    public void testCancelPendingChangeOnHide() {
        // setup currently defaults to no snapshot.
        setUpOnDisplay(mDisplayContent);

        mTask.setWindowingMode(WINDOWING_MODE_FREEFORM);
        assertEquals(1, mDisplayContent.mChangingContainers.size());
        assertTrue(mTask.isInChangeTransition());

        // Changing visibility should cancel the change transition and become closing
        mActivity.setVisibility(false, false);
        assertEquals(0, mDisplayContent.mChangingContainers.size());
        assertFalse(mTask.isInChangeTransition());

        waitUntilHandlersIdle();
        mActivity.removeImmediately();
    }
}
