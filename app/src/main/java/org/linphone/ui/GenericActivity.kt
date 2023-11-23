/*
 * Copyright (c) 2010-2023 Belledonne Communications SARL.
 *
 * This file is part of linphone-android
 * (see https://www.linphone.org).
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.linphone.ui

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.WindowCompat
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.core.tools.Log

open class GenericActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "[Generic Activity]"
    }

    @SuppressLint("SourceLockedOrientationActivity")
    override fun onCreate(savedInstanceState: Bundle?) {
        WindowCompat.setDecorFitsSystemWindows(window, true)

        while (!coreContext.isReady()) {
            Thread.sleep(20)
        }

        val nightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val darkModeEnabled = corePreferences.darkMode
        Log.i("$TAG Theme selected in config file is [$darkModeEnabled]")
        when (nightMode) {
            Configuration.UI_MODE_NIGHT_NO, Configuration.UI_MODE_NIGHT_UNDEFINED -> {
                if (darkModeEnabled == 1) {
                    // Force dark mode
                    Log.w("$TAG Forcing night mode")
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                }
            }
            Configuration.UI_MODE_NIGHT_YES -> {
                if (darkModeEnabled == 0) {
                    // Force light mode
                    Log.w("$TAG Forcing day mode")
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                }
            }
        }

        super.onCreate(savedInstanceState)
    }
}
