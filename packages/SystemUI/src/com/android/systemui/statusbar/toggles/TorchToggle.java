
package com.android.systemui.statusbar.toggles;

import android.content.ContentResolver;
import android.content.Context;
import android.content.ComponentName;
import android.content.Intent;
import android.database.ContentObserver;
import android.os.Handler;
import android.provider.Settings;
import android.view.View;

import static com.android.internal.util.aokp.AwesomeConstants.*;
import com.android.systemui.R;
import com.android.internal.util.aokp.AwesomeAction;

public class TorchToggle extends StatefulToggle {
    TorchObserver mObserver = null;

    @Override
    public void init(Context c, int style) {
        super.init(c, style);
        mObserver = new TorchObserver(mHandler);
        mObserver.observe();
    }

    @Override
    protected void cleanup() {
        if (mObserver != null) {
            mContext.getContentResolver().unregisterContentObserver(mObserver);
            mObserver = null;
        }
        super.cleanup();
    }

    @Override
    protected void doEnable() {
        AwesomeAction.launchAction(mContext, AwesomeConstant.ACTION_TORCH.value());
    }

    @Override
    protected void doDisable() {
        AwesomeAction.launchAction(mContext, AwesomeConstant.ACTION_TORCH.value());
    }

    @Override
    public boolean onLongClick(View v) {
        vibrateOnTouch();
        Intent intentTorch = new Intent("android.intent.action.MAIN");
        intentTorch.setComponent(ComponentName
                .unflattenFromString("com.aokp.Torch/.TorchActivity"));
        intentTorch.addCategory("android.intent.category.LAUNCHER");
        intentTorch.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intentTorch.putExtra("whitescreen", true);
        startActivity(intentTorch);
        return super.onLongClick(v);
    }


    @Override
    public int getDefaultIconResId() {
        return R.drawable.ic_qs_torch_on;
    }

    @Override
    protected void updateView() {
        boolean enabled = Settings.AOKP.getBoolean(mContext.getContentResolver(),
                Settings.AOKP.TORCH_STATE, false);
        setIcon(enabled
                ? R.drawable.ic_qs_torch_on
                : R.drawable.ic_qs_torch_off);
        setLabel(enabled
                ? R.string.quick_settings_torch_on_label
                : R.string.quick_settings_torch_off_label);
        updateCurrentState(enabled ? State.ENABLED : State.DISABLED);
        super.updateView();
    }

    protected class TorchObserver extends ContentObserver {
        TorchObserver(Handler handler) {
            super(handler);
            observe();
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(Settings.AOKP.getUriFor(
                    Settings.AOKP.TORCH_STATE), false, this);
            onChange(false);
        }

        @Override
        public void onChange(boolean selfChange) {
            scheduleViewUpdate();
        }
    }

}
