/**
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.systemui.biometrics;

import android.app.WallpaperColors;
import android.app.WallpaperManager;
import android.app.admin.DevicePolicyManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.os.Handler;
import android.os.Looper;
import android.os.RemoteException;
import android.os.UserHandle;
import android.provider.Settings;
import android.view.Display;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import androidx.palette.graphics.Palette;

import com.android.internal.widget.LockPatternUtils;
import com.android.keyguard.KeyguardSecurityModel.SecurityMode;
import com.android.keyguard.KeyguardUpdateMonitor;
import com.android.keyguard.KeyguardUpdateMonitorCallback;
import com.android.systemui.R;
import com.android.systemui.Dependency;
import com.android.systemui.statusbar.policy.ConfigurationController;
import com.android.systemui.statusbar.policy.ConfigurationController.ConfigurationListener;

import vendor.lineage.biometrics.fingerprint.inscreen.V1_0.IFingerprintInscreen;
import vendor.lineage.biometrics.fingerprint.inscreen.V1_0.IFingerprintInscreenCallback;

import java.util.NoSuchElementException;
import java.util.Timer;
import java.util.TimerTask;

public class FODCircleView extends ImageView implements ConfigurationListener {
    private final int mPositionX;
    private final int mPositionY;
    private final int mSize;
    private final int mDreamingMaxOffset;
    private final int mNavigationBarSize;
    private final boolean mShouldBoostBrightness;
    private final Paint mPaintFingerprintBackground = new Paint();
    private final Paint mPaintFingerprint = new Paint();
    private final WindowManager.LayoutParams mParams = new WindowManager.LayoutParams();
    private final WindowManager.LayoutParams mPressedParams = new WindowManager.LayoutParams();
    private final WindowManager mWindowManager;

    private IFingerprintInscreen mFingerprintInscreenDaemon;

    private int mDreamingOffsetX;
    private int mDreamingOffsetY;

    private boolean mIsBouncer;
    private boolean mIsDreaming;
    private boolean mIsCircleShowing;

    private Handler mHandler;

    private final ImageView mPressedView;

    private LockPatternUtils mLockPatternUtils;

    private Timer mBurnInProtectionTimer;

    private boolean mCutoutMasked;
    private boolean mIsRecognizingAnimEnabled;
    private boolean mCanUnlockWithFp;
    private boolean mIsAuthenticated;
    private FODAnimation mFODAnimation;
    private int mStatusbarHeight;
    private int iconcolor = 0xFF3980FF;

    private IFingerprintInscreenCallback mFingerprintInscreenCallback =
        new IFingerprintInscreenCallback.Stub() {
            @Override
            public void onFingerDown() {
                mHandler.post(() -> showCircle());
            }

            @Override
            public void onFingerUp() {
                mHandler.post(() -> hideCircle());
            }
        };

    private KeyguardUpdateMonitor mUpdateMonitor;

    private KeyguardUpdateMonitorCallback mMonitorCallback = new KeyguardUpdateMonitorCallback() {
        @Override
        public void onDreamingStateChanged(boolean dreaming) {
            mIsDreaming = dreaming;
            updateAlpha();

            if (dreaming) {
                mBurnInProtectionTimer = new Timer();
                mBurnInProtectionTimer.schedule(new BurnInProtectionTask(), 0, 60 * 1000);
            } else if (mBurnInProtectionTimer != null) {
                mBurnInProtectionTimer.cancel();
                updatePosition();
            }
        }

        @Override
        public void onKeyguardBouncerChanged(boolean isBouncer) {
            mIsBouncer = isBouncer;
            if (mUpdateMonitor.isFingerprintDetectionRunning()) {
                if (isPinOrPattern(mUpdateMonitor.getCurrentUser()) || !isBouncer) {
                    show();
                } else {
                    hide();
                }
            } else {
                hide();
            }
        }

        @Override
        public void onScreenTurnedOff() {
            hide();
        }

        @Override
        public void onScreenTurnedOn() {
            if (mUpdateMonitor.isFingerprintDetectionRunning()) {
                show();
            }
        }
    };

    public FODCircleView(Context context) {
        super(context);

        setScaleType(ScaleType.CENTER);

        IFingerprintInscreen daemon = getFingerprintInScreenDaemon();
        if (daemon == null) {
            throw new RuntimeException("Unable to get IFingerprintInscreen");
        }

        try {
            mShouldBoostBrightness = daemon.shouldBoostBrightness();
            mPositionX = daemon.getPositionX();
            mPositionY = daemon.getPositionY();
            mSize = daemon.getSize();
        } catch (RemoteException e) {
            throw new RuntimeException("Failed to retrieve FOD circle position or size");
        }

        Resources res = context.getResources();

        mPaintFingerprint.setColor(res.getColor(R.color.config_fodColor));
        mPaintFingerprint.setAntiAlias(true);

        mPaintFingerprintBackground.setColor(res.getColor(R.color.config_fodColorBackground));
        mPaintFingerprintBackground.setAntiAlias(true);

        mWindowManager = context.getSystemService(WindowManager.class);

        mNavigationBarSize = res.getDimensionPixelSize(R.dimen.navigation_bar_size);

        mDreamingMaxOffset = (int)(mSize * 0.1f);

        mHandler = new Handler(Looper.getMainLooper());

        mParams.height = mSize;
        mParams.width = mSize;
        mParams.format = PixelFormat.TRANSLUCENT;

        mParams.packageName = "android";
        mParams.type = WindowManager.LayoutParams.TYPE_DISPLAY_OVERLAY;
        mParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
        mParams.gravity = Gravity.TOP | Gravity.LEFT;

        mPressedParams.copyFrom(mParams);
        mPressedParams.flags |= WindowManager.LayoutParams.FLAG_DIM_BEHIND;

        mParams.setTitle("Fingerprint on display");
        mPressedParams.setTitle("Fingerprint on display.touched");

        mPressedView = new ImageView(context)  {
            @Override
            protected void onDraw(Canvas canvas) {
                if (mIsCircleShowing) {
                    canvas.drawCircle(mSize / 2, mSize / 2, mSize / 2.0f, mPaintFingerprint);
                }
                super.onDraw(canvas);
            }
        };

        mWindowManager.addView(this, mParams);

        updateCutoutFlags();
        hide();

        mLockPatternUtils = new LockPatternUtils(mContext);

        mUpdateMonitor = KeyguardUpdateMonitor.getInstance(context);
        mUpdateMonitor.registerCallback(mMonitorCallback);

        Dependency.get(ConfigurationController.class).addCallback(this);

        mFODAnimation = new FODAnimation(context, mPositionX, mPositionY);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (!mIsCircleShowing) {
            canvas.drawCircle(mSize / 2, mSize / 2, mSize / 2.0f, mPaintFingerprintBackground);
        }
        super.onDraw(canvas);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getAxisValue(MotionEvent.AXIS_X);
        float y = event.getAxisValue(MotionEvent.AXIS_Y);

        boolean newIsInside = (x > 0 && x < mSize) && (y > 0 && y < mSize);

        if (event.getAction() == MotionEvent.ACTION_DOWN && newIsInside) {
            showCircle();
            if (isRecognizingAnimEnabled()) {
                mFODAnimation.showFODanimation();
            }
            return true;
        } else if (event.getAction() == MotionEvent.ACTION_UP) {
            hideCircle();
            mFODAnimation.hideFODanimation();
            return true;
        } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
            return true;
        }

        mFODAnimation.hideFODanimation();
        return false;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        updatePosition();
    }

    public IFingerprintInscreen getFingerprintInScreenDaemon() {
        if (mFingerprintInscreenDaemon == null) {
            try {
                mFingerprintInscreenDaemon = IFingerprintInscreen.getService();
                if (mFingerprintInscreenDaemon != null) {
                    mFingerprintInscreenDaemon.setCallback(mFingerprintInscreenCallback);
                    mFingerprintInscreenDaemon.asBinder().linkToDeath((cookie) -> {
                        mFingerprintInscreenDaemon = null;
                    }, 0);
                }
            } catch (NoSuchElementException | RemoteException e) {
                // do nothing
            }
        }
        return mFingerprintInscreenDaemon;
    }

    public void dispatchPress() {
        IFingerprintInscreen daemon = getFingerprintInScreenDaemon();
        try {
            daemon.onPress();
        } catch (RemoteException e) {
            // do nothing
        }
    }

    public void dispatchRelease() {
        IFingerprintInscreen daemon = getFingerprintInScreenDaemon();
        try {
            daemon.onRelease();
        } catch (RemoteException e) {
            // do nothing
        }
    }

    public void dispatchShow() {
        IFingerprintInscreen daemon = getFingerprintInScreenDaemon();
        try {
            daemon.onShowFODView();
        } catch (RemoteException e) {
            // do nothing
        }
    }

    public void dispatchHide() {
        IFingerprintInscreen daemon = getFingerprintInScreenDaemon();
        try {
            daemon.onHideFODView();
        } catch (RemoteException e) {
            // do nothing
        }
    }

    public void showCircle() {
        mIsCircleShowing = true;

        setKeepScreenOn(true);

        setDim(true);
        dispatchPress();

        setImageDrawable(null);
        invalidate();
    }

    public void hideCircle() {
        mIsCircleShowing = false;

        setFODIcon();
        if (mFODAnimation != null) {
            mFODAnimation.setFODAnim();
        }
        invalidate();

        dispatchRelease();
        setDim(false);

        setKeepScreenOn(false);
    }

    public void show() {
        if (!mCanUnlockWithFp){
            // Ignore when unlocking with fp is not possible
            return;
        }

        if (!mUpdateMonitor.isScreenOn()) {
            // Keyguard is shown just after screen turning off
            return;
        }

        updatePosition();

        dispatchShow();
        setVisibility(View.VISIBLE);
    }

    public void hide() {
        setVisibility(View.GONE);
        hideCircle();
        dispatchHide();
    }

    private void updateAlpha() {
        setAlpha(mIsDreaming ? 0.5f : 1.0f);
    }

    private void updatePosition() {
        Display defaultDisplay = mWindowManager.getDefaultDisplay();

        Point size = new Point();
        defaultDisplay.getRealSize(size);

        int rotation = defaultDisplay.getRotation();
        int cutoutMaskedExtra = mCutoutMasked ? mStatusbarHeight : 0;

        int x, y;
        switch (rotation) {
            case Surface.ROTATION_0:
                x = mPositionX;
                y = mPositionY - cutoutMaskedExtra;
                break;
            case Surface.ROTATION_90:
                x = mPositionY;
                y = mPositionX - cutoutMaskedExtra;
                break;
            case Surface.ROTATION_180:
                x = mPositionX;
                y = size.y - mPositionY - mSize - cutoutMaskedExtra;
                break;
            case Surface.ROTATION_270:
                x = size.x - mPositionY - mSize - mNavigationBarSize - cutoutMaskedExtra;
                y = mPositionX;
                break;
            default:
                throw new IllegalArgumentException("Unknown rotation: " + rotation);
        }

        mPressedParams.x = mParams.x = x;
        mPressedParams.y = mParams.y = y;

        if (mIsDreaming) {
            mParams.y += mDreamingOffsetY;
        }

        if (mFODAnimation != null) {
            mFODAnimation.updateParams(mParams.y);
        }

        mWindowManager.updateViewLayout(this, mParams);
        if (mPressedView.getParent() != null) {
            mWindowManager.updateViewLayout(mPressedView, mPressedParams);
        }
    }

    private void setDim(boolean dim) {
        if (dim) {
            int curBrightness = Settings.System.getInt(getContext().getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS, 100);
            int dimAmount = 0;

            IFingerprintInscreen daemon = getFingerprintInScreenDaemon();
            try {
                dimAmount = daemon.getDimAmount(curBrightness);
            } catch (RemoteException e) {
                // do nothing
            }

            if (mShouldBoostBrightness) {
                mPressedParams.screenBrightness = 1.0f;
            }

            mPressedParams.dimAmount = dimAmount / 255.0f;
            if (mPressedView.getParent() == null) {
                mWindowManager.addView(mPressedView, mPressedParams);
            } else {
                mWindowManager.updateViewLayout(mPressedView, mPressedParams);
            }
        } else {
            mPressedParams.screenBrightness = 0.0f;
            mPressedParams.dimAmount = 0.0f;
            if (mPressedView.getParent() != null) {
                mWindowManager.removeView(mPressedView);
            }
        }
    }

    private boolean isPinOrPattern(int userId) {
        int passwordQuality = mLockPatternUtils.getActivePasswordQuality(userId);
        switch (passwordQuality) {
            // PIN
            case DevicePolicyManager.PASSWORD_QUALITY_NUMERIC:
            case DevicePolicyManager.PASSWORD_QUALITY_NUMERIC_COMPLEX:
            // Pattern
            case DevicePolicyManager.PASSWORD_QUALITY_SOMETHING:
                return true;
        }

        return false;
    }

    private class BurnInProtectionTask extends TimerTask {
        @Override
        public void run() {
            long now = System.currentTimeMillis() / 1000 / 60;

            mDreamingOffsetX = (int)(now % (mDreamingMaxOffset * 4));
            if (mDreamingOffsetX > mDreamingMaxOffset * 2) {
                mDreamingOffsetX = mDreamingMaxOffset * 4 - mDreamingOffsetX;
            }

            // Let y to be not synchronized with x, so that we get maximum movement
            mDreamingOffsetY = (int)((now + mDreamingMaxOffset / 3) % (mDreamingMaxOffset * 2));
            if (mDreamingOffsetY > mDreamingMaxOffset * 2) {
                mDreamingOffsetY = mDreamingMaxOffset * 4 - mDreamingOffsetY;
            }

            mDreamingOffsetX -= mDreamingMaxOffset;
            mDreamingOffsetY -= mDreamingMaxOffset;

            mHandler.post(() -> updatePosition());
        }
    };

    @Override
    public void onOverlayChanged() {
        updateCutoutFlags();
    }

    private void updateCutoutFlags() {
        mStatusbarHeight = getContext().getResources().getDimensionPixelSize(
                com.android.internal.R.dimen.status_bar_height_portrait);
        boolean cutoutMasked = getContext().getResources().getBoolean(
                com.android.internal.R.bool.config_maskMainBuiltInDisplayCutout);
        if (mCutoutMasked != cutoutMasked){
            mCutoutMasked = cutoutMasked;
            updatePosition();
        }
    }

    private boolean useWallpaperColor() {
        return Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.FOD_ICON_WALLPAPER_COLOR, 0) != 0;
    }

    private boolean isRecognizingAnimEnabled() {
        return Settings.System.getInt(mContext.getContentResolver(),
            Settings.System.FOD_RECOGNIZING_ANIMATION, 0) != 0;
    }

    private int getFODIcon() {
        return Settings.System.getInt(mContext.getContentResolver(),
            Settings.System.FOD_ICON, 0);
    }

    private void setFODIcon() {
        switch (getFODIcon()) {
            case 0:
                this.setImageResource(R.drawable.fod_icon_default);
                break;
            case 1:
                this.setImageResource(R.drawable.fod_icon_default_1);
                break;
            case 2:
                this.setImageResource(R.drawable.fod_icon_default_2);
                break;
            case 3:
                this.setImageResource(R.drawable.fod_icon_default_3);
                break;
            case 4:
                this.setImageResource(R.drawable.fod_icon_default_4);
                break;
            case 5:
                this.setImageResource(R.drawable.fod_icon_default_5);
                break;
            case 6:
                this.setImageResource(R.drawable.fod_icon_arc_reactor);
                break;
            case 7:
                this.setImageResource(R.drawable.fod_icon_cpt_america_flat);
                break;
            case 8:
                this.setImageResource(R.drawable.fod_icon_cpt_america_flat_gray);
                break;
            case 9:
                this.setImageResource(R.drawable.fod_icon_dragon_black_flat);
                break;
            case 10:
                this.setImageResource(R.drawable.fod_icon_future);
                break;
            case 11:
                this.setImageResource(R.drawable.fod_icon_glow_circle);
                break;
            case 12:
                this.setImageResource(R.drawable.fod_icon_neon_arc);
                break;
            case 13:
                this.setImageResource(R.drawable.fod_icon_neon_arc_gray);
                break;
            case 14:
                this.setImageResource(R.drawable.fod_icon_neon_circle_pink);
                break;
            case 15:
                this.setImageResource(R.drawable.fod_icon_neon_triangle);
                break;
            case 16:
                this.setImageResource(R.drawable.fod_icon_paint_splash_circle);
                break;
            case 17:
                this.setImageResource(R.drawable.fod_icon_rainbow_horn);
                break;
            case 18:
                this.setImageResource(R.drawable.fod_icon_shooky);
                break;
            case 19:
                this.setImageResource(R.drawable.fod_icon_spiral_blue);
                break;
            case 20:
                this.setImageResource(R.drawable.fod_icon_sun_metro);
                break;
            default:
                this.setImageResource(R.drawable.fod_icon_default);
        }
        this.setScaleType(ImageView.ScaleType.CENTER_INSIDE);

        if (useWallpaperColor()) {
            try {
                WallpaperManager wallpaperManager = WallpaperManager.getInstance(mContext);
                Drawable wallpaperDrawable = wallpaperManager.getDrawable();
                Bitmap bitmap = ((BitmapDrawable)wallpaperDrawable).getBitmap();
                if (bitmap != null) {
                    Palette p = Palette.from(bitmap).generate();
                    int wallColor = p.getDominantColor(iconcolor);
                    if (iconcolor != wallColor) {
                        iconcolor = wallColor;
                    }
                    this.setColorFilter(lighter(iconcolor, 3));
                }
            } catch (Exception e) {
                // Nothing to do
            }
        } else {
            this.setColorFilter(null);
        }
    }

    private static int lighter(int color, int factor) {
        int red = Color.red(color);
        int green = Color.green(color);
        int blue = Color.blue(color);

        blue = blue * factor;
        green = green * factor;
        blue = blue * factor;

        blue = blue > 255 ? 255 : blue;
        green = green > 255 ? 255 : green;
        red = red > 255 ? 255 : red;

        return Color.argb(Color.alpha(color), red, green, blue);
    }
}

class FODAnimation extends ImageView {

    private Context mContext;
    private int mAnimationPositionY;
    private WindowManager mWindowManager;
    private boolean mShowing = false;
    private boolean mIsKeyguard;
    private AnimationDrawable mRecognizingAnimDrawable;
    private final WindowManager.LayoutParams mAnimParams = new WindowManager.LayoutParams();

    public FODAnimation(Context context, int positionX, int positionY) {
        super(context);

        mContext = context;
        mWindowManager = mContext.getSystemService(WindowManager.class);

        mAnimParams.height = mContext.getResources().getDimensionPixelSize(R.dimen.fod_animation_size);
        mAnimParams.width = mContext.getResources().getDimensionPixelSize(R.dimen.fod_animation_size);
        final int animationOffsetY = mContext.getResources().getDimensionPixelSize(R.dimen.fod_animation_offset);
        mAnimationPositionY = (int) Math.round(positionY - mAnimParams.height / 2 + animationOffsetY);

        mAnimParams.format = PixelFormat.TRANSLUCENT;
        mAnimParams.type = WindowManager.LayoutParams.TYPE_VOLUME_OVERLAY; // it must be behind FOD icon
        mAnimParams.flags =  WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
        mAnimParams.gravity = Gravity.TOP | Gravity.CENTER;
        mAnimParams.y = mAnimationPositionY;

        this.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        setFODAnim();
    }

    protected void setFODAnim(){
        this.setBackgroundResource(getFODAnimResource());
        mRecognizingAnimDrawable = (AnimationDrawable) this.getBackground();
    }
    private int getFODAnim() {
        return Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.FOD_ANIM, 0);
    }

    private int getFODAnimResource() {
        switch (getFODAnim()) {
            case 1:
                return R.drawable.fod_miui_aod_recognizing_anim;
            case 2:
                return R.drawable.fod_miui_light_recognizing_anim;
            case 3:
                return R.drawable.fod_miui_pop_recognizing_anim;
            case 4:
                return R.drawable.fod_miui_pulse_recognizing_anim;
            case 5:
                return R.drawable.fod_miui_pulse_recognizing_anim_white;
            case 6:
                return R.drawable.fod_miui_rhythm_recognizing_anim;
            case 7:
                return R.drawable.fod_op_cosmos_recognizing_anim;
            case 8:
                return R.drawable.fod_op_mclaren_recognizing_anim;
            case 9:
                return R.drawable.fod_pureview_future_recognizing_anim;
            case 10:
                return R.drawable.fod_pureview_molecular_recognizing_anim;
            case 11:
                return R.drawable.fod_op_energy_recognizing_anim;
        }
        return R.drawable.fod_miui_normal_recognizing_anim;
    }

    public void updateParams(int dreamingOffsetY) {
        mAnimationPositionY = (int) Math.round(dreamingOffsetY
                                  - (mContext.getResources().getDimensionPixelSize(R.dimen.fod_animation_size) / 2)
                                  + mContext.getResources().getDimensionPixelSize(R.dimen.fod_animation_offset));
        mAnimParams.y = mAnimationPositionY;
    }

    public void setAnimationKeyguard(boolean state) {
        mIsKeyguard = state;
    }

    public void showFODanimation() {
        if (mAnimParams != null && !mShowing && mIsKeyguard) {
            mShowing = true;
            if (this.getWindowToken() == null){
                mWindowManager.addView(this, mAnimParams);
                mWindowManager.updateViewLayout(this, mAnimParams);
            }
            mRecognizingAnimDrawable.start();
        }
    }

    public void hideFODanimation() {
        if (mShowing) {
            mShowing = false;
            if (mRecognizingAnimDrawable != null) {
                this.clearAnimation();
                mRecognizingAnimDrawable.stop();
                mRecognizingAnimDrawable.selectDrawable(0);
            }
            if (this.getWindowToken() != null) {
                mWindowManager.removeView(this);
            }
        }
    }
}
