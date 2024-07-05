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

package com.android.systemui.communal.data.repository

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.appwidget.AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL
import android.appwidget.AppWidgetProviderInfo.WIDGET_FEATURE_RECONFIGURABLE
import android.content.ComponentName
import android.os.UserHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.communal.data.db.CommunalItemRank
import com.android.systemui.communal.data.db.CommunalWidgetDao
import com.android.systemui.communal.data.db.CommunalWidgetItem
import com.android.systemui.communal.shared.model.CommunalWidgetContentModel
import com.android.systemui.communal.widgets.CommunalAppWidgetHost
import com.android.systemui.communal.widgets.CommunalWidgetHost
import com.android.systemui.communal.widgets.widgetConfiguratorFail
import com.android.systemui.communal.widgets.widgetConfiguratorSuccess
import com.android.systemui.coroutines.collectLastValue
import com.android.systemui.kosmos.testDispatcher
import com.android.systemui.kosmos.testScope
import com.android.systemui.log.LogBuffer
import com.android.systemui.log.logcatLogBuffer
import com.android.systemui.res.R
import com.android.systemui.testKosmos
import com.android.systemui.util.mockito.any
import com.android.systemui.util.mockito.whenever
import com.google.common.truth.Truth.assertThat
import java.util.Optional
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.eq
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations

@OptIn(ExperimentalCoroutinesApi::class)
@SmallTest
@RunWith(AndroidJUnit4::class)
class CommunalWidgetRepositoryImplTest : SysuiTestCase() {
    @Mock private lateinit var appWidgetManager: AppWidgetManager
    @Mock private lateinit var appWidgetHost: CommunalAppWidgetHost
    @Mock private lateinit var stopwatchProviderInfo: AppWidgetProviderInfo
    @Mock private lateinit var providerInfoA: AppWidgetProviderInfo
    @Mock private lateinit var communalWidgetHost: CommunalWidgetHost
    @Mock private lateinit var communalWidgetDao: CommunalWidgetDao

    private lateinit var logBuffer: LogBuffer
    private lateinit var fakeWidgets: MutableStateFlow<Map<CommunalItemRank, CommunalWidgetItem>>

    private val kosmos = testKosmos()
    private val testScope = kosmos.testScope

    private val fakeAllowlist =
        listOf(
            "com.android.fake/WidgetProviderA",
            "com.android.fake/WidgetProviderB",
            "com.android.fake/WidgetProviderC",
        )

    private lateinit var underTest: CommunalWidgetRepositoryImpl

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)
        fakeWidgets = MutableStateFlow(emptyMap())
        logBuffer = logcatLogBuffer(name = "CommunalWidgetRepoImplTest")

        setAppWidgetIds(emptyList())

        overrideResource(R.array.config_communalWidgetAllowlist, fakeAllowlist.toTypedArray())

        whenever(stopwatchProviderInfo.loadLabel(any())).thenReturn("Stopwatch")
        whenever(communalWidgetDao.getWidgets()).thenReturn(fakeWidgets)

        underTest =
            CommunalWidgetRepositoryImpl(
                Optional.of(appWidgetManager),
                appWidgetHost,
                testScope.backgroundScope,
                kosmos.testDispatcher,
                communalWidgetHost,
                communalWidgetDao,
                logBuffer,
            )
    }

    @Test
    fun communalWidgets_queryWidgetsFromDb() =
        testScope.runTest {
            val communalItemRankEntry = CommunalItemRank(uid = 1L, rank = 1)
            val communalWidgetItemEntry = CommunalWidgetItem(uid = 1L, 1, "pk_name/cls_name", 1L)
            fakeWidgets.value = mapOf(communalItemRankEntry to communalWidgetItemEntry)
            whenever(appWidgetManager.getAppWidgetInfo(anyInt())).thenReturn(providerInfoA)

            installedProviders(listOf(stopwatchProviderInfo))

            val communalWidgets by collectLastValue(underTest.communalWidgets)
            verify(communalWidgetDao).getWidgets()
            assertThat(communalWidgets)
                .containsExactly(
                    CommunalWidgetContentModel(
                        appWidgetId = communalWidgetItemEntry.widgetId,
                        providerInfo = providerInfoA,
                        priority = communalItemRankEntry.rank,
                    )
                )
        }

    @Test
    fun addWidget_allocateId_bindWidget_andAddToDb() =
        testScope.runTest {
            val provider = ComponentName("pkg_name", "cls_name")
            val id = 1
            val priority = 1
            val user = UserHandle(0)
            whenever(communalWidgetHost.getAppWidgetInfo(id))
                .thenReturn(PROVIDER_INFO_REQUIRES_CONFIGURATION)
            whenever(
                    communalWidgetHost.allocateIdAndBindWidget(
                        any<ComponentName>(),
                        any<UserHandle>()
                    )
                )
                .thenReturn(id)
            underTest.addWidget(provider, user, priority, kosmos.widgetConfiguratorSuccess)
            runCurrent()

            verify(communalWidgetHost).allocateIdAndBindWidget(provider, user)
            verify(communalWidgetDao).addWidget(id, provider, priority)
        }

    @Test
    fun addWidget_configurationFails_doNotAddWidgetToDb() =
        testScope.runTest {
            val provider = ComponentName("pkg_name", "cls_name")
            val id = 1
            val priority = 1
            val user = UserHandle(0)
            whenever(communalWidgetHost.getAppWidgetInfo(id))
                .thenReturn(PROVIDER_INFO_REQUIRES_CONFIGURATION)
            whenever(
                    communalWidgetHost.allocateIdAndBindWidget(
                        any<ComponentName>(),
                        any<UserHandle>()
                    )
                )
                .thenReturn(id)
            underTest.addWidget(provider, user, priority, kosmos.widgetConfiguratorFail)
            runCurrent()

            verify(communalWidgetHost).allocateIdAndBindWidget(provider, user)
            verify(communalWidgetDao, never()).addWidget(id, provider, priority)
            verify(appWidgetHost).deleteAppWidgetId(id)
        }

    @Test
    fun addWidget_configurationThrowsError_doNotAddWidgetToDb() =
        testScope.runTest {
            val provider = ComponentName("pkg_name", "cls_name")
            val id = 1
            val priority = 1
            val user = UserHandle(0)
            whenever(communalWidgetHost.getAppWidgetInfo(id))
                .thenReturn(PROVIDER_INFO_REQUIRES_CONFIGURATION)
            whenever(
                    communalWidgetHost.allocateIdAndBindWidget(
                        any<ComponentName>(),
                        any<UserHandle>()
                    )
                )
                .thenReturn(id)
            underTest.addWidget(provider, user, priority) {
                throw IllegalStateException("some error")
            }
            runCurrent()

            verify(communalWidgetHost).allocateIdAndBindWidget(provider, user)
            verify(communalWidgetDao, never()).addWidget(id, provider, priority)
            verify(appWidgetHost).deleteAppWidgetId(id)
        }

    @Test
    fun addWidget_configurationNotRequired_doesNotConfigure_addWidgetToDb() =
        testScope.runTest {
            val provider = ComponentName("pkg_name", "cls_name")
            val id = 1
            val priority = 1
            val user = UserHandle(0)
            whenever(communalWidgetHost.getAppWidgetInfo(id))
                .thenReturn(PROVIDER_INFO_CONFIGURATION_OPTIONAL)
            whenever(
                    communalWidgetHost.allocateIdAndBindWidget(
                        any<ComponentName>(),
                        any<UserHandle>()
                    )
                )
                .thenReturn(id)
            underTest.addWidget(provider, user, priority, kosmos.widgetConfiguratorFail)
            runCurrent()

            verify(communalWidgetHost).allocateIdAndBindWidget(provider, user)
            verify(communalWidgetDao).addWidget(id, provider, priority)
        }

    @Test
    fun deleteWidget_deletefromDbTrue_alsoDeleteFromHost() =
        testScope.runTest {
            val id = 1
            whenever(communalWidgetDao.deleteWidgetById(eq(id))).thenReturn(true)
            underTest.deleteWidget(id)
            runCurrent()

            verify(communalWidgetDao).deleteWidgetById(id)
            verify(appWidgetHost).deleteAppWidgetId(id)
        }

    @Test
    fun deleteWidget_deletefromDbFalse_doesNotDeleteFromHost() =
        testScope.runTest {
            val id = 1
            whenever(communalWidgetDao.deleteWidgetById(eq(id))).thenReturn(false)
            underTest.deleteWidget(id)
            runCurrent()

            verify(communalWidgetDao).deleteWidgetById(id)
            verify(appWidgetHost, never()).deleteAppWidgetId(id)
        }

    @Test
    fun reorderWidgets_queryDb() =
        testScope.runTest {
            val widgetIdToPriorityMap = mapOf(104 to 1, 103 to 2, 101 to 3)
            underTest.updateWidgetOrder(widgetIdToPriorityMap)
            runCurrent()

            verify(communalWidgetDao).updateWidgetOrder(widgetIdToPriorityMap)
        }

    private fun installedProviders(providers: List<AppWidgetProviderInfo>) {
        whenever(appWidgetManager.installedProviders).thenReturn(providers)
    }

    private fun setAppWidgetIds(ids: List<Int>) {
        whenever(appWidgetHost.appWidgetIds).thenReturn(ids.toIntArray())
    }

    private companion object {
        val PROVIDER_INFO_REQUIRES_CONFIGURATION =
            AppWidgetProviderInfo().apply { configure = ComponentName("test.pkg", "test.cmp") }
        val PROVIDER_INFO_CONFIGURATION_OPTIONAL =
            AppWidgetProviderInfo().apply {
                configure = ComponentName("test.pkg", "test.cmp")
                widgetFeatures =
                    WIDGET_FEATURE_CONFIGURATION_OPTIONAL or WIDGET_FEATURE_RECONFIGURABLE
            }
    }
}
