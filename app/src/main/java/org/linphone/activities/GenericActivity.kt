/*
 * Copyright (c) 2010-2020 Belledonne Communications SARL.
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
package org.linphone.activities

import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import android.view.Surface
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.LinphoneApplication.Companion.ensureCoreExists
import org.linphone.R
import org.linphone.core.tools.Log

abstract class GenericActivity : AppCompatActivity() {
    @SuppressLint("SourceLockedOrientationActivity")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ensureCoreExists(applicationContext)

        if (corePreferences.forcePortrait) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        val nightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val darkModeEnabled = corePreferences.darkMode
        when (nightMode) {
            Configuration.UI_MODE_NIGHT_NO, Configuration.UI_MODE_NIGHT_UNDEFINED -> {
                if (darkModeEnabled == 1) {
                    // Force dark mode
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                }
            }
            Configuration.UI_MODE_NIGHT_YES -> {
                if (darkModeEnabled == 0) {
                    // Force light mode
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()

        var degrees = 270
        val orientation = windowManager.defaultDisplay.rotation
        when (orientation) {
            Surface.ROTATION_0 -> degrees = 0
            Surface.ROTATION_90 -> degrees = 270
            Surface.ROTATION_180 -> degrees = 180
            Surface.ROTATION_270 -> degrees = 90
        }
        Log.i("[Generic Activity] Device orientation is $degrees (raw value is $orientation)")
        val rotation = (360 - degrees) % 360
        coreContext.core.deviceRotation = rotation

        // Remove service notification if it has been started by device boot
        coreContext.notificationsManager.stopForegroundNotificationIfPossible()
    }

    fun isTablet(): Boolean {
        return resources.getBoolean(R.bool.isTablet)
    }
}
