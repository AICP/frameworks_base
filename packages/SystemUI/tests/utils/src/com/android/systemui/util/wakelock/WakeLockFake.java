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

package com.android.systemui.util.wakelock;

import android.content.Context;

import com.android.internal.util.Preconditions;

public class WakeLockFake implements WakeLock {

    private int mAcquired = 0;

    @Override
    public void acquire(String why) {
        mAcquired++;
    }

    @Override
    public void release(String why) {
        Preconditions.checkState(mAcquired > 0);
        mAcquired--;
    }

    @Override
    public Runnable wrap(Runnable runnable) {
        acquire(WakeLockFake.class.getSimpleName());
        return () -> {
            try {
                runnable.run();
            } finally {
                release(WakeLockFake.class.getSimpleName());
            }
        };
    }

    public boolean isHeld() {
        return mAcquired > 0;
    }

    public static class Builder extends WakeLock.Builder {
        private WakeLock mWakeLock;

        public Builder(Context context) {
            super(context);
        }

        public void setWakeLock(WakeLock wakeLock) {
            mWakeLock = wakeLock;
        }

        public WakeLock build() {
            if (mWakeLock != null) {
                return mWakeLock;
            }

            return super.build();
        }
    }
}
