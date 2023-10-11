/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.systemui.globalactions;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNull;

import android.os.PowerManager;
import android.testing.AndroidTestingRunner;

import androidx.test.filters.SmallTest;

import com.android.internal.R;
import com.android.systemui.SysuiTestCase;
import com.android.systemui.statusbar.BlurUtils;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;


@SmallTest
@RunWith(AndroidTestingRunner.class)
public class ShutdownUiTest extends SysuiTestCase {

    ShutdownUi mShutdownUi;
    @Mock
    BlurUtils mBlurUtils;

    @Before
    public void setUp() throws Exception {
        mShutdownUi = new ShutdownUi(getContext(), mBlurUtils);
    }

    @Test
    public void getRebootMessage_update() {
        int messageId = mShutdownUi.getRebootMessage(true, PowerManager.REBOOT_RECOVERY_UPDATE);
        assertEquals(messageId, R.string.reboot_to_update_reboot);
    }

    @Test
    public void getRebootMessage_rebootDefault() {
        int messageId = mShutdownUi.getRebootMessage(true, "anything-else");
        assertEquals(messageId, R.string.reboot_to_reset_message);
    }

    @Test
    public void getRebootMessage_shutdown() {
        int messageId = mShutdownUi.getRebootMessage(false, "anything-else");
        assertEquals(messageId, R.string.shutdown_progress);
    }

    @Test
    public void getReasonMessage_update() {
        String message = mShutdownUi.getReasonMessage(PowerManager.REBOOT_RECOVERY_UPDATE);
        assertEquals(message, mContext.getString(R.string.reboot_to_update_title));
    }

    @Test
    public void getReasonMessage_rebootDefault() {
        String message = mShutdownUi.getReasonMessage(PowerManager.REBOOT_RECOVERY);
        assertEquals(message, mContext.getString(R.string.reboot_to_reset_title));
    }

    @Test
    public void getRebootMessage_defaultToNone() {
        String message = mShutdownUi.getReasonMessage("anything-else");
        assertNull(message);
    }
}
