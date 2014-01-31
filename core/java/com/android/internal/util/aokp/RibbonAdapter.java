/*
 * Copyright (C) 2013 The Android Open Kang Project
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

package com.android.internal.util.aokp;

import java.io.File;
import java.util.ArrayList;

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuffXfermode;
import android.graphics.PorterDuff.Mode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.UserHandle;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.MarginLayoutParams;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.LinearLayout.LayoutParams;

import com.android.internal.R;

public class RibbonAdapter extends BaseAdapter implements OnItemClickListener, OnItemLongClickListener {

    private static final String TAG = "Ribbon Adapter";
    private Context mContext;
    private ArrayList<RibbonItem> mItems = new ArrayList<RibbonItem>();
    private int mSize;
    private boolean mIsVertical = true;

    private Intent u;
    private Intent b;
    private Intent a;

    public static class RibbonItem {
        public String mShortAction;
        public String mLongAction;
        public String mIcon;

        public RibbonItem(String shortAction, String longAction, String icon) {
            mShortAction = shortAction;
            mLongAction = longAction;
            if (!TextUtils.isEmpty(icon)) {
                mIcon = icon;
            } else {
                mIcon = "";
            }
        }

        public RibbonItem(String string) {
            String[] split = string.split("\\!");
            mShortAction = split[0];
            mLongAction = split[1];
            if (split.length > 2 && !TextUtils.isEmpty(split[2]))
                mIcon = split[2];
        }

        public String getString() {
            String[] array = new String[3];
            array[0] = mShortAction;
            array[1] = mLongAction;
            array[2] = mIcon;
            return TextUtils.join("!", array);
        }
    }

    static class RibbonViewHolder {
        TextView mTitle;
        ImageView mIcon;
    }

    public RibbonAdapter(Context context, ArrayList<RibbonItem> items) {
        mContext = context;
        mItems = items;
        u = new Intent();
        u.setAction("com.android.lockscreen.ACTION_UNLOCK_RECEIVER");
        b = new Intent();
        b.setAction("com.android.systemui.ACTION_HIDE_RIBBON");
        a = new Intent();
        a.setAction("com.android.systemui.ACTION_HIDE_APP_WINDOW");
    }

    @Override
    public int getCount() {
        return mItems.size();
    }

    @Override
    public Object getItem(int position) {
        return mItems.get(position);
    }

    @Override
    public long getItemId(int position) {
        return 0;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        RibbonViewHolder viewHolder;

        if (convertView == null) {
            convertView = View.inflate(mContext, R.layout.ribbon_item, null);
            viewHolder = new RibbonViewHolder();
            viewHolder.mTitle = (TextView) convertView.findViewById(R.id.ribbon_label);
            viewHolder.mIcon = (ImageView) convertView.findViewById(R.id.ribbon_icon);
            convertView.setTag(viewHolder);
        } else {
            viewHolder = (RibbonViewHolder) convertView.getTag();
        }

        RibbonItem item = (RibbonItem) getItem(position);
        if (item != null) {
            if (item.mShortAction.equals("**null**")) {
                viewHolder.mTitle.setText(NavBarHelpers.getProperSummary(mContext, item.mLongAction));
                viewHolder.mIcon.setImageDrawable(NavBarHelpers.getIconImage(mContext, item.mLongAction));
            } else {
                viewHolder.mIcon.setImageDrawable(NavBarHelpers.getIconImage(mContext, item.mShortAction));
                viewHolder.mTitle.setText(NavBarHelpers.getProperSummary(mContext, item.mShortAction));
            }
            String uri = item.mIcon;
            if (!TextUtils.isEmpty(uri) && !uri.equals("**null**") && !uri.equals("null")) {
                viewHolder.mIcon.setImageDrawable((RibbonAdapter.getCustomDrawable(mContext, uri)));
            }
        }

        if (mIsVertical) {
            AbsListView.LayoutParams params = new AbsListView.LayoutParams(AbsListView.LayoutParams.MATCH_PARENT, AbsListView.LayoutParams.WRAP_CONTENT);
            convertView.setLayoutParams(params);
            // set divider height as margin...
        } else {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT);
            params.height = mSize;
            viewHolder.mIcon.setMaxWidth(mSize);
            viewHolder.mTitle.setMaxWidth(mSize);
            convertView.setLayoutParams(params);
        }
        return convertView;

    }

    private void doAction(String action) {
        mContext.sendBroadcastAsUser(a, UserHandle.ALL);
        mContext.sendBroadcastAsUser(b, UserHandle.ALL);
        if (shouldUnlock(action)) {
            mContext.sendBroadcastAsUser(u, UserHandle.ALL);
        }
        Intent i = new Intent();
        i.setAction("com.android.systemui.aokp.LAUNCH_ACTION");
        i.putExtra("action", action);
        mContext.sendBroadcastAsUser(i, UserHandle.ALL);

    }

    private boolean shouldUnlock(String action) {
        if (action.equals(AwesomeConstants.AwesomeConstant.ACTION_TORCH.value()) ||
                action.equals(AwesomeConstants.AwesomeConstant.ACTION_NOTIFICATIONS.value()) ||
                action.equals(AwesomeConstants.AwesomeConstant.ACTION_POWER.value())) {
            return false;
        }

        return true;
    }

    public static Drawable getCustomDrawable(Context context, String action) {
        final Resources res = context.getResources();

        File f = new File(Uri.parse(action).getPath());
        Drawable front = new BitmapDrawable(res,
                getRoundedCornerBitmap(BitmapFactory.decodeFile(f.getAbsolutePath())));
        return front;
    }

    private static Bitmap getRoundedCornerBitmap(Bitmap bitmap) {
        Bitmap output = Bitmap.createBitmap(bitmap.getWidth(),
                bitmap.getHeight(), Config.ARGB_8888);
        Canvas canvas = new Canvas(output);

        final int color = 0xff424242;
        final Paint paint = new Paint();
        final Rect rect = new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());
        final RectF rectF = new RectF(rect);
        final float roundPx = 24;
        paint.setAntiAlias(true);
        canvas.drawARGB(0, 0, 0, 0);
        paint.setColor(color);
        canvas.drawRoundRect(rectF, roundPx, roundPx, paint);
        paint.setXfermode(new PorterDuffXfermode(Mode.SRC_IN));
        canvas.drawBitmap(bitmap, rect, rect, paint);
        return output;
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        doAction(((RibbonItem) getItem(position)).mShortAction);
    }

    @Override
    public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
        doAction(((RibbonItem) getItem(position)).mLongAction);
        return true;
    }

    public void setSize(int size) {
        mSize = size;
    }

    public void setOrientation(boolean vertical) {
        mIsVertical = vertical;
    }
}
