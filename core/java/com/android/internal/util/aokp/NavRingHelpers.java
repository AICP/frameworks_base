/*
 * Copyright (C) 2013 The CyanogenMod Project
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

package com.android.internal.util.aokp;

import android.app.SearchManager;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuffXfermode;
import android.graphics.PorterDuff.Mode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Xfermode;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.StateListDrawable;
import android.net.Uri;
import android.media.AudioManager;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.TextUtils;

import static com.android.internal.util.aokp.AwesomeConstants.*;
import com.android.internal.widget.multiwaveview.GlowPadView;
import com.android.internal.widget.multiwaveview.TargetDrawable;

import java.io.File;
import java.net.URISyntaxException;

public class NavRingHelpers {

    private NavRingHelpers() {
    }

    public static TargetDrawable getTargetDrawable(Context context, String action) {
        int resourceId = -1;
        final Resources res = context.getResources();

        if (TextUtils.isEmpty(action)) {
            TargetDrawable drawable = new TargetDrawable(res, com.android.internal.R.drawable.ic_action_empty);
            drawable.setEnabled(false);
            return drawable;
        }

        AwesomeConstant IconEnum = fromString(action);
            switch (IconEnum) {
            case ACTION_NULL:
                resourceId = com.android.internal.R.drawable.ic_action_empty;
                break;
            case ACTION_ASSIST:
                resourceId = com.android.internal.R.drawable.ic_action_assist_generic;
                break;
            case ACTION_IME:
                resourceId = com.android.internal.R.drawable.ic_action_ime_switcher;
                break;
            case ACTION_VIB:
                resourceId = com.android.internal.R.drawable.ic_action_vib;
                break;
            case ACTION_SILENT:
                resourceId = com.android.internal.R.drawable.ic_action_silent;
                break;
            case ACTION_SILENT_VIB:
                resourceId = com.android.internal.R.drawable.ic_action_ring_vib_silent;
                break;
            case ACTION_LAST_APP:
                resourceId = com.android.internal.R.drawable.ic_action_lastapp;
                break;
            case ACTION_KILL:
                resourceId = com.android.internal.R.drawable.ic_action_killtask;
                break;
            case ACTION_POWER:
                resourceId = com.android.internal.R.drawable.ic_action_power;
                break;
            case ACTION_APP:
                // no pre-defined action, try to resolve URI
                try {
                    Intent intent = Intent.parseUri(action, 0);
                    PackageManager pm = context.getPackageManager();
                    ActivityInfo info = intent.resolveActivityInfo(pm, PackageManager.GET_ACTIVITIES);

                    if (info == null) {
                        TargetDrawable drawable = new TargetDrawable(res, com.android.internal.R.drawable.ic_action_empty);
                        drawable.setEnabled(false);
                        return drawable;
                    }

                    Drawable activityIcon = info.loadIcon(pm);
                    Drawable iconBg = res.getDrawable(
                            com.android.internal.R.drawable.ic_navbar_blank);
                    Drawable iconBgActivated = res.getDrawable(
                            com.android.internal.R.drawable.ic_navbar_blank_activated);

                    int margin = (int)(iconBg.getIntrinsicHeight() / 3);
                    LayerDrawable icon = new LayerDrawable (new Drawable[] { iconBg, activityIcon });
                    LayerDrawable iconActivated = new LayerDrawable (new Drawable[] { iconBgActivated, activityIcon });

                    icon.setLayerInset(1, margin, margin, margin, margin);
                    iconActivated.setLayerInset(1, margin, margin, margin, margin);

                    StateListDrawable selector = new StateListDrawable();
                    selector.addState(new int[] {
                        android.R.attr.state_enabled,
                        -android.R.attr.state_active,
                        -android.R.attr.state_focused
                        }, icon);
                    selector.addState(new int[] {
                        android.R.attr.state_enabled,
                        android.R.attr.state_active,
                        -android.R.attr.state_focused
                        }, iconActivated);
                    selector.addState(new int[] {
                        android.R.attr.state_enabled,
                        -android.R.attr.state_active,
                        android.R.attr.state_focused
                        }, iconActivated);
                    return new TargetDrawable(res, selector);
                } catch (URISyntaxException e) {
                    resourceId = com.android.internal.R.drawable.ic_action_empty;
                }
                break;
            }
        TargetDrawable drawable = new TargetDrawable(res, resourceId);
        if (resourceId == com.android.internal.R.drawable.ic_action_empty) {
            drawable.setEnabled(false);
        }
        return drawable;
    }

    public static TargetDrawable getCustomDrawable(Context context, String action) {
        final Resources res = context.getResources();

        File f = new File(Uri.parse(action).getPath());
        Drawable activityIcon = new BitmapDrawable(res,
                         getRoundedCornerBitmap(BitmapFactory.decodeFile(f.getAbsolutePath())));

        Drawable iconBg = res.getDrawable(
                com.android.internal.R.drawable.ic_navbar_blank);
        Drawable iconBgActivated = res.getDrawable(
                    com.android.internal.R.drawable.ic_navbar_blank_activated);

        int margin = (int)(iconBg.getIntrinsicHeight() / 3);
        LayerDrawable icon = new LayerDrawable (new Drawable[] { iconBg, activityIcon });
        LayerDrawable iconActivated = new LayerDrawable (new Drawable[] { iconBgActivated, activityIcon });

        icon.setLayerInset(1, margin, margin, margin, margin);
        iconActivated.setLayerInset(1, margin, margin, margin, margin);

        StateListDrawable selector = new StateListDrawable();
        selector.addState(new int[] {
                android.R.attr.state_enabled,
                -android.R.attr.state_active,
                -android.R.attr.state_focused
            }, icon);
        selector.addState(new int[] {
                android.R.attr.state_enabled,
                android.R.attr.state_active,
                -android.R.attr.state_focused
            }, iconActivated);
        selector.addState(new int[] {
                android.R.attr.state_enabled,
                -android.R.attr.state_active,
                android.R.attr.state_focused
            }, iconActivated);
        return new TargetDrawable(res, selector);
    }

    public static Bitmap getRoundedCornerBitmap(Bitmap bitmap) {
        Bitmap output = Bitmap.createBitmap(bitmap.getWidth(),
            bitmap.getHeight(), Config.ARGB_8888);
        Canvas canvas = new Canvas(output);

        final int color = 0xff424242;
        final Paint paint = new Paint();
        final Rect rect = new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());
        final RectF rectF = new RectF(rect);
        final float roundPx = 24;
        paint.setAntiAlias(true);
        canvas.drawARGB(0, 0, 0, 0);
        paint.setColor(color);
        canvas.drawRoundRect(rectF, roundPx, roundPx, paint);
        paint.setXfermode(new PorterDuffXfermode(Mode.SRC_IN));
        canvas.drawBitmap(bitmap, rect, rect, paint);
        return output;
    }
}
