/*
 * SPDX-FileCopyrightText: DerpFest AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.biometrics

import android.content.Context
import android.graphics.Canvas
import android.graphics.PixelFormat

/**
 * Draws udfps fingerprint if sensor isn't illuminating.
 */
class UdfpsFpIconDrawable(context: Context) : UdfpsIconDrawable(context) {
    override fun draw(canvas: Canvas) {
        val udfpsDrawable = getUdfpsDrawable()
        udfpsDrawable?.apply {
            setBounds(bounds)
            draw(canvas)
        }
    }

    override fun getOpacity(): Int {
        return PixelFormat.TRANSLUCENT
    }
}
