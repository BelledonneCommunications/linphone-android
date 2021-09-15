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
package org.linphone.activities.voip.data

import androidx.lifecycle.MutableLiveData
import org.linphone.contact.GenericContactData
import org.linphone.core.*
import org.linphone.core.tools.Log
import org.linphone.utils.LinphoneUtils

class ConferenceParticipantData(
    val conference: Conference,
    val participant: Participant
) :
    GenericContactData(participant.address) {
    val sipUri: String get() = LinphoneUtils.getDisplayableAddress(participant.address)

    val isAdmin = MutableLiveData<Boolean>()
    val isMeAdmin = MutableLiveData<Boolean>()

    init {
        isAdmin.value = participant.isAdmin
        isMeAdmin.value = conference.me.isAdmin
        Log.i("[Conference Participant] Participant ${participant.address.asStringUriOnly()} is ${if (participant.isAdmin) "admin" else "not admin"}")
    }

    fun setAdmin() {
        if (conference.me.isAdmin) {
            Log.i("[Conference Participant] Participant ${participant.address.asStringUriOnly()} will be set as admin")
            conference.setParticipantAdminStatus(participant, true)
        } else {
            Log.e("[Conference Participant] You aren't admin, you can't change participants admin rights")
        }
    }

    fun unsetAdmin() {
        if (conference.me.isAdmin) {
            Log.i("[Conference Participant] Participant ${participant.address.asStringUriOnly()} will be unset as admin")
            conference.setParticipantAdminStatus(participant, false)
        } else {
            Log.e("[Conference Participant] You aren't admin, you can't change participants admin rights")
        }
    }

    fun removeParticipantFromConference() {
        if (conference.me.isAdmin) {
            Log.i("[Conference Participant] Removing participant ${participant.address.asStringUriOnly()} from conference")
            conference.removeParticipant(participant)
        } else {
            Log.e("[Conference Participant] Can't remove participant ${participant.address.asStringUriOnly()} from conference, you aren't admin")
        }
    }
}
