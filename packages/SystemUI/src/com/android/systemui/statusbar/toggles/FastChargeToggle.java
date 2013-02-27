
package com.android.systemui.statusbar.toggles;

import android.content.Context;
import android.content.Intent;
import android.os.FileObserver;

import com.android.systemui.R;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;

public class FastChargeToggle extends StatefulToggle {

    private String mFastChargePath;
    private FileObserver mObserver;

    @Override
    protected void init(Context c, int style) {
        super.init(c, style);
        mFastChargePath = c.getString(com.android.internal.R.string.config_fastChargePath);
        mObserver = new FileObserver(mFastChargePath) {
            @Override
            public void onEvent(int event, String file) {
                if (file == null)
                    file = "null";
                log("fast charge file modified, event:" + event + ", file: " + file);
                scheduleViewUpdate();
            }
        };
        mObserver.startWatching();
        scheduleViewUpdate();
    }

    @Override
    protected void cleanup() {
        if (mObserver != null) {
            mObserver.stopWatching();
            mObserver = null;
        }
        super.cleanup();
    }

    @Override
    protected void doEnable() {
        setFastCharge(true);
    }

    @Override
    protected void doDisable() {
        setFastCharge(false);
    }

    @Override
    protected void updateView() {
        boolean enabled = isFastChargeOn();
        setLabel(enabled
                ? R.string.quick_settings_fcharge_on_label
                : R.string.quick_settings_fcharge_off_label);
        setIcon(enabled
                ? R.drawable.ic_qs_fcharge_on
                : R.drawable.ic_qs_fcharge_off);
        super.updateView();
    }

    private void setFastCharge(final boolean on) {
        Intent fastChargeIntent = new Intent("com.aokp.romcontrol.ACTION_CHANGE_FCHARGE_STATE");
        fastChargeIntent.setPackage("com.aokp.romcontrol");
        fastChargeIntent.putExtra("newState", on);
        mContext.sendBroadcast(fastChargeIntent);
        scheduleViewUpdate();
    }

    private boolean isFastChargeOn() {
        if (mFastChargePath == null || mFastChargePath.isEmpty()) {
            return false;
        }
        File file = new File(mFastChargePath);
        if (!file.exists()) {
            return false;
        }
        String content = null;
        FileReader reader = null;
        try {
            reader = new FileReader(file);
            char[] chars = new char[(int) file.length()];
            reader.read(chars);
            content = new String(chars).trim();
        } catch (Exception e) {
            e.printStackTrace();
            content = null;
        } finally {
            try {
                reader.close();
            } catch (IOException e) {
                // ignore
            }
        }
        if (content == null)
            content = "";
        log("isFastChargeOn(): content: " + content);
        return "1".equals(content) || "Y".equalsIgnoreCase(content);
    }

}
