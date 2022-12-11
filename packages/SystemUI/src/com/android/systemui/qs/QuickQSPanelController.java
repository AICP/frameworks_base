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

package com.android.systemui.qs;

import static com.android.systemui.media.dagger.MediaModule.QUICK_QS_PANEL;
import static com.android.systemui.qs.dagger.QSFragmentModule.QS_USING_COLLAPSED_LANDSCAPE_MEDIA;
import static com.android.systemui.qs.dagger.QSFragmentModule.QS_USING_MEDIA_PLAYER;

import android.annotation.NonNull;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;

import androidx.annotation.VisibleForTesting;

import com.android.internal.logging.MetricsLogger;
import com.android.internal.logging.UiEventLogger;
import com.android.systemui.R;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.dump.DumpManager;
import com.android.systemui.media.MediaHierarchyManager;
import com.android.systemui.media.MediaHost;
import com.android.systemui.plugins.qs.QSTile;
import com.android.systemui.qs.customize.QSCustomizerController;
import com.android.systemui.qs.dagger.QSScope;
import com.android.systemui.qs.logging.QSLogger;
import com.android.systemui.settings.brightness.BrightnessMirrorHandler;
import com.android.systemui.statusbar.policy.BrightnessMirrorController;
import com.android.systemui.util.leak.RotationUtils;
import com.android.systemui.util.settings.SystemSettings;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;

/** Controller for {@link QuickQSPanel}. */
@QSScope
public class QuickQSPanelController extends QSPanelControllerBase<QuickQSPanel> {

    private final Provider<Boolean> mUsingCollapsedLandscapeMediaProvider;
    private final QuickQSBrightnessController mBrightnessController;
    private final BrightnessMirrorHandler mBrightnessMirrorHandler;


    private boolean mForceShowSlider = false;

    @Inject
    QuickQSPanelController(QuickQSPanel view, QSTileHost qsTileHost,
            QSCustomizerController qsCustomizerController,
            @Named(QS_USING_MEDIA_PLAYER) boolean usingMediaPlayer,
            @Named(QUICK_QS_PANEL) MediaHost mediaHost,
            @Named(QS_USING_COLLAPSED_LANDSCAPE_MEDIA)
                    Provider<Boolean> usingCollapsedLandscapeMediaProvider,
            MetricsLogger metricsLogger, UiEventLogger uiEventLogger, QSLogger qsLogger,
            DumpManager dumpManager,
            QuickQSBrightnessController quickQSBrightnessController,
            @Main Handler mainHandler,
            SystemSettings systemSettings
    ) {
        super(view, qsTileHost, qsCustomizerController, usingMediaPlayer, mediaHost, metricsLogger,
                uiEventLogger, qsLogger, dumpManager, mainHandler, systemSettings);
        mBrightnessController = quickQSBrightnessController;
        mBrightnessMirrorHandler = new BrightnessMirrorHandler(mBrightnessController);
        mUsingCollapsedLandscapeMediaProvider = usingCollapsedLandscapeMediaProvider;
    }

    @Override
    protected void onInit() {
        super.onInit();
        updateMediaExpansion();
        mMediaHost.setShowsOnlyActiveMedia(true);
        mMediaHost.init(MediaHierarchyManager.LOCATION_QQS);
        mBrightnessController.refreshVisibility(mForceShowSlider,
            mShouldUseSplitNotificationShade);
    }

    private void updateMediaExpansion() {
        int rotation = getRotation();
        boolean isLandscape = rotation == RotationUtils.ROTATION_LANDSCAPE
                || rotation == RotationUtils.ROTATION_SEASCAPE;
        boolean usingCollapsedLandscapeMedia = mUsingCollapsedLandscapeMediaProvider.get();
        if (!usingCollapsedLandscapeMedia || !isLandscape) {
            mMediaHost.setExpansion(MediaHost.EXPANDED);
        } else {
            mMediaHost.setExpansion(MediaHost.COLLAPSED);
        }
    }

    @VisibleForTesting
    protected int getRotation() {
        return RotationUtils.getRotation(getContext());
    }

    @Override
    public boolean handleSettingsChange(@NonNull String key) {
        final boolean handled = super.handleSettingsChange(key);
        if (key.equals(Settings.System.BRIGHTNESS_SLIDER_POSITION)) {
            updateBrightnessMirror();
            return true;
        } else if (key.equals(Settings.System.QQS_SHOW_BRIGHTNESS)) {
            updateSliderVisibility();
            return true;
        }
        return handled;
    }

    private void updateSliderVisibility() {
        mForceShowSlider = mSystemSettings.getIntForUser(
            Settings.System.QQS_SHOW_BRIGHTNESS,
            0, UserHandle.USER_CURRENT
        ) == 1;
        mBrightnessController.refreshVisibility(mForceShowSlider,
            mShouldUseSplitNotificationShade);
    }

    @Override
    protected void updateBrightnessMirror() {
        mBrightnessMirrorHandler.updateBrightnessMirror();
    }

    @Override
    protected void onViewAttached() {
        super.onViewAttached();
        mBrightnessMirrorHandler.onQsPanelAttached();
        updateSliderVisibility();
        registerObserver(Settings.System.QQS_SHOW_BRIGHTNESS);
    }

    @Override
    protected void onViewDetached() {
        super.onViewDetached();
        mBrightnessMirrorHandler.onQsPanelDettached();
    }

    @Override
    void setListening(boolean listening) {
        super.setListening(listening);
        mBrightnessController.setListening(listening);
    }

    public boolean isListening() {
        return mView.isListening();
    }

    private void setMaxTiles(int parseNumTiles) {
        mView.setMaxTiles(parseNumTiles);
        setTiles();
    }

    @Override
    protected void onConfigurationChanged() {
        int newMaxTiles = getResources().getInteger(R.integer.quick_qs_panel_max_tiles);
        if (newMaxTiles != mView.getNumQuickTiles()) {
            setMaxTiles(newMaxTiles);
        }
        updateMediaExpansion();
        mBrightnessController.refreshVisibility(mForceShowSlider,
            mShouldUseSplitNotificationShade);
    }

    @Override
    public void setTiles() {
        List<QSTile> tiles = new ArrayList<>();
        for (QSTile tile : mHost.getTiles()) {
            tiles.add(tile);
            if (tiles.size() == mView.getNumQuickTiles()) {
                break;
            }
        }
        super.setTiles(tiles, /* collapsedView */ true);
    }

    public void setContentMargins(int marginStart, int marginEnd) {
        mView.setContentMargins(marginStart, marginEnd, mMediaHost.getHostView());
    }

    public int getNumQuickTiles() {
        return mView.getNumQuickTiles();
    }
}
