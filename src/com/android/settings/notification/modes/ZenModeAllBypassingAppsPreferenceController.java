/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.settings.notification.modes;

import android.app.Application;
import android.app.settings.SettingsEnums;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.UserHandle;
import android.os.UserManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.core.text.BidiFormatter;
import androidx.fragment.app.Fragment;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceScreen;

import com.android.settings.R;
import com.android.settings.applications.AppInfoBase;
import com.android.settings.core.PreferenceControllerMixin;
import com.android.settings.core.SubSettingLauncher;
import com.android.settings.notification.app.AppChannelsBypassingDndSettings;
import com.android.settingslib.applications.AppUtils;
import com.android.settingslib.applications.ApplicationsState;
import com.android.settingslib.core.AbstractPreferenceController;
import com.android.settingslib.utils.ThreadUtils;
import com.android.settingslib.widget.AppPreference;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Adds a preference to the PreferenceScreen for each notification channel that can bypass DND.
 */
public class ZenModeAllBypassingAppsPreferenceController extends AbstractPreferenceController
        implements PreferenceControllerMixin {
    public static final String KEY_NO_APPS = "all_none";
    private static final String KEY = "zen_mode_bypassing_apps_list";

    @Nullable private final ZenHelperBackend mHelperBackend;
    private final UserManager mUserManager;

    @Nullable @VisibleForTesting ApplicationsState mApplicationsState;
    @VisibleForTesting PreferenceCategory mPreferenceCategory;
    @VisibleForTesting Context mPrefContext;

    private ApplicationsState.Session mAppSession;
    @Nullable private Fragment mHostFragment;

    public ZenModeAllBypassingAppsPreferenceController(Context context, @Nullable Application app,
            @Nullable Fragment host, @Nullable ZenHelperBackend helperBackend) {
        this(context, app == null ? null : ApplicationsState.getInstance(app), host, helperBackend);
    }

    private ZenModeAllBypassingAppsPreferenceController(Context context,
            @Nullable ApplicationsState appState, @Nullable Fragment host,
            @Nullable ZenHelperBackend helperBackend) {
        super(context);
        mApplicationsState = appState;
        mHostFragment = host;
        mHelperBackend = helperBackend;
        mUserManager = context.getSystemService(UserManager.class);

        if (mApplicationsState != null && host != null) {
            mAppSession = mApplicationsState.newSession(mAppSessionCallbacks, host.getLifecycle());
        }
    }

    @Override
    public void displayPreference(PreferenceScreen screen) {
        mPreferenceCategory = screen.findPreference(KEY);
        mPrefContext = screen.getContext();
        updateAppList();
        super.displayPreference(screen);
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public String getPreferenceKey() {
        return KEY;
    }

    /**
     * Call this method to trigger the app list to refresh.
     */
    public void updateAppList() {
        if (mAppSession == null) {
            return;
        }

        ApplicationsState.AppFilter filter = android.multiuser.Flags.enablePrivateSpaceFeatures()
                && android.multiuser.Flags.handleInterleavedSettingsForPrivateSpace()
                ? ApplicationsState.FILTER_ENABLED_NOT_QUIET
                : ApplicationsState.FILTER_ALL_ENABLED;
        mAppSession.rebuild(filter, ApplicationsState.ALPHA_COMPARATOR);
    }

    // Set the icon for the given preference to the entry icon from cache if available, or look
    // it up.
    private void updateIcon(Preference pref, ApplicationsState.AppEntry entry) {
        synchronized (entry) {
            final Drawable cachedIcon = AppUtils.getIconFromCache(entry);
            if (cachedIcon != null && entry.mounted) {
                pref.setIcon(cachedIcon);
            } else {
                ThreadUtils.postOnBackgroundThread(() -> {
                    final Drawable icon = AppUtils.getIcon(mPrefContext, entry);
                    if (icon != null) {
                        ThreadUtils.postOnMainThread(() -> pref.setIcon(icon));
                    }
                });
            }
        }
    }

    @VisibleForTesting
    void updateAppList(List<ApplicationsState.AppEntry> apps) {
        if (mPreferenceCategory == null || apps == null) {
            return;
        }

        boolean doAnyAppsPassCriteria = false;
        Map<Integer, Map<String, Boolean>> packagesBypassingDndByUser = new HashMap<>();
        for (UserHandle userHandle : mUserManager.getUserProfiles()) {
            packagesBypassingDndByUser.put(userHandle.getIdentifier(),
                    mHelperBackend.getPackagesBypassingDnd(userHandle.getIdentifier()));
        }
        for (ApplicationsState.AppEntry app : apps) {
            String pkg = app.info.packageName;
            final String key = getKey(pkg, app.info.uid);
            boolean doesAppBypassDnd = false;
            int userId = UserHandle.getUserId(app.info.uid);
            Map<String, Boolean> packagesBypassingDnd =
                    packagesBypassingDndByUser.getOrDefault(userId, new HashMap<>());
            if (packagesBypassingDnd.containsKey(pkg)) {
                doAnyAppsPassCriteria = true;
                doesAppBypassDnd = true;
            }
            Preference pref = mPreferenceCategory.findPreference(key);
            if (pref == null) {
                if (doesAppBypassDnd) {
                    // does not exist but should
                    pref = new AppPreference(mPrefContext);
                    pref.setKey(key);
                    pref.setOnPreferenceClickListener(preference -> {
                        Bundle args = new Bundle();
                        args.putString(AppInfoBase.ARG_PACKAGE_NAME, app.info.packageName);
                        args.putInt(AppInfoBase.ARG_PACKAGE_UID, app.info.uid);
                        new SubSettingLauncher(mContext)
                                .setDestination(AppChannelsBypassingDndSettings.class.getName())
                                .setArguments(args)
                                .setUserHandle(UserHandle.getUserHandleForUid(app.info.uid))
                                .setResultListener(mHostFragment, 0)
                                .setSourceMetricsCategory(
                                        SettingsEnums.NOTIFICATION_ZEN_MODE_OVERRIDING_APP)
                                .launch();
                        return true;
                    });
                    pref.setTitle(BidiFormatter.getInstance().unicodeWrap(app.label));
                    updateIcon(pref, app);
                    if (packagesBypassingDnd.get(pkg)) {
                        pref.setSummary(R.string.zen_mode_bypassing_apps_summary_all);
                    } else {
                        pref.setSummary(R.string.zen_mode_bypassing_apps_summary_some);
                    }
                    mPreferenceCategory.addPreference(pref);
                }
            } else if (!doesAppBypassDnd) {
                // exists but shouldn't anymore
                mPreferenceCategory.removePreference(pref);
            }
        }

        Preference pref = mPreferenceCategory.findPreference(KEY_NO_APPS);
        if (!doAnyAppsPassCriteria) {
            if (pref == null) {
                pref = new Preference(mPrefContext);
                pref.setKey(KEY_NO_APPS);
                pref.setTitle(R.string.zen_mode_bypassing_apps_none);
            }
            mPreferenceCategory.addPreference(pref);
        } else if (pref != null) {
            mPreferenceCategory.removePreference(pref);
        }
    }

    /**
     * Create a unique key to idenfity an AppPreference
     */
    static String getKey(String pkg, int uid) {
        return "all|" + pkg + "|" + uid;
    }

    private final ApplicationsState.Callbacks mAppSessionCallbacks =
            new ApplicationsState.Callbacks() {

                @Override
                public void onRunningStateChanged(boolean running) {
                }

                @Override
                public void onPackageListChanged() {
                }

                @Override
                public void onRebuildComplete(ArrayList<ApplicationsState.AppEntry> apps) {
                    updateAppList(apps);
                }

                @Override
                public void onPackageIconChanged() {
                }

                @Override
                public void onPackageSizeChanged(String packageName) {
                }

                @Override
                public void onAllSizesComputed() { }

                @Override
                public void onLauncherInfoChanged() {
                }

                @Override
                public void onLoadEntriesCompleted() {
                    updateAppList();
                }
            };
}
