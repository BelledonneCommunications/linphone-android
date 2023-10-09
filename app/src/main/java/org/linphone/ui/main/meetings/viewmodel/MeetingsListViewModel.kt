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
package org.linphone.ui.main.meetings.viewmodel

import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.lifecycle.MutableLiveData
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.core.ConferenceInfo
import org.linphone.core.Core
import org.linphone.core.CoreListenerStub
import org.linphone.core.tools.Log
import org.linphone.ui.main.meetings.model.MeetingModel
import org.linphone.ui.main.viewmodel.AbstractTopBarViewModel

class MeetingsListViewModel @UiThread constructor() : AbstractTopBarViewModel() {
    companion object {
        private const val TAG = "[Meetings List ViewModel]"
    }

    val meetings = MutableLiveData<ArrayList<MeetingModel>>()

    val fetchInProgress = MutableLiveData<Boolean>()

    private var currentFilter = ""

    private val coreListener = object : CoreListenerStub() {
        @WorkerThread
        override fun onConferenceInfoReceived(core: Core, conferenceInfo: ConferenceInfo) {
            Log.i("$TAG Conference info received [${conferenceInfo.uri?.asStringUriOnly()}]")
            computeMeetingsList(currentFilter)
        }
    }

    init {
        coreContext.postOnCoreThread { core ->
            core.addListener(coreListener)

            computeMeetingsList(currentFilter)
        }
    }

    @UiThread
    override fun onCleared() {
        super.onCleared()

        coreContext.postOnCoreThread { core ->
            core.removeListener(coreListener)
        }
    }

    @UiThread
    fun applyFilter(filter: String = currentFilter) {
        currentFilter = filter

        coreContext.postOnCoreThread {
            computeMeetingsList(filter)
        }
    }

    @WorkerThread
    private fun computeMeetingsList(filter: String) {
        val list = arrayListOf<MeetingModel>()

        // TODO FIXME: get list from default account
        for (conferenceInfo in coreContext.core.conferenceInformationList) {
            val model = MeetingModel(conferenceInfo)
            // TODO FIXME: apply filter
            list.add(model)
        }

        meetings.postValue(list)
    }
}
