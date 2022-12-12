/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.server.pm;

import android.content.pm.UserInfo;
import android.os.Bundle;
import android.os.FileUtils;
import android.os.Parcelable;
import android.os.UserHandle;
import android.os.UserManager;
import android.support.test.uiautomator.UiDevice;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;
import android.text.TextUtils;
import android.util.AtomicFile;

import androidx.test.InstrumentationRegistry;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

@SmallTest
public class UserManagerServiceTest extends AndroidTestCase {
    private static String[] STRING_ARRAY = new String[] {"<tag", "<![CDATA["};
    private File restrictionsFile;
    private int tempUserId = UserHandle.USER_NULL;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        restrictionsFile = new File(mContext.getCacheDir(), "restrictions.xml");
        restrictionsFile.delete();
    }

    @Override
    protected void tearDown() throws Exception {
        restrictionsFile.delete();
        if (tempUserId != UserHandle.USER_NULL) {
            UserManager.get(mContext).removeUser(tempUserId);
        }
        super.tearDown();
    }

    public void testWriteReadApplicationRestrictions() throws IOException {
        AtomicFile atomicFile = new AtomicFile(restrictionsFile);
        Bundle bundle = createBundle();
        UserManagerService.writeApplicationRestrictionsLAr(bundle, atomicFile);
        assertTrue(atomicFile.getBaseFile().exists());
        String s = FileUtils.readTextFile(restrictionsFile, 10000, "");
        System.out.println("restrictionsFile: " + s);
        bundle = UserManagerService.readApplicationRestrictionsLAr(atomicFile);
        System.out.println("readApplicationRestrictionsLocked bundle: " + bundle);
        assertBundle(bundle);
    }

    public void testAddUserWithAccount() {
        UserManager um = UserManager.get(mContext);
        UserInfo user = um.createUser("Test User", 0);
        assertNotNull(user);
        tempUserId = user.id;
        String accountName = "Test Account";
        um.setUserAccount(tempUserId, accountName);
        assertEquals(accountName, um.getUserAccount(tempUserId));
    }

    public void testUserSystemPackageWhitelist() throws Exception {
        String cmd = "cmd user report-system-user-package-whitelist-problems --critical-only";
        final String result = runShellCommand(cmd);
        if (!TextUtils.isEmpty(result)) {
            fail("Command '" + cmd + " reported errors:\n" + result);
        }
    }

    public void testValidateName() {
        assertNull(UserManagerService.validateName("android"));
        assertNull(UserManagerService.validateName("com.company.myapp"));
        assertNotNull(UserManagerService.validateName("/../../data"));
        assertNotNull(UserManagerService.validateName("/dir"));
    }

    private Bundle createBundle() {
        Bundle result = new Bundle();
        // Tests for 6 allowed types: Integer, Boolean, String, String[], Bundle and Parcelable[]
        result.putBoolean("boolean_0", false);
        result.putBoolean("boolean_1", true);
        result.putInt("integer", 100);
        result.putString("empty", "");
        result.putString("string", "text");
        result.putStringArray("string[]", STRING_ARRAY);

        Bundle bundle = new Bundle();
        bundle.putString("bundle_string", "bundle_string");
        bundle.putInt("bundle_int", 1);
        result.putBundle("bundle", bundle);

        Bundle[] bundleArray = new Bundle[2];
        bundleArray[0] = new Bundle();
        bundleArray[0].putString("bundle_array_string", "bundle_array_string");
        bundleArray[0].putBundle("bundle_array_bundle", bundle);
        bundleArray[1] = new Bundle();
        bundleArray[1].putString("bundle_array_string2", "bundle_array_string2");
        result.putParcelableArray("bundle_array", bundleArray);
        return result;
    }

    private void assertBundle(Bundle bundle) {
        assertFalse(bundle.getBoolean("boolean_0"));
        assertTrue(bundle.getBoolean("boolean_1"));
        assertEquals(100, bundle.getInt("integer"));
        assertEquals("", bundle.getString("empty"));
        assertEquals("text", bundle.getString("string"));
        assertEquals(Arrays.asList(STRING_ARRAY), Arrays.asList(bundle.getStringArray("string[]")));
        Parcelable[] bundle_array = bundle.getParcelableArray("bundle_array");
        assertEquals(2, bundle_array.length);
        Bundle bundle1 = (Bundle) bundle_array[0];
        assertEquals("bundle_array_string", bundle1.getString("bundle_array_string"));
        assertNotNull(bundle1.getBundle("bundle_array_bundle"));
        Bundle bundle2 = (Bundle) bundle_array[1];
        assertEquals("bundle_array_string2", bundle2.getString("bundle_array_string2"));
        Bundle childBundle = bundle.getBundle("bundle");
        assertEquals("bundle_string", childBundle.getString("bundle_string"));
        assertEquals(1, childBundle.getInt("bundle_int"));
    }

    private static String runShellCommand(String cmd) throws Exception {
        return UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
                .executeShellCommand(cmd);
    }
}
