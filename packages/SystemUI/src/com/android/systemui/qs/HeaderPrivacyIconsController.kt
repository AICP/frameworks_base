package com.android.systemui.qs

import android.view.View
import com.android.internal.R
import com.android.internal.logging.UiEventLogger
import com.android.systemui.privacy.OngoingPrivacyChip
import com.android.systemui.privacy.PrivacyChipEvent
import com.android.systemui.privacy.PrivacyDialogController
import com.android.systemui.privacy.PrivacyItem
import com.android.systemui.privacy.PrivacyItemController
import com.android.systemui.privacy.logging.PrivacyLogger
import com.android.systemui.statusbar.phone.StatusIconContainer
import javax.inject.Inject
import com.android.systemui.statusbar.policy.DeviceProvisionedController

interface ChipVisibilityListener {
    fun onChipVisibilityRefreshed(visible: Boolean)
}

/**
 * Controls privacy icons/chip residing in QS header which show up when app is using camera,
 * microphone or location.
 * Manages their visibility depending on privacy signals coming from [PrivacyItemController].
 *
 * Unlike typical controller extending [com.android.systemui.util.ViewController] this view doesn't
 * observe its attachment state because depending on where it is used, it might be never detached.
 * Instead, parent controller should use [onParentVisible] and [onParentInvisible] to "activate" or
 * "deactivate" this controller.
 */
class HeaderPrivacyIconsController @Inject constructor(
    private val privacyItemController: PrivacyItemController,
    private val uiEventLogger: UiEventLogger,
    private val privacyChip: OngoingPrivacyChip,
    private val privacyDialogController: PrivacyDialogController,
    private val privacyLogger: PrivacyLogger,
    private val iconContainer: StatusIconContainer,
    private val deviceProvisionedController: DeviceProvisionedController
) {

    var chipVisibilityListener: ChipVisibilityListener? = null
    private var listening = false
    private var micCameraIndicatorsEnabled = false
    private var locationIndicatorsEnabled = false
    private var privacyChipLogged = false
    private val cameraSlot = privacyChip.resources.getString(R.string.status_bar_camera)
    private val micSlot = privacyChip.resources.getString(R.string.status_bar_microphone)
    private val locationSlot = privacyChip.resources.getString(R.string.status_bar_location)

    private val picCallback: PrivacyItemController.Callback =
            object : PrivacyItemController.Callback {
        override fun onPrivacyItemsChanged(privacyItems: List<PrivacyItem>) {
            privacyChip.privacyList = privacyItems
            setChipVisibility(privacyItems.isNotEmpty())
        }

        override fun onFlagMicCameraChanged(flag: Boolean) {
            if (micCameraIndicatorsEnabled != flag) {
                micCameraIndicatorsEnabled = flag
                update()
            }
        }

        override fun onFlagLocationChanged(flag: Boolean) {
            if (locationIndicatorsEnabled != flag) {
                locationIndicatorsEnabled = flag
                update()
            }
        }

        private fun update() {
            updatePrivacyIconSlots()
            setChipVisibility(privacyChip.privacyList.isNotEmpty())
        }
    }

    private fun getChipEnabled() = micCameraIndicatorsEnabled || locationIndicatorsEnabled

    fun onParentVisible() {
        privacyChip.setOnClickListener {
            // Do not expand dialog while device is not provisioned
            if (!deviceProvisionedController.isDeviceProvisioned) return@setOnClickListener
            // If the privacy chip is visible, it means there were some indicators
            uiEventLogger.log(PrivacyChipEvent.ONGOING_INDICATORS_CHIP_CLICK)
            privacyDialogController.showDialog(privacyChip.context)
        }
        setChipVisibility(privacyChip.visibility == View.VISIBLE)
        micCameraIndicatorsEnabled = privacyItemController.micCameraAvailable
        locationIndicatorsEnabled = privacyItemController.locationAvailable

        // Ignore privacy icons because they show in the space above QQS
        updatePrivacyIconSlots()
    }

    fun onParentInvisible() {
        chipVisibilityListener = null
        privacyChip.setOnClickListener(null)
    }

    fun startListening() {
        listening = true
        // Get the most up to date info
        micCameraIndicatorsEnabled = privacyItemController.micCameraAvailable
        locationIndicatorsEnabled = privacyItemController.locationAvailable
        privacyItemController.addCallback(picCallback)
    }

    fun stopListening() {
        listening = false
        privacyItemController.removeCallback(picCallback)
        privacyChipLogged = false
    }

    private fun setChipVisibility(visible: Boolean) {
        if (visible && getChipEnabled()) {
            privacyLogger.logChipVisible(true)
            // Makes sure that the chip is logged as viewed at most once each time QS is opened
            // mListening makes sure that the callback didn't return after the user closed QS
            if (!privacyChipLogged && listening) {
                privacyChipLogged = true
                uiEventLogger.log(PrivacyChipEvent.ONGOING_INDICATORS_CHIP_VIEW)
            }
        } else {
            privacyLogger.logChipVisible(false)
        }

        privacyChip.visibility = if (visible) View.VISIBLE else View.GONE
        chipVisibilityListener?.onChipVisibilityRefreshed(visible)
    }

    private fun updatePrivacyIconSlots() {
        if (getChipEnabled()) {
            if (micCameraIndicatorsEnabled) {
                iconContainer.addIgnoredSlot(cameraSlot)
                iconContainer.addIgnoredSlot(micSlot)
            } else {
                iconContainer.removeIgnoredSlot(cameraSlot)
                iconContainer.removeIgnoredSlot(micSlot)
            }
            if (locationIndicatorsEnabled) {
                iconContainer.addIgnoredSlot(locationSlot)
            } else {
                iconContainer.removeIgnoredSlot(locationSlot)
            }
        } else {
            iconContainer.removeIgnoredSlot(cameraSlot)
            iconContainer.removeIgnoredSlot(micSlot)
            iconContainer.removeIgnoredSlot(locationSlot)
        }
    }
}
