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
package org.linphone.notifications

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service.STOP_FOREGROUND_REMOVE
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.net.Uri
import androidx.annotation.AnyThread
import androidx.annotation.MainThread
import androidx.annotation.WorkerThread
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.content.ContextCompat
import androidx.core.content.LocusIdCompat
import androidx.core.graphics.drawable.IconCompat
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.R
import org.linphone.contacts.getPerson
import org.linphone.core.Address
import org.linphone.core.Call
import org.linphone.core.ChatMessage
import org.linphone.core.ChatMessageReaction
import org.linphone.core.ChatRoom
import org.linphone.core.Core
import org.linphone.core.CoreForegroundService
import org.linphone.core.CoreListenerStub
import org.linphone.core.Friend
import org.linphone.core.tools.Log
import org.linphone.ui.voip.VoipActivity
import org.linphone.utils.AppUtils
import org.linphone.utils.ImageUtils
import org.linphone.utils.LinphoneUtils

class NotificationsManager @MainThread constructor(private val context: Context) {
    companion object {
        private const val TAG = "[Notifications Manager]"

        const val INTENT_HANGUP_CALL_NOTIF_ACTION = "org.linphone.HANGUP_CALL_ACTION"
        const val INTENT_ANSWER_CALL_NOTIF_ACTION = "org.linphone.ANSWER_CALL_ACTION"

        const val INTENT_CALL_ID = "CALL_ID"
        const val INTENT_NOTIF_ID = "NOTIFICATION_ID"

        const val CHAT_TAG = "Chat"
        const val CHAT_NOTIFICATIONS_GROUP = "CHAT_NOTIF_GROUP"
    }

    private val notificationManager: NotificationManagerCompat by lazy {
        NotificationManagerCompat.from(context)
    }

    private val coreListener = object : CoreListenerStub() {
        @WorkerThread
        override fun onCallStateChanged(
            core: Core,
            call: Call,
            state: Call.State?,
            message: String
        ) {
            when (state) {
                Call.State.IncomingReceived, Call.State.IncomingEarlyMedia -> {
                    showCallNotification(call, true)
                }
                Call.State.Connected -> {
                    showCallNotification(call, false)
                }
                Call.State.End, Call.State.Error -> {
                    dismissCallNotification(call)
                }
                else -> {
                }
            }
        }

        @WorkerThread
        override fun onLastCallEnded(core: Core) {
            Log.i("$TAG Last call ended, stopping foreground service")
            stopCallForeground()
        }

        @WorkerThread
        override fun onMessagesReceived(
            core: Core,
            chatRoom: ChatRoom,
            messages: Array<out ChatMessage>
        ) {
            Log.i("$TAG Received ${messages.size} aggregated messages")
            if (corePreferences.disableChat) return

            if (chatRoom.muted) {
                val id = LinphoneUtils.getChatRoomId(chatRoom.localAddress, chatRoom.peerAddress)
                Log.i("$TAG Chat room $id has been muted")
                return
            }

            showChatRoomNotification(chatRoom, messages)
        }

        @WorkerThread
        override fun onNewMessageReaction(
            core: Core,
            chatRoom: ChatRoom,
            message: ChatMessage,
            reaction: ChatMessageReaction
        ) {
            val address = reaction.fromAddress
            val defaultAccountAddress = core.defaultAccount?.params?.identityAddress
            // Do not notify our own reactions, it won't be done anyway since the chat room is very likely to be currently displayed
            if (defaultAccountAddress != null && defaultAccountAddress.weakEqual(address)) return

            Log.i(
                "$TAG Reaction received [${reaction.body}] from [${address.asStringUriOnly()}] for chat message [$message]"
            )
            if (corePreferences.disableChat) return

            if (chatRoom.muted) {
                val id = LinphoneUtils.getChatRoomId(chatRoom.localAddress, chatRoom.peerAddress)
                Log.i("$TAG Chat room $id has been muted")
                return
            }
            if (coreContext.isAddressMyself(address)) {
                Log.i("$TAG Reaction has been sent by ourselves, do not notify it")
                return
            }

            if (reaction.body.isNotEmpty()) {
                showChatMessageReactionNotification(chatRoom, reaction.body, address, message)
            }
        }

        @WorkerThread
        override fun onReactionRemoved(
            core: Core,
            chatRoom: ChatRoom,
            message: ChatMessage,
            address: Address
        ) {
            Log.i(
                "$TAG [${address.asStringUriOnly()}] removed it's previously sent reaction for chat message [$message]"
            )
            if (corePreferences.disableChat) return

            if (chatRoom.muted) {
                val id = LinphoneUtils.getChatRoomId(chatRoom.localAddress, chatRoom.peerAddress)
                Log.i("$TAG Chat room $id has been muted")
                return
            }

            val chatRoomPeerAddress = chatRoom.peerAddress.asStringUriOnly()
            var notifiable: Notifiable? = chatNotificationsMap[chatRoomPeerAddress]
            if (notifiable == null) {
                Log.i("$TAG No notification for chat room [$chatRoomPeerAddress], nothing to do")
                return
            }

            val from = address.asStringUriOnly()
            val found = notifiable.messages.find {
                it.isReaction && it.reactionToMessageId == message.messageId && it.reactionFrom == from
            }
            if (found != null) {
                if (notifiable.messages.remove(found)) {
                    if (notifiable.messages.isNotEmpty()) {
                        Log.i(
                            "$TAG After removing original reaction notification there is still messages, updating notification"
                        )
                        val me = coreContext.contactsManager.getMePerson(chatRoom.localAddress)
                        val notification = createMessageNotification(
                            notifiable,
                            LinphoneUtils.getChatRoomId(chatRoom),
                            me
                        )
                        notify(notifiable.notificationId, notification, CHAT_TAG)
                    } else {
                        Log.i(
                            "$TAG After removing original reaction notification there is nothing left to display, remove notification"
                        )
                        notificationManager.cancel(CHAT_TAG, notifiable.notificationId)
                    }
                }
            } else {
                Log.w("$TAG Original reaction not found in currently displayed notification")
            }
        }
    }

    private var coreService: CoreForegroundService? = null

    private val callNotificationsMap: HashMap<String, Notifiable> = HashMap()
    private val chatNotificationsMap: HashMap<String, Notifiable> = HashMap()

    init {
        createServiceChannel()
        createIncomingCallNotificationChannel()
        createActiveCallNotificationChannel()
        createMessageChannel()

        for (notification in notificationManager.activeNotifications) {
            if (notification.tag.isNullOrEmpty()) {
                Log.w(
                    "$TAG Found existing (call?) notification [${notification.id}] without tag, cancelling it"
                )
                notificationManager.cancel(notification.id)
            }
        }
    }

    @MainThread
    fun onServiceStarted(service: CoreForegroundService) {
        Log.i("$TAG Service has been started")
        coreService = service

        coreContext.postOnCoreThread { core ->
            if (core.callsNb == 0) {
                Log.w("$TAG No call anymore, stopping service")
                stopCallForeground()
            } else {
                startCallForeground()
            }
        }
    }

    @MainThread
    fun onServiceDestroyed() {
        Log.i("$TAG Service has been destroyed")
        coreService = null
    }

    @WorkerThread
    fun onCoreStarted(core: Core) {
        Log.i("$TAG Core has been started")
        core.addListener(coreListener)
    }

    @WorkerThread
    fun onCoreStopped(core: Core) {
        Log.i("$TAG Getting destroyed, clearing foreground Service & call notifications")
        core.removeListener(coreListener)
    }

    @WorkerThread
    private fun showCallNotification(call: Call, isIncoming: Boolean) {
        val notifiable = getNotifiableForCall(call)

        val callNotificationIntent = Intent(context, VoipActivity::class.java)
        callNotificationIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            callNotificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = createCallNotification(
            context,
            call,
            notifiable,
            pendingIntent,
            isIncoming
        )
        notify(notifiable.notificationId, notification)
    }

    @WorkerThread
    private fun startCallForeground() {
        val channelId = context.getString(R.string.notification_channel_call_id)
        val channel = notificationManager.getNotificationChannel(channelId)
        val importance = channel?.importance ?: NotificationManagerCompat.IMPORTANCE_NONE
        if (importance == NotificationManagerCompat.IMPORTANCE_NONE) {
            Log.e("$TAG Calls channel has been disabled, can't start foreground service!")
            return
        }

        val notifiable = getNotifiableForCall(
            coreContext.core.currentCall ?: coreContext.core.calls.first()
        )
        val notification = notificationManager.activeNotifications.find {
            it.id == notifiable.notificationId
        }
        notification ?: return

        val service = coreService
        if (service != null) {
            Log.i("$TAG Service found, starting it as foreground using notification")
            // TODO FIXME: API LEVEL, add compatibility
            try {
                service.startForeground(
                    notifiable.notificationId,
                    notification.notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL
                        or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                        or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
                )
            } catch (e: Exception) {
                Log.e("$TAG Can't start service as foreground! $e")
            }
        } else {
            Log.w("$TAG Core Foreground Service hasn't started yet...")
        }
    }

    @WorkerThread
    private fun stopCallForeground() {
        val service = coreService
        if (service != null) {
            Log.i("$TAG Stopping foreground service")
            service.stopForeground(STOP_FOREGROUND_REMOVE)
            service.stopSelf()
        } else {
            Log.w("$TAG Can't stop foreground service & notif, no service was found")
        }
    }

    private fun getNotifiableForRoom(chatRoom: ChatRoom): Notifiable {
        val address = chatRoom.peerAddress.asStringUriOnly()
        var notifiable: Notifiable? = chatNotificationsMap[address]
        if (notifiable == null) {
            notifiable = Notifiable(LinphoneUtils.getChatRoomId(chatRoom).hashCode())
            notifiable.myself = LinphoneUtils.getDisplayName(chatRoom.localAddress)
            notifiable.localIdentity = chatRoom.localAddress.asStringUriOnly()
            notifiable.remoteAddress = chatRoom.peerAddress.asStringUriOnly()

            chatNotificationsMap[address] = notifiable

            if (chatRoom.hasCapability(ChatRoom.Capabilities.OneToOne.toInt())) {
                notifiable.isGroup = false
            } else {
                notifiable.isGroup = true
                notifiable.groupTitle = chatRoom.subject
            }
        }
        return notifiable
    }

    @WorkerThread
    private fun showChatRoomNotification(chatRoom: ChatRoom, messages: Array<out ChatMessage>) {
        val notifiable = getNotifiableForRoom(chatRoom)

        var updated = false
        for (message in messages) {
            if (message.isRead || message.isOutgoing) continue
            val notifiableMessage = getNotifiableForChatMessage(message)
            notifiable.messages.add(notifiableMessage)
            updated = true
        }

        if (chatRoom.hasCapability(ChatRoom.Capabilities.OneToOne.toInt())) {
            notifiable.isGroup = false
        } else {
            notifiable.isGroup = true
            notifiable.groupTitle = chatRoom.subject
        }
        if (!updated) {
            Log.w("$TAG No changes made to notifiable, do not display it again")
            return
        }

        if (notifiable.messages.isNotEmpty()) {
            val me = coreContext.contactsManager.getMePerson(chatRoom.localAddress)
            val notification = createMessageNotification(
                notifiable,
                LinphoneUtils.getChatRoomId(chatRoom),
                me
            )
            notify(notifiable.notificationId, notification, CHAT_TAG)
        } else {
            Log.w(
                "$TAG No message to display in received aggregated messages"
            )
        }
    }

    @WorkerThread
    private fun showChatMessageReactionNotification(
        chatRoom: ChatRoom,
        reaction: String,
        from: Address,
        message: ChatMessage
    ) {
        val notifiable = getNotifiableForRoom(chatRoom)

        val contact =
            coreContext.contactsManager.findContactByAddress(from)
        val contactPicture = contact?.photo
        val roundPicture = if (!contactPicture.isNullOrEmpty()) {
            ImageUtils.getRoundBitmapFromUri(context, Uri.parse(contactPicture))
        } else {
            null
        }
        val displayName = contact?.name ?: LinphoneUtils.getDisplayName(from)

        val originalMessage = getTextDescribingMessage(message)
        val text = AppUtils.getString(R.string.chat_message_reaction_received).format(
            displayName,
            reaction,
            originalMessage
        )

        val notifiableMessage = NotifiableMessage(
            text,
            contact,
            displayName,
            message.time,
            senderAvatar = roundPicture,
            isOutgoing = false,
            isReaction = true,
            reactionToMessageId = message.messageId,
            reactionFrom = from.asStringUriOnly()
        )
        notifiable.messages.add(notifiableMessage)

        if (notifiable.messages.isNotEmpty()) {
            val me = coreContext.contactsManager.getMePerson(chatRoom.localAddress)
            val notification = createMessageNotification(
                notifiable,
                LinphoneUtils.getChatRoomId(chatRoom),
                me
            )
            notify(notifiable.notificationId, notification, CHAT_TAG)
        } else {
            Log.e(
                "$TAG Notifiable is empty but we should have displayed the reaction!"
            )
        }
    }

    @WorkerThread
    private fun notify(id: Int, notification: Notification, tag: String? = null) {
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            Log.i("$TAG Notifying [$id] with tag [$tag]")
            try {
                notificationManager.notify(tag, id, notification)
            } catch (iae: IllegalArgumentException) {
                if (coreService == null && tag == null) {
                    // We can't notify using CallStyle if there isn't a foreground service running
                    Log.w(
                        "$TAG Foreground service hasn't started yet, can't display a CallStyle notification until then: $iae"
                    )
                } else {
                    Log.e("$TAG Illegal Argument Exception occurred: $iae")
                }
            } catch (e: Exception) {
                Log.e("$TAG Exception occurred: $e")
            }
        }
    }

    @WorkerThread
    fun cancelNotification(id: Int, tag: String? = null) {
        Log.i("$TAG Canceling [$id] with tag [$tag]")
        notificationManager.cancel(tag, id)
    }

    @WorkerThread
    private fun getNotificationIdForCall(call: Call): Int {
        return call.callLog.startDate.toInt()
    }

    @WorkerThread
    private fun getNotifiableForCall(call: Call): Notifiable {
        val address = call.remoteAddress.asStringUriOnly()
        var notifiable: Notifiable? = callNotificationsMap[address]
        if (notifiable == null) {
            notifiable = Notifiable(getNotificationIdForCall(call))
            notifiable.callId = call.callLog.callId

            callNotificationsMap[address] = notifiable
        }
        return notifiable
    }

    @WorkerThread
    private fun getNotifiableForChatMessage(message: ChatMessage): NotifiableMessage {
        val contact =
            coreContext.contactsManager.findContactByAddress(message.fromAddress)
        val contactPicture = contact?.photo
        val roundPicture = if (!contactPicture.isNullOrEmpty()) {
            ImageUtils.getRoundBitmapFromUri(context, Uri.parse(contactPicture))
        } else {
            null
        }
        val displayName = contact?.name ?: LinphoneUtils.getDisplayName(message.fromAddress)

        val text = getTextDescribingMessage(message)
        val notifiableMessage = NotifiableMessage(
            text,
            contact,
            displayName,
            message.time * 1000, /* Linphone timestamps are in seconds */
            senderAvatar = roundPicture,
            isOutgoing = message.isOutgoing
        )

        for (content in message.contents) {
            /*if (content.isFile) { // TODO
                val path = content.filePath
                if (path != null) {
                    val contentUri: Uri = FileUtils.getFilePath(context, path)
                    val filePath: String = contentUri.toString()
                    val extension = FileUtils.getExtensionFromFileName(filePath)
                    if (extension.isNotEmpty()) {
                        val mime =
                            MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
                        notifiableMessage.filePath = contentUri
                        notifiableMessage.fileMime = mime
                        Log.i(
                            "$TAG Added file $contentUri with MIME $mime to notification"
                        )
                    } else {
                        Log.e(
                            "$TAG Couldn't find extension for incoming message with file $path"
                        )
                    }
                }
            }*/
        }

        return notifiableMessage
    }

    @WorkerThread
    private fun createCallNotification(
        context: Context,
        call: Call,
        notifiable: Notifiable,
        pendingIntent: PendingIntent?,
        isIncoming: Boolean
    ): Notification {
        val declineIntent = getCallDeclinePendingIntent(notifiable)
        val answerIntent = getCallAnswerPendingIntent(notifiable)

        val contact =
            coreContext.contactsManager.findContactByAddress(call.remoteAddress)
        val contactPicture = contact?.photo
        val roundPicture = if (!contactPicture.isNullOrEmpty()) {
            ImageUtils.getRoundBitmapFromUri(context, Uri.parse(contactPicture))
        } else {
            null
        }
        val displayName = contact?.name ?: LinphoneUtils.getDisplayName(call.remoteAddress)

        val person = getPerson(contact, displayName, roundPicture)
        val caller = Person.Builder()
            .setName(person.name)
            .setIcon(person.icon)
            .setUri(person.uri)
            .setKey(person.key)
            .setImportant(person.isImportant)
            .build()

        val isVideo = if (isIncoming) {
            call.remoteParams?.isVideoEnabled ?: false
        } else {
            call.currentParams.isVideoEnabled
        }

        val smallIcon = if (isVideo) {
            R.drawable.video_camera
        } else {
            R.drawable.phone
        }

        val style = if (isIncoming) {
            NotificationCompat.CallStyle.forIncomingCall(
                caller,
                declineIntent,
                answerIntent
            )
        } else {
            NotificationCompat.CallStyle.forOngoingCall(
                caller,
                declineIntent
            )
        }

        val channel = when (call.state) {
            Call.State.IncomingReceived, Call.State.IncomingEarlyMedia -> {
                context.getString(R.string.notification_channel_incoming_call_id)
            }
            else -> {
                context.getString(R.string.notification_channel_call_id)
            }
        }

        val builder = NotificationCompat.Builder(
            context,
            channel
        ).apply {
            try {
                style.setIsVideo(isVideo)
                style.setAnswerButtonColorHint(
                    context.resources.getColor(R.color.green_online, context.theme)
                )
                style.setDeclineButtonColorHint(
                    context.resources.getColor(R.color.red_danger, context.theme)
                )
                setStyle(style)
            } catch (iae: IllegalArgumentException) {
                Log.e(
                    "$TAG Can't use notification call style: $iae, using API 26 notification instead"
                )
            }
            setSmallIcon(smallIcon)
            setCategory(NotificationCompat.CATEGORY_CALL)
            setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            setWhen(System.currentTimeMillis())
            setAutoCancel(false)
            setShowWhen(true)
            setOngoing(true)
            color = ContextCompat.getColor(context, R.color.primary_color)
            setFullScreenIntent(pendingIntent, true)
        }

        return builder.build()
    }

    private fun createMessageNotification(
        notifiable: Notifiable,
        id: String,
        me: Person
    ): Notification {
        val style = NotificationCompat.MessagingStyle(me)
        val allPersons = arrayListOf<Person>()

        var lastPersonAvatar: Bitmap? = null
        var lastPerson: Person? = null
        for (message in notifiable.messages) {
            val friend = message.friend
            val person = getPerson(friend, message.sender, message.senderAvatar)

            if (!message.isOutgoing) {
                // We don't want to see our own avatar
                lastPerson = person
                lastPersonAvatar = message.senderAvatar

                if (allPersons.find { it.key == person.key } == null) {
                    allPersons.add(person)
                }
            }

            val senderPerson = if (message.isOutgoing) null else person // Use null for ourselves
            val tmp = NotificationCompat.MessagingStyle.Message(
                message.message,
                message.time,
                senderPerson
            )
            if (message.filePath != null) tmp.setData(message.fileMime, message.filePath)

            style.addMessage(tmp)
            if (message.isOutgoing) {
                style.addHistoricMessage(tmp)
            }
        }

        style.conversationTitle = if (notifiable.isGroup) notifiable.groupTitle else lastPerson?.name
        style.isGroupConversation = notifiable.isGroup

        val largeIcon = lastPersonAvatar
        val notificationBuilder = NotificationCompat.Builder(
            context,
            context.getString(R.string.notification_channel_chat_id)
        )
            .setSmallIcon(R.drawable.chat_text)
            .setAutoCancel(true)
            .setLargeIcon(largeIcon)
            .setColor(ContextCompat.getColor(context, R.color.primary_color))
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setGroup(CHAT_NOTIFICATIONS_GROUP)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setNumber(notifiable.messages.size)
            .setWhen(System.currentTimeMillis())
            .setShowWhen(true)
            .setStyle(style)
            .setShortcutId(id)
            .setLocusId(LocusIdCompat(id))

        for (person in allPersons) {
            notificationBuilder.addPerson(person)
        }

        return notificationBuilder.build()
    }

    @WorkerThread
    private fun dismissCallNotification(call: Call) {
        val address = call.remoteAddress.asStringUriOnly()
        val notifiable: Notifiable? = callNotificationsMap[address]
        if (notifiable != null) {
            cancelNotification(notifiable.notificationId)
            callNotificationsMap.remove(address)
        } else {
            Log.w("$TAG No notification found for call with remote address [$address]")
        }
    }

    @AnyThread
    fun getCallDeclinePendingIntent(notifiable: Notifiable): PendingIntent {
        val hangupIntent = Intent(context, NotificationBroadcastReceiver::class.java)
        hangupIntent.action = INTENT_HANGUP_CALL_NOTIF_ACTION
        hangupIntent.putExtra(INTENT_NOTIF_ID, notifiable.notificationId)
        hangupIntent.putExtra(INTENT_CALL_ID, notifiable.callId)

        return PendingIntent.getBroadcast(
            context,
            notifiable.notificationId,
            hangupIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    @AnyThread
    fun getCallAnswerPendingIntent(notifiable: Notifiable): PendingIntent {
        val answerIntent = Intent(context, NotificationBroadcastReceiver::class.java)
        answerIntent.action = INTENT_ANSWER_CALL_NOTIF_ACTION
        answerIntent.putExtra(INTENT_NOTIF_ID, notifiable.notificationId)
        answerIntent.putExtra(INTENT_CALL_ID, notifiable.callId)

        return PendingIntent.getBroadcast(
            context,
            notifiable.notificationId,
            answerIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    @WorkerThread
    private fun getPerson(friend: Friend?, displayName: String, picture: Bitmap?): Person {
        return friend?.getPerson()
            ?: Person.Builder()
                .setName(displayName)
                .setIcon(
                    if (picture != null) {
                        IconCompat.createWithAdaptiveBitmap(picture)
                    } else {
                        coreContext.contactsManager.contactAvatar
                    }
                )
                .setKey(displayName)
                .build()
    }

    @MainThread
    private fun createIncomingCallNotificationChannel() {
        val id = context.getString(R.string.notification_channel_incoming_call_id)
        val name = context.getString(R.string.notification_channel_incoming_call_name)

        val channel = NotificationChannel(id, name, NotificationManager.IMPORTANCE_HIGH)
        channel.description = name
        channel.lightColor = context.getColor(R.color.primary_color)
        channel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        channel.enableVibration(true)
        channel.enableLights(true)
        channel.setShowBadge(false)
        notificationManager.createNotificationChannel(channel)
    }

    @MainThread
    private fun createActiveCallNotificationChannel() {
        val id = context.getString(R.string.notification_channel_call_id)
        val name = context.getString(R.string.notification_channel_call_name)

        val channel = NotificationChannel(id, name, NotificationManager.IMPORTANCE_DEFAULT)
        channel.description = name
        channel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        channel.enableVibration(false)
        channel.enableLights(false)
        channel.setShowBadge(false)
        notificationManager.createNotificationChannel(channel)
    }

    @MainThread
    private fun createMessageChannel() {
        val id = context.getString(R.string.notification_channel_chat_id)
        val name = context.getString(R.string.notification_channel_chat_name)

        val channel = NotificationChannel(id, name, NotificationManager.IMPORTANCE_HIGH)
        channel.description = name
        channel.lightColor = context.getColor(R.color.primary_color)
        channel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        channel.enableLights(true)
        channel.enableVibration(true)
        channel.setShowBadge(true)
        channel.setAllowBubbles(true)
        notificationManager.createNotificationChannel(channel)
    }

    @MainThread
    private fun createServiceChannel() {
        val id = context.getString(R.string.notification_channel_service_id)
        val name = context.getString(R.string.notification_channel_service_name)

        val channel = NotificationChannel(id, name, NotificationManager.IMPORTANCE_LOW)
        channel.description = name
        channel.enableVibration(false)
        channel.enableLights(false)
        channel.setShowBadge(false)
        notificationManager.createNotificationChannel(channel)
    }

    @WorkerThread
    private fun getTextDescribingMessage(message: ChatMessage): String {
        // If message contains text, then use that
        var text = message.contents.find { content -> content.isText }?.utf8Text ?: ""

        if (text.isEmpty()) {
            val firstContent = message.contents.firstOrNull()
            if (firstContent?.isIcalendar == true) {
                text = "meeting invite" // TODO: use translated string
            } else if (firstContent?.isVoiceRecording == true) {
                text = "voice message" // TODO: use translated string
            } else {
                for (content in message.contents) {
                    if (text.isNotEmpty()) {
                        text += ", "
                    }
                    text += content.name
                }
            }
        }

        return text
    }

    class Notifiable(val notificationId: Int) {
        var myself: String? = null
        var callId: String? = null

        var localIdentity: String? = null
        var remoteAddress: String? = null

        var isGroup: Boolean = false
        var groupTitle: String? = null
        val messages: ArrayList<NotifiableMessage> = arrayListOf()
    }

    class NotifiableMessage(
        var message: String,
        val friend: Friend?,
        val sender: String,
        val time: Long,
        val senderAvatar: Bitmap? = null,
        var filePath: Uri? = null,
        var fileMime: String? = null,
        val isOutgoing: Boolean = false,
        val isReaction: Boolean = false,
        val reactionToMessageId: String? = null,
        val reactionFrom: String? = null
    )
}
