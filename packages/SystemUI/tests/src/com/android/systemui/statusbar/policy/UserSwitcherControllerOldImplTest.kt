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
 * limitations under the License
 */

package com.android.systemui.statusbar.policy

import android.app.IActivityManager
import android.app.NotificationManager
import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.UserInfo
import android.graphics.Bitmap
import android.hardware.face.FaceManager
import android.hardware.fingerprint.FingerprintManager
import android.os.Handler
import android.os.UserHandle
import android.os.UserManager
import android.provider.Settings
import android.testing.AndroidTestingRunner
import android.testing.TestableLooper
import android.view.ThreadedRenderer
import androidx.test.filters.SmallTest
import com.android.internal.jank.InteractionJankMonitor
import com.android.internal.logging.testing.UiEventLoggerFake
import com.android.internal.util.LatencyTracker
import com.android.internal.util.UserIcons
import com.android.systemui.GuestResetOrExitSessionReceiver
import com.android.systemui.GuestResumeSessionReceiver
import com.android.systemui.GuestSessionNotification
import com.android.systemui.R
import com.android.systemui.SysuiTestCase
import com.android.systemui.animation.DialogCuj
import com.android.systemui.animation.DialogLaunchAnimator
import com.android.systemui.broadcast.BroadcastDispatcher
import com.android.systemui.broadcast.BroadcastSender
import com.android.systemui.dump.DumpManager
import com.android.systemui.plugins.ActivityStarter
import com.android.systemui.plugins.FalsingManager
import com.android.systemui.qs.QSUserSwitcherEvent
import com.android.systemui.qs.user.UserSwitchDialogController
import com.android.systemui.settings.UserTracker
import com.android.systemui.shade.NotificationShadeWindowView
import com.android.systemui.telephony.TelephonyListenerManager
import com.android.systemui.user.data.source.UserRecord
import com.android.systemui.util.concurrency.FakeExecutor
import com.android.systemui.util.mockito.any
import com.android.systemui.util.mockito.argumentCaptor
import com.android.systemui.util.mockito.capture
import com.android.systemui.util.mockito.nullable
import com.android.systemui.util.settings.GlobalSettings
import com.android.systemui.util.settings.SecureSettings
import com.android.systemui.util.time.FakeSystemClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mock
import org.mockito.Mockito.doNothing
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.eq
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations

@RunWith(AndroidTestingRunner::class)
@TestableLooper.RunWithLooper(setAsMainLooper = true)
@SmallTest
class UserSwitcherControllerOldImplTest : SysuiTestCase() {
    @Mock private lateinit var keyguardStateController: KeyguardStateController
    @Mock private lateinit var activityManager: IActivityManager
    @Mock private lateinit var deviceProvisionedController: DeviceProvisionedController
    @Mock private lateinit var devicePolicyManager: DevicePolicyManager
    @Mock private lateinit var handler: Handler
    @Mock private lateinit var userTracker: UserTracker
    @Mock private lateinit var userManager: UserManager
    @Mock private lateinit var activityStarter: ActivityStarter
    @Mock private lateinit var broadcastDispatcher: BroadcastDispatcher
    @Mock private lateinit var broadcastSender: BroadcastSender
    @Mock private lateinit var telephonyListenerManager: TelephonyListenerManager
    @Mock private lateinit var secureSettings: SecureSettings
    @Mock private lateinit var falsingManager: FalsingManager
    @Mock private lateinit var dumpManager: DumpManager
    @Mock private lateinit var interactionJankMonitor: InteractionJankMonitor
    @Mock private lateinit var latencyTracker: LatencyTracker
    @Mock private lateinit var dialogShower: UserSwitchDialogController.DialogShower
    @Mock private lateinit var notificationShadeWindowView: NotificationShadeWindowView
    @Mock private lateinit var threadedRenderer: ThreadedRenderer
    @Mock private lateinit var dialogLaunchAnimator: DialogLaunchAnimator
    @Mock private lateinit var globalSettings: GlobalSettings
    @Mock private lateinit var guestSessionNotification: GuestSessionNotification
    @Mock private lateinit var guestResetOrExitSessionReceiver: GuestResetOrExitSessionReceiver
    private lateinit var resetSessionDialogFactory:
                            GuestResumeSessionReceiver.ResetSessionDialog.Factory
    private lateinit var guestResumeSessionReceiver: GuestResumeSessionReceiver
    private lateinit var testableLooper: TestableLooper
    private lateinit var bgExecutor: FakeExecutor
    private lateinit var longRunningExecutor: FakeExecutor
    private lateinit var uiExecutor: FakeExecutor
    private lateinit var uiEventLogger: UiEventLoggerFake
    private lateinit var userSwitcherController: UserSwitcherControllerOldImpl
    private lateinit var picture: Bitmap
    private val ownerId = UserHandle.USER_SYSTEM
    private val ownerInfo = UserInfo(ownerId, "Owner", null,
            UserInfo.FLAG_ADMIN or UserInfo.FLAG_FULL or UserInfo.FLAG_INITIALIZED or
                    UserInfo.FLAG_PRIMARY or UserInfo.FLAG_SYSTEM,
            UserManager.USER_TYPE_FULL_SYSTEM)
    private val guestId = 1234
    private val guestInfo = UserInfo(guestId, "Guest", null,
            UserInfo.FLAG_FULL or UserInfo.FLAG_GUEST, UserManager.USER_TYPE_FULL_GUEST)
    private val secondaryUser =
            UserInfo(10, "Secondary", null, 0, UserManager.USER_TYPE_FULL_SECONDARY)

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)
        testableLooper = TestableLooper.get(this)
        bgExecutor = FakeExecutor(FakeSystemClock())
        longRunningExecutor = FakeExecutor(FakeSystemClock())
        uiExecutor = FakeExecutor(FakeSystemClock())
        uiEventLogger = UiEventLoggerFake()

        mContext.orCreateTestableResources.addOverride(
                com.android.internal.R.bool.config_guestUserAutoCreated, false)

        mContext.addMockSystemService(Context.FACE_SERVICE, mock(FaceManager::class.java))
        mContext.addMockSystemService(Context.NOTIFICATION_SERVICE,
                mock(NotificationManager::class.java))
        mContext.addMockSystemService(Context.FINGERPRINT_SERVICE,
                mock(FingerprintManager::class.java))

        resetSessionDialogFactory = object : GuestResumeSessionReceiver.ResetSessionDialog.Factory {
                override fun create(userId: Int): GuestResumeSessionReceiver.ResetSessionDialog {
                    return GuestResumeSessionReceiver.ResetSessionDialog(
                                mContext,
                                mock(UserSwitcherController::class.java),
                                uiEventLogger,
                                userId
                            )
                }
            }

        guestResumeSessionReceiver = GuestResumeSessionReceiver(userTracker,
                                        secureSettings,
                                        broadcastDispatcher,
                                        guestSessionNotification,
                                        resetSessionDialogFactory)

        `when`(userManager.canAddMoreUsers(eq(UserManager.USER_TYPE_FULL_SECONDARY)))
                .thenReturn(true)
        `when`(notificationShadeWindowView.context).thenReturn(context)

        // Since userSwitcherController involves InteractionJankMonitor.
        // Let's fulfill the dependencies.
        val mockedContext = mock(Context::class.java)
        doReturn(mockedContext).`when`(notificationShadeWindowView).context
        doReturn(true).`when`(notificationShadeWindowView).isAttachedToWindow
        doNothing().`when`(threadedRenderer).addObserver(any())
        doNothing().`when`(threadedRenderer).removeObserver(any())
        doReturn(threadedRenderer).`when`(notificationShadeWindowView).threadedRenderer

        picture = UserIcons.convertToBitmap(context.getDrawable(R.drawable.ic_avatar_user))

        // Create defaults for the current user
        `when`(userTracker.userId).thenReturn(ownerId)
        `when`(userTracker.userInfo).thenReturn(ownerInfo)

        `when`(
            globalSettings.getIntForUser(
                eq(Settings.Global.ADD_USERS_WHEN_LOCKED),
                anyInt(),
                eq(UserHandle.USER_SYSTEM)
            )
        ).thenReturn(0)

        `when`(
            globalSettings.getIntForUser(
                eq(Settings.Global.USER_SWITCHER_ENABLED),
                anyInt(),
                eq(UserHandle.USER_SYSTEM)
            )
        ).thenReturn(1)

        setupController()
    }

    private fun setupController() {
        userSwitcherController =
            UserSwitcherControllerOldImpl(
                mContext,
                activityManager,
                userManager,
                userTracker,
                keyguardStateController,
                deviceProvisionedController,
                devicePolicyManager,
                handler,
                activityStarter,
                broadcastDispatcher,
                broadcastSender,
                uiEventLogger,
                falsingManager,
                telephonyListenerManager,
                secureSettings,
                globalSettings,
                bgExecutor,
                longRunningExecutor,
                uiExecutor,
                interactionJankMonitor,
                latencyTracker,
                dumpManager,
                dialogLaunchAnimator,
                guestResumeSessionReceiver,
                guestResetOrExitSessionReceiver
            )
        userSwitcherController.init(notificationShadeWindowView)
    }

    @Test
    fun testSwitchUser_parentDialogDismissed() {
        val otherUserRecord = UserRecord(
            secondaryUser,
            picture,
            false /* guest */,
            false /* current */,
            false /* isAddUser */,
            false /* isRestricted */,
            true /* isSwitchToEnabled */,
            false /* isAddSupervisedUser */
        )
        `when`(userTracker.userId).thenReturn(ownerId)
        `when`(userTracker.userInfo).thenReturn(ownerInfo)

        userSwitcherController.onUserListItemClicked(otherUserRecord, dialogShower)
        testableLooper.processAllMessages()

        verify(dialogShower).dismiss()
    }

    @Test
    fun testAddGuest_okButtonPressed() {
        val emptyGuestUserRecord =
            UserRecord(
                null,
                null,
                true /* guest */,
                false /* current */,
                false /* isAddUser */,
                false /* isRestricted */,
                true /* isSwitchToEnabled */,
                false /* isAddSupervisedUser */
            )
        `when`(userTracker.userId).thenReturn(ownerId)
        `when`(userTracker.userInfo).thenReturn(ownerInfo)

        `when`(userManager.createGuest(any())).thenReturn(guestInfo)

        userSwitcherController.onUserListItemClicked(emptyGuestUserRecord, null)
        bgExecutor.runAllReady()
        uiExecutor.runAllReady()
        testableLooper.processAllMessages()
        verify(interactionJankMonitor).begin(any())
        verify(latencyTracker).onActionStart(LatencyTracker.ACTION_USER_SWITCH)
        verify(activityManager).switchUser(guestInfo.id)
        assertEquals(1, uiEventLogger.numLogs())
        assertEquals(QSUserSwitcherEvent.QS_USER_GUEST_ADD.id, uiEventLogger.eventId(0))
    }

    @Test
    fun testAddGuest_parentDialogDismissed() {
        val emptyGuestUserRecord =
            UserRecord(
                null,
                null,
                true /* guest */,
                false /* current */,
                false /* isAddUser */,
                false /* isRestricted */,
                true /* isSwitchToEnabled */,
                false /* isAddSupervisedUser */
            )
        `when`(userTracker.userId).thenReturn(ownerId)
        `when`(userTracker.userInfo).thenReturn(ownerInfo)

        `when`(userManager.createGuest(any())).thenReturn(guestInfo)

        userSwitcherController.onUserListItemClicked(emptyGuestUserRecord, dialogShower)
        bgExecutor.runAllReady()
        uiExecutor.runAllReady()
        testableLooper.processAllMessages()
        verify(dialogShower).dismiss()
    }

    @Test
    fun testRemoveGuest_removeButtonPressed_isLogged() {
        val currentGuestUserRecord =
            UserRecord(
                guestInfo,
                picture,
                true /* guest */,
                true /* current */,
                false /* isAddUser */,
                false /* isRestricted */,
                true /* isSwitchToEnabled */,
                false /* isAddSupervisedUser */
            )
        `when`(userTracker.userId).thenReturn(guestInfo.id)
        `when`(userTracker.userInfo).thenReturn(guestInfo)

        userSwitcherController.onUserListItemClicked(currentGuestUserRecord, null)
        assertNotNull(userSwitcherController.mExitGuestDialog)
        userSwitcherController.mExitGuestDialog
                .getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        testableLooper.processAllMessages()
        assertEquals(1, uiEventLogger.numLogs())
        assertTrue(
            QSUserSwitcherEvent.QS_USER_GUEST_REMOVE.id == uiEventLogger.eventId(0) ||
            QSUserSwitcherEvent.QS_USER_SWITCH.id == uiEventLogger.eventId(0)
        )
    }

    @Test
    fun testRemoveGuest_removeButtonPressed_dialogDismissed() {
        val currentGuestUserRecord =
            UserRecord(
                guestInfo,
                picture,
                true /* guest */,
                true /* current */,
                false /* isAddUser */,
                false /* isRestricted */,
                true /* isSwitchToEnabled */,
                false /* isAddSupervisedUser */
            )
        `when`(userTracker.userId).thenReturn(guestInfo.id)
        `when`(userTracker.userInfo).thenReturn(guestInfo)

        userSwitcherController.onUserListItemClicked(currentGuestUserRecord, null)
        assertNotNull(userSwitcherController.mExitGuestDialog)
        userSwitcherController.mExitGuestDialog
                .getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        testableLooper.processAllMessages()
        assertFalse(userSwitcherController.mExitGuestDialog.isShowing)
    }

    @Test
    fun testRemoveGuest_dialogShowerUsed() {
        val currentGuestUserRecord =
            UserRecord(
                guestInfo,
                picture,
                true /* guest */,
                true /* current */,
                false /* isAddUser */,
                false /* isRestricted */,
                true /* isSwitchToEnabled */,
                false /* isAddSupervisedUser */
            )
        `when`(userTracker.userId).thenReturn(guestInfo.id)
        `when`(userTracker.userInfo).thenReturn(guestInfo)

        userSwitcherController.onUserListItemClicked(currentGuestUserRecord, dialogShower)
        assertNotNull(userSwitcherController.mExitGuestDialog)
        testableLooper.processAllMessages()
        verify(dialogShower)
            .showDialog(
                userSwitcherController.mExitGuestDialog,
                DialogCuj(InteractionJankMonitor.CUJ_USER_DIALOG_OPEN, "exit_guest_mode"))
    }

    @Test
    fun testRemoveGuest_cancelButtonPressed_isNotLogged() {
        val currentGuestUserRecord =
            UserRecord(
                guestInfo,
                picture,
                true /* guest */,
                true /* current */,
                false /* isAddUser */,
                false /* isRestricted */,
                true /* isSwitchToEnabled */,
                false /* isAddSupervisedUser */
            )
        `when`(userTracker.userId).thenReturn(guestId)
        `when`(userTracker.userInfo).thenReturn(guestInfo)

        userSwitcherController.onUserListItemClicked(currentGuestUserRecord, null)
        assertNotNull(userSwitcherController.mExitGuestDialog)
        userSwitcherController.mExitGuestDialog
                .getButton(DialogInterface.BUTTON_NEUTRAL).performClick()
        testableLooper.processAllMessages()
        assertEquals(0, uiEventLogger.numLogs())
    }

    @Test
    fun testWipeGuest_startOverButtonPressed_isLogged() {
        val currentGuestUserRecord =
            UserRecord(
                guestInfo,
                picture,
                true /* guest */,
                false /* current */,
                false /* isAddUser */,
                false /* isRestricted */,
                true /* isSwitchToEnabled */,
                false /* isAddSupervisedUser */
            )
        `when`(userTracker.userId).thenReturn(guestId)
        `when`(userTracker.userInfo).thenReturn(guestInfo)

        // Simulate that guest user has already logged in
        `when`(secureSettings.getIntForUser(
                eq(GuestResumeSessionReceiver.SETTING_GUEST_HAS_LOGGED_IN), anyInt(), anyInt()))
                .thenReturn(1)

        userSwitcherController.onUserListItemClicked(currentGuestUserRecord, null)

        // Simulate a user switch event
        val intent = Intent(Intent.ACTION_USER_SWITCHED).putExtra(Intent.EXTRA_USER_HANDLE, guestId)

        assertNotNull(userSwitcherController.mGuestResumeSessionReceiver)
        userSwitcherController.mGuestResumeSessionReceiver.onReceive(context, intent)

        assertNotNull(userSwitcherController.mGuestResumeSessionReceiver.mNewSessionDialog)
        userSwitcherController.mGuestResumeSessionReceiver.mNewSessionDialog
                .getButton(GuestResumeSessionReceiver.ResetSessionDialog.BUTTON_WIPE).performClick()
        testableLooper.processAllMessages()
        assertEquals(1, uiEventLogger.numLogs())
        assertEquals(QSUserSwitcherEvent.QS_USER_GUEST_WIPE.id, uiEventLogger.eventId(0))
    }

    @Test
    fun testWipeGuest_continueButtonPressed_isLogged() {
        val currentGuestUserRecord =
            UserRecord(
                guestInfo,
                picture,
                true /* guest */,
                false /* current */,
                false /* isAddUser */,
                false /* isRestricted */,
                true /* isSwitchToEnabled */,
                false /* isAddSupervisedUser */
            )
        `when`(userTracker.userId).thenReturn(guestId)
        `when`(userTracker.userInfo).thenReturn(guestInfo)

        // Simulate that guest user has already logged in
        `when`(secureSettings.getIntForUser(
                eq(GuestResumeSessionReceiver.SETTING_GUEST_HAS_LOGGED_IN), anyInt(), anyInt()))
                .thenReturn(1)

        userSwitcherController.onUserListItemClicked(currentGuestUserRecord, null)

        // Simulate a user switch event
        val intent = Intent(Intent.ACTION_USER_SWITCHED).putExtra(Intent.EXTRA_USER_HANDLE, guestId)

        assertNotNull(userSwitcherController.mGuestResumeSessionReceiver)
        userSwitcherController.mGuestResumeSessionReceiver.onReceive(context, intent)

        assertNotNull(userSwitcherController.mGuestResumeSessionReceiver.mNewSessionDialog)
        userSwitcherController.mGuestResumeSessionReceiver.mNewSessionDialog
                .getButton(GuestResumeSessionReceiver.ResetSessionDialog.BUTTON_DONTWIPE)
                .performClick()
        testableLooper.processAllMessages()
        assertEquals(1, uiEventLogger.numLogs())
        assertEquals(QSUserSwitcherEvent.QS_USER_GUEST_CONTINUE.id, uiEventLogger.eventId(0))
    }

    @Test
    fun test_getCurrentUserName_shouldReturnNameOfTheCurrentUser() {
        fun addUser(id: Int, name: String, isCurrent: Boolean) {
            userSwitcherController.users.add(
                UserRecord(
                    UserInfo(id, name, 0),
                    null, false, isCurrent, false,
                    false, false, false
                )
            )
        }
        val bgUserName = "background_user"
        val fgUserName = "foreground_user"

        addUser(1, bgUserName, false)
        addUser(2, fgUserName, true)

        assertEquals(fgUserName, userSwitcherController.currentUserName)
    }

    @Test
    fun isSystemUser_currentUserIsSystemUser_shouldReturnTrue() {
        `when`(userTracker.userId).thenReturn(UserHandle.USER_SYSTEM)
        assertEquals(true, userSwitcherController.isSystemUser)
    }

    @Test
    fun isSystemUser_currentUserIsNotSystemUser_shouldReturnFalse() {
        `when`(userTracker.userId).thenReturn(1)
        assertEquals(false, userSwitcherController.isSystemUser)
    }

    @Test
    fun testCanCreateSupervisedUserWithConfiguredPackage() {
        // GIVEN the supervised user creation package is configured
        `when`(context.getString(
            com.android.internal.R.string.config_supervisedUserCreationPackage))
            .thenReturn("some_pkg")

        // AND the current user is allowed to create new users
        `when`(userTracker.userId).thenReturn(ownerId)
        `when`(userTracker.userInfo).thenReturn(ownerInfo)

        // WHEN the controller is started with the above config
        setupController()
        testableLooper.processAllMessages()

        // THEN a supervised user can be constructed
        assertTrue(userSwitcherController.canCreateSupervisedUser())
    }

    @Test
    fun testCannotCreateSupervisedUserWithConfiguredPackage() {
        // GIVEN the supervised user creation package is NOT configured
        `when`(context.getString(
            com.android.internal.R.string.config_supervisedUserCreationPackage))
            .thenReturn(null)

        // AND the current user is allowed to create new users
        `when`(userTracker.userId).thenReturn(ownerId)
        `when`(userTracker.userInfo).thenReturn(ownerInfo)

        // WHEN the controller is started with the above config
        setupController()
        testableLooper.processAllMessages()

        // THEN a supervised user can NOT be constructed
        assertFalse(userSwitcherController.canCreateSupervisedUser())
    }

    @Test
    fun testCannotCreateUserWhenUserSwitcherDisabled() {
        `when`(
            globalSettings.getIntForUser(
                eq(Settings.Global.USER_SWITCHER_ENABLED),
                anyInt(),
                eq(UserHandle.USER_SYSTEM)
            )
        ).thenReturn(0)
        setupController()
        assertFalse(userSwitcherController.canCreateUser())
    }

    @Test
    fun testCannotCreateGuestUserWhenUserSwitcherDisabled() {
        `when`(
            globalSettings.getIntForUser(
                eq(Settings.Global.USER_SWITCHER_ENABLED),
                anyInt(),
                eq(UserHandle.USER_SYSTEM)
            )
        ).thenReturn(0)
        setupController()
        assertFalse(userSwitcherController.canCreateGuest(false))
    }

    @Test
    fun testCannotCreateSupervisedUserWhenUserSwitcherDisabled() {
        `when`(
            globalSettings.getIntForUser(
                eq(Settings.Global.USER_SWITCHER_ENABLED),
                anyInt(),
                eq(UserHandle.USER_SYSTEM)
            )
        ).thenReturn(0)
        setupController()
        assertFalse(userSwitcherController.canCreateSupervisedUser())
    }

    @Test
    fun addUserSwitchCallback() {
        val broadcastReceiverCaptor = argumentCaptor<BroadcastReceiver>()
        verify(broadcastDispatcher).registerReceiver(
                capture(broadcastReceiverCaptor),
                any(),
                nullable(), nullable(), anyInt(), nullable())

        val cb = mock(UserSwitcherController.UserSwitchCallback::class.java)
        userSwitcherController.addUserSwitchCallback(cb)

        val intent = Intent(Intent.ACTION_USER_SWITCHED).putExtra(Intent.EXTRA_USER_HANDLE, guestId)
        broadcastReceiverCaptor.value.onReceive(context, intent)
        verify(cb).onUserSwitched()
    }

    @Test
    fun onUserItemClicked_guest_runsOnBgThread() {
        val dialogShower = mock(UserSwitchDialogController.DialogShower::class.java)
        val guestUserRecord = UserRecord(
            null,
            picture,
            true /* guest */,
            false /* current */,
            false /* isAddUser */,
            false /* isRestricted */,
            true /* isSwitchToEnabled */,
            false /* isAddSupervisedUser */
        )

        userSwitcherController.onUserListItemClicked(guestUserRecord, dialogShower)
        assertTrue(bgExecutor.numPending() > 0)
        verify(userManager, never()).createGuest(context)
        bgExecutor.runAllReady()
        verify(userManager).createGuest(context)
    }
}
