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
package com.naminfo.ui.main.settings.model

import androidx.annotation.WorkerThread
import java.time.ZonedDateTime
import org.linphone.core.AccountDevice
import org.linphone.core.tools.Log
import com.naminfo.utils.TimestampUtils

class AccountDeviceModel
    @WorkerThread
    constructor(
    private val accountDevice: AccountDevice,
    private val onRemove: (model: AccountDeviceModel, accountDevice: AccountDevice) -> (Unit)
) {
    companion object {
        const val TAG = "[Account Device Model]"
    }

    val name = accountDevice.name
    val timestamp = if (accountDevice.lastUpdateTimestamp == 0L) {
        Log.w("$TAG SDK failed to parse [${accountDevice.lastUpdateTimestamp}] as time_t!")
        try {
            ZonedDateTime.parse(accountDevice.lastUpdateTime).toEpochSecond()
        } catch (e: Exception) {
            Log.e("$TAG Failed to parse [${accountDevice.lastUpdateTime}] as ZonedDateTime!")
            0L
        }
    } else {
        accountDevice.lastUpdateTimestamp
    }
    val lastConnectionDate = TimestampUtils.toString(
        timestamp,
        onlyDate = true,
        shortDate = true,
        hideYear = false
    )
    val lastConnectionTime = TimestampUtils.timeToString(timestamp)
    val isMobileDevice = accountDevice.userAgent.contains("LinphoneAndroid") || accountDevice.userAgent.contains(
        "LinphoneiOS"
    )

    init {
        Log.d(
            "$TAG Device's [$name] last update timestamp is [$timestamp] ($lastConnectionDate - $lastConnectionTime)"
        )
    }

    fun removeDevice() {
        onRemove(this, accountDevice)
    }
}
