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
package org.linphone.activities.call.data

import androidx.lifecycle.MutableLiveData
import org.linphone.contact.GenericContactData
import org.linphone.core.Conference
import org.linphone.core.Participant
import org.linphone.core.tools.Log

class ConferenceParticipantData(
    private val conference: Conference,
    val participant: Participant
) :
    GenericContactData(participant.address) {
    private val isAdmin = MutableLiveData<Boolean>()
    val isMeAdmin = MutableLiveData<Boolean>()

    init {
        isAdmin.value = participant.isAdmin
        isMeAdmin.value = conference.me.isAdmin
        Log.i("[Conference Participant VM] Participant ${participant.address.asStringUriOnly()} is ${if (participant.isAdmin) "admin" else "not admin"}")
        Log.i("[Conference Participant VM] Me is ${if (conference.me.isAdmin) "admin" else "not admin"} and is ${if (conference.me.isFocus) "focus" else "not focus"}")
    }

    fun removeFromConference() {
        Log.i("[Conference Participant VM] Removing participant ${participant.address.asStringUriOnly()} from conference $conference")
        conference.removeParticipant(participant)
    }
}
