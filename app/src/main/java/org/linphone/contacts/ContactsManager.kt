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
package org.linphone.contacts

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.core.app.Person
import androidx.core.graphics.drawable.IconCompat
import androidx.loader.app.LoaderManager
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.core.Address
import org.linphone.core.Core
import org.linphone.core.CoreListenerStub
import org.linphone.core.Friend
import org.linphone.core.FriendList
import org.linphone.core.FriendListListenerStub
import org.linphone.core.tools.Log
import org.linphone.ui.main.MainActivity
import org.linphone.ui.main.contacts.model.ContactNumberOrAddressClickListener
import org.linphone.ui.main.contacts.model.ContactNumberOrAddressModel
import org.linphone.ui.main.model.isInSecureMode
import org.linphone.utils.ImageUtils
import org.linphone.utils.PhoneNumberUtils

class ContactsManager @UiThread constructor(context: Context) {
    companion object {
        private const val TAG = "[Contacts Manager]"
    }

    val contactAvatar: IconCompat

    private val listeners = arrayListOf<ContactsListener>()

    private val friendListListener: FriendListListenerStub = object : FriendListListenerStub() {
        @WorkerThread
        override fun onPresenceReceived(list: FriendList, friends: Array<Friend>) {
            Log.i(
                "$TAG Presence received for list [${list.displayName}] and [${friends.size}] friends"
            )
            for (listener in listeners) {
                listener.onContactsLoaded()
            }
        }
    }

    private val coreListener: CoreListenerStub = object : CoreListenerStub() {
        @WorkerThread
        override fun onFriendListCreated(core: Core, friendList: FriendList) {
            Log.i("$TAG Friend list [${friendList.displayName}] created")
            friendList.addListener(friendListListener)
        }

        @WorkerThread
        override fun onFriendListRemoved(core: Core, friendList: FriendList) {
            Log.i("$TAG Friend list [${friendList.displayName}] remoed")
            friendList.removeListener(friendListListener)
        }
    }

    init {
        contactAvatar = IconCompat.createWithResource(
            context,
            R.drawable.user_circle
        )
    }

    @UiThread
    fun loadContacts(activity: MainActivity) {
        val manager = LoaderManager.getInstance(activity)
        manager.restartLoader(0, null, ContactLoader())
    }

    @WorkerThread
    fun addListener(listener: ContactsListener) {
        if (coreContext.isReady()) {
            listeners.add(listener)
        }
    }

    @WorkerThread
    fun removeListener(listener: ContactsListener) {
        if (coreContext.isReady()) {
            listeners.remove(listener)
        }
    }

    @UiThread
    fun onContactsLoaded() {
        coreContext.postOnCoreThread {
            notifyContactsListChanged()
        }
    }

    @WorkerThread
    fun notifyContactsListChanged() {
        for (listener in listeners) {
            listener.onContactsLoaded()
        }
    }

    @WorkerThread
    fun findContactById(id: String): Friend? {
        for (friendList in coreContext.core.friendsLists) {
            val found = friendList.findFriendByRefKey(id)
            if (found != null) return found
        }
        return null
    }

    @WorkerThread
    fun findContactByAddress(address: Address): Friend? {
        val friend = coreContext.core.findFriend(address)
        if (friend != null) return friend

        return null
    }

    @WorkerThread
    fun onCoreStarted(core: Core) {
        core.addListener(coreListener)
        for (list in core.friendsLists) {
            list.addListener(friendListListener)
        }
    }

    @WorkerThread
    fun onCoreStopped(core: Core) {
        core.removeListener(coreListener)
        for (list in core.friendsLists) {
            list.removeListener(friendListListener)
        }
    }

    interface ContactsListener {
        fun onContactsLoaded()
    }
}

@WorkerThread
fun Friend.getPerson(): Person {
    val personBuilder = Person.Builder().setName(name)

    val bm: Bitmap? = if (!photo.isNullOrEmpty()) {
        ImageUtils.getRoundBitmapFromUri(
            coreContext.context,
            Uri.parse(photo ?: "")
        )
    } else {
        null
    }

    personBuilder.setIcon(
        if (bm == null) {
            coreContext.contactsManager.contactAvatar
        } else {
            IconCompat.createWithAdaptiveBitmap(bm)
        }
    )

    personBuilder.setKey(refKey)
    personBuilder.setUri(nativeUri)
    personBuilder.setImportant(starred)
    return personBuilder.build()
}

@WorkerThread
fun Friend.getListOfSipAddressesAndPhoneNumbers(listener: ContactNumberOrAddressClickListener): ArrayList<ContactNumberOrAddressModel> {
    val addressesAndNumbers = arrayListOf<ContactNumberOrAddressModel>()

    for (address in addresses) {
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
        "[Friend] Contact [$name] has [$indexOfLastSipAddress] SIP ${if (indexOfLastSipAddress > 1) "addresses" else "address"}"
    )

    for (number in phoneNumbersWithLabel) {
        val presenceModel = getPresenceModelForUriOrTel(number.phoneNumber)
        val hasPresenceInfo = !presenceModel?.contact.isNullOrEmpty()
        var presenceAddress: Address? = null

        if (presenceModel != null && hasPresenceInfo) {
            Log.i("[Friend] Phone number [${number.phoneNumber}] has presence information")
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
                        "[Friend] Phone number [${number.phoneNumber}] is linked to SIP address [${presenceAddress.asStringUriOnly()}]"
                    )
                }
            } else if (found != null) {
                presenceAddress = found.address
                Log.i(
                    "[Friend] Phone number [${number.phoneNumber}] is linked to existing SIP address [${presenceAddress?.asStringUriOnly()}]"
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
            label,
            presenceAddress != null
        )
        addressesAndNumbers.add(data)
    }

    val phoneNumbersCount = addressesAndNumbers.count() - indexOfLastSipAddress
    Log.i(
        "[Friend] Contact [$name] has [$phoneNumbersCount] phone ${if (phoneNumbersCount > 1) "numbers" else "number"}"
    )
    return addressesAndNumbers
}
