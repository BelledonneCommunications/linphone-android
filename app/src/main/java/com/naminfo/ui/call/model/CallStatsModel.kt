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
package com.naminfo.ui.call.model

import androidx.annotation.WorkerThread
import androidx.lifecycle.MutableLiveData
import kotlin.math.roundToInt
import com.naminfo.R
import org.linphone.core.Call
import org.linphone.core.CallStats
import org.linphone.core.MediaDirection
import org.linphone.core.StreamType
import com.naminfo.utils.AppUtils

class CallStatsModel
    @WorkerThread
    constructor() {
    val audioCodec = MutableLiveData<String>()
    val audioBandwidth = MutableLiveData<String>()
    val lossRate = MutableLiveData<String>()
    val jitterBuffer = MutableLiveData<String>()

    val isVideoEnabled = MutableLiveData<Boolean>()
    val videoCodec = MutableLiveData<String>()
    val videoBandwidth = MutableLiveData<String>()
    val videoLossRate = MutableLiveData<String>()
    val videoResolution = MutableLiveData<String>()
    val videoFps = MutableLiveData<String>()

    val fecEnabled = MutableLiveData<Boolean>()
    val lostPackets = MutableLiveData<String>()
    val repairedPackets = MutableLiveData<String>()
    val fecBandwidth = MutableLiveData<String>()

    @WorkerThread
    fun update(call: Call, stats: CallStats?) {
        stats ?: return

        val videoEnabled = call.currentParams.isVideoEnabled
        val remoteParamsVideoDirection = call.remoteParams?.videoDirection
        val remoteSendsVideo = remoteParamsVideoDirection == MediaDirection.SendRecv || remoteParamsVideoDirection == MediaDirection.SendOnly
        val localParamsVideoDirection = call.params.videoDirection
        val localSendsVideo = localParamsVideoDirection == MediaDirection.SendRecv || localParamsVideoDirection == MediaDirection.SendOnly
        val showVideoStats = videoEnabled && (remoteSendsVideo || localSendsVideo)
        isVideoEnabled.postValue(showVideoStats)

        val isFecEnabled = call.currentParams.isFecEnabled
        fecEnabled.postValue(showVideoStats && isFecEnabled)

        when (stats.type) {
            StreamType.Audio -> {
                val payloadType = call.currentParams.usedAudioPayloadType
                val clockRate = (payloadType?.clockRate ?: 0) / 1000
                val codecLabel = AppUtils.getFormattedString(
                    R.string.call_stats_codec_label,
                    "${payloadType?.mimeType}/$clockRate kHz"
                )
                audioCodec.postValue(codecLabel)

                val uploadBandwidth = stats.uploadBandwidth.roundToInt()
                val downloadBandwidth = stats.downloadBandwidth.roundToInt()
                val bandwidthLabel = AppUtils.getFormattedString(
                    R.string.call_stats_bandwidth_label,
                    "↑ $uploadBandwidth kbits/s ↓ $downloadBandwidth kbits/s"
                )
                audioBandwidth.postValue(bandwidthLabel)

                val uploadLoss = stats.senderLossRate.roundToInt()
                val downloadLoss = stats.receiverLossRate.roundToInt()
                val lossRateLabel = AppUtils.getFormattedString(
                    R.string.call_stats_loss_rate_label,
                    "↑ $uploadLoss% ↓ $downloadLoss%"
                )
                lossRate.postValue(lossRateLabel)

                val jitterBufferSize = stats.jitterBufferSizeMs.roundToInt()
                val jitterBufferLabel = AppUtils.getFormattedString(
                    R.string.call_stats_jitter_buffer_label,
                    "$jitterBufferSize ms"
                )
                jitterBuffer.postValue(jitterBufferLabel)
            }
            StreamType.Video -> {
                val payloadType = call.currentParams.usedVideoPayloadType
                val clockRate = (payloadType?.clockRate ?: 0) / 1000
                val codecLabel = AppUtils.getFormattedString(
                    R.string.call_stats_codec_label,
                    "${payloadType?.mimeType}/$clockRate kHz"
                )
                videoCodec.postValue(codecLabel)

                val uploadBandwidth = stats.uploadBandwidth.roundToInt()
                val downloadBandwidth = stats.downloadBandwidth.roundToInt()
                val bandwidthLabel = AppUtils.getFormattedString(
                    R.string.call_stats_bandwidth_label,
                    "↑ $uploadBandwidth kbits/s ↓ $downloadBandwidth kbits/s"
                )
                videoBandwidth.postValue(bandwidthLabel)

                val uploadLoss = stats.senderLossRate.roundToInt()
                val downloadLoss = stats.receiverLossRate.roundToInt()
                val lossRateLabel = AppUtils.getFormattedString(
                    R.string.call_stats_loss_rate_label,
                    "↑ $uploadLoss% ↓ $downloadLoss%"
                )
                videoLossRate.postValue(lossRateLabel)

                val sentResolution = call.currentParams.sentVideoDefinition?.name
                val receivedResolution = call.currentParams.receivedVideoDefinition?.name
                val resolutionLabel = AppUtils.getFormattedString(
                    R.string.call_stats_resolution_label,
                    "↑ $sentResolution ↓ $receivedResolution"
                )
                videoResolution.postValue(resolutionLabel)

                val sentFps = call.currentParams.sentFramerate.roundToInt()
                val receivedFps = call.currentParams.receivedFramerate.roundToInt()
                val fpsLabel = AppUtils.getFormattedString(
                    R.string.call_stats_fps_label,
                    "↑ $sentFps ↓ $receivedFps"
                )
                videoFps.postValue(fpsLabel)

                if (isFecEnabled) {
                    val lostPacketsValue = stats.fecCumulativeLostPacketsNumber
                    val lostPacketsLabel = AppUtils.getFormattedString(
                        R.string.call_stats_fec_lost_packets_label,
                        lostPacketsValue
                    )
                    lostPackets.postValue(lostPacketsLabel)

                    val repairedPacketsValue = stats.fecRepairedPacketsNumber
                    val repairedPacketsLabel = AppUtils.getFormattedString(
                        R.string.call_stats_fec_repaired_packets_label,
                        repairedPacketsValue
                    )
                    repairedPackets.postValue(repairedPacketsLabel)

                    val fecUploadBandwidth = stats.fecUploadBandwidth.roundToInt()
                    val fecDownloadBandwidth = stats.fecDownloadBandwidth.roundToInt()
                    val fecBandwidthLabel = AppUtils.getFormattedString(
                        R.string.call_stats_fec_lost_bandwidth_label,
                        "↑ $fecUploadBandwidth kbits/s ↓ $fecDownloadBandwidth kbits/s"
                    )
                    fecBandwidth.postValue(fecBandwidthLabel)
                }
            }
            else -> {}
        }
    }
}
