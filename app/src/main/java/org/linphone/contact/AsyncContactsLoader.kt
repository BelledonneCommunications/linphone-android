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

import android.content.Context
import android.database.Cursor
import android.os.AsyncTask
import android.provider.ContactsContract
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.core.*
import org.linphone.core.tools.Log
import org.linphone.utils.LinphoneUtils
import org.linphone.utils.PermissionHelper

class AsyncContactsLoader(private val context: Context) :
    AsyncTask<Void, Void, AsyncContactsLoader.AsyncContactsData>() {
    companion object {
        val projection = arrayOf(
            ContactsContract.Data.CONTACT_ID,
            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
            ContactsContract.Data.MIMETYPE,
            ContactsContract.Contacts.STARRED,
            ContactsContract.Contacts.LOOKUP_KEY,
            "data1", // Company, Phone or SIP Address
            "data2", // ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME, ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.SipAddress.TYPE
            "data3", // ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME, ContactsContract.CommonDataKinds.Phone.LABEL, ContactsContract.CommonDataKinds.SipAddress.LABEL
            "data4"
        )
    }

    override fun onPreExecute() {
        if (isCancelled) return
        Log.i("[Contacts Loader] Synchronization started")
    }

    override fun doInBackground(vararg args: Void): AsyncContactsData {
        val data = AsyncContactsData()
        if (isCancelled) return data

        Log.i("[Contacts Loader] Background synchronization started")
        val core: Core = coreContext.core
        val androidContactsCache: HashMap<String, Contact> = HashMap()
        val nativeIds = arrayListOf<String>()
        val friendLists = core.friendsLists

        for (list in friendLists) {
            val friends = list.friends
            for (friend in friends) {
                if (isCancelled) {
                    Log.w("[Contacts Loader] Task cancelled")
                    return data
                }
                var contact: Contact? = friend.userData as? Contact
                if (contact != null) {
                    if (contact is NativeContact) {
                        contact.sipAddresses.clear()
                        contact.rawSipAddresses.clear()
                        contact.phoneNumbers.clear()
                        contact.rawPhoneNumbers.clear()
                        androidContactsCache[contact.nativeId] = contact
                        nativeIds.add(contact.nativeId)
                    } else {
                        data.contacts.add(contact)
                        if (contact.sipAddresses.isNotEmpty()) {
                            data.sipContacts.add(contact)
                        }
                    }
                } else {
                    if (friend.refKey != null) {
                        // Friend has a refkey but no LinphoneContact => represents a
                        // native contact stored in db from a previous version of Linphone,
                        // remove it
                        list.removeFriend(friend)
                    } else { // No refkey so it's a standalone contact
                        contact = Contact()
                        contact.friend = friend
                        contact.syncValuesFromFriend()
                        friend.userData = contact
                        data.contacts.add(contact)
                        if (contact.sipAddresses.isNotEmpty()) {
                            data.sipContacts.add(contact)
                        }
                    }
                }
            }
        }

        if (PermissionHelper.required(context).hasReadContactsPermission()) {
            var selection: String? = null
            if (corePreferences.fetchContactsFromDefaultDirectory) {
                Log.i("[Contacts Loader] Only fetching contacts in default directory")
                selection = ContactsContract.Data.IN_DEFAULT_DIRECTORY + " == 1"
            }
            val cursor: Cursor? = context.contentResolver
                .query(
                    ContactsContract.Data.CONTENT_URI,
                    projection,
                    selection,
                    null,
                    null
                )

            if (cursor != null) {
                Log.i("[Contacts Loader] Found ${cursor.count} entries in cursor")
                while (cursor.moveToNext()) {
                    if (isCancelled) {
                        Log.w("[Contacts Loader] Task cancelled")
                        cursor.close()
                        return data
                    }

                    try {
                        val id: String =
                            cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Data.CONTACT_ID))
                        val starred =
                            cursor.getInt(cursor.getColumnIndexOrThrow(ContactsContract.Contacts.STARRED)) == 1
                        val lookupKey = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Contacts.LOOKUP_KEY))
                        var contact: Contact? = androidContactsCache[id]
                        if (contact == null) {
                            Log.d(
                                "[Contacts Loader] Creating contact with native ID $id, favorite flag is $starred"
                            )
                            nativeIds.add(id)
                            contact = NativeContact(id, lookupKey)
                            contact.isStarred = starred
                            androidContactsCache[id] = contact
                        }
                        contact.syncValuesFromAndroidCursor(cursor)
                    } catch (ise: IllegalStateException) {
                        Log.e(
                            "[Contacts Loader] Couldn't get values from cursor, exception: $ise"
                        )
                    } catch (iae: IllegalArgumentException) {
                        Log.e(
                            "[Contacts Loader] Couldn't get values from cursor, exception: $iae"
                        )
                    }
                }
                cursor.close()
            } else {
                Log.w("[Contacts Loader] Read contacts permission denied, can't fetch native contacts")
            }

            for (list in core.friendsLists) {
                val friends = list.friends
                for (friend in friends) {
                    if (isCancelled) {
                        Log.w("[Contacts Loader] Task cancelled")
                        return data
                    }
                    val contact: Contact? = friend.userData as? Contact
                    if (contact != null && contact is NativeContact) {
                        if (!nativeIds.contains(contact.nativeId)) {
                            Log.i("[Contacts Loader] Contact removed since last fetch: ${contact.nativeId}")
                            // Has been removed since last fetch
                            androidContactsCache.remove(contact.nativeId)
                        }
                    }
                }
            }

            nativeIds.clear()
        }

        val contacts: Collection<Contact> = androidContactsCache.values
        // New friends count will be 0 after the first contacts fetch
        Log.i(
            "[Contacts Loader] Found ${contacts.size} native contacts plus ${data.contacts.size} friends in the configuration file"
        )
        for (contact in contacts) {
            if (isCancelled) {
                Log.w("[Contacts Loader] Task cancelled")
                return data
            }
            if (contact.sipAddresses.isEmpty() && contact.phoneNumbers.isEmpty()) {
                continue
            }

            if (contact.fullName == null) {
                for (address in contact.sipAddresses) {
                    contact.fullName = LinphoneUtils.getDisplayName(address)
                    Log.w(
                        "[Contacts Loader] Couldn't find a display name for contact ${contact.fullName}, used SIP address display name / username instead..."
                    )
                }
            }

            if (!corePreferences.hideContactsWithoutPresence) {
                if (contact.sipAddresses.isNotEmpty() && !data.sipContacts.contains(contact)) {
                    data.sipContacts.add(contact)
                }
            }
            data.contacts.add(contact)
        }
        androidContactsCache.clear()

        data.contacts.sort()

        Log.i("[Contacts Loader] Background synchronization finished")
        return data
    }

    override fun onPostExecute(data: AsyncContactsData) {
        if (isCancelled) return
        Log.i("[Contacts Loader] ${data.contacts.size} contacts found in which ${data.sipContacts.size} are SIP")

        for (contact in data.contacts) {
            if (contact is NativeContact) {
                contact.createOrUpdateFriendFromNativeContact()

                if (contact.friend?.presenceModel?.basicStatus == PresenceBasicStatus.Open && !data.sipContacts.contains(contact)) {
                    Log.i("[Contacts Loader] Friend $contact has presence information, adding it to SIP list")
                    data.sipContacts.add(contact)
                }
            }
        }
        data.sipContacts.sort()

        // Now that contact fetching is asynchronous, this is required to ensure
        // presence subscription event will be sent with all friends
        val core = coreContext.core
        if (core.isFriendListSubscriptionEnabled) {
            Log.i("[Contacts Loader] Matching friends created, updating subscription")
            for (list in core.friendsLists) {
                if (list.rlsAddress == null) {
                    Log.w("[Contacts Loader] Friend list subscription enabled but RLS URI not set!")
                    val defaultRlsUri = corePreferences.defaultRlsUri
                    if (defaultRlsUri.isNotEmpty()) {
                        val rlsAddress = core.interpretUrl(defaultRlsUri)
                        if (rlsAddress != null) {
                            Log.i("[Contacts Loader] Using new RLS URI: ${rlsAddress.asStringUriOnly()}")
                            list.rlsAddress = rlsAddress
                        } else {
                            Log.e("[Contacts Loader] Couldn't parse RLS URI: $defaultRlsUri")
                        }
                    } else {
                        Log.e("[Contacts Loader] RLS URI not found in config file!")
                    }
                }

                list.updateSubscriptions()
            }
        }

        coreContext.contactsManager.updateContacts(data.contacts, data.sipContacts)

        Log.i("[Contacts Loader] Synchronization finished")
    }

    class AsyncContactsData {
        val contacts = arrayListOf<Contact>()
        val sipContacts = arrayListOf<Contact>()
    }
}
