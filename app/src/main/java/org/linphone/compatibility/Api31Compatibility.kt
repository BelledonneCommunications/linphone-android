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
package org.linphone.compatibility

import android.app.Activity
import android.app.PictureInPictureParams
import android.app.UiModeManager
import android.content.Context
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.os.Environment
import android.view.View
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import org.linphone.core.tools.Log
import org.linphone.utils.AppUtils

@RequiresApi(Build.VERSION_CODES.S)
class Api31Compatibility {
    companion object {
        private const val TAG = "[API 31 Compatibility]"

        fun enableAutoEnterPiP(activity: Activity, enable: Boolean) {
            try {
                activity.setPictureInPictureParams(
                    PictureInPictureParams.Builder()
                        .setAspectRatio(AppUtils.getPipRatio(activity))
                        .setAutoEnterEnabled(enable)
                        .build()
                )
                Log.i("$TAG PiP auto enter has been [${if (enable) "enabled" else "disabled"}]")
            } catch (ise: IllegalArgumentException) {
                Log.e("$TAG Can't set PiP params: $ise")
            }
        }

        fun setBlurRenderEffect(view: View) {
            val blurEffect = RenderEffect.createBlurEffect(16F, 16F, Shader.TileMode.MIRROR)
            view.setRenderEffect(blurEffect)
        }

        fun removeBlurRenderEffect(view: View) {
            view.setRenderEffect(null)
        }

        fun forceDarkMode(context: Context) {
            val uiManager = ContextCompat.getSystemService(context, UiModeManager::class.java)
            if (uiManager == null) {
                Log.e("$TAG Failed to get UiModeManager system service!")
            }
            uiManager?.setApplicationNightMode(UiModeManager.MODE_NIGHT_YES)
        }

        fun forceLightMode(context: Context) {
            val uiManager = ContextCompat.getSystemService(context, UiModeManager::class.java)
            if (uiManager == null) {
                Log.e("$TAG Failed to get UiModeManager system service!")
            }
            uiManager?.setApplicationNightMode(UiModeManager.MODE_NIGHT_NO)
        }

        fun setAutoLightDarkMode(context: Context) {
            val uiManager = ContextCompat.getSystemService(context, UiModeManager::class.java)
            if (uiManager == null) {
                Log.e("$TAG Failed to get UiModeManager system service!")
            }
            uiManager?.setApplicationNightMode(UiModeManager.MODE_NIGHT_AUTO)
        }

        fun getRecordingsDirectory(): String {
            return Environment.DIRECTORY_RECORDINGS
        }
    }
}
