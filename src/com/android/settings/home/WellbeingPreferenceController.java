/*
 * Copyright (C) 2023-2024 The risingOS Android Project
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
package com.android.settings.home;

import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;

import androidx.preference.Preference;

import com.android.settings.core.BasePreferenceController;

public class WellbeingPreferenceController extends BasePreferenceController {

    private Context mContext;

    public WellbeingPreferenceController(Context context, String preferenceKey) {
        super(context, preferenceKey);
        mContext = context;
    }

    @Override
    public int getAvailabilityStatus() {
        return com.android.internal.util.derp.derpUtils.isPackageInstalled(mContext, 
            "com.google.android.apps.wellbeing") ? AVAILABLE : UNSUPPORTED_ON_DEVICE;
    }

    @Override
    public boolean handlePreferenceTreeClick(Preference preference) {
        if (preference == null || 
                !TextUtils.equals(preference.getKey(), getPreferenceKey())) {
            return false;
        }
        Intent intent = new Intent();
        intent.setClassName("com.google.android.apps.wellbeing", 
                            "com.google.android.apps.wellbeing.settings.TopLevelSettingsActivity");
        try {
            if (intent.resolveActivity(mContext.getPackageManager()) != null) {
                mContext.startActivity(intent);
            } else {
                return false;
            }
        } catch (Exception e) {
            return false;
        }
        return true;
    }
}
