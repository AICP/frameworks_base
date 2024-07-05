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

package com.android.systemui.qs.external

import android.content.ComponentName
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.util.mockito.mock

var Kosmos.componentName: ComponentName by Kosmos.Fixture()

/** Returns mocks */
var Kosmos.tileLifecycleManagerFactory: TileLifecycleManager.Factory by Kosmos.Fixture { mock {} }

val Kosmos.iQSTileService: FakeIQSTileService by Kosmos.Fixture { FakeIQSTileService() }
val Kosmos.tileServiceManagerFacade: FakeTileServiceManagerFacade by
    Kosmos.Fixture { FakeTileServiceManagerFacade(iQSTileService) }

val Kosmos.tileServiceManager: TileServiceManager by
    Kosmos.Fixture { tileServiceManagerFacade.tileServiceManager }

val Kosmos.tileServicesFacade: FakeTileServicesFacade by
    Kosmos.Fixture { (FakeTileServicesFacade(tileServiceManager)) }
val Kosmos.tileServices: TileServices by Kosmos.Fixture { tileServicesFacade.tileServices }
