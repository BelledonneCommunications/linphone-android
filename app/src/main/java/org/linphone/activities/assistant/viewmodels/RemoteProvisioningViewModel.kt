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

import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.core.ConfiguringState
import org.linphone.core.Core
import org.linphone.core.CoreListenerStub
import org.linphone.core.tools.Log
import org.linphone.utils.Event

class RemoteProvisioningViewModel : ViewModel() {
    val urlToFetch = MutableLiveData<String>()
    val urlError = MutableLiveData<String>()

    val fetchEnabled: MediatorLiveData<Boolean> = MediatorLiveData()
    val fetchInProgress = MutableLiveData<Boolean>()
    val fetchSuccessfulEvent = MutableLiveData<Event<Boolean>>()

    private val listener = object : CoreListenerStub() {
        override fun onConfiguringStatus(core: Core, status: ConfiguringState, message: String?) {
            fetchInProgress.value = false
            when (status) {
                ConfiguringState.Successful -> {
                    fetchSuccessfulEvent.value = Event(true)
                }
                ConfiguringState.Failed -> {
                    fetchSuccessfulEvent.value = Event(false)
                }
                else -> {}
            }
        }
    }

    init {
        fetchInProgress.value = false
        coreContext.core.addListener(listener)

        fetchEnabled.value = false
        fetchEnabled.addSource(urlToFetch) {
            fetchEnabled.value = isFetchEnabled()
        }
        fetchEnabled.addSource(urlError) {
            fetchEnabled.value = isFetchEnabled()
        }
    }

    override fun onCleared() {
        coreContext.core.removeListener(listener)
        super.onCleared()
    }

    fun fetchAndApply() {
        val url = urlToFetch.value.orEmpty()
        coreContext.core.provisioningUri = url
        Log.w("[Remote Provisioning] Url set to [$url], restarting Core")
        fetchInProgress.value = true
        coreContext.core.stop()
        coreContext.core.start()
    }

    private fun isFetchEnabled(): Boolean {
        return urlToFetch.value.orEmpty().isNotEmpty() && urlError.value.orEmpty().isEmpty()
    }
}
