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
package org.linphone.activities.voip.viewmodels

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.activities.voip.data.CallStatisticsData
import org.linphone.core.Call
import org.linphone.core.Core
import org.linphone.core.CoreListenerStub

class StatisticsListViewModel : ViewModel() {
    val callStatsList = MutableLiveData<ArrayList<CallStatisticsData>>()

    private var enabled = false

    private val listener = object : CoreListenerStub() {
        override fun onCallStateChanged(
            core: Core,
            call: Call,
            state: Call.State,
            message: String
        ) {
            if (state == Call.State.End || state == Call.State.Error || state == Call.State.Connected) {
                computeCallsList()
            }
        }
    }

    init {
        coreContext.core.addListener(listener)

        computeCallsList()
    }

    fun enable() {
        enabled = true
        for (stat in callStatsList.value.orEmpty()) {
            stat.enable()
        }
    }

    fun disable() {
        enabled = false
        for (stat in callStatsList.value.orEmpty()) {
            stat.disable()
        }
    }

    override fun onCleared() {
        callStatsList.value.orEmpty().forEach(CallStatisticsData::destroy)
        coreContext.core.removeListener(listener)

        super.onCleared()
    }

    private fun computeCallsList() {
        callStatsList.value.orEmpty().forEach(CallStatisticsData::destroy)

        val list = arrayListOf<CallStatisticsData>()
        for (call in coreContext.core.calls) {
            if (call.state != Call.State.End && call.state != Call.State.Released && call.state != Call.State.Error) {
                val data = CallStatisticsData(call)
                list.add(data)
                if (enabled) {
                    data.enable()
                }
            }
        }

        callStatsList.value = list
    }
}
