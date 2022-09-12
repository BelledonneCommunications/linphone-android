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
package org.linphone.activities.main.conference.data

import androidx.lifecycle.MutableLiveData
import java.util.concurrent.TimeUnit
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.core.ConferenceInfo
import org.linphone.core.ConferenceInfo.State
import org.linphone.core.tools.Log
import org.linphone.utils.LinphoneUtils
import org.linphone.utils.TimestampUtils

class ScheduledConferenceData(val conferenceInfo: ConferenceInfo, private val isFinished: Boolean) {
    val expanded = MutableLiveData<Boolean>()
    val backgroundResId = MutableLiveData<Int>()

    val address = MutableLiveData<String>()
    val subject = MutableLiveData<String>()
    val description = MutableLiveData<String>()
    val time = MutableLiveData<String>()
    val date = MutableLiveData<String>()
    val duration = MutableLiveData<String>()
    val organizer = MutableLiveData<String>()
    val canEdit = MutableLiveData<Boolean>()
    val participantsShort = MutableLiveData<String>()
    val participantsExpanded = MutableLiveData<String>()
    val showDuration = MutableLiveData<Boolean>()
    val isConferenceCancelled = MutableLiveData<Boolean>()

    init {
        expanded.value = false

        address.value = conferenceInfo.uri?.asStringUriOnly()
        subject.value = conferenceInfo.subject
        description.value = conferenceInfo.description

        time.value = TimestampUtils.timeToString(conferenceInfo.dateTime)
        date.value = TimestampUtils.toString(conferenceInfo.dateTime, onlyDate = true, shortDate = false, hideYear = false)
        isConferenceCancelled.value = conferenceInfo.state == State.Cancelled

        val minutes = conferenceInfo.duration
        val hours = TimeUnit.MINUTES.toHours(minutes.toLong())
        val remainMinutes = minutes - TimeUnit.HOURS.toMinutes(hours).toInt()
        duration.value = TimestampUtils.durationToString(hours.toInt(), remainMinutes)
        showDuration.value = minutes > 0

        val organizerAddress = conferenceInfo.organizer
        if (organizerAddress != null) {
            val localAccount = coreContext.core.accountList.find { account ->
                val address = account.params.identityAddress
                address != null && organizerAddress.weakEqual(address)
            }
            canEdit.value = localAccount != null

            val contact = coreContext.contactsManager.findContactByAddress(organizerAddress)
            organizer.value = if (contact != null)
                contact.name
            else
                LinphoneUtils.getDisplayName(conferenceInfo.organizer)
        } else {
            canEdit.value = false
            Log.e("[Scheduled Conference] No organizer SIP URI found for: ${conferenceInfo.uri?.asStringUriOnly()}")
        }

        computeBackgroundResId()
        computeParticipantsLists()
    }

    fun destroy() {}

    fun delete() {
        Log.w("[Scheduled Conference] Deleting conference info with URI: ${conferenceInfo.uri?.asStringUriOnly()}")
        coreContext.core.deleteConferenceInformation(conferenceInfo)
    }

    fun toggleExpand() {
        expanded.value = expanded.value == false
        computeBackgroundResId()
    }

    fun getAddressAsString(): String {
        val address = conferenceInfo.uri?.clone()
        if (address != null) {
            address.displayName = conferenceInfo.subject
            return address.asString()
        }
        return ""
    }

    private fun computeBackgroundResId() {
        backgroundResId.value = if (conferenceInfo.state == State.Cancelled) {
            if (expanded.value == true) {
                R.drawable.shape_round_red_background_with_orange_border
            } else {
                R.drawable.shape_round_red_background
            }
        } else if (isFinished) {
            if (expanded.value == true) {
                R.drawable.shape_round_dark_gray_background_with_orange_border
            } else {
                R.drawable.shape_round_dark_gray_background
            }
        } else {
            if (expanded.value == true) {
                R.drawable.shape_round_gray_background_with_orange_border
            } else {
                R.drawable.shape_round_gray_background
            }
        }
    }

    private fun computeParticipantsLists() {
        var participantsListShort = ""
        var participantsListExpanded = ""

        for (participant in conferenceInfo.participants) {
            val contact = coreContext.contactsManager.findContactByAddress(participant)
            val name = if (contact != null) contact.name else LinphoneUtils.getDisplayName(participant)
            val address = participant.asStringUriOnly()
            participantsListShort += "$name, "
            participantsListExpanded += "$name ($address)\n"
        }
        participantsListShort = participantsListShort.dropLast(2)
        participantsListExpanded = participantsListExpanded.dropLast(1)

        participantsShort.value = participantsListShort
        participantsExpanded.value = participantsListExpanded
    }
}
