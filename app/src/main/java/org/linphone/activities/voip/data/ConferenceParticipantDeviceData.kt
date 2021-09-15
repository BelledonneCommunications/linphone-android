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
import androidx.lifecycle.MutableLiveData
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.contact.GenericContactData
import org.linphone.core.MediaDirection
import org.linphone.core.ParticipantDevice
import org.linphone.core.ParticipantDeviceListenerStub
import org.linphone.core.tools.Log

class ConferenceParticipantDeviceData(
    private val participantDevice: ParticipantDevice,
    val isMe: Boolean
) :
    GenericContactData(participantDevice.address) {
    val videoEnabled = MutableLiveData<Boolean>()

    val activeSpeaker = MutableLiveData<Boolean>()

    val isInConference = MutableLiveData<Boolean>()

    private val listener = object : ParticipantDeviceListenerStub() {
        override fun onIsThisSpeakingChanged(
            participantDevice: ParticipantDevice,
            isSpeaking: Boolean
        ) {
            Log.i("[Conference Participant Device] Participant [${participantDevice.address.asStringUriOnly()}] is ${if (isSpeaking) "speaking" else "not speaking"}")
            activeSpeaker.value = isSpeaking
        }
    }

    init {
        Log.i("[Conference Participant Device] Created device width Address [${participantDevice.address.asStringUriOnly()}]")
        participantDevice.addListener(listener)

        activeSpeaker.value = participantDevice.isSpeaking

        // TODO: update with callback
        videoEnabled.value = participantDevice.videoDirection == MediaDirection.SendOnly || participantDevice.videoDirection == MediaDirection.SendRecv

        // TODO: update with callback
        isInConference.value = participantDevice.isInConference
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

    fun setTextureView(textureView: TextureView) {
        if (participantDevice.videoDirection != MediaDirection.SendRecv) {
            Log.e("[Conference Participant Device] Participant [${participantDevice.address.asStringUriOnly()}] device video direction is ${participantDevice.videoDirection}, don't set TextureView!")
            return
        }

        if (textureView.isAvailable) {
            Log.i("[Conference Participant Device] Setting textureView [$textureView] for participant [${participantDevice.address.asStringUriOnly()}]")
            if (isMe) { // TODO: remove
                coreContext.core.nativePreviewWindowId = textureView
            } else {
                participantDevice.nativeVideoWindowId = textureView
            }
        } else {
            textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(
                    surface: SurfaceTexture,
                    width: Int,
                    height: Int
                ) {
                    Log.i("[Conference Participant Device] Setting textureView [$textureView] for participant [${participantDevice.address.asStringUriOnly()}]")
                    if (isMe) { // TODO: remove
                        coreContext.core.nativePreviewWindowId = textureView
                    } else {
                        participantDevice.nativeVideoWindowId = textureView
                    }
                }

                override fun onSurfaceTextureSizeChanged(
                    surface: SurfaceTexture,
                    width: Int,
                    height: Int
                ) { }

                override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                    Log.w("[Conference Participant Device] TextureView [$textureView] for participant [${participantDevice.address.asStringUriOnly()}] has been destroyed")
                    participantDevice.nativeVideoWindowId = null
                    return true
                }

                override fun onSurfaceTextureUpdated(surface: SurfaceTexture) { }
            }
        }
    }
}
