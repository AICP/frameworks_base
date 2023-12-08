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

package com.android.systemui.scene.shared.model

import dagger.Module
import dagger.Provides

@Module
object SceneContainerConfigModule {

    @Provides
    fun containerConfig(): SceneContainerConfig {
        return SceneContainerConfig(
            // Note that this list is in z-order. The first one is the bottom-most and the
            // last
            // one is top-most.
            sceneKeys =
                listOf(
                    SceneKey.Gone,
                    SceneKey.Lockscreen,
                    SceneKey.Bouncer,
                    SceneKey.Shade,
                    SceneKey.QuickSettings,
                ),
            initialSceneKey = SceneKey.Lockscreen,
        )
    }
}
