
package com.android.systemui.statusbar.policy.key;

import android.content.Context;
import android.content.Intent;
import android.os.UserHandle;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import com.android.systemui.R;
import com.android.systemui.aokp.AwesomeAction;
import com.android.systemui.recent.RecentTasksLoader;
import com.android.systemui.recent.RecentsActivity;

public class RecentsKeyButtonView extends ExtensibleKeyButtonView {

    private boolean mRecentsLocked = false;

    public RecentsKeyButtonView(Context context, AttributeSet attrs, String clickAction,
            String longPress) {
        super(context, attrs, clickAction, longPress);
        setActions(clickAction, longPress);
        setLongPress();
    }

    @Override
    public void setActions(String clickAction, String longPress) {
        setId(R.id.recent_apps);
        setOnClickListener(mClickListener);
        setOnTouchListener(mRecentsPreloadOnTouchListener);
    }

    protected OnClickListener mClickListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            if (mRecentsLocked)
                return;

            AwesomeAction.launchAction(mContext, mClickAction);
            mRecentsLocked = true;
            postDelayed(mUnlockRecents, 100); // just to prevent spamming, it
                                              // looks ugly
        }
    };

    private Runnable mUnlockRecents = new Runnable() {
        @Override
        public void run() {
            mRecentsLocked = false;
        }
    };

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
}
