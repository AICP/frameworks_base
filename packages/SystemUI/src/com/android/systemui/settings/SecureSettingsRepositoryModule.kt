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

package com.android.systemui.settings

import android.content.ContentResolver
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.shared.settings.data.repository.SecureSettingsRepository
import com.android.systemui.shared.settings.data.repository.SecureSettingsRepositoryImpl
import dagger.Module
import dagger.Provides
import kotlinx.coroutines.CoroutineDispatcher

@Module
object SecureSettingsRepositoryModule {
    @JvmStatic
    @Provides
    @SysUISingleton
    fun provideSecureSettingsRepository(
        contentResolver: ContentResolver,
        @Background backgroundDispatcher: CoroutineDispatcher,
    ): SecureSettingsRepository =
        SecureSettingsRepositoryImpl(contentResolver, backgroundDispatcher)
}
