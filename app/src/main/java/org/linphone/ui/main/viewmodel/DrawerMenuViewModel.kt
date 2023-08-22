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

import android.view.View
import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.core.Account
import org.linphone.core.tools.Log
import org.linphone.ui.main.model.AccountModel
import org.linphone.utils.Event

class DrawerMenuViewModel : ViewModel() {
    companion object {
        const val TAG = "[Drawer Menu ViewModel]"
    }

    val accounts = MutableLiveData<ArrayList<AccountModel>>()

    val startAssistantEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val closeDrawerEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val showAccountPopupMenuEvent: MutableLiveData<Event<Pair<View, Account>>> by lazy {
        MutableLiveData<Event<Pair<View, Account>>>()
    }

    init {
        // TODO FIXME: update accounts list when a new account is added or when removing one
        coreContext.postOnCoreThread {
            computeAccountsList()
        }
    }

    @UiThread
    override fun onCleared() {
        super.onCleared()
    }

    @UiThread
    fun closeDrawerMenu() {
        closeDrawerEvent.value = Event(true)
    }

    @UiThread
    fun addAccount() {
        startAssistantEvent.value = Event(true)
    }

    @WorkerThread
    private fun computeAccountsList() {
        Log.i("$TAG Updating accounts list")
        accounts.value.orEmpty().forEach(AccountModel::destroy)

        val list = arrayListOf<AccountModel>()
        for (account in coreContext.core.accountList) {
            val model = AccountModel(account) { view, account ->
                showAccountPopupMenuEvent.postValue(Event(Pair(view, account)))
            }
            list.add(model)
        }
        accounts.postValue(list)
    }
}
