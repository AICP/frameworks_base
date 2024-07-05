/*
 * Copyright (C) 2024 The Android Open Source Project
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

import android.app.admin.DevicePolicyManager
import android.app.admin.DevicePolicyManager.KEYGUARD_DISABLE_WIDGETS_ALL
import android.appwidget.AppWidgetProviderInfo
import android.content.IntentFilter
import android.content.pm.UserInfo
import com.android.systemui.Flags.communalHub
import com.android.systemui.broadcast.BroadcastDispatcher
import com.android.systemui.communal.data.model.CommunalEnabledState
import com.android.systemui.communal.data.model.CommunalWidgetCategories
import com.android.systemui.communal.data.model.DisabledReason
import com.android.systemui.communal.data.model.DisabledReason.DISABLED_REASON_DEVICE_POLICY
import com.android.systemui.communal.data.model.DisabledReason.DISABLED_REASON_FLAG
import com.android.systemui.communal.data.model.DisabledReason.DISABLED_REASON_INVALID_USER
import com.android.systemui.communal.data.model.DisabledReason.DISABLED_REASON_USER_SETTING
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.flags.FeatureFlagsClassic
import com.android.systemui.flags.Flags
import com.android.systemui.util.kotlin.emitOnStart
import com.android.systemui.util.settings.SecureSettings
import com.android.systemui.util.settings.SettingsProxyExt.observerFlow
import java.util.EnumSet
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

interface CommunalSettingsRepository {
    /** A [CommunalEnabledState] for the specified user. */
    fun getEnabledState(user: UserInfo): Flow<CommunalEnabledState>

    /**
     * A flow that reports the widget categories to show on the hub as selected by the user in
     * Settings.
     */
    fun getWidgetCategories(user: UserInfo): Flow<CommunalWidgetCategories>
}

@SysUISingleton
class CommunalSettingsRepositoryImpl
@Inject
constructor(
    @Background private val bgDispatcher: CoroutineDispatcher,
    private val featureFlagsClassic: FeatureFlagsClassic,
    private val secureSettings: SecureSettings,
    private val broadcastDispatcher: BroadcastDispatcher,
    private val devicePolicyManager: DevicePolicyManager,
) : CommunalSettingsRepository {

    private val flagEnabled: Boolean by lazy {
        featureFlagsClassic.isEnabled(Flags.COMMUNAL_SERVICE_ENABLED) && communalHub()
    }

    override fun getEnabledState(user: UserInfo): Flow<CommunalEnabledState> {
        if (!user.isMain) {
            return flowOf(CommunalEnabledState(DISABLED_REASON_INVALID_USER))
        }
        if (!flagEnabled) {
            return flowOf(CommunalEnabledState(DISABLED_REASON_FLAG))
        }
        return combine(
                getEnabledByUser(user).mapToReason(DISABLED_REASON_USER_SETTING),
                getAllowedByDevicePolicy(user).mapToReason(DISABLED_REASON_DEVICE_POLICY),
            ) { reasons ->
                reasons.filterNotNull()
            }
            .map { reasons ->
                if (reasons.isEmpty()) {
                    EnumSet.noneOf(DisabledReason::class.java)
                } else {
                    EnumSet.copyOf(reasons)
                }
            }
            .map { reasons -> CommunalEnabledState(reasons) }
            .flowOn(bgDispatcher)
    }

    override fun getWidgetCategories(user: UserInfo): Flow<CommunalWidgetCategories> =
        secureSettings
            .observerFlow(userId = user.id, names = arrayOf(GLANCEABLE_HUB_CONTENT_SETTING))
            // Force an update
            .onStart { emit(Unit) }
            .map {
                CommunalWidgetCategories(
                    // The default is to show only keyguard widgets.
                    secureSettings.getIntForUser(
                        GLANCEABLE_HUB_CONTENT_SETTING,
                        AppWidgetProviderInfo.WIDGET_CATEGORY_KEYGUARD,
                        user.id
                    )
                )
            }
            .flowOn(bgDispatcher)

    private fun getEnabledByUser(user: UserInfo): Flow<Boolean> =
        secureSettings
            .observerFlow(userId = user.id, names = arrayOf(GLANCEABLE_HUB_ENABLED))
            // Force an update
            .onStart { emit(Unit) }
            .map {
                secureSettings.getIntForUser(
                    GLANCEABLE_HUB_ENABLED,
                    ENABLED_SETTING_DEFAULT,
                    user.id,
                ) == 1
            }

    private fun getAllowedByDevicePolicy(user: UserInfo): Flow<Boolean> =
        broadcastDispatcher
            .broadcastFlow(
                filter =
                    IntentFilter(DevicePolicyManager.ACTION_DEVICE_POLICY_MANAGER_STATE_CHANGED),
                user = user.userHandle
            )
            .emitOnStart()
            .map { devicePolicyManager.areKeyguardWidgetsAllowed(user.id) }

    companion object {
        const val GLANCEABLE_HUB_ENABLED = "glanceable_hub_enabled"
        const val GLANCEABLE_HUB_CONTENT_SETTING = "glanceable_hub_content_setting"
        private const val ENABLED_SETTING_DEFAULT = 1
    }
}

private fun DevicePolicyManager.areKeyguardWidgetsAllowed(userId: Int): Boolean =
    (getKeyguardDisabledFeatures(null, userId) and KEYGUARD_DISABLE_WIDGETS_ALL) == 0

private fun Flow<Boolean>.mapToReason(reason: DisabledReason) = map { enabled ->
    if (enabled) null else reason
}
