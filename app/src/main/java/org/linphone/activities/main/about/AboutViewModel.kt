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
package org.linphone.activities.main.about

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.core.Core
import org.linphone.core.CoreListenerStub
import org.linphone.utils.Event

class AboutViewModel : ViewModel() {
    val appVersion: String = coreContext.appVersion

    val sdkVersion: String = coreContext.sdkVersion

    val showLogsButtons: Boolean = corePreferences.debugLogs

    val uploadInProgress = MutableLiveData<Boolean>()

    val uploadFinishedEvent: MutableLiveData<Event<String>> by lazy {
        MutableLiveData<Event<String>>()
    }

    private val listener = object : CoreListenerStub() {
        override fun onLogCollectionUploadStateChanged(
            core: Core,
            state: Core.LogCollectionUploadState,
            info: String
        ) {
            if (state == Core.LogCollectionUploadState.Delivered) {
                uploadInProgress.value = false
                uploadFinishedEvent.value = Event(info)
            } else if (state == Core.LogCollectionUploadState.NotDelivered) {
                uploadInProgress.value = false
            }
        }
    }

    init {
        coreContext.core.addListener(listener)
        uploadInProgress.value = false
    }

    override fun onCleared() {
        coreContext.core.removeListener(listener)

        super.onCleared()
    }

    fun uploadLogs() {
        uploadInProgress.value = true
        coreContext.core.uploadLogCollection()
    }

    fun resetLogs() {
        coreContext.core.resetLogCollection()
    }
}
