/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.settings.network.telephony;

import android.content.Context;
import android.provider.Settings;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.OnLifecycleEvent;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;
import androidx.preference.TwoStatePreference;

import com.android.settings.R;
import com.android.settings.datausage.DataUsageUtils;
import com.android.settings.flags.Flags;
import com.android.settings.network.MobileNetworkRepository;
import com.android.settings.wifi.WifiPickerTrackerHelper;
import com.android.settingslib.core.lifecycle.Lifecycle;
import com.android.settingslib.mobile.dataservice.MobileNetworkInfoEntity;
import com.android.settingslib.mobile.dataservice.SubscriptionInfoEntity;

import com.google.android.setupcompat.util.WizardManagerHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Preference controller for "Mobile data"
 */
public class MobileDataPreferenceController extends TelephonyTogglePreferenceController
        implements DefaultLifecycleObserver, MobileNetworkRepository.MobileNetworkCallback {

    private static final String DIALOG_TAG = "MobileDataDialog";

    private TwoStatePreference mPreference;
    private TelephonyManager mTelephonyManager;
    private SubscriptionManager mSubscriptionManager;
    private FragmentManager mFragmentManager;
    @VisibleForTesting
    int mDialogType;
    @VisibleForTesting
    boolean mNeedDialog;
    boolean mIsInSetupWizard;

    private WifiPickerTrackerHelper mWifiPickerTrackerHelper;
    protected MobileNetworkRepository mMobileNetworkRepository;
    private List<SubscriptionInfoEntity> mSubscriptionInfoEntityList = new ArrayList<>();
    private List<MobileNetworkInfoEntity> mMobileNetworkInfoEntityList = new ArrayList<>();
    private int mDefaultSubId = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
    SubscriptionInfoEntity mSubscriptionInfoEntity;
    MobileNetworkInfoEntity mMobileNetworkInfoEntity;

    public MobileDataPreferenceController(Context context, String key, Lifecycle lifecycle,
            int subId, boolean isInSetupWizard) {
        this(context, key);
        mSubId = subId;
        mIsInSetupWizard = isInSetupWizard;
        if (lifecycle != null) {
            lifecycle.addObserver(this);
        }
    }

    public MobileDataPreferenceController(Context context, String key) {
        super(context, key);
        mSubscriptionManager = context.getSystemService(SubscriptionManager.class);
        mMobileNetworkRepository = MobileNetworkRepository.getInstance(context);
    }

    @Override
    public int getAvailabilityStatus(int subId) {
        if ((Flags.isDualSimOnboardingEnabled() && !mIsInSetupWizard)
                || mSubscriptionManager.getActiveSubscriptionInfo(subId) == null
                || !mSubscriptionManager.isUsableSubscriptionId(subId)
                || !DataUsageUtils.hasMobileData(mContext)) {
            return CONDITIONALLY_UNAVAILABLE;
        }
        return AVAILABLE;
    }

    @Override
    public void displayPreference(PreferenceScreen screen) {
        super.displayPreference(screen);
        mPreference = screen.findPreference(getPreferenceKey());
    }

    @Override
    public void onResume(@NonNull LifecycleOwner owner) {
        mMobileNetworkRepository.addRegister(owner, this, mSubId);
        mMobileNetworkRepository.updateEntity();
    }

    @Override
    public void onPause(@NonNull LifecycleOwner owner) {
        mMobileNetworkRepository.removeRegister(this);
    }

    @Override
    public boolean handlePreferenceTreeClick(Preference preference) {
        if (TextUtils.equals(preference.getKey(), getPreferenceKey())) {
            if (mNeedDialog) {
                showDialog(mDialogType);
            }
            return true;
        }

        return false;
    }

    @Override
    public boolean setChecked(boolean isChecked) {
        mNeedDialog = isDialogNeeded();

        // If we are still provisioning we need to allow enabling mobile data first.
        // By default it is not allowed to use mobile network during provisioning so
        // we need to allow it.
        if (!WizardManagerHelper.isDeviceProvisioned(mContext)) {
            Settings.Global.putInt(mContext.getContentResolver(),
                    Settings.Global.DEVICE_PROVISIONING_MOBILE_DATA_ENABLED, isChecked ? 1 : 0);
        }

        if (!mNeedDialog) {
            // Update data directly if we don't need dialog
            Log.d(DIALOG_TAG, "setMobileDataEnabled: " + isChecked);
            MobileNetworkUtils.setMobileDataEnabled(mContext, mSubId, isChecked, false);
            if (mWifiPickerTrackerHelper != null
                    && !mWifiPickerTrackerHelper.isCarrierNetworkProvisionEnabled(mSubId)) {
                mWifiPickerTrackerHelper.setCarrierNetworkEnabled(isChecked);
            }
            return true;
        }

        return false;
    }

    @Override
    public boolean isChecked() {
        return mMobileNetworkInfoEntity == null ? false
                : mMobileNetworkInfoEntity.isMobileDataEnabled;
    }

    @Override
    public void updateState(Preference preference) {
        super.updateState(preference);
        mPreference = (TwoStatePreference) preference;
        update();
    }

    private void update() {

        if (mSubscriptionInfoEntity == null || mPreference == null) {
            return;
        }

        mPreference.setVisible(isAvailable());
        mPreference.setChecked(isChecked());
        if (mSubscriptionInfoEntity.isOpportunistic) {
            mPreference.setEnabled(false);
            mPreference.setSummary(R.string.mobile_data_settings_summary_auto_switch);
        } else {
            mPreference.setEnabled(true);
            mPreference.setSummary(R.string.mobile_data_settings_summary);
        }
        if (!mSubscriptionInfoEntity.isValidSubscription) {
            mPreference.setSelectable(false);
            mPreference.setSummary(R.string.mobile_data_settings_summary_unavailable);
        } else {
            mPreference.setSelectable(true);
        }
    }

    public void init(FragmentManager fragmentManager, int subId,
            SubscriptionInfoEntity subInfoEntity, MobileNetworkInfoEntity networkInfoEntity) {
        mFragmentManager = fragmentManager;
        mSubId = subId;
        mTelephonyManager = null;
        mTelephonyManager = getTelephonyManager();
        mSubscriptionInfoEntity = subInfoEntity;
        mMobileNetworkInfoEntity = networkInfoEntity;
    }

    private TelephonyManager getTelephonyManager() {
        if (mTelephonyManager != null) {
            return mTelephonyManager;
        }
        TelephonyManager telMgr =
                mContext.getSystemService(TelephonyManager.class);
        if (mSubId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            telMgr = telMgr.createForSubscriptionId(mSubId);
        }
        mTelephonyManager = telMgr;
        return telMgr;
    }

    public void setWifiPickerTrackerHelper(WifiPickerTrackerHelper helper) {
        mWifiPickerTrackerHelper = helper;
    }

    @VisibleForTesting
    boolean isDialogNeeded() {
        final boolean enableData = !isChecked();
        mTelephonyManager = getTelephonyManager();
        final boolean isMultiSim = (mTelephonyManager.getActiveModemCount() > 1);
        final boolean needToDisableOthers = mDefaultSubId != mSubId;

        if (enableData && isMultiSim && needToDisableOthers) {
            mDialogType = MobileDataDialogFragment.TYPE_MULTI_SIM_DIALOG;
            return true;
        }
        return false;
    }

    private void showDialog(int type) {
        final MobileDataDialogFragment dialogFragment = MobileDataDialogFragment.newInstance(type,
                mSubId);
        dialogFragment.show(mFragmentManager, DIALOG_TAG);
    }

    @VisibleForTesting
    public void setSubscriptionInfoEntity(SubscriptionInfoEntity subscriptionInfoEntity) {
        mSubscriptionInfoEntity = subscriptionInfoEntity;
    }

    @VisibleForTesting
    public void setMobileNetworkInfoEntity(MobileNetworkInfoEntity mobileNetworkInfoEntity) {
        mMobileNetworkInfoEntity = mobileNetworkInfoEntity;
    }

    @Override
    public void onActiveSubInfoChanged(List<SubscriptionInfoEntity> subInfoEntityList) {
        mSubscriptionInfoEntityList = subInfoEntityList;
        mSubscriptionInfoEntityList.forEach(entity -> {
            if (entity.getSubId() == mSubId) {
                mSubscriptionInfoEntity = entity;
                if (entity.getSubId() == SubscriptionManager.getDefaultDataSubscriptionId()) {
                    mDefaultSubId = entity.getSubId();
                }
            }
        });

        update();
        refreshSummary(mPreference);
    }


    @Override
    public void onAllMobileNetworkInfoChanged(
            List<MobileNetworkInfoEntity> mobileNetworkInfoEntityList) {
        mMobileNetworkInfoEntityList = mobileNetworkInfoEntityList;
        mMobileNetworkInfoEntityList.forEach(entity -> {
            if (Integer.parseInt(entity.subId) == mSubId) {
                mMobileNetworkInfoEntity = entity;
                update();
                refreshSummary(mPreference);
                return;
            }
        });
    }
}
