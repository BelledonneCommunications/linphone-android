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
package org.linphone.telecom

import android.net.Uri
import android.telecom.Connection
import android.telecom.VideoProfile
import android.view.Surface
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.core.VideoDefinition
import org.linphone.core.tools.Log
import org.linphone.utils.LinphoneUtils

class NativeVideoProvider : Connection.VideoProvider() {
    override fun onSetCamera(cameraId: String?) {
        Log.i("[Video Provider] Changing camera to: $cameraId")
        // TODO FIXME: set correct camera ID
        coreContext.switchCamera()
    }

    override fun onSetPreviewSurface(surface: Surface?) {
        Log.i("[Video Provider] Changing preview surface to: $surface")
        coreContext.core.nativePreviewWindowId = surface
    }

    override fun onSetDisplaySurface(surface: Surface?) {
        Log.i("[Video Provider] Changing display surface to: $surface")
        coreContext.core.nativeVideoWindowId = surface
    }

    override fun onSetDeviceOrientation(degrees: Int) {
        Log.i("[Video Provider] Changing device rotation to: $degrees")
        val rotation = (360 - degrees) % 360
        coreContext.core.deviceRotation = rotation
    }

    override fun onSetZoom(value: Float) {
        Log.i("[Video Provider] Changing zoom to: $value")
        coreContext.core.currentCall?.zoom(value, 0f, 0f)
    }

    override fun onSendSessionModifyRequest(fromProfile: VideoProfile?, toProfile: VideoProfile?) {
        val fromVideoState = LinphoneUtils.videoStateToString(fromProfile?.videoState)
        val toVideoState = LinphoneUtils.videoStateToString(toProfile?.videoState)
        Log.i("[Video Provider] onSendSessionModifyRequest: $fromVideoState -> $toVideoState")
        // TODO
    }

    override fun onSendSessionModifyResponse(responseProfile: VideoProfile?) {
        Log.i("[Video Provider] onSendSessionModifyResponse: $responseProfile")
        // TODO
    }

    override fun onRequestCameraCapabilities() {
        Log.i("[Video Provider] onRequestCameraCapabilities")
        val currentVideoDefinition: VideoDefinition = coreContext.core.preferredVideoDefinition
        val capabilities = VideoProfile.CameraCapabilities(currentVideoDefinition.width, currentVideoDefinition.height)
        Log.i("[Video Provider] onRequestCameraCapabilities: capabilities set to width=${capabilities.width}, height=${capabilities.height}")
        changeCameraCapabilities(capabilities)
    }

    override fun onRequestConnectionDataUsage() {
        Log.i("[Video Provider] onRequestConnectionDataUsage")
        // TODO? setCallDataUsage()
    }

    override fun onSetPauseImage(uri: Uri?) {
        Log.i("[Video Provider] onSetPauseImage: $uri")
    }
}
