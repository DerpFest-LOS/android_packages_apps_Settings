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
package com.android.settings.language

import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.content.res.Resources
import com.android.settings.Settings.LanguageSettingsActivity
import com.android.settings.flags.Flags
import com.android.settingslib.preference.CatalystScreenTestCase
import com.google.common.truth.Truth.assertThat
import org.junit.Assert
import org.junit.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub

class LanguageSettingScreenTest: CatalystScreenTestCase() {
    override val preferenceScreenCreator = LanguageSettingScreen()

    override val flagName: String
        get() = Flags.FLAG_CATALYST_LANGUAGE_SETTING

    @Test
    fun key() {
        assertThat(preferenceScreenCreator.key).isEqualTo(LanguageSettingScreen.KEY)
    }

    override fun migration() {}
}
