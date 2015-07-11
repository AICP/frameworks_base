package com.android.systemui.statusbar.policy;

import java.text.DecimalFormat;
import java.util.Observable;
import java.util.Observer;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.PorterDuff;
import android.graphics.PorterDuff.Mode;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.widget.TextView;

import com.android.systemui.R;
import com.google.android.collect.Sets;

/*
*
* Seeing how an Integer object in java requires at least 16 Bytes, it seemed awfully wasteful
* to only use it for a single boolean. 32-bits is plenty of room for what we need it to do.
*
*/
public class NetworkTextView extends TextView implements Observer {
    /**
     * The network meter log tag.
     */
    private static final String LOG_TAG = "NETWORK_TEXT";
    private static final int KILOBIT = 1000;
    private static final int KILOBYTE = 1024;

    private static DecimalFormat decimalFormat = new DecimalFormat("##0.#");

    static {
        decimalFormat.setMaximumIntegerDigits(3);
        decimalFormat.setMaximumFractionDigits(1);
    }

    private int mState = 0;
    private boolean mAttached;
    private int txtSizeSingle;
    private int txtSizeMulti;
    private int KB = KILOBIT;
    private int MB = KB*KB;
    private int GB = MB*KB;
    private boolean mAutoHide;
    private int mAutoHideThreshold;
    private int mNetworkTrafficColor;

    /**
     * @hide
     */
    public NetworkTextView(Context context) {
        this(context, null);
    }

    /**
     * @hide
     */
    public NetworkTextView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    /**
     * @hide
     */
    public NetworkTextView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        final Resources resources = getResources();
        txtSizeSingle = resources.getDimensionPixelSize(R.dimen.net_traffic_single_text_size);
        txtSizeMulti = resources.getDimensionPixelSize(R.dimen.net_traffic_multi_text_size);
        Handler mHandler = new Handler();
        SettingsObserver settingsObserver = new SettingsObserver(mHandler);
        settingsObserver.observe();
        updateSettings();
    }

    /**
     * @hide
     */
    @Override
    public void update(Observable observable, Object data) {
        // Check data
        if (!(data instanceof NetworkTrafficMonitor.TrafficValues)) {
            return;
        }
        NetworkTrafficMonitor.TrafficValues values = (NetworkTrafficMonitor.TrafficValues) data;
        // Check settings
        boolean upTraffic = NetworkTrafficSettings.isUpTrafficDisplayed(mState);
        boolean downTraffic = NetworkTrafficSettings.isDownTrafficDisplayed(mState);
        // Check if text should hide
        if (shouldHide(values.inSpeed/1024, values.outSpeed/1024, upTraffic, downTraffic)) {
            setText("");
            setVisibility(View.GONE);
        } else {
            long inSpeed = values.inSpeed;
            long outSpeed = values.outSpeed;
            // If bit/s convert from Bytes to bits
            String symbol;
            if (KB==KILOBYTE) {
                symbol = "B/s";
            } else {
                symbol = "b/s";
                inSpeed *= 8;
                outSpeed *= 8;
            }
            // Get information for uplink ready so the line return can be added
            String output = "";
            if (upTraffic) {
                output = formatOutput(outSpeed, symbol);
            }
            // Ensure text size is where it needs to be
            int textSize;
            if (upTraffic&&downTraffic) {
                output += "\n";
                textSize = txtSizeMulti;
            } else {
                textSize = txtSizeSingle;
            }
            // Add information for downlink if it's called for
            if (downTraffic) {
                output += formatOutput(inSpeed, symbol);
            }
            // Update view if there's anything new to show
            if (!output.contentEquals(getText())) {
                setTextSize(TypedValue.COMPLEX_UNIT_PX, (float) textSize);
                setText(output);
            }
            setVisibility(View.VISIBLE);
        }
    }

    class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            Uri uri = Settings.System.getUriFor(Settings.System.NETWORK_TRAFFIC_VECTOR_STATE);
            resolver.registerContentObserver(uri, false,
                    this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System
                    .getUriFor(Settings.System.NETWORK_TRAFFIC_VECTOR_AUTOHIDE), false,
                    this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System
                    .getUriFor(Settings.System.NETWORK_TRAFFIC_VECTOR_AUTOHIDE_THRESHOLD), false,
                    this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System
                    .getUriFor(Settings.System.NETWORK_TRAFFIC_VECTOR_COLOR), false,
                    this, UserHandle.USER_ALL);
        }

        /*
         *  @hide
         */
        @Override
        public void onChange(boolean selfChange) {
            updateSettings();
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!mAttached) {
            mAttached = true;
            IntentFilter filter = new IntentFilter();
            filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
            mContext.registerReceiver(mIntentReceiver, filter, null, getHandler());
        }
        updateSettings();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (mAttached) {
            mContext.unregisterReceiver(mIntentReceiver);
            mAttached = false;
        }
    }

    private final BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action!=null&&action.equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
                updateSettings();
            }
        }
    };

    private boolean getConnectAvailable() {
        ConnectivityManager connManager =
                (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo network = (connManager!=null) ? connManager.getActiveNetworkInfo() : null;
        return network!=null&&network.isConnected();
    }

    private void updateSettings() {
        ContentResolver resolver = mContext.getContentResolver();

        int defaultColor = Settings.System.getInt(resolver,
                Settings.System.NETWORK_TRAFFIC_VECTOR_COLOR, 0xFFFFFFFF);

        mAutoHide = Settings.System.getIntForUser(resolver, Settings.System.NETWORK_TRAFFIC_VECTOR_AUTOHIDE, 0,
                UserHandle.USER_CURRENT)==1;

        mAutoHideThreshold = Settings.System.getIntForUser(resolver, Settings.System.NETWORK_TRAFFIC_VECTOR_AUTOHIDE_THRESHOLD,
                10, UserHandle.USER_CURRENT);

        mState = Settings.System.getInt(resolver, Settings.System.NETWORK_TRAFFIC_VECTOR_STATE, 0);

        mNetworkTrafficColor = Settings.System.getInt(resolver,
                Settings.System.NETWORK_TRAFFIC_VECTOR_COLOR, -2);

        if (mNetworkTrafficColor == Integer.MIN_VALUE
                || mNetworkTrafficColor == -2) {
                mNetworkTrafficColor = defaultColor;
        }

        setTextColor(mNetworkTrafficColor);

        if (NetworkTrafficSettings.hasMask(mState, NetworkTrafficSettings.UNIT_SWITCH_MASK)) {
            KB = KILOBYTE;
        } else {
            KB = KILOBIT;
        }
        MB = KB*KB;
        GB = MB*KB;

        if (NetworkTrafficSettings.isTextEnabled(mState) && getConnectAvailable()) {
            if (mAttached) {
                NetworkTrafficMonitor.INSTANCE.addObserver(this);
            }
            post(new Runnable() {
                @Override
                public void run() {
                    setVisibility(View.VISIBLE);
                    updateTrafficDrawable();
                }
            });
        } else {
            NetworkTrafficMonitor.INSTANCE.removeObserver(this);
            post(new Runnable() {
                @Override
                public void run() {
                    setVisibility(View.GONE);
                }
            });
        }

        int updateInterval = NetworkTrafficSettings.getUpdateInterval(mState);
        NetworkTrafficMonitor.INSTANCE.setUpdateInterval(updateInterval);
    }

    private void updateTrafficDrawable() {
        // Check settings
        boolean upTraffic = NetworkTrafficSettings.isUpTrafficDisplayed(mState);
        boolean downTraffic = NetworkTrafficSettings.isDownTrafficDisplayed(mState);
        // Compute drawable
        final int intTrafficDrawable;
        Drawable drw = null;
        if (upTraffic&&downTraffic) {
            intTrafficDrawable = R.drawable.stat_sys_network_traffic_updown;
        } else if (upTraffic) {
            intTrafficDrawable = R.drawable.stat_sys_network_traffic_up;
        } else if (downTraffic) {
            intTrafficDrawable = R.drawable.stat_sys_network_traffic_down;
        } else {
            intTrafficDrawable = 0;
        }
        // Apply drawable
        if (intTrafficDrawable != 0) {
            drw = getContext().getResources().getDrawable(intTrafficDrawable);
            drw.setColorFilter(mNetworkTrafficColor, PorterDuff.Mode.SRC_ATOP);
        }
        setCompoundDrawablesWithIntrinsicBounds(null, null, drw, null);
    }

    private boolean shouldHide(long inSpeed, long outSpeed, boolean upTraffic, boolean downTraffic) {
        if (!mAutoHide) {
            return false;
        }
        if (downTraffic&&upTraffic) {
            return inSpeed<=mAutoHideThreshold&&outSpeed<=mAutoHideThreshold;
        } else if (downTraffic) {
            return inSpeed<=mAutoHideThreshold;
        } else if (upTraffic) {
            return outSpeed<=mAutoHideThreshold;
        } else {
            return false;
        }
    }

    private String formatOutput(long speed, String symbol) {
        if (speed<KB) {
            return decimalFormat.format(speed)+symbol;
        } else if (speed<MB) {
            return decimalFormat.format(speed/(float) KB)+'k'+symbol;
        } else if (speed<GB) {
            return decimalFormat.format(speed/(float) MB)+'M'+symbol;
        }
        return decimalFormat.format(speed/(float) GB)+'G'+symbol;
    }
}
