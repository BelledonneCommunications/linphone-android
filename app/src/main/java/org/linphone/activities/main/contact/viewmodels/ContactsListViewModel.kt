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
package org.linphone.activities.main.contact.viewmodels

import android.content.ContentProviderOperation
import android.provider.ContactsContract
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import java.util.*
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.contact.Contact
import org.linphone.contact.ContactsUpdatedListenerStub
import org.linphone.contact.NativeContact
import org.linphone.core.tools.Log

class ContactsListViewModel : ViewModel() {
    val sipContactsSelected = MutableLiveData<Boolean>()

    val contactsList = MutableLiveData<ArrayList<Contact>>()

    val filter = MutableLiveData<String>()

    private val contactsUpdatedListener = object : ContactsUpdatedListenerStub() {
        override fun onContactsUpdated() {
            Log.i("[Contacts] Contacts have changed")
            updateContactsList()
        }
    }

    init {
        sipContactsSelected.value = coreContext.contactsManager.shouldDisplaySipContactsList()

        coreContext.contactsManager.addListener(contactsUpdatedListener)
    }

    override fun onCleared() {
        coreContext.contactsManager.removeListener(contactsUpdatedListener)

        super.onCleared()
    }

    fun updateContactsList() {
        var list = arrayListOf<Contact>()
        list.addAll(if (sipContactsSelected.value == true) coreContext.contactsManager.sipContacts else coreContext.contactsManager.contacts)

        val filterValue = filter.value.orEmpty().toLowerCase(Locale.getDefault())
        if (filterValue.isNotEmpty()) {
            list = list.filter { contact ->
                contact.fullName?.toLowerCase(Locale.getDefault())?.contains(filterValue) ?: false
            } as ArrayList<Contact>
        }

        // Prevent blinking items when list hasn't changed
        if (list.isEmpty() || list.size != contactsList.value.orEmpty().size) {
            contactsList.value = list
        }
    }

    fun deleteContact(contact: Contact?) {
        contact ?: return

        val select = ContactsContract.Data.CONTACT_ID + " = ?"
        val ops = ArrayList<ContentProviderOperation>()

        if (contact is NativeContact) {
            val nativeContact: NativeContact = contact
            Log.i("[Contacts] Adding Android contact id ${nativeContact.nativeId} to batch removal")
            val args = arrayOf(nativeContact.nativeId)
            ops.add(
                ContentProviderOperation.newDelete(ContactsContract.RawContacts.CONTENT_URI)
                    .withSelection(select, args)
                    .build()
            )
        }

        if (contact.friend != null) {
            Log.i("[Contacts] Removing friend")
            contact.friend?.remove()
        }

        if (ops.isNotEmpty()) {
            try {
                Log.i("[Contacts] Removing ${ops.size} contacts")
                coreContext.context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
            } catch (e: Exception) {
                Log.e("[Contacts] $e")
            }
        }
    }

    fun deleteContacts(list: ArrayList<Contact>) {
        val select = ContactsContract.Data.CONTACT_ID + " = ?"
        val ops = ArrayList<ContentProviderOperation>()

        for (contact in list) {
            if (contact is NativeContact) {
                val nativeContact: NativeContact = contact
                Log.i("[Contacts] Adding Android contact id ${nativeContact.nativeId} to batch removal")
                val args = arrayOf(nativeContact.nativeId)
                ops.add(
                    ContentProviderOperation.newDelete(ContactsContract.RawContacts.CONTENT_URI)
                        .withSelection(select, args)
                        .build()
                )
            }

            if (contact.friend != null) {
                Log.i("[Contacts] Removing friend")
                contact.friend?.remove()
            }
        }

        if (ops.isNotEmpty()) {
            try {
                Log.i("[Contacts] Removing ${ops.size} contacts")
                coreContext.context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
            } catch (e: Exception) {
                Log.e("[Contacts] $e")
            }
        }
    }
}
