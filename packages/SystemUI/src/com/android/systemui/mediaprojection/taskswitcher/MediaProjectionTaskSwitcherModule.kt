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

package com.android.systemui.mediaprojection.taskswitcher

import com.android.systemui.mediaprojection.taskswitcher.data.repository.ActivityTaskManagerTasksRepository
import com.android.systemui.mediaprojection.taskswitcher.data.repository.MediaProjectionManagerRepository
import com.android.systemui.mediaprojection.taskswitcher.data.repository.MediaProjectionRepository
import com.android.systemui.mediaprojection.taskswitcher.data.repository.TasksRepository
import dagger.Binds
import dagger.Module

@Module
interface MediaProjectionTaskSwitcherModule {

    @Binds fun mediaRepository(impl: MediaProjectionManagerRepository): MediaProjectionRepository

    @Binds fun tasksRepository(impl: ActivityTaskManagerTasksRepository): TasksRepository
}
