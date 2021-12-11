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
import android.app.Notification
import android.app.PendingIntent
import android.app.Person
import android.content.Context
import androidx.core.content.ContextCompat
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.R
import org.linphone.contact.Contact
import org.linphone.core.Call
import org.linphone.notifications.Notifiable
import org.linphone.notifications.NotificationsManager
import org.linphone.utils.ImageUtils
import org.linphone.utils.LinphoneUtils

@TargetApi(31)
class Api31Compatibility {
    companion object {
        fun getUpdateCurrentPendingIntentFlag(): Int {
            return PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        }

        fun createIncomingCallNotification(
            context: Context,
            call: Call,
            notifiable: Notifiable,
            pendingIntent: PendingIntent,
            notificationsManager: NotificationsManager
        ): Notification {
            val contact: Contact? = coreContext.contactsManager.findContactByAddress(call.remoteAddress)
            val pictureUri = contact?.getContactThumbnailPictureUri()
            val roundPicture = ImageUtils.getRoundBitmapFromUri(context, pictureUri)
            val displayName = contact?.fullName ?: LinphoneUtils.getDisplayName(call.remoteAddress)

            val person = notificationsManager.getPerson(contact, displayName, roundPicture)
            val caller = Person.Builder()
                .setName(person.name)
                .setIcon(person.icon?.toIcon(context))
                .setUri(person.uri)
                .setKey(person.key)
                .setImportant(person.isImportant)
                .build()
            val declineIntent = notificationsManager.getCallDeclinePendingIntent(notifiable)
            val answerIntent = notificationsManager.getCallAnswerPendingIntent(notifiable)
            val builder = Notification.Builder(context, context.getString(R.string.notification_channel_incoming_call_id))
                .setStyle(Notification.CallStyle.forIncomingCall(caller, declineIntent, answerIntent))
                .setSmallIcon(R.drawable.topbar_call_notification)
                .setCategory(Notification.CATEGORY_CALL)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setPriority(Notification.PRIORITY_HIGH)
                .setWhen(System.currentTimeMillis())
                .setAutoCancel(false)
                .setShowWhen(true)
                .setOngoing(true)
                .setColor(ContextCompat.getColor(context, R.color.primary_color))
                .setFullScreenIntent(pendingIntent, true)

            if (!corePreferences.preventInterfaceFromShowingUp) {
                builder.setContentIntent(pendingIntent)
            }

            return builder.build()
        }
    }
}
