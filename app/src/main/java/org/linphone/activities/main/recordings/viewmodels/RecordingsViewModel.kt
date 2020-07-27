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
import kotlin.collections.ArrayList
import org.linphone.core.tools.Log
import org.linphone.utils.FileUtils

class RecordingsViewModel : ViewModel() {
    val recordingsList = MutableLiveData<ArrayList<RecordingViewModel>>()

    val isVideoVisible = MutableLiveData<Boolean>()

    init {
        getRecordings()
        isVideoVisible.value = false
    }

    fun deleteRecordings(list: ArrayList<RecordingViewModel>) {
        for (recording in list) {
            Log.i("[Recordings] Deleting recording ${recording.path}")
            FileUtils.deleteFile(recording.path)
        }
        getRecordings()
    }

    private fun getRecordings() {
        val list = arrayListOf<RecordingViewModel>()

        for (f in FileUtils.getFileStorageDir().listFiles().orEmpty()) {
            Log.i("[Recordings] Found file ${f.path}")
            if (RecordingViewModel.RECORD_PATTERN.matcher(f.path).matches()) {
                list.add(
                    RecordingViewModel(
                        f.path
                    )
                )
                Log.i("[Recordings] Found record ${f.path}")
            }
        }

        list.sort()
        recordingsList.value = list
    }
}
