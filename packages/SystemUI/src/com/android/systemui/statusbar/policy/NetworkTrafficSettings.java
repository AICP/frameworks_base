package com.android.systemui.statusbar.policy;

import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.net.Uri;
import android.provider.Settings;

import java.util.HashSet;
import java.util.Set;

/**
 * Handle traffic network settings.
 *
 * @author Bruce BUJON (bruce.bujon@gmail.com)
 */
public class NetworkTrafficSettings {
    /**
     * The meter enabled status mask.
     */
    public static final int METER_ENABLED_MASK = 0x00000001;
    /**
     * The text enabled status mask.
     */
    public static final int TEXT_ENABLED_MASK = 0x00000002;
    /**
     * The up-stream traffic display mask.
     */
    public static final int UP_TRAFFIC_MASK = 0x00000004;
    /**
     * The down-stream traffic display mask.
     */
    public static final int DOWN_TRAFFIC_MASK = 0x00000008;
    /**
     * The unit switch mask.
     */
    public static final int UNIT_SWITCH_MASK = 0x00000010;
    /**
     * The update interval mask.
     */
    public static final int UPDATE_INTERVAL_MASK = 0xFFFF0000;

    /**
     * Check if the meter is enabled.
     *
     * @param state The state of the network traffic monitor.
     * @return <code>true</code> if the meter is enabled, <code>false</code>.
     */
    public static boolean isMeterEnabled(int state) {
        return hasMask(state, METER_ENABLED_MASK);
    }

    /**
     * Check if the text is enabled.
     *
     * @param state The state of the network traffic monitor.
     * @return <code>true</code> if the text is enabled, <code>false</code>.
     */
    public static boolean isTextEnabled(int state) {
        return hasMask(state, TEXT_ENABLED_MASK);
    }

    /**
     * Check if the up traffic is displayed.
     *
     * @param state The state of the network traffic monitor.
     * @return <code>true</code> if the up traffic is displayed, <code>false</code>.
     */
    public static boolean isUpTrafficDisplayed(int state) {
        return hasMask(state, UP_TRAFFIC_MASK);
    }

    /**
     * Check if the down traffic is displayed.
     *
     * @param state The state of the network traffic monitor.
     * @return <code>true</code> if the down traffic is displayed, <code>false</code>.
     */
    public static boolean isDownTrafficDisplayed(int state) {
        return hasMask(state, DOWN_TRAFFIC_MASK);
    }

    /**
     * Get the update interval.
     *
     * @param state The state of the network traffic monitor.
     * @return The update interval (in ms).
     */
    public static int getUpdateInterval(int state) {
        // Get update interval
        int updateInterval = state>>>16;
        // Check bounds
        if (updateInterval<250||updateInterval>30000) {
            updateInterval = 200;
        }
        // Return update interval
        return updateInterval;
    }

    /**
     * Check a mask on a value.
     *
     * @param value The value to test.
     * @param mask  The mask to test.
     * @return <code>true</code> if the value contains the mask, <code>otherwise</code>.
     */
    public static boolean hasMask(int value, int mask) {
        return (value&mask)==mask;
    }

    /**
     * Observe settings change.
     *
     * @author Bruce BUJON (bruce.bujon@gmail.com)
     */
    public static class Observer extends ContentObserver {
        /**
         * The observer context.
         */
        private final Context mContext;
        /**
         * The observed setting URIs.
         */
        private final Set<Uri> mSettingUris;
        /**
         * The callback to call on setting change.
         */
        private final ChangeCallback mCallback;

        /**
         * Constructor.
         *
         * @param context      The observer context.
         * @param settingNames The setting names to observe.
         * @param callback     The callback to notify on setting change.
         */
        public Observer(Context context, Set<String> settingNames, ChangeCallback callback) {
            super(null);
            // Save observer context
            mContext = context;
            // Save observed setting URIs
            mSettingUris = new HashSet<Uri>(settingNames.size());
            for (String settingName : settingNames) {
                mSettingUris.add(Settings.System.getUriFor(settingName));
            }
            // Save callback
            mCallback = callback;
        }

        /**
         * Register the observer.
         */
        public void register() {
            // Get content resolver
            ContentResolver resolver = mContext.getContentResolver();
            // Register the observer for each setting URI
            for (Uri settingUri : mSettingUris) {
                resolver.registerContentObserver(settingUri, false, this);
            }
        }

        /**
         * Unregister the observer.
         */
        public void unregister() {
            // Get content resolver
            ContentResolver resolver = mContext.getContentResolver();
            // Unregister the observer
            resolver.unregisterContentObserver(this);
        }

        @Override
        public void onChange(boolean selfChange) {
            // Notify callback
            mCallback.onSettingChanged();
        }
    }

    /**
     * Callback for setting change.
     *
     * @author Bruce BUJON (bruce.bujon@gmail.com)
     */
    public interface ChangeCallback {
        public void onSettingChanged();
    }
}
