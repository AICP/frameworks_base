/*
 * Copyright (C) 2018 crDroid Android Project
 * Copyright (C) 2018-2019 AICP
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

package com.android.systemui.aicp.logo;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.provider.Settings;
import android.util.AttributeSet;

public class LogoImageViewQuickRight extends LogoImage
{

    private Context mContext;

    public LogoImageViewQuickRight(Context context) {
        this(context, null);
    }

    public LogoImageViewQuickRight(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public LogoImageViewQuickRight(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        final Resources resources = getResources();
        mContext = context;
    }

    @Override
    protected boolean isLogoHidden()
    {
        if(mLogoPosition != 3){
            return true;
        }
        return false;
    }


}
