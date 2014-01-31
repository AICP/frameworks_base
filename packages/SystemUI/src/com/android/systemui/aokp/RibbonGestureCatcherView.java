
package com.android.systemui.aokp;

import com.android.systemui.R;
import com.android.systemui.aokp.AokpSwipeRibbon;

import static com.android.internal.util.aokp.AwesomeConstants.*;
import com.android.internal.util.aokp.AokpRibbonHelper;
import com.android.internal.util.aokp.AwesomeAction;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.os.Handler;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.view.animation.AnimationUtils;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

public class RibbonGestureCatcherView extends LinearLayout {

    private Context mContext;
    private Resources res;
    private AokpSwipeRibbon mAokpSwipeRibbon;
    private ImageView mDragButton;
    long mDowntime;
    int mTimeOut, mLocation;
    private int mButtonWeight = 30;
    private int mButtonHeight = 0;
    private int mGestureHeight;
    private int mDragButtonColor;
    private boolean mRightSide, vib;
    private boolean mVibLock = false;

    private int mTriggerThreshhold = 20;
    private float[] mDownPoint = new float[2];
    private boolean mRibbonSwipeStarted = false;
    private boolean mRibbonShortSwiped = false;
    private int mScreenWidth, mScreenHeight;
    private String mAction, mLongSwipeAction, mLongPressAction;
    private String[] SETTINGS_AOKP;

    final static String TAG = "PopUpRibbon";

    public RibbonGestureCatcherView(Context context, String action, String[] settings, AokpSwipeRibbon aokpSwipeRibbon) {
        super(context);

        mContext = context;
        mAction = action;
        mAokpSwipeRibbon = aokpSwipeRibbon;
        SETTINGS_AOKP = settings;
        mRightSide = mAction.equals("right");
        mDragButton = new ImageView(mContext);
        res = mContext.getResources();
        mGestureHeight = res.getDimensionPixelSize(R.dimen.ribbon_drag_handle_height);
        updateLayout();
        Point size = new Point();
        WindowManager wm = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
        wm.getDefaultDisplay().getSize(size);
        mScreenHeight = size.y;
        mScreenWidth = size.x;
        updateSettings();

        mDragButton.setOnTouchListener(new View.OnTouchListener() {

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                int action = event.getAction();
                switch (action) {
                    case MotionEvent.ACTION_DOWN:
                        if (!mRibbonSwipeStarted) {
                            if (vib && !mVibLock) {
                                mVibLock = true;
                                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                            }
                            mDownPoint[0] = event.getX();
                            mDownPoint[1] = event.getY();
                            mRibbonSwipeStarted = true;
                            mRibbonShortSwiped = false;
                        }
                        break;
                    case MotionEvent.ACTION_CANCEL:
                        mRibbonSwipeStarted = false;
                        mVibLock = false;
                        break;
                    case MotionEvent.ACTION_MOVE:
                        if (mRibbonSwipeStarted) {
                            final int historySize = event.getHistorySize();
                            for (int k = 0; k < historySize + 1; k++) {
                                float x = k < historySize ? event.getHistoricalX(k) : event.getX();
                                float y = k < historySize ? event.getHistoricalY(k) : event.getY();
                                float distance = 0f;
                                distance = mRightSide ? (mDownPoint[0] - x) : (x - mDownPoint[0]);
                                if (distance > mTriggerThreshhold
                                        && distance < mScreenWidth * 0.75f && !mRibbonShortSwiped) {
                                    mRibbonShortSwiped = true;
                                    mAokpSwipeRibbon.showRibbonView();
                                    mVibLock = false;
                                }
                                if (distance > mScreenWidth * 0.75f) {
                                    mAokpSwipeRibbon.hideRibbonView();
                                    AwesomeAction.launchAction(mContext, mLongSwipeAction);
                                    mVibLock = false;
                                    mRibbonSwipeStarted = false;
                                    mRibbonShortSwiped = false;
                                    return true;
                                }
                            }
                        }
                        break;
                    case MotionEvent.ACTION_UP:
                        mRibbonSwipeStarted = false;
                        mRibbonShortSwiped = false;
                        mVibLock = false;
                        break;
                }
                return false;
            }
        });

        mDragButton.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                AwesomeAction.launchAction(mContext, mLongPressAction);
                return true;
            }
        });
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
    }

    private int getGravity() {
        int gravity = 0;
        if (mAction.equals("left")) {
            switch (mLocation) {
                case 0:
                    gravity = Gravity.CENTER_VERTICAL | Gravity.LEFT;
                    break;
                case 1:
                    gravity = Gravity.TOP | Gravity.LEFT;
                    break;
                case 2:
                    gravity = Gravity.BOTTOM | Gravity.LEFT;
                    break;
            }
        } else {
            switch (mLocation) {
                case 0:
                    gravity = Gravity.CENTER_VERTICAL | Gravity.RIGHT;
                    break;
                case 1:
                    gravity = Gravity.TOP | Gravity.RIGHT;
                    break;
                case 2:
                    gravity = Gravity.BOTTOM | Gravity.RIGHT;
                    break;
            }
        }
        return gravity;
    }

    public WindowManager.LayoutParams getGesturePanelLayoutParams() {
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT);
        lp.gravity = getGravity();
        lp.setTitle("RibbonGesturePanel" + mAction);
        return lp;
    }

    public void setViewVisibility(boolean visibleIME) {
        mDragButton.setVisibility(visibleIME ? View.GONE : View.VISIBLE);
    }

    private void updateLayout() {
        LinearLayout.LayoutParams dragParams;
        float dragSize = 0;
        float dragHeight = (mGestureHeight * (mButtonHeight * 0.01f));
        removeAllViews();
        dragSize = ((mScreenHeight) * (mButtonWeight * 0.02f))
                / getResources().getDisplayMetrics().density;
        mDragButton.setBackgroundColor(mDragButtonColor);
        dragParams = new LinearLayout.LayoutParams((int) dragHeight, (int) dragSize);
        setOrientation(VERTICAL);
        mDragButton.setScaleType(ImageView.ScaleType.FIT_XY);
        addView(mDragButton, dragParams);
        invalidate();
    }

    protected void updateSettings() {
        ContentResolver cr = mContext.getContentResolver();
        // opacity should be zero for merge
        mDragButtonColor = Settings.AOKP.getInt(cr, SETTINGS_AOKP[AokpRibbonHelper.HANDLE_COLOR], Color.TRANSPARENT);
        mButtonWeight = Settings.AOKP.getInt(cr, SETTINGS_AOKP[AokpRibbonHelper.HANDLE_WEIGHT], 30);
        mButtonHeight = Settings.AOKP.getInt(cr, SETTINGS_AOKP[AokpRibbonHelper.HANDLE_HEIGHT], 50);
        mLongSwipeAction = Settings.AOKP.getString(cr, SETTINGS_AOKP[AokpRibbonHelper.LONG_SWIPE]);
        if (TextUtils.isEmpty(mLongSwipeAction)) {
            mLongSwipeAction = AwesomeConstant.ACTION_APP_WINDOW.value();
            Settings.AOKP
                    .putString(cr, SETTINGS_AOKP[AokpRibbonHelper.LONG_SWIPE], AwesomeConstant.ACTION_APP_WINDOW.value());
        }
        mLongPressAction = Settings.AOKP.getString(cr, SETTINGS_AOKP[AokpRibbonHelper.LONG_PRESS]);
        if (TextUtils.isEmpty(mLongPressAction)) {
            mLongPressAction = AwesomeConstant.ACTION_APP_WINDOW.value();
            Settings.AOKP
                    .putString(cr, SETTINGS_AOKP[AokpRibbonHelper.LONG_PRESS], AwesomeConstant.ACTION_APP_WINDOW.value());
        }
        vib = Settings.AOKP.getBoolean(cr, SETTINGS_AOKP[AokpRibbonHelper.HANDLE_VIBRATE], true);
        mLocation = Settings.AOKP.getInt(cr, SETTINGS_AOKP[AokpRibbonHelper.HANDLE_LOCATION], 0);
        updateLayout();
    }
}
