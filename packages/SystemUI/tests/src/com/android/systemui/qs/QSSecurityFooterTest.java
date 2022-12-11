/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.android.systemui.qs;

import static android.app.admin.DevicePolicyManager.DEVICE_OWNER_TYPE_DEFAULT;
import static android.app.admin.DevicePolicyManager.DEVICE_OWNER_TYPE_FINANCED;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.AlertDialog;
import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.UserInfo;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.VectorDrawable;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.test.suitebuilder.annotation.SmallTest;
import android.testing.AndroidTestingRunner;
import android.testing.LayoutInflaterBuilder;
import android.testing.TestableImageView;
import android.testing.TestableLooper;
import android.testing.TestableLooper.RunWithLooper;
import android.testing.ViewUtils;
import android.text.SpannableStringBuilder;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.android.systemui.R;
import com.android.systemui.SysuiTestCase;
import com.android.systemui.animation.DialogLaunchAnimator;
import com.android.systemui.broadcast.BroadcastDispatcher;
import com.android.systemui.plugins.ActivityStarter;
import com.android.systemui.settings.UserTracker;
import com.android.systemui.statusbar.policy.SecurityController;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.atomic.AtomicInteger;

/*
 * Compile and run the whole SystemUI test suite:
   runtest --path frameworks/base/packages/SystemUI/tests
 *
 * Compile and run just this class:
   runtest --path \
   frameworks/base/packages/SystemUI/tests/src/com/android/systemui/qs/QSSecurityFooterTest.java
*/

@SmallTest
@RunWith(AndroidTestingRunner.class)
@RunWithLooper
public class QSSecurityFooterTest extends SysuiTestCase {

    private final String MANAGING_ORGANIZATION = "organization";
    private final String DEVICE_OWNER_PACKAGE = "TestDPC";
    private final String VPN_PACKAGE = "TestVPN";
    private final String VPN_PACKAGE_2 = "TestVPN 2";
    private static final String PARENTAL_CONTROLS_LABEL = "Parental Control App";
    private static final ComponentName DEVICE_OWNER_COMPONENT =
            new ComponentName("TestDPC", "Test");
    private static final int DEFAULT_ICON_ID = R.drawable.ic_info_outline;

    private ViewGroup mRootView;
    private TextView mFooterText;
    private TestableImageView mPrimaryFooterIcon;
    private QSSecurityFooter mFooter;
    private QSSecurityFooterUtils mFooterUtils;
    @Mock
    private SecurityController mSecurityController;
    @Mock
    private UserTracker mUserTracker;
    @Mock
    private ActivityStarter mActivityStarter;
    @Mock
    private DialogLaunchAnimator mDialogLaunchAnimator;
    @Mock
    private BroadcastDispatcher mBroadcastDispatcher;

    private TestableLooper mTestableLooper;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mTestableLooper = TestableLooper.get(this);
        Looper looper = mTestableLooper.getLooper();
        Handler mainHandler = new Handler(looper);
        when(mUserTracker.getUserInfo()).thenReturn(mock(UserInfo.class));
        mRootView = (ViewGroup) new LayoutInflaterBuilder(mContext)
                .replace("ImageView", TestableImageView.class)
                .build().inflate(R.layout.quick_settings_security_footer, null, false);
        mFooterUtils = new QSSecurityFooterUtils(getContext(),
                getContext().getSystemService(DevicePolicyManager.class), mUserTracker,
                mainHandler, mActivityStarter, mSecurityController, looper, mDialogLaunchAnimator);
        mFooter = new QSSecurityFooter(mRootView, mainHandler, mSecurityController, looper,
                mBroadcastDispatcher, mFooterUtils);
        mFooterText = mRootView.findViewById(R.id.footer_text);
        mPrimaryFooterIcon = mRootView.findViewById(R.id.primary_footer_icon);

        when(mSecurityController.getDeviceOwnerComponentOnAnyUser())
                .thenReturn(DEVICE_OWNER_COMPONENT);
        when(mSecurityController.getDeviceOwnerType(DEVICE_OWNER_COMPONENT))
                .thenReturn(DEVICE_OWNER_TYPE_DEFAULT);
        ViewUtils.attachView(mRootView);

        mFooter.init();
    }

    @After
    public void tearDown() {
        ViewUtils.detachView(mRootView);
    }

    @Test
    public void testUnmanaged() {
        when(mSecurityController.isDeviceManaged()).thenReturn(false);
        when(mSecurityController.isProfileOwnerOfOrganizationOwnedDevice()).thenReturn(false);
        mFooter.refreshState();

        TestableLooper.get(this).processAllMessages();
        assertEquals(View.GONE, mRootView.getVisibility());
    }

    @Test
    public void testManagedNoOwnerName() {
        when(mSecurityController.isDeviceManaged()).thenReturn(true);
        when(mSecurityController.getDeviceOwnerOrganizationName()).thenReturn(null);
        mFooter.refreshState();

        TestableLooper.get(this).processAllMessages();
        assertEquals(mContext.getString(R.string.quick_settings_disclosure_management),
                     mFooterText.getText());
        assertEquals(View.VISIBLE, mRootView.getVisibility());
        assertEquals(View.VISIBLE, mPrimaryFooterIcon.getVisibility());
        assertEquals(DEFAULT_ICON_ID, mPrimaryFooterIcon.getLastImageResource());
    }

    @Test
    public void testManagedOwnerName() {
        when(mSecurityController.isDeviceManaged()).thenReturn(true);
        when(mSecurityController.getDeviceOwnerOrganizationName())
                .thenReturn(MANAGING_ORGANIZATION);
        mFooter.refreshState();

        TestableLooper.get(this).processAllMessages();
        assertEquals(mContext.getString(R.string.quick_settings_disclosure_named_management,
                                        MANAGING_ORGANIZATION),
                mFooterText.getText());
        assertEquals(View.VISIBLE, mRootView.getVisibility());
        assertEquals(View.VISIBLE, mPrimaryFooterIcon.getVisibility());
        assertEquals(DEFAULT_ICON_ID, mPrimaryFooterIcon.getLastImageResource());
    }

    @Test
    public void testManagedFinancedDeviceWithOwnerName() {
        when(mSecurityController.isDeviceManaged()).thenReturn(true);
        when(mSecurityController.getDeviceOwnerOrganizationName())
                .thenReturn(MANAGING_ORGANIZATION);
        when(mSecurityController.getDeviceOwnerType(DEVICE_OWNER_COMPONENT))
                .thenReturn(DEVICE_OWNER_TYPE_FINANCED);

        mFooter.refreshState();

        TestableLooper.get(this).processAllMessages();
        assertEquals(mContext.getString(
                R.string.quick_settings_financed_disclosure_named_management,
                MANAGING_ORGANIZATION), mFooterText.getText());
        assertEquals(View.VISIBLE, mRootView.getVisibility());
        assertEquals(View.VISIBLE, mPrimaryFooterIcon.getVisibility());
        assertEquals(DEFAULT_ICON_ID, mPrimaryFooterIcon.getLastImageResource());
    }

    @Test
    public void testManagedDemoMode() {
        when(mSecurityController.isDeviceManaged()).thenReturn(true);
        when(mSecurityController.getDeviceOwnerOrganizationName()).thenReturn(null);
        final UserInfo mockUserInfo = Mockito.mock(UserInfo.class);
        when(mockUserInfo.isDemo()).thenReturn(true);
        when(mUserTracker.getUserInfo()).thenReturn(mockUserInfo);
        Settings.Global.putInt(mContext.getContentResolver(), Settings.Global.DEVICE_DEMO_MODE, 1);

        mFooter.refreshState();

        TestableLooper.get(this).processAllMessages();
        assertEquals(View.GONE, mRootView.getVisibility());
    }

    @Test
    public void testUntappableView_profileOwnerOfOrgOwnedDevice() {
        when(mSecurityController.isProfileOwnerOfOrganizationOwnedDevice()).thenReturn(true);

        mFooter.refreshState();

        TestableLooper.get(this).processAllMessages();
        assertFalse(mRootView.isClickable());
        assertEquals(View.GONE, mRootView.findViewById(R.id.footer_icon).getVisibility());
    }

    @Test
    public void testTappableView_profileOwnerOfOrgOwnedDevice_networkLoggingEnabled() {
        when(mSecurityController.isProfileOwnerOfOrganizationOwnedDevice()).thenReturn(true);
        when(mSecurityController.isNetworkLoggingEnabled()).thenReturn(true);
        when(mSecurityController.isWorkProfileOn()).thenReturn(true);
        when(mSecurityController.hasWorkProfile()).thenReturn(true);

        mFooter.refreshState();

        TestableLooper.get(this).processAllMessages();
        assertTrue(mRootView.isClickable());
        assertEquals(View.VISIBLE, mRootView.findViewById(R.id.footer_icon).getVisibility());
    }

    @Test
    public void testUntappableView_profileOwnerOfOrgOwnedDevice_workProfileOff() {
        when(mSecurityController.isProfileOwnerOfOrganizationOwnedDevice()).thenReturn(true);
        when(mSecurityController.isNetworkLoggingEnabled()).thenReturn(true);
        when(mSecurityController.isWorkProfileOn()).thenReturn(false);

        mFooter.refreshState();

        TestableLooper.get(this).processAllMessages();
        assertFalse(mRootView.isClickable());
        assertEquals(View.GONE, mRootView.findViewById(R.id.footer_icon).getVisibility());
    }

    @Test
    public void testNetworkLoggingEnabled_deviceOwner() {
        when(mSecurityController.isDeviceManaged()).thenReturn(true);
        when(mSecurityController.isNetworkLoggingEnabled()).thenReturn(true);
        mFooter.refreshState();

        TestableLooper.get(this).processAllMessages();
        assertEquals(mContext.getString(R.string.quick_settings_disclosure_management_monitoring),
                mFooterText.getText());
        assertEquals(View.VISIBLE, mPrimaryFooterIcon.getVisibility());
        assertEquals(DEFAULT_ICON_ID, mPrimaryFooterIcon.getLastImageResource());

        // Same situation, but with organization name set
        when(mSecurityController.getDeviceOwnerOrganizationName())
                .thenReturn(MANAGING_ORGANIZATION);
        mFooter.refreshState();

        TestableLooper.get(this).processAllMessages();
        assertEquals(mContext.getString(
                             R.string.quick_settings_disclosure_named_management_monitoring,
                             MANAGING_ORGANIZATION),
                     mFooterText.getText());
    }

    @Test
    public void testNetworkLoggingEnabled_managedProfileOwner_workProfileOn() {
        when(mSecurityController.hasWorkProfile()).thenReturn(true);
        when(mSecurityController.isNetworkLoggingEnabled()).thenReturn(true);
        when(mSecurityController.isWorkProfileOn()).thenReturn(true);
        mFooter.refreshState();

        TestableLooper.get(this).processAllMessages();
        assertEquals(mContext.getString(
                R.string.quick_settings_disclosure_managed_profile_network_activity),
                mFooterText.getText());
    }

    @Test
    public void testNetworkLoggingEnabled_managedProfileOwner_workProfileOff() {
        when(mSecurityController.hasWorkProfile()).thenReturn(true);
        when(mSecurityController.isNetworkLoggingEnabled()).thenReturn(true);
        when(mSecurityController.isWorkProfileOn()).thenReturn(false);
        mFooter.refreshState();

        TestableLooper.get(this).processAllMessages();
        assertEquals("", mFooterText.getText());
    }

    @Test
    public void testManagedCACertsInstalled() {
        when(mSecurityController.isDeviceManaged()).thenReturn(true);
        when(mSecurityController.hasCACertInCurrentUser()).thenReturn(true);
        mFooter.refreshState();

        TestableLooper.get(this).processAllMessages();
        assertEquals(mContext.getString(R.string.quick_settings_disclosure_management_monitoring),
                mFooterText.getText());
    }

    @Test
    public void testManagedOneVpnEnabled() {
        when(mSecurityController.isDeviceManaged()).thenReturn(true);
        when(mSecurityController.isVpnEnabled()).thenReturn(true);
        when(mSecurityController.getPrimaryVpnName()).thenReturn(VPN_PACKAGE);
        mFooter.refreshState();

        TestableLooper.get(this).processAllMessages();
        assertEquals(mContext.getString(R.string.quick_settings_disclosure_management_named_vpn,
                                        VPN_PACKAGE),
                     mFooterText.getText());
        assertEquals(View.VISIBLE, mPrimaryFooterIcon.getVisibility());
        assertEquals(R.drawable.stat_sys_vpn_ic, mPrimaryFooterIcon.getLastImageResource());

        // Same situation, but with organization name set
        when(mSecurityController.getDeviceOwnerOrganizationName())
                .thenReturn(MANAGING_ORGANIZATION);
        mFooter.refreshState();

        TestableLooper.get(this).processAllMessages();
        assertEquals(mContext.getString(
                              R.string.quick_settings_disclosure_named_management_named_vpn,
                              MANAGING_ORGANIZATION, VPN_PACKAGE),
                     mFooterText.getText());
    }

    @Test
    public void testManagedTwoVpnsEnabled() {
        when(mSecurityController.isDeviceManaged()).thenReturn(true);
        when(mSecurityController.isVpnEnabled()).thenReturn(true);
        when(mSecurityController.getPrimaryVpnName()).thenReturn(VPN_PACKAGE);
        when(mSecurityController.getWorkProfileVpnName()).thenReturn(VPN_PACKAGE_2);
        mFooter.refreshState();

        TestableLooper.get(this).processAllMessages();
        assertEquals(mContext.getString(R.string.quick_settings_disclosure_management_vpns),
                     mFooterText.getText());
        assertEquals(View.VISIBLE, mPrimaryFooterIcon.getVisibility());
        assertEquals(R.drawable.stat_sys_vpn_ic, mPrimaryFooterIcon.getLastImageResource());

        // Same situation, but with organization name set
        when(mSecurityController.getDeviceOwnerOrganizationName())
                .thenReturn(MANAGING_ORGANIZATION);
        mFooter.refreshState();

        TestableLooper.get(this).processAllMessages();
        assertEquals(mContext.getString(R.string.quick_settings_disclosure_named_management_vpns,
                                        MANAGING_ORGANIZATION),
                     mFooterText.getText());
    }

    @Test
    public void testNetworkLoggingAndVpnEnabled() {
        when(mSecurityController.isDeviceManaged()).thenReturn(true);
        when(mSecurityController.isNetworkLoggingEnabled()).thenReturn(true);
        when(mSecurityController.isVpnEnabled()).thenReturn(true);
        when(mSecurityController.getPrimaryVpnName()).thenReturn("VPN Test App");
        mFooter.refreshState();

        TestableLooper.get(this).processAllMessages();
        assertEquals(View.VISIBLE, mPrimaryFooterIcon.getVisibility());
        assertEquals(R.drawable.stat_sys_vpn_ic, mPrimaryFooterIcon.getLastImageResource());
        assertEquals(mContext.getString(R.string.quick_settings_disclosure_management_monitoring),
                mFooterText.getText());
    }

    @Test
    public void testWorkProfileCACertsInstalled_workProfileOn() {
        when(mSecurityController.isDeviceManaged()).thenReturn(false);
        when(mSecurityController.hasCACertInWorkProfile()).thenReturn(true);
        when(mSecurityController.isWorkProfileOn()).thenReturn(true);
        mFooter.refreshState();

        TestableLooper.get(this).processAllMessages();
        assertEquals(DEFAULT_ICON_ID, mPrimaryFooterIcon.getLastImageResource());
        assertEquals(mContext.getString(
                             R.string.quick_settings_disclosure_managed_profile_monitoring),
                     mFooterText.getText());

        // Same situation, but with organization name set
        when(mSecurityController.getWorkProfileOrganizationName())
                .thenReturn(MANAGING_ORGANIZATION);
        mFooter.refreshState();

        TestableLooper.get(this).processAllMessages();
        assertEquals(mContext.getString(
                             R.string.quick_settings_disclosure_named_managed_profile_monitoring,
                             MANAGING_ORGANIZATION),
                     mFooterText.getText());
    }

    @Test
    public void testWorkProfileCACertsInstalled_workProfileOff() {
        when(mSecurityController.isDeviceManaged()).thenReturn(false);
        when(mSecurityController.hasCACertInWorkProfile()).thenReturn(true);
        when(mSecurityController.isWorkProfileOn()).thenReturn(false);
        mFooter.refreshState();

        TestableLooper.get(this).processAllMessages();
        assertEquals("", mFooterText.getText());
    }

    @Test
    public void testCACertsInstalled() {
        when(mSecurityController.isDeviceManaged()).thenReturn(false);
        when(mSecurityController.hasCACertInCurrentUser()).thenReturn(true);
        mFooter.refreshState();

        TestableLooper.get(this).processAllMessages();
        assertEquals(DEFAULT_ICON_ID, mPrimaryFooterIcon.getLastImageResource());
        assertEquals(mContext.getString(R.string.quick_settings_disclosure_monitoring),
                     mFooterText.getText());
    }

    @Test
    public void testTwoVpnsEnabled() {
        when(mSecurityController.isVpnEnabled()).thenReturn(true);
        when(mSecurityController.getPrimaryVpnName()).thenReturn(VPN_PACKAGE);
        when(mSecurityController.getWorkProfileVpnName()).thenReturn(VPN_PACKAGE_2);
        mFooter.refreshState();

        TestableLooper.get(this).processAllMessages();
        assertEquals(R.drawable.stat_sys_vpn_ic, mPrimaryFooterIcon.getLastImageResource());
        assertEquals(mContext.getString(R.string.quick_settings_disclosure_vpns),
                     mFooterText.getText());
    }

    @Test
    public void testWorkProfileVpnEnabled_workProfileOn() {
        when(mSecurityController.isVpnEnabled()).thenReturn(true);
        when(mSecurityController.getWorkProfileVpnName()).thenReturn(VPN_PACKAGE_2);
        when(mSecurityController.isWorkProfileOn()).thenReturn(true);
        mFooter.refreshState();

        TestableLooper.get(this).processAllMessages();
        assertEquals(R.drawable.stat_sys_vpn_ic, mPrimaryFooterIcon.getLastImageResource());
        assertEquals(mContext.getString(
                             R.string.quick_settings_disclosure_managed_profile_named_vpn,
                             VPN_PACKAGE_2),
                     mFooterText.getText());
    }

    @Test
    public void testWorkProfileVpnEnabled_workProfileOff() {
        when(mSecurityController.isVpnEnabled()).thenReturn(true);
        when(mSecurityController.getWorkProfileVpnName()).thenReturn(VPN_PACKAGE_2);
        when(mSecurityController.isWorkProfileOn()).thenReturn(false);
        mFooter.refreshState();

        TestableLooper.get(this).processAllMessages();
        assertEquals("", mFooterText.getText());
    }

    @Test
    public void testProfileOwnerOfOrganizationOwnedDeviceNoName() {
        when(mSecurityController.isProfileOwnerOfOrganizationOwnedDevice()).thenReturn(true);

        mFooter.refreshState();
        TestableLooper.get(this).processAllMessages();

        assertEquals(mContext.getString(
                R.string.quick_settings_disclosure_management),
                mFooterText.getText());
    }

    @Test
    public void testProfileOwnerOfOrganizationOwnedDeviceWithName() {
        when(mSecurityController.isProfileOwnerOfOrganizationOwnedDevice()).thenReturn(true);
        when(mSecurityController.getWorkProfileOrganizationName())
                .thenReturn(MANAGING_ORGANIZATION);

        mFooter.refreshState();
        TestableLooper.get(this).processAllMessages();

        assertEquals(mContext.getString(
                R.string.quick_settings_disclosure_named_management,
                MANAGING_ORGANIZATION),
                mFooterText.getText());
    }

    @Test
    public void testVpnEnabled() {
        when(mSecurityController.isVpnEnabled()).thenReturn(true);
        when(mSecurityController.getPrimaryVpnName()).thenReturn(VPN_PACKAGE);
        mFooter.refreshState();

        TestableLooper.get(this).processAllMessages();
        assertEquals(R.drawable.stat_sys_vpn_ic, mPrimaryFooterIcon.getLastImageResource());
        assertEquals(mContext.getString(R.string.quick_settings_disclosure_named_vpn,
                                        VPN_PACKAGE),
                     mFooterText.getText());

        when(mSecurityController.hasWorkProfile()).thenReturn(true);
        mFooter.refreshState();

        TestableLooper.get(this).processAllMessages();
        assertEquals(mContext.getString(
                             R.string.quick_settings_disclosure_personal_profile_named_vpn,
                             VPN_PACKAGE),
                     mFooterText.getText());
    }

    @Test
    public void testGetManagementTitleForNonFinancedDevice() {
        when(mSecurityController.isDeviceManaged()).thenReturn(true);

        assertEquals(mContext.getString(R.string.monitoring_title_device_owned),
                mFooterUtils.getManagementTitle(MANAGING_ORGANIZATION));
    }

    @Test
    public void testGetManagementTitleForFinancedDevice() {
        when(mSecurityController.isDeviceManaged()).thenReturn(true);
        when(mSecurityController.getDeviceOwnerType(DEVICE_OWNER_COMPONENT))
                .thenReturn(DEVICE_OWNER_TYPE_FINANCED);

        assertEquals(mContext.getString(R.string.monitoring_title_financed_device,
                MANAGING_ORGANIZATION),
                mFooterUtils.getManagementTitle(MANAGING_ORGANIZATION));
    }

    @Test
    public void testGetManagementMessage_noManagement() {
        assertEquals(null, mFooterUtils.getManagementMessage(
                /* isDeviceManaged= */ false, MANAGING_ORGANIZATION));
    }

    @Test
    public void testGetManagementMessage_deviceOwner() {
        assertEquals(mContext.getString(R.string.monitoring_description_named_management,
                                        MANAGING_ORGANIZATION),
                mFooterUtils.getManagementMessage(
                             /* isDeviceManaged= */ true, MANAGING_ORGANIZATION));
        assertEquals(mContext.getString(R.string.monitoring_description_management),
                mFooterUtils.getManagementMessage(
                             /* isDeviceManaged= */ true,
                             /* organizationName= */ null));
    }

    @Test
    public void testGetManagementMessage_deviceOwner_asFinancedDevice() {
        when(mSecurityController.isDeviceManaged()).thenReturn(true);
        when(mSecurityController.getDeviceOwnerType(DEVICE_OWNER_COMPONENT))
                .thenReturn(DEVICE_OWNER_TYPE_FINANCED);

        assertEquals(mContext.getString(R.string.monitoring_financed_description_named_management,
                MANAGING_ORGANIZATION, MANAGING_ORGANIZATION),
                mFooterUtils.getManagementMessage(
                        /* isDeviceManaged= */ true, MANAGING_ORGANIZATION));
    }

    @Test
    public void testGetCaCertsMessage() {
        assertEquals(null, mFooterUtils.getCaCertsMessage(true, false, false));
        assertEquals(null, mFooterUtils.getCaCertsMessage(false, false, false));
        assertEquals(mContext.getString(R.string.monitoring_description_management_ca_certificate),
                mFooterUtils.getCaCertsMessage(true, true, true));
        assertEquals(mContext.getString(R.string.monitoring_description_management_ca_certificate),
                mFooterUtils.getCaCertsMessage(true, false, true));
        assertEquals(mContext.getString(
                         R.string.monitoring_description_managed_profile_ca_certificate),
                mFooterUtils.getCaCertsMessage(false, false, true));
        assertEquals(mContext.getString(
                         R.string.monitoring_description_ca_certificate),
                mFooterUtils.getCaCertsMessage(false, true, false));
    }

    @Test
    public void testGetNetworkLoggingMessage() {
        // Test network logging message on a device with a device owner.
        // Network traffic may be monitored on the device.
        assertEquals(null, mFooterUtils.getNetworkLoggingMessage(true, false));
        assertEquals(mContext.getString(R.string.monitoring_description_management_network_logging),
                mFooterUtils.getNetworkLoggingMessage(true, true));

        // Test network logging message on a device with a managed profile owner
        // Network traffic may be monitored on the work profile.
        assertEquals(null, mFooterUtils.getNetworkLoggingMessage(false, false));
        assertEquals(
                mContext.getString(R.string.monitoring_description_managed_profile_network_logging),
                mFooterUtils.getNetworkLoggingMessage(false, true));
    }

    @Test
    public void testGetVpnMessage() {
        assertEquals(null, mFooterUtils.getVpnMessage(true, true, null, null));
        assertEquals(addLink(mContext.getString(R.string.monitoring_description_two_named_vpns,
                                 VPN_PACKAGE, VPN_PACKAGE_2)),
                mFooterUtils.getVpnMessage(true, true, VPN_PACKAGE, VPN_PACKAGE_2));
        assertEquals(addLink(mContext.getString(R.string.monitoring_description_two_named_vpns,
                                 VPN_PACKAGE, VPN_PACKAGE_2)),
                mFooterUtils.getVpnMessage(false, true, VPN_PACKAGE, VPN_PACKAGE_2));
        assertEquals(addLink(mContext.getString(R.string.monitoring_description_named_vpn,
                                 VPN_PACKAGE)),
                mFooterUtils.getVpnMessage(true, false, VPN_PACKAGE, null));
        assertEquals(addLink(mContext.getString(R.string.monitoring_description_named_vpn,
                                 VPN_PACKAGE)),
                mFooterUtils.getVpnMessage(false, false, VPN_PACKAGE, null));
        assertEquals(addLink(mContext.getString(R.string.monitoring_description_named_vpn,
                                 VPN_PACKAGE_2)),
                mFooterUtils.getVpnMessage(true, true, null, VPN_PACKAGE_2));
        assertEquals(addLink(mContext.getString(
                                 R.string.monitoring_description_managed_profile_named_vpn,
                                 VPN_PACKAGE_2)),
                mFooterUtils.getVpnMessage(false, true, null, VPN_PACKAGE_2));
        assertEquals(addLink(mContext.getString(
                                 R.string.monitoring_description_personal_profile_named_vpn,
                                 VPN_PACKAGE)),
                mFooterUtils.getVpnMessage(false, true, VPN_PACKAGE, null));
    }

    @Test
    public void testConfigSubtitleVisibility() {
        View view = LayoutInflater.from(mContext)
                .inflate(R.layout.quick_settings_footer_dialog, null);

        // Device Management subtitle should be shown when there is Device Management section only
        // Other sections visibility will be set somewhere else so it will not be tested here
        mFooterUtils.configSubtitleVisibility(true, false, false, false, view);
        assertEquals(View.VISIBLE,
                view.findViewById(R.id.device_management_subtitle).getVisibility());

        // If there are multiple sections, all subtitles should be shown
        mFooterUtils.configSubtitleVisibility(true, true, false, false, view);
        assertEquals(View.VISIBLE,
                view.findViewById(R.id.device_management_subtitle).getVisibility());
        assertEquals(View.VISIBLE,
                view.findViewById(R.id.ca_certs_subtitle).getVisibility());

        // If there are multiple sections, all subtitles should be shown
        mFooterUtils.configSubtitleVisibility(true, true, true, true, view);
        assertEquals(View.VISIBLE,
                view.findViewById(R.id.device_management_subtitle).getVisibility());
        assertEquals(View.VISIBLE,
                view.findViewById(R.id.ca_certs_subtitle).getVisibility());
        assertEquals(View.VISIBLE,
                view.findViewById(R.id.network_logging_subtitle).getVisibility());
        assertEquals(View.VISIBLE,
                view.findViewById(R.id.vpn_subtitle).getVisibility());

        // If there are multiple sections, all subtitles should be shown, event if there is no
        // Device Management section
        mFooterUtils.configSubtitleVisibility(false, true, true, true, view);
        assertEquals(View.VISIBLE,
                view.findViewById(R.id.ca_certs_subtitle).getVisibility());
        assertEquals(View.VISIBLE,
                view.findViewById(R.id.network_logging_subtitle).getVisibility());
        assertEquals(View.VISIBLE,
                view.findViewById(R.id.vpn_subtitle).getVisibility());

        // If there is only 1 section, the title should be hidden
        mFooterUtils.configSubtitleVisibility(false, true, false, false, view);
        assertEquals(View.GONE,
                view.findViewById(R.id.ca_certs_subtitle).getVisibility());
        mFooterUtils.configSubtitleVisibility(false, false, true, false, view);
        assertEquals(View.GONE,
                view.findViewById(R.id.network_logging_subtitle).getVisibility());
        mFooterUtils.configSubtitleVisibility(false, false, false, true, view);
        assertEquals(View.GONE,
                view.findViewById(R.id.vpn_subtitle).getVisibility());
    }

    @Test
    public void testNoClickWhenGone() {
        mFooter.refreshState();

        TestableLooper.get(this).processAllMessages();

        assertFalse(mFooter.hasFooter());
        mFooter.onClick(mFooter.getView());

        // Proxy for dialog being created
        verify(mDialogLaunchAnimator, never()).showFromView(any(), any());
    }

    @Test
    public void testParentalControls() {
        // Make sure the security footer is visible, so that the images are updated.
        when(mSecurityController.isProfileOwnerOfOrganizationOwnedDevice()).thenReturn(true);

        when(mSecurityController.isParentalControlsEnabled()).thenReturn(true);

        Drawable testDrawable = new VectorDrawable();
        when(mSecurityController.getIcon(any())).thenReturn(testDrawable);
        assertNotNull(mSecurityController.getIcon(null));

        mFooter.refreshState();

        TestableLooper.get(this).processAllMessages();

        assertEquals(mContext.getString(R.string.quick_settings_disclosure_parental_controls),
                mFooterText.getText());
        assertEquals(View.VISIBLE, mPrimaryFooterIcon.getVisibility());

        assertEquals(testDrawable, mPrimaryFooterIcon.getDrawable());

        // Ensure the primary icon is back to default after parental controls are gone
        when(mSecurityController.isParentalControlsEnabled()).thenReturn(false);
        mFooter.refreshState();
        TestableLooper.get(this).processAllMessages();

        assertEquals(DEFAULT_ICON_ID, mPrimaryFooterIcon.getLastImageResource());
    }

    @Test
    public void testParentalControlsDialog() {
        when(mSecurityController.isParentalControlsEnabled()).thenReturn(true);
        when(mSecurityController.getLabel(any())).thenReturn(PARENTAL_CONTROLS_LABEL);

        View view = mFooterUtils.createDialogView(getContext());
        TextView textView = (TextView) view.findViewById(R.id.parental_controls_title);
        assertEquals(PARENTAL_CONTROLS_LABEL, textView.getText());
    }

    @Test
    public void testDialogUsesDialogLauncher() {
        when(mSecurityController.isDeviceManaged()).thenReturn(true);
        mFooter.onClick(mRootView);

        mTestableLooper.processAllMessages();

        verify(mDialogLaunchAnimator).showFromView(any(), eq(mRootView), any());
    }

    @Test
    public void testCreateDialogViewForFinancedDevice() {
        when(mSecurityController.isDeviceManaged()).thenReturn(true);
        when(mSecurityController.getDeviceOwnerOrganizationName())
                .thenReturn(MANAGING_ORGANIZATION);
        when(mSecurityController.getDeviceOwnerType(DEVICE_OWNER_COMPONENT))
                .thenReturn(DEVICE_OWNER_TYPE_FINANCED);

        View view = mFooterUtils.createDialogView(getContext());

        TextView managementSubtitle = view.findViewById(R.id.device_management_subtitle);
        assertEquals(View.VISIBLE, managementSubtitle.getVisibility());
        assertEquals(mContext.getString(R.string.monitoring_title_financed_device,
                MANAGING_ORGANIZATION), managementSubtitle.getText());
        TextView managementMessage = view.findViewById(R.id.device_management_warning);
        assertEquals(View.VISIBLE, managementMessage.getVisibility());
        assertEquals(mContext.getString(R.string.monitoring_financed_description_named_management,
                MANAGING_ORGANIZATION, MANAGING_ORGANIZATION), managementMessage.getText());
        assertEquals(mContext.getString(R.string.monitoring_button_view_policies),
                mFooterUtils.getSettingsButton());
    }

    @Test
    public void testFinancedDeviceUsesSettingsButtonText() {
        when(mSecurityController.isDeviceManaged()).thenReturn(true);
        when(mSecurityController.getDeviceOwnerOrganizationName())
                .thenReturn(MANAGING_ORGANIZATION);
        when(mSecurityController.getDeviceOwnerType(DEVICE_OWNER_COMPONENT))
                .thenReturn(DEVICE_OWNER_TYPE_FINANCED);

        mFooter.showDeviceMonitoringDialog();
        ArgumentCaptor<AlertDialog> dialogCaptor = ArgumentCaptor.forClass(AlertDialog.class);

        mTestableLooper.processAllMessages();
        verify(mDialogLaunchAnimator).showFromView(dialogCaptor.capture(), any(), any());

        AlertDialog dialog = dialogCaptor.getValue();
        dialog.create();

        assertEquals(mFooterUtils.getSettingsButton(),
                dialog.getButton(DialogInterface.BUTTON_NEGATIVE).getText());

        dialog.dismiss();
    }

    @Test
    public void testVisibilityListener() {
        final AtomicInteger lastVisibility = new AtomicInteger(-1);
        VisibilityChangedDispatcher.OnVisibilityChangedListener listener = lastVisibility::set;

        mFooter.setOnVisibilityChangedListener(listener);

        when(mSecurityController.isDeviceManaged()).thenReturn(true);
        mFooter.refreshState();
        mTestableLooper.processAllMessages();
        assertEquals(View.VISIBLE, lastVisibility.get());

        when(mSecurityController.isDeviceManaged()).thenReturn(false);
        mFooter.refreshState();
        mTestableLooper.processAllMessages();
        assertEquals(View.GONE, lastVisibility.get());
    }

    @Test
    public void testBroadcastShowsDialog() {
        // Setup dialog content
        when(mSecurityController.isDeviceManaged()).thenReturn(true);
        when(mSecurityController.getDeviceOwnerOrganizationName())
                .thenReturn(MANAGING_ORGANIZATION);
        when(mSecurityController.getDeviceOwnerType(DEVICE_OWNER_COMPONENT))
                .thenReturn(DEVICE_OWNER_TYPE_FINANCED);

        ArgumentCaptor<BroadcastReceiver> captor = ArgumentCaptor.forClass(BroadcastReceiver.class);
        verify(mBroadcastDispatcher).registerReceiverWithHandler(captor.capture(), any(), any(),
                any());

        // Pretend view is not visible temporarily
        mRootView.onVisibilityAggregated(false);
        captor.getValue().onReceive(mContext,
                new Intent(DevicePolicyManager.ACTION_SHOW_DEVICE_MONITORING_DIALOG));
        mTestableLooper.processAllMessages();

        assertTrue(mFooterUtils.getDialog().isShowing());
        mFooterUtils.getDialog().dismiss();
    }

    private CharSequence addLink(CharSequence description) {
        final SpannableStringBuilder message = new SpannableStringBuilder();
        message.append(description);
        message.append(mContext.getString(R.string.monitoring_description_vpn_settings_separator));
        message.append(mContext.getString(R.string.monitoring_description_vpn_settings),
                mFooterUtils.new VpnSpan(), 0);
        return message;
    }
}
