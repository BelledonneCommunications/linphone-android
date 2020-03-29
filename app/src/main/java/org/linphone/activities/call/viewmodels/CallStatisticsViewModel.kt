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
package org.linphone.activities.call.viewmodels

import androidx.lifecycle.MutableLiveData
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.contact.GenericContactViewModel
import org.linphone.core.*

class CallStatisticsViewModel(val call: Call) : GenericContactViewModel(call.remoteAddress) {
    val audioStats = MutableLiveData<ArrayList<StatItemViewModel>>()

    val videoStats = MutableLiveData<ArrayList<StatItemViewModel>>()

    val isVideoEnabled = MutableLiveData<Boolean>()

    val isExpanded = MutableLiveData<Boolean>()

    private val listener = object : CoreListenerStub() {
        override fun onCallStatsUpdated(core: Core, call: Call, stats: CallStats) {
            if (call == this@CallStatisticsViewModel.call) {
                isVideoEnabled.value = call.currentParams.videoEnabled()
                updateCallStats(stats)
            }
        }
    }

    init {
        coreContext.core.addListener(listener)

        audioStats.value = arrayListOf()
        videoStats.value = arrayListOf()

        initCallStats()

        val videoEnabled = call.currentParams.videoEnabled()
        isVideoEnabled.value = videoEnabled

        isExpanded.value = coreContext.core.currentCall == call
    }

    override fun onCleared() {
        coreContext.core.removeListener(listener)

        super.onCleared()
    }

    fun toggleExpanded() {
        isExpanded.value = isExpanded.value != true
    }

    private fun initCallStats() {
        val audioList = arrayListOf<StatItemViewModel>()
        audioList.add(StatItemViewModel(StatType.CAPTURE))
        audioList.add(StatItemViewModel(StatType.PLAYBACK))
        audioList.add(StatItemViewModel(StatType.PAYLOAD))
        audioList.add(StatItemViewModel(StatType.ENCODER))
        audioList.add(StatItemViewModel(StatType.DECODER))
        audioList.add(StatItemViewModel(StatType.DOWNLOAD_BW))
        audioList.add(StatItemViewModel(StatType.UPLOAD_BW))
        audioList.add(StatItemViewModel(StatType.ICE))
        audioList.add(StatItemViewModel(StatType.IP_FAM))
        audioList.add(StatItemViewModel(StatType.SENDER_LOSS))
        audioList.add(StatItemViewModel(StatType.RECEIVER_LOSS))
        audioList.add(StatItemViewModel(StatType.JITTER))
        audioStats.value = audioList

        val videoList = arrayListOf<StatItemViewModel>()
        videoList.add(StatItemViewModel(StatType.CAPTURE))
        videoList.add(StatItemViewModel(StatType.PLAYBACK))
        videoList.add(StatItemViewModel(StatType.PAYLOAD))
        videoList.add(StatItemViewModel(StatType.ENCODER))
        videoList.add(StatItemViewModel(StatType.DECODER))
        videoList.add(StatItemViewModel(StatType.DOWNLOAD_BW))
        videoList.add(StatItemViewModel(StatType.UPLOAD_BW))
        videoList.add(StatItemViewModel(StatType.ESTIMATED_AVAILABLE_DOWNLOAD_BW))
        videoList.add(StatItemViewModel(StatType.ICE))
        videoList.add(StatItemViewModel(StatType.IP_FAM))
        videoList.add(StatItemViewModel(StatType.SENDER_LOSS))
        videoList.add(StatItemViewModel(StatType.RECEIVER_LOSS))
        videoList.add(StatItemViewModel(StatType.SENT_RESOLUTION))
        videoList.add(StatItemViewModel(StatType.RECEIVED_RESOLUTION))
        videoList.add(StatItemViewModel(StatType.SENT_FPS))
        videoList.add(StatItemViewModel(StatType.RECEIVED_FPS))
        videoStats.value = videoList
    }

    private fun updateCallStats(stats: CallStats) {
        if (stats.type == StreamType.Audio) {
            for (stat in audioStats.value.orEmpty()) {
                stat.update(call, stats)
            }
        } else if (stats.type == StreamType.Video) {
            for (stat in videoStats.value.orEmpty()) {
                stat.update(call, stats)
            }
        }
    }
}
