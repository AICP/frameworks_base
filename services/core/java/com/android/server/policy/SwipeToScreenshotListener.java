/*
 * Copyright (C) 2019 The PixelExperience Project
 * Copyright (C) 2022 FlamingoOS Project
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

package com.android.server.policy;

import android.content.Context;
import android.os.SystemProperties;
import android.provider.Settings;
import android.util.Log;
import android.util.DisplayMetrics;
import android.view.MotionEvent;
import android.view.WindowManagerPolicyConstants.PointerEventListener;

public final class SwipeToScreenshotListener implements PointerEventListener {
    private static final String TAG = "SwipeToScreenshotListener";

    private static final int STATE_NONE = 0;
    private static final int STATE_DETECTING = 1;
    private static final int STATE_DETECTED_FALSE = 2;
    private static final int STATE_DETECTED_TRUE = 3;
    private static final int STATE_NO_DETECT = 4;

    private final Context mContext;
    private final float[] mInitMotionY;
    private final int[] mPointerIds;
    private final int mThreeGestureThreshold;
    private final int mThreshold;
    private final Callbacks mCallbacks;
    private final DisplayMetrics mDisplayMetrics;
    private int mThreeGestureState = STATE_NONE;

    public SwipeToScreenshotListener(Context context, Callbacks callbacks) {
        mPointerIds = new int[3];
        mInitMotionY = new float[3];
        mContext = context;
        mCallbacks = callbacks;
        mDisplayMetrics = mContext.getResources().getDisplayMetrics();
        mThreshold = (int) (50.0f * mDisplayMetrics.density);
        mThreeGestureThreshold = mThreshold * 3;
    }

    @Override
    public void onPointerEvent(MotionEvent event) {
        if (event.getAction() == 0) {
            changeThreeGestureState(STATE_NONE);
        } else if (mThreeGestureState == STATE_NONE && event.getPointerCount() == 3) {
            if (checkIsStartThreeGesture(event)) {
                changeThreeGestureState(STATE_DETECTING);
                for (int i = 0; i < 3; i++) {
                    mPointerIds[i] = event.getPointerId(i);
                    mInitMotionY[i] = event.getY(i);
                }
            } else {
                changeThreeGestureState(STATE_NO_DETECT);
            }
        }
        if (mThreeGestureState == STATE_DETECTING) {
            if (event.getPointerCount() != 3) {
                changeThreeGestureState(STATE_DETECTED_FALSE);
                return;
            }
            if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
                float distance = 0.0f;
                int i = 0;
                while (i < 3) {
                    int index = event.findPointerIndex(mPointerIds[i]);
                    if (index < 0 || index >= 3) {
                        changeThreeGestureState(STATE_DETECTED_FALSE);
                        return;
                    } else {
                        distance += event.getY(index) - mInitMotionY[i];
                        i++;
                    }
                }
                if (distance >= mThreeGestureThreshold) {
                    changeThreeGestureState(STATE_DETECTED_TRUE);
                    mCallbacks.onSwipeThreeFinger();
                }
            }
        }
    }

    private void changeThreeGestureState(int state) {
        if (mThreeGestureState == state) return;
        mThreeGestureState = state;
        final boolean shouldEnableProp = mThreeGestureState == STATE_DETECTED_TRUE ||
            mThreeGestureState == STATE_DETECTING;
        try {
            SystemProperties.set("sys.android.screenshot", String.valueOf(shouldEnableProp));
        } catch(Exception e) {
            Log.e(TAG, "Exception while setting prop", e);
        }
    }

    private boolean checkIsStartThreeGesture(final MotionEvent event) {
        if (event.getEventTime() - event.getDownTime() > 500) {
            return false;
        }
        final int height = mDisplayMetrics.heightPixels;
        final int width = mDisplayMetrics.widthPixels;
        float minX = Float.MAX_VALUE;
        float maxX = Float.MIN_VALUE;
        float minY = Float.MAX_VALUE;
        float maxY = Float.MIN_VALUE;
        for (int i = 0; i < event.getPointerCount(); i++) {
            final float x = event.getX(i);
            final float y = event.getY(i);
            if (y > (height - mThreshold)) {
                return false;
            }
            maxX = Math.max(maxX, x);
            minX = Math.min(minX, x);
            maxY = Math.max(maxY, y);
            minY = Math.min(minY, y);
        }
        if ((maxY - minY) <= mDisplayMetrics.density * 150f) {
            return (maxX - minX) <= Math.min(width, height);
        }
        return false;
    }

    interface Callbacks {
        void onSwipeThreeFinger();
    }
}
