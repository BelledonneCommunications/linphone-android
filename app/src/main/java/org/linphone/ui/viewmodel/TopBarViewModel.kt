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
package org.linphone.ui.viewmodel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import org.linphone.utils.Event

abstract class TopBarViewModel : ViewModel() {
    val title = MutableLiveData<String>()

    val searchBarVisible = MutableLiveData<Boolean>()

    val searchFilter = MutableLiveData<String>()

    val focusSearchBarEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val bottomNavBarVisible = MutableLiveData<Boolean>()

    init {
        searchBarVisible.value = false
    }

    override fun onCleared() {
        super.onCleared()
    }

    fun openSearchBar() {
        // UI thread
        searchBarVisible.value = true
        focusSearchBarEvent.value = Event(true)
    }

    fun closeSearchBar() {
        // UI thread
        searchBarVisible.value = false
        focusSearchBarEvent.value = Event(false)
    }

    fun clearFilter() {
        // UI thread
        searchFilter.value = ""
    }
}
