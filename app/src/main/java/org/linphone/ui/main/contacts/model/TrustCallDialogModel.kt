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
import androidx.lifecycle.MutableLiveData
import org.linphone.utils.AppUtils
import org.linphone.utils.Event

class TrustCallDialogModel
    @UiThread
    constructor(contact: String, device: String) {
    val message = MutableLiveData<String>()

    val doNotShowAnymore = MutableLiveData<Boolean>()

    val dismissEvent = MutableLiveData<Event<Boolean>>()

    val confirmCallEvent = MutableLiveData<Event<Boolean>>()

    init {
        message.value = AppUtils.getFormattedString(
            org.linphone.R.string.contact_dialog_increase_trust_level_message,
            contact,
            device
        )
    }

    @UiThread
    fun dismiss() {
        dismissEvent.value = Event(true)
    }

    @UiThread
    fun confirmCall() {
        confirmCallEvent.value = Event(true)
    }
}
