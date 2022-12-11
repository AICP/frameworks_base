/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.systemui.shade

import android.testing.AndroidTestingRunner
import androidx.constraintlayout.widget.ConstraintSet
import androidx.constraintlayout.widget.ConstraintSet.PARENT_ID
import androidx.constraintlayout.widget.ConstraintSet.START
import androidx.test.filters.SmallTest
import com.android.systemui.R
import com.android.systemui.SysuiTestCase
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidTestingRunner::class)
class CombinedShadeHeaderConstraintsTest : SysuiTestCase() {

    private lateinit var qqsConstraint: ConstraintSet
    private lateinit var qsConstraint: ConstraintSet
    private lateinit var largeScreenConstraint: ConstraintSet

    @Before
    fun setUp() {
        qqsConstraint = ConstraintSet().apply {
            load(context, context.resources.getXml(R.xml.qqs_header))
        }
        qsConstraint = ConstraintSet().apply {
            load(context, context.resources.getXml(R.xml.qs_header_new))
        }
        largeScreenConstraint = ConstraintSet().apply {
            load(context, context.resources.getXml(R.xml.large_screen_shade_header))
        }
    }

    @Test
    fun testEdgeElementsAlignedWithGuide_qqs() {
        with(qqsConstraint) {
            assertThat(getConstraint(R.id.clock).layout.startToStart).isEqualTo(R.id.begin_guide)
            assertThat(getConstraint(R.id.clock).layout.horizontalBias).isEqualTo(0f)

            assertThat(getConstraint(R.id.batteryRemainingIcon).layout.endToEnd)
                .isEqualTo(R.id.end_guide)
            assertThat(getConstraint(R.id.batteryRemainingIcon).layout.horizontalBias)
                .isEqualTo(1f)

            assertThat(getConstraint(R.id.privacy_container).layout.endToEnd)
                .isEqualTo(R.id.end_guide)
            assertThat(getConstraint(R.id.privacy_container).layout.horizontalBias)
                .isEqualTo(1f)
        }
    }

    @Test
    fun testClockScale() {
        with(qqsConstraint.getConstraint(R.id.clock)) {
            assertThat(transform.scaleX).isEqualTo(1f)
            assertThat(transform.scaleY).isEqualTo(1f)
        }
        with(qsConstraint.getConstraint(R.id.clock)) {
            assertThat(transform.scaleX).isGreaterThan(1f)
            assertThat(transform.scaleY).isGreaterThan(1f)
        }
    }

    @Test
    fun testEdgeElementsAlignedWithEdgeOrGuide_qs() {
        with(qsConstraint) {
            assertThat(getConstraint(R.id.clock).layout.startToStart).isEqualTo(PARENT_ID)
            assertThat(getConstraint(R.id.clock).layout.horizontalBias).isEqualTo(0f)

            assertThat(getConstraint(R.id.date).layout.startToStart).isEqualTo(PARENT_ID)
            assertThat(getConstraint(R.id.date).layout.horizontalBias).isEqualTo(0f)

            assertThat(getConstraint(R.id.batteryRemainingIcon).layout.endToEnd)
                .isEqualTo(PARENT_ID)
            assertThat(getConstraint(R.id.batteryRemainingIcon).layout.horizontalBias)
                .isEqualTo(1f)

            assertThat(getConstraint(R.id.privacy_container).layout.endToEnd)
                .isEqualTo(R.id.end_guide)
            assertThat(getConstraint(R.id.privacy_container).layout.horizontalBias).isEqualTo(1f)
        }
    }

    @Test
    fun testEdgeElementsAlignedWithEdge_largeScreen() {
        with(largeScreenConstraint) {
            assertThat(getConstraint(R.id.clock).layout.startToStart).isEqualTo(PARENT_ID)
            assertThat(getConstraint(R.id.clock).layout.horizontalBias).isEqualTo(0f)

            assertThat(getConstraint(R.id.privacy_container).layout.endToEnd).isEqualTo(PARENT_ID)
            assertThat(getConstraint(R.id.privacy_container).layout.horizontalBias).isEqualTo(1f)
        }
    }

    @Test
    fun testCarrierAlpha() {
        assertThat(qqsConstraint.getConstraint(R.id.carrier_group).propertySet.alpha).isEqualTo(0f)
        assertThat(qsConstraint.getConstraint(R.id.carrier_group).propertySet.alpha).isEqualTo(1f)
        assertThat(largeScreenConstraint.getConstraint(R.id.carrier_group).propertySet.alpha)
            .isEqualTo(1f)
    }

    @Test
    fun testPrivacyChipVisibilityConstraints_notVisible() {
        val changes = CombinedShadeHeadersConstraintManagerImpl
            .privacyChipVisibilityConstraints(false)
        changes()

        with(qqsConstraint) {
            assertThat(getConstraint(R.id.statusIcons).propertySet.alpha).isEqualTo(1f)
            assertThat(getConstraint(R.id.batteryRemainingIcon).propertySet.alpha).isEqualTo(1f)
        }

        with(qsConstraint) {
            assertThat(getConstraint(R.id.statusIcons).propertySet.alpha).isEqualTo(1f)
            assertThat(getConstraint(R.id.batteryRemainingIcon).propertySet.alpha).isEqualTo(1f)
        }

        with(largeScreenConstraint) {
            assertThat(getConstraint(R.id.statusIcons).propertySet.alpha).isEqualTo(1f)
            assertThat(getConstraint(R.id.batteryRemainingIcon).propertySet.alpha).isEqualTo(1f)
        }
    }

    @Test
    fun testPrivacyChipVisibilityConstraints_visible() {
        val changes = CombinedShadeHeadersConstraintManagerImpl
            .privacyChipVisibilityConstraints(true)
        changes()

        with(qqsConstraint) {
            assertThat(getConstraint(R.id.statusIcons).propertySet.alpha).isEqualTo(0f)
            assertThat(getConstraint(R.id.batteryRemainingIcon).propertySet.alpha).isEqualTo(0f)
        }

        with(qsConstraint) {
            assertThat(getConstraint(R.id.statusIcons).propertySet.alpha).isEqualTo(1f)
            assertThat(getConstraint(R.id.batteryRemainingIcon).propertySet.alpha).isEqualTo(1f)
        }

        with(largeScreenConstraint) {
            assertThat(getConstraint(R.id.statusIcons).propertySet.alpha).isEqualTo(1f)
            assertThat(getConstraint(R.id.batteryRemainingIcon).propertySet.alpha).isEqualTo(1f)
        }
    }

    @Test
    fun testEmptyCutoutConstraints() {
        val changes = CombinedShadeHeadersConstraintManagerImpl.emptyCutoutConstraints()
        changes()

        // QS and Large Screen don't change with cutouts.
        assertThat(changes.qsConstraintsChanges).isNull()
        assertThat(changes.largeScreenConstraintsChanges).isNull()

        with(qqsConstraint) {
            // In this case, the date is constrained on the end by a Barrier determined by either
            // privacy or statusIcons
            assertThat(getConstraint(R.id.date).layout.endToStart).isEqualTo(R.id.barrier)
            assertThat(getConstraint(R.id.statusIcons).layout.startToEnd).isEqualTo(R.id.date)
            assertThat(getConstraint(R.id.privacy_container).layout.startToEnd).isEqualTo(R.id.date)
            assertThat(getConstraint(R.id.barrier).layout.mReferenceIds).asList().containsExactly(
                R.id.statusIcons,
                R.id.privacy_container
            )
            assertThat(getConstraint(R.id.barrier).layout.mBarrierDirection).isEqualTo(START)
        }
    }

    @Test
    fun testGuidesAreSetInCorrectPosition_largeCutoutSmallerPadding() {
        val cutoutStart = 100
        val padding = 10
        val cutoutEnd = 30
        val changes = CombinedShadeHeadersConstraintManagerImpl.edgesGuidelinesConstraints(
            cutoutStart,
            padding,
            cutoutEnd,
            padding
        )
        changes()

        with(qqsConstraint) {
            assertThat(getConstraint(R.id.begin_guide).layout.guideBegin)
                .isEqualTo(cutoutStart - padding)
            assertThat(getConstraint(R.id.end_guide).layout.guideEnd)
                .isEqualTo(cutoutEnd - padding)
        }

        with(qsConstraint) {
            assertThat(getConstraint(R.id.begin_guide).layout.guideBegin)
                .isEqualTo(cutoutStart - padding)
            assertThat(getConstraint(R.id.end_guide).layout.guideEnd)
                .isEqualTo(cutoutEnd - padding)
        }

        assertThat(changes.largeScreenConstraintsChanges).isNull()
    }

    @Test
    fun testGuidesAreSetInCorrectPosition_smallCutoutLargerPadding() {
        val cutoutStart = 5
        val padding = 10
        val cutoutEnd = 10

        val changes = CombinedShadeHeadersConstraintManagerImpl.edgesGuidelinesConstraints(
            cutoutStart,
            padding,
            cutoutEnd,
            padding
        )
        changes()

        with(qqsConstraint) {
            assertThat(getConstraint(R.id.begin_guide).layout.guideBegin).isEqualTo(0)
            assertThat(getConstraint(R.id.end_guide).layout.guideEnd).isEqualTo(0)
        }

        with(qsConstraint) {
            assertThat(getConstraint(R.id.begin_guide).layout.guideBegin).isEqualTo(0)
            assertThat(getConstraint(R.id.end_guide).layout.guideEnd).isEqualTo(0)
        }

        assertThat(changes.largeScreenConstraintsChanges).isNull()
    }

    @Test
    fun testCenterCutoutConstraints_ltr() {
        val offsetFromEdge = 400
        val rtl = false

        val changes = CombinedShadeHeadersConstraintManagerImpl
            .centerCutoutConstraints(rtl, offsetFromEdge)
        changes()

        // In LTR, center_left is towards the start and center_right is towards the end
        with(qqsConstraint) {
            assertThat(getConstraint(R.id.center_left).layout.guideBegin).isEqualTo(offsetFromEdge)
            assertThat(getConstraint(R.id.center_right).layout.guideEnd).isEqualTo(offsetFromEdge)
            assertThat(getConstraint(R.id.date).layout.endToStart).isEqualTo(R.id.center_left)
            assertThat(getConstraint(R.id.statusIcons).layout.startToEnd)
                .isEqualTo(R.id.center_right)
            assertThat(getConstraint(R.id.privacy_container).layout.startToEnd)
                .isEqualTo(R.id.center_right)
        }

        with(qsConstraint) {
            assertThat(getConstraint(R.id.center_left).layout.guideBegin).isEqualTo(offsetFromEdge)
            assertThat(getConstraint(R.id.center_right).layout.guideEnd).isEqualTo(offsetFromEdge)

            assertThat(getConstraint(R.id.date).layout.endToStart).isNotEqualTo(R.id.center_left)
            assertThat(getConstraint(R.id.date).layout.endToStart).isNotEqualTo(R.id.center_right)

            assertThat(getConstraint(R.id.statusIcons).layout.startToEnd)
                .isNotEqualTo(R.id.center_left)
            assertThat(getConstraint(R.id.statusIcons).layout.startToEnd)
                .isNotEqualTo(R.id.center_right)

            assertThat(getConstraint(R.id.privacy_container).layout.startToEnd)
                .isEqualTo(R.id.center_right)
        }

        assertThat(changes.largeScreenConstraintsChanges).isNull()
    }

    @Test
    fun testCenterCutoutConstraints_rtl() {
        val offsetFromEdge = 400
        val rtl = true

        val changes = CombinedShadeHeadersConstraintManagerImpl
            .centerCutoutConstraints(rtl, offsetFromEdge)
        changes()

        // In RTL, center_left is towards the end and center_right is towards the start
        with(qqsConstraint) {
            assertThat(getConstraint(R.id.center_left).layout.guideEnd).isEqualTo(offsetFromEdge)
            assertThat(getConstraint(R.id.center_right).layout.guideBegin).isEqualTo(offsetFromEdge)
            assertThat(getConstraint(R.id.date).layout.endToStart).isEqualTo(R.id.center_right)
            assertThat(getConstraint(R.id.statusIcons).layout.startToEnd)
                .isEqualTo(R.id.center_left)
            assertThat(getConstraint(R.id.privacy_container).layout.startToEnd)
                .isEqualTo(R.id.center_left)
        }

        with(qsConstraint) {
            assertThat(getConstraint(R.id.center_left).layout.guideEnd).isEqualTo(offsetFromEdge)
            assertThat(getConstraint(R.id.center_right).layout.guideBegin).isEqualTo(offsetFromEdge)

            assertThat(getConstraint(R.id.date).layout.endToStart).isNotEqualTo(R.id.center_left)
            assertThat(getConstraint(R.id.date).layout.endToStart).isNotEqualTo(R.id.center_right)

            assertThat(getConstraint(R.id.statusIcons).layout.startToEnd)
                .isNotEqualTo(R.id.center_left)
            assertThat(getConstraint(R.id.statusIcons).layout.startToEnd)
                .isNotEqualTo(R.id.center_right)

            assertThat(getConstraint(R.id.privacy_container).layout.startToEnd)
                .isEqualTo(R.id.center_left)
        }

        assertThat(changes.largeScreenConstraintsChanges).isNull()
    }

    private operator fun ConstraintsChanges.invoke() {
        qqsConstraintsChanges?.invoke(qqsConstraint)
        qsConstraintsChanges?.invoke(qsConstraint)
        largeScreenConstraintsChanges?.invoke(largeScreenConstraint)
    }
}
