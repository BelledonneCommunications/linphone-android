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
import org.linphone.utils.Event

class ContactNewOrEditViewModel() : ViewModel() {
    companion object {
        const val TAG = "[Contact New/Edit View Model]"
    }

    private lateinit var friend: Friend

    val isEdit = MutableLiveData<Boolean>()

    val firstName = MutableLiveData<String>()

    val lastName = MutableLiveData<String>()

    val company = MutableLiveData<String>()

    val jobTitle = MutableLiveData<String>()

    val saveChangesEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
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

            if (exists) {
                Log.i("$TAG Found friend [$friend] using ref key [$refKey]")
                val vCard = friend.vcard
                if (vCard != null) {
                    firstName.postValue(vCard.givenName)
                    lastName.postValue(vCard.familyName)
                } else {
                    // TODO
                }

                company.postValue(friend.organization)
                jobTitle.postValue(friend.jobTitle)

                friendFoundEvent.postValue(Event(true))
            } else {
                Log.e("$TAG No friend found using ref key [$refKey]")
            }
        }
    }

    fun saveChanges() {
        // UI thread
        coreContext.postOnCoreThread { core ->
            var status = Status.OK

            if (::friend.isInitialized) {
                friend.name = "${firstName.value.orEmpty()} ${lastName.value.orEmpty()}"

                val vCard = friend.vcard
                if (vCard != null) {
                    vCard.familyName = lastName.value
                    vCard.givenName = firstName.value
                }

                friend.organization = company.value.orEmpty()
                friend.jobTitle = jobTitle.value.orEmpty()

                if (isEdit.value == false) {
                    status = core.defaultFriendList?.addFriend(friend) ?: Status.InvalidFriend
                }
            } else {
                status = Status.NonExistentFriend
            }

            saveChangesEvent.postValue(Event(status == Status.OK))
        }
    }
}
