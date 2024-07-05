package com.android.systemui.scene.ui.composable

import com.android.compose.animation.scene.transitions
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.scene.shared.model.TransitionKeys.CollapseShadeInstantly
import com.android.systemui.scene.shared.model.TransitionKeys.SlightlyFasterShadeCollapse
import com.android.systemui.scene.ui.composable.transitions.bouncerToGoneTransition
import com.android.systemui.scene.ui.composable.transitions.goneToQuickSettingsTransition
import com.android.systemui.scene.ui.composable.transitions.goneToShadeTransition
import com.android.systemui.scene.ui.composable.transitions.lockscreenToBouncerTransition
import com.android.systemui.scene.ui.composable.transitions.lockscreenToCommunalTransition
import com.android.systemui.scene.ui.composable.transitions.lockscreenToGoneTransition
import com.android.systemui.scene.ui.composable.transitions.lockscreenToQuickSettingsTransition
import com.android.systemui.scene.ui.composable.transitions.lockscreenToShadeTransition
import com.android.systemui.scene.ui.composable.transitions.shadeToQuickSettingsTransition

/**
 * Comprehensive definition of all transitions between scenes in [SceneContainer].
 *
 * Transitions are automatically reversible, so define only one transition per scene pair. By\
 * convention, use the more common transition direction when defining the pair order, e.g.
 * Lockscreen to Bouncer rather than Bouncer to Lockscreen.
 *
 * The actual transition DSL must be placed in a separate file under the package
 * [com.android.systemui.scene.ui.composable.transitions].
 *
 * Please keep the list sorted alphabetically.
 */
val SceneContainerTransitions = transitions {
    from(Scenes.Bouncer, to = Scenes.Gone) { bouncerToGoneTransition() }
    from(Scenes.Gone, to = Scenes.Shade) { goneToShadeTransition() }
    from(
        Scenes.Gone,
        to = Scenes.Shade,
        key = CollapseShadeInstantly,
    ) {
        goneToShadeTransition(durationScale = 0.0)
    }
    from(
        Scenes.Gone,
        to = Scenes.Shade,
        key = SlightlyFasterShadeCollapse,
    ) {
        goneToShadeTransition(durationScale = 0.9)
    }
    from(Scenes.Gone, to = Scenes.QuickSettings) { goneToQuickSettingsTransition() }
    from(Scenes.Lockscreen, to = Scenes.Bouncer) { lockscreenToBouncerTransition() }
    from(Scenes.Lockscreen, to = Scenes.Communal) { lockscreenToCommunalTransition() }
    from(Scenes.Lockscreen, to = Scenes.Shade) { lockscreenToShadeTransition() }
    from(
        Scenes.Lockscreen,
        to = Scenes.Shade,
        key = CollapseShadeInstantly,
    ) {
        lockscreenToShadeTransition(durationScale = 0.0)
    }
    from(
        Scenes.Lockscreen,
        to = Scenes.Shade,
        key = SlightlyFasterShadeCollapse,
    ) {
        lockscreenToShadeTransition(durationScale = 0.9)
    }
    from(Scenes.Lockscreen, to = Scenes.QuickSettings) { lockscreenToQuickSettingsTransition() }
    from(Scenes.Lockscreen, to = Scenes.Gone) { lockscreenToGoneTransition() }
    from(Scenes.Shade, to = Scenes.QuickSettings) { shadeToQuickSettingsTransition() }
}
