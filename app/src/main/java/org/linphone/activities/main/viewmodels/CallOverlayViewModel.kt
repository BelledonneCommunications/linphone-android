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
package org.linphone.activities.main.viewmodels

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.core.Call
import org.linphone.core.Core
import org.linphone.core.CoreListenerStub
import org.linphone.core.tools.Log

class CallOverlayViewModel : ViewModel() {
    val displayCallOverlay = MutableLiveData<Boolean>()

    private val listener = object : CoreListenerStub() {
        override fun onCallStateChanged(
            core: Core,
            call: Call,
            state: Call.State?,
            message: String
        ) {
            if (core.callsNb == 1 && call.state == Call.State.Connected) {
                Log.i("[Call Overlay] First call connected, creating it")
                createCallOverlay()
            }
        }

        override fun onLastCallEnded(core: Core) {
            Log.i("[Call Overlay] Last call ended, removing it")
            removeCallOverlay()
        }
    }

    init {
        displayCallOverlay.value = corePreferences.showCallOverlay &&
            !corePreferences.systemWideCallOverlay &&
            coreContext.core.callsNb > 0

        coreContext.core.addListener(listener)
    }

    override fun onCleared() {
        coreContext.core.removeListener(listener)

        super.onCleared()
    }

    private fun createCallOverlay() {
        // If overlay is disabled or if system-wide call overlay is enabled, abort
        if (!corePreferences.showCallOverlay || corePreferences.systemWideCallOverlay) {
            return
        }

        displayCallOverlay.value = true
    }

    private fun removeCallOverlay() {
        displayCallOverlay.value = false
    }
}
