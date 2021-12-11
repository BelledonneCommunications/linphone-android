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
package org.linphone.compatibility

import android.annotation.TargetApi
import android.app.*
import android.content.Context
import android.graphics.BitmapFactory
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import org.linphone.LinphoneApplication
import org.linphone.R
import org.linphone.contact.Contact
import org.linphone.core.Call
import org.linphone.notifications.NotificationsManager
import org.linphone.utils.ImageUtils
import org.linphone.utils.LinphoneUtils

@TargetApi(26)
class XiaomiCompatibility {
    companion object {
        fun createIncomingCallNotificationBuilder(
            context: Context,
            call: Call,
            notificationsManager: NotificationsManager
        ): NotificationCompat.Builder {
            val contact: Contact? = LinphoneApplication.coreContext.contactsManager.findContactByAddress(call.remoteAddress)
            val pictureUri = contact?.getContactThumbnailPictureUri()
            val roundPicture = ImageUtils.getRoundBitmapFromUri(context, pictureUri)
            val displayName = contact?.fullName ?: LinphoneUtils.getDisplayName(call.remoteAddress)
            val address = LinphoneUtils.getDisplayableAddress(call.remoteAddress)

            return NotificationCompat.Builder(context, context.getString(R.string.notification_channel_incoming_call_id))
                .addPerson(notificationsManager.getPerson(contact, displayName, roundPicture))
                .setSmallIcon(R.drawable.topbar_call_notification)
                .setLargeIcon(roundPicture ?: BitmapFactory.decodeResource(context.resources, R.drawable.avatar))
                .setContentTitle(displayName)
                .setContentText(address)
                .setSubText(context.getString(R.string.incoming_call_notification_title))
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setWhen(System.currentTimeMillis())
                .setAutoCancel(false)
                .setShowWhen(true)
                .setOngoing(true)
                .setColor(ContextCompat.getColor(context, R.color.primary_color))
        }
    }
}
