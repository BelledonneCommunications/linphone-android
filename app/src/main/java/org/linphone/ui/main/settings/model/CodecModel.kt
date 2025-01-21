/*
 * Copyright (c) 2010-2024 Belledonne Communications SARL.
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
package org.linphone.ui.main.settings.model

import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.lifecycle.MutableLiveData
import org.linphone.utils.AppUtils
import org.linphone.R

class CodecModel
    @WorkerThread
    constructor(
    val mimeType: String,
    clockRate: Int,
    channels: Int,
    recvFmtp: String?,
    isAudioCodec: Boolean,
    enabled: Boolean,
    val onEnabledChanged: ((enabled: Boolean) -> Unit)
) {
    val isEnabled = MutableLiveData<Boolean>()

    val subtitle = MutableLiveData<String>()

    init {
        isEnabled.postValue(enabled)
        if (isAudioCodec) {
            val monoStereo = if (channels > 1) {
                AppUtils.getString(R.string.settings_advanced_audio_codecs_stereo_subtitle)
            } else {
                AppUtils.getString(R.string.settings_advanced_audio_codecs_mono_subtitle)
            }
            subtitle.postValue("$clockRate Hz ($monoStereo)")
        } else {
            subtitle.postValue(recvFmtp.orEmpty())
        }
    }

    @UiThread
    fun toggleEnabled() {
        val newValue = isEnabled.value == false
        onEnabledChanged(newValue)
        isEnabled.postValue(newValue)
    }
}
