package com.android.internal.util.aokp;

import java.util.ArrayList;

import android.content.Context;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.util.DisplayMetrics;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.HorizontalScrollView;
import android.widget.ScrollView;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.RelativeLayout;

public class AokpRibbonHelper {

    private static final String TAG = "Aokp Ribbon";

    public static final int HORIZONTAL_RIBBON_ITEMS = 0;
    public static final int HORIZONTAL_RIBBON_SIZE = 1;
    public static final int HORIZONTAL_RIBBON_MARGIN = 2;

    public static final int WINDOW_COLOR = 0;
    public static final int WINDOW_ANIMATION = 1;
    public static final int WINDOW_ANIMATION_DURATION = 2;
    public static final int WINDOW_SIZE = 3;
    public static final int WINDOW_SPACE = 4;

    public static final int ENABLE_RIBBON = 0;
    public static final int RIBBON_ITEMS = 1;
    public static final int RIBBON_SIZE = 2;
    public static final int HANDLE_WEIGHT = 3;
    public static final int HANDLE_HEIGHT = 4;
    public static final int HANDLE_COLOR = 5;
    public static final int HANDLE_VIBRATE = 6;
    public static final int HANDLE_LOCATION = 7;
    public static final int LONG_SWIPE = 8;
    public static final int LONG_PRESS = 9;
    public static final int AUTO_HIDE_DURATION = 10;
    public static final int RIBBON_COLOR = 11;
    public static final int RIBBON_ANIMATION_TYPE = 12;
    public static final int RIBBON_ANIMATION_DURATION = 13;
    public static final int RIBBON_MARGIN = 14;

    public static final LinearLayout.LayoutParams PARAMS_TARGET = new LinearLayout.LayoutParams(
            LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, 1f);

    public static final LinearLayout.LayoutParams PARAMS_TARGET_VERTICAL = new LinearLayout.LayoutParams(
            LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT, 1f);

    public static final LinearLayout.LayoutParams PARAMS_TARGET_SCROLL = new LinearLayout.LayoutParams(
            LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, 1f);

    public static final LinearLayout.LayoutParams PARAMS_GRID = new LinearLayout.LayoutParams(
            0, LayoutParams.WRAP_CONTENT, 1f);

}
