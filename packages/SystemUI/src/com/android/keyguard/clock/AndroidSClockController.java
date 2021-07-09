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
package com.android.keyguard.clock;

import static android.app.slice.Slice.HINT_LIST_ITEM;

import android.animation.ValueAnimator;
import android.app.PendingIntent;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.Paint.Style;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.transition.Fade;
import android.transition.TransitionManager;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;

import com.android.internal.graphics.ColorUtils;
import com.android.settingslib.Utils;
import com.android.systemui.R;
import com.android.systemui.colorextraction.SysuiColorExtractor;
import com.android.systemui.plugins.ClockPlugin;

import com.android.systemui.keyguard.KeyguardSliceProvider;
import com.android.keyguard.KeyguardSliceView.KeyguardSliceTextView;
import com.android.keyguard.KeyguardSliceView.Row;

import androidx.slice.Slice;
import androidx.slice.SliceItem;
import androidx.slice.core.SliceQuery;
import androidx.slice.widget.ListContent;
import androidx.slice.widget.RowContent;
import androidx.slice.widget.SliceContent;

import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.TimeZone;

/**
 * Plugin for the default clock face used only to provide a preview.
 */
public class AndroidSClockController implements ClockPlugin {

    private float mTextSizeNormal;
    private float mTextSizeBig;
    private float mRowTextSize;
    private boolean mHasVisibleNotification = false;
    private boolean mClockState = false;
    private float mClockDivideY = 6f;

    /**
     * Resources used to get title and thumbnail.
     */
    private final Resources mResources;

    /**
     * LayoutInflater used to inflate custom clock views.
     */
    private final LayoutInflater mLayoutInflater;

    /**
     * Extracts accent color from wallpaper.
     */
    private final SysuiColorExtractor mColorExtractor;

    /**
     * Renders preview from clock view.
     */
    private final ViewPreviewer mRenderer = new ViewPreviewer();

    /**
     * Helper to extract colors from wallpaper palette for clock face.
     */
    private final ClockPalette mPalette = new ClockPalette();

    /**
     * Root view of clock.
     */
    private ClockLayout mView;
    private ClockLayout mBigClockView;

    /**
     * Text clock in preview view hierarchy.
     */
    private TextClock mClock;
    private ConstraintLayout mContainer;
    private ConstraintLayout mContainerBig;
    private ConstraintSet mContainerSet = new ConstraintSet();
    private ConstraintSet mContainerSetBig = new ConstraintSet();

    private Context mContext;
    private Row mRow;
    private TextView mTitle;
    private int mIconSize;
    private Slice mSlice;
    private boolean mHasHeader;
    private final HashMap<View, PendingIntent> mClickActions = new HashMap<>();
    private Uri mKeyguardSliceUri;

    private int mTextColor;
    private float mDarkAmount = 0;
    private int mRowHeight = 0;

    private Typeface mSliceTypeface;

    /**
     * Create a DefaultClockController instance.
     *
     * @param res Resources contains title and thumbnail.
     * @param inflater Inflater used to inflate custom clock views.
     * @param colorExtractor Extracts accent color from wallpaper.
     */
    public AndroidSClockController(Resources res, LayoutInflater inflater,
            SysuiColorExtractor colorExtractor) {
        mResources = res;
        mLayoutInflater = inflater;
        mColorExtractor = colorExtractor;
        mContext = mLayoutInflater.getContext();
    }

    private void createViews() {
        mView = (ClockLayout) mLayoutInflater
                .inflate(R.layout.android_s_clock, null);
        final ClockLayout viewBig = (ClockLayout) mLayoutInflater
                .inflate(R.layout.android_s_big_clock, null);
        mClock = mView.findViewById(R.id.clock);
        mContainer = mView.findViewById(R.id.clock_view);
        mContainerBig = viewBig.findViewById(R.id.clock_view);
        mContainerSet.clone(mContainer);
        mContainerSetBig.clone(mContainerBig);
        mClock.setFormat12Hour("hh\nmm");
        mClock.setFormat24Hour("kk\nmm");

        mTitle = mView.findViewById(R.id.title);
        mRow = mView.findViewById(R.id.row);

        mTextSizeNormal = mContext.getResources().getDimension(R.dimen.sclock_clock_font_size);
        mTextSizeBig =  mContext.getResources().getDimension(R.dimen.sclock_big_clock_font_size);

        mIconSize = (int) mContext.getResources().getDimension(R.dimen.sclock_icon_size);
        mRowTextSize = mContext.getResources().getDimension(R.dimen.sclock_date_font_size);
        mTextColor = Utils.getColorAttrDefaultColor(mContext, R.attr.wallpaperTextColor);
        mSliceTypeface = mClock.getTypeface();

        animate();
    }

    @Override
    public void onDestroyView() {
        mView = null;
        mClock = null;
        mContainer = null;
    }

    @Override
    public String getName() {
        return "android_s";
    }

    @Override
    public String getTitle() {
        return mResources.getString(R.string.clock_title_android_s);
    }

    @Override
    public Bitmap getThumbnail() {
        return BitmapFactory.decodeResource(mResources, R.drawable.default_thumbnail);
    }

    @Override
    public Bitmap getPreview(int width, int height) {

        View previewView = mLayoutInflater.inflate(R.layout.android_s_clock, null);
        TextClock previewClock = mView.findViewById(R.id.clock);
        previewClock.setFormat12Hour("hh\nmm");
        previewClock.setFormat24Hour("kk\nmm");

        return mRenderer.createPreview(previewView, width, height);
    }

    @Override
    public View getView() {
        if (mView == null) {
            createViews();
        }
        return mView;
    }

    @Override
    public View getBigClockView() {
        return null;
    }

    @Override
    public int getPreferredY(int totalHeight) {
        return (int) (totalHeight / mClockDivideY);
    }

    @Override
    public void setStyle(Style style) {}

    @Override
    public void setTextColor(int color) {
        updateColor();
    }

    @Override
    public void setColorPalette(boolean supportsDarkText, int[] colorPalette) {
        mPalette.setColorPalette(supportsDarkText, colorPalette);
        updateColor();
    }

    private void updateColor() {
        mClock.setTextColor(mPalette.getSecondaryColor());
    }

    @Override
    public void setSlice(Slice slice) {
        mSlice = slice;
        if (mSlice == null) {
            mRow.setVisibility(View.GONE);
            mHasHeader = false;
            return;
        }
        mClickActions.clear();

        ListContent lc = new ListContent(mContext, mSlice);
        SliceContent headerContent = lc.getHeader();
        mHasHeader = headerContent != null && !headerContent.getSliceItem().hasHint(HINT_LIST_ITEM);
        List<SliceContent> subItems = new ArrayList<>();
        for (int i = 0; i < lc.getRowItems().size(); i++) {
            SliceContent subItem = lc.getRowItems().get(i);
            String itemUri = subItem.getSliceItem().getSlice().getUri().toString();
            // Filter out the action row
            if (!KeyguardSliceProvider.KEYGUARD_ACTION_URI.equals(itemUri)) {
                subItems.add(subItem);
            }
        }

        final int subItemsCount = subItems.size();
        final int blendedColor = getTextColor();
        final int startIndex = mHasHeader ? 1 : 0; // First item is header; skip it
        mRow.setVisibility(subItemsCount > 0 ? View.VISIBLE : View.GONE);

        if (!mHasHeader) {
            mTitle.setVisibility(View.GONE);
        } else {
            mTitle.setVisibility(View.VISIBLE);
            RowContent header = lc.getHeader();
            SliceItem mainTitle = header.getTitleItem();
            CharSequence title = mainTitle != null ? mainTitle.getText() : null;
            mTitle.setText(title);
            mTitle.setTextSize(mRowTextSize);
            if (mSliceTypeface != null) mTitle.setTypeface(mSliceTypeface);
            if (header.getPrimaryAction() != null
                    && header.getPrimaryAction().getAction() != null) {
                mClickActions.put(mTitle, header.getPrimaryAction().getAction());
            }
        }

        for (int i = startIndex; i < subItemsCount; i++) {
            RowContent rc = (RowContent) subItems.get(i);
            SliceItem item = rc.getSliceItem();
            final Uri itemTag = item.getSlice().getUri();
            final boolean isWeatherSlice = itemTag.toString().equals(KeyguardSliceProvider.KEYGUARD_WEATHER_URI);
            // Try to reuse the view if already exists in the layout
            KeyguardSliceTextView button = mRow.findViewWithTag(itemTag);
            if (button == null) {
                button = new KeyguardSliceTextView(mContext);
                button.setTextSize(mRowTextSize);
                button.setTextColor(blendedColor);
                button.setGravity(Gravity.START);
                button.setTag(itemTag);
                final int viewIndex = i - (mHasHeader ? 1 : 0);
                mRow.addView(button, viewIndex);
            } else {
                button.setTextSize(mRowTextSize);
                button.setGravity(Gravity.START);
            }
            button.setShouldTintDrawable(!isWeatherSlice);

            if (mSliceTypeface != null) button.setTypeface(mSliceTypeface);
            // button.setVisibility(isDateSlice ? View.GONE : View.VISIBLE);

            /*
            LinearLayout.LayoutParams layoutParams = (LinearLayout.LayoutParams) button.getLayoutParams();
            layoutParams.height = LinearLayout.LayoutParams.WRAP_CONTENT;
            layoutParams.width = LinearLayout.LayoutParams.MATCH_PARENT;
            layoutParams.gravity = Gravity.START;
            layoutParams.topMargin = 8;
            layoutParams.bottomMargin = 8;
            button.setLayoutParams(layoutParams);
            */

            PendingIntent pendingIntent = null;
            if (rc.getPrimaryAction() != null) {
                pendingIntent = rc.getPrimaryAction().getAction();
            }
            mClickActions.put(button, pendingIntent);

            final SliceItem titleItem = rc.getTitleItem();
            button.setText(titleItem == null ? null : titleItem.getText());
            button.setTextSize(mRowTextSize);
            button.setContentDescription(rc.getContentDescription());

            Drawable iconDrawable = null;
            SliceItem icon = SliceQuery.find(item.getSlice(),
                    android.app.slice.SliceItem.FORMAT_IMAGE);
            if (icon != null) {
                iconDrawable = icon.getIcon().loadDrawable(mContext);
                if (iconDrawable != null) {
                    final int width = (int) (iconDrawable.getIntrinsicWidth()
                            / (float) iconDrawable.getIntrinsicHeight() * (mIconSize));
                    iconDrawable.setBounds(0, 0, width, mIconSize);
                }
            }
            button.setCompoundDrawables(iconDrawable, null, null, null);
        }

        // Removing old views
        for (int i = 0; i < mRow.getChildCount(); i++) {
            View child = mRow.getChildAt(i);
            if (!mClickActions.containsKey(child)) {
                mRow.removeView(child);
                i--;
            }
        }

        mTitle.requestLayout();
        mRow.requestLayout();

        mRowHeight = mRow.getHeight() + (mHasHeader ? mTitle.getHeight() : 0);
        if (mRow.getChildCount() != 0) mContainerSetBig.setMargin(mClock.getId(), ConstraintSet.TOP, mRowHeight);
    };

    /**
     * Set whether or not the lock screen is showing notifications.
     */
    @Override
    public void setHasVisibleNotifications(boolean hasVisibleNotifications) {
        if (hasVisibleNotifications == mHasVisibleNotification) {
            return;
        }
        mHasVisibleNotification = hasVisibleNotifications;
        animate();
    }

    private void animate() {
        getView();
        if (!mHasVisibleNotification) {
            if (!mClockState) {
                mClock.animate()
                            .setUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                                @Override
                                public void onAnimationUpdate(ValueAnimator animation) {
                                    if (mClock != null) {
                                        mClock.setTextSize(mTextSizeBig);
                                        mClock.requestLayout();
                                    }
                                }
                            })
                            .setDuration(350)
                            .withStartAction(() -> {
                                TransitionManager.beginDelayedTransition(mContainer,
                                new Fade().setDuration(350).addTarget(mContainer));
                                mContainerSetBig.applyTo(mContainer);
                            })
                            .withEndAction(() -> {
                                mClockState = true;
                                setSlice(mSlice);
                            })
                            .start();
            }
        } else {
            if (mClockState) {
                mClock.animate()
                            .setUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                                @Override
                                public void onAnimationUpdate(ValueAnimator animation) {
                                    if (mClock != null) {
                                        mClock.setTextSize(mTextSizeNormal);
                                        mClock.requestLayout();
                                    }
                                }
                            })
                            .setDuration(350)
                            .withStartAction(() -> {
                                TransitionManager.beginDelayedTransition(mContainer,
                                new Fade().setDuration(350).addTarget(mContainer));
                                mContainerSet.applyTo(mContainer);
                            })
                            .withEndAction(() -> {
                                mClockState = false;
                                setSlice(mSlice);
                            })
                            .start();
            }
        }
    }

    @Override
    public void onTimeTick() {
        animate();
    }

    @Override
    public void setDarkAmount(float darkAmount) {
        mView.setDarkAmount(darkAmount);
        for (int i = 0; i < mRow.getChildCount(); i++) {
            KeyguardSliceTextView child = (KeyguardSliceTextView) mRow.getChildAt(i);
            child.setTextSize(mRowTextSize);

        }
        mTitle.setTextSize(mRowTextSize);
        mRow.setDarkAmount(darkAmount);
        mTitle.requestLayout();
        mRow.requestLayout();
        mDarkAmount = darkAmount;
        updateTextColors();
    }

    @Override
    public void onTimeZoneChanged(TimeZone timeZone) {}

    @Override
    public boolean shouldShowStatusArea() {
        return false;
    }

    private void updateTextColors() {
        final int blendedColor = getTextColor();
        mTitle.setTextColor(blendedColor);
        int childCount = mRow.getChildCount();
        for (int i = 0; i < childCount; i++) {
            View v = mRow.getChildAt(i);
            if (v instanceof TextView) {
                ((TextView) v).setTextColor(blendedColor);
            }
        }
    }

    int getTextColor() {
        return ColorUtils.blendARGB(mTextColor, Color.WHITE, mDarkAmount);
    }
}
