package com.android.systemui.qs

import android.content.Context
import android.view.View
import androidx.lifecycle.LifecycleOwner
import com.android.compose.theme.PlatformTheme
import com.android.compose.ui.platform.DensityAwareComposeView
import com.android.internal.policy.SystemBarUtils
import com.android.systemui.qs.footer.ui.compose.FooterActions
import com.android.systemui.qs.footer.ui.viewmodel.FooterActionsViewModel
import com.android.systemui.util.LargeScreenUtils.shouldUseLargeScreenShadeHeader

object QSUtils {

    /**
     * Gets the [R.dimen.qs_header_system_icons_area_height] unless we use large screen header.
     *
     * It's the same as [com.android.internal.R.dimen.quick_qs_offset_height] except for
     * sw600dp-land.
     */
    @JvmStatic
    fun getQsHeaderSystemIconsAreaHeight(context: Context): Int {
        return if (shouldUseLargeScreenShadeHeader(context.resources)) {
            // value should be 0 when using large screen shade header because it's not expandable
            0
        } else {
            SystemBarUtils.getQuickQsOffsetHeight(context)
        }
    }

    @JvmStatic
    fun createFooterActionsView(
        context: Context,
        viewModel: FooterActionsViewModel,
        qsVisibilityLifecycleOwner: LifecycleOwner,
    ): View {
        return DensityAwareComposeView(context).apply {
            setContent { PlatformTheme { FooterActions(viewModel, qsVisibilityLifecycleOwner) } }
        }
    }
}
