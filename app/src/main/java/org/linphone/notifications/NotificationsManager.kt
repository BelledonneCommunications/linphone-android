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
package org.linphone.notifications

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.webkit.MimeTypeMap
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.navigation.NavDeepLinkBuilder
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.R
import org.linphone.activities.call.CallActivity
import org.linphone.activities.call.IncomingCallActivity
import org.linphone.activities.call.OutgoingCallActivity
import org.linphone.activities.main.MainActivity
import org.linphone.compatibility.Compatibility
import org.linphone.contact.Contact
import org.linphone.core.*
import org.linphone.core.tools.Log
import org.linphone.utils.FileUtils
import org.linphone.utils.ImageUtils
import org.linphone.utils.LinphoneUtils

private class Notifiable(val notificationId: Int) {
    val messages: ArrayList<NotifiableMessage> = arrayListOf()

    var isGroup: Boolean = false
    var groupTitle: String? = null
    var localIdentity: String? = null
    var myself: String? = null
}

private class NotifiableMessage(
    var message: String,
    val contact: Contact?,
    val sender: String,
    val time: Long,
    val senderAvatar: Bitmap? = null,
    var filePath: Uri? = null,
    var fileMime: String? = null
)

class NotificationsManager(private val context: Context) {
    companion object {
        const val CHAT_NOTIFICATIONS_GROUP = "CHAT_NOTIF_GROUP"
        const val KEY_TEXT_REPLY = "key_text_reply"
        const val INTENT_NOTIF_ID = "NOTIFICATION_ID"
        const val INTENT_REPLY_NOTIF_ACTION = "org.linphone.REPLY_ACTION"
        const val INTENT_HANGUP_CALL_NOTIF_ACTION = "org.linphone.HANGUP_CALL_ACTION"
        const val INTENT_ANSWER_CALL_NOTIF_ACTION = "org.linphone.ANSWER_CALL_ACTION"
        const val INTENT_LOCAL_IDENTITY = "LOCAL_IDENTITY"
        const val INTENT_MARK_AS_READ_ACTION = "org.linphone.MARK_AS_READ_ACTION"

        private const val SERVICE_NOTIF_ID = 1
        private const val MISSED_CALLS_NOTIF_ID = 2
    }

    var currentlyDisplayedChatRoomAddress: String? = null
        set(value) {
            field = value
            if (value != null) {
                // When a chat room becomes visible, cancel unread chat notification if any
                val notifiable: Notifiable? = chatNotificationsMap[value]
                if (notifiable != null) {
                    cancel(notifiable.notificationId)
                    notifiable.messages.clear()
                }
            }
        }

    private val notificationManager: NotificationManagerCompat by lazy {
        NotificationManagerCompat.from(context)
    }
    private val chatNotificationsMap: HashMap<String, Notifiable> = HashMap()
    private val callNotificationsMap: HashMap<String, Notifiable> = HashMap()

    private var lastNotificationId: Int = 5
    private var currentForegroundServiceNotificationId: Int = 0
    private var serviceNotification: Notification? = null
    var service: CoreService? = null

    private val listener: CoreListenerStub = object : CoreListenerStub() {
        override fun onCallStateChanged(
            core: Core?,
            call: Call?,
            state: Call.State?,
            message: String?
        ) {
            if (call == null) return
            Log.i("[Notifications Manager] Call state changed [$state]")

            when (state) {
                Call.State.IncomingEarlyMedia, Call.State.IncomingReceived -> displayIncomingCallNotification(call)
                Call.State.End, Call.State.Error -> dismissCallNotification(call)
                Call.State.Released -> {
                    if (call.callLog.status == Call.Status.Missed ||
                        call.callLog.status == Call.Status.Aborted ||
                            call.callLog.status == Call.Status.EarlyAborted) {
                        displayMissedCallNotification(call)
                    }
                }
                else -> displayCallNotification(call)
            }
        }

        override fun onMessageReceived(core: Core, room: ChatRoom, message: ChatMessage) {
            if (message.isOutgoing) return

            if (currentlyDisplayedChatRoomAddress == room.peerAddress.asStringUriOnly()) {
                Log.i("[Notifications Manager] Chat room is currently displayed, do not notify received message")
                return
            }

            if (message.errorInfo.reason == Reason.UnsupportedContent) {
                Log.w("[Notifications Manager] Received message with unsupported content, do not notify")
                return
            }

            if (!message.hasTextContent() && message.fileTransferInformation == null) {
                Log.w("[Notifications Manager] Received message with neither text or attachment, do not notify")
                return
            }

            displayIncomingChatNotification(room, message)
        }
    }

    val chatListener: ChatMessageListener = object : ChatMessageListenerStub() {
        override fun onMsgStateChanged(message: ChatMessage, state: ChatMessage.State) {
            message.userData ?: return
            val id = message.userData as Int
            Log.i("[Notifications Manager] Reply message state changed [$state] for id $id")

            if (state != ChatMessage.State.InProgress) {
                // No need to be called here twice
                message.removeListener(this)
            }

            if (state == ChatMessage.State.Delivered || state == ChatMessage.State.Displayed) {
                val address = message.chatRoom.peerAddress.asStringUriOnly()
                val notifiable = chatNotificationsMap[address]
                if (notifiable != null) {
                    if (notifiable.notificationId != id) {
                        Log.w("[Notifications Manager] ID doesn't match: ${notifiable.notificationId} != $id")
                    }
                    displayReplyMessageNotification(message, notifiable)
                } else {
                    Log.e("[Notifications Manager] Couldn't find notification for chat room $address")
                    cancel(id)
                }
            } else if (state == ChatMessage.State.NotDelivered) {
                Log.e("[Notifications Manager] Reply wasn't delivered")
                cancel(id)
            }
        }
    }

    init {
        notificationManager.cancelAll()

        Compatibility.createNotificationChannels(context, notificationManager)
    }

    fun onCoreReady() {
        coreContext.core.addListener(listener)
    }

    fun destroy() {
        // Don't use cancelAll to keep message notifications !
        // When a message is received by a push, it will create a CoreService
        // but it might be getting killed quite quickly after that
        // causing the notification to be missed by the user...
        Log.i("[Notifications Manager] Getting destroyed, clearing foreground Service & call notifications")

        if (currentForegroundServiceNotificationId > 0) {
            notificationManager.cancel(currentForegroundServiceNotificationId)
        }

        for (notifiable in callNotificationsMap.values) {
            notificationManager.cancel(notifiable.notificationId)
        }

        stopForegroundNotification()
        coreContext.core.removeListener(listener)
    }

    private fun notify(id: Int, notification: Notification) {
        Log.i("[Notifications Manager] Notifying $id")
        notificationManager.notify(id, notification)
    }

    fun cancel(id: Int) {
        Log.i("[Notifications Manager] Canceling $id")
        notificationManager.cancel(id)
    }

    fun getSipUriForChatNotificationId(notificationId: Int): String? {
        for (address in chatNotificationsMap.keys) {
            if (chatNotificationsMap[address]?.notificationId == notificationId) {
                return address
            }
        }
        return null
    }

    fun getSipUriForCallNotificationId(notificationId: Int): String? {
        for (address in callNotificationsMap.keys) {
            if (callNotificationsMap[address]?.notificationId == notificationId) {
                return address
            }
        }
        return null
    }

    /* Service related */

    fun startForeground() {
        val coreService = service
        if (coreService != null) {
            startForeground(coreService, useAutoStartDescription = false)
        } else {
            Log.e("[Notifications Manager] Can't start service as foreground, no service!")
        }
    }

    fun startCallForeground(coreService: CoreService) {
        service = coreService
        when {
            currentForegroundServiceNotificationId != 0 -> {
                Log.e("[Notifications Manager] There is already a foreground service notification")
            }
            coreContext.core.callsNb > 0 -> {
                // When this method will be called, we won't have any notification yet
                val call = coreContext.core.currentCall ?: coreContext.core.calls[0]
                when (call.state) {
                    Call.State.IncomingReceived, Call.State.IncomingEarlyMedia -> {
                        displayIncomingCallNotification(call, true)
                    }
                    else -> displayCallNotification(call, true)
                }
            }
        }
    }

    fun startForeground(coreService: CoreService, useAutoStartDescription: Boolean = true) {
        Log.i("[Notifications Manager] Starting Service as foreground")
        if (serviceNotification == null) {
            createServiceNotification(useAutoStartDescription)
        }
        currentForegroundServiceNotificationId = SERVICE_NOTIF_ID
        coreService.startForeground(currentForegroundServiceNotificationId, serviceNotification)
        service = coreService
    }

    private fun startForeground(notificationId: Int, callNotification: Notification) {
        if (currentForegroundServiceNotificationId == 0 && service != null) {
            Log.i("[Notifications Manager] Starting Service as foreground using call notification")
            currentForegroundServiceNotificationId = notificationId
            service?.startForeground(currentForegroundServiceNotificationId, callNotification)
        }
   }

    private fun stopForegroundNotification() {
        if (service != null) {
            Log.i("[Notifications Manager] Stopping Service as foreground")
            service?.stopForeground(true)
            currentForegroundServiceNotificationId = 0
        }
    }

    fun stopForegroundNotificationIfPossible() {
        if (service != null && currentForegroundServiceNotificationId == SERVICE_NOTIF_ID && !corePreferences.keepServiceAlive) {
            stopForegroundNotification()
        }
    }

    fun stopCallForeground() {
        if (service != null && currentForegroundServiceNotificationId != SERVICE_NOTIF_ID && !corePreferences.keepServiceAlive) {
            stopForegroundNotification()
        }
    }

    private fun createServiceNotification(useAutoStartDescription: Boolean = false) {
        val pendingIntent = NavDeepLinkBuilder(context)
            .setComponentName(MainActivity::class.java)
            .setGraph(R.navigation.main_nav_graph)
            .setDestination(R.id.dialerFragment)
            .createPendingIntent()

        serviceNotification = NotificationCompat.Builder(context, context.getString(R.string.notification_channel_service_id))
            .setContentTitle(context.getString(R.string.service_name))
            .setContentText(if (useAutoStartDescription) context.getString(R.string.service_auto_start_description) else context.getString(R.string.service_description))
            .setSmallIcon(R.drawable.topbar_service_notification)
            .setContentIntent(pendingIntent)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setWhen(System.currentTimeMillis())
            .setShowWhen(true)
            .setOngoing(true)
            .setColor(ContextCompat.getColor(context, R.color.primary_color))
            .build()
    }

    /* Call related */

    private fun getNotifiableForCall(call: Call): Notifiable {
        val address = call.remoteAddress.asStringUriOnly()
        var notifiable: Notifiable? = callNotificationsMap[address]
        if (notifiable == null) {
            notifiable = Notifiable(lastNotificationId)
            lastNotificationId += 1
            callNotificationsMap[address] = notifiable
        }
        return notifiable
    }

    private fun displayIncomingCallNotification(call: Call, useAsForeground: Boolean = false) {
        val address = LinphoneUtils.getDisplayableAddress(call.remoteAddress)
        val notifiable = getNotifiableForCall(call)

        if (notifiable.notificationId == currentForegroundServiceNotificationId) {
            Log.w("[Notifications Manager] Incoming call notification already displayed by foreground service, skipping")
            return
        }

        val contact: Contact? = coreContext.contactsManager.findContactByAddress(call.remoteAddress)
        val pictureUri = contact?.getContactThumbnailPictureUri()
        val roundPicture = ImageUtils.getRoundBitmapFromUri(context, pictureUri)
        val displayName = contact?.fullName ?: LinphoneUtils.getDisplayName(call.remoteAddress)

        val incomingCallNotificationIntent = Intent(context, IncomingCallActivity::class.java)
        incomingCallNotificationIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pendingIntent = PendingIntent.getActivity(context, 0, incomingCallNotificationIntent, PendingIntent.FLAG_UPDATE_CURRENT)

        val notificationLayoutHeadsUp = RemoteViews(context.packageName, R.layout.call_incoming_notification_heads_up)
        notificationLayoutHeadsUp.setTextViewText(R.id.caller, displayName)
        notificationLayoutHeadsUp.setTextViewText(R.id.sip_uri, address)
        notificationLayoutHeadsUp.setTextViewText(R.id.incoming_call_info, context.getString(R.string.incoming_call_notification_title))

        if (roundPicture != null) {
            notificationLayoutHeadsUp.setImageViewBitmap(R.id.caller_picture, roundPicture)
        }

        val notification = NotificationCompat.Builder(context, context.getString(R.string.notification_channel_incoming_call_id))
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setSmallIcon(R.drawable.topbar_call_notification)
            .setContentTitle(displayName)
            .setContentText(context.getString(R.string.incoming_call_notification_title))
            .setContentIntent(pendingIntent)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setWhen(System.currentTimeMillis())
            .setAutoCancel(false)
            .setShowWhen(true)
            .setOngoing(true)
            .setColor(ContextCompat.getColor(context, R.color.primary_color))
            .setFullScreenIntent(pendingIntent, true)
            .addAction(getCallDeclineAction(notifiable.notificationId))
            .addAction(getCallAnswerAction(notifiable.notificationId))
            .setCustomHeadsUpContentView(notificationLayoutHeadsUp)
            .build()

        Log.i("[Notifications Manager] Notifying incoming call notification")
        notify(notifiable.notificationId, notification)

        if (useAsForeground) {
            Log.i("[Notifications Manager] Notifying incoming call notification for foreground service")
            startForeground(notifiable.notificationId, notification)
        }
    }

    fun displayMissedCallNotification(call: Call) {
        val missedCallCount: Int = call.core.missedCallsCount
        val body: String
        if (missedCallCount > 1) {
            body = context.getString(R.string.missed_calls_notification_body)
                .format(missedCallCount)
            Log.i("[Notifications Manager] Updating missed calls notification count to $missedCallCount")
        } else {
            val contact: Contact? = coreContext.contactsManager.findContactByAddress(call.remoteAddress)
            body = context.getString(R.string.missed_call_notification_body)
                .format(contact?.fullName ?: LinphoneUtils.getDisplayName(call.remoteAddress))
            Log.i("[Notifications Manager] Creating missed call notification")
        }

        val pendingIntent = NavDeepLinkBuilder(context)
            .setComponentName(MainActivity::class.java)
            .setGraph(R.navigation.main_nav_graph)
            .setDestination(R.id.masterCallLogsFragment)
            .createPendingIntent()

        val notification = NotificationCompat.Builder(
            context, context.getString(R.string.notification_channel_incoming_call_id))
            .setContentTitle(context.getString(R.string.missed_call_notification_title))
            .setContentText(body)
            .setSmallIcon(R.drawable.topbar_missed_call_notification)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setCategory(Notification.CATEGORY_EVENT)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setWhen(System.currentTimeMillis())
            .setShowWhen(true)
            .setNumber(missedCallCount)
            .setColor(ContextCompat.getColor(context, R.color.notification_led_color))
            .build()
        notify(MISSED_CALLS_NOTIF_ID, notification)
    }

    fun dismissMissedCallNotification() {
        cancel(MISSED_CALLS_NOTIF_ID)
    }

    fun displayCallNotification(call: Call, useAsForeground: Boolean = false) {
        val notifiable = getNotifiableForCall(call)

        val contact: Contact? = coreContext.contactsManager.findContactByAddress(call.remoteAddress)
        val pictureUri = contact?.getContactThumbnailPictureUri()
        val roundPicture = ImageUtils.getRoundBitmapFromUri(context, pictureUri)
        val displayName = contact?.fullName ?: LinphoneUtils.getDisplayName(call.remoteAddress)

        val stringResourceId: Int
        val iconResourceId: Int
        val callActivity: Class<*>
        when (call.state) {
            Call.State.Paused, Call.State.Pausing, Call.State.PausedByRemote -> {
                callActivity = CallActivity::class.java
                stringResourceId = R.string.call_notification_paused
                iconResourceId = R.drawable.topbar_call_paused_notification
            }
            Call.State.OutgoingRinging, Call.State.OutgoingProgress, Call.State.OutgoingInit, Call.State.OutgoingEarlyMedia -> {
                callActivity = OutgoingCallActivity::class.java
                stringResourceId = R.string.call_notification_outgoing
                iconResourceId = if (call.params.videoEnabled()) {
                    R.drawable.topbar_videocall_notification
                } else {
                    R.drawable.topbar_call_notification
                }
            }
            else -> {
                callActivity = CallActivity::class.java
                stringResourceId = R.string.call_notification_active
                iconResourceId = if (call.currentParams.videoEnabled()) {
                    R.drawable.topbar_videocall_notification
                } else {
                    R.drawable.topbar_call_notification
                }
            }
        }

        val callNotificationIntent = Intent(context, callActivity)
        callNotificationIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pendingIntent = PendingIntent.getActivity(context, 0, callNotificationIntent, PendingIntent.FLAG_UPDATE_CURRENT)

        val notification = NotificationCompat.Builder(
            context, context.getString(R.string.notification_channel_service_id))
            .setContentTitle(contact?.fullName ?: displayName)
            .setContentText(context.getString(stringResourceId))
            .setSmallIcon(iconResourceId)
            .setLargeIcon(roundPicture)
            .setAutoCancel(false)
            .setContentIntent(pendingIntent)
            .setCategory(Notification.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setWhen(System.currentTimeMillis())
            .setShowWhen(true)
            .setOngoing(true)
            .setColor(ContextCompat.getColor(context, R.color.notification_led_color))
            .addAction(getCallDeclineAction(notifiable.notificationId))
            .build()
        notify(notifiable.notificationId, notification)

        if (useAsForeground) {
            startForeground(notifiable.notificationId, notification)
        }
    }

    private fun dismissCallNotification(call: Call) {
        val address = call.remoteAddress.asStringUriOnly()
        val notifiable: Notifiable? = callNotificationsMap[address]
        if (notifiable != null) {
            cancel(notifiable.notificationId)
            callNotificationsMap.remove(address)
        }
    }

    /* Chat related */

    private fun displayChatNotifiable(room: ChatRoom, notifiable: Notifiable) {
        val localAddress = room.localAddress.asStringUriOnly()
        val peerAddress = room.peerAddress.asStringUriOnly()
        val args = Bundle()
        args.putString("RemoteSipUri", peerAddress)
        args.putString("LocalSipUri", localAddress)

        val pendingIntent = NavDeepLinkBuilder(context)
            .setComponentName(MainActivity::class.java)
            .setGraph(R.navigation.main_nav_graph)
            .setDestination(R.id.masterChatRoomsFragment)
            .setArguments(args)
            .createPendingIntent()

        val notification = createMessageNotification(notifiable, pendingIntent, "$localAddress#$peerAddress")
        if (notification != null) notify(notifiable.notificationId, notification)
    }

    private fun displayIncomingChatNotification(room: ChatRoom, message: ChatMessage) {
        val contact: Contact? = coreContext.contactsManager.findContactByAddress(message.fromAddress)
        val pictureUri = contact?.getContactThumbnailPictureUri()
        val roundPicture = ImageUtils.getRoundBitmapFromUri(context, pictureUri)
        val displayName = contact?.fullName ?: LinphoneUtils.getDisplayName(message.fromAddress)

        val notifiable = getNotifiableForRoom(room)
        var text = ""
        if (message.hasTextContent()) text = message.textContent.orEmpty()
        else {
            for (content in message.contents) {
                text += content.name
            }
        }
        val notifiableMessage = NotifiableMessage(text, contact, displayName, message.time, senderAvatar = roundPicture)
        notifiable.messages.add(notifiableMessage)

        for (content in message.contents) {
            if (content.isFile) {
                val path = content.filePath
                if (path != null) {
                    val contentUri: Uri = FileUtils.getPublicFilePath(context, path)
                    val filePath: String = contentUri.toString()
                    val extension = FileUtils.getExtensionFromFileName(filePath)
                    if (extension.isNotEmpty()) {
                        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
                        notifiableMessage.filePath = contentUri
                        notifiableMessage.fileMime = mime
                        Log.i("[Notifications Manager] Added file $contentUri with MIME $mime to notification")
                    } else {
                        Log.e("[Notifications Manager] Couldn't find extension for incoming message with file $path")
                    }
                }
            }
        }

        if (room.hasCapability(ChatRoomCapabilities.OneToOne.toInt())) {
            notifiable.isGroup = false
        } else {
            notifiable.isGroup = true
            notifiable.groupTitle = room.subject
        }

        notifiable.myself = LinphoneUtils.getDisplayName(room.localAddress)
        notifiable.localIdentity = room.localAddress.asStringUriOnly()

        displayChatNotifiable(room, notifiable)
    }

    private fun getNotifiableForRoom(room: ChatRoom): Notifiable {
        val address = room.peerAddress.asStringUriOnly()
        var notifiable: Notifiable? = chatNotificationsMap[address]
        if (notifiable == null) {
            notifiable = Notifiable(lastNotificationId)
            lastNotificationId += 1
            chatNotificationsMap[address] = notifiable
        }
        return notifiable
    }

    private fun displayReplyMessageNotification(message: ChatMessage, notifiable: Notifiable) {
        Log.i("[Notifications Manager] Updating message notification with reply for notification ${notifiable.notificationId}")

        val reply = NotifiableMessage(
            message.textContent.orEmpty(),
            null,
            notifiable.myself ?: LinphoneUtils.getDisplayName(message.fromAddress),
            System.currentTimeMillis()
        )
        notifiable.messages.add(reply)

        displayChatNotifiable(message.chatRoom, notifiable)
    }

    fun dismissChatNotification(room: ChatRoom) {
        val address = room.peerAddress.asStringUriOnly()
        val notifiable: Notifiable? = chatNotificationsMap[address]
        if (notifiable != null) {
            Log.i("[Notifications Manager] Dismissing notification for chat room $room with id ${notifiable.notificationId}")
            cancel(notifiable.notificationId)
        }
    }

    /* Notifications */

    private fun createMessageNotification(
        notifiable: Notifiable,
        pendingIntent: PendingIntent,
        shortcutId: String
    ): Notification? {
        val me = Person.Builder().setName(notifiable.myself).build()
        val style = NotificationCompat.MessagingStyle(me)
        val largeIcon: Bitmap? = notifiable.messages.last().senderAvatar

        for (message in notifiable.messages) {
            val contact = message.contact
            val person = if (contact != null) {
                contact.getPerson()
            } else {
                val builder = Person.Builder().setName(message.sender)
                val userIcon = if (message.senderAvatar != null) IconCompat.createWithBitmap(message.senderAvatar) else IconCompat.createWithResource(context, R.drawable.avatar)
                if (userIcon != null) builder.setIcon(userIcon)
                builder.build()
            }

            val msg = NotificationCompat.MessagingStyle.Message(message.message, message.time, person)
            if (message.filePath != null) msg.setData(message.fileMime, message.filePath)
            style.addMessage(msg)
        }

        if (notifiable.isGroup) {
            style.conversationTitle = notifiable.groupTitle
        }
        style.isGroupConversation = notifiable.isGroup

        return NotificationCompat.Builder(context, context.getString(R.string.notification_channel_chat_id))
            .setSmallIcon(R.drawable.topbar_chat_notification)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setLargeIcon(largeIcon)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setGroup(CHAT_NOTIFICATIONS_GROUP)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setNumber(notifiable.messages.size)
            .setWhen(System.currentTimeMillis())
            .setShowWhen(true)
            .setStyle(style)
            .setColor(ContextCompat.getColor(context, R.color.primary_color))
            .addAction(getReplyMessageAction(notifiable))
            .addAction(getMarkMessageAsReadAction(notifiable))
            .setShortcutId(shortcutId)
            .build()
    }

    /* Notifications actions */

    private fun getCallAnswerAction(callId: Int): NotificationCompat.Action {
        val answerIntent = Intent(context, NotificationBroadcastReceiver::class.java)
        answerIntent.action = INTENT_ANSWER_CALL_NOTIF_ACTION
        answerIntent.putExtra(INTENT_NOTIF_ID, callId)

        val answerPendingIntent = PendingIntent.getBroadcast(
            context, callId, answerIntent, PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Action.Builder(
            R.drawable.call_audio_start,
            context.getString(R.string.incoming_call_notification_answer_action_label),
            answerPendingIntent
        ).build()
    }

    private fun getCallDeclineAction(callId: Int): NotificationCompat.Action {
        val hangupIntent = Intent(context, NotificationBroadcastReceiver::class.java)
        hangupIntent.action = INTENT_HANGUP_CALL_NOTIF_ACTION
        hangupIntent.putExtra(INTENT_NOTIF_ID, callId)

        val hangupPendingIntent = PendingIntent.getBroadcast(
            context, callId, hangupIntent, PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Action.Builder(
            R.drawable.call_hangup,
            context.getString(R.string.incoming_call_notification_hangup_action_label),
            hangupPendingIntent
        ).build()
    }

    private fun getReplyMessageAction(notifiable: Notifiable): NotificationCompat.Action {
        val replyLabel =
            context.resources.getString(R.string.received_chat_notification_reply_label)
        val remoteInput =
            RemoteInput.Builder(KEY_TEXT_REPLY).setLabel(replyLabel).build()

        val replyIntent = Intent(context, NotificationBroadcastReceiver::class.java)
        replyIntent.action = INTENT_REPLY_NOTIF_ACTION
        replyIntent.putExtra(INTENT_NOTIF_ID, notifiable.notificationId)
        replyIntent.putExtra(INTENT_LOCAL_IDENTITY, notifiable.localIdentity)

        val replyPendingIntent = PendingIntent.getBroadcast(
            context,
            notifiable.notificationId,
            replyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Action.Builder(
            R.drawable.chat_send_over,
            context.getString(R.string.received_chat_notification_reply_label),
            replyPendingIntent
        )
            .addRemoteInput(remoteInput)
            .setAllowGeneratedReplies(true)
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
            .build()
    }

    private fun getMarkMessageAsReadAction(notifiable: Notifiable): NotificationCompat.Action {
        val markAsReadIntent = Intent(context, NotificationBroadcastReceiver::class.java)
        markAsReadIntent.action = INTENT_MARK_AS_READ_ACTION
        markAsReadIntent.putExtra(INTENT_NOTIF_ID, notifiable.notificationId)
        markAsReadIntent.putExtra(INTENT_LOCAL_IDENTITY, notifiable.localIdentity)

        val markAsReadPendingIntent = PendingIntent.getBroadcast(
            context,
            notifiable.notificationId,
            markAsReadIntent,
            PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Action.Builder(
            R.drawable.chat_send_over,
            context.getString(R.string.received_chat_notification_mark_as_read_label),
            markAsReadPendingIntent
        )
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ)
            .build()
    }
}
