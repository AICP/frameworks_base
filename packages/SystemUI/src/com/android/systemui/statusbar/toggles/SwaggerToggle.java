
package com.android.systemui.statusbar.toggles;

import android.content.Context;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;

import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QuickSettingsTileView;

public class SwaggerToggle extends BaseToggle implements OnTouchListener {

    boolean youAreATaco = false;
    long tacoTime = 0;

    @Override
    protected void init(Context c, int style) {
        super.init(c, style);
    }

    @Override
    public void onClick(View v) {
    }

    @Override
    public QuickSettingsTileView createTileView() {
        View root = super.createTileView();
        root.setOnTouchListener(this);
        root.setOnClickListener(null);
        root.setOnLongClickListener(null);
        return (QuickSettingsTileView) root;
    }

    @Override
    public View createTraditionalView() {
        View root = super.createTraditionalView();
        root.setOnTouchListener(this);
        root.setOnClickListener(null);
        root.setOnLongClickListener(null);
        return root;
    }

    @Override
    protected void updateView() {
        setLabel(youAreATaco
                ? R.string.quick_settings_fbgt
                : R.string.quick_settings_swagger);
        setIcon(youAreATaco
                ? R.drawable.ic_qs_fbgt_on
                : R.drawable.ic_qs_swagger);
        super.updateView();
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                if (youAreATaco) {
                    tacoTime = event.getEventTime();
                    youAreATaco = false;
                } else {
                    tacoTime = event.getEventTime();
                }
                break;
            case MotionEvent.ACTION_UP:
                if (tacoTime > 0 && (event.getEventTime() - tacoTime) > 2500) {
                    youAreATaco = true;
                }
                break;
        }
        scheduleViewUpdate();
        return true;
    }
}
