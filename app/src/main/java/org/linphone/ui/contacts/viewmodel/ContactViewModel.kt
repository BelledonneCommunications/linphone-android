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
package org.linphone.ui.contacts.viewmodel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.core.Address
import org.linphone.ui.contacts.ContactNumberOrAddressClickListener
import org.linphone.ui.contacts.ContactNumberOrAddressData
import org.linphone.ui.contacts.model.ContactModel
import org.linphone.utils.Event

class ContactViewModel : ViewModel() {
    val contact = MutableLiveData<ContactModel>()

    val sipAddressesAndPhoneNumbers = MutableLiveData<ArrayList<ContactNumberOrAddressData>>()

    val company = MutableLiveData<String>()

    val showBackButton = MutableLiveData<Boolean>()

    val showNumbersAndAddresses = MutableLiveData<Boolean>()

    val showCompany = MutableLiveData<Boolean>()

    val showDevicesTrust = MutableLiveData<Boolean>()

    val friendFoundEvent = MutableLiveData<Event<Boolean>>()

    val listener = object : ContactNumberOrAddressClickListener {
        override fun onCall(address: Address) {
            // UI thread
        }

        override fun onVideoCall(address: Address) {
            // UI thread
        }

        override fun onChat(address: Address) {
            // UI thread
        }
    }

    init {
        showNumbersAndAddresses.value = true
        showCompany.value = false
    }

    fun findContactByRefKey(refKey: String) {
        // UI thread
        coreContext.postOnCoreThread { core ->
            val friend = coreContext.contactsManager.findContactById(refKey)
            if (friend != null) {
                val organization = friend.organization
                if (!organization.isNullOrEmpty()) {
                    company.postValue(organization)
                    showCompany.postValue(true)
                }

                val addressesAndNumbers = arrayListOf<ContactNumberOrAddressData>()
                for (address in friend.addresses) {
                    val data = ContactNumberOrAddressData(
                        address,
                        address.asStringUriOnly(),
                        listener,
                        true
                    )
                    addressesAndNumbers.add(data)
                }
                for (number in friend.phoneNumbersWithLabel) {
                    val address = core.interpretUrl(number.phoneNumber, true)
                    val data = ContactNumberOrAddressData(
                        address,
                        number.phoneNumber,
                        listener,
                        false,
                        label = number.label.orEmpty()
                    )
                    addressesAndNumbers.add(data)
                }
                sipAddressesAndPhoneNumbers.postValue(addressesAndNumbers)
                contact.postValue(ContactModel(friend))
            }
        }
    }

    fun toggleNumbersAndAddressesVisibility() {
        showNumbersAndAddresses.value = showNumbersAndAddresses.value == false
    }

    fun toggleDevicesTrustVisibility() {
        showDevicesTrust.value = showDevicesTrust.value == false
    }
}
