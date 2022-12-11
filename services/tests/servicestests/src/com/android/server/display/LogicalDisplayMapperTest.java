/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.server.display;

import static android.view.Display.DEFAULT_DISPLAY;
import static android.view.Display.DEFAULT_DISPLAY_GROUP;

import static com.android.server.display.DisplayAdapter.DISPLAY_DEVICE_EVENT_ADDED;
import static com.android.server.display.DisplayAdapter.DISPLAY_DEVICE_EVENT_CHANGED;
import static com.android.server.display.DisplayAdapter.DISPLAY_DEVICE_EVENT_REMOVED;
import static com.android.server.display.LogicalDisplayMapper.LOGICAL_DISPLAY_EVENT_ADDED;
import static com.android.server.display.LogicalDisplayMapper.LOGICAL_DISPLAY_EVENT_REMOVED;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.PropertyInvalidatedCache;
import android.content.Context;
import android.content.res.Resources;
import android.os.Handler;
import android.os.IPowerManager;
import android.os.IThermalService;
import android.os.PowerManager;
import android.os.Process;
import android.os.test.TestLooper;
import android.platform.test.annotations.Presubmit;
import android.view.Display;
import android.view.DisplayAddress;
import android.view.DisplayInfo;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Set;

@SmallTest
@Presubmit
@RunWith(AndroidJUnit4.class)
public class LogicalDisplayMapperTest {
    private static int sUniqueTestDisplayId = 0;
    private static final int DEVICE_STATE_CLOSED = 0;
    private static final int DEVICE_STATE_OPEN = 2;

    private DisplayDeviceRepository mDisplayDeviceRepo;
    private LogicalDisplayMapper mLogicalDisplayMapper;
    private TestLooper mLooper;
    private Handler mHandler;
    private PowerManager mPowerManager;

    @Mock LogicalDisplayMapper.Listener mListenerMock;
    @Mock Context mContextMock;
    @Mock Resources mResourcesMock;
    @Mock IPowerManager mIPowerManagerMock;
    @Mock IThermalService mIThermalServiceMock;

    @Captor ArgumentCaptor<LogicalDisplay> mDisplayCaptor;

    @Before
    public void setUp() {
        // Share classloader to allow package private access.
        System.setProperty("dexmaker.share_classloader", "true");
        MockitoAnnotations.initMocks(this);

        mDisplayDeviceRepo = new DisplayDeviceRepository(
                new DisplayManagerService.SyncRoot(),
                new PersistentDataStore(new PersistentDataStore.Injector() {
                    @Override
                    public InputStream openRead() {
                        return null;
                    }

                    @Override
                    public OutputStream startWrite() {
                        return null;
                    }

                    @Override
                    public void finishWrite(OutputStream os, boolean success) {}
                }));

        // Disable binder caches in this process.
        PropertyInvalidatedCache.disableForTestMode();

        mPowerManager = new PowerManager(mContextMock, mIPowerManagerMock, mIThermalServiceMock,
                null);

        when(mContextMock.getSystemServiceName(PowerManager.class))
                .thenReturn(Context.POWER_SERVICE);
        when(mContextMock.getSystemService(PowerManager.class)).thenReturn(mPowerManager);
        when(mContextMock.getResources()).thenReturn(mResourcesMock);
        when(mResourcesMock.getBoolean(
                com.android.internal.R.bool.config_supportsConcurrentInternalDisplays))
                .thenReturn(true);
        when(mResourcesMock.getIntArray(
                com.android.internal.R.array.config_deviceStatesOnWhichToWakeUp))
                .thenReturn(new int[]{1, 2});
        when(mResourcesMock.getIntArray(
                com.android.internal.R.array.config_deviceStatesOnWhichToSleep))
                .thenReturn(new int[]{0});

        mLooper = new TestLooper();
        mHandler = new Handler(mLooper.getLooper());
        mLogicalDisplayMapper = new LogicalDisplayMapper(mContextMock, mDisplayDeviceRepo,
                mListenerMock, new DisplayManagerService.SyncRoot(), mHandler);
    }


    /////////////////
    // Test Methods
    /////////////////

    @Test
    public void testDisplayDeviceAddAndRemove_Internal() {
        DisplayDevice device = createDisplayDevice(Display.TYPE_INTERNAL, 600, 800,
                DisplayDeviceInfo.FLAG_ALLOWED_TO_BE_DEFAULT_DISPLAY);

        // add
        LogicalDisplay displayAdded = add(device);
        assertEquals(info(displayAdded).address, info(device).address);
        assertEquals(DEFAULT_DISPLAY, id(displayAdded));

        // remove
        mDisplayDeviceRepo.onDisplayDeviceEvent(device, DISPLAY_DEVICE_EVENT_REMOVED);
        verify(mListenerMock).onLogicalDisplayEventLocked(
                mDisplayCaptor.capture(), eq(LOGICAL_DISPLAY_EVENT_REMOVED));
        LogicalDisplay displayRemoved = mDisplayCaptor.getValue();
        assertEquals(DEFAULT_DISPLAY, id(displayRemoved));
        assertEquals(displayAdded, displayRemoved);
    }

    @Test
    public void testDisplayDeviceAddAndRemove_NonInternalTypes() {
        testDisplayDeviceAddAndRemove_NonInternal(Display.TYPE_EXTERNAL);
        testDisplayDeviceAddAndRemove_NonInternal(Display.TYPE_WIFI);
        testDisplayDeviceAddAndRemove_NonInternal(Display.TYPE_OVERLAY);
        testDisplayDeviceAddAndRemove_NonInternal(Display.TYPE_VIRTUAL);
        testDisplayDeviceAddAndRemove_NonInternal(Display.TYPE_UNKNOWN);

        // Call the internal test again, just to verify that adding non-internal displays
        // doesn't affect the ability for an internal display to become the default display.
        testDisplayDeviceAddAndRemove_Internal();
    }

    @Test
    public void testDisplayDeviceAdd_TwoInternalOneDefault() {
        DisplayDevice device1 = createDisplayDevice(Display.TYPE_INTERNAL, 600, 800, 0);
        DisplayDevice device2 = createDisplayDevice(Display.TYPE_INTERNAL, 600, 800,
                DisplayDeviceInfo.FLAG_ALLOWED_TO_BE_DEFAULT_DISPLAY);

        LogicalDisplay display1 = add(device1);
        assertEquals(info(display1).address, info(device1).address);
        assertNotEquals(DEFAULT_DISPLAY, id(display1));

        LogicalDisplay display2 = add(device2);
        assertEquals(info(display2).address, info(device2).address);
        assertEquals(DEFAULT_DISPLAY, id(display2));
    }

    @Test
    public void testDisplayDeviceAdd_TwoInternalBothDefault() {
        DisplayDevice device1 = createDisplayDevice(Display.TYPE_INTERNAL, 600, 800,
                DisplayDeviceInfo.FLAG_ALLOWED_TO_BE_DEFAULT_DISPLAY);
        DisplayDevice device2 = createDisplayDevice(Display.TYPE_INTERNAL, 600, 800,
                DisplayDeviceInfo.FLAG_ALLOWED_TO_BE_DEFAULT_DISPLAY);

        LogicalDisplay display1 = add(device1);
        assertEquals(info(display1).address, info(device1).address);
        assertEquals(DEFAULT_DISPLAY, id(display1));

        LogicalDisplay display2 = add(device2);
        assertEquals(info(display2).address, info(device2).address);
        // Despite the flags, we can only have one default display
        assertNotEquals(DEFAULT_DISPLAY, id(display2));
    }

    @Test
    public void testDisplayDeviceAddAndRemove_OneExternalDefault() {
        DisplayDevice device = createDisplayDevice(Display.TYPE_EXTERNAL, 600, 800,
                DisplayDeviceInfo.FLAG_ALLOWED_TO_BE_DEFAULT_DISPLAY);

        // add
        LogicalDisplay displayAdded = add(device);
        assertEquals(info(displayAdded).address, info(device).address);
        assertEquals(Display.DEFAULT_DISPLAY, id(displayAdded));

        // remove
        mDisplayDeviceRepo.onDisplayDeviceEvent(device, DISPLAY_DEVICE_EVENT_REMOVED);
        verify(mListenerMock).onLogicalDisplayEventLocked(
                mDisplayCaptor.capture(), eq(LOGICAL_DISPLAY_EVENT_REMOVED));
        LogicalDisplay displayRemoved = mDisplayCaptor.getValue();
        assertEquals(DEFAULT_DISPLAY, id(displayRemoved));
        assertEquals(displayAdded, displayRemoved);
    }

    @Test
    public void testDisplayDeviceAddAndRemove_SwitchDefault() {
        DisplayDevice device1 = createDisplayDevice(Display.TYPE_INTERNAL, 600, 800,
                DisplayDeviceInfo.FLAG_ALLOWED_TO_BE_DEFAULT_DISPLAY);
        DisplayDevice device2 = createDisplayDevice(Display.TYPE_INTERNAL, 600, 800,
                DisplayDeviceInfo.FLAG_ALLOWED_TO_BE_DEFAULT_DISPLAY);

        LogicalDisplay display1 = add(device1);
        assertEquals(info(display1).address, info(device1).address);
        assertEquals(DEFAULT_DISPLAY, id(display1));

        LogicalDisplay display2 = add(device2);
        assertEquals(info(display2).address, info(device2).address);
        // We can only have one default display
        assertEquals(DEFAULT_DISPLAY, id(display1));

        // remove
        mDisplayDeviceRepo.onDisplayDeviceEvent(device1, DISPLAY_DEVICE_EVENT_REMOVED);

        verify(mListenerMock).onLogicalDisplayEventLocked(
                mDisplayCaptor.capture(), eq(LOGICAL_DISPLAY_EVENT_REMOVED));
        LogicalDisplay displayRemoved = mDisplayCaptor.getValue();
        // Display 1 is still the default logical display
        assertEquals(DEFAULT_DISPLAY, id(display1));
        // The logical displays had their devices swapped and Display 2 was removed
        assertEquals(display2, displayRemoved);
        assertEquals(info(display1).address, info(device2).address);
    }

    @Test
    public void testGetDisplayIdsLocked() {
        add(createDisplayDevice(Display.TYPE_INTERNAL, 600, 800,
                DisplayDeviceInfo.FLAG_ALLOWED_TO_BE_DEFAULT_DISPLAY));
        add(createDisplayDevice(Display.TYPE_EXTERNAL, 600, 800, 0));
        add(createDisplayDevice(Display.TYPE_VIRTUAL, 600, 800, 0));

        int [] ids = mLogicalDisplayMapper.getDisplayIdsLocked(Process.SYSTEM_UID);
        assertEquals(3, ids.length);
        Arrays.sort(ids);
        assertEquals(DEFAULT_DISPLAY, ids[0]);
    }

    @Test
    public void testGetDisplayInfoForStateLocked_oneDisplayGroup_internalType() {
        add(createDisplayDevice(Display.TYPE_INTERNAL, 600, 800,
                DisplayDeviceInfo.FLAG_ALLOWED_TO_BE_DEFAULT_DISPLAY));
        add(createDisplayDevice(Display.TYPE_INTERNAL, 200, 800,
                DisplayDeviceInfo.FLAG_ALLOWED_TO_BE_DEFAULT_DISPLAY));
        add(createDisplayDevice(Display.TYPE_INTERNAL, 700, 800,
                DisplayDeviceInfo.FLAG_ALLOWED_TO_BE_DEFAULT_DISPLAY));

        Set<DisplayInfo> displayInfos = mLogicalDisplayMapper.getDisplayInfoForStateLocked(
                DeviceStateToLayoutMap.STATE_DEFAULT, DEFAULT_DISPLAY, DEFAULT_DISPLAY_GROUP);
        assertThat(displayInfos.size()).isEqualTo(1);
        for (DisplayInfo displayInfo : displayInfos) {
            assertThat(displayInfo.displayId).isEqualTo(DEFAULT_DISPLAY);
            assertThat(displayInfo.displayGroupId).isEqualTo(DEFAULT_DISPLAY_GROUP);
            assertThat(displayInfo.logicalWidth).isEqualTo(600);
            assertThat(displayInfo.logicalHeight).isEqualTo(800);
        }
    }

    @Test
    public void testGetDisplayInfoForStateLocked_oneDisplayGroup_differentTypes() {
        add(createDisplayDevice(Display.TYPE_INTERNAL, 600, 800,
                DisplayDeviceInfo.FLAG_ALLOWED_TO_BE_DEFAULT_DISPLAY));
        add(createDisplayDevice(Display.TYPE_INTERNAL, 200, 800,
                DisplayDeviceInfo.FLAG_ALLOWED_TO_BE_DEFAULT_DISPLAY));
        add(createDisplayDevice(Display.TYPE_EXTERNAL, 700, 800,
                DisplayDeviceInfo.FLAG_ALLOWED_TO_BE_DEFAULT_DISPLAY));

        Set<DisplayInfo> displayInfos = mLogicalDisplayMapper.getDisplayInfoForStateLocked(
                DeviceStateToLayoutMap.STATE_DEFAULT, DEFAULT_DISPLAY, DEFAULT_DISPLAY_GROUP);
        assertThat(displayInfos.size()).isEqualTo(1);
        for (DisplayInfo displayInfo : displayInfos) {
            assertThat(displayInfo.displayId).isEqualTo(DEFAULT_DISPLAY);
            assertThat(displayInfo.displayGroupId).isEqualTo(DEFAULT_DISPLAY_GROUP);
            assertThat(displayInfo.logicalWidth).isEqualTo(600);
            assertThat(displayInfo.logicalHeight).isEqualTo(800);
        }
    }

    @Test
    public void testGetDisplayInfoForStateLocked_multipleDisplayGroups_defaultGroup() {
        add(createDisplayDevice(Display.TYPE_INTERNAL, 600, 800,
                DisplayDeviceInfo.FLAG_ALLOWED_TO_BE_DEFAULT_DISPLAY));
        add(createDisplayDevice(Display.TYPE_INTERNAL, 200, 800,
                DisplayDeviceInfo.FLAG_ALLOWED_TO_BE_DEFAULT_DISPLAY));
        add(createDisplayDevice(Display.TYPE_VIRTUAL, 700, 800,
                DisplayDeviceInfo.FLAG_OWN_DISPLAY_GROUP));

        Set<DisplayInfo> displayInfos = mLogicalDisplayMapper.getDisplayInfoForStateLocked(
                DeviceStateToLayoutMap.STATE_DEFAULT, DEFAULT_DISPLAY, DEFAULT_DISPLAY_GROUP);
        assertThat(displayInfos.size()).isEqualTo(1);
        for (DisplayInfo displayInfo : displayInfos) {
            assertThat(displayInfo.displayId).isEqualTo(DEFAULT_DISPLAY);
            assertThat(displayInfo.displayGroupId).isEqualTo(DEFAULT_DISPLAY_GROUP);
            assertThat(displayInfo.logicalWidth).isEqualTo(600);
            assertThat(displayInfo.logicalHeight).isEqualTo(800);
        }
    }

    @Test
    public void testSingleDisplayGroup() {
        LogicalDisplay display1 = add(createDisplayDevice(Display.TYPE_INTERNAL, 600, 800,
                DisplayDeviceInfo.FLAG_ALLOWED_TO_BE_DEFAULT_DISPLAY));
        LogicalDisplay display2 = add(createDisplayDevice(Display.TYPE_INTERNAL, 600, 800, 0));
        LogicalDisplay display3 = add(createDisplayDevice(Display.TYPE_VIRTUAL, 600, 800, 0));

        assertEquals(DEFAULT_DISPLAY_GROUP,
                mLogicalDisplayMapper.getDisplayGroupIdFromDisplayIdLocked(id(display1)));
        assertEquals(DEFAULT_DISPLAY_GROUP,
                mLogicalDisplayMapper.getDisplayGroupIdFromDisplayIdLocked(id(display2)));
        assertEquals(DEFAULT_DISPLAY_GROUP,
                mLogicalDisplayMapper.getDisplayGroupIdFromDisplayIdLocked(id(display3)));
    }

    @Test
    public void testMultipleDisplayGroups() {
        LogicalDisplay display1 = add(createDisplayDevice(Display.TYPE_INTERNAL, 600, 800,
                DisplayDeviceInfo.FLAG_ALLOWED_TO_BE_DEFAULT_DISPLAY));
        LogicalDisplay display2 = add(createDisplayDevice(Display.TYPE_INTERNAL, 600, 800, 0));


        TestDisplayDevice device3 = createDisplayDevice(Display.TYPE_VIRTUAL, 600, 800,
                DisplayDeviceInfo.FLAG_OWN_DISPLAY_GROUP);
        LogicalDisplay display3 = add(device3);

        assertEquals(DEFAULT_DISPLAY_GROUP,
                mLogicalDisplayMapper.getDisplayGroupIdFromDisplayIdLocked(id(display1)));
        assertEquals(DEFAULT_DISPLAY_GROUP,
                mLogicalDisplayMapper.getDisplayGroupIdFromDisplayIdLocked(id(display2)));
        assertNotEquals(DEFAULT_DISPLAY_GROUP,
                mLogicalDisplayMapper.getDisplayGroupIdFromDisplayIdLocked(id(display3)));

        // Now switch it back to the default group by removing the flag and issuing an update
        DisplayDeviceInfo info = device3.getSourceInfo();
        info.flags = info.flags & ~DisplayDeviceInfo.FLAG_OWN_DISPLAY_GROUP;
        mDisplayDeviceRepo.onDisplayDeviceEvent(device3, DISPLAY_DEVICE_EVENT_CHANGED);

        // Verify the new group is correct.
        assertEquals(DEFAULT_DISPLAY_GROUP,
                mLogicalDisplayMapper.getDisplayGroupIdFromDisplayIdLocked(id(display3)));
    }

    @Test
    public void testDeviceShouldBeWoken() {
        assertTrue(mLogicalDisplayMapper.shouldDeviceBeWoken(DEVICE_STATE_OPEN,
                DEVICE_STATE_CLOSED,
                /* isInteractive= */false,
                /* isBootCompleted= */true));
    }

    @Test
    public void testDeviceShouldNotBeWoken() {
        assertFalse(mLogicalDisplayMapper.shouldDeviceBeWoken(DEVICE_STATE_CLOSED,
                DEVICE_STATE_OPEN,
                /* isInteractive= */false,
                /* isBootCompleted= */true));
    }

    @Test
    public void testDeviceShouldBePutToSleep() {
        assertTrue(mLogicalDisplayMapper.shouldDeviceBePutToSleep(DEVICE_STATE_CLOSED,
                DEVICE_STATE_OPEN,
                /* isOverrideActive= */false,
                /* isInteractive= */true,
                /* isBootCompleted= */true));
    }

    @Test
    public void testDeviceShouldNotBePutToSleep() {
        assertFalse(mLogicalDisplayMapper.shouldDeviceBePutToSleep(DEVICE_STATE_OPEN,
                DEVICE_STATE_CLOSED,
                /* isOverrideActive= */false,
                /* isInteractive= */true,
                /* isBootCompleted= */true));
    }

    @Test
    public void testDeviceShouldNotBePutToSleepDifferentBaseState() {
        assertFalse(mLogicalDisplayMapper.shouldDeviceBePutToSleep(DEVICE_STATE_CLOSED,
                DEVICE_STATE_OPEN,
                /* isOverrideActive= */true,
                /* isInteractive= */true,
                /* isBootCompleted= */true));
    }

    /////////////////
    // Helper Methods
    /////////////////

    private TestDisplayDevice createDisplayDevice(int type, int width, int height, int flags) {
        return createDisplayDevice(new TestUtils.TestDisplayAddress(), type, width, height, flags);
    }

    private TestDisplayDevice createDisplayDevice(
            DisplayAddress address, int type, int width, int height, int flags) {
        TestDisplayDevice device = new TestDisplayDevice();
        DisplayDeviceInfo displayDeviceInfo = device.getSourceInfo();
        displayDeviceInfo.type = type;
        displayDeviceInfo.width = width;
        displayDeviceInfo.height = height;
        displayDeviceInfo.flags = flags;
        displayDeviceInfo.supportedModes = new Display.Mode[1];
        displayDeviceInfo.supportedModes[0] = new Display.Mode(1, width, height, 60f);
        displayDeviceInfo.modeId = 1;
        displayDeviceInfo.address = address;
        return device;
    }

    private DisplayDeviceInfo info(DisplayDevice device) {
        return device.getDisplayDeviceInfoLocked();
    }

    private DisplayInfo info(LogicalDisplay display) {
        return display.getDisplayInfoLocked();
    }

    private int id(LogicalDisplay display) {
        return display.getDisplayIdLocked();
    }

    private LogicalDisplay add(DisplayDevice device) {
        mDisplayDeviceRepo.onDisplayDeviceEvent(device, DISPLAY_DEVICE_EVENT_ADDED);
        ArgumentCaptor<LogicalDisplay> displayCaptor =
                ArgumentCaptor.forClass(LogicalDisplay.class);
        verify(mListenerMock).onLogicalDisplayEventLocked(
                displayCaptor.capture(), eq(LOGICAL_DISPLAY_EVENT_ADDED));
        clearInvocations(mListenerMock);
        return displayCaptor.getValue();
    }

    private void testDisplayDeviceAddAndRemove_NonInternal(int type) {
        DisplayDevice device = createDisplayDevice(type, 600, 800, 0);

        // add
        LogicalDisplay displayAdded = add(device);
        assertEquals(info(displayAdded).address, info(device).address);
        assertNotEquals(DEFAULT_DISPLAY, id(displayAdded));

        // remove
        mDisplayDeviceRepo.onDisplayDeviceEvent(device, DISPLAY_DEVICE_EVENT_REMOVED);
        verify(mListenerMock).onLogicalDisplayEventLocked(
                mDisplayCaptor.capture(), eq(LOGICAL_DISPLAY_EVENT_REMOVED));
        LogicalDisplay displayRemoved = mDisplayCaptor.getValue();
        assertNotEquals(DEFAULT_DISPLAY, id(displayRemoved));
    }

    class TestDisplayDevice extends DisplayDevice {
        private DisplayDeviceInfo mInfo;
        private DisplayDeviceInfo mSentInfo;

        TestDisplayDevice() {
            super(null, null, "test_display_" + sUniqueTestDisplayId++, mContextMock);
            mInfo = new DisplayDeviceInfo();
        }

        @Override
        public DisplayDeviceInfo getDisplayDeviceInfoLocked() {
            if (mSentInfo == null) {
                mSentInfo = new DisplayDeviceInfo();
                mSentInfo.copyFrom(mInfo);
            }
            return mSentInfo;
        }

        @Override
        public void applyPendingDisplayDeviceInfoChangesLocked() {
            mSentInfo = null;
        }

        @Override
        public boolean hasStableUniqueId() {
            return true;
        }

        public DisplayDeviceInfo getSourceInfo() {
            return mInfo;
        }
    }
}

