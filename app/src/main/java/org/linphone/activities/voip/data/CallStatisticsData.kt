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
package org.linphone.activities.voip.data

import androidx.lifecycle.MutableLiveData
import org.linphone.contact.GenericContactData
import org.linphone.core.*

class CallStatisticsData(val call: Call) : GenericContactData(call.remoteAddress) {
    val audioStats = MutableLiveData<ArrayList<StatItemData>>()

    val videoStats = MutableLiveData<ArrayList<StatItemData>>()

    val mediaEncryptionStats = MutableLiveData<ArrayList<StatItemData>>()

    val isVideoEnabled = MutableLiveData<Boolean>()

    private var enabled = false

    private val listener = object : CallListenerStub() {
        override fun onStatsUpdated(call: Call, stats: CallStats) {
            isVideoEnabled.value = call.currentParams.isVideoEnabled
            updateCallStats(stats)
        }
    }

    init {
        enabled = false
        audioStats.value = arrayListOf()
        videoStats.value = arrayListOf()

        initCallStats()

        val videoEnabled = call.currentParams.isVideoEnabled
        isVideoEnabled.value = videoEnabled

        updateMediaEncryptionStats()
    }

    fun enable() {
        enabled = true
        call.addListener(listener)

        // Needed for media encryption stats
        updateMediaEncryptionStats()
    }

    fun disable() {
        enabled = false
        call.removeListener(listener)
    }

    override fun destroy() {
        if (enabled) disable()
        super.destroy()
    }

    private fun updateMediaEncryptionStats() {
        initCallStats()
    }

    private fun initCallStats() {
        val audioList = arrayListOf<StatItemData>()

        audioList.add(StatItemData(StatType.CAPTURE))
        audioList.add(StatItemData(StatType.PLAYBACK))
        audioList.add(StatItemData(StatType.PAYLOAD))
        audioList.add(StatItemData(StatType.ENCODER))
        audioList.add(StatItemData(StatType.DECODER))
        audioList.add(StatItemData(StatType.DOWNLOAD_BW))
        audioList.add(StatItemData(StatType.UPLOAD_BW))
        audioList.add(StatItemData(StatType.ICE))
        audioList.add(StatItemData(StatType.IP_FAM))
        audioList.add(StatItemData(StatType.SENDER_LOSS))
        audioList.add(StatItemData(StatType.RECEIVER_LOSS))
        audioList.add(StatItemData(StatType.JITTER))

        audioStats.value = audioList

        val mediaEncryptionList = arrayListOf<StatItemData>()

        mediaEncryptionList.add(StatItemData(StatType.MEDIA_ENCRYPTION))

        // ZRTP stats are only available when authentication token isn't null !
        if (call.currentParams.mediaEncryption == MediaEncryption.ZRTP && call.authenticationToken != null) {
            mediaEncryptionList.add(StatItemData(StatType.ZRTP_CIPHER_ALGO))
            mediaEncryptionList.add(StatItemData(StatType.ZRTP_KEY_AGREEMENT_ALGO))
            mediaEncryptionList.add(StatItemData(StatType.ZRTP_HASH_ALGO))
            mediaEncryptionList.add(StatItemData(StatType.ZRTP_AUTH_TAG_ALGO))
            mediaEncryptionList.add(StatItemData(StatType.ZRTP_AUTH_SAS_ALGO))
        }

        mediaEncryptionStats.value = mediaEncryptionList

        val videoList = arrayListOf<StatItemData>()

        videoList.add(StatItemData(StatType.CAPTURE))
        videoList.add(StatItemData(StatType.PLAYBACK))
        videoList.add(StatItemData(StatType.PAYLOAD))
        videoList.add(StatItemData(StatType.ENCODER))
        videoList.add(StatItemData(StatType.DECODER))
        videoList.add(StatItemData(StatType.DOWNLOAD_BW))
        videoList.add(StatItemData(StatType.UPLOAD_BW))
        videoList.add(StatItemData(StatType.ESTIMATED_AVAILABLE_DOWNLOAD_BW))
        videoList.add(StatItemData(StatType.ICE))
        videoList.add(StatItemData(StatType.IP_FAM))
        videoList.add(StatItemData(StatType.SENDER_LOSS))
        videoList.add(StatItemData(StatType.RECEIVER_LOSS))
        videoList.add(StatItemData(StatType.SENT_RESOLUTION))
        videoList.add(StatItemData(StatType.RECEIVED_RESOLUTION))
        videoList.add(StatItemData(StatType.SENT_FPS))
        videoList.add(StatItemData(StatType.RECEIVED_FPS))

        videoStats.value = videoList
    }

    private fun updateCallStats(stats: CallStats) {
        if (stats.type == StreamType.Audio) {
            for (stat in audioStats.value.orEmpty()) {
                stat.update(call, stats)
            }
            for (stat in mediaEncryptionStats.value.orEmpty()) {
                stat.update(call, stats)
            }
        } else if (stats.type == StreamType.Video) {
            for (stat in videoStats.value.orEmpty()) {
                stat.update(call, stats)
            }
        }
    }
}
