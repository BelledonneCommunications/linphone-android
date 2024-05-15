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
package org.linphone.ui.main.recordings.viewmodel

import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.lifecycle.MutableLiveData
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.core.tools.Log
import org.linphone.ui.GenericViewModel
import org.linphone.ui.main.recordings.model.RecordingModel
import org.linphone.utils.Event
import org.linphone.utils.FileUtils

class RecordingsListViewModel @UiThread constructor() : GenericViewModel() {
    companion object {
        private const val TAG = "[Recordings List ViewModel]"
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

    override fun onCleared() {
        recordings.value.orEmpty().forEach(RecordingModel::destroy)
        super.onCleared()
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
        searchFilter.value = ""
    }

    @UiThread
    fun applyFilter(filter: String) {
        coreContext.postOnCoreThread {
            computeList(filter)
        }
    }

    @WorkerThread
    private fun computeList(filter: String) {
        recordings.value.orEmpty().forEach(RecordingModel::destroy)
        val list = arrayListOf<RecordingModel>()

        // TODO FIXME: also load recordings from previous Linphone versions
        for (file in FileUtils.getFileStorageDir(isRecording = true).listFiles().orEmpty()) {
            val path = file.path
            val name = file.name
            Log.i("$TAG Found file $path")
            list.add(RecordingModel(path, name))
        }

        list.sortBy {
            it.filePath // TODO FIXME
        }
        recordings.postValue(list)
    }
}
