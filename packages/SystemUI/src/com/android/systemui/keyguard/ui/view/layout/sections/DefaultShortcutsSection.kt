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
 *
 */

package com.android.systemui.keyguard.ui.view.layout.sections

import android.content.res.Resources
import androidx.constraintlayout.widget.ConstraintSet
import androidx.constraintlayout.widget.ConstraintSet.BOTTOM
import androidx.constraintlayout.widget.ConstraintSet.LEFT
import androidx.constraintlayout.widget.ConstraintSet.PARENT_ID
import androidx.constraintlayout.widget.ConstraintSet.RIGHT
import com.android.systemui.R
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.keyguard.data.repository.KeyguardSection
import javax.inject.Inject

class DefaultShortcutsSection @Inject constructor(@Main private val resources: Resources) :
    KeyguardSection {
    override fun apply(constraintSet: ConstraintSet) {
        val width = resources.getDimensionPixelSize(R.dimen.keyguard_affordance_fixed_width)
        val height = resources.getDimensionPixelSize(R.dimen.keyguard_affordance_fixed_height)
        val horizontalOffsetMargin =
            resources.getDimensionPixelSize(R.dimen.keyguard_affordance_horizontal_offset)
        val verticalOffsetMargin =
            resources.getDimensionPixelSize(R.dimen.keyguard_affordance_vertical_offset)

        constraintSet.apply {
            constrainWidth(R.id.start_button, width)
            constrainHeight(R.id.start_button, height)
            connect(R.id.start_button, LEFT, PARENT_ID, LEFT, horizontalOffsetMargin)
            connect(R.id.start_button, BOTTOM, PARENT_ID, BOTTOM, verticalOffsetMargin)

            constrainWidth(R.id.end_button, width)
            constrainHeight(R.id.end_button, height)
            connect(R.id.end_button, RIGHT, PARENT_ID, RIGHT, horizontalOffsetMargin)
            connect(R.id.end_button, BOTTOM, PARENT_ID, BOTTOM, verticalOffsetMargin)
        }
    }
}
