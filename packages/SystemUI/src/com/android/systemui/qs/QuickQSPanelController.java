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
import static com.android.systemui.qs.dagger.QSFragmentModule.QQS_FOOTER;
import static com.android.systemui.qs.dagger.QSFragmentModule.QS_USING_MEDIA_PLAYER;

import android.annotation.NonNull;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;

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
import com.android.systemui.util.settings.SystemSettings;

import com.aicp.gear.util.AicpUtils;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Named;

/** Controller for {@link QuickQSPanel}. */
@QSScope
public class QuickQSPanelController extends QSPanelControllerBase<QuickQSPanel> {

    private final QSPanel.OnConfigurationChangedListener mOnConfigurationChangedListener =
            newConfig -> {
                int newMaxTiles = getResources().getInteger(R.integer.quick_qs_panel_max_columns);
                newMaxTiles = AicpUtils.getQuickQSColumnsCount(getContext(), newMaxTiles);
                if (newMaxTiles != mView.getNumQuickTiles()) {
                    setMaxTiles(newMaxTiles);
                }
            };

    // brightness is visible only in split shade
    private final QuickQSBrightnessController mBrightnessController;
    private final BrightnessMirrorHandler mBrightnessMirrorHandler;
    private final FooterActionsController mFooterActionsController;

    private boolean mForceShowSlider = false;

    @Inject
    QuickQSPanelController(QuickQSPanel view, QSTileHost qsTileHost,
            QSCustomizerController qsCustomizerController,
            @Named(QS_USING_MEDIA_PLAYER) boolean usingMediaPlayer,
            @Named(QUICK_QS_PANEL) MediaHost mediaHost,
            MetricsLogger metricsLogger, UiEventLogger uiEventLogger, QSLogger qsLogger,
            DumpManager dumpManager,
            QuickQSBrightnessController quickQSBrightnessController,
            @Named(QQS_FOOTER) FooterActionsController footerActionsController,
            @Main Handler mainHandler,
            SystemSettings systemSettings
    ) {
        super(view, qsTileHost, qsCustomizerController, usingMediaPlayer, mediaHost, metricsLogger,
                uiEventLogger, qsLogger, dumpManager, mainHandler, systemSettings);
        mBrightnessController = quickQSBrightnessController;
        mBrightnessMirrorHandler = new BrightnessMirrorHandler(mBrightnessController);
        mFooterActionsController = footerActionsController;
    }

    @Override
    protected void onInit() {
        super.onInit();
        mMediaHost.setExpansion(0.0f);
        mMediaHost.setShowsOnlyActiveMedia(true);
        mMediaHost.init(MediaHierarchyManager.LOCATION_QQS);
        mForceShowSlider = shouldShowSlider();
        mBrightnessController.refreshVisibility(mForceShowSlider,
            mShouldUseSplitNotificationShade);
        mFooterActionsController.init();
        mFooterActionsController.refreshVisibility(mShouldUseSplitNotificationShade);
    }

    @Override
    public boolean handleSettingsChange(@NonNull String key) {
        final boolean handled = super.handleSettingsChange(key);
        if (key.equals(Settings.System.BRIGHTNESS_SLIDER_POSITION)) {
            updateBrightnessMirror();
        } else if (key.equals(Settings.System.QQS_SHOW_BRIGHTNESS)) {
            mForceShowSlider = shouldShowSlider();
            mBrightnessController.refreshVisibility(mForceShowSlider,
                mShouldUseSplitNotificationShade);
            return true;
        }
        return handled;
    }

    @Override
    protected void updateBrightnessMirror() {
        mBrightnessMirrorHandler.updateBrightnessMirror();
    }

    @Override
    protected void onViewAttached() {
        super.onViewAttached();
        mView.addOnConfigurationChangedListener(mOnConfigurationChangedListener);
        mBrightnessMirrorHandler.onQsPanelAttached();
        registerObserver(Settings.System.QQS_SHOW_BRIGHTNESS);
    }

    @Override
    protected void onViewDetached() {
        super.onViewDetached();
        mView.removeOnConfigurationChangedListener(mOnConfigurationChangedListener);
        mBrightnessMirrorHandler.onQsPanelDettached();
    }

    @Override
    void setListening(boolean listening) {
        super.setListening(listening);
        mBrightnessController.setListening(listening);
        mFooterActionsController.setListening(listening);
    }

    private boolean shouldShowSlider() {
        return mSystemSettings.getIntForUser(
            Settings.System.QQS_SHOW_BRIGHTNESS,
            0, UserHandle.USER_CURRENT
        ) == 1;
    }

    public boolean isListening() {
        return mView.isListening();
    }

    private void setMaxTiles(int parseNumTiles) {
        mView.setMaxTiles(parseNumTiles);
        setTiles();
    }

    @Override
    public void refreshAllTiles() {
        mBrightnessController.checkRestrictionAndSetEnabled();
        super.refreshAllTiles();
    }

    @Override
    protected void onConfigurationChanged() {
        mBrightnessController.refreshVisibility(mForceShowSlider,
            mShouldUseSplitNotificationShade);
        mFooterActionsController.refreshVisibility(mShouldUseSplitNotificationShade);
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

    /** */
    public void setContentMargins(int marginStart, int marginEnd) {
        mView.setContentMargins(marginStart, marginEnd, mMediaHost.getHostView());
    }

    public int getNumQuickTiles() {
        return mView.getNumQuickTiles();
    }

    public void setBrightnessMirror(BrightnessMirrorController brightnessMirrorController) {
        mBrightnessMirrorHandler.setController(brightnessMirrorController);
    }
}
