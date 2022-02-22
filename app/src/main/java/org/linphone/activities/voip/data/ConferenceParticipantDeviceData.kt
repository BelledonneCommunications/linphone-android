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

import android.graphics.SurfaceTexture
import android.view.TextureView
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.contact.GenericContactData
import org.linphone.core.MediaDirection
import org.linphone.core.ParticipantDevice
import org.linphone.core.ParticipantDeviceListenerStub
import org.linphone.core.StreamType
import org.linphone.core.tools.Log

class ConferenceParticipantDeviceData(
    val participantDevice: ParticipantDevice,
    val isMe: Boolean
) :
    GenericContactData(participantDevice.address) {
    val videoEnabled: MediatorLiveData<Boolean> = MediatorLiveData()

    val videoAvailable = MutableLiveData<Boolean>()

    val videoSendReceive = MutableLiveData<Boolean>()

    val activeSpeaker = MutableLiveData<Boolean>()

    val micMuted = MutableLiveData<Boolean>()

    val isInConference = MutableLiveData<Boolean>()

    private var textureView: TextureView? = null

    private val listener = object : ParticipantDeviceListenerStub() {
        override fun onIsSpeakingChanged(
            participantDevice: ParticipantDevice,
            isSpeaking: Boolean
        ) {
            Log.i("[Conference Participant Device] Participant [${participantDevice.address.asStringUriOnly()}] is ${if (isSpeaking) "speaking" else "not speaking"}")
            activeSpeaker.value = isSpeaking
        }

        override fun onConferenceJoined(participantDevice: ParticipantDevice) {
            Log.i("[Conference Participant Device] Participant [${participantDevice.address.asStringUriOnly()}] has joined the conference")
            isInConference.value = true
            updateWindowId(textureView)
        }

        override fun onConferenceLeft(participantDevice: ParticipantDevice) {
            Log.i("[Conference Participant Device] Participant [${participantDevice.address.asStringUriOnly()}] has left the conference")
            isInConference.value = false
            updateWindowId(null)
        }

        override fun onStreamCapabilityChanged(
            participantDevice: ParticipantDevice,
            direction: MediaDirection,
            streamType: StreamType
        ) {
            if (streamType == StreamType.Video) {
                Log.i("[Conference Participant Device] Participant [${participantDevice.address.asStringUriOnly()}] video capability changed to $direction")
                videoSendReceive.value = direction == MediaDirection.SendRecv
            }
        }

        override fun onStreamAvailabilityChanged(
            participantDevice: ParticipantDevice,
            available: Boolean,
            streamType: StreamType
        ) {
            if (streamType == StreamType.Video) {
                Log.i("[Conference Participant Device] Participant [${participantDevice.address.asStringUriOnly()}] video availability changed to ${if (available) "available" else "unavailable"}")
                videoAvailable.value = available
                if (available) {
                    updateWindowId(textureView)
                }
            }
        }
    }

    init {
        Log.i("[Conference Participant Device] Created device width Address [${participantDevice.address.asStringUriOnly()}], is it myself? $isMe")
        participantDevice.addListener(listener)

        activeSpeaker.value = false
        videoAvailable.value = participantDevice.getStreamAvailability(StreamType.Video)
        val videoCapability = participantDevice.getStreamCapability(StreamType.Video)
        videoSendReceive.value = videoCapability == MediaDirection.SendRecv
        micMuted.value = false // TODO
        isInConference.value = participantDevice.isInConference

        videoEnabled.value = isVideoAvailableAndSendReceive()
        videoEnabled.addSource(videoAvailable) {
            videoEnabled.value = isVideoAvailableAndSendReceive()
        }
        videoEnabled.addSource(videoSendReceive) {
            videoEnabled.value = isVideoAvailableAndSendReceive()
        }

        Log.i("[Conference Participant Device] Participant [${participantDevice.address.asStringUriOnly()}], is in conf? ${isInConference.value}, is video available? ${videoAvailable.value} ($videoCapability)")
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

        if (tv.isAvailable) {
            Log.i("[Conference Participant Device] Setting textureView [$textureView] for participant [${participantDevice.address.asStringUriOnly()}]")
            updateWindowId(textureView)
        } else {
            Log.i("[Conference Participant Device] Got textureView [$textureView] for participant [${participantDevice.address.asStringUriOnly()}], but it is not available yet")
            tv.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(
                    surface: SurfaceTexture,
                    width: Int,
                    height: Int
                ) {
                    Log.i("[Conference Participant Device] Setting textureView [$textureView] for participant [${participantDevice.address.asStringUriOnly()}]")
                    updateWindowId(textureView)
                }

                override fun onSurfaceTextureSizeChanged(
                    surface: SurfaceTexture,
                    width: Int,
                    height: Int
                ) { }

                override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                    Log.w("[Conference Participant Device] TextureView [$textureView] for participant [${participantDevice.address.asStringUriOnly()}] has been destroyed")
                    textureView = null
                    updateWindowId(null)
                    return true
                }

                override fun onSurfaceTextureUpdated(surface: SurfaceTexture) { }
            }
        }
    }

    private fun updateWindowId(windowId: Any?) {
        if (isMe) {
            coreContext.core.nativePreviewWindowId = windowId
        } else {
            participantDevice.nativeVideoWindowId = windowId
        }
    }

    private fun isVideoAvailableAndSendReceive(): Boolean {
        return videoAvailable.value == true && videoSendReceive.value == true
    }
}
