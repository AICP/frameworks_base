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

package com.android.systemui.qs.ui.composable

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.android.compose.animation.scene.ElementKey
import com.android.compose.animation.scene.MovableElementScenePicker
import com.android.compose.animation.scene.SceneScope
import com.android.compose.animation.scene.TransitionState
import com.android.compose.animation.scene.ValueKey
import com.android.compose.modifiers.thenIf
import com.android.systemui.qs.ui.adapter.QSSceneAdapter
import com.android.systemui.qs.ui.adapter.QSSceneAdapter.State.Companion.Collapsing
import com.android.systemui.qs.ui.adapter.QSSceneAdapter.State.Expanding
import com.android.systemui.qs.ui.adapter.QSSceneAdapter.State.Unsquishing
import com.android.systemui.scene.shared.model.Scenes

object QuickSettings {
    private val SCENES =
        setOf(
            Scenes.QuickSettings,
            Scenes.Shade,
        )

    object Elements {
        val Content =
            ElementKey("QuickSettingsContent", scenePicker = MovableElementScenePicker(SCENES))
        val FooterActions = ElementKey("QuickSettingsFooterActions")
    }

    object SharedValues {
        val TilesSquishiness = ValueKey("QuickSettingsTileSquishiness")
        object SquishinessValues {
            val Default = 1f
            val LockscreenSceneStarting = 0f
            val GoneSceneStarting = 0.3f
        }
    }
}

private fun SceneScope.stateForQuickSettingsContent(
    squishiness: Float = QuickSettings.SharedValues.SquishinessValues.Default
): QSSceneAdapter.State {
    return when (val transitionState = layoutState.transitionState) {
        is TransitionState.Idle -> {
            when (transitionState.currentScene) {
                Scenes.Shade -> QSSceneAdapter.State.QQS
                Scenes.QuickSettings -> QSSceneAdapter.State.QS
                else -> QSSceneAdapter.State.CLOSED
            }
        }
        is TransitionState.Transition ->
            with(transitionState) {
                when {
                    fromScene == Scenes.Shade && toScene == Scenes.QuickSettings ->
                        Expanding(progress)
                    fromScene == Scenes.QuickSettings && toScene == Scenes.Shade ->
                        Collapsing(progress)
                    fromScene == Scenes.Shade || toScene == Scenes.Shade -> Unsquishing(squishiness)
                    fromScene == Scenes.QuickSettings || toScene == Scenes.QuickSettings -> {
                        QSSceneAdapter.State.QS
                    }
                    else ->
                        error(
                            "Bad transition for QuickSettings: fromScene=$fromScene," +
                                " toScene=$toScene"
                        )
                }
            }
    }
}

/**
 * This composable will show QuickSettingsContent in the correct state (as determined by its
 * [SceneScope]).
 *
 * If adding to scenes not in:
 * * QuickSettingsScene
 * * ShadeScene
 *
 * amend:
 * * [stateForQuickSettingsContent],
 * * [QuickSettings.SCENES],
 * * this doc.
 */
@Composable
fun SceneScope.QuickSettings(
    qsSceneAdapter: QSSceneAdapter,
    heightProvider: () -> Int,
    modifier: Modifier = Modifier,
    squishiness: Float = QuickSettings.SharedValues.SquishinessValues.Default,
) {
    val contentState = stateForQuickSettingsContent(squishiness)

    MovableElement(
        key = QuickSettings.Elements.Content,
        modifier =
            modifier.fillMaxWidth().layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                // Use the height of the correct view based on the scene it is being composed in
                val height = heightProvider()

                layout(placeable.width, height) { placeable.placeRelative(0, 0) }
            }
    ) {
        content { QuickSettingsContent(qsSceneAdapter = qsSceneAdapter, contentState) }
    }
}

@Composable
private fun QuickSettingsContent(
    qsSceneAdapter: QSSceneAdapter,
    state: QSSceneAdapter.State,
    modifier: Modifier = Modifier,
) {
    val qsView by qsSceneAdapter.qsView.collectAsState(null)
    val isCustomizing by qsSceneAdapter.isCustomizing.collectAsState()
    QuickSettingsTheme {
        val context = LocalContext.current

        LaunchedEffect(key1 = context) {
            if (qsView == null) {
                qsSceneAdapter.inflate(context)
            }
        }
        qsView?.let { view ->
            Box(
                modifier =
                    modifier.fillMaxWidth().thenIf(isCustomizing) { Modifier.fillMaxHeight() }
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxWidth(),
                    factory = { _ ->
                        qsSceneAdapter.setState(state)
                        view
                    },
                    update = { qsSceneAdapter.setState(state) }
                )
            }
        }
    }
}
