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
import androidx.compose.runtime.DisposableEffectResult
import androidx.compose.runtime.DisposableEffectScope
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.lerp
import com.android.compose.ui.util.lerp

/**
 * Animate a shared Int value.
 *
 * @see SceneScope.animateSharedValueAsState
 */
@Composable
fun SceneScope.animateSharedIntAsState(
    value: Int,
    key: ValueKey,
    element: ElementKey,
    canOverflow: Boolean = true,
): State<Int> {
    return animateSharedValueAsState(value, key, element, ::lerp, canOverflow)
}

/**
 * Animate a shared Float value.
 *
 * @see SceneScope.animateSharedValueAsState
 */
@Composable
fun SceneScope.animateSharedFloatAsState(
    value: Float,
    key: ValueKey,
    element: ElementKey,
    canOverflow: Boolean = true,
): State<Float> {
    return animateSharedValueAsState(value, key, element, ::lerp, canOverflow)
}

/**
 * Animate a shared Dp value.
 *
 * @see SceneScope.animateSharedValueAsState
 */
@Composable
fun SceneScope.animateSharedDpAsState(
    value: Dp,
    key: ValueKey,
    element: ElementKey,
    canOverflow: Boolean = true,
): State<Dp> {
    return animateSharedValueAsState(value, key, element, ::lerp, canOverflow)
}

/**
 * Animate a shared Color value.
 *
 * @see SceneScope.animateSharedValueAsState
 */
@Composable
fun SceneScope.animateSharedColorAsState(
    value: Color,
    key: ValueKey,
    element: ElementKey,
): State<Color> {
    return animateSharedValueAsState(value, key, element, ::lerp, canOverflow = false)
}

@Composable
internal fun <T> animateSharedValueAsState(
    layoutImpl: SceneTransitionLayoutImpl,
    scene: Scene,
    element: Element,
    key: ValueKey,
    value: T,
    lerp: (T, T, Float) -> T,
    canOverflow: Boolean,
): State<T> {
    val sharedValue = remember(key) { Element.SharedValue(key, value) }
    if (value != sharedValue.value) {
        sharedValue.value = value
    }

    DisposableEffect(element, scene, sharedValue) {
        addSharedValueToElement(element, scene, sharedValue)
    }

    return remember(layoutImpl, element, sharedValue, lerp, canOverflow) {
        derivedStateOf { computeValue(layoutImpl, element, sharedValue, lerp, canOverflow) }
    }
}

private fun <T> DisposableEffectScope.addSharedValueToElement(
    element: Element,
    scene: Scene,
    sharedValue: Element.SharedValue<T>,
): DisposableEffectResult {
    val sceneValues =
        element.sceneValues[scene.key] ?: error("Element $element is not present in $scene")
    val sharedValues = sceneValues.sharedValues

    sharedValues[sharedValue.key] = sharedValue
    return onDispose { sharedValues.remove(sharedValue.key) }
}

private fun <T> computeValue(
    layoutImpl: SceneTransitionLayoutImpl,
    element: Element,
    sharedValue: Element.SharedValue<T>,
    lerp: (T, T, Float) -> T,
    canOverflow: Boolean,
): T {
    val state = layoutImpl.state.transitionState
    if (
        state !is TransitionState.Transition ||
            state.fromScene == state.toScene ||
            !layoutImpl.isTransitionReady(state)
    ) {
        return sharedValue.value
    }

    fun sceneValue(scene: SceneKey): Element.SharedValue<T>? {
        val sceneValues = element.sceneValues[scene] ?: return null
        val value = sceneValues.sharedValues[sharedValue.key] ?: return null
        return value as Element.SharedValue<T>
    }

    val fromValue = sceneValue(state.fromScene)
    val toValue = sceneValue(state.toScene)
    return if (fromValue != null && toValue != null) {
        val progress = if (canOverflow) state.progress else state.progress.coerceIn(0f, 1f)
        lerp(fromValue.value, toValue.value, progress)
    } else if (fromValue != null) {
        fromValue.value
    } else if (toValue != null) {
        toValue.value
    } else {
        sharedValue.value
    }
}
