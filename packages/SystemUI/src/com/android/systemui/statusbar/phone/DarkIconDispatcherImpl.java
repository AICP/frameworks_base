/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.android.systemui.statusbar.phone;

import static com.android.settingslib.flags.Flags.newStatusBarIcons;
import static com.android.systemui.plugins.DarkIconDispatcher.getTint;

import android.animation.ArgbEvaluator;
import android.content.ContentResolver;
import android.content.Context;
import android.content.res.ColorStateList;
import android.database.ContentObserver;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.ArrayMap;
import android.view.Display;
import android.widget.ImageView;

import com.android.systemui.display.dagger.SystemUIDisplaySubcomponent.DisplayAware;
import com.android.systemui.display.dagger.SystemUIDisplaySubcomponent.LifecycleListener;
import com.android.systemui.display.dagger.SystemUIDisplaySubcomponent.PerDisplaySingleton;
import com.android.systemui.dump.DumpManager;
import com.android.systemui.statusbar.policy.ConfigurationController;
import com.android.settingslib.Utils;

import kotlinx.coroutines.flow.FlowKt;
import kotlinx.coroutines.flow.MutableStateFlow;
import kotlinx.coroutines.flow.StateFlow;
import kotlinx.coroutines.flow.StateFlowKt;

import java.io.PrintWriter;
import java.util.ArrayList;

import javax.inject.Inject;

/**
 */
@PerDisplaySingleton
public class DarkIconDispatcherImpl implements SysuiDarkIconDispatcher,
        LightBarTransitionsController.DarkIntensityApplier, LifecycleListener,
        ConfigurationController.ConfigurationListener {

    private final LightBarTransitionsController mTransitionsController;
    private final ArrayList<Rect> mTintAreas = new ArrayList<>();
    private final ArrayMap<Object, DarkReceiver> mReceivers = new ArrayMap<>();
    private final DumpManager mDumpManager;
    private final Context mContext;
    private final ContentObserver mSettingsObserver;
    private final ConfigurationController mConfigurationController;

    private int mIconTint = DEFAULT_ICON_TINT;
    private int mContrastTint = DEFAULT_INVERSE_ICON_TINT;

    private int mDarkModeContrastColor = DEFAULT_ICON_TINT;
    private int mLightModeContrastColor = DEFAULT_INVERSE_ICON_TINT;

    private float mDarkIntensity;
    private int mDarkModeIconColorSingleTone;
    private int mLightModeIconColorSingleTone;

    private final MutableStateFlow<DarkChange> mDarkChangeFlow = StateFlowKt.MutableStateFlow(
            DarkChange.EMPTY);

    private final String mDumpableName;

    @Inject
    public DarkIconDispatcherImpl(
            @DisplayAware int displayId,
            @DisplayAware Context context,
            LightBarTransitionsController.Factory lightBarTransitionsControllerFactory,
            DumpManager dumpManager,
            ConfigurationController configurationController) {
        mContext = context;
        mDumpManager = dumpManager;
        mConfigurationController = configurationController;
        
        // Create ContentObserver to watch for setting changes
        mSettingsObserver = new ContentObserver(new Handler()) {
            @Override
            public void onChange(boolean selfChange) {
                // Re-apply dark intensity when setting changes
                applyDarkIntensity(mDarkIntensity);
            }
        };
        if (newStatusBarIcons()) {
            mDarkModeIconColorSingleTone = Color.BLACK;
            mLightModeIconColorSingleTone = Color.WHITE;
        } else {
            mDarkModeIconColorSingleTone = context.getColor(
                    com.android.settingslib.R.color.dark_mode_icon_color_single_tone);
            mLightModeIconColorSingleTone = context.getColor(
                    com.android.settingslib.R.color.light_mode_icon_color_single_tone);
        }

        mTransitionsController = lightBarTransitionsControllerFactory.create(this);

        mDumpableName = getDumpableName(displayId);
        dumpManager.registerNormalDumpable(mDumpableName, this);
        
        // Register ContentObserver to watch for setting changes
        mContext.getContentResolver().registerContentObserver(
                Settings.System.getUriFor(Settings.System.TINT_STATUSBAR_ICONS_WITH_ACCENT),
                false, mSettingsObserver, UserHandle.USER_ALL);
        
        // Register ConfigurationListener to watch for theme changes
        mConfigurationController.addCallback(this);
        
        // Apply initial tinting to ensure persistence after reboot
        // Use a post to ensure ContentResolver is fully initialized
        new Handler().post(() -> applyDarkIntensity(0.0f));
    }

    @Override
    public void stop() {
        mDumpManager.unregisterDumpable(mDumpableName);
        // Unregister ContentObserver
        mContext.getContentResolver().unregisterContentObserver(mSettingsObserver);
        // Unregister ConfigurationListener
        mConfigurationController.removeCallback(this);
    }

    private String getDumpableName(int displayId) {
        String dumpableNameSuffix =
                displayId == Display.DEFAULT_DISPLAY ? "" : String.valueOf(displayId);
        return getClass().getSimpleName() + dumpableNameSuffix;
    }

    public LightBarTransitionsController getTransitionsController() {
        return mTransitionsController;
    }

    @Override
    public StateFlow<DarkChange> darkChangeFlow() {
        return FlowKt.asStateFlow(mDarkChangeFlow);
    }

    public void addDarkReceiver(DarkReceiver receiver) {
        mReceivers.put(receiver, receiver);
        receiver.onDarkChanged(mTintAreas, mDarkIntensity, mIconTint);
        receiver.onDarkChangedWithContrast(mTintAreas, mIconTint, mContrastTint);
    }

    public void addDarkReceiver(ImageView imageView) {
        DarkReceiver receiver = (area, darkIntensity, tint) -> imageView.setImageTintList(
                ColorStateList.valueOf(getTint(mTintAreas, imageView, mIconTint)));
        mReceivers.put(imageView, receiver);
        receiver.onDarkChanged(mTintAreas, mDarkIntensity, mIconTint);
        receiver.onDarkChangedWithContrast(mTintAreas, mIconTint, mContrastTint);
    }

    public void removeDarkReceiver(DarkReceiver object) {
        mReceivers.remove(object);
    }

    public void removeDarkReceiver(ImageView object) {
        mReceivers.remove(object);
    }

    public void applyDark(DarkReceiver object) {
        mReceivers.get(object).onDarkChanged(mTintAreas, mDarkIntensity, mIconTint);
        mReceivers.get(object).onDarkChangedWithContrast(mTintAreas, mIconTint, mContrastTint);
    }

    /**
     * Sets the dark area so {@link #applyDark} only affects the icons in the specified area.
     *
     * @param darkAreas the areas in which icons should change it's tint, in logical screen
     *                  coordinates
     */
    public void setIconsDarkArea(ArrayList<Rect> darkAreas) {
        if (darkAreas == null && mTintAreas.isEmpty()) {
            return;
        }

        mTintAreas.clear();
        if (darkAreas != null) {
            mTintAreas.addAll(darkAreas);
        }
        applyIconTint();
    }

    @Override
    public void applyDarkIntensity(float darkIntensity) {
        mDarkIntensity = darkIntensity;
        ArgbEvaluator evaluator = ArgbEvaluator.getInstance();

        // Check if accent color tinting is enabled
        boolean useAccentColor = Settings.System.getIntForUser(
                mContext.getContentResolver(),
                Settings.System.TINT_STATUSBAR_ICONS_WITH_ACCENT,
                0,
                UserHandle.USER_CURRENT) == 1;

        if (useAccentColor) {
            // Use system accent color for tinting
            int accentColor = Utils.getColorAccentDefaultColor(mContext);
            mIconTint = accentColor;
            mContrastTint = accentColor;
        } else {
            // Use default behavior
            mIconTint = (int) evaluator.evaluate(darkIntensity,
                    mLightModeIconColorSingleTone, mDarkModeIconColorSingleTone);
            mContrastTint = (int) evaluator
                    .evaluate(darkIntensity, mLightModeContrastColor, mDarkModeContrastColor);
        }

        applyIconTint();
    }

    @Override
    public int getTintAnimationDuration() {
        return LightBarTransitionsController.DEFAULT_TINT_ANIMATION_DURATION;
    }

    private void applyIconTint() {
        mDarkChangeFlow.setValue(new DarkChange(mTintAreas, mDarkIntensity, mIconTint));
        for (int i = 0; i < mReceivers.size(); i++) {
            mReceivers.valueAt(i).onDarkChanged(mTintAreas, mDarkIntensity, mIconTint);
            mReceivers.valueAt(i).onDarkChangedWithContrast(mTintAreas, mIconTint, mContrastTint);
        }
    }

    @Override
    public void onThemeChanged() {
        // Re-apply dark intensity when theme changes to update accent color
        applyDarkIntensity(mDarkIntensity);
    }

    @Override
    public void dump(PrintWriter pw, String[] args) {
        pw.println("DarkIconDispatcher: ");
        pw.println("  mIconTint: 0x" + Integer.toHexString(mIconTint));
        pw.println("  mContrastTint: 0x" + Integer.toHexString(mContrastTint));

        pw.println("  mDarkModeIconColorSingleTone: 0x"
                + Integer.toHexString(mDarkModeIconColorSingleTone));
        pw.println("  mLightModeIconColorSingleTone: 0x"
                + Integer.toHexString(mLightModeIconColorSingleTone));

        pw.println("  mDarkModeContrastColor: 0x" + Integer.toHexString(mDarkModeContrastColor));
        pw.println("  mLightModeContrastColor: 0x" + Integer.toHexString(mLightModeContrastColor));

        pw.println("  mDarkIntensity: " + mDarkIntensity + "f");
        pw.println("  mTintAreas: " + mTintAreas);
    }
}
