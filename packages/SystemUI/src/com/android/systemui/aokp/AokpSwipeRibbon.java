/*
 * Copyright (C) 2013 The Android Open Kand Project
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

package com.android.systemui.aokp;

import com.android.systemui.R;

import java.lang.IllegalArgumentException;
import java.io.File;
import java.util.ArrayList;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.view.animation.AnimationUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.ScrollView;
import android.widget.Button;

import com.android.internal.util.aokp.AokpRibbonHelper;
import com.android.internal.util.aokp.BackgroundAlphaColorDrawable;
import com.android.systemui.aokp.RibbonGestureCatcherView;

public class AokpSwipeRibbon extends LinearLayout {
    public static final String TAG = "NAVIGATION BAR RIBBON";

    private Context mContext;
    private RibbonGestureCatcherView mGesturePanel;
    public FrameLayout mPopupView;
    public WindowManager mWindowManager;
    private SettingsObserver mSettingsObserver;
    private LinearLayout mRibbon;
    private LinearLayout mRibbonMain;
    private Button mBackGround;
    private boolean mText, hasNavBarByDefault, NavBarEnabled, navAutoHide, mNavBarShowing, mVib;
    private int mHideTimeOut = 5000;
    private boolean showing = false;
    private boolean animating = false;
    private int mSize, mColor, mTextColor, mOpacity, animationIn, animationOut, mIconLoc, mVerticalPad, mHorizontalPad;
    private ArrayList<String> shortTargets = new ArrayList<String>();
    private ArrayList<String> longTargets = new ArrayList<String>();
    private ArrayList<String> customIcons = new ArrayList<String>();
    private String mLocation;
    private Handler mHandler;
    private boolean[] mEnableSides = new boolean[3];

    private static final LinearLayout.LayoutParams backgroundParams = new LinearLayout.LayoutParams(
            LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT);

    public AokpSwipeRibbon(Context context, AttributeSet attrs, String location) {
        super(context, attrs);
        mContext = context;
        mLocation = location;
        IntentFilter filter = new IntentFilter();
        filter.addAction(RibbonReceiver.ACTION_TOGGLE_RIBBON);
        filter.addAction(RibbonReceiver.ACTION_SHOW_RIBBON);
        filter.addAction(RibbonReceiver.ACTION_HIDE_RIBBON);
        mContext.registerReceiver(new RibbonReceiver(), filter);
        mWindowManager = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
        mHandler = new Handler();
        mSettingsObserver = new SettingsObserver(new Handler());
        mSettingsObserver.observe();
        updateSettings();
    }


    public void toggleRibbonView() {
        if (showing) {
            hideRibbonView();
        } else {
            showRibbonView();
        }
    }

    public void showRibbonView() {
        if (!showing) {
            showing = true;
            WindowManager.LayoutParams params = getParams();
            params.gravity = getGravity();
            params.setTitle("Ribbon" + mLocation);
            if (mWindowManager != null) {
                if (mGesturePanel != null) {
                    try {
                        mWindowManager.removeView(mGesturePanel);
                    } catch (IllegalArgumentException e) {
                        //If we try to remove the gesture panel and it's not currently attached.
                    }
                }
                mWindowManager.addView(mPopupView, params);
                PlayInAnim();
                if (mHideTimeOut > 0) {
                    mHandler.postDelayed(delayHide, mHideTimeOut);
                }
            }
        }
    }

    public void hideRibbonView() {
        if (mPopupView != null && showing) {
            showing = false;
            PlayOutAnim();
        }
    }

    private Runnable delayHide = new Runnable() {
        public void run() {
            if (showing) {
                hideRibbonView();
            }
        }
    };

    private WindowManager.LayoutParams getParams() {
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                mLocation.equals("bottom") ? WindowManager.LayoutParams.MATCH_PARENT
                    : WindowManager.LayoutParams.WRAP_CONTENT,
                mLocation.equals("bottom") ? WindowManager.LayoutParams.WRAP_CONTENT
                    : WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT);
        return params;
    }

    private int getGravity() {
        int gravity = 0;
        if (mLocation.equals("bottom")) {
            gravity = Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM;
            animationIn = com.android.internal.R.anim.slide_in_up;
            animationOut = com.android.internal.R.anim.slide_out_down;
        } else if (mLocation.equals("left")) {
            gravity = Gravity.CENTER_VERTICAL | Gravity.LEFT;
            animationIn = com.android.internal.R.anim.slide_in_left;
            animationOut = com.android.internal.R.anim.slide_out_left;
        } else {
            gravity = Gravity.CENTER_VERTICAL | Gravity.RIGHT;
            animationIn = com.android.internal.R.anim.slide_in_right;
            animationOut = com.android.internal.R.anim.slide_out_right;
        }
        return gravity;
    }

    public void createRibbonView() {
        if (mGesturePanel != null) {
            try {
                mWindowManager.removeView(mGesturePanel);
            } catch (IllegalArgumentException e) {
                //If we try to remove the gesture panel and it's not currently attached.
            }
        }
        if (sideEnabled()) {
            mGesturePanel = new RibbonGestureCatcherView(mContext,null,mLocation);
            mWindowManager.addView(mGesturePanel, mGesturePanel.getGesturePanelLayoutParams());
        }
        mPopupView = new FrameLayout(mContext);
        mPopupView.removeAllViews();
        if (mNavBarShowing) {
            int adjustment = mContext.getResources().getDimensionPixelSize(
                        com.android.internal.R.dimen.status_bar_height);
            mPopupView.setPadding(0, adjustment, 0, 0);
        }
        mBackGround = new Button(mContext);
        mBackGround.setClickable(false);
        mBackGround.setBackgroundColor(mColor);
        float opacity = (255f * (mOpacity * 0.01f));
        mBackGround.getBackground().setAlpha((int)opacity);
        View ribbonView = View.inflate(mContext, R.layout.aokp_swipe_ribbon, null);
        mRibbonMain = (LinearLayout) ribbonView.findViewById(R.id.ribbon_main);
        switch (mIconLoc) {
            case 0:
                mRibbonMain.setGravity(Gravity.CENTER_VERTICAL | Gravity.CENTER_HORIZONTAL);
                break;
            case 1:
                mRibbonMain.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
                break;
            case 2:
                mRibbonMain.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
                break;
        }
        mRibbon = (LinearLayout) ribbonView.findViewById(R.id.ribbon);
        setupRibbon();
        mPopupView.addView(mBackGround, backgroundParams);
        mPopupView.addView(ribbonView);
        mPopupView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_OUTSIDE) {
                    mHandler.removeCallbacks(delayHide);
                    if (showing) {
                        hideRibbonView();
                    }
                    return true;
                }
                return false;
            }
        });
    }

    private boolean sideEnabled() {
        if (mLocation.equals("bottom") && mEnableSides[0]) {
            return true;
        } else if (mLocation.equals("left") && mEnableSides[1]) {
            return true;
        } else if (mLocation.equals("right") && mEnableSides[2]) {
            return true;
        }
        return false;
    }

    public Animation PlayInAnim() {
        if (mRibbon != null) {
            Animation animation = AnimationUtils.loadAnimation(mContext, animationIn);
            animation.setStartOffset(0);
            mBackGround.startAnimation(animation);
            mRibbonMain.startAnimation(animation);
            return animation;
        }
        return null;
    }

    public Animation PlayOutAnim() {
        if (mRibbon != null) {
            Animation animation = AnimationUtils.loadAnimation(mContext, animationOut);
            animation.setStartOffset(0);
            mBackGround.startAnimation(animation);
            mRibbonMain.startAnimation(animation);
            animation.setAnimationListener(new Animation.AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {
                    animating = true;
                }

                @Override
                public void onAnimationEnd(Animation animation) {
                    mWindowManager.removeView(mPopupView);
                        if (mGesturePanel != null) {
                            mWindowManager.addView(mGesturePanel, mGesturePanel.getGesturePanelLayoutParams());
                        }
                    animating = false;
                }

                @Override
                public void onAnimationRepeat(Animation animation) {
                }
            });
            return animation;
        }
        return null;
    }

    private void setupRibbon() {
        mRibbon.removeAllViews();
        if (mLocation.equals("bottom")) {
            HorizontalScrollView hsv = new HorizontalScrollView(mContext);
            hsv = AokpRibbonHelper.getRibbon(mContext,
                shortTargets, longTargets, customIcons, mText, mTextColor, mSize, mHorizontalPad, mVib);
            hsv.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    mHandler.removeCallbacks(delayHide);
                    if (mHideTimeOut > 0) {
                        mHandler.postDelayed(delayHide, mHideTimeOut);
                    }
                    return false;
                }
            });
            mRibbon.addView(hsv);
        } else {
            ScrollView sv = new ScrollView(mContext);
            sv = AokpRibbonHelper.getVerticalRibbon(mContext,
                shortTargets, longTargets, customIcons, mText, mTextColor, mSize, mVerticalPad, mVib);
            sv.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    mHandler.removeCallbacks(delayHide);
                    if (mHideTimeOut > 0) {
                        mHandler.postDelayed(delayHide, mHideTimeOut);
                    }
                    return false;
                }
            });
            mRibbon.addView(sv);
            mRibbon.setPadding(0, 0, 0, 0);
        }
    }

    class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }
        void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.RIBBON_TARGETS_SHORT[AokpRibbonHelper.SWIPE_RIBBON]), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.RIBBON_TARGETS_LONG[AokpRibbonHelper.SWIPE_RIBBON]), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.RIBBON_TARGETS_ICONS[AokpRibbonHelper.SWIPE_RIBBON]), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.ENABLE_RIBBON_TEXT[AokpRibbonHelper.SWIPE_RIBBON]), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.RIBBON_ICON_SIZE[AokpRibbonHelper.SWIPE_RIBBON]), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.RIBBON_HIDE_TIMEOUT), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.NAV_HIDE_ENABLE), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.SWIPE_RIBBON_COLOR), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.SWIPE_RIBBON_OPACITY), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.RIBBON_DRAG_HANDLE_LOCATION), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.RIBBON_TEXT_COLOR[AokpRibbonHelper.SWIPE_RIBBON]), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.RIBBON_ICON_LOCATION), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.RIBBON_ICON_SPACE[AokpRibbonHelper.SWIPE_RIBBON]), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.RIBBON_ICON_SPACE_VERTICAL), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.RIBBON_ICON_VIBRATE[AokpRibbonHelper.SWIPE_RIBBON]), false, this);
            for (int i = 0; i < 3; i++) {
	            resolver.registerContentObserver(
                    Settings.System.getUriFor(Settings.System.ENABLE_RIBBON_LOCATION[i]), false, this);
            }
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.NAVIGATION_BAR_SHOW_NOW), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.NAVIGATION_BAR_HEIGHT), false, this);

        }
         @Override
        public void onChange(boolean selfChange) {
            updateSettings();
        }
    }
   protected void updateSettings() {
        ContentResolver cr = mContext.getContentResolver();
        shortTargets = Settings.System.getArrayList(cr,
                 Settings.System.RIBBON_TARGETS_SHORT[AokpRibbonHelper.SWIPE_RIBBON]);
        longTargets = Settings.System.getArrayList(cr,
                 Settings.System.RIBBON_TARGETS_LONG[AokpRibbonHelper.SWIPE_RIBBON]);
        customIcons = Settings.System.getArrayList(cr,
                 Settings.System.RIBBON_TARGETS_ICONS[AokpRibbonHelper.SWIPE_RIBBON]);
        mText = Settings.System.getBoolean(cr,
                 Settings.System.ENABLE_RIBBON_TEXT[AokpRibbonHelper.SWIPE_RIBBON], true);
        mTextColor = Settings.System.getInt(cr,
                 Settings.System.RIBBON_TEXT_COLOR[AokpRibbonHelper.SWIPE_RIBBON], -1);
        mSize = Settings.System.getInt(cr,
                 Settings.System.RIBBON_ICON_SIZE[AokpRibbonHelper.SWIPE_RIBBON], 0);
        mHideTimeOut = Settings.System.getInt(cr,
                 Settings.System.RIBBON_HIDE_TIMEOUT, mHideTimeOut);
        mColor = Settings.System.getInt(cr,
                 Settings.System.SWIPE_RIBBON_COLOR, Color.BLACK);
        mOpacity = Settings.System.getInt(cr,
                 Settings.System.SWIPE_RIBBON_OPACITY, 255);
        mIconLoc = Settings.System.getInt(cr,
                 Settings.System.RIBBON_ICON_LOCATION, 0);
        mHorizontalPad = Settings.System.getInt(cr,
                 Settings.System.RIBBON_ICON_SPACE[AokpRibbonHelper.SWIPE_RIBBON], 5);
        mVerticalPad = Settings.System.getInt(cr,
                 Settings.System.RIBBON_ICON_SPACE_VERTICAL, 1);
        mVib = Settings.System.getBoolean(cr,
                 Settings.System.RIBBON_ICON_VIBRATE[AokpRibbonHelper.SWIPE_RIBBON], true);

        for (int i = 0; i < 3; i++) {
            mEnableSides[i] = Settings.System.getBoolean(cr,
                 Settings.System.ENABLE_RIBBON_LOCATION[i], false);
        }
        boolean manualNavBarHide = Settings.System.getBoolean(mContext.getContentResolver(), Settings.System.NAVIGATION_BAR_SHOW_NOW, true);
        boolean navHeightZero = Settings.System.getInt(mContext.getContentResolver(), Settings.System.NAVIGATION_BAR_HEIGHT, 10) < 5;
        navAutoHide = Settings.System.getBoolean(cr, Settings.System.NAV_HIDE_ENABLE, false);
        NavBarEnabled = Settings.System.getBoolean(cr, Settings.System.NAVIGATION_BAR_SHOW, false);
        hasNavBarByDefault = mContext.getResources().getBoolean(com.android.internal.R.bool.config_showNavigationBar);
        mNavBarShowing = (NavBarEnabled || hasNavBarByDefault) && manualNavBarHide && !navHeightZero && !navAutoHide;
        mEnableSides[0] = mEnableSides[0] && !(NavBarEnabled || hasNavBarByDefault);
        if (!showing && !animating) {
            createRibbonView();
        }
    }

    public class RibbonReceiver extends BroadcastReceiver {
        public static final String ACTION_TOGGLE_RIBBON = "com.android.systemui.ACTION_TOGGLE_RIBBON";
        public static final String ACTION_SHOW_RIBBON = "com.android.systemui.ACTION_SHOW_RIBBON";
        public static final String ACTION_HIDE_RIBBON = "com.android.systemui.ACTION_HIDE_RIBBON";
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            String location = intent.getStringExtra("action");
            if (ACTION_TOGGLE_RIBBON.equals(action)) {
                mHandler.removeCallbacks(delayHide);
                if (location.equals(mLocation)) {
                    toggleRibbonView();
                }
            } else if (ACTION_SHOW_RIBBON.equals(action)) {
                if (location.equals(mLocation)) {
                    if (!showing) {
                        showRibbonView();
                    }
                }
            } else if (ACTION_HIDE_RIBBON.equals(action)) {
                mHandler.removeCallbacks(delayHide);
                if (showing) {
                    hideRibbonView();
                }
            }
        }
    }
}
