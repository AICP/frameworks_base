
package com.android.systemui.statusbar.toggles;

import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.view.View;

import com.android.systemui.R;

public class BluetoothToggle extends StatefulToggle {

    public void init(Context c, int style) {
        super.init(c, style);

        BluetoothAdapter bt = BluetoothAdapter.getDefaultAdapter();
        if (bt == null) {
            return;
        }
        boolean enabled = bt.isEnabled();
        setIcon(enabled ? R.drawable.ic_qs_bluetooth_on : R.drawable.ic_qs_bluetooth_off);
        setLabel(enabled ? R.string.quick_settings_bluetooth_label
                : R.string.quick_settings_bluetooth_off_label);
        updateCurrentState(enabled ? State.ENABLED : State.DISABLED);

        registerBroadcastReceiver(new BroadcastReceiver() {

            @Override
            public void onReceive(Context context, Intent intent) {
                int state = intent.getIntExtra(
                        BluetoothAdapter.EXTRA_STATE,
                        BluetoothAdapter.STATE_OFF);

                String label = null;
                int iconId = 0;
                State newState = getState();
                switch (state) {
                    case BluetoothAdapter.STATE_CONNECTED:
                        iconId = R.drawable.ic_qs_bluetooth_on;
                        label = mContext.getString(R.string.quick_settings_bluetooth_label);
                        newState = State.ENABLED;
                        break;
                    case BluetoothAdapter.STATE_ON:
                        iconId = R.drawable.ic_qs_bluetooth_not_connected;
                        label = mContext.getString(R.string.quick_settings_bluetooth_label);
                        newState = State.ENABLED;
                        break;
                    case BluetoothAdapter.STATE_CONNECTING:
                    case BluetoothAdapter.STATE_TURNING_ON:
                        iconId = R.drawable.ic_qs_bluetooth_not_connected;
                        label = mContext.getString(R.string.quick_settings_bluetooth_label);
                        newState = State.ENABLING;
                        break;
                    case BluetoothAdapter.STATE_DISCONNECTED:
                        iconId = R.drawable.ic_qs_bluetooth_not_connected;
                        label = mContext.getString(R.string.quick_settings_bluetooth_label);
                        newState = State.DISABLED;
                        break;
                    case BluetoothAdapter.STATE_DISCONNECTING:
                        iconId = R.drawable.ic_qs_bluetooth_not_connected;
                        label = mContext.getString(R.string.quick_settings_bluetooth_label);
                        newState = State.DISABLING;
                        break;
                    case BluetoothAdapter.STATE_OFF:
                        iconId = R.drawable.ic_qs_bluetooth_off;
                        label = mContext.getString(R.string.quick_settings_bluetooth_off_label);
                        newState = State.DISABLED;
                        break;
                    case BluetoothAdapter.STATE_TURNING_OFF:
                        iconId = R.drawable.ic_qs_bluetooth_off;
                        label = mContext.getString(R.string.quick_settings_bluetooth_off_label);
                        newState = State.DISABLING;
                        break;
                }
                if (label != null && iconId > 0) {
                    setInfo(label, iconId);
                    scheduleViewUpdate();
                    updateCurrentState(newState);
                }
            }
        }, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
    }

    @Override
    public boolean onLongClick(View v) {
        Intent intent = new Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS);
        startActivity(intent);
        return super.onLongClick(v);
    }

    @Override
    protected void doEnable() {
        BluetoothAdapter.getDefaultAdapter().enable();
    }

    @Override
    protected void doDisable() {
        BluetoothAdapter.getDefaultAdapter().disable();
    }

}
