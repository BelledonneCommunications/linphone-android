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
package org.linphone.ui.call.model

import androidx.annotation.WorkerThread
import androidx.lifecycle.MutableLiveData
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.core.ParticipantDevice
import org.linphone.core.ParticipantDeviceListenerStub
import org.linphone.core.tools.Log

class ConferenceParticipantDeviceModel @WorkerThread constructor(
    val device: ParticipantDevice,
    val isMe: Boolean = false
) {
    companion object {
        private const val TAG = "[Conference Participant Device Model]"
    }

    val avatarModel = coreContext.contactsManager.getContactAvatarModelForAddress(device.address)

    val isMuted = MutableLiveData<Boolean>()

    val isSpeaking = MutableLiveData<Boolean>()

    private val deviceListener = object : ParticipantDeviceListenerStub() {
        override fun onStateChanged(
            participantDevice: ParticipantDevice,
            state: ParticipantDevice.State?
        ) {
            Log.i(
                "$TAG Participant device [${participantDevice.address.asStringUriOnly()}] state changed [$state]"
            )
        }

        override fun onIsMuted(participantDevice: ParticipantDevice, muted: Boolean) {
            Log.i(
                "$TAG Participant device [${participantDevice.address.asStringUriOnly()}] is ${if (participantDevice.isMuted) "muted" else "no longer muted"}"
            )
            isMuted.postValue(participantDevice.isMuted)
        }

        override fun onIsSpeakingChanged(
            participantDevice: ParticipantDevice,
            speaking: Boolean
        ) {
            Log.i(
                "$TAG Participant device [${participantDevice.address.asStringUriOnly()}] is ${if (participantDevice.isSpeaking) "speaking" else "no longer speaking"}"
            )
            isSpeaking.postValue(participantDevice.isSpeaking)
        }
    }

    init {
        device.addListener(deviceListener)

        isMuted.postValue(device.isMuted)
        isSpeaking.postValue(device.isSpeaking)
        Log.i(
            "$TAG Participant [${device.address.asStringUriOnly()}] is in state [${device.state}]"
        )
    }

    @WorkerThread
    fun destroy() {
        device.removeListener(deviceListener)
    }
}
