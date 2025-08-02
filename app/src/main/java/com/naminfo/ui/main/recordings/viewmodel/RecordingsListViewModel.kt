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
package com.naminfo.ui.main.recordings.viewmodel

import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.lifecycle.MutableLiveData
import java.util.regex.Pattern
import com.naminfo.LinphoneApplication.Companion.coreContext
import org.linphone.core.tools.Log
import com.naminfo.ui.GenericViewModel
import com.naminfo.ui.main.recordings.model.RecordingModel
import com.naminfo.utils.Event
import com.naminfo.utils.FileUtils

class RecordingsListViewModel
    @UiThread
    constructor() : GenericViewModel() {
    companion object {
        private const val TAG = "[Recordings List ViewModel]"

        private val LEGACY_RECORD_PATTERN: Pattern =
            Pattern.compile(".*/(.*)_(\\d{2}-\\d{2}-\\d{4}-\\d{2}-\\d{2}-\\d{2})\\..*")
    }

    val recordings = MutableLiveData<ArrayList<RecordingModel>>()

    val searchBarVisible = MutableLiveData<Boolean>()

    val searchFilter = MutableLiveData<String>()

    val fetchInProgress = MutableLiveData<Boolean>()

    val focusSearchBarEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    init {
        searchBarVisible.value = false
        fetchInProgress.value = true

        coreContext.postOnCoreThread {
            computeList("")
        }
    }

    @UiThread
    fun openSearchBar() {
        searchBarVisible.value = true
        focusSearchBarEvent.value = Event(true)
    }

    @UiThread
    fun closeSearchBar() {
        clearFilter()
        searchBarVisible.value = false
        focusSearchBarEvent.value = Event(false)
    }

    @UiThread
    fun clearFilter() {
        if (searchFilter.value.orEmpty().isEmpty()) {
            searchBarVisible.value = false
            focusSearchBarEvent.value = Event(false)
        } else {
            searchFilter.value = ""
        }
    }

    @UiThread
    fun applyFilter(filter: String) {
        coreContext.postOnCoreThread {
            computeList(filter)
        }
    }

    @WorkerThread
    private fun computeList(filter: String) {
        val list = arrayListOf<RecordingModel>()

        for (file in FileUtils.getFileStorageDir(isRecording = true).listFiles().orEmpty()) {
            val path = file.path
            val name = file.name

            Log.d("$TAG Found file $path")
            val model = RecordingModel(path, name)

            if (filter.isEmpty() || model.sipUri.contains(filter)) {
                Log.i("$TAG Added file $path")
                list.add(model)
            }
        }
        // Legacy path where to find recordings
        for (file in FileUtils.getFileStorageDir().listFiles().orEmpty()) {
            val path = file.path
            val name = file.name

            if (LEGACY_RECORD_PATTERN.matcher(path).matches()) {
                Log.d("$TAG Found legacy file $path")
                val model = RecordingModel(path, name, true)

                if (filter.isEmpty() || model.sipUri.contains(filter)) {
                    Log.i("$TAG Added legacy file $path")
                    list.add(model)
                }
            }
        }

        list.sortByDescending {
            it.timestamp
        }
        recordings.postValue(list)
    }
}
