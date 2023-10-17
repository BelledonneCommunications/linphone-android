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
import androidx.annotation.WorkerThread
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import org.linphone.R
import org.linphone.mediastream.Log
import org.linphone.ui.main.model.SelectedAddressModel
import org.linphone.utils.AppUtils

abstract class AddressSelectionViewModel @UiThread constructor() : ViewModel() {
    companion object {
        private const val TAG = "[Address Selection ViewModel]"
    }

    val multipleSelectionMode = MutableLiveData<Boolean>()

    val selection = MutableLiveData<ArrayList<SelectedAddressModel>>()

    val selectionCount = MutableLiveData<String>()

    init {
        multipleSelectionMode.value = false
    }

    @UiThread
    fun switchToMultipleSelectionMode() {
        Log.i("$$TAG Multiple selection mode ON")
        multipleSelectionMode.value = true
    }

    @WorkerThread
    fun addAddressModelToSelection(model: SelectedAddressModel) {
        val actual = selection.value.orEmpty()
        if (actual.find {
            it.address.weakEqual(model.address)
        } == null
        ) {
            Log.i("$TAG Adding [${model.address.asStringUriOnly()}] address to selection")

            val list = arrayListOf<SelectedAddressModel>()
            list.addAll(actual)
            list.add(model)

            selectionCount.postValue(
                AppUtils.getStringWithPlural(
                    R.plurals.selection_count_label,
                    list.size,
                    list.size.toString()
                )
            )
            selection.postValue(list)
        } else {
            Log.w("$TAG Address is already in selection, doing nothing")
        }
    }

    @WorkerThread
    fun removeAddressModelFromSelection(model: SelectedAddressModel) {
        val actual = selection.value.orEmpty()
        if (actual.find {
            it.address.weakEqual(model.address)
        } != null
        ) {
            Log.i("$TAG Removing [${model.address.asStringUriOnly()}] address from selection")

            val list = arrayListOf<SelectedAddressModel>()
            list.addAll(actual)
            model.avatarModel?.destroy()
            list.remove(model)

            selectionCount.postValue(
                AppUtils.getStringWithPlural(
                    R.plurals.selection_count_label,
                    list.size,
                    list.size.toString()
                )
            )
            selection.postValue(list)
        } else {
            Log.w("$TAG Address isn't in selection, doing nothing")
        }
    }
}
