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
import org.linphone.contact.GenericContactData
import org.linphone.core.MediaDirection
import org.linphone.core.ParticipantDevice
import org.linphone.core.ParticipantDeviceListenerStub
import org.linphone.core.VideoSize
import org.linphone.core.tools.Log

class ConferenceParticipantDeviceData(private val participantDevice: ParticipantDevice) :
    GenericContactData(participantDevice.address) {
    val videoEnabled = MutableLiveData<Boolean>()

    // TODO: Set it to true when info is available
    val activeSpeaker = MutableLiveData<Boolean>()

    private val listener = object : ParticipantDeviceListenerStub() {
        override fun onCaptureVideoSizeChanged(
            participantDevice: ParticipantDevice,
            size: VideoSize
        ) {
            Log.i("[Conference Participant Device] Video size changed to ${size.width}x${size.height}")
        }
    }

    init {
        Log.i("[Conference Participant Device] Created device width Address [${participantDevice.address.asStringUriOnly()}]")
        participantDevice.addListener(listener)

        videoEnabled.value = participantDevice.videoDirection == MediaDirection.SendOnly || participantDevice.videoDirection == MediaDirection.SendRecv
    }

    override fun destroy() {
        participantDevice.removeListener(listener)

        super.destroy()
    }

    fun setTextureView(textureView: TextureView) {
        if (participantDevice.videoDirection != MediaDirection.SendRecv) {
            Log.e("[Conference Participant Device] Participant [${participantDevice.address.asStringUriOnly()}] device video direction is ${participantDevice.videoDirection}, don't set TextureView!")
            return
        }

        Log.i("[Conference Participant Device] Setting textureView [$textureView] for participant [${participantDevice.address.asStringUriOnly()}]")
        if (textureView.isAvailable) {
            participantDevice.nativeVideoWindowId = textureView
        } else {
            textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(
                    surface: SurfaceTexture,
                    width: Int,
                    height: Int
                ) {
                    participantDevice.nativeVideoWindowId = textureView
                }

                override fun onSurfaceTextureSizeChanged(
                    surface: SurfaceTexture,
                    width: Int,
                    height: Int
                ) { }

                override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                    participantDevice.nativeVideoWindowId = null
                    return true
                }

                override fun onSurfaceTextureUpdated(surface: SurfaceTexture) { }
            }
        }
    }
}
