/*
 * Copyright (C) 2019 The Android Open Source Project
 *               2021 Paranoid Android
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
package com.android.systemui.biometrics

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.drawable.AnimationDrawable
import android.view.Gravity
import android.view.WindowManager
import android.view.WindowManager.LayoutParams
import android.widget.ImageView

import com.android.systemui.R

class FODAnimation(context: Context, y: Int) : ImageView(context) {
    private val animParams: LayoutParams = LayoutParams()
    private val recognizingAnim: AnimationDrawable?
    private var showing = false
    private var isKeyguard = false
    private var canUnlock = false

    init {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val animationSize = context.resources.getDimensionPixelSize(R.dimen.fod_animation_size)
        animParams.height = animationSize
        animParams.width = animationSize
        animParams.format = PixelFormat.TRANSLUCENT
        animParams.type = LayoutParams.TYPE_VOLUME_OVERLAY
        animParams.flags = (LayoutParams.FLAG_NOT_FOCUSABLE
                or LayoutParams.FLAG_NOT_TOUCH_MODAL
                or LayoutParams.FLAG_NOT_TOUCHABLE
                or LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        animParams.gravity = Gravity.TOP or Gravity.CENTER
        animParams.y = y - animationSize / 2
        scaleType = ScaleType.CENTER_INSIDE
        setBackgroundResource(R.drawable.fod_recognizing_animation)
        visibility = GONE
        recognizingAnim = background as AnimationDrawable
        windowManager.addView(this, animParams)
    }

    fun setAnimationKeyguard(state: Boolean) {
        isKeyguard = state
    }

    fun setCanUnlock(canUnlock: Boolean) {
        this.canUnlock = canUnlock
    }

    fun showFODAnimation() {
        if (!showing && isKeyguard && canUnlock) {
            showing = true
            visibility = VISIBLE
            recognizingAnim?.start()
        }
    }

    fun hideFODAnimation() {
        if (showing) {
            showing = false
            if (recognizingAnim != null) {
                clearAnimation()
                recognizingAnim.stop()
                recognizingAnim.selectDrawable(0)
            }
            visibility = GONE
        }
    }
}
