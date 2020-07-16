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
package org.linphone.activities.call.viewmodels

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.core.*
import org.linphone.core.tools.Log

class ConferenceViewModel : ViewModel() {
    val isConferencePaused = MutableLiveData<Boolean>()

    val isMeConferenceFocus = MutableLiveData<Boolean>()

    val conferenceParticipants = MutableLiveData<List<ConferenceParticipantViewModel>>()

    private val conferenceListener = object : ConferenceListenerStub() {
        override fun onParticipantAdded(conference: Conference, participant: Participant) {
            Log.i("[Conference VM] Participant added")
            updateParticipantsList(conference)
        }

        override fun onParticipantRemoved(conference: Conference, participant: Participant) {
            Log.i("[Conference VM] Participant removed")
            updateParticipantsList(conference)
        }

        override fun onParticipantAdminStatusChanged(
            conference: Conference,
            participant: Participant
        ) {
            Log.i("[Conference VM] Participant admin status changed")
            updateParticipantsList(conference)
        }
    }

    private val listener = object : CoreListenerStub() {
        override fun onConferenceStateChanged(
            core: Core,
            conference: Conference,
            state: Conference.State
        ) {
            Log.i("[Conference VM] Conference state changed: $state")
            isConferencePaused.value = false; // !coreContext.core.isInConference

            if (state == Conference.State.Instantiated) {
                conference.addListener(conferenceListener)
            } else if (state == Conference.State.Created) {
                updateParticipantsList(conference)
                isMeConferenceFocus.value = conference.me.isFocus
            } else if (state == Conference.State.Terminated || state == Conference.State.TerminationFailed) {
                conference.removeListener(conferenceListener)
                conferenceParticipants.value = arrayListOf()
            }
        }
    }

    init {
        coreContext.core.addListener(listener)

        isConferencePaused.value = false; // !coreContext.core.isInConference
        isMeConferenceFocus.value = false
        conferenceParticipants.value = arrayListOf()

        val conference = coreContext.core.conference
        if (conference != null) {
            conference.addListener(conferenceListener)
            isMeConferenceFocus.value = conference.me.isFocus
            updateParticipantsList(conference)
        }
    }

    override fun onCleared() {
        coreContext.core.removeListener(listener)

        super.onCleared()
    }

    fun pauseConference() {
        if (coreContext.core.isInConference) {
            coreContext.core.leaveConference()
            isConferencePaused.value = true
        }
    }

    fun resumeConference() {
        if (!coreContext.core.isInConference) {
            coreContext.core.enterConference()
            isConferencePaused.value = false
        }
    }

    private fun updateParticipantsList(conference: Conference) {
        val participants = arrayListOf<ConferenceParticipantViewModel>()
        for (participant in conference.participantList) {
            Log.i("[Conference VM] Participant found: ${participant.address.asStringUriOnly()}")
            val viewModel = ConferenceParticipantViewModel(conference, participant)
            participants.add(viewModel)
        }
        conferenceParticipants.value = participants
    }
}
