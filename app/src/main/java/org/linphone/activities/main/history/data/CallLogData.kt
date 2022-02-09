/*
 * Copyright (c) 2010-2021 Belledonne Communications SARL.
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

import java.text.SimpleDateFormat
import java.util.*
import org.linphone.R
import org.linphone.contact.GenericContactData
import org.linphone.core.Call
import org.linphone.core.CallLog
import org.linphone.utils.TimestampUtils

class CallLogData(callLog: CallLog) : GenericContactData(callLog.remoteAddress) {
    val statusIconResource: Int by lazy {
        if (callLog.dir == Call.Dir.Incoming) {
            if (callLog.status == Call.Status.Missed) {
                R.drawable.call_status_missed
            } else {
                R.drawable.call_status_incoming
            }
        } else {
            R.drawable.call_status_outgoing
        }
    }

    val iconContentDescription: Int by lazy {
        if (callLog.dir == Call.Dir.Incoming) {
            if (callLog.status == Call.Status.Missed) {
                R.string.content_description_missed_call
            } else {
                R.string.content_description_incoming_call
            }
        } else {
            R.string.content_description_outgoing_call
        }
    }

    val directionIconResource: Int by lazy {
        if (callLog.dir == Call.Dir.Incoming) {
            if (callLog.status == Call.Status.Missed) {
                R.drawable.call_missed
            } else {
                R.drawable.call_incoming
            }
        } else {
            R.drawable.call_outgoing
        }
    }

    val duration: String by lazy {
        val dateFormat = SimpleDateFormat(if (callLog.duration >= 3600) "HH:mm:ss" else "mm:ss", Locale.getDefault())
        val cal = Calendar.getInstance()
        cal[0, 0, 0, 0, 0] = callLog.duration
        dateFormat.format(cal.time)
    }

    val date: String by lazy {
        TimestampUtils.toString(callLog.startDate, shortDate = false, hideYear = false)
    }
}
