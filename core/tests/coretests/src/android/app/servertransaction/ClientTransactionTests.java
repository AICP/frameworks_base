/*
 * Copyright 2017 The Android Open Source Project
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

package android.app.servertransaction;

import static android.platform.test.flag.junit.SetFlagsRule.DefaultInitValueType.DEVICE_DEFAULT;

import static com.android.window.flags.Flags.FLAG_BUNDLE_CLIENT_TRANSACTION_FLAG;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import android.app.ClientTransactionHandler;
import android.platform.test.annotations.Presubmit;
import android.platform.test.flag.junit.SetFlagsRule;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests for {@link ClientTransaction}.
 *
 * <p>Build/Install/Run:
 *  atest FrameworksCoreTests:ClientTransactionTests
 *
 * <p>This test class is a part of Window Manager Service tests and specified in
 * {@link com.android.server.wm.test.filters.FrameworksTestsFilter}.
 */
@RunWith(AndroidJUnit4.class)
@SmallTest
@Presubmit
public class ClientTransactionTests {

    @Rule
    public final SetFlagsRule mSetFlagsRule = new SetFlagsRule(DEVICE_DEFAULT);

    @Test
    public void testPreExecute() {
        mSetFlagsRule.disableFlags(FLAG_BUNDLE_CLIENT_TRANSACTION_FLAG);

        testPreExecuteInner();
    }

    @Test
    public void testPreExecute_bundleClientTransaction() {
        mSetFlagsRule.enableFlags(FLAG_BUNDLE_CLIENT_TRANSACTION_FLAG);

        testPreExecuteInner();
    }

    private void testPreExecuteInner() {
        final ClientTransactionItem callback1 = mock(ClientTransactionItem.class);
        final ClientTransactionItem callback2 = mock(ClientTransactionItem.class);
        final ActivityLifecycleItem stateRequest = mock(ActivityLifecycleItem.class);
        doReturn(true).when(stateRequest).isActivityLifecycleItem();
        final ClientTransactionHandler clientTransactionHandler =
                mock(ClientTransactionHandler.class);

        final ClientTransaction transaction = ClientTransaction.obtain(null /* client */);
        transaction.addTransactionItem(callback1);
        transaction.addTransactionItem(callback2);
        transaction.addTransactionItem(stateRequest);

        transaction.preExecute(clientTransactionHandler);

        verify(callback1).preExecute(clientTransactionHandler);
        verify(callback2).preExecute(clientTransactionHandler);
        verify(stateRequest).preExecute(clientTransactionHandler);
    }
}
