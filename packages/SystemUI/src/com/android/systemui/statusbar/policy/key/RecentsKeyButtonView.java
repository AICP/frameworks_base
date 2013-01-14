
package com.android.systemui.statusbar.policy.key;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

import com.android.systemui.R;
import com.android.systemui.aokp.AwesomeAction;
import com.android.systemui.recent.RecentTasksLoader;

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
        setOnTouchListener(RecentTasksLoader.getInstance(mContext));
    }

    protected OnClickListener mClickListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            if (mRecentsLocked)
                return;

            AwesomeAction.getInstance(mContext).launchAction(mClickAction);
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
}
