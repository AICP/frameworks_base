package com.android.systemui.statusbar.info;

import android.content.Context;
import android.graphics.Canvas;
import android.provider.Settings;
import android.telephony.SubscriptionManager;
import android.text.BidiFormatter;
import android.text.format.Formatter;
import android.text.format.Formatter.BytesResult;
import android.util.AttributeSet;
import android.widget.TextView;

import com.android.internal.util.aicp.AicpUtils;
import com.android.settingslib.net.DataUsageController;
import com.android.systemui.Dependency;
import com.android.systemui.R;
import com.android.systemui.statusbar.phone.StatusBar;
import com.android.systemui.statusbar.policy.NetworkController;

public class DataUsageView extends TextView {

    private Context mContext;
    private NetworkController mNetworkController;
    private static boolean shouldUpdateData;
    private String formatedinfo;

    public DataUsageView(Context context, AttributeSet attrs) {
        super(context, attrs);

        mContext = context;
        mNetworkController = Dependency.get(NetworkController.class);
    }

    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if ((isDataUsageEnabled() == 0) && this.getText().toString() != "") {
            setText("");
        } else if (isDataUsageEnabled() != 0 && shouldUpdateData) {
            shouldUpdateData = false;
            updateUsageData();
            setText(formatedinfo);
        }
        updateDataUsageImage();
    }

    private void updateUsageData() {
        DataUsageController mobileDataController = new DataUsageController(mContext);
        mobileDataController.setSubscriptionId(
            SubscriptionManager.getDefaultDataSubscriptionId());
        final DataUsageController.DataUsageInfo info = isDataUsageEnabled() == 1 ?
                (AicpUtils.isWiFiConnected(mContext) ?
                        mobileDataController.getDailyWifiDataUsageInfo()
                        : mobileDataController.getDailyDataUsageInfo())
                : (AicpUtils.isWiFiConnected(mContext) ?
                        mobileDataController.getWifiDataUsageInfo()
                        : mobileDataController.getDataUsageInfo());

        formatedinfo = formatDataUsage(info.usageLevel);
    }

    public int isDataUsageEnabled() {
        return Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.QS_DATAUSAGE, 0);
    }

    public static void updateUsage() {
        shouldUpdateData = true;
    }

    private void updateDataUsageImage() {
        StatusBar statusBar = Dependency.get(StatusBar.class);
        statusBar.updateDataUsageImage();
    }

    private String formatDataUsage(long byteValue) {
        final BytesResult res = Formatter.formatBytes(mContext.getResources(), byteValue,
                Formatter.FLAG_IEC_UNITS);
        return BidiFormatter.getInstance().unicodeWrap(res.value + res.units);
    }
}
