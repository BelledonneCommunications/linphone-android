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
import org.linphone.core.Friend
import org.linphone.core.FriendList.Status
import org.linphone.core.tools.Log
import org.linphone.ui.main.contacts.model.NewOrEditNumberOrAddressModel
import org.linphone.utils.Event

class ContactNewOrEditViewModel() : ViewModel() {
    companion object {
        const val TAG = "[Contact New/Edit View Model]"
    }

    private lateinit var friend: Friend

    val isEdit = MutableLiveData<Boolean>()

    val firstName = MutableLiveData<String>()

    val lastName = MutableLiveData<String>()

    val sipAddresses = MutableLiveData<ArrayList<NewOrEditNumberOrAddressModel>>()

    val phoneNumbers = MutableLiveData<ArrayList<NewOrEditNumberOrAddressModel>>()

    val company = MutableLiveData<String>()

    val jobTitle = MutableLiveData<String>()

    val saveChangesEvent: MutableLiveData<Event<String>> by lazy {
        MutableLiveData<Event<String>>()
    }

    val friendFoundEvent = MutableLiveData<Event<Boolean>>()

    fun findFriendByRefKey(refKey: String?) {
        // UI thread
        coreContext.postOnCoreThread { core ->
            friend = if (refKey.isNullOrEmpty()) {
                core.createFriend()
            } else {
                coreContext.contactsManager.findContactById(refKey) ?: core.createFriend()
            }
            val exists = !friend.refKey.isNullOrEmpty()
            isEdit.postValue(exists)

            val addresses = arrayListOf<NewOrEditNumberOrAddressModel>()
            val numbers = arrayListOf<NewOrEditNumberOrAddressModel>()

            if (exists) {
                Log.i("$TAG Found friend [$friend] using ref key [$refKey]")
                val vCard = friend.vcard
                if (vCard != null) {
                    firstName.postValue(vCard.givenName)
                    lastName.postValue(vCard.familyName)
                } else {
                    // TODO ?
                }

                for (address in friend.addresses) {
                    addresses.add(
                        NewOrEditNumberOrAddressModel(address.asStringUriOnly(), true, { }, { model ->
                            removeModel(model)
                        })
                    )
                }
                for (number in friend.phoneNumbers) {
                    numbers.add(
                        NewOrEditNumberOrAddressModel(number, false, { }, { model ->
                            removeModel(model)
                        })
                    )
                }

                company.postValue(friend.organization)
                jobTitle.postValue(friend.jobTitle)

                friendFoundEvent.postValue(Event(true))
            } else if (refKey.orEmpty().isNotEmpty()) {
                Log.e("$TAG No friend found using ref key [$refKey]")
            }

            addresses.add(
                NewOrEditNumberOrAddressModel("", true, {
                    addNewModel(true)
                }, { model ->
                    removeModel(model)
                })
            )
            numbers.add(
                NewOrEditNumberOrAddressModel("", false, {
                    addNewModel(false)
                }, { model ->
                    removeModel(model)
                })
            )

            sipAddresses.postValue(addresses)
            phoneNumbers.postValue(numbers)
        }
    }

    fun saveChanges() {
        // UI thread
        coreContext.postOnCoreThread { core ->
            var status = Status.OK

            if (!::friend.isInitialized) {
                friend = core.createFriend()
            }

            if (isEdit.value == true) {
                friend.edit()
            }

            friend.name = "${firstName.value.orEmpty()} ${lastName.value.orEmpty()}"

            val vCard = friend.vcard
            if (vCard != null) {
                vCard.familyName = lastName.value
                vCard.givenName = firstName.value
            }

            friend.organization = company.value.orEmpty()
            friend.jobTitle = jobTitle.value.orEmpty()

            for (address in friend.addresses) {
                friend.removeAddress(address)
            }
            for (address in sipAddresses.value.orEmpty()) {
                val data = address.value.value
                if (!data.isNullOrEmpty()) {
                    val parsedAddress = core.interpretUrl(data, true)
                    if (parsedAddress != null) {
                        friend.addAddress(parsedAddress)
                    }
                }
            }

            for (number in friend.phoneNumbers) {
                friend.removePhoneNumber(number)
            }
            for (number in phoneNumbers.value.orEmpty()) {
                val data = number.value.value
                if (!data.isNullOrEmpty()) {
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
                    // TODO : generate unique ref key
                }
                status = core.defaultFriendList?.addFriend(friend) ?: Status.InvalidFriend
            } else {
                friend.done()
            }
            coreContext.contactsManager.notifyContactsListChanged()

            saveChangesEvent.postValue(
                Event(if (status == Status.OK) friend.refKey.orEmpty() else "")
            )
        }
    }

    private fun addNewModel(isSip: Boolean) {
        // UI thread
        // TODO FIXME: causes focus issues
        val list = arrayListOf<NewOrEditNumberOrAddressModel>()
        val source = if (isSip) sipAddresses.value.orEmpty() else phoneNumbers.value.orEmpty()

        list.addAll(source)
        list.add(
            NewOrEditNumberOrAddressModel("", isSip, {
                addNewModel(isSip)
            }, { model ->
                removeModel(model)
            })
        )

        if (isSip) {
            sipAddresses.value = list
        } else {
            phoneNumbers.value = list
        }
    }

    private fun removeModel(model: NewOrEditNumberOrAddressModel) {
        // UI thread
        val list = arrayListOf<NewOrEditNumberOrAddressModel>()
        val source = if (model.isSip) sipAddresses.value.orEmpty() else phoneNumbers.value.orEmpty()

        for (item in source) {
            if (item != model) {
                list.add(item)
            }
        }

        if (model.isSip) {
            sipAddresses.value = list
        } else {
            phoneNumbers.value = list
        }
    }
}
