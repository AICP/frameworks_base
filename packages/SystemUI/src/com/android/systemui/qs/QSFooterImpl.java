/*
 * Copyright (C) 2017 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.systemui.qs;

import static android.app.StatusBarManager.DISABLE2_QUICK_SETTINGS;

import static com.android.systemui.util.InjectionInflationController.VIEW_CONTEXT;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.PorterDuff.Mode;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.Settings;
import android.util.AttributeSet;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.android.internal.logging.MetricsLogger;
import com.android.internal.logging.nano.MetricsProto;
import com.android.keyguard.KeyguardUpdateMonitor;
import com.android.settingslib.Utils;
import com.android.settingslib.drawable.UserIconDrawable;
import com.android.systemui.Dependency;
import com.android.systemui.R;
import com.android.systemui.R.dimen;
import com.android.systemui.plugins.ActivityStarter;
import com.android.systemui.qs.TouchAnimator.Builder;
import com.android.systemui.statusbar.phone.MultiUserSwitch;
import com.android.systemui.statusbar.phone.SettingsButton;
import com.android.systemui.statusbar.policy.DeviceProvisionedController;
import com.android.systemui.statusbar.policy.UserInfoController;
import com.android.systemui.statusbar.policy.UserInfoController.OnUserInfoChangedListener;
import com.android.systemui.tuner.TunerService;

import javax.inject.Inject;
import javax.inject.Named;

public class QSFooterImpl extends FrameLayout implements QSFooter,
        OnClickListener, OnUserInfoChangedListener, TunerService.Tunable {

    private static final String TAG = "QSFooterImpl";
    public static final String QS_SHOW_AUTO_BRIGHTNESS_BUTTON = "qs_show_auto_brightness_button";
    public static final String SCREEN_BRIGHTNESS_MODE = "screen_brightness_mode";

    public static final String AICP_FOOTER_TEXT_SHOW =
            "system:" + Settings.System.AICP_FOOTER_TEXT_SHOW;
    public static final String AICP_FOOTER_TEXT_STRING =
            "system:" + Settings.System.AICP_FOOTER_TEXT_STRING;
    public static final String QS_FOOTER_SHOW_SETTINGS =
            "system:" + Settings.System.QS_FOOTER_SHOW_SETTINGS;
    public static final String QS_FOOTER_SHOW_SERVICES =
            "system:" + Settings.System.QS_FOOTER_SHOW_SERVICES;
    public static final String QS_FOOTER_SHOW_EDIT =
            "system:" + Settings.System.QS_FOOTER_SHOW_EDIT;
    public static final String QS_FOOTER_SHOW_USER =
            "system:" + Settings.System.QS_FOOTER_SHOW_USER;

    private final ActivityStarter mActivityStarter;
    private final UserInfoController mUserInfoController;
    private final DeviceProvisionedController mDeviceProvisionedController;
    private SettingsButton mSettingsButton;
    protected View mSettingsContainer;
    private PageIndicator mPageIndicator;
    private TextView mBuildText;
    private boolean mShouldShowFooterText;
    private View mRunningServicesButton;
    private View mEditButton;

    private boolean mQsDisabled;
    private QSPanel mQsPanel;
    private QuickQSPanel mQuickQsPanel;

    private boolean mExpanded;

    private boolean mListening;

    protected MultiUserSwitch mMultiUserSwitch;
    private ImageView mMultiUserAvatar;

    protected TouchAnimator mFooterAnimator;
    private float mExpansionAmount;

    protected View mEdit;
    protected View mEditContainer;
    private TouchAnimator mSettingsCogAnimator;

    private View mActionsContainer;

    private OnClickListener mExpandClickListener;

    private ImageView mAutoBrightnessIcon;
    protected View mAutoBrightnessContainer;
    private boolean mShowAutoBrightnessButton;
    private boolean mAutoBrightOn;
    private boolean mShowSettingsIcon;
    private boolean mShowServicesIcon;
    private boolean mShowEditIcon;
    private boolean mShowUserIcon;

    @Inject
    public QSFooterImpl(@Named(VIEW_CONTEXT) Context context, AttributeSet attrs,
            ActivityStarter activityStarter, UserInfoController userInfoController,
            DeviceProvisionedController deviceProvisionedController) {
        super(context, attrs);
        mActivityStarter = activityStarter;
        mUserInfoController = userInfoController;
        mDeviceProvisionedController = deviceProvisionedController;
    }

    @VisibleForTesting
    public QSFooterImpl(Context context, AttributeSet attrs) {
        this(context, attrs,
                Dependency.get(ActivityStarter.class),
                Dependency.get(UserInfoController.class),
                Dependency.get(DeviceProvisionedController.class));
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mEdit = findViewById(android.R.id.edit);
        mEdit.setOnClickListener(view ->
                mActivityStarter.postQSRunnableDismissingKeyguard(() ->
                        mQsPanel.showEdit(view)));

        mPageIndicator = findViewById(R.id.footer_page_indicator);

        mSettingsButton = findViewById(R.id.settings_button);
        mSettingsContainer = findViewById(R.id.settings_button_container);
        mSettingsButton.setOnClickListener(this);

        mRunningServicesButton = findViewById(R.id.running_services_button);
        mRunningServicesButton.setOnClickListener(this);

        mMultiUserSwitch = findViewById(R.id.multi_user_switch);
        mMultiUserAvatar = mMultiUserSwitch.findViewById(R.id.multi_user_avatar);

        mActionsContainer = findViewById(R.id.qs_footer_actions_container);
        mEditContainer = findViewById(R.id.qs_footer_actions_edit_container);
        mBuildText = findViewById(R.id.build);
        mEditButton = findViewById(com.android.internal.R.id.edit);

        mAutoBrightnessContainer = findViewById(R.id.brightness_icon_container);
        mAutoBrightnessIcon = findViewById(R.id.brightness_icon);
        mAutoBrightnessIcon.setOnClickListener(this);
        // RenderThread is doing more harm than good when touching the header (to expand quick
        // settings), so disable it for this view
        ((RippleDrawable) mSettingsButton.getBackground()).setForceSoftware(true);
        ((RippleDrawable) mRunningServicesButton.getBackground()).setForceSoftware(true);


        updateResources();

        addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight,
                oldBottom) -> updateAnimator(right - left));
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        updateEverything();
        setFooterText();
    }

    private void setFooterText() {
        if (mBuildText == null) return;
        String footerText = Settings.System.getStringForUser(mContext.getContentResolver(),
                        Settings.System.AICP_FOOTER_TEXT_STRING, UserHandle.USER_CURRENT);
        mBuildText.setText((footerText != null && !footerText.isEmpty()) ? footerText :
                        mContext.getResources().getString(R.string.qs_footer_aicp_text));

        if (mShouldShowFooterText) {
            // Set as selected for marquee before its made visible, then it won't be announced when
            // it's made visible.
            mBuildText.setSelected(true);
        } else {
            mBuildText.setSelected(false);
        }
    }

    private void updateAnimator(int width) {
        int numTiles = mQuickQsPanel != null ? mQuickQsPanel.getNumQuickTiles()
                : QuickQSPanel.getDefaultMaxTiles();
        int size = mContext.getResources().getDimensionPixelSize(R.dimen.qs_quick_tile_size)
                - mContext.getResources().getDimensionPixelSize(dimen.qs_quick_tile_padding);
        int remaining = (width - numTiles * size) / (numTiles - 1);
        int defSpace = mContext.getResources().getDimensionPixelOffset(R.dimen.default_gear_space);

        mSettingsCogAnimator = new Builder()
                .addFloat(mSettingsContainer, "translationX",
                        isLayoutRtl() ? (remaining - defSpace) : -(remaining - defSpace), 0)
                .addFloat(mSettingsButton, "rotation", -120, 0)
                .addFloat(mAutoBrightnessIcon, "rotation", 120, 0)
                .build();

        setExpansion(mExpansionAmount);
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        updateResources();
    }

    @Override
    public void onRtlPropertiesChanged(int layoutDirection) {
        super.onRtlPropertiesChanged(layoutDirection);
        updateResources();
    }

    private void updateResources() {
        updateFooterAnimator();
    }

    private void updateFooterAnimator() {
        mFooterAnimator = createFooterAnimator();
    }

    @Nullable
    private TouchAnimator createFooterAnimator() {
        return new TouchAnimator.Builder()
                .addFloat(mActionsContainer, "alpha", 0, 1) // contains mRunningServicesButton
                .addFloat(mEditContainer, "alpha", 0, 1)
                .addFloat(mAutoBrightnessContainer, "alpha", 0, 1)
                .addFloat(mPageIndicator, "alpha", 0, 1)
                .setStartDelay(0.9f)
                .build();
    }

    @Override
    public void setKeyguardShowing(boolean keyguardShowing) {
        setExpansion(mExpansionAmount);
    }

    @Override
    public void setExpandClickListener(OnClickListener onClickListener) {
        mExpandClickListener = onClickListener;
    }

    @Override
    public void setExpanded(boolean expanded) {
        if (mExpanded == expanded) return;
        mExpanded = expanded;
        updateEverything();
    }

    @Override
    public void setExpansion(float headerExpansionFraction) {
        mExpansionAmount = headerExpansionFraction;
        if (mSettingsCogAnimator != null) mSettingsCogAnimator.setPosition(headerExpansionFraction);

        if (mFooterAnimator != null) {
            mFooterAnimator.setPosition(headerExpansionFraction);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        final TunerService tunerService = Dependency.get(TunerService.class);
        tunerService.addTunable(this,
                QS_SHOW_AUTO_BRIGHTNESS_BUTTON,
                SCREEN_BRIGHTNESS_MODE,
                AICP_FOOTER_TEXT_SHOW,
                AICP_FOOTER_TEXT_STRING,
                QS_FOOTER_SHOW_SETTINGS,
                QS_FOOTER_SHOW_SERVICES,
                QS_FOOTER_SHOW_EDIT,
                QS_FOOTER_SHOW_USER);
    }

    @Override
    @VisibleForTesting
    public void onDetachedFromWindow() {
        Dependency.get(TunerService.class).removeTunable(this);
        setListening(false);
        super.onDetachedFromWindow();
    }

    @Override
    public void onTuningChanged(String key, String newValue) {
        switch (key) {
            case QS_SHOW_AUTO_BRIGHTNESS_BUTTON:
                setHideAutoBright(newValue != null && Integer.parseInt(newValue) == 0);
                break;
            case SCREEN_BRIGHTNESS_MODE:
                setAutoBrightnessIcon(newValue != null && Integer.parseInt(newValue) != 0);
                break;
            case AICP_FOOTER_TEXT_SHOW:
                mShouldShowFooterText =
                        TunerService.parseIntegerSwitch(newValue, false);
                setFooterText();
                break;
            case AICP_FOOTER_TEXT_STRING:
                setFooterText();
                break;
            case QS_FOOTER_SHOW_SETTINGS:
                mShowSettingsIcon =
                        TunerService.parseIntegerSwitch(newValue, true);
                break;
            case QS_FOOTER_SHOW_SERVICES:
                mShowServicesIcon =
                        TunerService.parseIntegerSwitch(newValue, true);
                break;
            case QS_FOOTER_SHOW_EDIT:
                mShowEditIcon =
                        TunerService.parseIntegerSwitch(newValue, true);
                break;
            case QS_FOOTER_SHOW_USER:
                mShowUserIcon =
                        TunerService.parseIntegerSwitch(newValue, false);
                break;
            default:
                break;
        }
        updateVisibilities();
    }

    @Override
    public void setListening(boolean listening) {
        if (listening == mListening) {
            return;
        }
        mListening = listening;
        updateListeners();
    }

    @Override
    public boolean performAccessibilityAction(int action, Bundle arguments) {
        if (action == AccessibilityNodeInfo.ACTION_EXPAND) {
            if (mExpandClickListener != null) {
                mExpandClickListener.onClick(null);
                return true;
            }
        }
        return super.performAccessibilityAction(action, arguments);
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_EXPAND);
    }

    @Override
    public void disable(int state1, int state2, boolean animate) {
        final boolean disabled = (state2 & DISABLE2_QUICK_SETTINGS) != 0;
        if (disabled == mQsDisabled) return;
        mQsDisabled = disabled;
        updateEverything();
    }

    public void updateEverything() {
        post(() -> {
            updateVisibilities();
            updateClickabilities();
            setClickable(false);
        });
        mAutoBrightOn = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
                UserHandle.USER_CURRENT) != Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL;
        setAutoBrightnessIcon(mAutoBrightOn);
    }

    private void updateClickabilities() {
        mMultiUserSwitch.setClickable(mMultiUserSwitch.getVisibility() == View.VISIBLE);
        mEdit.setClickable(mEdit.getVisibility() == View.VISIBLE);
        mSettingsButton.setClickable(mSettingsButton.getVisibility() == View.VISIBLE);
        mAutoBrightnessIcon.setClickable(mAutoBrightnessIcon.getVisibility() == View.VISIBLE);
    }

    private void updateVisibilities() {
        mSettingsContainer.setVisibility(!mShowSettingsIcon || mQsDisabled ? View.GONE : View.VISIBLE);
        mAutoBrightnessContainer.setVisibility(mShowAutoBrightnessButton ? View.GONE : View.VISIBLE);
        final boolean isDemo = UserManager.isDeviceInDemoMode(mContext);
        mMultiUserSwitch.setVisibility(showUserSwitcher() ? View.VISIBLE : View.GONE);
        mEditButton.setVisibility(!mShowEditIcon || isDemo || !mExpanded ? View.GONE : View.VISIBLE);
        mSettingsButton.setVisibility(!mShowSettingsIcon || isDemo || !mExpanded ? View.GONE : View.VISIBLE);
        mRunningServicesButton.setVisibility(!mShowServicesIcon || isDemo || !mExpanded ? View.GONE : View.VISIBLE);
        mBuildText.setVisibility(mExpanded && mShouldShowFooterText ? View.VISIBLE : View.GONE);
    }

    private boolean showUserSwitcher() {
        return mShowUserIcon && mExpanded && mMultiUserSwitch.isMultiUserEnabled();
    }

    private void updateListeners() {
        if (mListening) {
            mUserInfoController.addCallback(this);
        } else {
            mUserInfoController.removeCallback(this);
        }
    }

    @Override
    public void setQSPanel(final QSPanel qsPanel, final QuickQSPanel quickQSPanel) {
        mQsPanel = qsPanel;
        mQuickQsPanel = quickQSPanel;
        if (mQsPanel != null) {
            mMultiUserSwitch.setQsPanel(qsPanel);
            mQsPanel.setFooterPageIndicator(mPageIndicator);
        }
    }

    @Override
    public void setQQSPanel(@Nullable QuickQSPanel panel) {
        mQuickQsPanel = panel;
    }

    @Override
    public void onClick(View v) {
        // Don't do anything until view are unhidden
        if (!mExpanded) {
            return;
        }

        if (v == mSettingsButton) {
            if (!mDeviceProvisionedController.isCurrentUserSetup()) {
                // If user isn't setup just unlock the device and dump them back at SUW.
                mActivityStarter.postQSRunnableDismissingKeyguard(() -> {
                });
                return;
            }
            MetricsLogger.action(mContext,
                    mExpanded ? MetricsProto.MetricsEvent.ACTION_QS_EXPANDED_SETTINGS_LAUNCH
                            : MetricsProto.MetricsEvent.ACTION_QS_COLLAPSED_SETTINGS_LAUNCH);
            startSettingsActivity();
        } else if (v == mRunningServicesButton) {
            MetricsLogger.action(mContext,
                    mExpanded ? MetricsProto.MetricsEvent.ACTION_QS_EXPANDED_SETTINGS_LAUNCH
                            : MetricsProto.MetricsEvent.ACTION_QS_COLLAPSED_SETTINGS_LAUNCH);
            startRunningServicesActivity();
        } else if (v == mAutoBrightnessIcon) {
            if (mAutoBrightOn) {
                 Settings.System.putIntForUser(mContext.getContentResolver(),
                        Settings.System.SCREEN_BRIGHTNESS_MODE,
                        Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
                        UserHandle.USER_CURRENT);
                 mAutoBrightOn = false;
            } else {
                 Settings.System.putIntForUser(mContext.getContentResolver(),
                        Settings.System.SCREEN_BRIGHTNESS_MODE,
                        Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC,
                        UserHandle.USER_CURRENT);
                 mAutoBrightOn = true;
            }
            setAutoBrightnessIcon(mAutoBrightOn);
        }

    }

    private void startRunningServicesActivity() {
        Intent intent = new Intent();
        intent.setClassName("com.android.settings",
                "com.android.settings.Settings$DevRunningServicesActivity");
        mActivityStarter.startActivity(intent, true /* dismissShade */);
    }

    private void startSettingsActivity() {
        mActivityStarter.startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS),
                true /* dismissShade */);
    }

    @Override
    public void onUserInfoChanged(String name, Drawable picture, String userAccount) {
        if (picture != null &&
                UserManager.get(mContext).isGuestUser(KeyguardUpdateMonitor.getCurrentUser()) &&
                !(picture instanceof UserIconDrawable)) {
            picture = picture.getConstantState().newDrawable(mContext.getResources()).mutate();
            picture.setColorFilter(
                    Utils.getColorAttrDefaultColor(mContext, android.R.attr.colorForeground),
                    Mode.SRC_IN);
        }
        mMultiUserAvatar.setImageDrawable(picture);
    }

    private void setHideAutoBright(boolean hide) {
        mAutoBrightnessIcon.setVisibility(hide ? View.GONE : View.VISIBLE);
        mShowAutoBrightnessButton = hide;
    }

    private void setAutoBrightnessIcon(boolean automatic) {
        mAutoBrightnessIcon.setImageResource(automatic ?
                com.android.systemui.R.drawable.ic_qs_brightness_auto_on_new :
                com.android.systemui.R.drawable.ic_qs_brightness_auto_off_new);
    }
}
