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
package org.linphone.ui.main.contacts.viewmodel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.core.Address
import org.linphone.ui.main.contacts.model.ContactAvatarModel
import org.linphone.ui.main.contacts.model.ContactDeviceModel
import org.linphone.ui.main.contacts.model.ContactNumberOrAddressClickListener
import org.linphone.ui.main.contacts.model.ContactNumberOrAddressModel
import org.linphone.utils.Event

class ContactViewModel : ViewModel() {
    val contact = MutableLiveData<ContactAvatarModel>()

    val sipAddressesAndPhoneNumbers = MutableLiveData<ArrayList<ContactNumberOrAddressModel>>()

    val devices = MutableLiveData<ArrayList<ContactDeviceModel>>()

    val company = MutableLiveData<String>()

    val showBackButton = MutableLiveData<Boolean>()

    val showNumbersAndAddresses = MutableLiveData<Boolean>()

    val showCompany = MutableLiveData<Boolean>()

    val showDevicesTrust = MutableLiveData<Boolean>()

    val contactFoundEvent = MutableLiveData<Event<Boolean>>()

    val showLongPressMenuForNumberOrAddressEvent: MutableLiveData<Event<ContactNumberOrAddressModel>> by lazy {
        MutableLiveData<Event<ContactNumberOrAddressModel>>()
    }

    val showNumberOrAddressPickerDialogEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val listener = object : ContactNumberOrAddressClickListener {
        override fun onClicked(address: Address?) {
            // UI thread
            if (address != null) {
                coreContext.postOnCoreThread {
                    coreContext.startCall(address)
                }
            }
        }

        override fun onLongPress(model: ContactNumberOrAddressModel) {
            // UI thread
            showLongPressMenuForNumberOrAddressEvent.value = Event(model)
        }
    }

    init {
        showNumbersAndAddresses.value = true
        showDevicesTrust.value = true
        showCompany.value = false
    }

    fun findContactByRefKey(refKey: String) {
        // UI thread
        coreContext.postOnCoreThread { core ->
            val friend = coreContext.contactsManager.findContactById(refKey)
            if (friend != null) {
                contact.postValue(ContactAvatarModel(friend))
                val organization = friend.organization
                if (!organization.isNullOrEmpty()) {
                    company.postValue(organization)
                    showCompany.postValue(true)
                }

                val addressesAndNumbers = arrayListOf<ContactNumberOrAddressModel>()
                for (address in friend.addresses) {
                    val data = ContactNumberOrAddressModel(
                        address,
                        address.asStringUriOnly(),
                        listener,
                        true
                    )
                    addressesAndNumbers.add(data)
                }
                for (number in friend.phoneNumbersWithLabel) {
                    val address = core.interpretUrl(number.phoneNumber, true)
                    val data = ContactNumberOrAddressModel(
                        address,
                        number.phoneNumber,
                        listener,
                        false,
                        label = number.label.orEmpty()
                    )
                    addressesAndNumbers.add(data)
                }
                sipAddressesAndPhoneNumbers.postValue(addressesAndNumbers)

                val devicesList = arrayListOf<ContactDeviceModel>()
                // TODO FIXME
                devicesList.add(ContactDeviceModel("Pixel 6 Pro de Sylvain", true))
                devicesList.add(ContactDeviceModel("Sylvain Galaxy Tab S9 Pro+ Ultra", true))
                devicesList.add(ContactDeviceModel("MacBook Pro de Marcel", false))
                devicesList.add(ContactDeviceModel("sylvain@fedora-linux-38", true))
                devices.postValue(devicesList)

                contactFoundEvent.postValue(Event(true))
            }
        }
    }

    fun toggleNumbersAndAddressesVisibility() {
        showNumbersAndAddresses.value = showNumbersAndAddresses.value == false
    }

    fun toggleDevicesTrustVisibility() {
        showDevicesTrust.value = showDevicesTrust.value == false
    }

    fun startAudioCall() {
        val numbersAndAddresses = sipAddressesAndPhoneNumbers.value.orEmpty()
        if (numbersAndAddresses.size == 1) {
            val address = numbersAndAddresses.first().address
            if (address != null) {
                coreContext.postOnCoreThread { core ->
                    val params = core.createCallParams(null)
                    params?.isVideoEnabled = false
                    coreContext.startCall(address, params)
                }
            }
        } else {
            showNumberOrAddressPickerDialogEvent.value = Event(true)
        }
    }

    fun startVideoCall() {
        val numbersAndAddresses = sipAddressesAndPhoneNumbers.value.orEmpty()
        if (numbersAndAddresses.size == 1) {
            val address = numbersAndAddresses.first().address
            if (address != null) {
                coreContext.postOnCoreThread { core ->
                    val params = core.createCallParams(null)
                    params?.isVideoEnabled = true
                    coreContext.startCall(address, params)
                }
            }
        } else {
            showNumberOrAddressPickerDialogEvent.value = Event(true)
        }
    }

    fun sendMessage() {
        if (sipAddressesAndPhoneNumbers.value.orEmpty().size == 1) {
            // TODO
        } else {
            showNumberOrAddressPickerDialogEvent.value = Event(true)
        }
    }
}
