/*
 * Copyright 2023 The Android Open Source Project
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

package com.android.compose.animation.scene

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.IntermediateMeasureScope
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.intermediateLayout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.round
import com.android.compose.animation.scene.transformation.PropertyTransformation
import com.android.compose.modifiers.thenIf
import com.android.compose.ui.util.lerp

/** An element on screen, that can be composed in one or more scenes. */
internal class Element(val key: ElementKey) {
    /**
     * The last offset assigned to this element, relative to the SceneTransitionLayout containing
     * it.
     */
    var lastOffset = Offset.Unspecified

    /** The last size assigned to this element. */
    var lastSize = SizeUnspecified

    /** The last alpha assigned to this element. */
    var lastAlpha = 1f

    /** The mapping between a scene and the values/state this element has in that scene, if any. */
    val sceneValues = SnapshotStateMap<SceneKey, SceneValues>()

    override fun toString(): String {
        return "Element(key=$key)"
    }

    /** The target values of this element in a given scene. */
    class SceneValues {
        var size by mutableStateOf(SizeUnspecified)
        var offset by mutableStateOf(Offset.Unspecified)
        val sharedValues = SnapshotStateMap<ValueKey, SharedValue<*>>()
    }

    /** A shared value of this element. */
    class SharedValue<T>(val key: ValueKey, initialValue: T) {
        var value by mutableStateOf(initialValue)
    }

    companion object {
        val SizeUnspecified = IntSize(Int.MAX_VALUE, Int.MAX_VALUE)
    }
}

/** The implementation of [SceneScope.element]. */
@Composable
@OptIn(ExperimentalComposeUiApi::class)
internal fun Modifier.element(
    layoutImpl: SceneTransitionLayoutImpl,
    scene: Scene,
    key: ElementKey,
): Modifier {
    val sceneValues = remember(scene, key) { Element.SceneValues() }
    val element =
        // Get the element associated to [key] if it was already composed in another scene,
        // otherwise create it and add it to our Map<ElementKey, Element>. This is done inside a
        // withoutReadObservation() because there is no need to recompose when that map is mutated.
        Snapshot.withoutReadObservation {
            val element =
                layoutImpl.elements[key] ?: Element(key).also { layoutImpl.elements[key] = it }
            val previousValues = element.sceneValues[scene.key]
            if (previousValues == null) {
                element.sceneValues[scene.key] = sceneValues
            } else if (previousValues != sceneValues) {
                error("$key was composed multiple times in $scene")
            }

            element
        }

    DisposableEffect(scene, sceneValues, element) {
        onDispose {
            element.sceneValues.remove(scene.key)

            // This was the last scene this element was in, so remove it from the map.
            if (element.sceneValues.isEmpty()) {
                layoutImpl.elements.remove(element.key)
            }
        }
    }

    val alpha =
        remember(layoutImpl, element, scene) {
            derivedStateOf { elementAlpha(layoutImpl, element, scene) }
        }
    val isOpaque by remember(alpha) { derivedStateOf { alpha.value == 1f } }
    SideEffect {
        if (isOpaque && element.lastAlpha != 1f) {
            element.lastAlpha = 1f
        }
    }

    return drawWithContent {
            if (shouldDrawElement(layoutImpl, scene, element)) {
                drawContent()
            }
        }
        .modifierTransformations(layoutImpl, scene, element, sceneValues)
        .intermediateLayout { measurable, constraints ->
            val placeable =
                measure(layoutImpl, scene, element, sceneValues, measurable, constraints)
            layout(placeable.width, placeable.height) {
                place(layoutImpl, scene, element, sceneValues, placeable, placementScope = this)
            }
        }
        .thenIf(!isOpaque) {
            Modifier.graphicsLayer {
                val alpha = alpha.value
                this.alpha = alpha
                element.lastAlpha = alpha
            }
        }
        .testTag(key.name)
}

private fun shouldDrawElement(
    layoutImpl: SceneTransitionLayoutImpl,
    scene: Scene,
    element: Element,
): Boolean {
    val state = layoutImpl.state.transitionState

    // Always draw the element if there is no ongoing transition or if the element is not shared.
    if (
        state !is TransitionState.Transition ||
            state.fromScene == state.toScene ||
            !layoutImpl.isTransitionReady(state) ||
            state.fromScene !in element.sceneValues ||
            state.toScene !in element.sceneValues
    ) {
        return true
    }

    val otherScene =
        layoutImpl.scenes.getValue(
            if (scene.key == state.fromScene) {
                state.toScene
            } else {
                state.fromScene
            }
        )

    // When the element is shared, draw the one in the highest scene unless it is a background, i.e.
    // it is usually drawn below everything else.
    val isHighestScene = scene.zIndex > otherScene.zIndex
    return if (element.key.isBackground) {
        !isHighestScene
    } else {
        isHighestScene
    }
}

/**
 * Chain the [com.android.compose.animation.scene.transformation.ModifierTransformation] applied
 * throughout the current transition, if any.
 */
private fun Modifier.modifierTransformations(
    layoutImpl: SceneTransitionLayoutImpl,
    scene: Scene,
    element: Element,
    sceneValues: Element.SceneValues,
): Modifier {
    when (val state = layoutImpl.state.transitionState) {
        is TransitionState.Idle -> return this
        is TransitionState.Transition -> {
            val fromScene = state.fromScene
            val toScene = state.toScene
            if (fromScene == toScene) {
                // Same as idle.
                return this
            }

            return layoutImpl.transitions
                .transitionSpec(fromScene, state.toScene)
                .transformations(element.key)
                .modifier
                .fold(this) { modifier, transformation ->
                    with(transformation) {
                        modifier.transform(layoutImpl, scene, element, sceneValues)
                    }
                }
        }
    }
}

private fun elementAlpha(
    layoutImpl: SceneTransitionLayoutImpl,
    element: Element,
    scene: Scene
): Float {
    return computeValue(
            layoutImpl,
            scene,
            element,
            sceneValue = { 1f },
            transformation = { it.alpha },
            idleValue = 1f,
            currentValue = { 1f },
            lastValue = { element.lastAlpha },
            ::lerp,
        )
        .coerceIn(0f, 1f)
}

@OptIn(ExperimentalComposeUiApi::class)
private fun IntermediateMeasureScope.measure(
    layoutImpl: SceneTransitionLayoutImpl,
    scene: Scene,
    element: Element,
    sceneValues: Element.SceneValues,
    measurable: Measurable,
    constraints: Constraints,
): Placeable {
    // Update the size this element has in this scene when idle.
    val targetSizeInScene = lookaheadSize
    if (targetSizeInScene != sceneValues.size) {
        // TODO(b/290930950): Better handle when this changes to avoid instant size jumps.
        sceneValues.size = targetSizeInScene
    }

    // Some lambdas called (max once) by computeValue() will need to measure [measurable], in which
    // case we store the resulting placeable here to make sure the element is not measured more than
    // once.
    var maybePlaceable: Placeable? = null

    fun Placeable.size() = IntSize(width, height)

    val targetSize =
        computeValue(
            layoutImpl,
            scene,
            element,
            sceneValue = { it.size },
            transformation = { it.size },
            idleValue = lookaheadSize,
            currentValue = { measurable.measure(constraints).also { maybePlaceable = it }.size() },
            lastValue = {
                val lastSize = element.lastSize
                if (lastSize != Element.SizeUnspecified) {
                    lastSize
                } else {
                    measurable.measure(constraints).also { maybePlaceable = it }.size()
                }
            },
            ::lerp,
        )

    val placeable =
        maybePlaceable
            ?: measurable.measure(
                Constraints.fixed(
                    targetSize.width.coerceAtLeast(0),
                    targetSize.height.coerceAtLeast(0),
                )
            )

    element.lastSize = placeable.size()
    return placeable
}

@OptIn(ExperimentalComposeUiApi::class)
private fun IntermediateMeasureScope.place(
    layoutImpl: SceneTransitionLayoutImpl,
    scene: Scene,
    element: Element,
    sceneValues: Element.SceneValues,
    placeable: Placeable,
    placementScope: Placeable.PlacementScope,
) {
    with(placementScope) {
        // Update the offset (relative to the SceneTransitionLayout) this element has in this scene
        // when idle.
        val coords = coordinates!!
        val targetOffsetInScene = lookaheadScopeCoordinates.localLookaheadPositionOf(coords)
        if (targetOffsetInScene != sceneValues.offset) {
            // TODO(b/290930950): Better handle when this changes to avoid instant offset jumps.
            sceneValues.offset = targetOffsetInScene
        }

        val currentOffset = lookaheadScopeCoordinates.localPositionOf(coords, Offset.Zero)
        val targetOffset =
            computeValue(
                layoutImpl,
                scene,
                element,
                sceneValue = { it.offset },
                transformation = { it.offset },
                idleValue = targetOffsetInScene,
                currentValue = { currentOffset },
                lastValue = {
                    val lastValue = element.lastOffset
                    if (lastValue.isSpecified) {
                        lastValue
                    } else {
                        currentOffset
                    }
                },
                ::lerp,
            )

        element.lastOffset = targetOffset
        placeable.place((targetOffset - currentOffset).round())
    }
}

/**
 * Return the value that should be used depending on the current layout state and transition.
 *
 * Important: This function must remain inline because of all the lambda parameters. These lambdas
 * are necessary because getting some of them might require some computation, like measuring a
 * Measurable.
 *
 * @param layoutImpl the [SceneTransitionLayoutImpl] associated to [element].
 * @param scene the scene containing [element].
 * @param element the element being animated.
 * @param sceneValue the value being animated.
 * @param transformation the transformation associated to the value being animated.
 * @param idleValue the value when idle, i.e. when there is no transition happening.
 * @param currentValue the value that would be used if it is not transformed. Note that this is
 *   different than [idleValue] even if the value is not transformed directly because it could be
 *   impacted by the transformations on other elements, like a parent that is being translated or
 *   resized.
 * @param lastValue the last value that was used. This should be equal to [currentValue] if this is
 *   the first time the value is set.
 * @param lerp the linear interpolation function used to interpolate between two values of this
 *   value type.
 */
private inline fun <T> computeValue(
    layoutImpl: SceneTransitionLayoutImpl,
    scene: Scene,
    element: Element,
    sceneValue: (Element.SceneValues) -> T,
    transformation: (ElementTransformations) -> PropertyTransformation<T>?,
    idleValue: T,
    currentValue: () -> T,
    lastValue: () -> T,
    lerp: (T, T, Float) -> T,
): T {
    val state = layoutImpl.state.transitionState

    // There is no ongoing transition.
    if (state !is TransitionState.Transition || state.fromScene == state.toScene) {
        return idleValue
    }

    // A transition was started but it's not ready yet (not all elements have been composed/laid
    // out yet). Use the last value that was set, to make sure elements don't unexpectedly jump.
    if (!layoutImpl.isTransitionReady(state)) {
        return lastValue()
    }

    val fromScene = state.fromScene
    val toScene = state.toScene
    val fromValues = element.sceneValues[fromScene]
    val toValues = element.sceneValues[toScene]

    if (fromValues == null && toValues == null) {
        error("This should not happen, element $element is neither in $fromScene or $toScene")
    }

    // TODO(b/291053278): Handle overscroll correctly. We should probably coerce between [0f, 1f]
    // here and consume overflows at drawing time, somehow reusing Compose OverflowEffect or some
    // similar mechanism.
    val transitionProgress = state.progress

    // The element is shared: interpolate between the value in fromScene and the value in toScene.
    // TODO(b/290184746): Support non linear shared paths as well as a way to make sure that shared
    // elements follow the finger direction.
    if (fromValues != null && toValues != null) {
        return lerp(
            sceneValue(fromValues),
            sceneValue(toValues),
            transitionProgress,
        )
    }

    val transformation =
        transformation(
            layoutImpl.transitions.transitionSpec(fromScene, toScene).transformations(element.key)
        )
        // If there is no transformation explicitly associated to this element value, let's use
        // the value given by the system (like the current position and size given by the layout
        // pass).
        ?: return currentValue()

    // Get the transformed value, i.e. the target value at the beginning (for entering elements) or
    // end (for leaving elements) of the transition.
    val targetValue =
        transformation.transform(
            layoutImpl,
            scene,
            element,
            fromValues ?: toValues!!,
            state,
            idleValue,
        )

    // TODO(b/290184746): Make sure that we don't overflow transformations associated to a range.
    val rangeProgress = transformation.range?.progress(transitionProgress) ?: transitionProgress

    // Interpolate between the value at rest and the value before entering/after leaving.
    val isEntering = fromValues == null
    return if (isEntering) {
        lerp(targetValue, idleValue, rangeProgress)
    } else {
        lerp(idleValue, targetValue, rangeProgress)
    }
}
