
package com.android.systemui.statusbar.toggles;

import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.os.Handler;
import android.os.PowerManager;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.View;
import android.view.WindowManager;

import com.android.systemui.R;

public class SleepToggle extends BaseToggle {

    @Override
    public void init(Context c, int style) {
        super.init(c, style);
        setIcon(R.drawable.ic_qs_sleep);
        setLabel(R.string.quick_settings_sleep);
    }

    @Override
    public void onClick(View v) {
        vibrateOnTouch();
        PowerManager pm = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
        pm.goToSleep(SystemClock.uptimeMillis());
    }

    @Override
    public boolean onLongClick(View v) {
        collapseStatusBar();
        dismissKeyguard();
        Intent intent = new Intent(Intent.ACTION_POWERMENU);
        mContext.sendBroadcast(intent);

        return super.onLongClick(v);
    }

    @Override
    public int getDefaultIconResId() {
        return R.drawable.ic_qs_sleep;
    }
}
