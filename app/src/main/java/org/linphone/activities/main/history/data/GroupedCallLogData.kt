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
package org.linphone.activities.main.history.data

import org.linphone.activities.main.history.viewmodels.CallLogViewModel
import org.linphone.core.CallLog

class GroupedCallLogData(callLog: CallLog) {
    val callLogs = arrayListOf(callLog)

    var lastCallLog: CallLog = callLog
    var lastCallLogId: String? = callLog.callId
    var lastCallLogStartTimestamp: Long = callLog.startDate
    val lastCallLogViewModel: CallLogViewModel
        get() {
            if (::_lastCallLogViewModel.isInitialized) {
                return _lastCallLogViewModel
            }
            _lastCallLogViewModel = CallLogViewModel(lastCallLog)
            return _lastCallLogViewModel
        }

    private lateinit var _lastCallLogViewModel: CallLogViewModel

    fun destroy() {
        if (::_lastCallLogViewModel.isInitialized) {
            lastCallLogViewModel
        }
    }

    fun updateLastCallLog(callLog: CallLog) {
        lastCallLog = callLog
        lastCallLogId = callLog.callId
        lastCallLogStartTimestamp = callLog.startDate
    }
}
