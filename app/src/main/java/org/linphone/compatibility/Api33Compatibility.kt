/*
 * Copyright (c) 2010-2022 Belledonne Communications SARL.
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
import android.content.Context
import androidx.fragment.app.Fragment

@TargetApi(33)
class Api33Compatibility {
    companion object {
        fun requestPostNotificationsPermission(fragment: Fragment, code: Int) {
            fragment.requestPermissions(
                arrayOf(
                    "android.permission.POST_NOTIFICATIONS"
                ),
                code
            )
        }

        fun hasPostNotificationsPermission(context: Context): Boolean {
            return Compatibility.hasPermission(context, "android.permission.POST_NOTIFICATIONS")
        }
    }
}
