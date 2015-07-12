/*
 * Copyright (C) 2015 AICP
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
import android.database.ContentObserver;
import android.os.Handler;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckedTextView;
import android.widget.ListView;

import com.android.systemui.R;
import com.android.systemui.qs.QSTile;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class SlimPieTile extends QSTile<QSTile.BooleanState> {
    private boolean mListening;
    private SlimPieObserver mObserver;

    public SlimPieTile(Host host) {
        super(host);
        mObserver = new SlimPieObserver(mHandler);
    }

    @Override
    protected BooleanState newTileState() {
        return new BooleanState();
    }

    @Override
    protected void handleClick() {
        toggleState();
        refreshState();
    }

     @Override
    protected void handleSecondaryClick() {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setClassName("com.android.settings",
            "com.android.settings.Settings$PieControlSettingsActivity");
        mHost.startSettingsActivity(intent);
    }

    @Override
    public void handleLongClick() {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setClassName("com.android.settings",
            "com.android.settings.Settings$PieControlSettingsActivity");
        mHost.startSettingsActivity(intent);
    }

 protected void toggleState() {
         Settings.System.putInt(mContext.getContentResolver(),
                        Settings.System.PIE_CONTROLS, !PieEnabled() ? 1 : 0);
    }


    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        state.visible = true;
	if (PieEnabled()) {
        state.icon = ResourceIcon.get(R.drawable.ic_qs_pie_on);
        state.label = mContext.getString(R.string.quick_settings_slimpie_on);
	} else {
        state.icon = ResourceIcon.get(R.drawable.ic_qs_pie_off);
	state.label = mContext.getString(R.string.quick_settings_slimpie_off);
	    }
	}

    private boolean PieEnabled() {
        return Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.PIE_CONTROLS, 1) == 1;
    }

    @Override
    public void setListening(boolean listening) {
        if (mListening == listening) return;
        mListening = listening;
        if (listening) {
            mObserver.startObserving();
        } else {
            mObserver.endObserving();
        }
    }

    private class SlimPieObserver extends ContentObserver {
        public SlimPieObserver(Handler handler) {
            super(handler);
        }

        @Override
        public void onChange(boolean selfChange) {
            refreshState();
        }

        public void startObserving() {
            mContext.getContentResolver().registerContentObserver(
                    Settings.System.getUriFor(Settings.System.PIE_CONTROLS),
                    false, this);
        }

        public void endObserving() {
            mContext.getContentResolver().unregisterContentObserver(this);
        }
    }
}


