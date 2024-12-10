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
package org.linphone.ui.main.contacts.model

import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.lifecycle.MutableLiveData

class NewOrEditNumberOrAddressModel
    @WorkerThread
    constructor(
    defaultValue: String,
    val isSip: Boolean,
    val label: String? = "",
    private val onValueNoLongerEmpty: (() -> Unit)? = null,
    private val onRemove: ((model: NewOrEditNumberOrAddressModel) -> Unit)? = null
) {
    val value = MutableLiveData<String>()

    val showRemoveButton = MutableLiveData<Boolean>()

    init {
        value.postValue(defaultValue)
        showRemoveButton.postValue(defaultValue.isNotEmpty())
    }

    @UiThread
    fun onValueChanged(newValue: String) {
        if (newValue.isNotEmpty() && showRemoveButton.value == false) {
            onValueNoLongerEmpty?.invoke()
            showRemoveButton.value = true
        }
    }

    @UiThread
    fun remove() {
        onRemove?.invoke(this)
    }
}
