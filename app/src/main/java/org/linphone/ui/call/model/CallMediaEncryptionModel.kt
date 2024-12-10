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
package org.linphone.ui.call.model

import androidx.annotation.WorkerThread
import androidx.lifecycle.MutableLiveData
import org.linphone.R
import org.linphone.core.Call
import org.linphone.core.MediaEncryption
import org.linphone.core.StreamType
import org.linphone.utils.AppUtils

class CallMediaEncryptionModel
    @WorkerThread
    constructor(
    private val showZrtpSasValidationDialog: () -> Unit
) {
    val mediaEncryption = MutableLiveData<String>()

    val isMediaEncryptionZrtp = MutableLiveData<Boolean>()
    val zrtpCipher = MutableLiveData<String>()
    val zrtpKeyAgreement = MutableLiveData<String>()
    val zrtpHash = MutableLiveData<String>()
    val zrtpAuthTag = MutableLiveData<String>()
    val zrtpAuthSas = MutableLiveData<String>()

    @WorkerThread
    fun update(call: Call) {
        isMediaEncryptionZrtp.postValue(false)

        val stats = call.getStats(StreamType.Audio)
        if (stats != null) {
            // ZRTP stats are only available when authentication token isn't null !
            if (call.currentParams.mediaEncryption == MediaEncryption.ZRTP && call.authenticationToken != null) {
                isMediaEncryptionZrtp.postValue(true)

                if (stats.isZrtpKeyAgreementAlgoPostQuantum) {
                    mediaEncryption.postValue(
                        AppUtils.getFormattedString(
                            R.string.call_stats_media_encryption,
                            AppUtils.getString(
                                R.string.call_stats_media_encryption_zrtp_post_quantum
                            )
                        )
                    )
                } else {
                    mediaEncryption.postValue(
                        AppUtils.getFormattedString(
                            R.string.call_stats_media_encryption,
                            call.currentParams.mediaEncryption.name
                        )
                    )
                }

                zrtpCipher.postValue(
                    AppUtils.getFormattedString(
                        R.string.call_stats_zrtp_cipher_algo,
                        stats.zrtpCipherAlgo
                    )
                )
                zrtpKeyAgreement.postValue(
                    AppUtils.getFormattedString(
                        R.string.call_stats_zrtp_key_agreement_algo,
                        stats.zrtpKeyAgreementAlgo
                    )
                )
                zrtpHash.postValue(
                    AppUtils.getFormattedString(
                        R.string.call_stats_zrtp_hash_algo,
                        stats.zrtpHashAlgo
                    )
                )
                zrtpAuthTag.postValue(
                    AppUtils.getFormattedString(
                        R.string.call_stats_zrtp_auth_tag_algo,
                        stats.zrtpAuthTagAlgo
                    )
                )
                zrtpAuthSas.postValue(
                    AppUtils.getFormattedString(
                        R.string.call_stats_zrtp_sas_algo,
                        stats.zrtpSasAlgo
                    )
                )
            } else {
                mediaEncryption.postValue(
                    AppUtils.getFormattedString(
                        R.string.call_stats_media_encryption,
                        call.currentParams.mediaEncryption.name
                    )
                )
            }
        }
    }

    fun showSasValidationDialog() {
        showZrtpSasValidationDialog.invoke()
    }
}
