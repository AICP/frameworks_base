/*
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
#ifndef ANDROID_GRAPHICS_PAINT_H
#define ANDROID_GRAPHICS_PAINT_H

#include <cutils/compiler.h>
#include <sys/cdefs.h>

__BEGIN_DECLS

/**
 * Opaque handle for a native graphics canvas.
 */
typedef struct APaint APaint;

/**
 * Predefined Image filter type.
 */
enum AImageFilter {
    /** Drop shadow image filter for PointerIcons. */
    AIMAGE_FILTER_DROP_SHADOW_FOR_POINTER_ICON = 0,
};

/** Bitmap pixel format. */
enum ABlendMode {
    /** replaces destination with zero: fully transparent */
    ABLEND_MODE_CLEAR    = 0,
    /** source over destination */
    ABLEND_MODE_SRC_OVER = 1,
    /** replaces destination **/
    ABLEND_MODE_SRC      = 2,
};

ANDROID_API APaint* APaint_createPaint();

ANDROID_API void APaint_destroyPaint(APaint* paint);

ANDROID_API void APaint_setBlendMode(APaint* paint, ABlendMode blendMode);

ANDROID_API void APaint_setImageFilter(APaint* paint, AImageFilter imageFilter);

__END_DECLS

#ifdef	__cplusplus
namespace android {
namespace graphics {
    class Paint {
    public:
        Paint() : mPaint(APaint_createPaint()) {}
        ~Paint() { APaint_destroyPaint(mPaint); }

        void setBlendMode(ABlendMode blendMode) { APaint_setBlendMode(mPaint, blendMode); }

        void setImageFilter(AImageFilter imageFilter) {
            APaint_setImageFilter(mPaint, imageFilter);
        }

        const APaint& get() const { return *mPaint; }

    private:
        APaint* mPaint;
    };
}; // namespace graphics
}; // namespace android
#endif // __cplusplus


#endif // ANDROID_GRAPHICS_PAINT_H