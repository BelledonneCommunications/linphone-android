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
package org.linphone.ui.main.viewmodel

import androidx.annotation.UiThread
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.ui.main.model.AccountModel
import org.linphone.utils.Event

class TopBarViewModel : ViewModel() {
    val title = MutableLiveData<String>()

    val account = MutableLiveData<AccountModel>()

    val searchBarVisible = MutableLiveData<Boolean>()

    val searchFilter = MutableLiveData<String>()

    val focusSearchBarEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val openDrawerMenuEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    init {
        searchBarVisible.value = false

        coreContext.postOnCoreThread { core ->
            if (core.accountList.isNotEmpty()) {
                val defaultAccount = core.defaultAccount ?: core.accountList.first()
                account.postValue(AccountModel(defaultAccount))
            }
        }
    }

    @UiThread
    override fun onCleared() {
        super.onCleared()

        coreContext.postOnCoreThread {
            account.value?.destroy()
        }
    }

    @UiThread
    fun openDrawerMenu() {
        openDrawerMenuEvent.value = Event(true)
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
}
