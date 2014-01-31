/*
 * Copyright (C) 2013 The Android Open Kang Project
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

import java.util.ArrayList;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.inputmethodservice.InputMethodService;
import android.os.Handler;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.AdapterView;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;

import com.android.internal.util.aokp.AokpRibbonHelper;
import com.android.internal.util.aokp.AwesomeAction;
import com.android.internal.util.aokp.AwesomeAnimationHelper;
import com.android.internal.util.aokp.NavRingHelpers;
import com.android.internal.util.aokp.RibbonAdapter;
import com.android.internal.util.aokp.RibbonAdapter.RibbonItem;
import com.android.systemui.R;

public class AokpSwipeRibbon extends LinearLayout implements OnItemClickListener, OnItemLongClickListener {
    public static final String TAG = "NAVIGATION BAR RIBBON";

    private Context mContext;
    private RibbonGestureCatcherView mGesturePanel;
    private String mLocation;
    private WindowManager mWindowManager;
    private SettingsObserver mSettingsObserver;
    private boolean showing, animating, mEnabled;
    private FrameLayout mPopupView;
    private ListView mRibbon;
    private LinearLayout mContainerFrame;
    private Animation mAnimationIn;
    private Animation mAnimationOut;
    private int mHideTimeOut, animationIn, animationOut, mAnim, visible, mDisabledFlags, mRibbonColor, mWidth, mMargin;
    private Handler mHandler;
    private String[] SETTINGS_AOKP;
    private float mAnimDur;
    private ArrayList<RibbonItem> mItems = new ArrayList<RibbonItem>();
    private RibbonAdapter mRibbonAdapter;

    public AokpSwipeRibbon(Context context, String location) {
        super(context);
        mContext = context;
        mLocation = location;
        IntentFilter filter = new IntentFilter();
        filter.addAction(RibbonReceiver.ACTION_TOGGLE_RIBBON);
        filter.addAction(RibbonReceiver.ACTION_SHOW_RIBBON);
        filter.addAction(RibbonReceiver.ACTION_HIDE_RIBBON);
        mContext.registerReceiver(new RibbonReceiver(), filter);
        mWindowManager = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
        SETTINGS_AOKP = mLocation.equals("left") ? Settings.AOKP.AOKP_LEFT_RIBBON : Settings.AOKP.AOKP_RIGHT_RIBBON;
        mSettingsObserver = new SettingsObserver(new Handler());
        mSettingsObserver.observe();
        updateSettings();
    }

    public Handler getHandler() {
        if (mHandler == null) mHandler = new Handler();
        return mHandler;
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
                mWindowManager.addView(mPopupView, params);
                mContainerFrame.startAnimation(mAnimationIn);
                if (mHideTimeOut > 0) {
                    getHandler().postDelayed(delayHide, mHideTimeOut);
                }
            }
        }
    }

    public void hideRibbonView() {
        if (mPopupView != null && showing) {
            showing = false;
            mContainerFrame.startAnimation(mAnimationOut);
            mRibbon.setItemChecked(mRibbon.getSelectedItemPosition(), false);
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
        } else if (mLocation.equals("left")) {
            gravity = Gravity.CENTER_VERTICAL | Gravity.LEFT;
        } else {
            gravity = Gravity.CENTER_VERTICAL | Gravity.RIGHT;
        }
        return gravity;
    }

    private void setAnimation() {
        if (mLocation.equals("bottom")) {
            animationIn = com.android.internal.R.anim.slide_in_up_ribbon;
            animationOut = com.android.internal.R.anim.slide_out_down_ribbon;
        } else if (mLocation.equals("left")) {
            animationIn = com.android.internal.R.anim.slide_in_left_ribbon;
            animationOut = com.android.internal.R.anim.slide_out_left_ribbon;
        } else {
            animationIn = com.android.internal.R.anim.slide_in_right_ribbon;
            animationOut = com.android.internal.R.anim.slide_out_right_ribbon;
        }
        if (mAnim > 0) {
            int[] animArray = AwesomeAnimationHelper.getAnimations(mAnim);
            animationIn = animArray[1];
            animationOut = animArray[0];
        }
    }

    public void createRibbonView() {
        if (mGesturePanel != null) {
            try {
                mWindowManager.removeView(mGesturePanel);
            } catch (IllegalArgumentException e) {
                //If we try to remove the gesture panel and it's not currently attached.
            }
        }
        if (!mEnabled) return;
        mGesturePanel = new RibbonGestureCatcherView(mContext, mLocation, SETTINGS_AOKP, this);
        mWindowManager.addView(mGesturePanel, mGesturePanel.getGesturePanelLayoutParams());
        mPopupView = new FrameLayout(mContext);
        mPopupView.removeAllViews();
        View ribbonView = View.inflate(mContext, R.layout.aokp_swipe_ribbon, null);
        mContainerFrame = (LinearLayout) ribbonView.findViewById(R.id.ribbon_main);
        mRibbon = (ListView) ribbonView.findViewById(R.id.ribbon_list);
        setupRibbon();
        setAnimation();
        mAnimationIn = PlayInAnim();
        mAnimationOut = PlayOutAnim();
        mPopupView.addView(mContainerFrame, getNewLayoutParams());
        mPopupView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_OUTSIDE) {
                    getHandler().removeCallbacks(delayHide);
                    if (showing) {
                        hideRibbonView();
                    }
                    return true;
                }
                return false;
            }
        });
        mRibbon.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                getHandler().removeCallbacks(delayHide);
                if (mHideTimeOut > 0) {
                    getHandler().postDelayed(delayHide, mHideTimeOut);
                }
                return false;
            }
        });
    }

    public Animation PlayInAnim() {
        if (mRibbon != null) {
            Animation animation = AnimationUtils.loadAnimation(mContext, animationIn);
            animation.setStartOffset(0);
            animation.setDuration((int) (animation.getDuration() * (mAnimDur * 0.01f)));
            return animation;
        }
        return null;
    }

    public Animation PlayOutAnim() {
        if (mRibbon != null) {
            Animation animation = AnimationUtils.loadAnimation(mContext, animationOut);
            animation.setStartOffset(0);
            animation.setDuration((int) (animation.getDuration() * (mAnimDur * 0.01f)));
            animation.setAnimationListener(new Animation.AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {
                    animating = true;
                }

                @Override
                public void onAnimationEnd(Animation animation) {
                    mWindowManager.removeView(mPopupView);
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
        if (mRibbon != null && mItems != null) {
            mRibbonAdapter = new RibbonAdapter(mContext, mItems);
            mRibbon.setAdapter(mRibbonAdapter);
            mRibbon.setOnItemClickListener(this);
            mRibbon.setOnItemLongClickListener(this);
            mRibbon.setBackgroundColor(mRibbonColor);
            mRibbon.setDividerHeight(mMargin);
        }
    }

    private LinearLayout.LayoutParams getNewLayoutParams() {
        return new LinearLayout.LayoutParams(mWidth, LayoutParams.MATCH_PARENT);
    }

    protected void updateSwipeArea() {
        final boolean showingIme = ((visible & InputMethodService.IME_VISIBLE) != 0);
        if (mGesturePanel != null) {
            mGesturePanel.setViewVisibility(showingIme);
        }
    }

    public void setNavigationIconHints(int hints) {
          if (hints == visible) return;
             visible = hints;
             updateSwipeArea();
    }

    public void setDisabledFlags(int disabledFlags) {
        if (disabledFlags == mDisabledFlags) return;
            mDisabledFlags = disabledFlags;
            updateSwipeArea();
    }



    class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }
        void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(Settings.AOKP.getUriFor(SETTINGS_AOKP[AokpRibbonHelper.ENABLE_RIBBON]), false, this);
            resolver.registerContentObserver(Settings.AOKP.getUriFor(SETTINGS_AOKP[AokpRibbonHelper.RIBBON_ITEMS]), false, this);
            resolver.registerContentObserver(Settings.AOKP.getUriFor(SETTINGS_AOKP[AokpRibbonHelper.LONG_SWIPE]), false, this);
            resolver.registerContentObserver(Settings.AOKP.getUriFor(SETTINGS_AOKP[AokpRibbonHelper.HANDLE_HEIGHT]), false, this);
            resolver.registerContentObserver(Settings.AOKP.getUriFor(SETTINGS_AOKP[AokpRibbonHelper.HANDLE_LOCATION]), false, this);
            resolver.registerContentObserver(Settings.AOKP.getUriFor(SETTINGS_AOKP[AokpRibbonHelper.HANDLE_COLOR]), false, this);
            resolver.registerContentObserver(Settings.AOKP.getUriFor(SETTINGS_AOKP[AokpRibbonHelper.HANDLE_VIBRATE]), false, this);
            resolver.registerContentObserver(Settings.AOKP.getUriFor(SETTINGS_AOKP[AokpRibbonHelper.HANDLE_WEIGHT]), false, this);
            resolver.registerContentObserver(Settings.AOKP.getUriFor(SETTINGS_AOKP[AokpRibbonHelper.LONG_PRESS]), false, this);
            resolver.registerContentObserver(Settings.AOKP.getUriFor(SETTINGS_AOKP[AokpRibbonHelper.AUTO_HIDE_DURATION]), false, this);
            resolver.registerContentObserver(Settings.AOKP.getUriFor(SETTINGS_AOKP[AokpRibbonHelper.RIBBON_COLOR]), false, this);
            resolver.registerContentObserver(Settings.AOKP.getUriFor(SETTINGS_AOKP[AokpRibbonHelper.RIBBON_ANIMATION_DURATION]), false, this);
            resolver.registerContentObserver(Settings.AOKP.getUriFor(SETTINGS_AOKP[AokpRibbonHelper.RIBBON_ANIMATION_TYPE]), false, this);
            resolver.registerContentObserver(Settings.AOKP.getUriFor(SETTINGS_AOKP[AokpRibbonHelper.RIBBON_MARGIN]), false, this);
            resolver.registerContentObserver(Settings.AOKP.getUriFor(SETTINGS_AOKP[AokpRibbonHelper.RIBBON_SIZE]), false, this);
        }
         @Override
        public void onChange(boolean selfChange) {
            updateSettings();
        }
    }
   protected void updateSettings() {
        ContentResolver cr = mContext.getContentResolver();
        // needs to be defauted to false before merge
        mEnabled = Settings.AOKP.getBoolean(cr, SETTINGS_AOKP[AokpRibbonHelper.ENABLE_RIBBON], false);
        mItems.clear();
        ArrayList<String> list = Settings.AOKP.getArrayList(cr, SETTINGS_AOKP[AokpRibbonHelper.RIBBON_ITEMS]);
        for (String item : list) {
            mItems.add(new RibbonItem(item));
        }
        mHideTimeOut = Settings.AOKP.getInt(cr, SETTINGS_AOKP[AokpRibbonHelper.AUTO_HIDE_DURATION], 0);
        mRibbonColor = Settings.AOKP.getInt(cr, SETTINGS_AOKP[AokpRibbonHelper.RIBBON_COLOR], Color.BLACK);
        mAnim = Settings.AOKP.getInt(cr, SETTINGS_AOKP[AokpRibbonHelper.RIBBON_ANIMATION_TYPE], 0);
        mAnimDur = Settings.AOKP.getInt(cr, SETTINGS_AOKP[AokpRibbonHelper.RIBBON_ANIMATION_DURATION], 50);
        int size = Settings.AOKP.getInt(mContext.getContentResolver(), SETTINGS_AOKP[AokpRibbonHelper.RIBBON_SIZE], 30);
        mWidth = (int) (((size * 0.01f) * 150) + 150);
        int margin = Settings.AOKP.getInt(mContext.getContentResolver(), SETTINGS_AOKP[AokpRibbonHelper.RIBBON_MARGIN], 5);
        mMargin = (int) ((margin * 0.01f) * 200);
        // -------------------------
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
                getHandler().removeCallbacks(delayHide);
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
                getHandler().removeCallbacks(delayHide);
                if (showing) {
                    hideRibbonView();
                }
            }
        }
    }
    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        String action = ((RibbonItem) mRibbonAdapter.getItem(position)).mShortAction;
        hideRibbonView();
        AwesomeAction.launchAction(mContext, action);
    }

    @Override
    public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
        String action = ((RibbonItem) mRibbonAdapter.getItem(position)).mLongAction;
        hideRibbonView();
        AwesomeAction.launchAction(mContext, action);
        return true;
    }
}
