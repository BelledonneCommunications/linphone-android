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

import androidx.annotation.UiThread
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import java.util.Locale
import kotlinx.coroutines.launch
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.core.Address
import org.linphone.core.Friend
import org.linphone.core.tools.Log
import org.linphone.ui.main.contacts.model.ContactAvatarModel
import org.linphone.ui.main.contacts.model.ContactDeviceModel
import org.linphone.ui.main.contacts.model.ContactNumberOrAddressClickListener
import org.linphone.ui.main.contacts.model.ContactNumberOrAddressModel
import org.linphone.utils.Event
import org.linphone.utils.FileUtils

class ContactViewModel : ViewModel() {
    companion object {
        const val TAG = "[Contact ViewModel]"
    }

    val contact = MutableLiveData<ContactAvatarModel>()

    val sipAddressesAndPhoneNumbers = MutableLiveData<ArrayList<ContactNumberOrAddressModel>>()

    val devices = MutableLiveData<ArrayList<ContactDeviceModel>>()

    val company = MutableLiveData<String>()

    val title = MutableLiveData<String>()

    val isFavourite = MutableLiveData<Boolean>()

    val showBackButton = MutableLiveData<Boolean>()

    val showNumbersAndAddresses = MutableLiveData<Boolean>()

    val showDevicesTrust = MutableLiveData<Boolean>()

    val contactFoundEvent = MutableLiveData<Event<Boolean>>()

    val showLongPressMenuForNumberOrAddressEvent: MutableLiveData<Event<ContactNumberOrAddressModel>> by lazy {
        MutableLiveData<Event<ContactNumberOrAddressModel>>()
    }

    val showNumberOrAddressPickerDialogEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val openNativeContactEditor: MutableLiveData<Event<String>> by lazy {
        MutableLiveData<Event<String>>()
    }

    val openLinphoneContactEditor: MutableLiveData<Event<String>> by lazy {
        MutableLiveData<Event<String>>()
    }

    val vCardTerminatedEvent: MutableLiveData<Event<File>> by lazy {
        MutableLiveData<Event<File>>()
    }

    private val listener = object : ContactNumberOrAddressClickListener {
        @UiThread
        override fun onClicked(address: Address?) {
            if (address != null) {
                coreContext.postOnCoreThread {
                    coreContext.startCall(address)
                }
            }
        }

        @UiThread
        override fun onLongPress(model: ContactNumberOrAddressModel) {
            showLongPressMenuForNumberOrAddressEvent.value = Event(model)
        }
    }

    private lateinit var friend: Friend

    init {
        showNumbersAndAddresses.value = true
        showDevicesTrust.value = false // TODO FIXME: set it to true when it will work for real
    }

    @UiThread
    fun findContactByRefKey(refKey: String) {
        coreContext.postOnCoreThread { core ->
            val friend = coreContext.contactsManager.findContactById(refKey)
            if (friend != null) {
                this.friend = friend
                isFavourite.postValue(friend.starred)

                contact.postValue(ContactAvatarModel(friend))

                val organization = friend.organization
                if (!organization.isNullOrEmpty()) {
                    company.postValue(organization!!)
                }
                val jobTitle = friend.jobTitle
                if (!jobTitle.isNullOrEmpty()) {
                    title.postValue(jobTitle!!)
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
                val indexOfLastSipAddress = addressesAndNumbers.count()

                for (number in friend.phoneNumbersWithLabel) {
                    val presenceModel = friend.getPresenceModelForUriOrTel(number.phoneNumber)
                    if (presenceModel != null && !presenceModel.contact.isNullOrEmpty()) {
                        // Show linked SIP address if not already stored as-is
                        val contact = presenceModel.contact
                        val found = addressesAndNumbers.find {
                            it.displayedValue == contact
                        }
                        if (!contact.isNullOrEmpty() && found == null) {
                            val address = core.interpretUrl(contact, false)
                            if (address != null) {
                                address.clean() // To remove ;user=phone
                                val data = ContactNumberOrAddressModel(
                                    address,
                                    address.asStringUriOnly(),
                                    listener,
                                    true
                                )
                                addressesAndNumbers.add(indexOfLastSipAddress, data)
                            }
                        }
                    }

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

    @UiThread
    fun toggleNumbersAndAddressesVisibility() {
        showNumbersAndAddresses.value = showNumbersAndAddresses.value == false
    }

    @UiThread
    fun toggleDevicesTrustVisibility() {
        showDevicesTrust.value = showDevicesTrust.value == false
    }

    @UiThread
    fun editContact() {
        coreContext.postOnCoreThread {
            if (::friend.isInitialized) {
                val uri = friend.nativeUri
                if (uri != null) {
                    openNativeContactEditor.postValue(Event(uri))
                } else {
                    openLinphoneContactEditor.postValue(Event(contact.value?.id.orEmpty()))
                }
            }
        }
    }

    @UiThread
    fun exportContactAsVCard() {
        coreContext.postOnCoreThread {
            if (::friend.isInitialized) {
                val vCard = friend.vcard?.asVcard4String()
                if (!vCard.isNullOrEmpty()) {
                    Log.i("$TAG Friend has been successfully dumped as vCard string")
                    val fileName = friend.name.orEmpty().replace(" ", "_").toLowerCase(
                        Locale.getDefault()
                    )
                    val file = FileUtils.getFileStorageCacheDir("$fileName.vcf")
                    viewModelScope.launch {
                        if (FileUtils.dumpStringToFile(vCard, file)) {
                            Log.i("$TAG vCard string saved as file in cache folder")
                            vCardTerminatedEvent.postValue(Event(file))
                        } else {
                            Log.e("$TAG Failed to save vCard string as file in cache folder")
                        }
                    }
                } else {
                    Log.e("$TAG Failed to dump contact as vCard string")
                }
            }
        }
    }

    @UiThread
    fun deleteContact() {
        coreContext.postOnCoreThread { core ->
            if (::friend.isInitialized) {
                Log.i("$TAG Deleting friend [$friend]")
                friend.remove()
                coreContext.contactsManager.notifyContactsListChanged()
            }
        }
    }

    @UiThread
    fun toggleFavourite() {
        coreContext.postOnCoreThread {
            friend.edit()
            friend.starred = !friend.starred
            friend.done()
            isFavourite.postValue(friend.starred)
            coreContext.contactsManager.notifyContactsListChanged()
        }
    }

    @UiThread
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

    @UiThread
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

    @UiThread
    fun sendMessage() {
        if (sipAddressesAndPhoneNumbers.value.orEmpty().size == 1) {
            // TODO
        } else {
            showNumberOrAddressPickerDialogEvent.value = Event(true)
        }
    }
}
