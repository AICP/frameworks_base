/*
 * Copyright (C) 2023-2025 The LineageOS Project
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

package com.android.systemui.aicp

import com.android.systemui.qs.QsEventLogger
import com.android.systemui.qs.pipeline.shared.TileSpec
import com.android.systemui.qs.shared.model.TileCategory
import com.android.systemui.qs.tileimpl.QSTileImpl
import com.android.systemui.qs.tiles.AicpExtrasTile
import com.android.systemui.qs.tiles.AmbientDisplayTile
import com.android.systemui.qs.tiles.AODTile
import com.android.systemui.qs.tiles.CaffeineTile
import com.android.systemui.qs.tiles.CompassTile;
import com.android.systemui.qs.tiles.CPUInfoTile;
import com.android.systemui.qs.tiles.HeadsUpTile
import com.android.systemui.qs.tiles.MusicTile
import com.android.systemui.qs.tiles.OnTheGoTile;
import com.android.systemui.qs.tiles.PowerShareTile
import com.android.systemui.qs.tiles.ProfilesTile
import com.android.systemui.qs.tiles.ReadingModeTile
import com.android.systemui.qs.tiles.SyncTile
import com.android.systemui.qs.tiles.UsbTetherTile
import com.android.systemui.qs.tiles.VolumeTile
import com.android.systemui.qs.tiles.VpnTile
import com.android.systemui.qs.tiles.base.shared.model.QSTileConfig
import com.android.systemui.qs.tiles.base.shared.model.QSTileUIConfig
import com.android.systemui.res.R

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoMap
import dagger.multibindings.StringKey

@Module
interface AicpModule {
    /** Inject Aicp_extrasTile into tileMap in QSModule */
    @Binds
    @IntoMap
    @StringKey(AicpExtrasTile.TILE_SPEC)
    fun bindAicpExtrasTile(aicpExtrasTile: AicpExtrasTile): QSTileImpl<*>

    /** Inject AmbientDisplayTile into tileMap in QSModule */
    @Binds
    @IntoMap
    @StringKey(AmbientDisplayTile.TILE_SPEC)
    fun bindAmbientDisplayTile(ambientDisplayTile: AmbientDisplayTile): QSTileImpl<*>

    /** Inject AODTile into tileMap in QSModule */
    @Binds
    @IntoMap
    @StringKey(AODTile.TILE_SPEC)
    fun bindAODTile(aodTile: AODTile): QSTileImpl<*>

    /** Inject CaffeineTile into tileMap in QSModule */
    @Binds
    @IntoMap
    @StringKey(CaffeineTile.TILE_SPEC)
    fun bindCaffeineTile(caffeineTile: CaffeineTile): QSTileImpl<*>

    /** Inject CompassTile into tileMap in QSModule */
    @Binds
    @IntoMap
    @StringKey(CompassTile.TILE_SPEC)
    fun bindCompassTile(compassTile: CompassTile): QSTileImpl<*>

    /** Inject CPUInfoTile into tileMap in QSModule */
    @Binds
    @IntoMap
    @StringKey(CPUInfoTile.TILE_SPEC)
    fun bindCPUInfoTile(cpuinfoTile: CPUInfoTile): QSTileImpl<*>

    /** Inject HeadsUpTile into tileMap in QSModule */
    @Binds
    @IntoMap
    @StringKey(HeadsUpTile.TILE_SPEC)
    fun bindHeadsUpTile(headsUpTile: HeadsUpTile): QSTileImpl<*>

    /** Inject MusicTile into tileMap in QSModule */
    @Binds
    @IntoMap
    @StringKey(MusicTile.TILE_SPEC)
    fun bindMusicTile(musicTile: MusicTile): QSTileImpl<*>

    /** Inject PowerShareTile into tileMap in QSModule */
    @Binds
    @IntoMap
    @StringKey(PowerShareTile.TILE_SPEC)
    fun bindPowerShareTile(powerShareTile: PowerShareTile): QSTileImpl<*>

    /** Inject OnthGoTile into tileMap in QSModule */
    @Binds
    @IntoMap
    @StringKey(OnTheGoTile.TILE_SPEC)
    fun bindOnTheGoTile(onthegoTile: OnTheGoTile): QSTileImpl<*>

    /** Inject ProfilesTile into tileMap in QSModule */
    @Binds
    @IntoMap
    @StringKey(ProfilesTile.TILE_SPEC)
    fun bindProfilesTile(profilesTile: ProfilesTile): QSTileImpl<*>

    /** Inject ReadingModeTile into tileMap in QSModule */
    @Binds
    @IntoMap
    @StringKey(ReadingModeTile.TILE_SPEC)
    fun bindReadingModeTile(readingModeTile: ReadingModeTile): QSTileImpl<*>

    /** Inject SyncTile into tileMap in QSModule */
    @Binds
    @IntoMap
    @StringKey(SyncTile.TILE_SPEC)
    fun bindSyncTile(syncTile: SyncTile): QSTileImpl<*>

    /** Inject UsbTetherTile into tileMap in QSModule */
    @Binds
    @IntoMap
    @StringKey(UsbTetherTile.TILE_SPEC)
    fun bindUsbTetherTile(usbTetherTile: UsbTetherTile): QSTileImpl<*>

    /** Inject VpnTile into tileMap in QSModule */
    @Binds
    @IntoMap
    @StringKey(VpnTile.TILE_SPEC)
    fun bindVpnTile(vpnTile: VpnTile): QSTileImpl<*>

    /** Inject VolumeTile into tileMap in QSModule */
    @Binds
    @IntoMap
    @StringKey(VolumeTile.TILE_SPEC)
    fun bindVolumeTile(volumeTile: VolumeTile): QSTileImpl<*>

    companion object {
        const val AICP_EXTRAS_TILE_SPEC = "aicp_extras"
        const val AMBIENT_DISPLAY_TILE_SPEC = "ambient_display"
        const val AOD_TILE_SPEC = "aod"
        const val CAFFEINE_TILE_SPEC = "caffeine"
        const val COMPASS_TILE_SPEC = "compass"
        const val CPUINFO_TILE_SPEC = "cpuinfo"
        const val HEADS_UP_TILE_SPEC = "heads_up"
        const val MUSIC_TILE_SPEC = "music"
        const val ONTHEGO_TILE_SPEC = "onthego"
        const val POWERSHARE_TILE_SPEC = "powershare"
        const val PROFILES_TILE_SPEC = "profiles"
        const val READING_MODE_TILE_SPEC = "reading_mode"
        const val SYNC_TILE_SPEC = "sync"
        const val USB_TETHER_TILE_SPEC = "usb_tether"
        const val VOLUME_PANEL_TILE_SPEC = "volume_panel"
        const val VPN_TILE_SPEC = "vpn"

        @Provides
        @IntoMap
        @StringKey(AICP_EXTRAS_TILE_SPEC)
        fun provideAicpExtrasTileConfig(uiEventLogger: QsEventLogger): QSTileConfig =
            QSTileConfig(
                tileSpec = TileSpec.create(AICP_EXTRAS_TILE_SPEC),
                uiConfig =
                    QSTileUIConfig.Resource(
                        iconRes = R.drawable.ic_qs_aicp_extras,
                        labelRes = R.string.quick_aicp_extras_label
                    ),
                instanceId = uiEventLogger.getNewInstanceId(),
                category = TileCategory.UTILITIES,
            )

        @Provides
        @IntoMap
        @StringKey(AMBIENT_DISPLAY_TILE_SPEC)
        fun provideAmbientDisplayTileConfig(uiEventLogger: QsEventLogger): QSTileConfig =
            QSTileConfig(
                tileSpec = TileSpec.create(AMBIENT_DISPLAY_TILE_SPEC),
                uiConfig =
                    QSTileUIConfig.Resource(
                        iconRes = R.drawable.ic_qs_ambient_display,
                        labelRes = R.string.quick_settings_ambient_display_label
                    ),
                instanceId = uiEventLogger.getNewInstanceId(),
                category = TileCategory.DISPLAY,
            )

        @Provides
        @IntoMap
        @StringKey(AOD_TILE_SPEC)
        fun provideAodTileConfig(uiEventLogger: QsEventLogger): QSTileConfig =
            QSTileConfig(
                tileSpec = TileSpec.create(AOD_TILE_SPEC),
                uiConfig =
                    QSTileUIConfig.Resource(
                        iconRes = R.drawable.ic_qs_aod,
                        labelRes = R.string.quick_settings_aod_label
                    ),
                instanceId = uiEventLogger.getNewInstanceId(),
                category = TileCategory.DISPLAY,
            )

        @Provides
        @IntoMap
        @StringKey(CAFFEINE_TILE_SPEC)
        fun provideCaffeineTileConfig(uiEventLogger: QsEventLogger): QSTileConfig =
            QSTileConfig(
                tileSpec = TileSpec.create(CAFFEINE_TILE_SPEC),
                uiConfig =
                    QSTileUIConfig.Resource(
                        iconRes = R.drawable.ic_qs_caffeine,
                        labelRes = R.string.quick_settings_caffeine_label
                    ),
                instanceId = uiEventLogger.getNewInstanceId(),
                category = TileCategory.DISPLAY,
            )

        @Provides
        @IntoMap
        @StringKey(COMPASS_TILE_SPEC)
        fun provideCompassTileConfig(uiEventLogger: QsEventLogger): QSTileConfig =
            QSTileConfig(
                tileSpec = TileSpec.create(COMPASS_TILE_SPEC),
                uiConfig =
                    QSTileUIConfig.Resource(
                        iconRes = R.drawable.ic_qs_compass_on,
                        labelRes = R.string.quick_settings_compass_label
                    ),
                instanceId = uiEventLogger.getNewInstanceId(),
                category = TileCategory.UTILITIES,
            )

        @Provides
        @IntoMap
        @StringKey(CPUINFO_TILE_SPEC)
        fun provideCpuInfoTileConfig(uiEventLogger: QsEventLogger): QSTileConfig =
            QSTileConfig(
                tileSpec = TileSpec.create(CPUINFO_TILE_SPEC),
                uiConfig =
                    QSTileUIConfig.Resource(
                        iconRes = R.drawable.ic_qs_cpu_info,
                        labelRes = R.string.quick_settings_cpuinfo_label
                    ),
                instanceId = uiEventLogger.getNewInstanceId(),
                category = TileCategory.UTILITIES,
            )

        @Provides
        @IntoMap
        @StringKey(HEADS_UP_TILE_SPEC)
        fun provideHeadsUpTileConfig(uiEventLogger: QsEventLogger): QSTileConfig =
            QSTileConfig(
                tileSpec = TileSpec.create(HEADS_UP_TILE_SPEC),
                uiConfig =
                    QSTileUIConfig.Resource(
                        iconRes = R.drawable.ic_qs_heads_up,
                        labelRes = R.string.quick_settings_heads_up_label
                    ),
                instanceId = uiEventLogger.getNewInstanceId(),
                category = TileCategory.ACCESSIBILITY,
            )

        @Provides
        @IntoMap
        @StringKey(MUSIC_TILE_SPEC)
        fun provideMusicTileConfig(uiEventLogger: QsEventLogger): QSTileConfig =
            QSTileConfig(
                tileSpec = TileSpec.create(MUSIC_TILE_SPEC),
                uiConfig =
                    QSTileUIConfig.Resource(
                        iconRes = R.drawable.ic_qs_media_play,
                        labelRes = R.string.quick_settings_music_label
                    ),
                instanceId = uiEventLogger.getNewInstanceId(),
                category = TileCategory.UTILITIES,
            )

        @Provides
        @IntoMap
        @StringKey(ONTHEGO_TILE_SPEC)
        fun provideOnTheGoTileConfig(uiEventLogger: QsEventLogger): QSTileConfig =
            QSTileConfig(
                tileSpec = TileSpec.create(ONTHEGO_TILE_SPEC),
                uiConfig =
                    QSTileUIConfig.Resource(
                        iconRes = R.drawable.ic_qs_onthego,
                        labelRes = R.string.quick_settings_onthego_label
                    ),
                instanceId = uiEventLogger.getNewInstanceId(),
                category = TileCategory.UTILITIES,
            )

        @Provides
        @IntoMap
        @StringKey(POWERSHARE_TILE_SPEC)
        fun providePowershareTileConfig(uiEventLogger: QsEventLogger): QSTileConfig =
            QSTileConfig(
                tileSpec = TileSpec.create(POWERSHARE_TILE_SPEC),
                uiConfig =
                    QSTileUIConfig.Resource(
                        iconRes = R.drawable.ic_qs_powershare,
                        labelRes = R.string.quick_settings_powershare_label
                    ),
                instanceId = uiEventLogger.getNewInstanceId(),
                category = TileCategory.UTILITIES,
            )

        @Provides
        @IntoMap
        @StringKey(PROFILES_TILE_SPEC)
        fun provideProfilesTileConfig(uiEventLogger: QsEventLogger): QSTileConfig =
            QSTileConfig(
                tileSpec = TileSpec.create(PROFILES_TILE_SPEC),
                uiConfig =
                    QSTileUIConfig.Resource(
                        iconRes = R.drawable.ic_qs_profiles,
                        labelRes = R.string.quick_settings_profiles_label
                    ),
                instanceId = uiEventLogger.getNewInstanceId(),
                category = TileCategory.UTILITIES,
            )

        @Provides
        @IntoMap
        @StringKey(READING_MODE_TILE_SPEC)
        fun provideReadingModeTileConfig(uiEventLogger: QsEventLogger): QSTileConfig =
            QSTileConfig(
                tileSpec = TileSpec.create(READING_MODE_TILE_SPEC),
                uiConfig =
                    QSTileUIConfig.Resource(
                        iconRes = R.drawable.ic_qs_reader,
                        labelRes = R.string.quick_settings_reading_mode
                    ),
                instanceId = uiEventLogger.getNewInstanceId(),
                category = TileCategory.DISPLAY,
            )

        @Provides
        @IntoMap
        @StringKey(SYNC_TILE_SPEC)
        fun provideSyncTileConfig(uiEventLogger: QsEventLogger): QSTileConfig =
            QSTileConfig(
                tileSpec = TileSpec.create(SYNC_TILE_SPEC),
                uiConfig =
                    QSTileUIConfig.Resource(
                        iconRes = R.drawable.ic_qs_sync,
                        labelRes = R.string.quick_settings_sync_label
                    ),
                instanceId = uiEventLogger.getNewInstanceId(),
                category = TileCategory.CONNECTIVITY,
            )

        @Provides
        @IntoMap
        @StringKey(USB_TETHER_TILE_SPEC)
        fun provideUsbTetherTileConfig(uiEventLogger: QsEventLogger): QSTileConfig =
            QSTileConfig(
                tileSpec = TileSpec.create(USB_TETHER_TILE_SPEC),
                uiConfig =
                    QSTileUIConfig.Resource(
                        iconRes = R.drawable.ic_qs_usb_tether,
                        labelRes = R.string.quick_settings_usb_tether_label
                    ),
                instanceId = uiEventLogger.getNewInstanceId(),
                category = TileCategory.CONNECTIVITY,
            )

        @Provides
        @IntoMap
        @StringKey(VOLUME_PANEL_TILE_SPEC)
        fun provideVolumePanelConfig(uiEventLogger: QsEventLogger): QSTileConfig =
            QSTileConfig(
                tileSpec = TileSpec.create(VOLUME_PANEL_TILE_SPEC),
                uiConfig =
                    QSTileUIConfig.Resource(
                        iconRes = R.drawable.ic_qs_volume_panel,
                        labelRes = R.string.quick_settings_volume_panel_label
                    ),
                instanceId = uiEventLogger.getNewInstanceId(),
                category = TileCategory.UTILITIES,
            )

        @Provides
        @IntoMap
        @StringKey(VPN_TILE_SPEC)
        fun provideVpnTileConfig(uiEventLogger: QsEventLogger): QSTileConfig =
            QSTileConfig(
                tileSpec = TileSpec.create(VPN_TILE_SPEC),
                uiConfig =
                    QSTileUIConfig.Resource(
                        iconRes = R.drawable.ic_qs_vpn,
                        labelRes = R.string.quick_settings_vpn_label
                    ),
                instanceId = uiEventLogger.getNewInstanceId(),
                category = TileCategory.CONNECTIVITY,
            )
    }
}
