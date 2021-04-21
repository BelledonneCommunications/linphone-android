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
package org.linphone.contact

import androidx.lifecycle.MutableLiveData
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.activities.main.viewmodels.ErrorReportingViewModel
import org.linphone.core.Address
import org.linphone.utils.LinphoneUtils

abstract class GenericContactViewModel(private val sipAddress: Address) : ErrorReportingViewModel(), ContactDataInterface {
    override val displayName: String = LinphoneUtils.getDisplayName(sipAddress)

    override val contact = MutableLiveData<Contact>()

    private val contactsUpdatedListener = object : ContactsUpdatedListenerStub() {
        override fun onContactUpdated(contact: Contact) {
            contactLookup()
        }
    }

    init {
        coreContext.contactsManager.addListener(contactsUpdatedListener)
        contactLookup()
    }

    override fun onCleared() {
        coreContext.contactsManager.removeListener(contactsUpdatedListener)

        super.onCleared()
    }

    private fun contactLookup() {
        contact.value = coreContext.contactsManager.findContactByAddress(sipAddress)
    }
}
