/*
 * Copyright (C) 2024 Yet Another AOSP Project
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

package com.android.systemui.screenrecord;

import android.content.Context;
import android.content.res.Resources;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.systemui.res.R;

import java.util.List;

/**
 * Screen recording quality view adapter
 */
public class ScreenRecordingQualityAdapter extends ArrayAdapter<Integer> {
    private LinearLayout mSelectedHigh;
    private LinearLayout mSelectedMedium;
    private LinearLayout mSelectedLow;

    public ScreenRecordingQualityAdapter(Context context, int resource, List<Integer> objects) {
        super(context, resource, objects);
        initViews();
    }

    private void initViews() {
        mSelectedHigh = getSelected(R.string.screenrecord_lowquality_high);
        mSelectedMedium = getSelected(R.string.screenrecord_lowquality_medium);
        mSelectedLow = getSelected(R.string.screenrecord_lowquality_low);
    }

    private LinearLayout getOption(int label, int description) {
        LayoutInflater inflater = LayoutInflater.from(getContext());
        LinearLayout layout = (LinearLayout) inflater
                .inflate(R.layout.screen_record_dialog_audio_source, null, false);
        ((TextView) layout.findViewById(R.id.screen_recording_dialog_source_text))
                .setText(label);
        TextView descriptionView = layout.findViewById(
                R.id.screen_recording_dialog_source_description);
        if (description != Resources.ID_NULL) {
            descriptionView.setText(description);
        } else {
            descriptionView.setVisibility(View.GONE);
        }
        return layout;
    }

    private LinearLayout getSelected(int label) {
        LayoutInflater inflater = LayoutInflater.from(getContext());
        LinearLayout layout = (LinearLayout) inflater
                .inflate(R.layout.screen_record_dialog_quality_selected, null, false);
        ((TextView) layout.findViewById(R.id.screen_recording_dialog_source_text))
                .setText(label);
        return layout;
    }

    @Override
    public View getDropDownView(int position, View convertView, ViewGroup parent) {
        switch (getItem(position)) {
            case 0:
                return getOption(R.string.screenrecord_lowquality_high, Resources.ID_NULL);
            case 1:
                return getOption(R.string.screenrecord_lowquality_medium, Resources.ID_NULL);
            case 2:
                return getOption(R.string.screenrecord_lowquality_low, Resources.ID_NULL);
            default:
                return super.getDropDownView(position, convertView, parent);
        }
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        switch (getItem(position)) {
            case 0:
                return mSelectedHigh;
            case 1:
                return mSelectedMedium;
            case 2:
                return mSelectedLow;
            default:
                return super.getView(position, convertView, parent);
        }
    }
}
