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
package com.naminfo.ui.assistant.viewmodel

import android.util.Patterns
import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.lifecycle.MutableLiveData
import com.naminfo.LinphoneApplication.Companion.coreContext
import org.linphone.core.ConfiguringState
import org.linphone.core.Core
import org.linphone.core.CoreListenerStub
import org.linphone.core.tools.Log
import com.naminfo.ui.GenericViewModel
import com.naminfo.utils.Event
import com.naminfo.R

class QrCodeViewModel
    @UiThread
    constructor() : GenericViewModel() {
    companion object {
        private const val TAG = "[Qr Code Scanner ViewModel]"
    }

    val qrCodeFoundEvent = MutableLiveData<Event<Boolean>>()

    val onErrorEvent = MutableLiveData<Event<Boolean>>()

    private val coreListener = object : CoreListenerStub() {
        @WorkerThread
        override fun onConfiguringStatus(core: Core, status: ConfiguringState, message: String?) {
            Log.i("$TAG Configuring state is [$status]")
            if (status == ConfiguringState.Successful) {
                qrCodeFoundEvent.postValue(Event(true))
            } else if (status == ConfiguringState.Failed) {
                Log.e("$TAG Failure applying remote provisioning: $message")
                showRedToast(R.string.remote_provisioning_config_failed_toast, R.drawable.warning_circle)
                onErrorEvent.postValue(Event(true))
            }
        }

        @WorkerThread
        override fun onQrcodeFound(core: Core, result: String?) {
            Log.i("$TAG QR Code found: [$result]")
            if (result == null) {
                showRedToast(R.string.assistant_qr_code_invalid_toast, R.drawable.warning_circle)
            } else {
                val isValidUrl = Patterns.WEB_URL.matcher(result).matches()
                if (!isValidUrl) {
                    Log.e("$TAG The content of the QR Code doesn't seem to be a valid web URL")
                    showRedToast(R.string.assistant_qr_code_invalid_toast, R.drawable.warning_circle)
                } else {
                    Log.i(
                        "$TAG QR code URL set, restarting the Core to apply configuration changes"
                    )
                    core.nativePreviewWindowId = null
                    core.isVideoPreviewEnabled = false
                    core.isQrcodeVideoPreviewEnabled = false

                    core.provisioningUri = result
                    coreContext.core.stop()
                    Log.i("$TAG Core has been stopped, restarting it")
                    coreContext.core.start()
                    Log.i("$TAG Core has been restarted")
                }
            }
        }
    }

    init {
        coreContext.postOnCoreThread { core ->
            core.addListener(coreListener)
        }
    }

    @UiThread
    override fun onCleared() {
        coreContext.postOnCoreThread { core ->
            core.removeListener(coreListener)
        }
        super.onCleared()
    }

    @UiThread
    fun setBackCamera() {
        coreContext.postOnCoreThread { core ->
            for (camera in core.videoDevicesList) {
                if (camera.contains("Back")) {
                    Log.i("$TAG Found back facing camera [$camera], using it")
                    coreContext.core.videoDevice = camera
                    return@postOnCoreThread
                }
            }

            for (camera in core.videoDevicesList) {
                if (camera != "StaticImage: Static picture") {
                    Log.w("$TAG No back facing camera found, using first one available [$camera]")
                    coreContext.core.videoDevice = camera
                    return@postOnCoreThread
                }
            }

            Log.e("$TAG No camera device found!")
        }
    }
}
