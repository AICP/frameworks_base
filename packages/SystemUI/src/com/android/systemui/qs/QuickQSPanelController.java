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

import com.android.internal.logging.MetricsLogger;
import com.android.internal.logging.UiEventLogger;
import com.android.systemui.R;
import com.android.systemui.dump.DumpManager;
import com.android.systemui.media.MediaHierarchyManager;
import com.android.systemui.media.MediaHost;
import com.android.systemui.plugins.qs.QSTile;
import com.android.systemui.qs.customize.QSCustomizerController;
import com.android.systemui.qs.dagger.QSScope;
import com.android.systemui.qs.logging.QSLogger;
import com.android.systemui.settings.brightness.BrightnessController;
import com.android.systemui.settings.brightness.BrightnessMirrorHandler;
import com.android.systemui.settings.brightness.BrightnessSliderController;
import com.android.systemui.statusbar.policy.BrightnessMirrorController;
import com.android.systemui.tuner.TunerService;

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
    private final BrightnessController mBrightnessController;
    private final BrightnessMirrorHandler mBrightnessMirrorHandler;
    private final FooterActionsController mFooterActionsController;
    private final TunerService mTunerService;
    private final BrightnessSliderController mBrightnessSliderController;
    private BrightnessMirrorController mBrightnessMirrorController;

    @Inject
    QuickQSPanelController(QuickQSPanel view, QSTileHost qsTileHost,
            QSCustomizerController qsCustomizerController,
            @Named(QS_USING_MEDIA_PLAYER) boolean usingMediaPlayer,
            @Named(QUICK_QS_PANEL) MediaHost mediaHost,
            MetricsLogger metricsLogger, UiEventLogger uiEventLogger, QSLogger qsLogger,
            DumpManager dumpManager,
            @Named(QQS_FOOTER) FooterActionsController footerActionsController,
            TunerService tunerService,
            BrightnessController.Factory brightnessControllerFactory,
            BrightnessSliderController.Factory brightnessSliderFactory
    ) {
        super(view, qsTileHost, qsCustomizerController, usingMediaPlayer, mediaHost, metricsLogger,
                uiEventLogger, qsLogger, dumpManager);
        mTunerService = tunerService;
        mFooterActionsController = footerActionsController;

        mBrightnessSliderController = brightnessSliderFactory.create(getContext(), mView);
        mView.setBrightnessView(mBrightnessSliderController.getRootView());

        mBrightnessController = brightnessControllerFactory.create(
                mBrightnessSliderController.getIconView(), mBrightnessSliderController);
        mBrightnessMirrorHandler = new BrightnessMirrorHandler(mBrightnessController);
    }

    @Override
    protected void onInit() {
        super.onInit();
        mMediaHost.setExpansion(0.0f);
        mMediaHost.setShowsOnlyActiveMedia(true);
        mMediaHost.init(MediaHierarchyManager.LOCATION_QQS);
        mFooterActionsController.init();
        mFooterActionsController.refreshVisibility(mShouldUseSplitNotificationShade);
        mBrightnessSliderController.init();
    }

    @Override
    protected void onViewAttached() {
        super.onViewAttached();

        mTunerService.addTunable(mView, QSPanel.QS_BRIGHTNESS_SLIDER_POSITION);
        mTunerService.addTunable(mView, QSPanel.QS_SHOW_AUTO_BRIGHTNESS);
        mTunerService.addTunable(mView, QSPanel.QS_SHOW_BRIGHTNESS_SLIDER);
        mTunerService.addTunable(mView, QSPanel.QS_TILE_VERTICAL_LAYOUT);
        mTunerService.addTunable(mView, QSPanel.QS_LAYOUT_COLUMNS);
        mTunerService.addTunable(mView, QSPanel.QS_LAYOUT_COLUMNS_LANDSCAPE);
        mTunerService.addTunable(mView, QSPanel.QS_TILE_LABEL_HIDE);

        mView.setBrightnessRunnable(() -> {
            mView.updateResources();
            updateBrightnessMirror();
        });

        mView.setLayoutRunnable(() -> {
            mView.updateSettings();
            setTiles();
        });

        mView.addOnConfigurationChangedListener(mOnConfigurationChangedListener);
        mBrightnessMirrorHandler.onQsPanelAttached();
    }

    @Override
    protected void onViewDetached() {
        super.onViewDetached();
        mTunerService.removeTunable(mView);
        mView.setBrightnessRunnable(null);
        mView.removeOnConfigurationChangedListener(mOnConfigurationChangedListener);
        mBrightnessMirrorHandler.onQsPanelDettached();
    }

    private void updateBrightnessMirror() {
        if (mBrightnessMirrorController != null) {
            mBrightnessSliderController.setMirrorControllerAndMirror(mBrightnessMirrorController);
        }
    }

    @Override
    void setListening(boolean listening) {
        super.setListening(listening);
        mFooterActionsController.setListening(listening);

        // Set the listening as soon as the QS fragment starts listening regardless of the
        //expansion, so it will update the current brightness before the slider is visible.
        if (listening) {
            mBrightnessController.registerCallbacks();
        } else {
            mBrightnessController.unregisterCallbacks();
        }
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
        mBrightnessMirrorController = brightnessMirrorController;
        mBrightnessMirrorHandler.setController(brightnessMirrorController);
    }
}
