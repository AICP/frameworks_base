package com.android.systemui.statusbar.toggles;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.view.View;
import android.view.WindowManager;

import com.android.systemui.R;

public class ScreenrecordToggle extends BaseToggle {

    private Handler mHandler = new Handler();

    @Override
    public void init(Context c, int style) {
        super.init(c, style);
        setIcon(R.drawable.ic_sysbar_record);
        setLabel(R.string.quick_settings_screenrecord);
    }

    @Override
    public void onClick(View v) {
        collapseStatusBar();
        // just enough delay for statusbar to collapse
        mHandler.postDelayed(mRunnable, 500);
    }

    private Runnable mRunnable = new Runnable() {
        public void run() {
            Intent intent = new Intent(Intent.ACTION_SCREENRECORD);
            mContext.sendBroadcast(intent);
        }
    };

    @Override
    public int getDefaultIconResId() {
        return R.drawable.ic_sysbar_camera;
    }
}
