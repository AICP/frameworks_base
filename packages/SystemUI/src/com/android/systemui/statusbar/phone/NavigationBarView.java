/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.systemui.statusbar.phone;

import android.animation.LayoutTransition;
import android.animation.LayoutTransition.TransitionListener;
import android.animation.ObjectAnimator;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.app.ActivityManagerNative;
import android.app.KeyguardManager;
import android.app.StatusBarManager;
import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.graphics.Point;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.provider.Settings;
import android.provider.Settings.AOKP;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Display;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityManager.TouchExplorationStateChangeListener;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Space;
import com.android.internal.util.aokp.AwesomeConstants.AwesomeConstant;
import com.android.internal.widget.LockPatternUtils;

import com.android.systemui.R;
import com.android.systemui.statusbar.BaseStatusBar;
import com.android.systemui.statusbar.DelegateViewHelper;
import com.android.systemui.statusbar.policy.DeadZone;
import com.android.systemui.statusbar.policy.KeyButtonView;
import com.android.systemui.statusbar.policy.KeyButtonView.AwesomeButtonInfo;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;

public class NavigationBarView extends LinearLayout {
    final static boolean DEBUG = false;
    final static String TAG = "PhoneStatusBar/NavigationBarView";

    final static boolean NAVBAR_ALWAYS_AT_RIGHT = true;

    // slippery nav bar when everything is disabled, e.g. during setup
    final static boolean SLIPPERY_WHEN_DISABLED = true;

    private LockPatternUtils mLockUtils;

    final Display mDisplay;
    View mCurrentView = null;
    View[] mRotatedViews = new View[4];

    int mBarSize;
    boolean mVertical;
    boolean mScreenOn;
    boolean mLeftInLandscape;

    boolean mShowMenu;
    int mDisabledFlags = 0;
    int mNavigationIconHints = 0;

    int mNavigationBarForceMenu = 0;
    int mNavigationBarMenuLocation = 0;

    private float mButtonWidth, mMenuButtonWidth;
    private int mMenuButtonId, mMenuButtonIdTwo;

    final boolean mTablet = isTablet(getContext());

    private ArrayList<AwesomeButtonInfo> mNavButtons = new ArrayList<AwesomeButtonInfo>();

    private ContentObserver mSettingsObserver = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
            setupNavigationButtons();
            setMenuVisibility(mShowMenu, true /* force */);
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            setupNavigationButtons();
            setMenuVisibility(mShowMenu, true /* force */);
        }
    };

    boolean mWasNotifsButtonVisible = false;

    private DelegateViewHelper mDelegateHelper;
    private DeadZone mDeadZone;
    private final NavigationBarTransitions mBarTransitions;

    // workaround for LayoutTransitions leaving the nav buttons in a weird state (bug 5549288)
    final static boolean WORKAROUND_INVALID_LAYOUT = true;
    final static int MSG_CHECK_INVALID_LAYOUT = 8686;

    // used to disable the camera icon in navbar when disabled by DPM
    private boolean mCameraDisabledByDpm;

    // performs manual animation in sync with layout transitions
    private final NavTransitionListener mTransitionListener = new NavTransitionListener();

    private class NavTransitionListener implements TransitionListener {
        private boolean mBackTransitioning;
        private boolean mHomeAppearing;
        private long mStartDelay;
        private long mDuration;
        private TimeInterpolator mInterpolator;

        @Override
        public void startTransition(LayoutTransition transition, ViewGroup container,
                                    View view, int transitionType) {
            if (view.getTag() != null) {
                if (view.getTag().equals(AwesomeConstant.ACTION_BACK.value())) {
                    mBackTransitioning = true;
                } else if (view.getTag().equals(AwesomeConstant.ACTION_HOME.value())
                        && transitionType == LayoutTransition.APPEARING) {
                    mHomeAppearing = true;
                    mStartDelay = transition.getStartDelay(transitionType);
                    mDuration = transition.getDuration(transitionType);
                    mInterpolator = transition.getInterpolator(transitionType);
                }
            }
        }

        @Override
        public void endTransition(LayoutTransition transition, ViewGroup container,
                                  View view, int transitionType) {
            if (view.getTag() != null) {
                if (view.getTag().equals(AwesomeConstant.ACTION_BACK.value())) {
                    mBackTransitioning = false;
                } else if (view.getTag().equals(AwesomeConstant.ACTION_HOME.value())
                        && transitionType == LayoutTransition.APPEARING) {
                    mHomeAppearing = false;
                }
            }
        }

        public void onBackAltCleared() {
            // When dismissing ime during unlock, force the back button to run the same appearance
            // animation as home (if we catch this condition early enough).
            if (!mBackTransitioning && getBackButton() != null && getBackButton().getVisibility() == VISIBLE
                    && mHomeAppearing && getHomeButton() != null && getHomeButton().getAlpha() == 0) {
                ((KeyButtonView) getBackButton()).resetImage();
                getBackButton().setAlpha(0);
                ValueAnimator a = ObjectAnimator.ofFloat(getBackButton(), "alpha", 0, 1);
                a.setStartDelay(mStartDelay);
                a.setDuration(mDuration);
                a.setInterpolator(mInterpolator);
                a.start();
            }
        }
    }

    // simplified click handler to be used when device is in accessibility mode
    private final OnClickListener mAccessibilityClickListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            if (v.getId() == R.id.camera_button) {
                KeyguardTouchDelegate.getInstance(getContext()).launchCamera();
            } else if (v.getId() == R.id.search_light) {
                KeyguardTouchDelegate.getInstance(getContext()).showAssistant();
            }
        }
    };

    private final OnTouchListener mCameraTouchListener = new OnTouchListener() {
        @Override
        public boolean onTouch(View view, MotionEvent event) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    // disable search gesture while interacting with additional navbar button
                    mDelegateHelper.setDisabled(true);
                    mBarTransitions.setContentVisible(false);
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    mDelegateHelper.setDisabled(false);
                    mBarTransitions.setContentVisible(true);
                    break;
            }
            return KeyguardTouchDelegate.getInstance(getContext()).dispatch(event);
        }
    };

    private final OnClickListener mNavBarClickListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            KeyguardTouchDelegate.getInstance(getContext()).dispatchButtonClick(0);
        }
    };

    private class H extends Handler {
        public void handleMessage(Message m) {
            switch (m.what) {
                case MSG_CHECK_INVALID_LAYOUT:
                    final String how = "" + m.obj;
                    final int w = getWidth();
                    final int h = getHeight();
                    final int vw = mCurrentView.getWidth();
                    final int vh = mCurrentView.getHeight();

                    if (h != vh || w != vw) {
                        Log.w(TAG, String.format(
                                "*** Invalid layout in navigation bar (%s this=%dx%d cur=%dx%d)",
                                how, w, h, vw, vh));
                        if (WORKAROUND_INVALID_LAYOUT) {
                            requestLayout();
                        }
                    }
                    break;
            }
        }
    }

    public NavigationBarView(Context context, AttributeSet attrs) {
        super(context, attrs);

        mDisplay = ((WindowManager) context.getSystemService(
                Context.WINDOW_SERVICE)).getDefaultDisplay();

        final Resources res = mContext.getResources();
        mBarSize = res.getDimensionPixelSize(R.dimen.navigation_bar_size);
        mVertical = false;
        mShowMenu = false;
        mLeftInLandscape = false;
        mDelegateHelper = new DelegateViewHelper(this);
        mButtonWidth = res.getDimensionPixelSize(R.dimen.navigation_key_width);
        mMenuButtonWidth = res.getDimensionPixelSize(R.dimen.navigation_menu_key_width);

        mBarTransitions = new NavigationBarTransitions(this);

        mCameraDisabledByDpm = isCameraDisabledByDpm();
        watchForDevicePolicyChanges();

        mLockUtils = new LockPatternUtils(context);
    }

    private void watchForDevicePolicyChanges() {
        final IntentFilter filter = new IntentFilter();
        filter.addAction(DevicePolicyManager.ACTION_DEVICE_POLICY_MANAGER_STATE_CHANGED);
        mContext.registerReceiver(new BroadcastReceiver() {
            public void onReceive(Context context, Intent intent) {
                post(new Runnable() {
                    @Override
                    public void run() {
                        mCameraDisabledByDpm = isCameraDisabledByDpm();
                    }
                });
            }
        }, filter);
    }

    public BarTransitions getBarTransitions() {
        return mBarTransitions;
    }

    public void setDelegateView(View view) {
        mDelegateHelper.setDelegateView(view);
    }

    public void setBar(BaseStatusBar phoneStatusBar) {
        mDelegateHelper.setBar(phoneStatusBar);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (mDeadZone != null && event.getAction() == MotionEvent.ACTION_OUTSIDE) {
            mDeadZone.poke(event);
        }
        if (mDelegateHelper != null) {
            boolean ret = mDelegateHelper.onInterceptTouchEvent(event);
            if (ret) return true;
        }
        return super.onTouchEvent(event);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        return mDelegateHelper.onInterceptTouchEvent(event);
    }

    private H mHandler = new H();

    public View getCurrentView() {
        return mCurrentView;
    }

    public View getRecentsButton() {
        return mCurrentView.findViewWithTag(AwesomeConstant.ACTION_RECENTS.value());
    }

    public View getMenuButton() {
        return mCurrentView.findViewById(mMenuButtonId);
    }

    public View getMenuButtonTwo() {
        return mCurrentView.findViewById(mMenuButtonIdTwo);
    }

    public View getBackButton() {
        return mCurrentView.findViewWithTag(AwesomeConstant.ACTION_BACK.value());
    }

    public View getHomeButton() {
        return mCurrentView.findViewWithTag(AwesomeConstant.ACTION_HOME.value());
    }

    // for when home is disabled, but search isn't
    public View getSearchLight() {
        return mCurrentView.findViewById(R.id.search_light);
    }

    // shown when keyguard is visible and camera is available
    public View getCameraButton() {
        return mCurrentView.findViewById(R.id.camera_button);
    }

    // used for lockscreen notifications
    public View getNotifsButton() {
        return mCurrentView.findViewById(R.id.show_notifs);
    }

    public void updateResources() {
        for (int i = 0; i < mRotatedViews.length; i++) {
            ViewGroup container = (ViewGroup) mRotatedViews[i];
            if (container != null) {
                updateKeyButtonViewResources(container);
            }
        }
    }

    private void updateKeyButtonViewResources(ViewGroup container) {
        // TODO: fix this for AOKP
    }

    @Override
    public void setLayoutDirection(int layoutDirection) {

        super.setLayoutDirection(layoutDirection);
    }

    public void notifyScreenOn(boolean screenOn) {
        mScreenOn = screenOn;
        setDisabledFlags(mDisabledFlags, true);
    }

    public void setNavigationIconHints(int hints) {
        setNavigationIconHints(hints, false);
    }

    public void setNavigationIconHints(int hints, boolean force) {
        if (!force && hints == mNavigationIconHints) return;
        final boolean backAlt = (hints & StatusBarManager.NAVIGATION_HINT_BACK_ALT) != 0;
        if ((mNavigationIconHints & StatusBarManager.NAVIGATION_HINT_BACK_ALT) != 0 && !backAlt) {
            mTransitionListener.onBackAltCleared();
        }
        if (DEBUG) {
            android.widget.Toast.makeText(mContext,
                    "Navigation icon hints = " + hints,
                    500).show();
        }

        mNavigationIconHints = hints;

        if (getBackButton() != null) {
            ((ImageView) getBackButton()).setImageResource(backAlt
                    ? R.drawable.ic_sysbar_back_ime
                    : R.drawable.ic_sysbar_back);
        }

        setDisabledFlags(mDisabledFlags, true);
    }

    public void setButtonDrawable(int buttonId, final int iconId) {
        final ImageView iv = (ImageView)getNotifsButton();
        mHandler.post(new Runnable() {
            public void run() {
                if (iconId == 1) iv.setImageResource(R.drawable.search_light_land);
                mWasNotifsButtonVisible = iconId != 0 && ((mDisabledFlags & View.STATUS_BAR_DISABLE_HOME) != 0);
                setVisibleOrGone(getNotifsButton(), mWasNotifsButtonVisible);
            }
        });
    }

    public void setDisabledFlags(int disabledFlags) {
        setDisabledFlags(disabledFlags, false);
    }

    public void setDisabledFlags(int disabledFlags, boolean force) {
        if (!force && mDisabledFlags == disabledFlags) return;

        mDisabledFlags = disabledFlags;

        if (mNavButtons.isEmpty()) return; // no buttons yet!

        final boolean disableHome = ((disabledFlags & View.STATUS_BAR_DISABLE_HOME) != 0);
        final boolean disableRecent = ((disabledFlags & View.STATUS_BAR_DISABLE_RECENT) != 0);
        final boolean disableBack = ((disabledFlags & View.STATUS_BAR_DISABLE_BACK) != 0)
                && ((mNavigationIconHints & StatusBarManager.NAVIGATION_HINT_BACK_ALT) == 0);
        final boolean disableSearch = ((disabledFlags & View.STATUS_BAR_DISABLE_SEARCH) != 0);

        if (SLIPPERY_WHEN_DISABLED) {
            setSlippery(disableHome && disableRecent && disableBack && disableSearch);
        }

        ViewGroup navButtons = (ViewGroup) mCurrentView.findViewById(R.id.nav_buttons);
        if (navButtons != null) {
            LayoutTransition lt = navButtons.getLayoutTransition();
            if (lt != null) {
                if (!lt.getTransitionListeners().contains(mTransitionListener)) {
                    lt.addTransitionListener(mTransitionListener);
                }
                if (!mScreenOn && mCurrentView != null) {
                    lt.disableTransitionType(
                            LayoutTransition.CHANGE_APPEARING |
                                    LayoutTransition.CHANGE_DISAPPEARING |
                                    LayoutTransition.APPEARING |
                                    LayoutTransition.DISAPPEARING);
                }
            }
        }

        KeyButtonView[] allButtons = getAllButtons();
        for (KeyButtonView button : allButtons) {

            if (button != null) {
                Object tag = button.getTag();
                if (tag == null) {
                    setVisibleOrInvisible(button, !disableHome);
                } else if (AwesomeConstant.ACTION_HOME.value().equals(tag)) {
                    setVisibleOrInvisible(button, !disableHome);
                } else if (AwesomeConstant.ACTION_BACK.value().equals(tag)) {
                    setVisibleOrInvisible(button, !disableBack);
                } else if (AwesomeConstant.ACTION_RECENTS.value().equals(tag)) {
                    setVisibleOrInvisible(button, !disableRecent);
                } else {
                    // fall back to the recents flag for the other keys
                    setVisibleOrInvisible(button, !disableRecent);
                }
            }
        }

        KeyguardManager kgMgr =
            (KeyguardManager) mContext.getSystemService(Context.KEYGUARD_SERVICE);
        if (kgMgr.inKeyguardRestrictedInputMode()) {
            if (getMenuButton() != null) {
                getMenuButton().setVisibility(INVISIBLE);
            }

            if (getMenuButtonTwo() != null) {
                getMenuButtonTwo().setVisibility(INVISIBLE);
            }
        } else {
            setMenuVisibility(mShowMenu, true /* force */);
        }

        final boolean showSearch = disableHome && !disableSearch;
        final boolean showCamera = showSearch && !mCameraDisabledByDpm
                && mLockUtils.getCameraEnabled();
        final boolean showNotifs = disableHome && !disableSearch &&
            Settings.System.getInt(mContext.getContentResolver(),
                    Settings.System.LOCKSCREEN_NOTIFICATIONS, 0) == 1
            && Settings.System.getInt(mContext.getContentResolver(),
                    Settings.System.ACTIVE_NOTIFICATIONS, 0) == 1
            && Settings.System.getInt(mContext.getContentResolver(),
                    Settings.System.ACTIVE_NOTIFICATIONS_PRIVACY_MODE, 0) == 0;

        setVisibleOrGone(getSearchLight(), showSearch);
        setVisibleOrGone(getCameraButton(), showCamera);
        setVisibleOrGone(getNotifsButton(), showNotifs && mWasNotifsButtonVisible);

        mBarTransitions.applyBackButtonQuiescentAlpha(mBarTransitions.getMode(), true /*animate*/);

    }

    private void setVisibleOrInvisible(View view, boolean visible) {
        if (view != null) {
            view.setVisibility(visible ? VISIBLE : INVISIBLE);
        }
    }

    private void setVisibleOrGone(View view, boolean visible) {
        if (view != null) {
            view.setVisibility(visible ? VISIBLE : GONE);
        }
    }

    private boolean isCameraDisabledByDpm() {
        final DevicePolicyManager dpm =
                (DevicePolicyManager) mContext.getSystemService(Context.DEVICE_POLICY_SERVICE);
        if (dpm != null) {
            try {
                final int userId = ActivityManagerNative.getDefault().getCurrentUser().id;
                final int disabledFlags = dpm.getKeyguardDisabledFeatures(null, userId);
                final boolean disabledBecauseKeyguardSecure =
                        (disabledFlags & DevicePolicyManager.KEYGUARD_DISABLE_SECURE_CAMERA) != 0
                                && KeyguardTouchDelegate.getInstance(getContext()).isSecure();
                return dpm.getCameraDisabled(null) || disabledBecauseKeyguardSecure;
            } catch (RemoteException e) {
                Log.e(TAG, "Can't get userId", e);
            }
        }
        return false;
    }

    public void setSlippery(boolean newSlippery) {
        WindowManager.LayoutParams lp = (WindowManager.LayoutParams) getLayoutParams();
        if (lp != null) {
            boolean oldSlippery = (lp.flags & WindowManager.LayoutParams.FLAG_SLIPPERY) != 0;
            if (!oldSlippery && newSlippery) {
                lp.flags |= WindowManager.LayoutParams.FLAG_SLIPPERY;
            } else if (oldSlippery && !newSlippery) {
                lp.flags &= ~WindowManager.LayoutParams.FLAG_SLIPPERY;
            } else {
                return;
            }
            WindowManager wm = (WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE);
            wm.updateViewLayout(this, lp);
        }
    }

    public void setMenuVisibility(final boolean show) {
        setMenuVisibility(show, false);
    }

    public void setMenuVisibility(final boolean show, final boolean force) {
        if (!force && mShowMenu == show) return;

        readUserConfig();

        mShowMenu = show;

        if (getMenuButton() != null) {
            if (mNavigationBarMenuLocation != 0) {
                if (mNavigationBarForceMenu == 1) {
                    getMenuButton().setVisibility(View.VISIBLE);
                } else {
                    getMenuButton().setVisibility(mShowMenu ? View.VISIBLE : View.INVISIBLE);
                }
            } else {
                getMenuButton().setVisibility(View.INVISIBLE);
            }
        }

        if (getMenuButtonTwo() != null) {
            if (mNavigationBarMenuLocation != 1) {
                if (mNavigationBarForceMenu == 1) {
                    getMenuButtonTwo().setVisibility(View.VISIBLE);
                } else {
                    getMenuButtonTwo().setVisibility(mShowMenu ? View.VISIBLE : View.INVISIBLE);
                }
            } else {
                getMenuButtonTwo().setVisibility(View.INVISIBLE);
            }
        }
    }

    @Override
    public void onFinishInflate() {
        mRotatedViews[Surface.ROTATION_0] =
                mRotatedViews[Surface.ROTATION_180] = findViewById(R.id.rot0);

        mRotatedViews[Surface.ROTATION_90] = findViewById(R.id.rot90);

        mRotatedViews[Surface.ROTATION_270] = NAVBAR_ALWAYS_AT_RIGHT
                ? findViewById(R.id.rot90)
                : findViewById(R.id.rot270);

        mCurrentView = mRotatedViews[Surface.ROTATION_0];

        watchForAccessibilityChanges();
        setupNavigationButtons();
        setDisabledFlags(mDisabledFlags);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        mContext.getContentResolver().registerContentObserver(Settings.AOKP.getUriFor(AOKP.NAVIGATION_BAR_BUTTONS),
                false, mSettingsObserver);
        mContext.getContentResolver().registerContentObserver(Settings.System.getUriFor(Settings.System.NAVIGATION_MENU),
                false, mSettingsObserver);
        mContext.getContentResolver().registerContentObserver(Settings.System.getUriFor(Settings.System.NAVIGATION_MENU_FORCE),
                false, mSettingsObserver);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        mContext.getContentResolver().unregisterContentObserver(mSettingsObserver);
    }

    private void readUserConfig() {
        mNavigationBarForceMenu = Settings.System.getInt(getContext().getContentResolver(),
                Settings.System.NAVIGATION_MENU_FORCE, 0);
        mNavigationBarMenuLocation = Settings.System.getInt(getContext().getContentResolver(),
                Settings.System.NAVIGATION_MENU, 0);

        mNavButtons.clear();
        String buttons = Settings.AOKP.getString(getContext().getContentResolver(), Settings.AOKP.NAVIGATION_BAR_BUTTONS);
        if (buttons == null || buttons.isEmpty()) {
            // use default buttons
            mNavButtons.add(new AwesomeButtonInfo(
                    AwesomeConstant.ACTION_BACK.value(),    /* short press */
                    null,                                   /* double press */
                    null,                                   /* long press */
                    null                                    /* icon */
            ));
            mNavButtons.add(new AwesomeButtonInfo(
                    AwesomeConstant.ACTION_HOME.value(),           /* short press */
                    null,                                          /* double press */
                    null,                                          /* long press */
                    null                                           /* icon */
            ));
            mNavButtons.add(new AwesomeButtonInfo(
                    AwesomeConstant.ACTION_RECENTS.value(),        /* short press */
                    null,                                          /* double press */
                    null,                                          /* long press */
                    null                                           /* icon */
            ));
        } else {
            /**
             * Format:
             *
             * singleTapAction,doubleTapAction,longPressAction,iconUri|singleTap...
             */
            String[] userButtons = buttons.split("\\|");
            if (userButtons != null) {
                for (String button : userButtons) {
                    String[] actions = button.split(",", 4);
                    mNavButtons.add(new AwesomeButtonInfo(actions[0], actions[1], actions[2], actions[3]));
                }
            }
        }
    }

    private void setupNavigationButtons() {
        readUserConfig();
        final boolean stockThreeButtonLayout = mNavButtons.size() == 3;
        int separatorSize = (int) mMenuButtonWidth;

        for (int i = 0; i <= 1; i++) {
            boolean landscape = (i == 1);

            LinearLayout navButtons = (LinearLayout) (landscape ? mRotatedViews[Surface.ROTATION_90]
                    .findViewById(R.id.nav_buttons) : mRotatedViews[Surface.ROTATION_0]
                    .findViewById(R.id.nav_buttons));
            LinearLayout lightsOut = (LinearLayout) (landscape ? mRotatedViews[Surface.ROTATION_90]
                    .findViewById(R.id.lights_out) : mRotatedViews[Surface.ROTATION_0]
                    .findViewById(R.id.lights_out));

            navButtons.removeAllViews();
            lightsOut.removeAllViews();

            // legacy menu button
            AwesomeButtonInfo menuButtonInfo = new AwesomeButtonInfo(AwesomeConstant.ACTION_MENU.value(),
                    null, null, null);
            KeyButtonView menuButton = new KeyButtonView(getContext(), null);
            menuButton.setButtonActions(menuButtonInfo);
            menuButton.setImageResource(R.drawable.ic_sysbar_menu);
            menuButton.setLayoutParams(getLayoutParams(landscape, mMenuButtonWidth, 0f));
            menuButton.setGlowBackground(landscape ? R.drawable.ic_sysbar_highlight_land
                    : R.drawable.ic_sysbar_highlight);
            menuButton.setVisibility(mShowMenu ? View.VISIBLE : View.INVISIBLE);
            if (mMenuButtonId == 0) {
                // assign the same id for layout and horizontal buttons
                mMenuButtonId = View.generateViewId();
            }
            menuButton.setId(mMenuButtonId);
            // MENU BUTTON NOT YET ADDED ANYWHERE!

            if (mTablet) {
                // om nom
                addSeparator(navButtons, landscape, 0,  stockThreeButtonLayout ? 1f : 0.5f);
                addSeparator(lightsOut, landscape, 0,  stockThreeButtonLayout ? 1f : 0.5f);

                // add the button last so it hangs on the edge
                addButton(navButtons, menuButton, landscape);
                addLightsOutButton(lightsOut, menuButton, landscape, true);
            } else {
                addButton(navButtons, menuButton, landscape);
                addLightsOutButton(lightsOut, menuButton, landscape, true);
            }

            for (int j = 0; j < mNavButtons.size(); j++) {
                // create the button
                AwesomeButtonInfo info = mNavButtons.get(j);
                KeyButtonView button = new KeyButtonView(getContext(), null);
                button.setButtonActions(info);
                if (mTablet) {
                    if (mNavButtons.size() <= 4) {
                        // use stock tablet button spacing, even with 4 buttons it seems to work
                        int padding = getResources().getDimensionPixelSize(landscape
                                ? R.dimen.navigation_tablet_key_padding_land
                                : R.dimen.navigation_tablet_key_padding
                        );
                        int width = getResources().getDimensionPixelSize(landscape
                                ? R.dimen.navigation_tablet_key_width_land
                                : R.dimen.navigation_tablet_key_width
                        );
                        button.setLayoutParams(getLayoutParams(landscape, width, 0f));
                        button.setPaddingRelative(padding, 0, padding, 0);
                    } else {
                        // 5 or more buttons don't fit in portrait, so spread them all out equally
                        button.setLayoutParams(getLayoutParams(landscape, mButtonWidth, 1f));
                    }

                    button.setGlowBackground(R.drawable.ic_sysbar_highlight);
                } else {
                    button.setLayoutParams(getLayoutParams(landscape, mButtonWidth, stockThreeButtonLayout ? 0f : 0.5f));
                    button.setGlowBackground(landscape ? R.drawable.ic_sysbar_highlight_land
                            : R.drawable.ic_sysbar_highlight);
                }

                // add button
                addButton(navButtons, button, landscape);
                addLightsOutButton(lightsOut, button, landscape, false);

                if (!mTablet && stockThreeButtonLayout && j != (mNavButtons.size() - 1)) {
                    // in the case of a 'stock' 3-button layout, the buttons need to be spaced further out apart
                    addSeparator(navButtons, landscape, separatorSize, 0.5f);
                    addSeparator(lightsOut, landscape, separatorSize, 0.5f);
                }
            }

            // legacy menu button
            AwesomeButtonInfo menuButtonInfoTwo = new AwesomeButtonInfo(AwesomeConstant.ACTION_MENU.value(),
                    null, null, null);
            KeyButtonView menuButtonTwo = new KeyButtonView(getContext(), null);
            menuButtonTwo.setButtonActions(menuButtonInfoTwo);
            menuButtonTwo.setImageResource(R.drawable.ic_sysbar_menu);
            menuButtonTwo.setLayoutParams(getLayoutParams(landscape, mMenuButtonWidth, 0f));
            menuButtonTwo.setGlowBackground(landscape ? R.drawable.ic_sysbar_highlight_land
                    : R.drawable.ic_sysbar_highlight);
            menuButtonTwo.setVisibility(mShowMenu ? View.VISIBLE : View.INVISIBLE);
            if (mMenuButtonIdTwo == 0) {
                // assign the same id for layout and horizontal buttons
                mMenuButtonIdTwo = View.generateViewId();
            }
            menuButtonTwo.setId(mMenuButtonIdTwo);
            // MENU BUTTON NOT YET ADDED ANYWHERE!

            if (mTablet) {
                // om nom
                addSeparator(navButtons, landscape, 0,  stockThreeButtonLayout ? 1f : 0.5f);
                addSeparator(lightsOut, landscape, 0,  stockThreeButtonLayout ? 1f : 0.5f);

                // add the button last so it hangs on the edge
                addButton(navButtons, menuButtonTwo, landscape);
                addLightsOutButton(lightsOut, menuButtonTwo, landscape, true);
            } else {
                addButton(navButtons, menuButtonTwo, landscape);
                addLightsOutButton(lightsOut, menuButtonTwo, landscape, true);
            }
        }
        invalidate();
    }

    private void watchForAccessibilityChanges() {
        final AccessibilityManager am =
                (AccessibilityManager) mContext.getSystemService(Context.ACCESSIBILITY_SERVICE);

        // Set the initial state
        enableAccessibility(am.isTouchExplorationEnabled());

        // Watch for changes
        am.addTouchExplorationStateChangeListener(new TouchExplorationStateChangeListener() {
            @Override
            public void onTouchExplorationStateChanged(boolean enabled) {
                enableAccessibility(enabled);
            }
        });
    }

    private void enableAccessibility(boolean touchEnabled) {
        Log.v(TAG, "touchEnabled:" + touchEnabled);

        // Add a touch handler or accessibility click listener for camera and search buttons
        // for all view orientations.
        final OnClickListener onClickListener = touchEnabled ? mAccessibilityClickListener : null;
        final OnTouchListener onTouchListener = touchEnabled ? null : mCameraTouchListener;
        boolean hasCamera = false;
        for (int i = 0; i < mRotatedViews.length; i++) {
            final View cameraButton = mRotatedViews[i].findViewById(R.id.camera_button);
            final View notifsButton = mRotatedViews[i].findViewById(R.id.show_notifs);
            final View searchLight = mRotatedViews[i].findViewById(R.id.search_light);
            if (cameraButton != null) {
                hasCamera = true;
                cameraButton.setOnTouchListener(onTouchListener);
                cameraButton.setOnClickListener(onClickListener);
            }
            if (notifsButton != null) {
                notifsButton.setOnClickListener(mNavBarClickListener);
            }
            if (searchLight != null) {
                searchLight.setOnClickListener(onClickListener);
            }
        }
        if (hasCamera) {
            // Warm up KeyguardTouchDelegate so it's ready by the time the camera button is touched.
            // This will connect to KeyguardService so that touch events are processed.
            KeyguardTouchDelegate.getInstance(mContext);
        }
    }

    public boolean isVertical() {
        return mVertical;
    }

    public void setLeftInLandscape(boolean leftInLandscape) {
        mLeftInLandscape = leftInLandscape;
        mDeadZone.setStartFromRight(leftInLandscape);
    }

    public void reorient() {
        final int rot = mDisplay.getRotation();
        for (int i = 0; i < 4; i++) {
            mRotatedViews[i].setVisibility(View.GONE);
        }

        mCurrentView = mRotatedViews[rot];
        mCurrentView.setVisibility(View.VISIBLE);

        mDeadZone = (DeadZone) mCurrentView.findViewById(R.id.deadzone);
        mDeadZone.setStartFromRight(mLeftInLandscape);

        // force the low profile & disabled states into compliance
        mBarTransitions.init(mVertical);
        setDisabledFlags(mDisabledFlags, true /* force */);
        setMenuVisibility(mShowMenu, true /* force */);

        if (DEBUG) {
            Log.d(TAG, "reorient(): rot=" + mDisplay.getRotation());
        }

        setNavigationIconHints(mNavigationIconHints, true);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        mDelegateHelper.setInitialTouchRegion(getAllButtons());
    }

    public KeyButtonView[] getAllButtons() {
        ViewGroup view = (ViewGroup) mCurrentView.findViewById(R.id.nav_buttons);
        int N = view.getChildCount();
        KeyButtonView[] views = new KeyButtonView[mNavButtons.size()];

        int workingIdx = 0;
        for (int i = 0; i < N; i++) {
            View child = view.getChildAt(i);
            if (child.getId() == mMenuButtonId || child.getId() == mMenuButtonIdTwo) {
                // included in container but not in buttons array
                continue;
            }
            if (child instanceof KeyButtonView) {
                views[workingIdx++] = (KeyButtonView) child;
            }
        }
        return views;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        if (DEBUG) Log.d(TAG, String.format(
                "onSizeChanged: (%dx%d) old: (%dx%d)", w, h, oldw, oldh));

        final boolean newVertical = w > 0 && h > w;
        if (newVertical != mVertical) {
            mVertical = newVertical;
            //Log.v(TAG, String.format("onSizeChanged: h=%d, w=%d, vert=%s", h, w, mVertical?"y":"n"));
            reorient();
        }

        postCheckForInvalidLayout("sizeChanged");
        super.onSizeChanged(w, h, oldw, oldh);
    }

    /*
    @Override
    protected void onLayout (boolean changed, int left, int top, int right, int bottom) {
        if (DEBUG) Log.d(TAG, String.format(
                    "onLayout: %s (%d,%d,%d,%d)",
                    changed?"changed":"notchanged", left, top, right, bottom));
        super.onLayout(changed, left, top, right, bottom);
    }

    // uncomment this for extra defensiveness in WORKAROUND_INVALID_LAYOUT situations: if all else
    // fails, any touch on the display will fix the layout.
    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (DEBUG) Log.d(TAG, "onInterceptTouchEvent: " + ev.toString());
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            postCheckForInvalidLayout("touch");
        }
        return super.onInterceptTouchEvent(ev);
    }
    */


    private String getResourceName(int resId) {
        if (resId != 0) {
            final android.content.res.Resources res = mContext.getResources();
            try {
                return res.getResourceName(resId);
            } catch (android.content.res.Resources.NotFoundException ex) {
                return "(unknown)";
            }
        } else {
            return "(null)";
        }
    }

    private void postCheckForInvalidLayout(final String how) {
        mHandler.obtainMessage(MSG_CHECK_INVALID_LAYOUT, 0, 0, how).sendToTarget();
    }

    private static String visibilityToString(int vis) {
        switch (vis) {
            case View.INVISIBLE:
                return "INVISIBLE";
            case View.GONE:
                return "GONE";
            default:
                return "VISIBLE";
        }
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("NavigationBarView {");
        final Rect r = new Rect();
        final Point size = new Point();
        mDisplay.getRealSize(size);

        pw.println(String.format("      this: " + PhoneStatusBar.viewInfo(this)
                + " " + visibilityToString(getVisibility())));

        getWindowVisibleDisplayFrame(r);
        final boolean offscreen = r.right > size.x || r.bottom > size.y;
        pw.println("      window: "
                + r.toShortString()
                + " " + visibilityToString(getWindowVisibility())
                + (offscreen ? " OFFSCREEN!" : ""));

        pw.println(String.format("      mCurrentView: id=%s (%dx%d) %s",
                getResourceName(mCurrentView.getId()),
                mCurrentView.getWidth(), mCurrentView.getHeight(),
                visibilityToString(mCurrentView.getVisibility())));

        pw.println(String.format("      disabled=0x%08x vertical=%s menu=%s",
                mDisabledFlags,
                mVertical ? "true" : "false",
                mShowMenu ? "true" : "false"));

        dumpButton(pw, "back", getBackButton());
        dumpButton(pw, "home", getHomeButton());
        dumpButton(pw, "rcnt", getRecentsButton());
        dumpButton(pw, "menu", getMenuButton());
        dumpButton(pw, "srch", getSearchLight());
        dumpButton(pw, "cmra", getCameraButton());

        pw.println("    }");
    }

    private static void dumpButton(PrintWriter pw, String caption, View button) {
        pw.print("      " + caption + ": ");
        if (button == null) {
            pw.print("null");
        } else {
            pw.print(PhoneStatusBar.viewInfo(button)
                    + " " + visibilityToString(button.getVisibility())
                    + " alpha=" + button.getAlpha()
            );
            if (button instanceof KeyButtonView) {
                pw.print(" drawingAlpha=" + ((KeyButtonView) button).getDrawingAlpha());
                pw.print(" quiescentAlpha=" + ((KeyButtonView) button).getQuiescentAlpha());
            }
        }
        pw.println();
    }

    private void addSeparator(LinearLayout layout, boolean landscape, int size, float weight) {
        Space separator = new Space(mContext);
        separator.setLayoutParams(getLayoutParams(landscape, size, weight));
        if (landscape && !mTablet) {
            layout.addView(separator, 0);
        } else {
            layout.addView(separator);
        }
    }

    private void addButton(ViewGroup root, View v, boolean landscape) {
        if (landscape && !mTablet)
            root.addView(v, 0);
        else
            root.addView(v);
    }

    private void addLightsOutButton(LinearLayout root, View v, boolean landscape, boolean empty) {
        ImageView addMe = new ImageView(mContext);
        addMe.setLayoutParams(v.getLayoutParams());
        addMe.setImageResource(empty ? R.drawable.ic_sysbar_lights_out_dot_large
                : R.drawable.ic_sysbar_lights_out_dot_small);
        addMe.setScaleType(ImageView.ScaleType.CENTER);
        addMe.setVisibility(empty ? View.INVISIBLE : View.VISIBLE);

        if (landscape && !mTablet)
            root.addView(addMe, 0);
        else
            root.addView(addMe);
    }

    public LinearLayout.LayoutParams getLayoutParams(boolean landscape, float px, float weight) {
        if (weight != 0) {
            px = 0;
        }
        return landscape && !mTablet ?
                new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (int) px, weight) :
                new LinearLayout.LayoutParams((int) px, LinearLayout.LayoutParams.MATCH_PARENT, weight);
    }

    public static boolean isTablet(Context context) {
        boolean xlarge = ((context.getResources().getConfiguration().screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK) == 4);
        boolean large = ((context.getResources().getConfiguration().screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK) == Configuration.SCREENLAYOUT_SIZE_LARGE);
        return (xlarge || large);
    }
}
