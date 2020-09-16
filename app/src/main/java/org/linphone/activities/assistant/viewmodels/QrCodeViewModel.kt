/*
 * Copyright (c) 2010-2020 Belledonne Communications SARL.
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
package org.linphone.activities.assistant.viewmodels

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.core.Core
import org.linphone.core.CoreListenerStub
import org.linphone.core.tools.Log
import org.linphone.utils.Event

class QrCodeViewModel : ViewModel() {
    val qrCodeFoundEvent = MutableLiveData<Event<String>>()

    val showSwitchCamera = MutableLiveData<Boolean>()

    private val listener = object : CoreListenerStub() {
        override fun onQrcodeFound(core: Core, result: String?) {
            Log.i("[QR Code] Found [$result]")
            if (result != null) qrCodeFoundEvent.postValue(Event(result))
        }
    }

    init {
        coreContext.core.addListener(listener)
        showSwitchCamera.value = coreContext.showSwitchCameraButton()
    }

    override fun onCleared() {
        coreContext.core.removeListener(listener)
        super.onCleared()
    }

    fun setBackCamera() {
        showSwitchCamera.value = coreContext.showSwitchCameraButton()

        for (camera in coreContext.core.videoDevicesList) {
            if (camera.contains("Back")) {
                Log.i("[QR Code] Found back facing camera: $camera")
                coreContext.core.videoDevice = camera
                return
            }
        }

        val first = coreContext.core.videoDevicesList.firstOrNull()
        if (first != null) {
            Log.i("[QR Code] Using first camera found: $first")
            coreContext.core.videoDevice = first
        }
    }

    fun switchCamera() {
        coreContext.switchCamera()
    }
}
