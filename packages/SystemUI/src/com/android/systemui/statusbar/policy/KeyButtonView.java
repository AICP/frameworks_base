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

package com.android.systemui.statusbar.policy;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.util.Log;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewDebug;
import android.view.accessibility.AccessibilityEvent;
import android.widget.ImageView;
import com.android.internal.util.aokp.AwesomeAction;
import com.android.internal.util.aokp.AwesomeConstants.AwesomeConstant;
import com.android.internal.util.aokp.NavBarHelpers;
import com.android.systemui.R;

import java.io.File;

public class KeyButtonView extends ImageView {
    private static final String TAG = "StatusBar.KeyButtonView";
    private static final boolean DEBUG = false;

    final float GLOW_MAX_SCALE_FACTOR = 1.8f;
    public static final float DEFAULT_QUIESCENT_ALPHA = 0.70f;

    private final int mDoubleTapTimeout;
    private final int mSinglePressTimeout;
    private final int mLongPressTimeout;

    long mDownTime;
    long mUpTime;
    int mTouchSlop;
    Drawable mGlowBG;
    int mGlowBgId;
    int mGlowWidth, mGlowHeight;
    float mGlowAlpha = 0f, mGlowScale = 1f;
    @ViewDebug.ExportedProperty(category = "drawing")
    float mDrawingAlpha = 1f;
    @ViewDebug.ExportedProperty(category = "drawing")
    float mQuiescentAlpha = DEFAULT_QUIESCENT_ALPHA;
    RectF mRect = new RectF();
    AnimatorSet mPressedAnim;
    Animator mAnimateToQuiescent = new ObjectAnimator();

    AwesomeButtonInfo mActions;

    boolean mHasSingleAction = true, mHasDoubleAction, mHasLongAction;

    Runnable mCheckLongPress = new Runnable() {
        public void run() {
            if (isPressed()) {
                removeCallbacks(mSingleTap);
                doLongPress();
            }
        }
    };
    private Runnable mSingleTap = new Runnable() {
        @Override
        public void run() {
            if (!isPressed()) {
                doSinglePress();
            }
        }
    };

    public KeyButtonView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public KeyButtonView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs);

        setDrawingAlpha(mQuiescentAlpha);
        if (mGlowBG != null) {
            mGlowWidth = mGlowBG.getIntrinsicWidth();
            mGlowHeight = mGlowBG.getIntrinsicHeight();
        }

        setClickable(true);
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        mSinglePressTimeout = 200;
        mDoubleTapTimeout = 200;
        mLongPressTimeout = ViewConfiguration.getLongPressTimeout();
        setLongClickable(false);
    }

    public void setButtonActions(AwesomeButtonInfo actions) {
        this.mActions = actions;

        setTag(mActions.singleAction); // should be OK even if it's null

        resetImage();

        mHasSingleAction = mActions != null && (mActions.singleAction != null);
        mHasLongAction = mActions != null && mActions.longPressAction != null;
        mHasDoubleAction = mActions != null && mActions.doubleTapAction != null;
        setLongClickable(mHasLongAction);
    }

    public void resetImage() {
        // set image
        if (mActions.iconUri != null && mActions.iconUri.length() > 0) {
            // custom icon from the URI here
            File f = new File(Uri.parse(mActions.iconUri).getPath());
            if (f.exists()) {
                setImageDrawable(new BitmapDrawable(getResources(), f.getAbsolutePath()));
            }
        } else if (mActions.singleAction != null) {
            setImageDrawable(NavBarHelpers.getIconImage(mContext, mActions.singleAction));
        } else {
            setImageResource(R.drawable.ic_sysbar_null);
        }
    }

    public void updateResources() {
        if (mGlowBgId != 0) {
            mGlowBG = mContext.getResources().getDrawable(mGlowBgId);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mGlowBG != null) {
            canvas.save();
            final int w = getWidth();
            final int h = getHeight();
            final float aspect = (float) mGlowWidth / mGlowHeight;
            final int drawW = (int) (h * aspect);
            final int drawH = h;
            final int margin = (drawW - w) / 2;
            canvas.scale(mGlowScale, mGlowScale, w * 0.5f, h * 0.5f);
            mGlowBG.setBounds(-margin, 0, drawW - margin, drawH);
            mGlowBG.setAlpha((int) (mDrawingAlpha * mGlowAlpha * 255));
            mGlowBG.draw(canvas);
            canvas.restore();
            mRect.right = w;
            mRect.bottom = h;
        }
        super.onDraw(canvas);
    }

    public void setQuiescentAlpha(float alpha, boolean animate) {
        mAnimateToQuiescent.cancel();
        alpha = Math.min(Math.max(alpha, 0), 1);
        if (alpha == mQuiescentAlpha && alpha == mDrawingAlpha) return;
        mQuiescentAlpha = alpha;
        if (DEBUG) Log.d(TAG, "New quiescent alpha = " + mQuiescentAlpha);
        if (mGlowBG != null && animate) {
            mAnimateToQuiescent = animateToQuiescent();
            mAnimateToQuiescent.start();
        } else {
            setDrawingAlpha(mQuiescentAlpha);
        }
    }

    private ObjectAnimator animateToQuiescent() {
        return ObjectAnimator.ofFloat(this, "drawingAlpha", mQuiescentAlpha);
    }

    public float getQuiescentAlpha() {
        return mQuiescentAlpha;
    }

    public float getDrawingAlpha() {
        return mDrawingAlpha;
    }

    public void setDrawingAlpha(float x) {
        // Calling setAlpha(int), which is an ImageView-specific
        // method that's different from setAlpha(float). This sets
        // the alpha on this ImageView's drawable directly
        setAlpha((int) (x * 255));
        mDrawingAlpha = x;
    }

    public float getGlowAlpha() {
        if (mGlowBG == null) return 0;
        return mGlowAlpha;
    }

    public void setGlowAlpha(float x) {
        if (mGlowBG == null) return;
        mGlowAlpha = x;
        invalidate();
    }

    public float getGlowScale() {
        if (mGlowBG == null) return 0;
        return mGlowScale;
    }

    public void setGlowScale(float x) {
        if (mGlowBG == null) return;
        mGlowScale = x;
        final float w = getWidth();
        final float h = getHeight();
        if (GLOW_MAX_SCALE_FACTOR <= 1.0f) {
            // this only works if we know the glow will never leave our bounds
            invalidate();
        } else {
            final float rx = (w * (GLOW_MAX_SCALE_FACTOR - 1.0f)) / 2.0f + 1.0f;
            final float ry = (h * (GLOW_MAX_SCALE_FACTOR - 1.0f)) / 2.0f + 1.0f;
            com.android.systemui.SwipeHelper.invalidateGlobalRegion(
                    this,
                    new RectF(getLeft() - rx,
                            getTop() - ry,
                            getRight() + rx,
                            getBottom() + ry));

            // also invalidate our immediate parent to help avoid situations where nearby glows
            // interfere
            ((View) getParent()).invalidate();
        }
    }

    public void setPressed(boolean pressed) {
        if (mGlowBG != null) {
            if (pressed != isPressed()) {
                if (mPressedAnim != null && mPressedAnim.isRunning()) {
                    mPressedAnim.cancel();
                }
                final AnimatorSet as = mPressedAnim = new AnimatorSet();
                if (pressed) {
                    if (mGlowScale < GLOW_MAX_SCALE_FACTOR)
                        mGlowScale = GLOW_MAX_SCALE_FACTOR;
                    if (mGlowAlpha < mQuiescentAlpha)
                        mGlowAlpha = mQuiescentAlpha;
                    setDrawingAlpha(1f);
                    as.playTogether(
                            ObjectAnimator.ofFloat(this, "glowAlpha", 1f),
                            ObjectAnimator.ofFloat(this, "glowScale", GLOW_MAX_SCALE_FACTOR)
                    );
                    as.setDuration(50);
                } else {
                    mAnimateToQuiescent.cancel();
                    mAnimateToQuiescent = animateToQuiescent();
                    as.playTogether(
                            ObjectAnimator.ofFloat(this, "glowAlpha", 0f),
                            ObjectAnimator.ofFloat(this, "glowScale", 1f),
                            mAnimateToQuiescent
                    );
                    as.setDuration(500);
                }
                as.start();
            }
        }
        super.setPressed(pressed);
    }


    public boolean onTouchEvent(MotionEvent ev) {
        final int action = ev.getAction();
        int x, y;

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                mDownTime = SystemClock.uptimeMillis();
                setPressed(true);
                if (hasSingleTapAction()) {
                    removeCallbacks(mSingleTap);
                }
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                long diff = mDownTime - mUpTime; // difference between last up and now
                if (hasDoubleTapAction() && diff <= mDoubleTapTimeout) {
                    doDoubleTap();
                } else {
                    if (hasLongTapAction()) {
                        removeCallbacks(mCheckLongPress);
                        postDelayed(mCheckLongPress, mLongPressTimeout);
                    }

                    if (hasSingleTapAction()) {
                        postDelayed(mSingleTap, mSinglePressTimeout);
                    }
                }
                break;
            case MotionEvent.ACTION_MOVE:
                x = (int) ev.getX();
                y = (int) ev.getY();
                setPressed(x >= -mTouchSlop
                        && x < getWidth() + mTouchSlop
                        && y >= -mTouchSlop
                        && y < getHeight() + mTouchSlop);
                break;
            case MotionEvent.ACTION_CANCEL:
                setPressed(false);
                if (hasSingleTapAction()) {
                    removeCallbacks(mSingleTap);
                }
                if (hasLongTapAction()) {
                    removeCallbacks(mCheckLongPress);

                }
                break;
            case MotionEvent.ACTION_UP:
                mUpTime = SystemClock.uptimeMillis();

                if (hasLongTapAction()) {
                    removeCallbacks(mCheckLongPress);
                }
                final boolean doIt = isPressed();
                setPressed(false);
                if (doIt) {
                    playSoundEffect(SoundEffectConstants.CLICK);
                }

                if (!hasDoubleTapAction() && !hasLongTapAction()) {
                    // a little optimization here
                    removeCallbacks(mSingleTap);
                    doSinglePress();
                }

                break;
        }
        return true;
    }

    private boolean hasLongTapAction() {
        return mHasLongAction;
    }

    private boolean hasDoubleTapAction() {
        return mHasDoubleAction;
    }

    private boolean hasSingleTapAction() {
        return mHasSingleAction;
    }

    private void doSinglePress() {
        if (callOnClick()) {
            // cool
            sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_CLICKED);
        } else if (mActions != null) {
            if (mActions.singleAction != null) {
                AwesomeAction.launchAction(getContext(), mActions.singleAction);
                sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_CLICKED);
            }
        }
    }

    private void doDoubleTap() {
        if (hasDoubleTapAction()) {
            removeCallbacks(mSingleTap);
            AwesomeAction.launchAction(getContext(), mActions.doubleTapAction);
        }
    }

    private void doLongPress() {
        if (hasLongTapAction()) {
            removeCallbacks(mSingleTap);
            AwesomeAction.launchAction(getContext(), mActions.longPressAction);
            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_LONG_CLICKED);
        }
    }

    public void setGlowBackground(int resId) {
        mGlowBgId = resId;
        mGlowBG = getResources().getDrawable(resId);
        if (mGlowBG != null) {
            setDrawingAlpha(mDrawingAlpha);
            mGlowWidth = mGlowBG.getIntrinsicWidth();
            mGlowHeight = mGlowBG.getIntrinsicHeight();
        }
    }

    public static class AwesomeButtonInfo {
        String singleAction, doubleTapAction, longPressAction, iconUri;

        public AwesomeButtonInfo(String singleTap, String doubleTap, String longPress, String uri) {
            this.singleAction = singleTap;
            this.doubleTapAction = doubleTap;
            this.longPressAction = longPress;
            this.iconUri = uri;

            if (singleAction != null && (singleAction.isEmpty()
                    || singleAction.equals(AwesomeConstant.ACTION_NULL.value()))) {
                singleAction = null;
            }
            if (doubleTapAction != null && (doubleTapAction.isEmpty()
                    || doubleTapAction.equals(AwesomeConstant.ACTION_NULL.value()))) {
                doubleTapAction = null;
            }
            if (longPressAction != null && (longPressAction.isEmpty()
                    || longPressAction.equals(AwesomeConstant.ACTION_NULL.value()))) {
                longPressAction = null;
            }
        }
    }
}
