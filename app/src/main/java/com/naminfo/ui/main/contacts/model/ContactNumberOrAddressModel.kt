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
package com.naminfo.ui.main.contacts.model

import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.lifecycle.MutableLiveData
import org.linphone.core.Address
import org.linphone.core.Friend

class ContactNumberOrAddressModel
    @WorkerThread
    constructor(
    val friend: Friend,
    val address: Address?,
    val displayedValue: String,
    val isEnabled: Boolean,
    private val listener: ContactNumberOrAddressClickListener,
    val isSip: Boolean = true,
    val label: String = "",
    val hasPresence: Boolean = true
) {
    val selected = MutableLiveData<Boolean>()

    private var actionDoneCallback: (() -> Unit)? = null

    @UiThread
    fun setActionDoneCallback(lambda: () -> Unit) {
        actionDoneCallback = lambda
    }

    @UiThread
    fun onClicked() {
        listener.onClicked(this)
        actionDoneCallback?.invoke()
    }

    @UiThread
    fun onLongPress(): Boolean {
        selected.value = true
        listener.onLongPress(this)
        actionDoneCallback?.invoke()
        return true
    }
}

interface ContactNumberOrAddressClickListener {
    fun onClicked(model: ContactNumberOrAddressModel)

    fun onLongPress(model: ContactNumberOrAddressModel)
}
