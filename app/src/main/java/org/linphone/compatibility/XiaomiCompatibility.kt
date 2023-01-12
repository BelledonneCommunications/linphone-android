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
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.R
import org.linphone.contact.getThumbnailUri
import org.linphone.core.Call
import org.linphone.core.Friend
import org.linphone.core.tools.Log
import org.linphone.notifications.Notifiable
import org.linphone.notifications.NotificationsManager
import org.linphone.utils.ImageUtils
import org.linphone.utils.LinphoneUtils

@TargetApi(26)
class XiaomiCompatibility {
    companion object {
        fun createIncomingCallNotification(
            context: Context,
            call: Call,
            notifiable: Notifiable,
            pendingIntent: PendingIntent,
            notificationsManager: NotificationsManager
        ): Notification {
            val contact: Friend?
            val roundPicture: Bitmap?
            val displayName: String
            val address: String
            val info: String

            val remoteContact = call.remoteContact
            val conferenceAddress = if (remoteContact != null) coreContext.core.interpretUrl(remoteContact, false) else null
            val conferenceInfo = if (conferenceAddress != null) coreContext.core.findConferenceInformationFromUri(conferenceAddress) else null
            if (conferenceInfo == null) {
                Log.i("[Notifications Manager] No conference info found for remote contact address $remoteContact")
                contact = coreContext.contactsManager.findContactByAddress(call.remoteAddress)
                roundPicture =
                    ImageUtils.getRoundBitmapFromUri(context, contact?.getThumbnailUri())
                displayName = contact?.name ?: LinphoneUtils.getDisplayName(call.remoteAddress)
                address = LinphoneUtils.getDisplayableAddress(call.remoteAddress)
                info = context.getString(R.string.incoming_call_notification_title)
            } else {
                contact = null
                displayName = conferenceInfo.subject ?: context.getString(R.string.conference)
                address = LinphoneUtils.getDisplayableAddress(conferenceInfo.organizer)
                roundPicture = coreContext.contactsManager.groupBitmap
                info = context.getString(R.string.incoming_group_call_notification_title)
                Log.i("[Notifications Manager] Displaying incoming group call notification with subject $displayName and remote contact address $remoteContact")
            }

            val builder = NotificationCompat.Builder(context, context.getString(R.string.notification_channel_incoming_call_id))
                .addPerson(notificationsManager.getPerson(contact, displayName, roundPicture))
                .setSmallIcon(R.drawable.topbar_call_notification)
                .setLargeIcon(roundPicture ?: BitmapFactory.decodeResource(context.resources, R.drawable.voip_single_contact_avatar_alt))
                .setContentTitle(displayName)
                .setContentText(address)
                .setSubText(info)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setWhen(System.currentTimeMillis())
                .setAutoCancel(false)
                .setShowWhen(true)
                .setOngoing(true)
                .setColor(ContextCompat.getColor(context, R.color.primary_color))
                .setFullScreenIntent(pendingIntent, true)
                .addAction(notificationsManager.getCallDeclineAction(notifiable))
                .addAction(notificationsManager.getCallAnswerAction(notifiable))

            if (!corePreferences.preventInterfaceFromShowingUp) {
                builder.setContentIntent(pendingIntent)
            }

            return builder.build()
        }
    }
}
