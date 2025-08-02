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
package com.naminfo.ui.call.model

import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.lifecycle.MutableLiveData
import com.naminfo.LinphoneApplication.Companion.coreContext
import org.linphone.core.Call
import org.linphone.core.CallListenerStub
import org.linphone.core.tools.Log
import com.naminfo.ui.main.contacts.model.ContactAvatarModel
import com.naminfo.utils.LinphoneUtils

class CallModel
    @WorkerThread
    constructor(val call: Call) {
    companion object {
        private const val TAG = "[Call Model]"
    }

    val id = call.callLog.callId

    val displayName = MutableLiveData<String>()

    val state = MutableLiveData<String>()

    val isPaused = MutableLiveData<Boolean>()

    val friend = coreContext.contactsManager.findContactByAddress(call.callLog.remoteAddress)

    val contact = MutableLiveData<ContactAvatarModel>()

    private val callListener = object : CallListenerStub() {
        @WorkerThread
        override fun onStateChanged(call: Call, state: Call.State, message: String) {
            this@CallModel.state.postValue(LinphoneUtils.callStateToString(state))
            isPaused.postValue(LinphoneUtils.isCallPaused(state))
        }
    }

    init {
        call.addListener(callListener)

        val conferenceInfo = coreContext.core.findConferenceInformationFromUri(call.remoteAddress)
        val remoteAddress = call.callLog.remoteAddress
        val avatarModel = if (conferenceInfo != null) {
            coreContext.contactsManager.getContactAvatarModelForConferenceInfo(conferenceInfo)
        } else {
            coreContext.contactsManager.getContactAvatarModelForAddress(
                remoteAddress
            )
        }
        contact.postValue(avatarModel)
        displayName.postValue(
            avatarModel.friend.name ?: LinphoneUtils.getDisplayName(remoteAddress)
        )

        state.postValue(LinphoneUtils.callStateToString(call.state))
        isPaused.postValue(LinphoneUtils.isCallPaused(call.state))
    }

    @WorkerThread
    fun destroy() {
        call.removeListener(callListener)
    }

    @WorkerThread
    fun togglePauseResume() {
        when (call.state) {
            Call.State.Paused -> {
                Log.i("$TAG Trying to resume call [${call.remoteAddress.asStringUriOnly()}]")
                call.resume()
            }
            else -> {
                Log.i("$TAG Trying to resume call [${call.remoteAddress.asStringUriOnly()}]")
                call.pause()
            }
        }
    }

    @UiThread
    fun hangUp() {
        coreContext.postOnCoreThread {
            Log.i("$TAG Terminating call [${call.remoteAddress.asStringUriOnly()}]")
            coreContext.terminateCall(call)
        }
    }
}
