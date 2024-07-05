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

package com.android.systemui.volume.panel.ui.composable

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.testKosmos
import com.android.systemui.volume.panel.componentByKey
import com.android.systemui.volume.panel.mockVolumePanelUiComponentProvider
import com.android.systemui.volume.panel.shared.model.VolumePanelComponentKey
import com.google.common.truth.Truth
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class ComponentsFactoryTest : SysuiTestCase() {

    private val kosmos = testKosmos()

    private lateinit var underTest: ComponentsFactory

    private fun initUnderTest() {
        underTest = ComponentsFactory(kosmos.componentByKey)
    }

    @Test
    fun existingComponent_created() {
        kosmos.componentByKey = mapOf(TEST_COMPONENT to kosmos.mockVolumePanelUiComponentProvider)
        initUnderTest()

        val component = underTest.createComponent(TEST_COMPONENT)

        Truth.assertThat(component).isNotNull()
    }

    @Test(expected = IllegalArgumentException::class)
    fun componentAbsence_throws() {
        kosmos.componentByKey = emptyMap()
        initUnderTest()

        underTest.createComponent(TEST_COMPONENT)
    }

    private companion object {
        const val TEST_COMPONENT: VolumePanelComponentKey = "test_component"
    }
}
