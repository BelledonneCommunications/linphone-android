/*
 * Copyright (c) 2010-2024 Belledonne Communications SARL.
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
package org.linphone.ui.main.recordings.model

import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.core.Factory
import org.linphone.core.tools.Log
import org.linphone.utils.FileUtils
import org.linphone.utils.LinphoneUtils
import org.linphone.utils.TimestampUtils

class RecordingModel @WorkerThread constructor(val filePath: String, val fileName: String) {
    companion object {
        private const val TAG = "[Recording Model]"
    }

    val displayName: String

    val month: String

    val dateTime: String

    init {
        val withoutHeader = fileName.substring(LinphoneUtils.RECORDING_FILE_NAME_HEADER.length)
        val indexOfSeparator = withoutHeader.indexOf(
            LinphoneUtils.RECORDING_FILE_NAME_URI_TIMESTAMP_SEPARATOR
        )
        val sipUri = withoutHeader.substring(0, indexOfSeparator)
        val timestamp = withoutHeader.substring(
            indexOfSeparator + LinphoneUtils.RECORDING_FILE_NAME_URI_TIMESTAMP_SEPARATOR.length,
            withoutHeader.length - LinphoneUtils.RECORDING_FILE_EXTENSION.length
        )
        Log.i("$TAG Extract SIP URI [$sipUri] and timestamp [$timestamp] from file [$fileName]")

        val parsedTimestamp = timestamp.toLong()
        month = TimestampUtils.month(parsedTimestamp, timestampInSecs = false)
        val date = TimestampUtils.toString(
            parsedTimestamp,
            timestampInSecs = false,
            onlyDate = true,
            shortDate = false
        )
        val time = TimestampUtils.timeToString(parsedTimestamp, timestampInSecs = false)
        dateTime = "$date - $time"

        val sipAddress = Factory.instance().createAddress(sipUri)
        displayName = if (sipAddress != null) {
            val contact = coreContext.contactsManager.findContactByAddress(sipAddress)
            contact?.name ?: LinphoneUtils.getDisplayName(sipAddress)
        } else {
            sipUri
        }
    }

    @UiThread
    suspend fun delete() {
        Log.i("$TAG Deleting call recording [$filePath]")
        FileUtils.deleteFile(filePath)
    }
}
