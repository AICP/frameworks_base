package com.android.systemui.statusbar.policy;


import android.content.Context;
import android.content.Intent;
import android.os.UserHandle;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;

import com.android.systemui.R;
import com.android.systemui.aokp.AokpTarget;
import com.android.systemui.recent.RecentTasksLoader;
import com.android.systemui.recent.RecentsActivity;


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

    protected void preloadRecentTasksList() {
        Intent intent = new Intent(RecentsActivity.PRELOAD_INTENT);
        intent.setClassName("com.android.systemui",
                "com.android.systemui.recent.RecentsPreloadReceiver");
        mContext.sendBroadcastAsUser(intent, new UserHandle(UserHandle.USER_CURRENT));

        RecentTasksLoader.getInstance(mContext).preloadFirstTask();
    }

    protected void cancelPreloadingRecentTasksList() {
        Intent intent = new Intent(RecentsActivity.CANCEL_PRELOAD_INTENT);
        intent.setClassName("com.android.systemui",
                "com.android.systemui.recent.RecentsPreloadReceiver");
        mContext.sendBroadcastAsUser(intent, new UserHandle(UserHandle.USER_CURRENT));

        RecentTasksLoader.getInstance(mContext).cancelPreloadingFirstTask();
    }

    protected View.OnTouchListener mRecentsPreloadOnTouchListener = new View.OnTouchListener() {
        // additional optimization when we have software system buttons - start loading the recent
        // tasks on touch down
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            int action = event.getAction() & MotionEvent.ACTION_MASK;
            if (action == MotionEvent.ACTION_DOWN) {
                preloadRecentTasksList();
            } else if (action == MotionEvent.ACTION_CANCEL) {
                cancelPreloadingRecentTasksList();
            } else if (action == MotionEvent.ACTION_UP) {
                if (!v.isPressed()) {
                    cancelPreloadingRecentTasksList();
                }

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
