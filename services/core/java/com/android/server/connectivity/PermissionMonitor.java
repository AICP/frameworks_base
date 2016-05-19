/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server.connectivity;

import static android.Manifest.permission.CHANGE_NETWORK_STATE;
import static android.Manifest.permission.CONNECTIVITY_INTERNAL;
import static android.content.pm.ApplicationInfo.FLAG_SYSTEM;
import static android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP;
import static android.content.pm.PackageManager.GET_PERMISSIONS;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.IPackageManager;
import android.content.pm.UserInfo;
import android.app.AppGlobals;
import android.net.Uri;
import android.os.INetworkManagementService;
import android.os.RemoteException;
import android.os.UserHandle;
import android.os.UserManager;
import android.text.TextUtils;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.Map;

/**
 * A utility class to inform Netd of UID permisisons.
 * Does a mass update at boot and then monitors for app install/remove.
 *
 * @hide
 */
public class PermissionMonitor {
    private static final String TAG = "PermissionMonitor";
    private static final boolean DBG = false;
    private static final Boolean SYSTEM = Boolean.TRUE;
    private static final Boolean NETWORK = Boolean.FALSE;

    private final Context mContext;
    private final IPackageManager mPackageManager;
    private final UserManager mUserManager;
    private final INetworkManagementService mNetd;
    private final BroadcastReceiver mIntentReceiver;

    // The first keys are User IDs, the second keys are App IDs. Values are true
    // for SYSTEM permission and false for NETWORK permission.
    private final Map<Integer, Map<Integer, Boolean>> mUserApps = new HashMap<Integer, Map<Integer, Boolean>>();

    public PermissionMonitor(Context context, INetworkManagementService netd) {
        mContext = context;
        mPackageManager = AppGlobals.getPackageManager();
        mUserManager = UserManager.get(context);
        mNetd = netd;
        mIntentReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                final String action = intent.getAction();
                final int user = intent.getIntExtra(Intent.EXTRA_USER_HANDLE, UserHandle.USER_NULL);
                final int appUid = intent.getIntExtra(Intent.EXTRA_UID, -1);
                final Uri appData = intent.getData();
                final String appName = appData != null ? appData.getSchemeSpecificPart() : null;
                final boolean removedForAllUsers = intent.
                        getBooleanExtra(Intent.EXTRA_REMOVED_FOR_ALL_USERS, false);

                if (Intent.ACTION_USER_ADDED.equals(action)) {
                    onUserAdded(user);
                } else if (Intent.ACTION_USER_REMOVED.equals(action)) {
                    onUserRemoved(user);
                } else if (Intent.ACTION_PACKAGE_ADDED.equals(action)) {
                    onAppAdded(appName, appUid, user);
                } else if (Intent.ACTION_PACKAGE_REMOVED.equals(action)) {
                    onAppRemoved(appUid, removedForAllUsers, user);
                }
            }
        };
    }

    // Intended to be called only once at startup, after the system is ready. Installs a broadcast
    // receiver to monitor ongoing UID changes, so this shouldn't/needn't be called again.
    public synchronized void startMonitoring() {
        log("Monitoring");

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Intent.ACTION_USER_ADDED);
        intentFilter.addAction(Intent.ACTION_USER_REMOVED);
        mContext.registerReceiverAsUser(mIntentReceiver, UserHandle.ALL, intentFilter, null, null);

        intentFilter = new IntentFilter();
        intentFilter.addAction(Intent.ACTION_PACKAGE_ADDED);
        intentFilter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        intentFilter.addDataScheme("package");
        mContext.registerReceiverAsUser(mIntentReceiver, UserHandle.ALL, intentFilter, null, null);

        List<UserInfo> users = mUserManager.getUsers(true);  // exclude dying users
        if (users != null) {
            for (UserInfo user : users) {
                mUserApps.put(user.id, getAppsNetworkPermissionForUser(user.id));
                update(mUserApps.get(user.id), true);
                log("user: " + user.id + ", Apps: " + mUserApps.get(user.id).size());
            }
        }
    }

    private boolean hasPermission(PackageInfo app, String permission) {
        if (app.requestedPermissions != null) {
            for (String p : app.requestedPermissions) {
                if (permission.equals(p)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasNetworkPermission(PackageInfo app) {
        return hasPermission(app, CHANGE_NETWORK_STATE);
    }

    private boolean hasSystemPermission(PackageInfo app) {
        int flags = app.applicationInfo != null ? app.applicationInfo.flags : 0;
        if ((flags & FLAG_SYSTEM) != 0 || (flags & FLAG_UPDATED_SYSTEM_APP) != 0) {
            return true;
        }
        return hasPermission(app, CONNECTIVITY_INTERNAL);
    }

    private int[] toIntArray(List<Integer> list) {
        int[] array = new int[list.size()];
        for (int i = 0; i < list.size(); i++) {
            array[i] = list.get(i);
        }
        return array;
    }

    private void update(Map<Integer, Boolean> apps, boolean add) {
        List<Integer> network = new ArrayList<Integer>();
        List<Integer> system = new ArrayList<Integer>();
        for (Entry<Integer, Boolean> app : apps.entrySet()) {
            List<Integer> list = app.getValue() ? system : network;
            list.add(app.getKey());
        }
        try {
            if (add) {
                mNetd.setPermission("NETWORK", toIntArray(network));
                mNetd.setPermission("SYSTEM", toIntArray(system));
            } else {
                mNetd.clearPermission(toIntArray(network));
                mNetd.clearPermission(toIntArray(system));
            }
        } catch (RemoteException e) {
            loge("Exception when updating permissions: " + e);
        }
    }

    private Map<Integer, Boolean> getAppsNetworkPermissionForUser(int user) {
        Map<Integer, Boolean> apps = new HashMap<Integer, Boolean>();

        try {
            final List<PackageInfo> packages = mPackageManager
                    .getInstalledPackages(GET_PERMISSIONS, user).getList();
            if (packages != null) {
                for (PackageInfo pkg : packages) {
                    int uid = pkg.applicationInfo != null ? pkg.applicationInfo.uid : -1;
                    if (uid < 0) {
                        continue;
                    }

                    boolean isNetwork = hasNetworkPermission(pkg);
                    boolean isSystem = hasSystemPermission(pkg);

                    if (isNetwork || isSystem) {
                        Boolean permission = apps.get(uid);
                        // If multiple packages share a UID (cf: android:sharedUserId) and ask for different
                        // permissions, don't downgrade (i.e., if it's already SYSTEM, leave it as is).
                        if (permission == null || permission == NETWORK) {
                            apps.put(uid, isSystem);
                        }
                    }
                }
            }
        } catch (RemoteException e) {
            loge("Package manager has died" + e);
        }

        return apps;
    }

    private synchronized void onUserAdded(int user) {
        if (user < 0) {
            loge("Invalid user in onUserAdded: " + user);
            return;
        }
        mUserApps.put(user, getAppsNetworkPermissionForUser(user));
        update(mUserApps.get(user), true);
    }

    private synchronized void onUserRemoved(int user) {
        if (user < 0) {
            loge("Invalid user in onUserRemoved: " + user);
            return;
        }
        update(mUserApps.get(user), false);
        mUserApps.remove(user);
    }

    private Boolean highestPermissionForUid(Boolean currentPermission, String name, int user) {
        if (currentPermission == SYSTEM) {
            return currentPermission;
        }
        try {
            final PackageInfo app = mPackageManager.getPackageInfo(name, GET_PERMISSIONS, user);
            if (app != null) {
                final boolean isNetwork = hasNetworkPermission(app);
                final boolean isSystem = hasSystemPermission(app);
                if (isNetwork || isSystem) {
                    currentPermission = isSystem;
                }
            } else {
                loge("NameNotFoundException " + name);
            }
        } catch (RemoteException e) {
            loge("Package manager has died" + e);
        }

        return currentPermission;
    }

    private synchronized void onAppAdded(String appName, int appUid, int user) {
        if (TextUtils.isEmpty(appName) || appUid < 0 || user < 0 || mUserApps.get(user) == null) {
            loge("Invalid app in onAppAdded: " + appName + " | " + appUid + " | " + user);
            return;
        }

        // If multiple packages share a UID (cf: android:sharedUserId) and ask for different
        // permissions, don't downgrade (i.e., if it's already SYSTEM, leave it as is).
        Map<Integer, Boolean> userApps = mUserApps.get(user);
        final Boolean permission = highestPermissionForUid(userApps.get(appUid), appName, user);
        if (permission != userApps.get(appUid)) {
            userApps.put(appUid, permission);

            Map<Integer, Boolean> apps = new HashMap<Integer, Boolean>();
            apps.put(appUid, permission);
            update(apps, true);
        }
    }

    private void removeNetworkPermissionForUid(int appUid, int user) {
        final Map<Integer, Boolean> apps = new HashMap<Integer, Boolean>();
        Boolean permission = null;
        try {
            String[] packages = mPackageManager.getPackagesForUid(appUid);
            if (packages != null && packages.length > 0) {
                for (String name : packages) {
                    permission = highestPermissionForUid(permission, name, user);
                    if (permission == SYSTEM) {
                        // An app with this UID still has the SYSTEM permission.
                        // Therefore, this UID must already have the SYSTEM permission.
                        // Nothing to do.
                        return;
                    }
                }
            }
        } catch (RemoteException e) {
            loge("Package manager has died" + e);
        }

        final Map<Integer, Boolean> userApps = mUserApps.get(user);
        if (permission == userApps.get(appUid)) {
            // The permissions of this UID have not changed. Nothing to do.
            return;
        } else if (permission != null) {
            userApps.put(appUid, permission);
            apps.put(appUid, permission);
            update(apps, true);
        } else {
            userApps.remove(appUid);
            apps.put(appUid, NETWORK); // doesn't matter which permission we pick here
            update(apps, false);
        }
    }

    private synchronized void onAppRemoved(int appUid, boolean removedAllUsers, int user) {
        if (appUid < 0 || user < 0) {
            loge("Invalid app in onAppRemoved: " + appUid);
            return;
        }

        if (removedAllUsers) {
            for (int userId : mUserApps.keySet()) {
                removeNetworkPermissionForUid(UserHandle.getUid(userId, appUid), userId);
            }
        } else {
            removeNetworkPermissionForUid(appUid, user);
        }
    }

    private static void log(String s) {
        if (DBG) {
            Log.d(TAG, s);
        }
    }

    private static void loge(String s) {
        Log.e(TAG, s);
    }
}
