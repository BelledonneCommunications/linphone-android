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
package org.linphone.ui.call.model

import androidx.annotation.WorkerThread
import androidx.lifecycle.MutableLiveData
import org.linphone.core.Call
import org.linphone.core.Conference
import org.linphone.core.tools.Log

class ConferenceModel {
    companion object {
        private const val TAG = "[Conference ViewModel]"
    }

    val subject = MutableLiveData<String>()

    private lateinit var conference: Conference

    @WorkerThread
    fun configureFromCall(call: Call) {
        val conf = call.conference ?: return
        conference = conf

        Log.i(
            "$TAG Configuring conference with subject [${conference.subject}] from call [${call.callLog.callId}]"
        )
        subject.postValue(conference.subject)
    }
}
