/*
 * Copyright (C) 2020 The exTHmUI Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.internal.util;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import com.android.internal.R;

public class GamingModeHelper {

    private static final String TAG = "GamingModeHelper";
    private static final String GAMING_MODE_PACKAGE = "org.exthmui.game";

    public static final int MSG_SEND_GAMING_MODE_BROADCAST = 60;

    public static final String ACTION_GAMING_MODE_ON = "exthmui.intent.action.GAMING_MODE_ON";
    public static final String ACTION_GAMING_MODE_OFF = "exthmui.intent.action.GAMING_MODE_OFF";

    private Context mContext;

    private boolean mGamingModeEnabled;
    private boolean mIsGaming;
    private boolean mDynamicAddGame;

    private String mCurrentGamePackage;

    private Intent mGamingModeOn = new Intent(ACTION_GAMING_MODE_ON);
    private Intent mGamingModeOff = new Intent(ACTION_GAMING_MODE_OFF);
    private PackageManager mPackageManager;

    private ArrayList<String> mGamingPackages = new ArrayList<>();
    private ArrayList<String> mRemovedPackages = new ArrayList<>();
    private ArrayList<String> mCheckedPackages = new ArrayList<>();

    private Handler mHandler;

    public GamingModeHelper(Context context) {
        mContext = context;
        mPackageManager = context.getPackageManager();

        mGamingModeEnabled = Settings.System.getInt(mContext.getContentResolver(), Settings.System.GAMING_MODE_ENABLED, 0) == 1;
        mDynamicAddGame = Settings.System.getInt(mContext.getContentResolver(), Settings.System.GAMING_MODE_DYNAMIC_ADD, 0) == 1;

        parseGameList();

        mGamingModeOn.addFlags(Intent.FLAG_RECEIVER_FOREGROUND | Intent.FLAG_RECEIVER_INCLUDE_BACKGROUND | Intent.FLAG_INCLUDE_STOPPED_PACKAGES);

        Settings.System.putInt(mContext.getContentResolver(), Settings.System.GAMING_MODE_ACTIVE, 0);

        GamingSettingsObserver observer = new GamingSettingsObserver(new Handler(Looper.getMainLooper()));
        observer.init();
    }

    private void sendBroadcast(Intent intent) {
        Intent clonedIntent = (Intent) intent.clone();
        Message message = makeMessage(MSG_SEND_GAMING_MODE_BROADCAST, clonedIntent);
        mHandler.sendMessage(message);
    }

    private static Message makeMessage(int what, Object obj) {
        Message message = new Message();
        message.what = what;
        message.obj = obj;
        return message;
    }

    public static boolean useGameDriver(Context context, String packageName) {
        if (Settings.System.getInt(context.getContentResolver(), Settings.System.GAMING_MODE_ENABLED, 0) == 0) {
            return false;
        }
        if (Settings.System.getInt(context.getContentResolver(), Settings.System.GAMING_MODE_USE_GAME_DRIVER, 0) == 0) {
            return false;
        }
        String gameListData = Settings.System.getString(context.getContentResolver(), Settings.System.GAMING_MODE_APP_LIST);
        if (!TextUtils.isEmpty(gameListData)) {
            List<String> gameList = Arrays.asList(gameListData.split(";"));
            return gameList.contains(packageName);
        } else {
            return false;
        }
    }

    public boolean isInGamingMode() {
        return mIsGaming;
    }

    public String getCurrentGame() {
        return mCurrentGamePackage;
    }

    public void setAmsHandler(Handler handler) {
        mHandler = handler;
    }

    public void startGamingMode(String packageName) {
        Log.d(TAG, "startGamingMode called!");
        mGamingModeOn.putExtra("package", packageName);
        mCurrentGamePackage = packageName;
        sendBroadcast(mGamingModeOn);
    }

    public void stopGamingMode() {
        Log.d(TAG, "stopGamingMode called!");
        mCurrentGamePackage = null;
        sendBroadcast(mGamingModeOff);
    }

    private void parseGameList() {
        Log.d(TAG, "parseGameList called!");
        mGamingPackages.clear();
        mRemovedPackages.clear();
        String gameListData = Settings.System.getString(mContext.getContentResolver(), Settings.System.GAMING_MODE_APP_LIST);
        if (!TextUtils.isEmpty(gameListData)) {
            String[] gameListArray = gameListData.split(";");
            mGamingPackages.addAll(Arrays.asList(gameListArray));
        }
        String removedGameListData = Settings.System.getString(mContext.getContentResolver(), Settings.System.GAMING_MODE_REMOVED_APP_LIST);
        if (!TextUtils.isEmpty(removedGameListData)) {
            String[] gameListArray = removedGameListData.split(";");
            mRemovedPackages.addAll(Arrays.asList(gameListArray));
        }
    }

    private ApplicationInfo getAppInfo(String packageName) {
        try {
            return mPackageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
            return null;
        }
    }

    private void addGameToList(String packageName) {
        if (!mGamingPackages.contains(packageName)) {
            mGamingPackages.add(packageName);
            mRemovedPackages.remove(packageName);
            saveGamesList();
        }
    }

    private void saveGamesList() {
        String gameListData = String.join(";", mGamingPackages);
        Settings.System.putString(mContext.getContentResolver(), Settings.System.GAMING_MODE_APP_LIST, gameListData);
        String removedGameListData = String.join(";", mRemovedPackages);
        Settings.System.putString(mContext.getContentResolver(), Settings.System.GAMING_MODE_REMOVED_APP_LIST, removedGameListData);
    }

    public void onPackageUninstalled(String packageName) {
        if (mGamingPackages.remove(packageName)) {
            saveGamesList();
        }
    }

    public void onTopAppChanged(String packageName) {
        Log.d(TAG, "onTopAppChanged: " + packageName);

        if (!mGamingModeEnabled) {
            if (isInGamingMode()) {
                stopGamingMode();
            }
            return;
        }

        if (isInGamingMode() && TextUtils.equals(packageName, getCurrentGame())) {
            return;
        }

        if (mGamingPackages.contains(packageName)) {
            startGamingMode(packageName);
            return;
        }
        if (mDynamicAddGame && !mRemovedPackages.contains(packageName) && !mCheckedPackages.contains(packageName)) {
            mCheckedPackages.add(packageName);
            ApplicationInfo appInfo = getAppInfo(packageName);
            if (appInfo != null && appInfo.category == ApplicationInfo.CATEGORY_GAME || (appInfo.flags & ApplicationInfo.FLAG_IS_GAME) == ApplicationInfo.FLAG_IS_GAME) {
                addGameToList(packageName);
                startGamingMode(packageName);
                return;
            }

            try {
                Resources resources = mPackageManager.getResourcesForApplication(GAMING_MODE_PACKAGE);
                int gamingRegexId = resources.getIdentifier("game_package_regex", "array", GAMING_MODE_PACKAGE);
                if (gamingRegexId != 0) {
                    String[] regexArray = resources.getStringArray(gamingRegexId);
                    for (String pattern : regexArray) {
                        if (Pattern.matches(pattern, packageName)) {
                            addGameToList(packageName);
                            startGamingMode(packageName);
                            return;
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        if (isInGamingMode()) {
            stopGamingMode();
        }
    }

    private class GamingSettingsObserver extends ContentObserver {

        private Uri mUriForGamingModeEnabled = Settings.System.getUriFor(Settings.System.GAMING_MODE_ENABLED);
        private Uri mUriForIsGaming = Settings.System.getUriFor(Settings.System.GAMING_MODE_ACTIVE);
        private Uri mUriForGamingModeList = Settings.System.getUriFor(Settings.System.GAMING_MODE_APP_LIST);
        private Uri mUriForRemovedList = Settings.System.getUriFor(Settings.System.GAMING_MODE_REMOVED_APP_LIST);
        private Uri mUriForDynamicAddGame = Settings.System.getUriFor(Settings.System.GAMING_MODE_DYNAMIC_ADD);

        public GamingSettingsObserver(Handler handler) {
            super(handler);
        }

        public void init() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(mUriForGamingModeEnabled, false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(mUriForIsGaming, false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(mUriForGamingModeList, false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(mUriForRemovedList, false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(mUriForDynamicAddGame, false, this, UserHandle.USER_ALL);
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            super.onChange(selfChange, uri);
            if (mUriForGamingModeEnabled.equals(uri)) {
                mGamingModeEnabled = Settings.System.getInt(mContext.getContentResolver(), Settings.System.GAMING_MODE_ENABLED, 0) == 1;
                if (!mGamingModeEnabled && mIsGaming) {
                    stopGamingMode();
                }
            } else if (mUriForGamingModeList.equals(uri) || mUriForRemovedList.equals(uri)) {
                parseGameList();
            } else if (mUriForDynamicAddGame.equals(uri)) {
                mDynamicAddGame = Settings.System.getInt(mContext.getContentResolver(), Settings.System.GAMING_MODE_DYNAMIC_ADD, 0) == 1;
            } else if (mUriForIsGaming.equals(uri)) {
                mIsGaming = Settings.System.getInt(mContext.getContentResolver(), Settings.System.GAMING_MODE_ACTIVE, 0) == 1;
            }
        }
    }
}
