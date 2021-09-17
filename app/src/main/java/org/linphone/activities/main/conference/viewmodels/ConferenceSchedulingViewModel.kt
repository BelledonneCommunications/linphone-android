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
package org.linphone.activities.main.conference.viewmodels

import androidx.lifecycle.MutableLiveData
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.contact.ContactsSelectionViewModel
import org.linphone.core.Address
import org.linphone.core.tools.Log

class ConferenceSchedulingViewModel : ContactsSelectionViewModel() {
    val subject = MutableLiveData<String>()

    val isEncrypted = MutableLiveData<Boolean>()

    val sendInviteViaChat = MutableLiveData<Boolean>()
    val sendInviteViaEmail = MutableLiveData<Boolean>()

    init {
        subject.value = ""
        isEncrypted.value = false
        sendInviteViaChat.value = true
        sendInviteViaEmail.value = false
    }

    override fun onCleared() {
        super.onCleared()
    }

    fun updateEncryption(enable: Boolean) {
        isEncrypted.value = enable
    }

    fun createConference() {
        val conferenceParams = coreContext.core.createConferenceParams()
        val conference = coreContext.core.createConferenceWithParams(conferenceParams)
        if (conference == null) {
            Log.e("[Conference Creation] Couldn't create conference from params!")
            return
        }

        val participantsCount = selectedAddresses.value.orEmpty().size
        if (participantsCount == 0) {
            Log.e("[Conference Creation] Couldn't create conference without any participant!")
            return
        }

        val participants = arrayOfNulls<Address>(participantsCount)
        val callParams = coreContext.core.createCallParams(null)
        selectedAddresses.value?.toArray(participants)
        conference.inviteParticipants(participants, callParams)
    }
}
