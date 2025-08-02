/*
 * Copyright (c) 2010-2024 Belledonne Communications SARL.
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
package com.naminfo.telecom.auto

import android.content.pm.ApplicationInfo
import androidx.car.app.CarAppService
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator
import com.naminfo.R
import org.linphone.core.tools.Log

class AndroidAutoService : CarAppService() {
    companion object {
        private const val TAG = "[Android Auto Service]"
    }

    override fun createHostValidator(): HostValidator {
        val host = hostInfo
        Log.i("$TAG Host is [${host?.packageName}] with UID [${host?.uid}]")

        val validator = HostValidator.Builder(applicationContext)
            .addAllowedHosts(R.array.hosts_allowlist_sample_copy) // androidx.car.app.R.array.hosts_allowlist_sample
            .build()
        if (host != null) {
            val allowed = validator.isValidHost(host)
            Log.i("$TAG Host is [${if (allowed) "allowed" else "not allowed"}] in our validator")
        } else {
            Log.w("$TAG Host is null!")
        }

        return if ((applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            Log.w("$TAG App is in debug mode, allowing all hosts")
            HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
        } else {
            validator
        }
    }

    override fun onCreateSession(): Session {
        Log.i("$TAG Creating Session object")
        return AndroidAutoSession()
    }
}
