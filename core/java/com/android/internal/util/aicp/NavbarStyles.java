/*
 * Copyright (C) 2018-2020 crDroid Android Project
 * Copyright (C) 2020      AICP
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

package com.android.internal.util.aicp;

import android.content.om.IOverlayManager;
import android.content.om.OverlayInfo;
import android.os.RemoteException;

public class NavbarStyles {

    public static final String TAG = "NavbarStyles";

    // Navbar styles
    public static final String[] NAVBAR_STYLES = {
        "com.aicp.overlay.navbar_style.stock.com.android.systemui", //0
        "com.aicp.overlay.navbar_style.asus.com.android.systemui", //1
        "com.aicp.overlay.navbar_style.oneplus.com.android.systemui", //2
        "com.aicp.overlay.navbar_style.oneui.com.android.systemui", //3
        "com.aicp.overlay.navbar_style.tecno.com.android.systemui", //4
    };

    // Unloads the navbar styles
    private static void unloadNavbarStyle(IOverlayManager om, int userId) {
        for (String style : NAVBAR_STYLES) {
            try {
                om.setEnabled(style, false, userId);
            } catch (RemoteException e) {
            }
        }
    }

    // Set navbar style
    public static void setNavbarStyle(IOverlayManager om, int userId, int navbarStyle) {
        // Always unload navbar styles
        unloadNavbarStyle(om, userId);

        if (navbarStyle == 0) return;

        try {
            om.setEnabled(NAVBAR_STYLES[navbarStyle], true, userId);
        } catch (RemoteException e) {
        }
    }
}
