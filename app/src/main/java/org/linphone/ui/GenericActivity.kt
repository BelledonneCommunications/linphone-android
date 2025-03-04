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
import android.content.Intent
import android.content.res.Configuration
import android.content.res.Resources
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.ViewGroup
import android.view.WindowManager
import androidx.annotation.DrawableRes
import androidx.annotation.MainThread
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.children
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.R
import org.linphone.compatibility.Compatibility
import org.linphone.core.tools.Log
import org.linphone.utils.ToastUtils
import org.linphone.utils.slideInToastFromTop
import org.linphone.utils.slideInToastFromTopForDuration

@MainThread
open class GenericActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "[Generic Activity]"
    }

    private lateinit var toastsArea: ViewGroup

    private var mainColor: String = "orange"

    override fun getTheme(): Resources.Theme {
        mainColor = corePreferences.themeMainColor
        val theme = super.theme
        when (mainColor) {
            "yellow" -> theme.applyStyle(R.style.Theme_LinphoneYellow, true)
            "green" -> theme.applyStyle(R.style.Theme_LinphoneGreen, true)
            "blue" -> theme.applyStyle(R.style.Theme_LinphoneBlue, true)
            "red" -> theme.applyStyle(R.style.Theme_LinphoneRed, true)
            "pink" -> theme.applyStyle(R.style.Theme_LinphonePink, true)
            "purple" -> theme.applyStyle(R.style.Theme_LinphonePurple, true)
            else -> theme.applyStyle(R.style.Theme_Linphone, true)
        }
        return theme
    }

    @SuppressLint("SourceLockedOrientationActivity")
    override fun onCreate(savedInstanceState: Bundle?) {
        enableWindowSecureMode(corePreferences.enableSecureMode)

        val nightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val darkModeEnabled = corePreferences.darkMode
        Log.i(
            "$TAG Theme selected in config file is [${if (darkModeEnabled == -1) "auto" else if (darkModeEnabled == 0) "light" else "dark"}]"
        )
        when (nightMode) {
            Configuration.UI_MODE_NIGHT_NO, Configuration.UI_MODE_NIGHT_UNDEFINED -> {
                if (darkModeEnabled == 1) {
                    Compatibility.forceDarkMode(this)
                }
            }
            Configuration.UI_MODE_NIGHT_YES -> {
                if (darkModeEnabled == 0) {
                    Compatibility.forceLightMode(this)
                }
            }
        }

        super.onCreate(savedInstanceState)
    }

    protected fun checkMainColorTheme() {
        if (mainColor != corePreferences.themeMainColor) {
            Log.i("$TAG Main color setting has changed, re-creating activity")
            recreate()
        }
    }

    fun setUpToastsArea(viewGroup: ViewGroup) {
        toastsArea = viewGroup
    }

    fun showGreenToast(
        message: String,
        @DrawableRes icon: Int,
        duration: Long = 4000,
        doNotTint: Boolean = false
    ) {
        lifecycleScope.launch {
            withContext(Dispatchers.Main) {
                val greenToast = ToastUtils.getGreenToast(
                    this@GenericActivity,
                    toastsArea,
                    message,
                    icon,
                    doNotTint
                )
                toastsArea.addView(greenToast.root)

                greenToast.root.slideInToastFromTopForDuration(
                    toastsArea,
                    lifecycleScope,
                    duration
                )
            }
        }
    }

    fun showBlueToast(
        message: String,
        @DrawableRes icon: Int,
        duration: Long = 4000,
        doNotTint: Boolean = false
    ) {
        lifecycleScope.launch {
            withContext(Dispatchers.Main) {
                val blueToast = ToastUtils.getBlueToast(
                    this@GenericActivity,
                    toastsArea,
                    message,
                    icon,
                    doNotTint
                )
                toastsArea.addView(blueToast.root)

                blueToast.root.slideInToastFromTopForDuration(
                    toastsArea,
                    lifecycleScope,
                    duration
                )
            }
        }
    }

    fun showRedToast(
        message: String,
        @DrawableRes icon: Int,
        duration: Long = 4000,
        doNotTint: Boolean = false
    ) {
        lifecycleScope.launch {
            withContext(Dispatchers.Main) {
                val redToast = ToastUtils.getRedToast(
                    this@GenericActivity,
                    toastsArea,
                    message,
                    icon,
                    doNotTint
                )
                toastsArea.addView(redToast.root)

                redToast.root.slideInToastFromTopForDuration(
                    toastsArea,
                    lifecycleScope,
                    duration
                )
            }
        }
    }

    fun showPersistentRedToast(
        message: String,
        @DrawableRes icon: Int,
        tag: String,
        doNotTint: Boolean = false
    ) {
        lifecycleScope.launch {
            withContext(Dispatchers.Main) {
                val redToast =
                    ToastUtils.getRedToast(
                        this@GenericActivity,
                        toastsArea,
                        message,
                        icon,
                        doNotTint
                    )
                redToast.root.tag = tag
                toastsArea.addView(redToast.root)

                redToast.root.slideInToastFromTop(
                    toastsArea,
                    true
                )
            }
        }
    }

    fun removePersistentRedToast(tag: String) {
        lifecycleScope.launch {
            withContext(Dispatchers.Main) {
                for (child in toastsArea.children) {
                    if (child.tag == tag) {
                        toastsArea.removeView(child)
                    }
                }
            }
        }
    }

    fun goToAndroidPermissionSettings() {
        Log.i("$TAG Going into Android settings for our app")
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts(
                "package",
                packageName, null
            )
        )
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    protected fun enableWindowSecureMode(enable: Boolean) {
        val flags: Int = window.attributes.flags
        if ((enable && flags and WindowManager.LayoutParams.FLAG_SECURE != 0) ||
            (!enable && flags and WindowManager.LayoutParams.FLAG_SECURE == 0)
        ) {
            Log.d(
                "$TAG Secure flag is already ${if (enable) "enabled" else "disabled"}, skipping..."
            )
            return
        }

        if (enable) {
            Log.i("$TAG Secure flag added to window")
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            Log.w("$TAG Secure flag cleared from window")
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }

        if (window.decorView.isAttachedToWindow) {
            Log.d("$TAG Redrawing window decorView to apply flag")
            try {
                windowManager.updateViewLayout(window.decorView, window.attributes)
            } catch (ise: IllegalStateException) {
                Log.e("$TAG Failed to update window's decorView layout: $ise")
            }
        }
    }
}
