package com.android.systemui.statusbar.policy;


import android.content.Context;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;

import com.android.systemui.R;
import com.android.systemui.aokp.AokpTarget;
import com.android.systemui.recent.RecentTasksLoader;


public class ExtensibleKeyButtonView extends KeyButtonView {

    private AokpTarget mAokpTarget;

    public String mClickAction, mLongpress;

    public ExtensibleKeyButtonView(Context context, AttributeSet attrs, String ClickAction, String Longpress) {
        super(context, attrs);
        setActions(ClickAction,Longpress);
    }

    public void setAokpTarget(AokpTarget targ){
        mAokpTarget = targ;
    }

    public void setActions(String ClickAction, String Longpress) {
        mClickAction = ClickAction;
        mLongpress = Longpress;
        if (ClickAction != null) {
            if (ClickAction.equals(AokpTarget.ACTION_HOME)) {
                setCode(KeyEvent.KEYCODE_HOME);
                setId(R.id.home);
            } else if (ClickAction.equals(AokpTarget.ACTION_BACK)) {
                setCode(KeyEvent.KEYCODE_BACK);
                setId(R.id.back);
            } else if (ClickAction.equals(AokpTarget.ACTION_MENU)) {
                setCode(KeyEvent.KEYCODE_MENU);
                setId(R.id.menu);
            } else if (ClickAction.equals(AokpTarget.ACTION_POWER)) {
                setCode(KeyEvent.KEYCODE_POWER);
            } else if (ClickAction.equals(AokpTarget.ACTION_SEARCH)) {
                setCode(KeyEvent.KEYCODE_SEARCH);
            } else if (ClickAction.equals(AokpTarget.ACTION_RECENTS)) {
                setId(R.id.recent_apps);
                setOnClickListener(mClickListener);
                setOnTouchListener(mRecentsPreloadOnTouchListener);
            } else {
                setOnClickListener(mClickListener);
            }
            setSupportsLongPress(false);
            if (Longpress != null)
                if ((!Longpress.equals(AokpTarget.ACTION_NULL)) || (getCode() != 0)) {
                    // I want to allow long presses for defined actions, or if
                    // primary action is a 'key' and long press isn't defined
                    // otherwise
                    setSupportsLongPress(true);
                    setOnLongClickListener(mLongPressListener);
                }
        }
    }

    /*
     * The default implementation preloads the first task and then sends an
     * intent to preload the rest of them; let's just preload the first task on
     * touch down and get out. It also cancels the first task preload if
     * ACTION_UP and the button isn't pressed, but there is a rare case where
     * the user spams the recents button, and this could result in unwanted
     * behavior
     */
    protected View.OnTouchListener mRecentsPreloadOnTouchListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            switch(event.getAction() & MotionEvent.ACTION_MASK) {
                case MotionEvent.ACTION_DOWN:
                    RecentTasksLoader.getInstance(mContext).preloadFirstTask();
                    break;

            }
            return false;
        }
    };

    private OnClickListener mClickListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            mAokpTarget.launchAction(mClickAction);
        }
    };

    private OnLongClickListener mLongPressListener = new OnLongClickListener() {
        @Override
        public boolean onLongClick(View v) {
            return mAokpTarget.launchAction(mLongpress);
        }
    };
}
