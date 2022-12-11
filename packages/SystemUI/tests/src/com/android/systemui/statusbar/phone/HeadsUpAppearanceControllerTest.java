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
 * limitations under the License
 */

package com.android.systemui.statusbar.phone;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;
import android.testing.TestableLooper.RunWithLooper;
import android.view.View;
import android.widget.TextView;

import androidx.test.filters.SmallTest;

import com.android.systemui.SysuiTestCase;
import com.android.systemui.plugins.DarkIconDispatcher;
import com.android.systemui.plugins.statusbar.StatusBarStateController;
import com.android.systemui.shade.NotificationPanelViewController;
import com.android.systemui.statusbar.CommandQueue;
import com.android.systemui.statusbar.HeadsUpStatusBarView;
import com.android.systemui.statusbar.notification.NotificationWakeUpCoordinator;
import com.android.systemui.statusbar.notification.row.ExpandableNotificationRow;
import com.android.systemui.statusbar.notification.row.NotificationTestHelper;
import com.android.systemui.statusbar.notification.stack.NotificationStackScrollLayoutController;
import com.android.systemui.statusbar.policy.Clock;
import com.android.systemui.statusbar.policy.KeyguardStateController;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Optional;

@SmallTest
@RunWith(AndroidTestingRunner.class)
@RunWithLooper
public class HeadsUpAppearanceControllerTest extends SysuiTestCase {

    private final NotificationStackScrollLayoutController mStackScrollerController =
            mock(NotificationStackScrollLayoutController.class);
    private final NotificationPanelViewController mPanelView =
            mock(NotificationPanelViewController.class);
    private final DarkIconDispatcher mDarkIconDispatcher = mock(DarkIconDispatcher.class);
    private HeadsUpAppearanceController mHeadsUpAppearanceController;
    private ExpandableNotificationRow mFirst;
    private HeadsUpStatusBarView mHeadsUpStatusBarView;
    private HeadsUpManagerPhone mHeadsUpManager;
    private View mOperatorNameView;
    private StatusBarStateController mStatusbarStateController;
    private KeyguardBypassController mBypassController;
    private NotificationWakeUpCoordinator mWakeUpCoordinator;
    private KeyguardStateController mKeyguardStateController;
    private CommandQueue mCommandQueue;

    @Before
    public void setUp() throws Exception {
        allowTestableLooperAsMainThread();
        NotificationTestHelper testHelper = new NotificationTestHelper(
                mContext,
                mDependency,
                TestableLooper.get(this));
        mFirst = testHelper.createRow();
        mHeadsUpStatusBarView = new HeadsUpStatusBarView(mContext, mock(View.class),
                mock(TextView.class));
        mHeadsUpManager = mock(HeadsUpManagerPhone.class);
        mOperatorNameView = new View(mContext);
        mStatusbarStateController = mock(StatusBarStateController.class);
        mBypassController = mock(KeyguardBypassController.class);
        mWakeUpCoordinator = mock(NotificationWakeUpCoordinator.class);
        mKeyguardStateController = mock(KeyguardStateController.class);
        mCommandQueue = mock(CommandQueue.class);
        mHeadsUpAppearanceController = new HeadsUpAppearanceController(
                mock(NotificationIconAreaController.class),
                mHeadsUpManager,
                mStatusbarStateController,
                mBypassController,
                mWakeUpCoordinator,
                mDarkIconDispatcher,
                mKeyguardStateController,
                mCommandQueue,
                mStackScrollerController,
                mPanelView,
                mHeadsUpStatusBarView,
                new Clock(mContext, null),
                Optional.of(mOperatorNameView),
                new View(mContext));
        mHeadsUpAppearanceController.setAppearFraction(0.0f, 0.0f);
    }

    @Test
    public void testShowinEntryUpdated() {
        mFirst.setPinned(true);
        when(mHeadsUpManager.hasPinnedHeadsUp()).thenReturn(true);
        when(mHeadsUpManager.getTopEntry()).thenReturn(mFirst.getEntry());
        mHeadsUpAppearanceController.onHeadsUpPinned(mFirst.getEntry());
        Assert.assertEquals(mFirst.getEntry(), mHeadsUpStatusBarView.getShowingEntry());

        mFirst.setPinned(false);
        when(mHeadsUpManager.hasPinnedHeadsUp()).thenReturn(false);
        mHeadsUpAppearanceController.onHeadsUpUnPinned(mFirst.getEntry());
        Assert.assertEquals(null, mHeadsUpStatusBarView.getShowingEntry());
    }

    @Test
    public void testShownUpdated() {
        mFirst.setPinned(true);
        when(mHeadsUpManager.hasPinnedHeadsUp()).thenReturn(true);
        when(mHeadsUpManager.getTopEntry()).thenReturn(mFirst.getEntry());
        mHeadsUpAppearanceController.onHeadsUpPinned(mFirst.getEntry());
        Assert.assertTrue(mHeadsUpAppearanceController.isShown());

        mFirst.setPinned(false);
        when(mHeadsUpManager.hasPinnedHeadsUp()).thenReturn(false);
        mHeadsUpAppearanceController.onHeadsUpUnPinned(mFirst.getEntry());
        Assert.assertFalse(mHeadsUpAppearanceController.isShown());
    }

    @Test
    public void testHeaderUpdated() {
        mFirst.setPinned(true);
        when(mHeadsUpManager.hasPinnedHeadsUp()).thenReturn(true);
        when(mHeadsUpManager.getTopEntry()).thenReturn(mFirst.getEntry());
        mHeadsUpAppearanceController.onHeadsUpPinned(mFirst.getEntry());
        Assert.assertEquals(mFirst.getHeaderVisibleAmount(), 0.0f, 0.0f);

        mFirst.setPinned(false);
        when(mHeadsUpManager.hasPinnedHeadsUp()).thenReturn(false);
        mHeadsUpAppearanceController.onHeadsUpUnPinned(mFirst.getEntry());
        Assert.assertEquals(mFirst.getHeaderVisibleAmount(), 1.0f, 0.0f);
    }

    @Test
    public void testOperatorNameViewUpdated() {
        mHeadsUpAppearanceController.setAnimationsEnabled(false);

        mFirst.setPinned(true);
        when(mHeadsUpManager.hasPinnedHeadsUp()).thenReturn(true);
        when(mHeadsUpManager.getTopEntry()).thenReturn(mFirst.getEntry());
        mHeadsUpAppearanceController.onHeadsUpPinned(mFirst.getEntry());
        Assert.assertEquals(View.INVISIBLE, mOperatorNameView.getVisibility());

        mFirst.setPinned(false);
        when(mHeadsUpManager.hasPinnedHeadsUp()).thenReturn(false);
        mHeadsUpAppearanceController.onHeadsUpUnPinned(mFirst.getEntry());
        Assert.assertEquals(View.VISIBLE, mOperatorNameView.getVisibility());
    }

    @Test
    public void constructor_animationValuesUpdated() {
        float appearFraction = .75f;
        float expandedHeight = 400f;
        when(mStackScrollerController.getAppearFraction()).thenReturn(appearFraction);
        when(mStackScrollerController.getExpandedHeight()).thenReturn(expandedHeight);

        HeadsUpAppearanceController newController = new HeadsUpAppearanceController(
                mock(NotificationIconAreaController.class),
                mHeadsUpManager,
                mStatusbarStateController,
                mBypassController,
                mWakeUpCoordinator,
                mDarkIconDispatcher,
                mKeyguardStateController,
                mCommandQueue,
                mStackScrollerController,
                mPanelView,
                mHeadsUpStatusBarView,
                new Clock(mContext, null),
                Optional.empty(),
                new View(mContext));

        Assert.assertEquals(expandedHeight, newController.mExpandedHeight, 0.0f);
        Assert.assertEquals(appearFraction, newController.mAppearFraction, 0.0f);
    }

    @Test
    public void testDestroy() {
        reset(mHeadsUpManager);
        reset(mDarkIconDispatcher);
        reset(mPanelView);
        reset(mStackScrollerController);

        mHeadsUpAppearanceController.onViewDetached();

        verify(mHeadsUpManager).removeListener(any());
        verify(mDarkIconDispatcher).removeDarkReceiver((DarkIconDispatcher.DarkReceiver) any());
        verify(mPanelView).removeTrackingHeadsUpListener(any());
        verify(mPanelView).setHeadsUpAppearanceController(isNull());
        verify(mStackScrollerController).removeOnExpandedHeightChangedListener(any());
    }
}
