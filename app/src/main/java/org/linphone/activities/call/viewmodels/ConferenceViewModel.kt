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

    val conferenceParticipants = MutableLiveData<List<ConferenceParticipantViewModel>>()

    private val conferenceListener = object : ConferenceListenerStub() {
        // TODO add participant added/removed/admin state changed callbacks
    }

    private val listener = object : CoreListenerStub() {
        override fun onConferenceStateChanged(
            core: Core,
            conference: Conference,
            state: Conference.State
        ) {
            Log.i("[Conference VM] Conference state changed: $state")
            isConferencePaused.value = !coreContext.core.isInConference

            if (state == Conference.State.Created) {
                conference.addListener(conferenceListener)
                updateParticipantsList(conference)
            } else if (state == Conference.State.Terminated || state == Conference.State.TerminationFailed) {
                conference.removeListener(conferenceListener)
            }
        }
    }

    init {
        coreContext.core.addListener(listener)

        isConferencePaused.value = !coreContext.core.isInConference
        conferenceParticipants.value = arrayListOf()
    }

    override fun onCleared() {
        coreContext.core.removeListener(listener)

        super.onCleared()
    }

    fun pauseConference() {
        // TODO: If conference.getMe().isFocus()
        if (coreContext.core.isInConference) {
            coreContext.core.leaveConference()
            isConferencePaused.value = true
        }
    }

    fun resumeConference() {
        // TODO: If conference.getMe().isFocus()
        if (!coreContext.core.isInConference) {
            coreContext.core.enterConference()
            isConferencePaused.value = false
        }
    }

    private fun updateParticipantsList(conference: Conference) {
        val participants = arrayListOf<ConferenceParticipantViewModel>()
        for (participant in conference.participantList) {
            val viewModel = ConferenceParticipantViewModel(conference, participant)
            participants.add(viewModel)
        }
        conferenceParticipants.value = participants
    }
}
