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

package android.content;

import static android.content.Context.DEVICE_ID_DEFAULT;
import static android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY;
import static android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC;
import static android.view.Display.DEFAULT_DISPLAY;
import static android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.ActivityThread;
import android.content.res.Configuration;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.ImageReader;
import android.os.UserHandle;
import android.platform.test.annotations.DisabledOnRavenwood;
import android.platform.test.annotations.Presubmit;
import android.platform.test.ravenwood.RavenwoodRule;
import android.view.Display;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 *  Build/Install/Run:
 *   atest FrameworksCoreTests:ContextTest
 */
@Presubmit
@SmallTest
@RunWith(AndroidJUnit4.class)
public class ContextTest {
    @Rule
    public final RavenwoodRule mRavenwood = new RavenwoodRule.Builder().build();

    @Test
    public void testInstrumentationContext() {
        // Confirm that we have a valid Context
        assertNotNull(InstrumentationRegistry.getInstrumentation().getContext());
    }

    @Test
    public void testInstrumentationTargetContext() {
        // Confirm that we have a valid Context
        assertNotNull(InstrumentationRegistry.getInstrumentation().getTargetContext());
    }

    @Test
    @DisabledOnRavenwood(blockedBy = Context.class)
    public void testDisplayIdForSystemContext() {
        final Context systemContext =
                ActivityThread.currentActivityThread().getSystemContext();

        assertEquals(systemContext.getDisplay().getDisplayId(), systemContext.getDisplayId());
    }

    @Test
    @DisabledOnRavenwood(blockedBy = Context.class)
    public void testDisplayIdForSystemUiContext() {
        final Context systemUiContext =
                ActivityThread.currentActivityThread().getSystemUiContext();

        assertEquals(systemUiContext.getDisplay().getDisplayId(), systemUiContext.getDisplayId());
    }

    @Test
    @DisabledOnRavenwood(blockedBy = Context.class)
    public void testDisplayIdForTestContext() {
        final Context testContext =
                InstrumentationRegistry.getInstrumentation().getTargetContext();

        assertEquals(testContext.getDisplayNoVerify().getDisplayId(), testContext.getDisplayId());
    }

    @Test
    @DisabledOnRavenwood(blockedBy = Context.class)
    public void testDisplayIdForDefaultDisplayContext() {
        final Context testContext =
                InstrumentationRegistry.getInstrumentation().getTargetContext();
        final DisplayManager dm = testContext.getSystemService(DisplayManager.class);
        final Context defaultDisplayContext =
                testContext.createDisplayContext(dm.getDisplay(DEFAULT_DISPLAY));

        assertEquals(defaultDisplayContext.getDisplay().getDisplayId(),
                defaultDisplayContext.getDisplayId());
    }

    @Test(expected = NullPointerException.class)
    @DisabledOnRavenwood(blockedBy = Context.class)
    public void testStartActivityAsUserNullIntentNullUser() {
        final Context testContext =
                InstrumentationRegistry.getInstrumentation().getTargetContext();
        testContext.startActivityAsUser(null, null);
    }

    @Test(expected = NullPointerException.class)
    @DisabledOnRavenwood(blockedBy = Context.class)
    public void testStartActivityAsUserNullIntentNonNullUser() {
        final Context testContext =
                InstrumentationRegistry.getInstrumentation().getTargetContext();
        testContext.startActivityAsUser(null, new UserHandle(UserHandle.USER_ALL));
    }

    @Test(expected = NullPointerException.class)
    @DisabledOnRavenwood(blockedBy = Context.class)
    public void testStartActivityAsUserNonNullIntentNullUser() {
        final Context testContext =
                InstrumentationRegistry.getInstrumentation().getTargetContext();
        testContext.startActivityAsUser(new Intent(), null);
    }

    @Test(expected = RuntimeException.class)
    @DisabledOnRavenwood(blockedBy = Context.class)
    public void testStartActivityAsUserNonNullIntentNonNullUser() {
        final Context testContext =
                InstrumentationRegistry.getInstrumentation().getTargetContext();
        testContext.startActivityAsUser(new Intent(), new UserHandle(UserHandle.USER_ALL));
    }

    @Test
    @DisabledOnRavenwood(blockedBy = Context.class)
    public void testIsUiContext_appContext_returnsFalse() {
        final Context appContext = ApplicationProvider.getApplicationContext();

        assertThat(appContext.isUiContext()).isFalse();
    }

    @Test
    @DisabledOnRavenwood(blockedBy = Context.class)
    public void testIsUiContext_systemContext_returnsTrue() {
        final Context systemContext =
                ActivityThread.currentActivityThread().getSystemContext();

        assertThat(systemContext.isUiContext()).isTrue();
    }

    @Test
    @DisabledOnRavenwood(blockedBy = Context.class)
    public void testIsUiContext_systemUiContext_returnsTrue() {
        final Context systemUiContext =
                ActivityThread.currentActivityThread().getSystemUiContext();

        assertThat(systemUiContext.isUiContext()).isTrue();
    }

    @Test
    @DisabledOnRavenwood(blockedBy = Context.class)
    public void testGetDisplayFromDisplayContextDerivedContextOnPrimaryDisplay() {
        verifyGetDisplayFromDisplayContextDerivedContext(false /* onSecondaryDisplay */);
    }

    @Test
    @DisabledOnRavenwood(blockedBy = Context.class)
    public void testGetDisplayFromDisplayContextDerivedContextOnSecondaryDisplay() {
        verifyGetDisplayFromDisplayContextDerivedContext(true /* onSecondaryDisplay */);
    }

    private static void verifyGetDisplayFromDisplayContextDerivedContext(
            boolean onSecondaryDisplay) {
        final Context appContext = ApplicationProvider.getApplicationContext();
        final DisplayManager displayManager = appContext.getSystemService(DisplayManager.class);
        final Display display;
        if (onSecondaryDisplay) {
            display = getSecondaryDisplay(displayManager);
        } else {
            display = displayManager.getDisplay(DEFAULT_DISPLAY);
        }
        final Context context = appContext.createDisplayContext(display)
                .createConfigurationContext(new Configuration());
        assertEquals(display, context.getDisplay());
    }

    private static Display getSecondaryDisplay(DisplayManager displayManager) {
        final int width = 800;
        final int height = 480;
        final int density = 160;
        ImageReader reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888,
                2 /* maxImages */);
        VirtualDisplay virtualDisplay = displayManager.createVirtualDisplay(
                ContextTest.class.getName(), width, height, density, reader.getSurface(),
                VIRTUAL_DISPLAY_FLAG_PUBLIC | VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY);
        return virtualDisplay.getDisplay();
    }

    @Test
    @DisabledOnRavenwood(blockedBy = Context.class)
    public void testIsUiContext_ContextWrapper() {
        ContextWrapper wrapper = new ContextWrapper(null /* base */);

        assertFalse(wrapper.isUiContext());

        wrapper = new ContextWrapper(createUiContext());

        assertTrue(wrapper.isUiContext());
    }

    @Test
    @DisabledOnRavenwood(blockedBy = Context.class)
    public void testIsUiContext_UiContextDerivedContext() {
        final Context uiContext = createUiContext();
        Context context = uiContext.createAttributionContext(null /* attributionTag */);

        assertTrue(context.isUiContext());

        context = uiContext.createConfigurationContext(new Configuration());

        assertTrue(context.isUiContext());
    }

    @Test
    @DisabledOnRavenwood(blockedBy = Context.class)
    public void testIsUiContext_UiContextDerivedDisplayContext() {
        final Context uiContext = createUiContext();
        final Display secondaryDisplay =
                getSecondaryDisplay(uiContext.getSystemService(DisplayManager.class));
        final Context context = uiContext.createDisplayContext(secondaryDisplay);

        assertFalse(context.isUiContext());
    }

    @Test
    @DisabledOnRavenwood(blockedBy = Context.class)
    public void testDeviceIdForSystemContext() {
        final Context systemContext =
                ActivityThread.currentActivityThread().getSystemContext();

        assertEquals(systemContext.getDeviceId(), DEVICE_ID_DEFAULT);
    }

    @Test
    @DisabledOnRavenwood(blockedBy = Context.class)
    public void testDeviceIdForSystemUiContext() {
        final Context systemUiContext =
                ActivityThread.currentActivityThread().getSystemUiContext();

        assertEquals(systemUiContext.getDeviceId(), DEVICE_ID_DEFAULT);
    }

    @Test
    @DisabledOnRavenwood(blockedBy = Context.class)
    public void testDeviceIdForTestContext() {
        final Context testContext =
                InstrumentationRegistry.getInstrumentation().getTargetContext();

        assertEquals(testContext.getDeviceId(), DEVICE_ID_DEFAULT);
    }

    private Context createUiContext() {
        final Context appContext = ApplicationProvider.getApplicationContext();
        final DisplayManager displayManager = appContext.getSystemService(DisplayManager.class);
        final Display display = displayManager.getDisplay(DEFAULT_DISPLAY);
        return appContext.createDisplayContext(display)
                .createWindowContext(TYPE_APPLICATION_OVERLAY, null /* options */);
    }
}
