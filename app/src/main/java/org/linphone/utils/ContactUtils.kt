/*
 * Copyright (c) 2010-2021 Belledonne Communications SARL.
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
package org.linphone.utils

import android.content.ContentResolver
import android.net.Uri
import android.provider.ContactsContract
import java.io.*
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.core.tools.Log

class ContactUtils {
    companion object {
        fun getContactVcardFilePath(contactUri: Uri): String? {
            val contentResolver: ContentResolver = coreContext.context.contentResolver
            val lookupUri = ContactsContract.Contacts.getLookupUri(contentResolver, contactUri)
            Log.i("[Contact Utils] Contact lookup URI is $lookupUri")

            val contactID = FileUtils.getNameFromFilePath(lookupUri.toString())
            Log.i("[Contact Utils] Contact ID is $contactID")

            val contact = coreContext.contactsManager.findContactById(contactID)
            if (contact == null) {
                Log.e("[Contact Utils] Failed to find contact with ID $contactID")
                return null
            }

            val vcard = contact.vcard?.asVcard4String()
            if (vcard == null) {
                Log.e("[Contact Utils] Failed to get vCard from contact $contactID")
                return null
            }

            val contactName = contact.name?.replace(" ", "_") ?: contactID
            val vcardPath = FileUtils.getFileStoragePath("$contactName.vcf")
            val inputStream = ByteArrayInputStream(vcard.toByteArray())
            try {
                FileOutputStream(vcardPath).use { out ->
                    val buffer = ByteArray(4096)
                    var bytesRead: Int
                    while (inputStream.read(buffer).also { bytesRead = it } >= 0) {
                        out.write(buffer, 0, bytesRead)
                    }
                }
            } catch (e: IOException) {
                Log.e("[Contact Utils] creating vcard file exception: $e")
                return null
            }

            Log.i("[Contact Utils] Contact vCard path is $vcardPath")
            return vcardPath.absolutePath
        }
    }
}
