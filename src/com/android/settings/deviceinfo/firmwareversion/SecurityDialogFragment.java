/*
 * SPDX-FileCopyrightText: DerpFest AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settings.deviceinfo.firmwareversion;

import android.app.Dialog;
import android.app.settings.SettingsEnums;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.android.settings.R;
import com.android.settingslib.DeviceInfoUtils;

public class SecurityDialogFragment extends DialogFragment {

    public static final String TAG = "SecurityDialogFragment";

    public static SecurityDialogFragment newInstance() {
        return new SecurityDialogFragment();
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final View content = LayoutInflater.from(getContext())
                .inflate(R.layout.security_dialog_layout, null);

        final TextView securityPatchLevelText = content.findViewById(R.id.security_patch_level_text);
        securityPatchLevelText.setText(DeviceInfoUtils.getSecurityPatch());

        final TextView vendorPatchText = content.findViewById(R.id.vendor_patch_level_text);
        final TextView vendorPatchSummary = content.findViewById(R.id.vendor_patch_level_summary);
        if (vendorPatchSummary != null && vendorPatchText != null) {
            vendorPatchSummary.setText(Build.VENDOR_PATCH_LEVEL.replace("_", " "));
            if ("unknown".equals(vendorPatchSummary.getText().toString())) {
                vendorPatchText.setVisibility(View.GONE);
                vendorPatchSummary.setVisibility(View.GONE);
            }
        }

        return new AlertDialog.Builder(getContext())
                .setView(content)
                .setPositiveButton(android.R.string.ok, null)
                .create();
    }

    @Override
    public int getMetricsCategory() {
        return SettingsEnums.DIALOG_SETTINGS_SECURITY_PATCH;
    }
} 
