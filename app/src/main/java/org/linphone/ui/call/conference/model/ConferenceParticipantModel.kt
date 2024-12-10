/*
 * Copyright (c) 2010-2023 Belledonne Communications SARL.
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
package org.linphone.ui.call.conference.model

import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.lifecycle.MutableLiveData
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.core.Participant
import org.linphone.core.tools.Log
import org.linphone.ui.main.contacts.model.ContactAvatarModel

class ConferenceParticipantModel
    @WorkerThread
    constructor(
    val participant: Participant,
    val avatarModel: ContactAvatarModel,
    isMyselfAdmin: Boolean,
    val isMyself: Boolean,
    private val removeFromConference: ((participant: Participant) -> Unit)?,
    private val changeAdminStatus: ((participant: Participant, setAdmin: Boolean) -> Unit)?
) {
    companion object {
        private const val TAG = "[Conference Participant Model]"
    }

    val sipUri = participant.address.asStringUriOnly()

    val isAdmin = MutableLiveData<Boolean>()

    val isMeAdmin = MutableLiveData<Boolean>()

    init {
        isAdmin.postValue(participant.isAdmin)
        isMeAdmin.postValue(isMyselfAdmin)
    }

    @UiThread
    fun removeParticipant() {
        Log.w("$TAG Removing participant from conference")
        coreContext.postOnCoreThread {
            removeFromConference?.invoke(participant)
        }
    }

    @UiThread
    fun toggleAdminStatus() {
        val newStatus = isAdmin.value == false
        Log.w(
            "$TAG Changing participant admin status to ${if (newStatus) "admin" else "not admin"}"
        )
        isAdmin.postValue(newStatus)

        coreContext.postOnCoreThread {
            changeAdminStatus?.invoke(participant, newStatus)
        }
    }
}
