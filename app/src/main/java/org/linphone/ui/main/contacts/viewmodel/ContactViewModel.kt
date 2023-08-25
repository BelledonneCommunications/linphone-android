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
import org.linphone.ui.main.model.isInSecureMode
import org.linphone.utils.Event
import org.linphone.utils.FileUtils
import org.linphone.utils.PhoneNumberUtils

class ContactViewModel @UiThread constructor() : ViewModel() {
    companion object {
        private const val TAG = "[Contact ViewModel]"
    }

    val contact = MutableLiveData<ContactAvatarModel>()

    val sipAddressesAndPhoneNumbers = MutableLiveData<ArrayList<ContactNumberOrAddressModel>>()

    val devices = MutableLiveData<ArrayList<ContactDeviceModel>>()

    val company = MutableLiveData<String>()

    val title = MutableLiveData<String>()

    val isFavourite = MutableLiveData<Boolean>()

    val showBackButton = MutableLiveData<Boolean>()

    val expandNumbersAndAddresses = MutableLiveData<Boolean>()

    val expandDevicesTrust = MutableLiveData<Boolean>()

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

    val displayTrustProcessDialogEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val startCallToDeviceToIncreaseTrustEvent: MutableLiveData<Event<Pair<String, String>>> by lazy {
        MutableLiveData<Event<Pair<String, String>>>()
    }

    private val listener = object : ContactNumberOrAddressClickListener {
        @UiThread
        override fun onClicked(model: ContactNumberOrAddressModel) {
            val address = model.address
            if (model.isEnabled && address != null) {
                coreContext.postOnCoreThread {
                    Log.i("$TAG Calling SIP address [${address.asStringUriOnly()}]")
                    coreContext.startCall(address)
                }
            } else if (!model.isEnabled) {
                Log.w(
                    "$TAG Can't call SIP address [${address?.asStringUriOnly()}], it is disabled due to currently selected mode"
                )
                // TODO: Explain why user can't call that number
            }
        }

        @UiThread
        override fun onLongPress(model: ContactNumberOrAddressModel) {
            showLongPressMenuForNumberOrAddressEvent.value = Event(model)
        }
    }

    private lateinit var friend: Friend

    init {
        expandNumbersAndAddresses.value = true
        expandDevicesTrust.value = false // TODO FIXME: set it to true when it will work for real
    }

    @UiThread
    fun findContactByRefKey(refKey: String) {
        coreContext.postOnCoreThread { core ->
            val friend = coreContext.contactsManager.findContactById(refKey)
            if (friend != null) {
                Log.i("$TAG Found contact [${friend.name}] matching ref key [$refKey]")
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
                        true, // SIP addresses are always enabled
                        listener,
                        true
                    )
                    addressesAndNumbers.add(data)
                }
                val indexOfLastSipAddress = addressesAndNumbers.count()
                Log.i(
                    "$TAG Contact [${friend.name}] has [$indexOfLastSipAddress] SIP ${if (indexOfLastSipAddress > 1) "addresses" else "address"}"
                )

                for (number in friend.phoneNumbersWithLabel) {
                    val presenceModel = friend.getPresenceModelForUriOrTel(number.phoneNumber)
                    val hasPresenceInfo = !presenceModel?.contact.isNullOrEmpty()
                    var presenceAddress: Address? = null

                    if (presenceModel != null && hasPresenceInfo) {
                        Log.i("$TAG Phone number [${number.phoneNumber}] has presence information")
                        // Show linked SIP address if not already stored as-is
                        val contact = presenceModel.contact
                        val found = addressesAndNumbers.find {
                            it.displayedValue == contact
                        }
                        if (!contact.isNullOrEmpty() && found == null) {
                            val address = core.interpretUrl(contact, false)
                            if (address != null) {
                                address.clean() // To remove ;user=phone
                                presenceAddress = address
                                val data = ContactNumberOrAddressModel(
                                    address,
                                    address.asStringUriOnly(),
                                    true, // SIP addresses are always enabled
                                    listener,
                                    true
                                )
                                addressesAndNumbers.add(indexOfLastSipAddress, data)
                                Log.i(
                                    "$TAG Phone number [${number.phoneNumber}] is linked to SIP address [${presenceAddress.asStringUriOnly()}]"
                                )
                            }
                        } else if (found != null) {
                            presenceAddress = found.address
                            Log.i(
                                "$TAG Phone number [${number.phoneNumber}] is linked to existing SIP address [${presenceAddress?.asStringUriOnly()}]"
                            )
                        }
                    }

                    // phone numbers are disabled is secure mode unless linked to a SIP address
                    val enablePhoneNumbers = hasPresenceInfo || core.defaultAccount?.isInSecureMode() != true
                    val address = presenceAddress ?: core.interpretUrl(number.phoneNumber, true)
                    val label = PhoneNumberUtils.vcardParamStringToAddressBookLabel(
                        coreContext.context.resources,
                        number.label ?: ""
                    )
                    val data = ContactNumberOrAddressModel(
                        address,
                        number.phoneNumber,
                        enablePhoneNumbers,
                        listener,
                        false,
                        label
                    )
                    addressesAndNumbers.add(data)
                }

                val phoneNumbersCount = addressesAndNumbers.count() - indexOfLastSipAddress
                Log.i(
                    "$TAG Contact [${friend.name}] has [$phoneNumbersCount] phone ${if (phoneNumbersCount > 1) "numbers" else "number"}"
                )
                sipAddressesAndPhoneNumbers.postValue(addressesAndNumbers)

                val devicesList = arrayListOf<ContactDeviceModel>()
                // TODO FIXME: use real devices list from API
                devicesList.add(ContactDeviceModel("Pixel 6 Pro de Sylvain", true))
                devicesList.add(ContactDeviceModel("Sylvain Galaxy Tab S9 Pro+ Ultra", true))
                devicesList.add(
                    ContactDeviceModel("MacBook Pro de Marcel", false) {
                        // TODO: check if do not show dialog anymore setting is set
                        if (::friend.isInitialized) {
                            startCallToDeviceToIncreaseTrustEvent.value =
                                Event(Pair(friend.name.orEmpty(), it.name))
                        }
                    }
                )
                devicesList.add(ContactDeviceModel("sylvain@fedora-linux-38", true))
                devices.postValue(devicesList)

                contactFoundEvent.postValue(Event(true))
            }
        }
    }

    @UiThread
    fun toggleNumbersAndAddressesExpand() {
        expandNumbersAndAddresses.value = expandNumbersAndAddresses.value == false
    }

    @UiThread
    fun toggleDevicesTrustExpand() {
        expandDevicesTrust.value = expandDevicesTrust.value == false
    }

    @UiThread
    fun displayTrustDialog() {
        displayTrustProcessDialogEvent.value = Event(true)
    }

    @UiThread
    fun editContact() {
        coreContext.postOnCoreThread {
            if (::friend.isInitialized) {
                val uri = friend.nativeUri
                if (uri != null) {
                    Log.i(
                        "$TAG Contact [${friend.name}] is a native contact, opening native contact editor using URI [$uri]"
                    )
                    openNativeContactEditor.postValue(Event(uri))
                } else {
                    val id = contact.value?.id.orEmpty()
                    Log.i(
                        "$TAG Contact [${friend.name}] is a Linphone contact, opening in-app contact editor using ID [$id]"
                    )
                    openLinphoneContactEditor.postValue(Event(id))
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
        coreContext.postOnCoreThread {
            if (::friend.isInitialized) {
                Log.w("$TAG Deleting friend [$friend]")
                friend.remove()
                coreContext.contactsManager.notifyContactsListChanged()
            }
        }
    }

    @UiThread
    fun toggleFavourite() {
        coreContext.postOnCoreThread {
            val favourite = friend.starred
            Log.i(
                "$TAG Flagging contact [${friend.name}] as ${if (favourite) "no longer favourite" else "favourite"}"
            )

            friend.edit()
            friend.starred = !favourite
            friend.done()

            isFavourite.postValue(friend.starred)
            coreContext.contactsManager.notifyContactsListChanged()
        }
    }

    @UiThread
    fun startAudioCall() {
        val numbersAndAddresses = sipAddressesAndPhoneNumbers.value.orEmpty()
        val count = numbersAndAddresses.size
        if (count == 1) {
            Log.i(
                "$TAG Only 1 number or address found for contact [${friend.name}], starting audio call directly"
            )
            val address = numbersAndAddresses.first().address
            if (address != null) {
                coreContext.postOnCoreThread { core ->
                    val params = core.createCallParams(null)
                    params?.isVideoEnabled = false
                    coreContext.startCall(address, params)
                }
            }
        } else {
            Log.i(
                "$TAG [$count] numbers or addresses found for contact [${friend.name}], showing selection dialog"
            )
            showNumberOrAddressPickerDialogEvent.value = Event(true)
        }
    }

    @UiThread
    fun startVideoCall() {
        val numbersAndAddresses = sipAddressesAndPhoneNumbers.value.orEmpty()
        val count = numbersAndAddresses.size
        if (count == 1) {
            Log.i(
                "$TAG Only 1 number or address found for contact [${friend.name}], starting video call directly"
            )
            val address = numbersAndAddresses.first().address
            if (address != null) {
                coreContext.postOnCoreThread { core ->
                    val params = core.createCallParams(null)
                    params?.isVideoEnabled = true
                    coreContext.startCall(address, params)
                }
            }
        } else {
            Log.i(
                "$TAG [$count] numbers or addresses found for contact [${friend.name}], showing selection dialog"
            )
            showNumberOrAddressPickerDialogEvent.value = Event(true)
        }
    }

    @UiThread
    fun sendMessage() {
        val numbersAndAddresses = sipAddressesAndPhoneNumbers.value.orEmpty()
        val count = numbersAndAddresses.size
        if (count == 1) {
            Log.i(
                "$TAG Only 1 number or address found for contact [${friend.name}], sending message directly"
            )
            // TODO
        } else {
            Log.i(
                "$TAG [$count] numbers or addresses found for contact [${friend.name}], showing selection dialog"
            )
            showNumberOrAddressPickerDialogEvent.value = Event(true)
        }
    }
}
