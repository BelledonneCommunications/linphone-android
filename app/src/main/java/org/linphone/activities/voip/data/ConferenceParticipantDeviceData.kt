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
package org.linphone.activities.voip.data

import android.view.TextureView
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.contact.GenericContactData
import org.linphone.core.*
import org.linphone.core.tools.Log

class ConferenceParticipantDeviceData(
    val participantDevice: ParticipantDevice,
    val isMe: Boolean
) :
    GenericContactData(participantDevice.address) {
    val videoEnabled: MediatorLiveData<Boolean> = MediatorLiveData()

    val videoAvailable = MutableLiveData<Boolean>()

    val isSendingVideo = MutableLiveData<Boolean>()

    val isSpeaking = MutableLiveData<Boolean>()

    val isMuted = MutableLiveData<Boolean>()

    val isInConference = MutableLiveData<Boolean>()

    val isJoining = MutableLiveData<Boolean>()

    val isActiveSpeaker = MutableLiveData<Boolean>()

    private var textureView: TextureView? = null

    private val listener = object : ParticipantDeviceListenerStub() {
        override fun onIsSpeakingChanged(
            participantDevice: ParticipantDevice,
            isSpeaking: Boolean
        ) {
            Log.i(
                "[Conference Participant Device] Participant [${participantDevice.address.asStringUriOnly()}] is ${if (isSpeaking) "speaking" else "not speaking"}"
            )
            this@ConferenceParticipantDeviceData.isSpeaking.value = isSpeaking
        }

        override fun onIsMuted(participantDevice: ParticipantDevice, isMuted: Boolean) {
            Log.i(
                "[Conference Participant Device] Participant [${participantDevice.address.asStringUriOnly()}] is ${if (isMuted) "muted" else "not muted"}"
            )
            this@ConferenceParticipantDeviceData.isMuted.value = isMuted
        }

        override fun onStateChanged(
            participantDevice: ParticipantDevice,
            state: ParticipantDevice.State
        ) {
            Log.i(
                "[Conference Participant Device] Participant [${participantDevice.address.asStringUriOnly()}] state has changed: $state"
            )
            when (state) {
                ParticipantDevice.State.Joining, ParticipantDevice.State.Alerting -> isJoining.value = true
                ParticipantDevice.State.OnHold -> {
                    isInConference.value = false
                }
                ParticipantDevice.State.Present -> {
                    isJoining.value = false
                    isInConference.value = true
                    updateWindowId(textureView)
                }
                else -> {}
            }
        }

        override fun onStreamCapabilityChanged(
            participantDevice: ParticipantDevice,
            direction: MediaDirection,
            streamType: StreamType
        ) {
            if (streamType == StreamType.Video) {
                Log.i(
                    "[Conference Participant Device] Participant [${participantDevice.address.asStringUriOnly()}] video capability changed to $direction"
                )
                isSendingVideo.value = direction == MediaDirection.SendRecv || direction == MediaDirection.SendOnly
            }
        }

        override fun onStreamAvailabilityChanged(
            participantDevice: ParticipantDevice,
            available: Boolean,
            streamType: StreamType
        ) {
            if (streamType == StreamType.Video) {
                Log.i(
                    "[Conference Participant Device] Participant [${participantDevice.address.asStringUriOnly()}] video availability changed to ${if (available) "available" else "unavailable"}"
                )
                videoAvailable.value = available
                if (available) {
                    updateWindowId(textureView)
                }
            }
        }
    }

    init {
        Log.i(
            "[Conference Participant Device] Created device width Address [${participantDevice.address.asStringUriOnly()}], is it myself? $isMe"
        )
        participantDevice.addListener(listener)

        isSpeaking.value = false
        isActiveSpeaker.value = false
        isMuted.value = participantDevice.isMuted

        videoAvailable.value = participantDevice.getStreamAvailability(StreamType.Video)
        val videoCapability = participantDevice.getStreamCapability(StreamType.Video)
        isSendingVideo.value = videoCapability == MediaDirection.SendRecv || videoCapability == MediaDirection.SendOnly
        isInConference.value = participantDevice.isInConference

        val state = participantDevice.state
        isJoining.value = state == ParticipantDevice.State.Joining || state == ParticipantDevice.State.Alerting
        Log.i(
            "[Conference Participant Device] State for participant [${participantDevice.address.asStringUriOnly()}] is $state"
        )

        videoEnabled.value = isVideoAvailableAndSendReceive()
        videoEnabled.addSource(videoAvailable) {
            videoEnabled.value = isVideoAvailableAndSendReceive()
        }
        videoEnabled.addSource(isSendingVideo) {
            videoEnabled.value = isVideoAvailableAndSendReceive()
        }

        Log.i(
            "[Conference Participant Device] Participant [${participantDevice.address.asStringUriOnly()}], is in conf? ${isInConference.value}, is video available? ${videoAvailable.value} ($videoCapability), is mic muted? ${isMuted.value}"
        )
    }

    override fun destroy() {
        participantDevice.removeListener(listener)

        super.destroy()
    }

    fun switchCamera() {
        coreContext.switchCamera()
    }

    fun isSwitchCameraAvailable(): Boolean {
        return isMe && coreContext.showSwitchCameraButton()
    }

    fun setTextureView(tv: TextureView) {
        textureView = tv

        Log.i(
            "[Conference Participant Device] Setting textureView [$textureView] for participant [${participantDevice.address.asStringUriOnly()}]"
        )
        updateWindowId(textureView)
    }

    private fun updateWindowId(windowId: Any?) {
        participantDevice.nativeVideoWindowId = windowId
    }

    private fun isVideoAvailableAndSendReceive(): Boolean {
        return videoAvailable.value == true && isSendingVideo.value == true
    }
}
