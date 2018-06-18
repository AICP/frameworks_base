/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.systemui.aicp.dagger;

import android.app.Service;

import com.android.systemui.omni.OmniSettingsService;
import com.android.systemui.omni.OmniSettingsServiceImpl;
import com.android.systemui.omni.OmniSystemUIService;

import com.android.systemui.qs.tiles.AicpExtrasTile;
import com.android.systemui.qs.tiles.AODTile;
import com.android.systemui.qs.tiles.CaffeineTile;
import com.android.systemui.qs.tiles.CompassTile;
import com.android.systemui.qs.tiles.DataSwitchTile;
import com.android.systemui.qs.tiles.ScreenshotTile;
import com.android.systemui.qs.tiles.HeadsUpTile;
import com.android.systemui.qs.tiles.MusicTile;
import com.android.systemui.qs.tiles.VolumeTile;
import com.android.systemui.qs.tiles.OnTheGoTile;

import com.android.systemui.qs.tileimpl.QSTileImpl;

import dagger.Binds;
import dagger.Module;
import dagger.multibindings.ClassKey;
import dagger.multibindings.IntoMap;
import dagger.multibindings.StringKey;


/** Dagger Module for code in the systemui package. */
@Module
public interface AicpModule {
    /** */
    @Binds
    OmniSettingsService provideOmniSettingsService(OmniSettingsServiceImpl impl);
    
    /** Inject Tiles */
    @Binds
    @IntoMap
    @StringKey(AODTile.TILE_SPEC)
    public abstract QSTileImpl<?> bindAODTile(AODTile aodTile);

    @Binds
    @IntoMap
    @StringKey(CaffeineTile.TILE_SPEC)
    public abstract QSTileImpl<?> bindCaffeineTile(CaffeineTile caffeineTile);

    @Binds
    @IntoMap
    @StringKey(CompassTile.TILE_SPEC)
    public abstract QSTileImpl<?> bindCompassTile(CompassTile compassTile);

    @Binds
    @IntoMap
    @StringKey(DataSwitchTile.TILE_SPEC)
    public abstract QSTileImpl<?> bindDataSwitchTile(DataSwitchTile dataSwitchTile);

    @Binds
    @IntoMap
    @StringKey(ScreenshotTile.TILE_SPEC)
    public abstract QSTileImpl<?> bindScreenshotTile(ScreenshotTile screenshotTile);

    /** Inject HeadsUpTile into tileMap in QSModule */
    @Binds
    @IntoMap
    @StringKey(HeadsUpTile.TILE_SPEC)
    public abstract QSTileImpl<?> bindHeadsUpTile(HeadsUpTile headsUpTile);

    @Binds
    @IntoMap
    @StringKey(AicpExtrasTile.TILE_SPEC)
    public abstract QSTileImpl<?> bindAicpExtrasTile(AicpExtrasTile aicp_extrasTile);

    @Binds
    @IntoMap
    @StringKey(MusicTile.TILE_SPEC)
    public abstract QSTileImpl<?> bindMusicTile(MusicTile musicTile);

    @Binds
    @IntoMap
    @StringKey(VolumeTile.TILE_SPEC)
    public abstract QSTileImpl<?> bindVolumeTile(VolumeTile volumeTile);

    @Binds
    @IntoMap
    @StringKey(OnTheGoTile.TILE_SPEC)
    public abstract QSTileImpl<?> bindOnTheGoTile(OnTheGoTile onthegoTile);

    /** */
    @Binds
    @IntoMap
    @ClassKey(OmniSystemUIService.class)
    public abstract Service bindOmniSystemUIService(OmniSystemUIService service);
}
