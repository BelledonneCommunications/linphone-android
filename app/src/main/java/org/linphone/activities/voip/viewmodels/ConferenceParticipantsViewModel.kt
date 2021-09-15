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
package org.linphone.activities.voip.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import org.linphone.contact.ContactsSelectionViewModel
import org.linphone.core.Address
import org.linphone.core.Conference
import org.linphone.core.tools.Log

class ConferenceParticipantsViewModelFactory(private val conference: Conference) :
    ViewModelProvider.NewInstanceFactory() {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return ConferenceParticipantsViewModel(conference) as T
    }
}

class ConferenceParticipantsViewModel(val conference: Conference) : ContactsSelectionViewModel() {
    init {
        selectCurrentParticipants()
    }

    fun applyChanges() {
        // Adding new participants first, because if we remove all of them (or all of them except one)
        // It will terminate the conference first and we won't be able to add new participants after
        for (address in selectedAddresses.value.orEmpty()) {
            val participant = conference.participantList.find { participant ->
                participant.address.weakEqual(address)
            }
            if (participant == null) {
                Log.i("[Conference Participants] Participant ${address.asStringUriOnly()} will be added to group")
                conference.addParticipant(address)
            }
        }

        // Removing participants
        for (participant in conference.participantList) {
            val member = selectedAddresses.value.orEmpty().find { address ->
                participant.address.weakEqual(address)
            }
            if (member == null) {
                Log.w("[Conference Participants] Participant ${participant.address.asStringUriOnly()} will be removed from conference")
                conference.removeParticipant(participant)
            }
        }
    }

    private fun selectCurrentParticipants() {
        val list = arrayListOf<Address>()

        for (participant in conference.participantList) {
            list.add(participant.address)
        }

        selectedAddresses.value = list
    }
}
