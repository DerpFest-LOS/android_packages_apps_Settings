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

package com.android.settings.display;

import static android.provider.Settings.Global.DEVELOPMENT_OVERRIDE_DESKTOP_MODE_FEATURES;
import static android.window.DesktopModeFlags.ToggleOverride.fromSetting;
import static android.window.DesktopModeFlags.ToggleOverride.OVERRIDE_OFF;
import static android.window.DesktopModeFlags.ToggleOverride.OVERRIDE_ON;
import static android.window.DesktopModeFlags.ToggleOverride.OVERRIDE_UNSET;

import android.content.Context;
import android.provider.Settings;
import android.window.DesktopModeFlags.ToggleOverride;

import androidx.annotation.NonNull;
import androidx.preference.Preference;
import androidx.preference.TwoStatePreference;

import com.android.settings.R;
import com.android.settings.core.BasePreferenceController;
import com.android.wm.shell.shared.desktopmode.DesktopModeStatus;
import androidx.fragment.app.Fragment;
import com.android.settings.development.RebootConfirmationDialogFragment;
import com.android.settings.development.RebootConfirmationDialogHost;

/**
 * Preference controller to control Desktop mode features in regular settings
 */
public class DesktopModePreferenceController extends BasePreferenceController
        implements Preference.OnPreferenceChangeListener, RebootConfirmationDialogHost {

    private static final String DESKTOP_MODE_FEATURES_KEY = "desktop_mode_features";
    private Fragment mHostFragment;
    private TwoStatePreference mPreference;

    public DesktopModePreferenceController(Context context) {
        super(context, DESKTOP_MODE_FEATURES_KEY);
    }

    public void setHostFragment(Fragment fragment) {
        mHostFragment = fragment;
    }

    @Override
    public int getAvailabilityStatus() {
        return DesktopModeStatus.canShowDesktopModeDevOption(mContext) ? AVAILABLE : UNSUPPORTED_ON_DEVICE;
    }

    @Override
    public String getPreferenceKey() {
        return DESKTOP_MODE_FEATURES_KEY;
    }

    @Override
    public boolean onPreferenceChange(@NonNull Preference preference, Object newValue) {
        final boolean isEnabled = (Boolean) newValue;
        Settings.Global.putInt(mContext.getContentResolver(),
                DEVELOPMENT_OVERRIDE_DESKTOP_MODE_FEATURES,
                isEnabled ? OVERRIDE_ON.getSetting() : OVERRIDE_OFF.getSetting());
        if (mHostFragment != null) {
            RebootConfirmationDialogFragment.show(
                mHostFragment,
                R.string.reboot_dialog_override_desktop_mode,
                this
            );
        }
        return true;
    }

    @Override
    public void updateState(Preference preference) {
        mPreference = (TwoStatePreference) preference;
        // Use overridden state, if not present, then use default state
        final int overrideInt = Settings.Global.getInt(mContext.getContentResolver(),
                DEVELOPMENT_OVERRIDE_DESKTOP_MODE_FEATURES, OVERRIDE_UNSET.getSetting());
        final ToggleOverride toggleOverride = fromSetting(overrideInt,
                OVERRIDE_UNSET);
        final boolean shouldDevOptionBeEnabled = switch (toggleOverride) {
            case OVERRIDE_OFF -> false;
            case OVERRIDE_ON -> true;
            case OVERRIDE_UNSET -> DesktopModeStatus.shouldDevOptionBeEnabledByDefault();
        };
        ((TwoStatePreference) mPreference).setChecked(shouldDevOptionBeEnabled);
    }
} 
