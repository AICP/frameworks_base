
package com.android.systemui.statusbar.policy.key;

import android.content.Context;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.View;

import static com.android.internal.util.aokp.AwesomeConstants.*;
import com.android.systemui.R;
import com.android.systemui.aokp.AwesomeAction;
import com.android.systemui.statusbar.policy.KeyButtonView;

public class ExtensibleKeyButtonView extends KeyButtonView {

    public String mClickAction, mLongpress;

    public ExtensibleKeyButtonView(Context context, AttributeSet attrs, String clickAction,
            String longPress) {
        super(context, attrs);
        mClickAction = clickAction;
        mLongpress = longPress;
        setActions(clickAction, longPress);
        setLongPress();
    }

    public void setActions(String clickAction, String longPress) {
        if (clickAction != null) {
            AwesomeConstant clickEnum = fromString(clickAction);
            switch (clickEnum) {
            case ACTION_HOME:
                setCode(KeyEvent.KEYCODE_HOME);
                setId(R.id.home);
                break;
            case ACTION_BACK:
                setCode(KeyEvent.KEYCODE_BACK);
                setId(R.id.back);
                break;
            case ACTION_MENU:
                setCode(KeyEvent.KEYCODE_MENU);
                setId(R.id.navbar_menu_big);
                break;
            case ACTION_POWER:
                setCode(KeyEvent.KEYCODE_POWER);
                break;
            case ACTION_SEARCH:
                setCode(KeyEvent.KEYCODE_SEARCH);
                break;
            default:
                setOnClickListener(mClickListener);
                break;
            }
        }
    }

    protected void setLongPress() {
        setSupportsLongPress(false);
        if (mLongpress != null) {
            if ((!mLongpress.equals(AwesomeConstant.ACTION_NULL)) || (getCode() != 0)) {
                // I want to allow long presses for defined actions, or if
                // primary action is a 'key' and long press isn't defined
                // otherwise
                setSupportsLongPress(true);
                setOnLongClickListener(mLongPressListener);
            }
        }
    }

    protected OnClickListener mClickListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            AwesomeAction.launchAction(mContext, mClickAction);
        }
    };

    protected OnLongClickListener mLongPressListener = new OnLongClickListener() {
        @Override
        public boolean onLongClick(View v) {
            return AwesomeAction.launchAction(mContext, mLongpress);
        }
    };
}
