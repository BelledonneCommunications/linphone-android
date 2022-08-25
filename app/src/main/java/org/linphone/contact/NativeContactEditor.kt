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

import android.content.ContentProviderOperation
import android.content.ContentUris
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds
import android.provider.ContactsContract.RawContacts
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.activities.main.contact.data.NumberOrAddressEditorData
import org.linphone.core.Friend
import org.linphone.core.tools.Log
import org.linphone.utils.AppUtils
import org.linphone.utils.PermissionHelper

class NativeContactEditor(val friend: Friend) {
    companion object {
        fun createAndroidContact(accountName: String?, accountType: String?): Long {
            Log.i("[Native Contact Editor] Using sync account $accountName with type $accountType")

            val changes = arrayListOf<ContentProviderOperation>()
            changes.add(
                ContentProviderOperation.newInsert(RawContacts.CONTENT_URI)
                    .withValue(RawContacts.ACCOUNT_NAME, accountName)
                    .withValue(RawContacts.ACCOUNT_TYPE, accountType)
                    .build()
            )
            val contentResolver = coreContext.context.contentResolver
            val results = contentResolver.applyBatch(ContactsContract.AUTHORITY, changes)
            for (result in results) {
                val uri = result.uri
                Log.i("[Native Contact Editor] Contact creation result is $uri")
                if (uri != null) {
                    try {
                        val cursor = contentResolver.query(
                            uri,
                            arrayOf(RawContacts.CONTACT_ID),
                            null,
                            null,
                            null
                        )
                        if (cursor != null) {
                            cursor.moveToNext()
                            val contactId: Long = cursor.getLong(0)
                            Log.i("[Native Contact Editor] New contact id is $contactId")
                            cursor.close()
                            return contactId
                        }
                    } catch (e: Exception) {
                        Log.e("[Native Contact Editor] Failed to get cursor: $e")
                    }
                }
            }

            return 0
        }
    }

    private val changes = arrayListOf<ContentProviderOperation>()
    private val selection =
        "${ContactsContract.Data.CONTACT_ID} =? AND ${ContactsContract.Data.MIMETYPE} =?"
    private val phoneNumberSelection =
        "$selection AND (${CommonDataKinds.Phone.NUMBER}=? OR ${CommonDataKinds.Phone.NORMALIZED_NUMBER}=?)"
    private val presenceUpdateSelection =
        "${ContactsContract.Data.CONTACT_ID} =? AND ${ContactsContract.Data.MIMETYPE} =? AND data3=?"
    private val contactUri = ContactsContract.Data.CONTENT_URI

    private var rawId: String? = null
    private var syncAccountRawId: String? = null
    private var pictureByteArray: ByteArray? = null

    init {
        val contentResolver = coreContext.context.contentResolver
        val cursor = contentResolver.query(
            RawContacts.CONTENT_URI,
            arrayOf(RawContacts._ID),
            "${RawContacts.CONTACT_ID} =?",
            arrayOf(friend.refKey),
            null
        )
        if (cursor?.moveToFirst() == true) {
            do {
                if (rawId == null) {
                    try {
                        rawId = cursor.getString(cursor.getColumnIndexOrThrow(RawContacts._ID))
                        Log.d("[Native Contact Editor] Found raw id $rawId for native contact with id ${friend.refKey}")
                    } catch (iae: IllegalArgumentException) {
                        Log.e("[Native Contact Editor] Exception: $iae")
                    }
                }
            } while (cursor.moveToNext() && rawId == null)
        }
        cursor?.close()
    }

    fun setFirstAndLastNames(firstName: String, lastName: String): NativeContactEditor {
        if (firstName == friend.vcard?.givenName && lastName == friend.vcard?.familyName) {
            Log.w("[Native Contact Editor] First & last names haven't changed")
            return this
        }

        val builder = if (friend.vcard?.givenName == null && friend.vcard?.familyName == null) {
            // Probably a contact creation
            ContentProviderOperation.newInsert(contactUri)
                .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawId)
        } else {
            ContentProviderOperation.newUpdate(contactUri)
                .withSelection(
                    selection,
                    arrayOf(friend.refKey, CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                )
        }

        builder.withValue(
            ContactsContract.Data.MIMETYPE,
            CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE
        )
            .withValue(
                CommonDataKinds.StructuredName.GIVEN_NAME, firstName
            )
            .withValue(
                CommonDataKinds.StructuredName.FAMILY_NAME, lastName
            )
        addChanges(builder.build())
        return this
    }

    fun setOrganization(value: String): NativeContactEditor {
        val previousValue = friend.organization.orEmpty()
        if (value == previousValue) {
            Log.d("[Native Contact Editor] Organization hasn't changed")
            return this
        }

        val builder = if (previousValue.isNotEmpty()) {
            ContentProviderOperation.newUpdate(contactUri)
                .withSelection(
                    "$selection AND ${CommonDataKinds.Organization.COMPANY} =?",
                    arrayOf(
                        friend.refKey,
                        CommonDataKinds.Organization.CONTENT_ITEM_TYPE,
                        previousValue
                    )
                )
        } else {
            ContentProviderOperation.newInsert(contactUri)
                .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawId)
        }

        builder.withValue(
            ContactsContract.Data.MIMETYPE,
            CommonDataKinds.Organization.CONTENT_ITEM_TYPE
        )
            .withValue(
                CommonDataKinds.Organization.COMPANY, value
            )

        addChanges(builder.build())
        return this
    }

    fun setPhoneNumbers(value: List<NumberOrAddressEditorData>): NativeContactEditor {
        var addCount = 0
        var removeCount = 0
        var editCount = 0

        for (phoneNumber in value) {
            when {
                phoneNumber.currentValue.isEmpty() -> {
                    // New phone number to add
                    val number = phoneNumber.newValue.value.orEmpty()
                    if (number.isNotEmpty()) {
                        addCount++
                        addPhoneNumber(number)
                    }
                }
                phoneNumber.toRemove.value == true -> {
                    // Existing number to remove
                    removeCount++
                    removePhoneNumber(phoneNumber.currentValue)
                }
                phoneNumber.currentValue != phoneNumber.newValue.value -> {
                    // Existing number to update
                    val number = phoneNumber.newValue.value.orEmpty()
                    if (number.isNotEmpty()) {
                        editCount++
                        updatePhoneNumber(phoneNumber.currentValue, number)
                    }
                }
            }
        }

        Log.i("[Native Contact Editor] $addCount numbers added, $removeCount numbers removed and $editCount numbers updated")
        return this
    }

    fun setSipAddresses(value: List<NumberOrAddressEditorData>): NativeContactEditor {
        var addCount = 0
        var removeCount = 0
        var editCount = 0

        for (sipAddress in value) {
            when {
                sipAddress.currentValue.isEmpty() -> {
                    // New address to add
                    val address = sipAddress.newValue.value.orEmpty()
                    if (address.isNotEmpty()) {
                        addCount++
                        addSipAddress(address)
                    }
                }
                sipAddress.toRemove.value == true -> {
                    // Existing address to remove
                    removeCount++
                    removeLinphoneOrSipAddress(sipAddress.currentValue)
                }
                sipAddress.currentValue != sipAddress.newValue.value -> {
                    // Existing address to update
                    val address = sipAddress.newValue.value.orEmpty()
                    if (address.isNotEmpty()) {
                        editCount++
                        updateLinphoneOrSipAddress(sipAddress.currentValue, address)
                    }
                }
            }
        }

        Log.i("[Native Contact Editor] $addCount addresses added, $removeCount addresses removed and $editCount addresses updated")
        return this
    }

    fun setPicture(value: ByteArray?): NativeContactEditor {
        pictureByteArray = value
        if (value != null) Log.i("[Native Contact Editor] Adding operation: picture set/update")
        return this
    }

    fun setPresenceInformation(phoneNumber: String, sipAddress: String): NativeContactEditor {
        if (syncAccountRawId == null) {
            val contentResolver = coreContext.context.contentResolver
            val cursor = contentResolver.query(
                RawContacts.CONTENT_URI,
                arrayOf(RawContacts._ID, RawContacts.ACCOUNT_TYPE),
                "${RawContacts.CONTACT_ID} =?",
                arrayOf(friend.refKey),
                null
            )
            if (cursor?.moveToFirst() == true) {
                do {
                    try {
                        val accountType =
                            cursor.getString(cursor.getColumnIndexOrThrow(RawContacts.ACCOUNT_TYPE))
                        if (accountType == AppUtils.getString(R.string.sync_account_type) && syncAccountRawId == null) {
                            syncAccountRawId =
                                cursor.getString(cursor.getColumnIndexOrThrow(RawContacts._ID))
                            Log.d("[Native Contact Editor] Found linphone raw id $syncAccountRawId for native contact with id ${friend.refKey}")
                        }
                    } catch (iae: IllegalArgumentException) {
                        Log.e("[Native Contact Editor] Exception: $iae")
                    }
                } while (cursor.moveToNext() && syncAccountRawId == null)
            }
            cursor?.close()
        }

        if (syncAccountRawId == null) {
            Log.w("[Native Contact Editor] Linphone raw id not found")
            val insert = ContentProviderOperation.newInsert(RawContacts.CONTENT_URI)
                .withValue(RawContacts.ACCOUNT_NAME, AppUtils.getString(R.string.sync_account_name))
                .withValue(RawContacts.ACCOUNT_TYPE, AppUtils.getString(R.string.sync_account_type))
                .withValue(RawContacts.AGGREGATION_MODE, RawContacts.AGGREGATION_MODE_DEFAULT)
                .build()
            addChanges(insert)
            val update =
                ContentProviderOperation.newUpdate(ContactsContract.AggregationExceptions.CONTENT_URI)
                    .withValue(
                        ContactsContract.AggregationExceptions.TYPE,
                        ContactsContract.AggregationExceptions.TYPE_KEEP_TOGETHER
                    )
                    .withValue(ContactsContract.AggregationExceptions.RAW_CONTACT_ID1, rawId)
                    .withValueBackReference(
                        ContactsContract.AggregationExceptions.RAW_CONTACT_ID2,
                        0
                    )
                    .build()
            addChanges(update)
            commit(true)
        }

        if (syncAccountRawId == null) {
            Log.e("[Native Contact Editor] Can't add presence to contact in Linphone sync account, no raw id")
            return this
        }

        Log.d("[Native Contact Editor] Trying to add presence information to contact")
        setPresenceLinphoneSipAddressForPhoneNumber(sipAddress, phoneNumber)
        return this
    }

    fun commit(updateSyncAccountRawId: Boolean = false) {
        if (PermissionHelper.get().hasWriteContactsPermission()) {
            try {
                if (changes.isNotEmpty()) {
                    val contentResolver = coreContext.context.contentResolver
                    val results = contentResolver.applyBatch(ContactsContract.AUTHORITY, changes)
                    for (result in results) {
                        val uri = result.uri
                        Log.i("[Native Contact Editor] Result is $uri")
                        if (uri != null && updateSyncAccountRawId && syncAccountRawId == null) {
                            syncAccountRawId = ContentUris.parseId(uri).toString()
                            Log.i("[Native Contact Editor] Sync account raw id is $syncAccountRawId")
                        }
                    }
                }
                if (pictureByteArray != null) {
                    updatePicture()
                }
            } catch (e: Exception) {
                Log.e("[Native Contact Editor] Exception raised while applying changes: $e")
            }
        } else {
            Log.e("[Native Contact Editor] WRITE_CONTACTS permission isn't granted!")
        }
        changes.clear()
    }

    private fun addChanges(operation: ContentProviderOperation) {
        Log.i("[Native Contact Editor] Adding operation: $operation")
        changes.add(operation)
    }

    private fun addPhoneNumber(phoneNumber: String) {
        val insert = ContentProviderOperation.newInsert(contactUri)
            .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawId)
            .withValue(
                ContactsContract.Data.MIMETYPE,
                CommonDataKinds.Phone.CONTENT_ITEM_TYPE
            )
            .withValue(CommonDataKinds.Phone.NUMBER, phoneNumber)
            .withValue(
                CommonDataKinds.Phone.TYPE,
                CommonDataKinds.Phone.TYPE_MOBILE
            )
            .build()
        addChanges(insert)
    }

    private fun updatePhoneNumber(currentValue: String, phoneNumber: String) {
        val update = ContentProviderOperation.newUpdate(contactUri)
            .withSelection(
                phoneNumberSelection,
                arrayOf(
                    friend.refKey,
                    CommonDataKinds.Phone.CONTENT_ITEM_TYPE,
                    currentValue,
                    currentValue
                )
            )
            .withValue(ContactsContract.Data.MIMETYPE, CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
            .withValue(CommonDataKinds.Phone.NUMBER, phoneNumber)
            .withValue(
                CommonDataKinds.Phone.TYPE,
                CommonDataKinds.Phone.TYPE_MOBILE
            )
            .build()
        addChanges(update)
    }

    private fun removePhoneNumber(phoneNumber: String) {
        val delete = ContentProviderOperation.newDelete(contactUri)
            .withSelection(
                phoneNumberSelection,
                arrayOf(
                    friend.refKey,
                    CommonDataKinds.Phone.CONTENT_ITEM_TYPE,
                    phoneNumber,
                    phoneNumber
                )
            )
            .build()
        addChanges(delete)
    }

    private fun addSipAddress(address: String) {
        val insert = ContentProviderOperation.newInsert(contactUri)
            .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawId)
            .withValue(
                ContactsContract.Data.MIMETYPE,
                CommonDataKinds.SipAddress.CONTENT_ITEM_TYPE
            )
            .withValue("data1", address) // value
            .build()
        addChanges(insert)
    }

    private fun updateLinphoneOrSipAddress(currentValue: String, sipAddress: String) {
        val updateLegacy = ContentProviderOperation.newUpdate(contactUri)
            .withSelection(
                "${ContactsContract.Data.CONTACT_ID} =? AND ${ContactsContract.Data.MIMETYPE} =? AND data1=?",
                arrayOf(
                    friend.refKey,
                    AppUtils.getString(R.string.linphone_address_mime_type),
                    currentValue
                )
            )
            .withValue("data1", sipAddress) // value
            .withValue("data2", AppUtils.getString(R.string.app_name)) // summary
            .withValue("data3", sipAddress) // detail
            .build()

        val update = ContentProviderOperation.newUpdate(contactUri)
            .withSelection(
                "${ContactsContract.Data.CONTACT_ID} =? AND ${ContactsContract.Data.MIMETYPE} =? AND data1=?",
                arrayOf(
                    friend.refKey,
                    CommonDataKinds.SipAddress.CONTENT_ITEM_TYPE,
                    currentValue
                )
            )
            .withValue("data1", sipAddress) // value
            .build()

        addChanges(updateLegacy)
        addChanges(update)
    }

    private fun removeLinphoneOrSipAddress(sipAddress: String) {
        val delete = ContentProviderOperation.newDelete(contactUri)
            .withSelection(
                "${ContactsContract.Data.CONTACT_ID} =? AND (${ContactsContract.Data.MIMETYPE} =? OR ${ContactsContract.Data.MIMETYPE} =?) AND data1=?",
                arrayOf(
                    friend.refKey,
                    CommonDataKinds.SipAddress.CONTENT_ITEM_TYPE,
                    AppUtils.getString(R.string.linphone_address_mime_type),
                    sipAddress
                )
            )
            .build()
        addChanges(delete)
    }

    private fun setPresenceLinphoneSipAddressForPhoneNumber(sipAddress: String, phoneNumber: String) {
        val contentResolver = coreContext.context.contentResolver
        val cursor = contentResolver.query(
            ContactsContract.Data.CONTENT_URI,
            arrayOf("data1"),
            "${ContactsContract.Data.RAW_CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ? AND data3 = ?",
            arrayOf(
                syncAccountRawId,
                AppUtils.getString(R.string.linphone_address_mime_type),
                phoneNumber
            ),
            null
        )
        val count = cursor?.count ?: 0
        val data1 = if (count > 0) {
            if (cursor?.moveToFirst() == true) {
                try {
                    cursor.getString(cursor.getColumnIndexOrThrow("data1"))
                } catch (iae: IllegalArgumentException) {
                    Log.e("[Native Contact Editor] Exception: $iae")
                }
            } else null
        } else null
        cursor?.close()

        val address = if (sipAddress.endsWith(";user=phone")) {
            sipAddress.substring(0, sipAddress.length - ";user=phone".length)
        } else sipAddress

        if (count == 0) {
            Log.i("[Native Contact Editor] No existing presence information found for this phone number ($phoneNumber) & SIP address ($address), let's add it")
            addPresenceLinphoneSipAddressForPhoneNumber(address, phoneNumber)
        } else {
            if (data1 != null && data1 == address) {
                Log.d("[Native Contact Editor] There is already an entry for this phone number and SIP address, skipping")
            } else {
                Log.w("[Native Contact Editor] There is already an entry for this phone number ($phoneNumber) but not for the same SIP address ($data1 != $address)")
                updatePresenceLinphoneSipAddressForPhoneNumber(address, phoneNumber)
            }
        }
    }

    private fun addPresenceLinphoneSipAddressForPhoneNumber(address: String, detail: String) {
        val insert = ContentProviderOperation.newInsert(contactUri)
            .withValue(ContactsContract.Data.RAW_CONTACT_ID, syncAccountRawId)
            .withValue(
                ContactsContract.Data.MIMETYPE,
                AppUtils.getString(R.string.linphone_address_mime_type)
            )
            .withValue("data1", address) // value
            .withValue("data2", AppUtils.getString(R.string.app_name)) // summary
            .withValue("data3", detail) // detail
            .build()
        addChanges(insert)
    }

    private fun updatePresenceLinphoneSipAddressForPhoneNumber(
        sipAddress: String,
        phoneNumber: String
    ) {
        val update = ContentProviderOperation.newUpdate(contactUri)
            .withSelection(
                presenceUpdateSelection,
                arrayOf(
                    friend.refKey,
                    AppUtils.getString(R.string.linphone_address_mime_type),
                    phoneNumber
                )
            )
            .withValue(
                ContactsContract.Data.MIMETYPE,
                AppUtils.getString(R.string.linphone_address_mime_type)
            )
            .withValue("data1", sipAddress) // value
            .withValue("data2", AppUtils.getString(R.string.app_name)) // summary
            .withValue("data3", phoneNumber) // detail
            .build()
        addChanges(update)
    }

    private fun updatePicture() {
        val value = pictureByteArray
        val id = rawId
        if (value == null || id == null) return

        try {
            val uri = Uri.withAppendedPath(
                ContentUris.withAppendedId(RawContacts.CONTENT_URI, id.toLong()),
                RawContacts.DisplayPhoto.CONTENT_DIRECTORY
            )
            val contentResolver = coreContext.context.contentResolver
            val assetFileDescriptor = contentResolver.openAssetFileDescriptor(uri, "rw")
            val outputStream = assetFileDescriptor?.createOutputStream()
            outputStream?.write(value)
            outputStream?.close()
            assetFileDescriptor?.close()
            Log.i("[Native Contact Editor] Picture updated")
        } catch (e: Exception) {
            Log.e("[Native Contact Editor] Failed to update picture, raised exception: $e")
        }

        pictureByteArray = null
    }
}
