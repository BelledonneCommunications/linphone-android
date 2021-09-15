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

import android.text.format.DateFormat
import androidx.lifecycle.MutableLiveData
import java.text.Format
import java.text.SimpleDateFormat
import java.util.*
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.activities.main.conference.data.ConferenceSchedulingParticipantData
import org.linphone.contact.ContactsSelectionViewModel
import org.linphone.core.Address
import org.linphone.core.Factory
import org.linphone.core.tools.Log
import org.linphone.utils.Event

class ConferenceSchedulingViewModel : ContactsSelectionViewModel() {
    val subject = MutableLiveData<String>()

    val scheduleForLater = MutableLiveData<Boolean>()

    val formattedDate = MutableLiveData<String>()
    val formattedTime = MutableLiveData<String>()
    val formattedDuration = MutableLiveData<String>()
    val formattedTimezone = MutableLiveData<String>()

    val isEncrypted = MutableLiveData<Boolean>()

    val sendInviteViaChat = MutableLiveData<Boolean>()
    val sendInviteViaEmail = MutableLiveData<Boolean>()

    val participantsData = MutableLiveData<List<ConferenceSchedulingParticipantData>>()

    val address = MutableLiveData<String>()

    val copyToClipboardEvent: MutableLiveData<Event<String>> by lazy {
        MutableLiveData<Event<String>>()
    }

    private var date: Long = 0
    private var hour: Int = 0
    private var minutes: Int = 0

    init {
        sipContactsSelected.value = true

        subject.value = ""
        scheduleForLater.value = false
        isEncrypted.value = false
        sendInviteViaChat.value = true
        sendInviteViaEmail.value = false

        address.value = "conf.linphone.org/sak-pesj-toc" // TODO
    }

    override fun onCleared() {
        participantsData.value.orEmpty().forEach(ConferenceSchedulingParticipantData::destroy)
        super.onCleared()
    }

    fun setDate(d: Long) {
        val dateFormat: Format = DateFormat.getDateFormat(coreContext.context)
        val pattern = (dateFormat as SimpleDateFormat).toLocalizedPattern()

        date = d
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = date
        formattedDate.value = SimpleDateFormat(pattern, Locale.getDefault()).format(calendar.time)
    }

    fun setTime(h: Int, m: Int) {
        hour = h
        minutes = m
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, hour)
        calendar.set(Calendar.MINUTE, minutes)
        if (DateFormat.is24HourFormat(coreContext.context)) {
            formattedTime.value =
                SimpleDateFormat("HH'h'mm", Locale.getDefault()).format(calendar.time)
        } else {
            formattedTime.value =
                SimpleDateFormat("h:mm a", Locale.getDefault()).format(calendar.time)
        }
    }

    fun updateEncryption(enable: Boolean) {
        isEncrypted.value = enable
    }

    fun computeParticipantsData() {
        participantsData.value.orEmpty().forEach(ConferenceSchedulingParticipantData::destroy)
        val list = arrayListOf<ConferenceSchedulingParticipantData>()

        for (address in selectedAddresses.value.orEmpty()) {
            val data = ConferenceSchedulingParticipantData(address, isEncrypted.value == true)
            list.add(data)
        }

        participantsData.value = list
    }

    fun createConference() {
        val participantsCount = selectedAddresses.value.orEmpty().size
        if (participantsCount == 0) {
            Log.e("[Conference Creation] Couldn't create conference without any participant!")
            return
        }

        val participants = arrayOfNulls<Address>(participantsCount)
        selectedAddresses.value?.toArray(participants)

        if (scheduleForLater.value == true) {
            val conferenceInfo = Factory.instance().createConferenceInfo()
            conferenceInfo.setParticipants(participants)
            conferenceInfo.organizer = coreContext.core.defaultAccount?.params?.identityAddress
            conferenceInfo.subject = subject.value
            coreContext.core.sendConferenceInformation(conferenceInfo, "Je t'invite !")
        } else {
            val conferenceParams = coreContext.core.createConferenceParams()
            val conference = coreContext.core.createConferenceWithParams(conferenceParams)
            if (conference == null) {
                Log.e("[Conference Creation] Couldn't create conference from params!")
                return
            }

            val callParams = coreContext.core.createCallParams(null)
            conference.inviteParticipants(participants, callParams)
        }
    }

    fun copyAddressToClipboard() {
        copyToClipboardEvent.value = Event(address.value.orEmpty())
    }
}
