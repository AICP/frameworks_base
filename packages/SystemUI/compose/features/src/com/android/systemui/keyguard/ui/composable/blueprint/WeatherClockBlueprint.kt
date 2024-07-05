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

package com.android.systemui.keyguard.ui.composable.blueprint

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import com.android.compose.animation.scene.SceneScope
import com.android.compose.modifiers.padding
import com.android.systemui.Flags
import com.android.systemui.keyguard.domain.interactor.KeyguardBlueprintInteractor.Companion.SPLIT_SHADE_WEATHER_CLOCK_BLUEPRINT_ID
import com.android.systemui.keyguard.domain.interactor.KeyguardBlueprintInteractor.Companion.WEATHER_CLOCK_BLUEPRINT_ID
import com.android.systemui.keyguard.domain.interactor.KeyguardClockInteractor
import com.android.systemui.keyguard.ui.composable.LockscreenLongPress
import com.android.systemui.keyguard.ui.composable.section.AmbientIndicationSection
import com.android.systemui.keyguard.ui.composable.section.BottomAreaSection
import com.android.systemui.keyguard.ui.composable.section.LockSection
import com.android.systemui.keyguard.ui.composable.section.MediaCarouselSection
import com.android.systemui.keyguard.ui.composable.section.NotificationSection
import com.android.systemui.keyguard.ui.composable.section.SettingsMenuSection
import com.android.systemui.keyguard.ui.composable.section.SmartSpaceSection
import com.android.systemui.keyguard.ui.composable.section.StatusBarSection
import com.android.systemui.keyguard.ui.composable.section.WeatherClockSection
import com.android.systemui.keyguard.ui.viewmodel.LockscreenContentViewModel
import com.android.systemui.media.controls.ui.composable.MediaCarousel
import com.android.systemui.res.R
import com.android.systemui.shade.LargeScreenHeaderHelper
import dagger.Binds
import dagger.Module
import dagger.multibindings.IntoSet
import java.util.Optional
import javax.inject.Inject

class WeatherClockBlueprint
@Inject
constructor(
    private val viewModel: LockscreenContentViewModel,
    private val statusBarSection: StatusBarSection,
    private val weatherClockSection: WeatherClockSection,
    private val smartSpaceSection: SmartSpaceSection,
    private val notificationSection: NotificationSection,
    private val lockSection: LockSection,
    private val ambientIndicationSectionOptional: Optional<AmbientIndicationSection>,
    private val bottomAreaSection: BottomAreaSection,
    private val settingsMenuSection: SettingsMenuSection,
    private val clockInteractor: KeyguardClockInteractor,
    private val mediaCarouselSection: MediaCarouselSection,
) : ComposableLockscreenSceneBlueprint {

    override val id: String = WEATHER_CLOCK_BLUEPRINT_ID
    @Composable
    override fun SceneScope.Content(modifier: Modifier) {
        val isUdfpsVisible = viewModel.isUdfpsVisible
        val burnIn = rememberBurnIn(clockInteractor)
        val resources = LocalContext.current.resources

        LockscreenLongPress(
            viewModel = viewModel.longPress,
            modifier = modifier,
        ) { onSettingsMenuPlaced ->
            Layout(
                content = {
                    // Constrained to above the lock icon.
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        with(statusBarSection) { StatusBar(modifier = Modifier.fillMaxWidth()) }
                        // TODO: Add weather clock for small and large clock
                        with(smartSpaceSection) {
                            SmartSpace(
                                burnInParams = burnIn.parameters,
                                onTopChanged = burnIn.onSmartspaceTopChanged,
                                modifier =
                                    Modifier.fillMaxWidth()
                                        .padding(
                                            top = { viewModel.getSmartSpacePaddingTop(resources) },
                                        )
                                        .padding(
                                            bottom =
                                                dimensionResource(
                                                    R.dimen.keyguard_status_view_bottom_margin
                                                ),
                                        ),
                            )
                        }

                        with(mediaCarouselSection) { MediaCarousel() }

                        if (viewModel.areNotificationsVisible(resources = resources)) {
                            with(notificationSection) {
                                Notifications(
                                    modifier = Modifier.fillMaxWidth().weight(weight = 1f)
                                )
                            }
                        }

                        if (!isUdfpsVisible && ambientIndicationSectionOptional.isPresent) {
                            with(ambientIndicationSectionOptional.get()) {
                                AmbientIndication(modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }

                    with(lockSection) { LockIcon() }

                    // Aligned to bottom and constrained to below the lock icon.
                    Column(modifier = Modifier.fillMaxWidth()) {
                        if (isUdfpsVisible && ambientIndicationSectionOptional.isPresent) {
                            with(ambientIndicationSectionOptional.get()) {
                                AmbientIndication(modifier = Modifier.fillMaxWidth())
                            }
                        }

                        with(bottomAreaSection) {
                            IndicationArea(modifier = Modifier.fillMaxWidth())
                        }
                    }

                    // Aligned to bottom and NOT constrained by the lock icon.
                    with(bottomAreaSection) {
                        Shortcut(isStart = true, applyPadding = true)
                        Shortcut(isStart = false, applyPadding = true)
                    }
                    with(settingsMenuSection) { SettingsMenu(onSettingsMenuPlaced) }
                },
                modifier = Modifier.fillMaxSize(),
            ) { measurables, constraints ->
                check(measurables.size == 6)
                val aboveLockIconMeasurable = measurables[0]
                val lockIconMeasurable = measurables[1]
                val belowLockIconMeasurable = measurables[2]
                val startShortcutMeasurable = measurables[3]
                val endShortcutMeasurable = measurables[4]
                val settingsMenuMeasurable = measurables[5]

                val noMinConstraints =
                    constraints.copy(
                        minWidth = 0,
                        minHeight = 0,
                    )
                val lockIconPlaceable = lockIconMeasurable.measure(noMinConstraints)
                val lockIconBounds =
                    IntRect(
                        left = lockIconPlaceable[BlueprintAlignmentLines.LockIcon.Left],
                        top = lockIconPlaceable[BlueprintAlignmentLines.LockIcon.Top],
                        right = lockIconPlaceable[BlueprintAlignmentLines.LockIcon.Right],
                        bottom = lockIconPlaceable[BlueprintAlignmentLines.LockIcon.Bottom],
                    )

                val aboveLockIconPlaceable =
                    aboveLockIconMeasurable.measure(
                        noMinConstraints.copy(maxHeight = lockIconBounds.top)
                    )
                val belowLockIconPlaceable =
                    belowLockIconMeasurable.measure(
                        noMinConstraints.copy(
                            maxHeight =
                                (constraints.maxHeight - lockIconBounds.bottom).coerceAtLeast(0)
                        )
                    )
                val startShortcutPleaceable = startShortcutMeasurable.measure(noMinConstraints)
                val endShortcutPleaceable = endShortcutMeasurable.measure(noMinConstraints)
                val settingsMenuPlaceable = settingsMenuMeasurable.measure(noMinConstraints)

                layout(constraints.maxWidth, constraints.maxHeight) {
                    aboveLockIconPlaceable.place(
                        x = 0,
                        y = 0,
                    )
                    lockIconPlaceable.place(
                        x = lockIconBounds.left,
                        y = lockIconBounds.top,
                    )
                    belowLockIconPlaceable.place(
                        x = 0,
                        y = constraints.maxHeight - belowLockIconPlaceable.height,
                    )
                    startShortcutPleaceable.place(
                        x = 0,
                        y = constraints.maxHeight - startShortcutPleaceable.height,
                    )
                    endShortcutPleaceable.place(
                        x = constraints.maxWidth - endShortcutPleaceable.width,
                        y = constraints.maxHeight - endShortcutPleaceable.height,
                    )
                    settingsMenuPlaceable.place(
                        x = (constraints.maxWidth - settingsMenuPlaceable.width) / 2,
                        y = constraints.maxHeight - settingsMenuPlaceable.height,
                    )
                }
            }
        }
    }
}

class SplitShadeWeatherClockBlueprint
@Inject
constructor(
    private val viewModel: LockscreenContentViewModel,
    private val statusBarSection: StatusBarSection,
    private val smartSpaceSection: SmartSpaceSection,
    private val notificationSection: NotificationSection,
    private val lockSection: LockSection,
    private val ambientIndicationSectionOptional: Optional<AmbientIndicationSection>,
    private val bottomAreaSection: BottomAreaSection,
    private val settingsMenuSection: SettingsMenuSection,
    private val clockInteractor: KeyguardClockInteractor,
    private val largeScreenHeaderHelper: LargeScreenHeaderHelper,
    private val weatherClockSection: WeatherClockSection,
    private val mediaCarouselSection: MediaCarouselSection,
) : ComposableLockscreenSceneBlueprint {
    override val id: String = SPLIT_SHADE_WEATHER_CLOCK_BLUEPRINT_ID

    @Composable
    override fun SceneScope.Content(modifier: Modifier) {
        val isUdfpsVisible = viewModel.isUdfpsVisible
        val burnIn = rememberBurnIn(clockInteractor)
        val resources = LocalContext.current.resources

        LockscreenLongPress(
            viewModel = viewModel.longPress,
            modifier = modifier,
        ) { onSettingsMenuPlaced ->
            Layout(
                content = {
                    // Constrained to above the lock icon.
                    Column(
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        with(statusBarSection) { StatusBar(modifier = Modifier.fillMaxWidth()) }
                        Row(
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            // TODO: Add weather clock for small and large clock
                            Column(
                                modifier = Modifier.fillMaxHeight().weight(weight = 1f),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                with(smartSpaceSection) {
                                    SmartSpace(
                                        burnInParams = burnIn.parameters,
                                        onTopChanged = burnIn.onSmartspaceTopChanged,
                                        modifier =
                                            Modifier.fillMaxWidth()
                                                .padding(
                                                    top = {
                                                        viewModel.getSmartSpacePaddingTop(resources)
                                                    },
                                                )
                                                .padding(
                                                    bottom =
                                                        dimensionResource(
                                                            R.dimen
                                                                .keyguard_status_view_bottom_margin
                                                        )
                                                ),
                                    )
                                }

                                with(mediaCarouselSection) { MediaCarousel() }
                            }
                            with(notificationSection) {
                                val splitShadeTopMargin: Dp =
                                    if (Flags.centralizedStatusBarHeightFix()) {
                                        largeScreenHeaderHelper.getLargeScreenHeaderHeight().dp
                                    } else {
                                        dimensionResource(
                                            id = R.dimen.large_screen_shade_header_height
                                        )
                                    }
                                Notifications(
                                    modifier =
                                        Modifier.fillMaxHeight()
                                            .weight(weight = 1f)
                                            .padding(top = splitShadeTopMargin)
                                )
                            }
                        }

                        if (!isUdfpsVisible && ambientIndicationSectionOptional.isPresent) {
                            with(ambientIndicationSectionOptional.get()) {
                                AmbientIndication(modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }

                    with(lockSection) { LockIcon() }

                    // Aligned to bottom and constrained to below the lock icon.
                    Column(modifier = Modifier.fillMaxWidth()) {
                        if (isUdfpsVisible && ambientIndicationSectionOptional.isPresent) {
                            with(ambientIndicationSectionOptional.get()) {
                                AmbientIndication(modifier = Modifier.fillMaxWidth())
                            }
                        }

                        with(bottomAreaSection) {
                            IndicationArea(modifier = Modifier.fillMaxWidth())
                        }
                    }

                    // Aligned to bottom and NOT constrained by the lock icon.
                    with(bottomAreaSection) {
                        Shortcut(isStart = true, applyPadding = true)
                        Shortcut(isStart = false, applyPadding = true)
                    }
                    with(settingsMenuSection) { SettingsMenu(onSettingsMenuPlaced) }
                },
                modifier = Modifier.fillMaxSize(),
            ) { measurables, constraints ->
                check(measurables.size == 6)
                val aboveLockIconMeasurable = measurables[0]
                val lockIconMeasurable = measurables[1]
                val belowLockIconMeasurable = measurables[2]
                val startShortcutMeasurable = measurables[3]
                val endShortcutMeasurable = measurables[4]
                val settingsMenuMeasurable = measurables[5]

                val noMinConstraints =
                    constraints.copy(
                        minWidth = 0,
                        minHeight = 0,
                    )
                val lockIconPlaceable = lockIconMeasurable.measure(noMinConstraints)
                val lockIconBounds =
                    IntRect(
                        left = lockIconPlaceable[BlueprintAlignmentLines.LockIcon.Left],
                        top = lockIconPlaceable[BlueprintAlignmentLines.LockIcon.Top],
                        right = lockIconPlaceable[BlueprintAlignmentLines.LockIcon.Right],
                        bottom = lockIconPlaceable[BlueprintAlignmentLines.LockIcon.Bottom],
                    )

                val aboveLockIconPlaceable =
                    aboveLockIconMeasurable.measure(
                        noMinConstraints.copy(maxHeight = lockIconBounds.top)
                    )
                val belowLockIconPlaceable =
                    belowLockIconMeasurable.measure(
                        noMinConstraints.copy(
                            maxHeight =
                                (constraints.maxHeight - lockIconBounds.bottom).coerceAtLeast(0)
                        )
                    )
                val startShortcutPleaceable = startShortcutMeasurable.measure(noMinConstraints)
                val endShortcutPleaceable = endShortcutMeasurable.measure(noMinConstraints)
                val settingsMenuPlaceable = settingsMenuMeasurable.measure(noMinConstraints)

                layout(constraints.maxWidth, constraints.maxHeight) {
                    aboveLockIconPlaceable.place(
                        x = 0,
                        y = 0,
                    )
                    lockIconPlaceable.place(
                        x = lockIconBounds.left,
                        y = lockIconBounds.top,
                    )
                    belowLockIconPlaceable.place(
                        x = 0,
                        y = constraints.maxHeight - belowLockIconPlaceable.height,
                    )
                    startShortcutPleaceable.place(
                        x = 0,
                        y = constraints.maxHeight - startShortcutPleaceable.height,
                    )
                    endShortcutPleaceable.place(
                        x = constraints.maxWidth - endShortcutPleaceable.width,
                        y = constraints.maxHeight - endShortcutPleaceable.height,
                    )
                    settingsMenuPlaceable.place(
                        x = (constraints.maxWidth - settingsMenuPlaceable.width) / 2,
                        y = constraints.maxHeight - settingsMenuPlaceable.height,
                    )
                }
            }
        }
    }
}

@Module
interface WeatherClockBlueprintModule {
    @Binds
    @IntoSet
    fun blueprint(blueprint: WeatherClockBlueprint): ComposableLockscreenSceneBlueprint
}

@Module
interface SplitShadeWeatherClockBlueprintModule {
    @Binds
    @IntoSet
    fun blueprint(blueprint: SplitShadeWeatherClockBlueprint): ComposableLockscreenSceneBlueprint
}
