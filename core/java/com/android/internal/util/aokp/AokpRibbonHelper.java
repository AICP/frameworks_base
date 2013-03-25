package com.android.internal.util.aokp;

import java.util.ArrayList;

import android.content.Context;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.ScrollView;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;

public class AokpRibbonHelper {

    private static final String TAG = "Aokp Ribbon";

    private static final String TARGET_DELIMITER = "|";
    public static final int LOCKSCREEN = 0;
    public static final int NOTIFICATIONS = 1;
    public static final int SWIPE_RIBBON = 2;
    public static final int QUICK_SETTINGS = 3;

    public static final LinearLayout.LayoutParams PARAMS_TARGET = new LinearLayout.LayoutParams(
            LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, 1f);

    public static final LinearLayout.LayoutParams PARAMS_TARGET_VERTICAL = new LinearLayout.LayoutParams(
            LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT, 1f);

    public static final LinearLayout.LayoutParams PARAMS_TARGET_SCROLL = new LinearLayout.LayoutParams(
            LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, 1f);

    public static HorizontalScrollView getRibbon(Context mContext, ArrayList<String> shortTargets,
             ArrayList<String> longTargets, ArrayList<String> customIcons, boolean text, int color, int size) {
        int length = shortTargets.size();
        HorizontalScrollView targetScrollView = new HorizontalScrollView(mContext);
        if (length > 0 && (shortTargets.size() == customIcons.size())) {

            ArrayList<RibbonTarget> targets = new ArrayList<RibbonTarget>();
            for (int i = 0; i < length; i++) {
                if (!TextUtils.isEmpty(shortTargets.get(i))) {
                    RibbonTarget newTarget = null;
                    newTarget = new RibbonTarget(mContext, shortTargets.get(i), longTargets.get(i), customIcons.get(i), text, color, size);
                    if (newTarget != null) {
                        targets.add(newTarget);
                    }
                }
            }
            LinearLayout targetsLayout = new LinearLayout(mContext);
            targetsLayout.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            targetScrollView.setHorizontalFadingEdgeEnabled(true);
            for (int i = 0; i < targets.size(); i++) {
                targetsLayout.addView(targets.get(i).getView(), PARAMS_TARGET_SCROLL);
            }
            targetScrollView.addView(targetsLayout, PARAMS_TARGET);
        }
        return targetScrollView;
    }

    public static ScrollView getVerticalRibbon(Context mContext, ArrayList<String> shortTargets,
                    ArrayList<String> longTargets, ArrayList<String> customIcons, boolean text, int color, int size) {
        int length = shortTargets.size();
        ScrollView targetScrollView = new ScrollView(mContext);
        if (length > 0 && (shortTargets.size() == customIcons.size())) {

            ArrayList<RibbonTarget> targets = new ArrayList<RibbonTarget>();
            for (int i = 0; i < length; i++) {
                if (!TextUtils.isEmpty(shortTargets.get(i))) {
                    RibbonTarget newTarget = null;
                    newTarget = new RibbonTarget(mContext, shortTargets.get(i), longTargets.get(i), customIcons.get(i), text, color, size);
                    if (newTarget != null) {
                        targets.add(newTarget);
                    }
                }
            }
            LinearLayout targetsLayout = new LinearLayout(mContext);
            targetsLayout.setOrientation(LinearLayout.VERTICAL);
            targetsLayout.setGravity(Gravity.CENTER);
            targetScrollView.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
            for (int i = 0; i < targets.size(); i++) {
                targetsLayout.addView(targets.get(i).getView(), PARAMS_TARGET_SCROLL);
            }
            targetScrollView.addView(targetsLayout, PARAMS_TARGET_SCROLL);
        }
        return targetScrollView;
    }
}