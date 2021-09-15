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
package org.linphone.compatibility

import android.annotation.TargetApi
import android.app.Activity
import android.app.PictureInPictureParams
import android.content.pm.PackageManager
import org.linphone.core.tools.Log

@TargetApi(31)
class Api31Compatibility {
    companion object {
        fun enableAutoEnterPiP(activity: Activity, enable: Boolean) {
            val supportsPip = activity.packageManager
                .hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
            Log.i("[Call] Is PiP supported: $supportsPip")
            if (supportsPip) {
                val params = PictureInPictureParams.Builder().setAutoEnterEnabled(enable).build()
                activity.setPictureInPictureParams(params)
                Log.i("[Call] PiP auto enter enabled params set to $enable")
            }
        }
    }
}
