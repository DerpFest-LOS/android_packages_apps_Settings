/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.settings.vpn2;

import static android.app.AppOpsManager.OP_ACTIVATE_PLATFORM_VPN;
import static android.app.AppOpsManager.OP_ACTIVATE_VPN;

import android.app.AppOpsManager;
import android.app.Dialog;
import android.app.admin.DevicePolicyManager;
import android.app.settings.SettingsEnums;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.UserInfo;
import android.net.VpnManager;
import android.os.Bundle;
import android.os.UserHandle;
import android.os.UserManager;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.preference.Preference;
import androidx.preference.SwitchPreferenceCompat;

import com.android.internal.net.VpnConfig;
import com.android.internal.util.ArrayUtils;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.core.SubSettingLauncher;
import com.android.settings.core.instrumentation.InstrumentedDialogFragment;
import com.android.settings.overlay.FeatureFactory;
import com.android.settingslib.RestrictedLockUtils;
import com.android.settingslib.RestrictedLockUtils.EnforcedAdmin;
import com.android.settingslib.RestrictedPreference;
import com.android.settingslib.RestrictedSwitchPreference;

import java.util.List;

import lineageos.providers.LineageSettings;

public class AppManagementFragment extends SettingsPreferenceFragment
        implements Preference.OnPreferenceChangeListener, Preference.OnPreferenceClickListener,
        ConfirmLockdownFragment.ConfirmLockdownListener {

    private static final String TAG = "AppManagementFragment";

    private static final String ARG_PACKAGE_NAME = "package";

    private static final String KEY_VERSION = "version";
    private static final String KEY_GLOBAL_VPN = "global_vpn";
    private static final String KEY_ALWAYS_ON_VPN = "always_on_vpn";
    private static final String KEY_LOCKDOWN_VPN = "lockdown_vpn";
    private static final String KEY_FORGET_VPN = "forget_vpn";

    private PackageManager mPackageManager;
    private DevicePolicyManager mDevicePolicyManager;
    private VpnManager mVpnManager;
    private AdvancedVpnFeatureProvider mFeatureProvider;

    // VPN app info
    private final int mUserId = UserHandle.myUserId();
    private String mPackageName;
    private PackageInfo mPackageInfo;
    private String mVpnLabel;

    // UI preference
    private Preference mPreferenceVersion;
    private SwitchPreferenceCompat mPreferenceGlobal;
    private RestrictedSwitchPreference mPreferenceAlwaysOn;
    private RestrictedSwitchPreference mPreferenceLockdown;
    private RestrictedPreference mPreferenceForget;

    // Listener
    private final AppDialogFragment.Listener mForgetVpnDialogFragmentListener =
            new AppDialogFragment.Listener() {
        @Override
        public void onForget() {
            // Unset always-on-vpn when forgetting the VPN
            if (isVpnAlwaysOn()) {
                setAlwaysOnVpn(false, false);
            }
            // Also dismiss and go back to VPN list
            finish();
        }

        @Override
        public void onCancel() {
            // do nothing
        }
    };

    public static void show(Context context, AppPreference pref, int sourceMetricsCategory) {
        final Bundle args = new Bundle();
        args.putString(ARG_PACKAGE_NAME, pref.getPackageName());
        new SubSettingLauncher(context)
                .setDestination(AppManagementFragment.class.getName())
                .setArguments(args)
                .setTitleText(pref.getLabel())
                .setSourceMetricsCategory(sourceMetricsCategory)
                .setUserHandle(new UserHandle(pref.getUserId()))
                .launch();
    }

    @Override
    public void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        addPreferencesFromResource(R.xml.vpn_app_management);

        mPackageManager = getContext().getPackageManager();
        mDevicePolicyManager = getContext().getSystemService(DevicePolicyManager.class);
        mVpnManager = getContext().getSystemService(VpnManager.class);
        mFeatureProvider = FeatureFactory.getFeatureFactory().getAdvancedVpnFeatureProvider();

        mPreferenceVersion = findPreference(KEY_VERSION);
        mPreferenceGlobal = (SwitchPreferenceCompat) findPreference(KEY_GLOBAL_VPN);
        mPreferenceAlwaysOn = (RestrictedSwitchPreference) findPreference(KEY_ALWAYS_ON_VPN);
        mPreferenceLockdown = (RestrictedSwitchPreference) findPreference(KEY_LOCKDOWN_VPN);
        mPreferenceForget = (RestrictedPreference) findPreference(KEY_FORGET_VPN);

        if (mUserId != UserHandle.USER_SYSTEM) {
            removePreference(KEY_GLOBAL_VPN);
        }

        mPreferenceGlobal.setOnPreferenceChangeListener(this);
        mPreferenceAlwaysOn.setOnPreferenceChangeListener(this);
        mPreferenceLockdown.setOnPreferenceChangeListener(this);
        mPreferenceForget.setOnPreferenceClickListener(this);
    }

    @Override
    public void onResume() {
        super.onResume();

        boolean isInfoLoaded = loadInfo();
        if (isInfoLoaded) {
            mPreferenceVersion.setSummary(mPackageInfo.versionName);
            updateUI();
        } else {
            finish();
        }
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        String key = preference.getKey();
        switch (key) {
            case KEY_FORGET_VPN:
                return onForgetVpnClick();
            default:
                Log.w(TAG, "unknown key is clicked: " + key);
                return false;
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        switch (preference.getKey()) {
            case KEY_GLOBAL_VPN:
                return onGlobalVpnClick((Boolean) newValue);
            case KEY_ALWAYS_ON_VPN:
                return onAlwaysOnVpnClick((Boolean) newValue, mPreferenceLockdown.isChecked());
            case KEY_LOCKDOWN_VPN:
                return onAlwaysOnVpnClick(mPreferenceAlwaysOn.isChecked(), (Boolean) newValue);
            default:
                Log.w(TAG, "unknown key is clicked: " + preference.getKey());
                return false;
        }
    }

    @Override
    public int getMetricsCategory() {
        return SettingsEnums.VPN_APP_MANAGEMENT;
    }

    private boolean onForgetVpnClick() {
        updateRestrictedViews();
        if (!mPreferenceForget.isEnabled()) {
            return false;
        }
        AppDialogFragment.show(this, mForgetVpnDialogFragmentListener, mPackageInfo, mVpnLabel,
                true /* editing */, true);
        return true;
    }

    private boolean onAlwaysOnVpnClick(final boolean alwaysOnSetting, final boolean lockdown) {
        final boolean replacing = isAnotherVpnActive();
        final boolean wasLockdown = VpnUtils.isAnyLockdownActive(getActivity());
        if (ConfirmLockdownFragment.shouldShow(replacing, wasLockdown, lockdown)) {
            // Place a dialog to confirm that traffic should be locked down.
            final Bundle options = null;
            ConfirmLockdownFragment.show(
                    this, replacing, alwaysOnSetting, wasLockdown, lockdown, options);
            return false;
        }
        // No need to show the dialog. Change the setting straight away.
        return setAlwaysOnVpnByUI(alwaysOnSetting, lockdown);
    }

    private boolean onGlobalVpnClick(final boolean global) {
        return LineageSettings.Global.putString(getContext().getContentResolver(),
                LineageSettings.Global.GLOBAL_VPN_APP, global ? mPackageName : "");
    }

    @Override
    public void onConfirmLockdown(Bundle options, boolean isEnabled, boolean isLockdown) {
        setAlwaysOnVpnByUI(isEnabled, isLockdown);
    }

    private boolean setAlwaysOnVpnByUI(boolean isEnabled, boolean isLockdown) {
        updateRestrictedViews();
        if (!mPreferenceAlwaysOn.isEnabled()) {
            return false;
        }
        // Only clear legacy lockdown vpn in system user.
        if (mUserId == UserHandle.USER_SYSTEM) {
            VpnUtils.clearLockdownVpn(getContext());
        }
        final boolean success = setAlwaysOnVpn(isEnabled, isLockdown);
        if (isEnabled && (!success || !isVpnAlwaysOn())) {
            CannotConnectFragment.show(this, mVpnLabel);
        } else {
            updateUI();
        }
        return success;
    }

    private boolean setAlwaysOnVpn(boolean isEnabled, boolean isLockdown) {
        return mVpnManager.setAlwaysOnVpnPackageForUser(mUserId,
                isEnabled ? mPackageName : null, isLockdown, /* lockdownAllowlist */ null);
    }

    private void updateUI() {
        if (isAdded()) {
            final boolean alwaysOn = isVpnAlwaysOn();
            final boolean lockdown = alwaysOn
                    && VpnUtils.isAnyLockdownActive(getActivity());
            final boolean anyVpnActive = isAnyVpnActive();
            final boolean globalVpn = isGlobalVpn();

            mPreferenceGlobal.setEnabled(!anyVpnActive);
            mPreferenceGlobal.setChecked(globalVpn);
            if (globalVpn) {
                mPreferenceGlobal.setSummary(R.string.global_vpn_summary_on);
            } else if (anyVpnActive) {
                mPreferenceGlobal.setSummary(R.string.global_vpn_summary_any_vpn_active);
            } else {
                mPreferenceGlobal.setSummary(R.string.global_vpn_summary);
            }
            mPreferenceAlwaysOn.setChecked(alwaysOn);
            mPreferenceLockdown.setChecked(lockdown);
            updateRestrictedViews();
        }
    }

    @VisibleForTesting
    void updateRestrictedViews() {
        if (mFeatureProvider.isAdvancedVpnSupported(getContext())
                && !mFeatureProvider.isAdvancedVpnRemovable()
                && TextUtils.equals(mPackageName, mFeatureProvider.getAdvancedVpnPackageName())) {
            mPreferenceForget.setVisible(false);
        } else {
            mPreferenceForget.setVisible(true);
        }

        if (isAdded()) {
            mPreferenceAlwaysOn.checkRestrictionAndSetDisabled(UserManager.DISALLOW_CONFIG_VPN,
                    mUserId);
            mPreferenceLockdown.checkRestrictionAndSetDisabled(UserManager.DISALLOW_CONFIG_VPN,
                    mUserId);
            mPreferenceForget.checkRestrictionAndSetDisabled(UserManager.DISALLOW_CONFIG_VPN,
                    mUserId);

            if (mPackageName.equals(mDevicePolicyManager.getAlwaysOnVpnPackage())) {
                EnforcedAdmin admin = RestrictedLockUtils.getProfileOrDeviceOwner(
                        getContext(), UserHandle.of(mUserId));
                mPreferenceAlwaysOn.setDisabledByAdmin(admin);
                mPreferenceForget.setDisabledByAdmin(admin);
                if (mDevicePolicyManager.isAlwaysOnVpnLockdownEnabled()) {
                    mPreferenceLockdown.setDisabledByAdmin(admin);
                }
            }
            if (mVpnManager.isAlwaysOnVpnPackageSupportedForUser(mUserId, mPackageName)) {
                // setSummary doesn't override the admin message when user restriction is applied
                mPreferenceAlwaysOn.setSummary(R.string.vpn_always_on_summary);
                // setEnabled is not required here, as checkRestrictionAndSetDisabled
                // should have refreshed the enable state.
            } else {
                mPreferenceAlwaysOn.setEnabled(false);
                mPreferenceLockdown.setEnabled(false);
                mPreferenceAlwaysOn.setSummary(R.string.vpn_always_on_summary_not_supported);
            }
        }
    }

    @VisibleForTesting
    void init(String packageName, AdvancedVpnFeatureProvider featureProvider,
            RestrictedPreference preference) {
        mPackageName = packageName;
        mFeatureProvider = featureProvider;
        mPreferenceForget = preference;
    }

    private String getAlwaysOnVpnPackage() {
        return mVpnManager.getAlwaysOnVpnPackageForUser(mUserId);
    }

    private boolean isVpnAlwaysOn() {
        return mPackageName.equals(getAlwaysOnVpnPackage());
    }

    private boolean isGlobalVpn() {
        return mPackageName.equals(LineageSettings.Global.getString(
                getContext().getContentResolver(), LineageSettings.Global.GLOBAL_VPN_APP));
    }

    /**
     * @return false if the intent doesn't contain an existing package or can't retrieve activated
     * vpn info.
     */
    private boolean loadInfo() {
        final Bundle args = getArguments();
        if (args == null) {
            Log.e(TAG, "empty bundle");
            return false;
        }

        mPackageName = args.getString(ARG_PACKAGE_NAME);
        if (mPackageName == null) {
            Log.e(TAG, "empty package name");
            return false;
        }

        try {
            mPackageInfo = mPackageManager.getPackageInfo(mPackageName, /* PackageInfoFlags */ 0);
            mVpnLabel = VpnConfig.getVpnLabel(getPrefContext(), mPackageName).toString();
        } catch (NameNotFoundException nnfe) {
            Log.e(TAG, "package not found", nnfe);
            return false;
        }

        if (mPackageInfo.applicationInfo == null) {
            Log.e(TAG, "package does not include an application");
            return false;
        }
        if (!appHasVpnPermission(getContext(), mPackageInfo.applicationInfo)) {
            Log.e(TAG, "package didn't register VPN profile");
            return false;
        }

        return true;
    }

    @VisibleForTesting
    static boolean appHasVpnPermission(Context context, @NonNull ApplicationInfo application) {
        final AppOpsManager service =
                (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
        final List<AppOpsManager.PackageOps> ops = service.getOpsForPackage(application.uid,
                application.packageName, new int[]{OP_ACTIVATE_VPN, OP_ACTIVATE_PLATFORM_VPN});
        return !ArrayUtils.isEmpty(ops);
    }

    /**
     * @return {@code true} if another VPN (VpnService or legacy) is connected or set as always-on.
     */
    private boolean isAnotherVpnActive() {
        final VpnConfig config = mVpnManager.getVpnConfig(mUserId);
        return config != null && !TextUtils.equals(config.user, mPackageName);
    }

    /**
     * @return {@code true} if any VPN (VpnService or legacy) is connected or set as always-on.
     */
    private boolean isAnyVpnActive() {
        for (UserInfo userInfo : UserManager.get(getContext()).getUsers()) {
            if (mVpnManager.getVpnConfig(userInfo.id) != null) {
                return true;
            }
        }
        return false;
    }

    public static class CannotConnectFragment extends InstrumentedDialogFragment {
        private static final String TAG = "CannotConnect";
        private static final String ARG_VPN_LABEL = "label";

        @Override
        public int getMetricsCategory() {
            return SettingsEnums.DIALOG_VPN_CANNOT_CONNECT;
        }

        public static void show(AppManagementFragment parent, String vpnLabel) {
            if (parent.getFragmentManager().findFragmentByTag(TAG) == null) {
                final Bundle args = new Bundle();
                args.putString(ARG_VPN_LABEL, vpnLabel);

                final DialogFragment frag = new CannotConnectFragment();
                frag.setArguments(args);
                frag.show(parent.getFragmentManager(), TAG);
            }
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final String vpnLabel = getArguments().getString(ARG_VPN_LABEL);
            return new AlertDialog.Builder(getActivity())
                    .setTitle(getActivity().getString(R.string.vpn_cant_connect_title, vpnLabel))
                    .setMessage(getActivity().getString(R.string.vpn_cant_connect_message))
                    .setPositiveButton(R.string.okay, null)
                    .create();
        }
    }
}
