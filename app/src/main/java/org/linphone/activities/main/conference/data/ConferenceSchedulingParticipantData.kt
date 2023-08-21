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
import org.linphone.contact.GenericContactData
import org.linphone.core.Address
import org.linphone.utils.LinphoneUtils

class ConferenceSchedulingParticipantData(
    val sipAddress: Address,
    val showLimeBadge: Boolean = false,
    val showDivider: Boolean = true,
    val showBroadcastControls: Boolean = false,
    val speaker: Boolean = false,
    private val onAddedToSpeakers: ((data: ConferenceSchedulingParticipantData) -> Unit)? = null,
    private val onRemovedFromSpeakers: ((data: ConferenceSchedulingParticipantData) -> Unit)? = null
) :
    GenericContactData(sipAddress) {
    val isSpeaker = MutableLiveData<Boolean>()

    val sipUri: String get() = LinphoneUtils.getDisplayableAddress(sipAddress)

    init {
        isSpeaker.value = speaker
    }

    fun changeIsSpeaker() {
        isSpeaker.value = isSpeaker.value == false
        if (isSpeaker.value == true) {
            onAddedToSpeakers?.invoke(this)
        } else {
            onRemovedFromSpeakers?.invoke(this)
        }
    }
}
