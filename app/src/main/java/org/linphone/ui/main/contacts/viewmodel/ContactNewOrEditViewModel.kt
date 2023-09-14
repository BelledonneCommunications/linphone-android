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
import androidx.annotation.WorkerThread
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.contacts.ContactLoader.Companion.LINPHONE_ADDRESS_BOOK_FRIEND_LIST
import org.linphone.core.Friend
import org.linphone.core.FriendList.Status
import org.linphone.core.SubscribePolicy
import org.linphone.core.tools.Log
import org.linphone.ui.main.contacts.model.NewOrEditNumberOrAddressModel
import org.linphone.utils.Event
import org.linphone.utils.FileUtils

class ContactNewOrEditViewModel @UiThread constructor() : ViewModel() {
    companion object {
        private const val TAG = "[Contact New/Edit View Model]"
    }

    private lateinit var friend: Friend

    val isEdit = MutableLiveData<Boolean>()

    val picturePath = MutableLiveData<String>()

    val firstName = MutableLiveData<String>()

    val lastName = MutableLiveData<String>()

    val sipAddresses = ArrayList<NewOrEditNumberOrAddressModel>()

    val phoneNumbers = ArrayList<NewOrEditNumberOrAddressModel>()

    val company = MutableLiveData<String>()

    val jobTitle = MutableLiveData<String>()

    val saveButtonEnabled = MediatorLiveData<Boolean>()

    val saveChangesEvent: MutableLiveData<Event<String>> by lazy {
        MutableLiveData<Event<String>>()
    }

    val friendFoundEvent = MutableLiveData<Event<Boolean>>()

    val addNewNumberOrAddressFieldEvent = MutableLiveData<Event<NewOrEditNumberOrAddressModel>>()

    val removeNewNumberOrAddressFieldEvent = MutableLiveData<Event<NewOrEditNumberOrAddressModel>>()

    init {
        saveButtonEnabled.value = isSaveButtonEnabled()
        saveButtonEnabled.addSource(firstName) {
            saveButtonEnabled.value = isSaveButtonEnabled()
        }
        saveButtonEnabled.addSource(lastName) {
            saveButtonEnabled.value = isSaveButtonEnabled()
        }
    }

    @UiThread
    fun findFriendByRefKey(refKey: String?) {
        reset()

        coreContext.postOnCoreThread { core ->
            friend = if (refKey.isNullOrEmpty()) {
                core.createFriend()
            } else {
                coreContext.contactsManager.findContactById(refKey) ?: core.createFriend()
            }
            val exists = !friend.refKey.isNullOrEmpty()
            isEdit.postValue(exists)

            if (exists) {
                Log.i("$TAG Found friend [$friend] using ref key [$refKey]")
                val vCard = friend.vcard
                if (vCard != null) {
                    firstName.postValue(vCard.givenName)
                    lastName.postValue(vCard.familyName)
                } else {
                    // TODO ? What to do when vCard is null
                }

                picturePath.postValue(friend.photo)

                for (address in friend.addresses) {
                    addSipAddress(address.asStringUriOnly())
                }
                for (number in friend.phoneNumbers) {
                    addPhoneNumber(number)
                }

                company.postValue(friend.organization)
                jobTitle.postValue(friend.jobTitle)
            } else if (refKey.orEmpty().isNotEmpty()) {
                Log.e("$TAG No friend found using ref key [$refKey]")
            }

            addSipAddress()
            addPhoneNumber()

            friendFoundEvent.postValue(Event(exists))
        }
    }

    @UiThread
    fun saveChanges() {
        coreContext.postOnCoreThread { core ->
            var status = Status.OK

            if (!::friend.isInitialized) {
                friend = core.createFriend()
                friend.isSubscribesEnabled = false
                // Disable peer to peer short term presence
                friend.incSubscribePolicy = SubscribePolicy.SPDeny
            }
            val fn = firstName.value.orEmpty().trim()
            val ln = lastName.value.orEmpty().trim()
            friend.name = "$fn $ln"

            friend.edit()

            val vCard = friend.vcard
            if (vCard != null) {
                vCard.familyName = fn
                vCard.givenName = ln

                val picture = picturePath.value.orEmpty()
                if (picture.isNotEmpty()) {
                    friend.photo = FileUtils.getProperFilePath(picture)
                }
            }

            friend.organization = company.value.orEmpty().trim()
            friend.jobTitle = jobTitle.value.orEmpty().trim()

            for (address in friend.addresses) {
                friend.removeAddress(address)
            }
            for (address in sipAddresses) {
                val data = address.value.value.orEmpty().trim()
                if (data.isNotEmpty()) {
                    val parsedAddress = core.interpretUrl(data, true)
                    if (parsedAddress != null) {
                        friend.addAddress(parsedAddress)
                    }
                }
            }

            for (number in friend.phoneNumbers) {
                friend.removePhoneNumber(number)
            }
            for (number in phoneNumbers) {
                val data = number.value.value.orEmpty().trim()
                if (data.isNotEmpty()) {
                    friend.addPhoneNumber(data)
                }
            }

            if (isEdit.value == false) {
                if (friend.vcard?.generateUniqueId() == true) {
                    friend.refKey = friend.vcard?.uid
                    Log.i(
                        "$TAG Newly created friend will have generated ref key [${friend.refKey}]"
                    )
                } else {
                    Log.e("$TAG Failed to generate a ref key using vCard's generateUniqueId()")
                    // TODO? : generate unique ref key
                }
                friend.done()

                val fl = core.getFriendListByName(LINPHONE_ADDRESS_BOOK_FRIEND_LIST) ?: core.createFriendList()
                if (fl.displayName.isNullOrEmpty()) {
                    Log.i(
                        "$TAG Locally saved friend list [$LINPHONE_ADDRESS_BOOK_FRIEND_LIST] didn't exist yet, let's create it"
                    )
                    fl.isDatabaseStorageEnabled = true // We do want to store friends created in app in DB
                    fl.displayName = LINPHONE_ADDRESS_BOOK_FRIEND_LIST
                    core.addFriendList(fl)
                }
                status = fl.addFriend(friend)
                fl.updateSubscriptions()
            } else {
                friend.done()
            }

            coreContext.contactsManager.notifyContactsListChanged()

            saveChangesEvent.postValue(
                Event(if (status == Status.OK) friend.refKey.orEmpty() else "")
            )
        }
    }

    @WorkerThread
    fun addSipAddress(address: String = "", requestFieldToBeAddedInUi: Boolean = false) {
        val newModel = NewOrEditNumberOrAddressModel(address, true, {
            if (address.isEmpty()) {
                addSipAddress(requestFieldToBeAddedInUi = true)
            }
        }, { model ->
            removeModel(model)
        })
        sipAddresses.add(newModel)

        if (requestFieldToBeAddedInUi) {
            addNewNumberOrAddressFieldEvent.postValue(Event(newModel))
        }
    }

    @WorkerThread
    private fun addPhoneNumber(number: String = "", requestFieldToBeAddedInUi: Boolean = false) {
        val newModel = NewOrEditNumberOrAddressModel(number, false, {
            if (number.isEmpty()) {
                addPhoneNumber(requestFieldToBeAddedInUi = true)
            }
        }, { model ->
            removeModel(model)
        })
        phoneNumbers.add(newModel)

        if (requestFieldToBeAddedInUi) {
            addNewNumberOrAddressFieldEvent.postValue(Event(newModel))
        }
    }

    @UiThread
    private fun removeModel(model: NewOrEditNumberOrAddressModel) {
        if (model.isSip) {
            sipAddresses.remove(model)
        } else {
            phoneNumbers.remove(model)
        }
        removeNewNumberOrAddressFieldEvent.value = Event(model)
    }

    @UiThread
    private fun isSaveButtonEnabled(): Boolean {
        return firstName.value.orEmpty().isNotEmpty() || lastName.value.orEmpty().isNotEmpty()
    }

    @UiThread
    private fun reset() {
        isEdit.value = false
        picturePath.value = ""
        firstName.value = ""
        lastName.value = ""
        sipAddresses.clear()
        phoneNumbers.clear()
        company.value = ""
        jobTitle.value = ""
        saveButtonEnabled.value = false
    }
}
