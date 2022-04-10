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
 * limitations under the License.
 */

package com.android.systemui.statusbar.connectivity

import android.telephony.ServiceState
import android.telephony.SignalStrength
import android.telephony.TelephonyDisplayInfo
import android.telephony.TelephonyManager
import com.android.settingslib.Utils
import com.android.settingslib.mobile.MobileStatusTracker.MobileStatus
import com.android.settingslib.mobile.TelephonyIcons
import java.lang.IllegalArgumentException

/**
 * Box for all policy-related state used in [MobileSignalController]
 */
internal class MobileState(
    @JvmField var networkName: String? = null,
    @JvmField var networkNameData: String? = null,
    @JvmField var dataSim: Boolean = false,
    @JvmField var dataConnected: Boolean = false,
    @JvmField var isEmergency: Boolean = false,
    @JvmField var airplaneMode: Boolean = false,
    @JvmField var carrierNetworkChangeMode: Boolean = false,
    @JvmField var isDefault: Boolean = false,
    @JvmField var userSetup: Boolean = false,
    @JvmField var roaming: Boolean = false,
    @JvmField var dataState: Int = TelephonyManager.DATA_DISCONNECTED,
    // Tracks the on/off state of the defaultDataSubscription
    @JvmField var defaultDataOff: Boolean = false,
    @JvmField var imsRegistered: Boolean = false,
    @JvmField var voiceCapable: Boolean = false,
    @JvmField var videoCapable: Boolean = false
) : ConnectivityState() {

    @JvmField var telephonyDisplayInfo = TelephonyDisplayInfo(TelephonyManager.NETWORK_TYPE_UNKNOWN,
            TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NONE)
    @JvmField var serviceState: ServiceState? = null
    @JvmField var signalStrength: SignalStrength? = null

    /** @return true if this state is disabled or not default data */
    val isDataDisabledOrNotDefault: Boolean
        get() = (iconGroup === TelephonyIcons.DATA_DISABLED ||
                iconGroup === TelephonyIcons.NOT_DEFAULT_DATA) && userSetup

    /** @return if this state is considered to have inbound activity */
    fun hasActivityIn(): Boolean {
        return dataConnected && !carrierNetworkChangeMode && activityIn
    }

    /** @return if this state is considered to have outbound activity */
    fun hasActivityOut(): Boolean {
        return dataConnected && !carrierNetworkChangeMode && activityOut
    }

    /** @return true if this state should show a RAT icon in quick settings */
    fun showQuickSettingsRatIcon(): Boolean {
        return dataConnected || isDataDisabledOrNotDefault
    }

    override fun copyFrom(other: ConnectivityState) {
        val o = other as? MobileState ?: throw IllegalArgumentException(
                "MobileState can only update from another MobileState")

        super.copyFrom(o)
        networkName = o.networkName
        networkNameData = o.networkNameData
        dataSim = o.dataSim
        dataConnected = o.dataConnected
        isEmergency = o.isEmergency
        airplaneMode = o.airplaneMode
        carrierNetworkChangeMode = o.carrierNetworkChangeMode
        isDefault = o.isDefault
        userSetup = o.userSetup
        roaming = o.roaming
        dataState = o.dataState
        defaultDataOff = o.defaultDataOff
        imsRegistered = o.imsRegistered
        voiceCapable = o.voiceCapable
        videoCapable = o.videoCapable

        telephonyDisplayInfo = o.telephonyDisplayInfo
        serviceState = o.serviceState
        signalStrength = o.signalStrength
    }

    fun isDataConnected(): Boolean {
        return connected && dataState == TelephonyManager.DATA_CONNECTED
    }

    /** @return the current voice service state, or -1 if null */
    fun getVoiceServiceState(): Int {
        return serviceState?.state ?: -1
    }

    fun isNoCalling(): Boolean {
        return serviceState?.state != ServiceState.STATE_IN_SERVICE
    }

    fun getOperatorAlphaShort(): String {
        return serviceState?.operatorAlphaShort ?: ""
    }

    fun isCdma(): Boolean {
        return signalStrength != null && !signalStrength!!.isGsm
    }

    fun isEmergencyOnly(): Boolean {
        return serviceState != null && serviceState!!.isEmergencyOnly
    }

    fun isInService(): Boolean {
        return Utils.isInService(serviceState)
    }

    fun isRoaming(): Boolean {
        return serviceState != null && serviceState!!.roaming
    }

    fun setFromMobileStatus(mobileStatus: MobileStatus) {
        activityIn = mobileStatus.activityIn
        activityOut = mobileStatus.activityOut
        dataSim = mobileStatus.dataSim
        carrierNetworkChangeMode = mobileStatus.carrierNetworkChangeMode
        dataState = mobileStatus.dataState
        signalStrength = mobileStatus.signalStrength
        telephonyDisplayInfo = mobileStatus.telephonyDisplayInfo
        serviceState = mobileStatus.serviceState
    }

    override fun toString(builder: StringBuilder) {
        super.toString(builder)
        builder.append(',')
        builder.append("dataSim=$dataSim,")
        builder.append("networkName=$networkName,")
        builder.append("networkNameData=$networkNameData,")
        builder.append("dataConnected=$dataConnected,")
        builder.append("roaming=$roaming,")
        builder.append("isDefault=$isDefault,")
        builder.append("isEmergency=$isEmergency,")
        builder.append("airplaneMode=$airplaneMode,")
        builder.append("carrierNetworkChangeMode=$carrierNetworkChangeMode,")
        builder.append("userSetup=$userSetup,")
        builder.append("dataState=$dataState,")
        builder.append("defaultDataOff=$defaultDataOff,")
        builder.append("imsRegistered=$imsRegistered,")
        builder.append("voiceCapable=$voiceCapable,")
        builder.append("videoCapable=$videoCapable,")

        // Computed properties
        builder.append("showQuickSettingsRatIcon=${showQuickSettingsRatIcon()},")
        builder.append("voiceServiceState=${getVoiceServiceState()},")
        builder.append("isInService=${isInService()},")

        builder.append("serviceState=${serviceState?.minLog() ?: "(null)"},")
        builder.append("signalStrength=${signalStrength?.minLog() ?: "(null)"},")
        builder.append("displayInfo=$telephonyDisplayInfo")
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        if (!super.equals(other)) return false

        other as MobileState

        if (networkName != other.networkName) return false
        if (networkNameData != other.networkNameData) return false
        if (dataSim != other.dataSim) return false
        if (dataConnected != other.dataConnected) return false
        if (isEmergency != other.isEmergency) return false
        if (airplaneMode != other.airplaneMode) return false
        if (carrierNetworkChangeMode != other.carrierNetworkChangeMode) return false
        if (isDefault != other.isDefault) return false
        if (userSetup != other.userSetup) return false
        if (roaming != other.roaming) return false
        if (dataState != other.dataState) return false
        if (defaultDataOff != other.defaultDataOff) return false
        if (imsRegistered != other.imsRegistered) return false
        if (voiceCapable != other.voiceCapable) return false
        if (videoCapable != other.videoCapable) return false
        if (telephonyDisplayInfo != other.telephonyDisplayInfo) return false
        if (serviceState != other.serviceState) return false
        if (signalStrength != other.signalStrength) return false

        return true
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + (networkName?.hashCode() ?: 0)
        result = 31 * result + (networkNameData?.hashCode() ?: 0)
        result = 31 * result + dataSim.hashCode()
        result = 31 * result + dataConnected.hashCode()
        result = 31 * result + isEmergency.hashCode()
        result = 31 * result + airplaneMode.hashCode()
        result = 31 * result + carrierNetworkChangeMode.hashCode()
        result = 31 * result + isDefault.hashCode()
        result = 31 * result + userSetup.hashCode()
        result = 31 * result + roaming.hashCode()
        result = 31 * result + dataState
        result = 31 * result + defaultDataOff.hashCode()
        result = 31 * result + imsRegistered.hashCode()
        result = 31 * result + voiceCapable.hashCode()
        result = 31 * result + videoCapable.hashCode()
        result = 31 * result + telephonyDisplayInfo.hashCode()
        result = 31 * result + (serviceState?.hashCode() ?: 0)
        result = 31 * result + (signalStrength?.hashCode() ?: 0)
        return result
    }
}

/** toString() is a little more verbose than we need. Just log the fields we read */
private fun ServiceState.minLog(): String {
    return "serviceState={" +
            "state=$state," +
            "isEmergencyOnly=$isEmergencyOnly," +
            "roaming=$roaming," +
            "operatorNameAlphaShort=$operatorAlphaShort}"
}

private fun SignalStrength.minLog(): String {
    return "signalStrength={" +
            "isGsm=$isGsm," +
            "level=$level}"
}
