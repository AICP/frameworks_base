/*
 * Copyright (C) 2018 Sony Mobile Communications Inc.
 * All rights, including trade secret rights, reserved.
 */

package com.android.server.am;

final class Cgroups {
    private Cgroups() { }
    static native void putProc(int pid, int uid);
    static native void putThreadInRoot(int tid);
    static native void uidPrio(int uid, int shares);
}
