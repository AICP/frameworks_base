package com.android.systemui.statusbar.notification

import android.testing.AndroidTestingRunner
import android.testing.TestableLooper
import android.testing.TestableLooper.RunWithLooper
import androidx.test.filters.SmallTest
import com.android.internal.jank.InteractionJankMonitor
import com.android.systemui.SysuiTestCase
import com.android.systemui.shade.NotificationShadeWindowViewController
import com.android.systemui.statusbar.notification.row.ExpandableNotificationRow
import com.android.systemui.statusbar.notification.row.NotificationTestHelper
import com.android.systemui.statusbar.notification.stack.NotificationListContainer
import com.android.systemui.statusbar.phone.HeadsUpManagerPhone
import com.android.systemui.statusbar.policy.HeadsUpUtil
import junit.framework.Assert.assertFalse
import junit.framework.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.MockitoJUnit

@SmallTest
@RunWith(AndroidTestingRunner::class)
@RunWithLooper
class NotificationLaunchAnimatorControllerTest : SysuiTestCase() {
    @Mock lateinit var notificationShadeWindowViewController: NotificationShadeWindowViewController
    @Mock lateinit var notificationListContainer: NotificationListContainer
    @Mock lateinit var headsUpManager: HeadsUpManagerPhone
    @Mock lateinit var jankMonitor: InteractionJankMonitor
    @Mock lateinit var onFinishAnimationCallback: Runnable

    private lateinit var notificationTestHelper: NotificationTestHelper
    private lateinit var notification: ExpandableNotificationRow
    private lateinit var controller: NotificationLaunchAnimatorController

    private val notificationKey: String
        get() = notification.entry.sbn.key

    @get:Rule val rule = MockitoJUnit.rule()

    @Before
    fun setUp() {
        allowTestableLooperAsMainThread()
        notificationTestHelper =
                NotificationTestHelper(mContext, mDependency, TestableLooper.get(this))
        notification = notificationTestHelper.createRow()
        controller = NotificationLaunchAnimatorController(
                notificationShadeWindowViewController,
                notificationListContainer,
                headsUpManager,
                notification,
                jankMonitor,
                onFinishAnimationCallback
        )
    }

    private fun flagNotificationAsHun() {
        `when`(headsUpManager.isAlerting(notificationKey)).thenReturn(true)
    }

    @Test
    fun testHunIsRemovedAndCallbackIsInvokedIfWeDontAnimateLaunch() {
        flagNotificationAsHun()
        controller.onIntentStarted(willAnimate = false)

        assertTrue(HeadsUpUtil.isClickedHeadsUpNotification(notification))
        assertFalse(notification.entry.isExpandAnimationRunning)
        verify(headsUpManager).removeNotification(
                notificationKey, true /* releaseImmediately */, true /* animate */)
        verify(onFinishAnimationCallback).run()
    }

    @Test
    fun testHunIsRemovedAndCallbackIsInvokedWhenAnimationIsCancelled() {
        flagNotificationAsHun()
        controller.onLaunchAnimationCancelled()

        assertTrue(HeadsUpUtil.isClickedHeadsUpNotification(notification))
        assertFalse(notification.entry.isExpandAnimationRunning)
        verify(headsUpManager).removeNotification(
                notificationKey, true /* releaseImmediately */, true /* animate */)
        verify(onFinishAnimationCallback).run()
    }

    @Test
    fun testHunIsRemovedAndCallbackIsInvokedWhenAnimationEnds() {
        flagNotificationAsHun()
        controller.onLaunchAnimationEnd(isExpandingFullyAbove = true)

        assertFalse(HeadsUpUtil.isClickedHeadsUpNotification(notification))
        assertFalse(notification.entry.isExpandAnimationRunning)
        verify(headsUpManager).removeNotification(
                notificationKey, true /* releaseImmediately */, false /* animate */)
        verify(onFinishAnimationCallback).run()
    }

    @Test
    fun testNotificationIsExpandingDuringAnimation() {
        controller.onIntentStarted(willAnimate = true)

        assertTrue(notification.entry.isExpandAnimationRunning)
    }
}
