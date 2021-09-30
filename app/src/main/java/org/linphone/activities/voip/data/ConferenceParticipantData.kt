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
import org.linphone.core.Conference
import org.linphone.core.Participant
import org.linphone.core.tools.Log
import org.linphone.utils.LinphoneUtils

class ConferenceParticipantData(
    conference: Conference,
    val participant: Participant
) :
    GenericContactData(participant.address) {
    val sipUri: String get() = LinphoneUtils.getDisplayableAddress(participant.address)

    private val isAdmin = MutableLiveData<Boolean>()
    private val isMeAdmin = MutableLiveData<Boolean>()

    init {
        isAdmin.value = participant.isAdmin
        isMeAdmin.value = conference.me.isAdmin
        Log.i("[Conference Participant] Participant ${participant.address.asStringUriOnly()} is ${if (participant.isAdmin) "admin" else "not admin"}")
        Log.i("[Conference Participant] Me is ${if (conference.me.isAdmin) "admin" else "not admin"} and is ${if (conference.me.isFocus) "focus" else "not focus"}")
    }
}
