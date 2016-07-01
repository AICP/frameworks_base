/*
 * Copyright (C) 2015 The Dirty Unicorns Project
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

package com.android.systemui.qs.tiles;

import android.content.Context;
import android.content.Intent;
import android.content.ComponentName;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckedTextView;
import android.widget.ListView;
import android.widget.Toast;

import com.android.systemui.R;
import com.android.systemui.SysUIToast;
import com.android.systemui.qs.QSTile;
import com.android.systemui.qs.QSTileView;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.cyanogenmod.internal.logging.CMMetricsLogger;


public class AicpExtrasTile extends QSTile<QSTile.BooleanState> {
    private boolean mListening;

    private static final Intent AICP_EXTRAS = new Intent().setComponent(new ComponentName(
            "com.droidvnteam", "com.droidvnteam.hexagonrom.MainActivity"));

    public AicpExtrasTile(Host host) {
        super(host);
    }

    @Override
    protected BooleanState newTileState() {
        return new BooleanState();
    }


    @Override
    public int getMetricsCategory() {
        return CMMetricsLogger.AICPEXTRAS;
    }


    @Override
    protected void handleClick() {
        mHost.collapsePanels();
        startAicpExtras();
        refreshState();
    }

    @Override
    protected void handleSecondaryClick() {
	    handleClick();
    }

    @Override
    public void handleLongClick() {
        // Collapse the panels, so the user can see the toast.
        mHost.collapsePanels();
        SysUIToast.makeText(mContext, mContext.getString(
                R.string.quick_aicp_extras_toast),
                Toast.LENGTH_LONG).show();
    }

    protected void startAicpExtras() {
        mHost.startActivityDismissingKeyguard(AICP_EXTRAS);
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        state.visible = true;
        state.icon = ResourceIcon.get(R.drawable.ic_qs_hexagon);
        state.label = mContext.getString(R.string.quick_aicp_extras_label);
    }

    @Override
    public void setListening(boolean listening) {
        if (mListening == listening) return;
        mListening = listening;
    }
}
