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
import java.text.DecimalFormat
import org.linphone.R
import org.linphone.core.*
import org.linphone.utils.AppUtils

enum class StatType(val nameResource: Int) {
    CAPTURE(R.string.call_stats_capture_filter),
    PLAYBACK(R.string.call_stats_player_filter),
    PAYLOAD(R.string.call_stats_codec),
    ENCODER(R.string.call_stats_encoder_name),
    DECODER(R.string.call_stats_decoder_name),
    DOWNLOAD_BW(R.string.call_stats_download),
    UPLOAD_BW(R.string.call_stats_upload),
    ICE(R.string.call_stats_ice),
    IP_FAM(R.string.call_stats_ip),
    SENDER_LOSS(R.string.call_stats_sender_loss_rate),
    RECEIVER_LOSS(R.string.call_stats_receiver_loss_rate),
    JITTER(R.string.call_stats_jitter_buffer),
    SENT_RESOLUTION(R.string.call_stats_video_resolution_sent),
    RECEIVED_RESOLUTION(R.string.call_stats_video_resolution_received),
    SENT_FPS(R.string.call_stats_video_fps_sent),
    RECEIVED_FPS(R.string.call_stats_video_fps_received),
    ESTIMATED_AVAILABLE_DOWNLOAD_BW(R.string.call_stats_estimated_download),
    MEDIA_ENCRYPTION(R.string.call_stats_media_encryption),
    ZRTP_CIPHER_ALGO(R.string.call_stats_zrtp_cipher_algo),
    ZRTP_KEY_AGREEMENT_ALGO(R.string.call_stats_zrtp_key_agreement_algo),
    ZRTP_HASH_ALGO(R.string.call_stats_zrtp_hash_algo),
    ZRTP_AUTH_TAG_ALGO(R.string.call_stats_zrtp_auth_tag_algo),
    ZRTP_AUTH_SAS_ALGO(R.string.call_stats_zrtp_sas_algo)
}

class StatItemData(val type: StatType) {
    companion object {
        fun audioDeviceToString(device: AudioDevice?): String {
            if (device == null) return "null"
            return "${device.deviceName} [${device.type}] (${device.driverName})"
        }
    }

    val value = MutableLiveData<String>()

    fun update(call: Call, stats: CallStats) {
        val payloadType = if (stats.type == StreamType.Audio) call.currentParams.usedAudioPayloadType else call.currentParams.usedVideoPayloadType
        payloadType ?: return
        value.value = when (type) {
            StatType.CAPTURE -> if (stats.type == StreamType.Audio) audioDeviceToString(call.inputAudioDevice) else call.core.videoDevice
            StatType.PLAYBACK -> if (stats.type == StreamType.Audio) audioDeviceToString(call.outputAudioDevice) else call.core.videoDisplayFilter
            StatType.PAYLOAD -> "${payloadType.mimeType}/${payloadType.clockRate / 1000} kHz"
            StatType.ENCODER -> call.core.mediastreamerFactory.getDecoderText(payloadType.mimeType)
            StatType.DECODER -> call.core.mediastreamerFactory.getEncoderText(payloadType.mimeType)
            StatType.DOWNLOAD_BW -> "${stats.downloadBandwidth} kbits/s"
            StatType.UPLOAD_BW -> "${stats.uploadBandwidth} kbits/s"
            StatType.ICE -> stats.iceState.toString()
            StatType.IP_FAM -> if (stats.ipFamilyOfRemote == AddressFamily.Inet6) "IPv6" else "IPv4"
            StatType.SENDER_LOSS -> DecimalFormat("##.##%").format(stats.senderLossRate)
            StatType.RECEIVER_LOSS -> DecimalFormat("##.##%").format(stats.receiverLossRate)
            StatType.JITTER -> DecimalFormat("##.## ms").format(stats.jitterBufferSizeMs)
            StatType.SENT_RESOLUTION -> call.currentParams.sentVideoDefinition?.name
            StatType.RECEIVED_RESOLUTION -> call.currentParams.receivedVideoDefinition?.name
            StatType.SENT_FPS -> "${call.currentParams.sentFramerate}"
            StatType.RECEIVED_FPS -> "${call.currentParams.receivedFramerate}"
            StatType.ESTIMATED_AVAILABLE_DOWNLOAD_BW -> "${stats.estimatedDownloadBandwidth} kbit/s"
            StatType.MEDIA_ENCRYPTION -> {
                when (call.currentParams.mediaEncryption) {
                    MediaEncryption.ZRTP -> {
                        if (stats.isZrtpKeyAgreementAlgoPostQuantum) {
                            AppUtils.getString(R.string.call_settings_media_encryption_zrtp_post_quantum)
                        } else {
                            AppUtils.getString(R.string.call_settings_media_encryption_zrtp)
                        }
                    }
                    MediaEncryption.DTLS -> AppUtils.getString(R.string.call_settings_media_encryption_dtls)
                    MediaEncryption.SRTP -> AppUtils.getString(R.string.call_settings_media_encryption_srtp)
                    MediaEncryption.None -> AppUtils.getString(R.string.call_settings_media_encryption_none)
                    else -> "Unexpected!"
                }
            }
            StatType.ZRTP_CIPHER_ALGO -> stats.zrtpCipherAlgo
            StatType.ZRTP_KEY_AGREEMENT_ALGO -> stats.zrtpKeyAgreementAlgo
            StatType.ZRTP_HASH_ALGO -> stats.zrtpHashAlgo
            StatType.ZRTP_AUTH_TAG_ALGO -> stats.zrtpAuthTagAlgo
            StatType.ZRTP_AUTH_SAS_ALGO -> stats.zrtpSasAlgo
        }
    }
}
