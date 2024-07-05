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

package com.android.systemui.communal.widgets

import android.content.Context
import android.content.Intent
import com.android.systemui.communal.widgets.EditWidgetsActivity.Companion.EXTRA_PRESELECTED_KEY
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.plugins.ActivityStarter
import javax.inject.Inject

interface EditWidgetsActivityStarter {
    fun startActivity(preselectedKey: String? = null)
}

class EditWidgetsActivityStarterImpl
@Inject
constructor(
    @Application private val applicationContext: Context,
    private val activityStarter: ActivityStarter,
) : EditWidgetsActivityStarter {

    override fun startActivity(preselectedKey: String?) {
        activityStarter.startActivityDismissingKeyguard(
            Intent(applicationContext, EditWidgetsActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                .apply { preselectedKey?.let { putExtra(EXTRA_PRESELECTED_KEY, preselectedKey) } },
            /* onlyProvisioned = */ true,
            /* dismissShade = */ true,
        )
    }
}
