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
package org.linphone.ui.main.viewmodel

import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.core.Call
import org.linphone.core.Core
import org.linphone.core.CoreListenerStub
import org.linphone.core.tools.Log
import org.linphone.utils.LinphoneUtils

class BottomNavBarViewModel @UiThread constructor() : ViewModel() {
    companion object {
        private const val TAG = "[Bottom Navigation Bar ViewModel]"
    }

    val contactsSelected = MutableLiveData<Boolean>()

    val callsSelected = MutableLiveData<Boolean>()

    val conversationsSelected = MutableLiveData<Boolean>()

    val meetingsSelected = MutableLiveData<Boolean>()

    val hideConversations = MutableLiveData<Boolean>()

    val hideMeetings = MutableLiveData<Boolean>()

    val missedCallsCount = MutableLiveData<Int>()

    private val coreListener = object : CoreListenerStub() {
        @WorkerThread
        override fun onCallStateChanged(
            core: Core,
            call: Call,
            state: Call.State?,
            message: String
        ) {
            if (state == Call.State.End || state == Call.State.Error) {
                updateMissedCallsCount()
            }
        }
    }

    init {
        coreContext.postOnCoreThread { core ->
            core.addListener(coreListener)
            updateMissedCallsCount()

            hideConversations.postValue(corePreferences.disableChat)

            val hideGroupCall = corePreferences.disableMeetings || !LinphoneUtils.isRemoteConferencingAvailable(
                core
            )
            hideMeetings.postValue(hideGroupCall)
        }
    }

    @UiThread
    override fun onCleared() {
        super.onCleared()

        coreContext.postOnCoreThread { core ->
            core.removeListener(coreListener)
        }
    }

    @WorkerThread
    fun updateMissedCallsCount() {
        val account = LinphoneUtils.getDefaultAccount()
        val count = account?.missedCallsCount ?: coreContext.core.missedCallsCount
        val moreThanOne = count > 1
        Log.i(
            "$TAG There ${if (moreThanOne) "are" else "is"} [$count] missed ${if (moreThanOne) "calls" else "call"}"
        )
        missedCallsCount.postValue(count)
    }

    @UiThread
    fun resetMissedCallsCount() {
        coreContext.postOnCoreThread { core ->
            val account = LinphoneUtils.getDefaultAccount()
            account?.resetMissedCallsCount() ?: core.resetMissedCallsCount()
            updateMissedCallsCount()
        }
    }
}
