/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.server.content;

import android.accounts.Account;
import android.content.ContentResolver;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.test.AndroidTestCase;

import androidx.test.filters.SmallTest;

/**
 * Test for SyncOperation.
 *
 * atest ${ANDROID_BUILD_TOP}/frameworks/base/services/tests/servicestests/src/com/android/server/content/SyncOperationTest.java
 */
@SmallTest
public class SyncOperationTest extends AndroidTestCase {

    Account mDummy;
    /** Indicate an unimportant long that we're not testing. */
    long mUnimportantLong = 0L;
    /** Empty bundle. */
    Bundle mEmpty;
    /** Silly authority. */
    String mAuthority;

    @Override
    public void setUp() {
        mDummy = new Account("account1", "type1");
        mEmpty = new Bundle();
        mAuthority = "authority1";
    }

    @SmallTest
    public void testToKey() {
        Account account1 = new Account("account1", "type1");
        Account account2 = new Account("account2", "type2");

        Bundle b1 = new Bundle();
        Bundle b2 = new Bundle();
        b2.putBoolean("b2", true);

        SyncOperation op1 = new SyncOperation(account1, 0,
                1, "foo", 0,
                SyncOperation.REASON_PERIODIC,
                "authority1",
                b1,
                false,
                ContentResolver.SYNC_EXEMPTION_NONE);

        // Same as op1 but different time infos
        SyncOperation op2 = new SyncOperation(account1, 0,
                1, "foo", 0,
                SyncOperation.REASON_PERIODIC,
                "authority1",
                b1,
                false,
                ContentResolver.SYNC_EXEMPTION_NONE);

        // Same as op1 but different authority
        SyncOperation op3 = new SyncOperation(account1, 0,
                1, "foo", 0,
                SyncOperation.REASON_PERIODIC,
                "authority2",
                b1,
                false,
                ContentResolver.SYNC_EXEMPTION_NONE);

        // Same as op1 but different account
        SyncOperation op4 = new SyncOperation(account2, 0,
                1, "foo", 0,
                SyncOperation.REASON_PERIODIC,
                "authority1",
                b1,
                false,
                ContentResolver.SYNC_EXEMPTION_NONE);

        // Same as op1 but different bundle
        SyncOperation op5 = new SyncOperation(account1, 0,
                1, "foo", 0,
                SyncOperation.REASON_PERIODIC,
                "authority1",
                b2,
                false,
                ContentResolver.SYNC_EXEMPTION_NONE);

        assertEquals(op1.key, op2.key);
        assertNotSame(op1.key, op3.key);
        assertNotSame(op1.key, op4.key);
        assertNotSame(op1.key, op5.key);
    }

    @SmallTest
    public void testConversionToExtras() {
        Account account1 = new Account("account1", "type1");
        Bundle b1 = new Bundle();
        b1.putParcelable("acc", account1);
        b1.putString("str", "String");

        SyncOperation op1 = new SyncOperation(account1, 0,
                1, "foo", 0,
                SyncOperation.REASON_PERIODIC,
                "authority1",
                b1,
                false,
                ContentResolver.SYNC_EXEMPTION_NONE);

        PersistableBundle pb = op1.toJobInfoExtras();
        SyncOperation op2 = SyncOperation.maybeCreateFromJobExtras(pb);

        assertTrue("Account fields in extras not persisted.",
                account1.equals(op2.getClonedExtras().get("acc")));
        assertTrue("Fields in extras not persisted", "String".equals(
                op2.getClonedExtras().getString("str")));
    }

    @SmallTest
    public void testConversionFromExtras() {
        PersistableBundle extras = new PersistableBundle();
        SyncOperation op = SyncOperation.maybeCreateFromJobExtras(extras);
        assertTrue("Non sync operation bundle falsely converted to SyncOperation.", op == null);
    }

    /**
     * Tests whether a failed periodic sync operation is converted correctly into a one time
     * sync operation, and whether the periodic sync can be re-created from the one-time operation.
     */
    @SmallTest
    public void testFailedPeriodicConversion() {
        SyncStorageEngine.EndPoint ep = new SyncStorageEngine.EndPoint(new Account("name", "type"),
                "provider", 0);
        Bundle extras = new Bundle();
        SyncOperation periodic = new SyncOperation(ep, 0, "package", 0, 0, extras, false, true,
                SyncOperation.NO_JOB_ID, 60000, 10000,
                ContentResolver.SYNC_EXEMPTION_NONE);
        SyncOperation oneoff = periodic.createOneTimeSyncOperation();
        assertFalse("Conversion to oneoff sync failed.", oneoff.isPeriodic);
        assertEquals("Period not restored", periodic.periodMillis, oneoff.periodMillis);
        assertEquals("Flex not restored", periodic.flexMillis, oneoff.flexMillis);
    }

    @SmallTest
    public void testScheduleAsEjIsInExtras() {
        Account account1 = new Account("account1", "type1");
        Bundle b1 = new Bundle();
        b1.putBoolean(ContentResolver.SYNC_EXTRAS_SCHEDULE_AS_EXPEDITED_JOB, true);

        SyncOperation op1 = new SyncOperation(account1, 0, 1, "foo", 0,
                SyncOperation.REASON_USER_START, "authority1", b1, false,
                ContentResolver.SYNC_EXEMPTION_NONE);
        assertTrue(op1.isScheduledAsExpeditedJob());

        PersistableBundle pb = op1.toJobInfoExtras();
        assertTrue("EJ extra not found in job extras",
                ((PersistableBundle) pb.get("syncExtras"))
                        .containsKey(ContentResolver.SYNC_EXTRAS_SCHEDULE_AS_EXPEDITED_JOB));

        SyncOperation op2 = SyncOperation.maybeCreateFromJobExtras(pb);
        assertTrue("EJ extra not found in extras", op2.getClonedExtras()
                .getBoolean(ContentResolver.SYNC_EXTRAS_SCHEDULE_AS_EXPEDITED_JOB));
    }
}
