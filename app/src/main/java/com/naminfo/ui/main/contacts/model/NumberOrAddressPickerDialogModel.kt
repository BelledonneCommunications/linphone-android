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
package com.naminfo.ui.main.contacts.model

import androidx.annotation.UiThread
import androidx.lifecycle.MutableLiveData
import com.naminfo.utils.Event

class NumberOrAddressPickerDialogModel
    @UiThread
    constructor(
    list: List<ContactNumberOrAddressModel>
) {
    val sipAddressesAndPhoneNumbers = MutableLiveData<List<ContactNumberOrAddressModel>>()

    val dismissEvent = MutableLiveData<Event<Boolean>>()

    init {
        for (model in list) {
            model.setActionDoneCallback {
                dismiss()
            }
        }
        sipAddressesAndPhoneNumbers.value = list
    }

    @UiThread
    fun dismiss() {
        dismissEvent.value = Event(true)
    }
}
