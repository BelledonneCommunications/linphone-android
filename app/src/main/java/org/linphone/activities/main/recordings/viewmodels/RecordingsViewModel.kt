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
package org.linphone.activities.main.recordings.viewmodels

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.media.AudioFocusRequestCompat
import kotlin.collections.ArrayList
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.activities.main.recordings.data.RecordingData
import org.linphone.core.tools.Log
import org.linphone.utils.AppUtils
import org.linphone.utils.Event
import org.linphone.utils.FileUtils

class RecordingsViewModel : ViewModel() {
    val recordingsList = MutableLiveData<ArrayList<RecordingData>>()

    val isVideoVisible = MutableLiveData<Boolean>()

    val exportRecordingEvent: MutableLiveData<Event<String>> by lazy {
        MutableLiveData<Event<String>>()
    }

    private var recordingPlayingAudioFocusRequest: AudioFocusRequestCompat? = null

    private val recordingListener = object : RecordingData.RecordingListener {
        override fun onPlayingStarted(videoAvailable: Boolean) {
            if (recordingPlayingAudioFocusRequest == null) {
                recordingPlayingAudioFocusRequest = AppUtils.acquireAudioFocusForVoiceRecordingOrPlayback(
                    coreContext.context
                )
            }

            isVideoVisible.value = videoAvailable
        }

        override fun onPlayingEnded() {
            val request = recordingPlayingAudioFocusRequest
            if (request != null) {
                AppUtils.releaseAudioFocusForVoiceRecordingOrPlayback(coreContext.context, request)
                recordingPlayingAudioFocusRequest = null
            }

            isVideoVisible.value = false
        }

        override fun onExportClicked(path: String) {
            exportRecordingEvent.value = Event(path)
        }
    }

    init {
        isVideoVisible.value = false
    }

    override fun onCleared() {
        recordingsList.value.orEmpty().forEach(RecordingData::destroy)

        val request = recordingPlayingAudioFocusRequest
        if (request != null) {
            AppUtils.releaseAudioFocusForVoiceRecordingOrPlayback(coreContext.context, request)
            recordingPlayingAudioFocusRequest = null
        }

        super.onCleared()
    }

    fun deleteRecordings(list: ArrayList<RecordingData>) {
        for (recording in list) {
            // Hide video when removing a recording being played with video.
            if (recording.isPlaying.value == true && recording.isVideoAvailable()) {
                isVideoVisible.value = false
            }

            Log.i("[Recordings] Deleting recording ${recording.path}")
            FileUtils.deleteFile(recording.path)
        }

        updateRecordingsList()
    }

    fun updateRecordingsList() {
        recordingsList.value.orEmpty().forEach(RecordingData::destroy)
        val list = arrayListOf<RecordingData>()

        for (f in FileUtils.getFileStorageDir().listFiles().orEmpty()) {
            Log.d("[Recordings] Found file ${f.path}")
            if (RecordingData.RECORD_PATTERN.matcher(f.path).matches()) {
                list.add(
                    RecordingData(
                        f.path,
                        recordingListener
                    )
                )
                Log.i("[Recordings] Found record ${f.path}")
            }
        }

        list.sort()
        recordingsList.value = list
    }
}
