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
package com.naminfo.ui.main.viewmodel

import androidx.annotation.UiThread
import androidx.lifecycle.MutableLiveData
import com.naminfo.LinphoneApplication.Companion.coreContext
import com.naminfo.R
import org.linphone.core.Address
import org.linphone.core.tools.Log
import com.naminfo.ui.main.model.SelectedAddressModel
import com.naminfo.utils.AppUtils
import com.naminfo.utils.Event

class AddParticipantsViewModel
    @UiThread
    constructor() : AddressSelectionViewModel() {
    companion object {
        private const val TAG = "[Add Participants ViewModel]"
    }

    val selectedSipUrisEvent = MutableLiveData<Event<ArrayList<String>>>()

    init {
        Log.i("$TAG Forcing multiple selection mode")
        switchToMultipleSelectionMode()
    }

    @UiThread
    fun isSelectionEmpty(): Boolean {
        return selection.value.orEmpty().isEmpty()
    }

    @UiThread
    fun addSelectedParticipants(participants: Array<String>) {
        coreContext.postOnCoreThread { core ->
            Log.i("$TAG Adding [${participants.size}] pre-selected participants")
            val list = arrayListOf<SelectedAddressModel>()
            val addresses = arrayListOf<Address>()

            for (uri in participants) {
                val address = core.interpretUrl(uri, false)
                if (address == null) {
                    Log.e("$TAG Failed to parse participant URI [$uri] as address!")
                    continue
                }
                addresses.add(address)

                val avatarModel = coreContext.contactsManager.getContactAvatarModelForAddress(
                    address
                )
                val model = SelectedAddressModel(address, avatarModel) {
                    removeAddressModelFromSelection(it)
                }
                list.add(model)
            }

            selectionCount.postValue(
                AppUtils.getStringWithPlural(
                    R.plurals.selection_count_label,
                    list.size,
                    list.size.toString()
                )
            )
            selection.postValue(list)
            updateSelectedParticipants(addresses)
        }
    }

    @UiThread
    fun addParticipants() {
        val selected = selection.value.orEmpty()
        Log.i("$TAG [${selected.size}] participants selected")

        coreContext.postOnCoreThread {
            val list = arrayListOf<String>()
            for (model in selected) {
                list.add(model.address.asStringUriOnly())
            }

            selectedSipUrisEvent.postValue(Event(list))
        }
    }
}
