/*
 * Copyright (C) 2023-2024 The Nameless-AOSP Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.derpfest.settings.display.refreshrate;

import android.os.Bundle;

import com.android.internal.util.derpfest.DisplayRefreshRateHelper;

import com.android.settings.R;

import java.util.ArrayList;
import java.util.List;

import org.derpfest.display.RefreshRateManager;

import org.derpfest.settings.fragment.PerAppListConfigFragment;

public class PerAppRefreshRateFragment extends PerAppListConfigFragment {

    private DisplayRefreshRateHelper mHelper;
    private RefreshRateManager mRefreshRateManager;

    private List<String> mEntries;
    private List<Integer> mValues;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mHelper = DisplayRefreshRateHelper.getInstance(getActivity());
        mRefreshRateManager = getActivity().getSystemService(RefreshRateManager.class);

        mEntries = new ArrayList<>();
        mEntries.add(getString(R.string.per_app_refresh_rate_default));
        mValues = new ArrayList<>();
        mValues.add(-1);

        for (int refreshRate : mHelper.getSupportedRefreshRateList()) {
            mEntries.add(String.valueOf(refreshRate) + " Hz");
            mValues.add(refreshRate);
        }
    }

    @Override
    protected int getPreferenceScreenResId() {
        return R.xml.per_app_refresh_rate_config;
    }

    @Override
    protected int getTopInfoResId() {
        return R.string.per_app_refresh_rate_summary;
    }

    @Override
    protected List<String> getEntries() {
        return mEntries;
    }

    @Override
    protected List<Integer> getValues() {
        return mValues;
    }

    @Override
    protected int getCurrentValue(String packageName, int uid) {
        return mRefreshRateManager.getRefreshRateForPackage(packageName);
    }

    @Override
    protected void onValueChanged(String packageName, int uid, int value) {
        if (value > 0) {
            mRefreshRateManager.setRefreshRateForPackage(packageName, value);
        } else {
            mRefreshRateManager.unsetRefreshRateForPackage(packageName);
        }
    }
}
