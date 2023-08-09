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
package org.linphone.ui.voip.viewmodel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.core.Call
import org.linphone.core.tools.Log

class CallViewModel() : ViewModel() {
    companion object {
        const val TAG = "[Call ViewModel]"
    }

    val videoEnabled = MutableLiveData<Boolean>()

    private lateinit var call: Call

    init {
        videoEnabled.value = false

        coreContext.postOnCoreThread { core ->
            val currentCall = core.currentCall ?: core.calls.firstOrNull()

            if (currentCall != null) {
                call = currentCall
                Log.i("$TAG Found call [$call]")

                if (call.state == Call.State.StreamsRunning) {
                    videoEnabled.postValue(call.currentParams.isVideoEnabled)
                } else {
                    videoEnabled.postValue(call.params.isVideoEnabled)
                }
            } else {
                Log.e("$TAG Failed to find outgoing call!")
            }
        }
    }

    fun hangUp() {
        // UI thread
        coreContext.postOnCoreThread {
            Log.i("$TAG Terminating call [$call]")
            call.terminate()
        }
    }
}
