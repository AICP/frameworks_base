
package com.android.systemui.statusbar.view;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;

import com.android.systemui.ExpandHelper;
import com.android.systemui.R;
import com.android.systemui.statusbar.policy.NotificationRowLayout;

public class PieExpandPanel extends LinearLayout {
    private ExpandHelper mExpandHelper;
    private NotificationRowLayout latestItems;

    public PieExpandPanel(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PieExpandPanel(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public void init(NotificationRowLayout pile, View scrollView) {
        latestItems = pile;
        int minHeight = getResources().getDimensionPixelSize(R.dimen.notification_row_min_height);
        int maxHeight = getResources().getDimensionPixelSize(R.dimen.notification_row_max_height);
        mExpandHelper = new ExpandHelper(mContext, pile, minHeight, maxHeight);
        mExpandHelper.setEventSource(this);
        mExpandHelper.setScrollView(scrollView);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        MotionEvent cancellation = MotionEvent.obtain(ev);
        cancellation.setAction(MotionEvent.ACTION_CANCEL);

        boolean intercept = mExpandHelper.onInterceptTouchEvent(ev) ||
                super.onInterceptTouchEvent(ev);
        if (intercept) {
            latestItems.onInterceptTouchEvent(cancellation);
        }
        return intercept;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        boolean handled = mExpandHelper.onTouchEvent(ev) ||
                super.onTouchEvent(ev);
        return handled;
    }
}
