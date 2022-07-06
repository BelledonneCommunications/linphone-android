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
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.webkit.MimeTypeMap
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import androidx.core.content.LocusIdCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.navigation.NavDeepLinkBuilder
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.R
import org.linphone.activities.chat_bubble.ChatBubbleActivity
import org.linphone.activities.main.MainActivity
import org.linphone.activities.voip.CallActivity
import org.linphone.compatibility.Compatibility
import org.linphone.contact.getPerson
import org.linphone.contact.getThumbnailUri
import org.linphone.core.*
import org.linphone.core.tools.Log
import org.linphone.utils.*

class Notifiable(val notificationId: Int) {
    val messages: ArrayList<NotifiableMessage> = arrayListOf()

    var isGroup: Boolean = false
    var groupTitle: String? = null
    var localIdentity: String? = null
    var myself: String? = null
    var remoteAddress: String? = null
}

class NotifiableMessage(
    var message: String,
    val friend: Friend?,
    val sender: String,
    val time: Long,
    val senderAvatar: Bitmap? = null,
    var filePath: Uri? = null,
    var fileMime: String? = null,
    val isOutgoing: Boolean = false
)

class NotificationsManager(private val context: Context) {
    companion object {
        const val CHAT_NOTIFICATIONS_GROUP = "CHAT_NOTIF_GROUP"
        const val KEY_TEXT_REPLY = "key_text_reply"
        const val INTENT_NOTIF_ID = "NOTIFICATION_ID"
        const val INTENT_REPLY_NOTIF_ACTION = "org.linphone.REPLY_ACTION"
        const val INTENT_HANGUP_CALL_NOTIF_ACTION = "org.linphone.HANGUP_CALL_ACTION"
        const val INTENT_ANSWER_CALL_NOTIF_ACTION = "org.linphone.ANSWER_CALL_ACTION"
        const val INTENT_MARK_AS_READ_ACTION = "org.linphone.MARK_AS_READ_ACTION"
        const val INTENT_LOCAL_IDENTITY = "LOCAL_IDENTITY"
        const val INTENT_REMOTE_ADDRESS = "REMOTE_ADDRESS"

        private const val SERVICE_NOTIF_ID = 1
        private const val MISSED_CALLS_NOTIF_ID = 2

        const val CHAT_TAG = "Chat"
        private const val MISSED_CALL_TAG = "Missed call"
    }

    private val notificationManager: NotificationManagerCompat by lazy {
        NotificationManagerCompat.from(context)
    }
    private val chatNotificationsMap: HashMap<String, Notifiable> = HashMap()
    private val callNotificationsMap: HashMap<String, Notifiable> = HashMap()
    private val previousChatNotifications: ArrayList<Int> = arrayListOf()
    private val chatBubbleNotifications: ArrayList<Int> = arrayListOf()

    private var currentForegroundServiceNotificationId: Int = 0
    private var serviceNotification: Notification? = null

    private var service: CoreService? = null

    var currentlyDisplayedChatRoomAddress: String? = null

    private val listener: CoreListenerStub = object : CoreListenerStub() {
        override fun onCallStateChanged(
            core: Core,
            call: Call,
            state: Call.State,
            message: String
        ) {
            Log.i("[Notifications Manager] Call state changed [$state]")

            if (corePreferences.preventInterfaceFromShowingUp) {
                Log.w("[Notifications Manager] We were asked to not show the call notifications")
                return
            }

            when (call.state) {
                Call.State.IncomingEarlyMedia, Call.State.IncomingReceived -> {
                    if (service != null) {
                        Log.i("[Notifications Manager] Service isn't null, show incoming call notification")
                        displayIncomingCallNotification(call, false)
                    } else {
                        Log.w("[Notifications Manager] No service found, waiting for it to start")
                    }
                }
                Call.State.End, Call.State.Error -> dismissCallNotification(call)
                Call.State.Released -> {
                    if (LinphoneUtils.isCallLogMissed(call.callLog)) {
                        displayMissedCallNotification(call.remoteAddress)
                    }
                }
                else -> displayCallNotification(call)
            }
        }

        override fun onMessagesReceived(
            core: Core,
            room: ChatRoom,
            messages: Array<out ChatMessage>
        ) {
            Log.i("[Notifications Manager] Received ${messages.size} aggregated messages")
            if (corePreferences.disableChat) return

            if (corePreferences.preventInterfaceFromShowingUp) {
                Log.w("[Notifications Manager] We were asked to not show the chat notifications")
                return
            }

            if (currentlyDisplayedChatRoomAddress == room.peerAddress.asStringUriOnly()) {
                Log.i("[Notifications Manager] Chat room is currently displayed, do not notify received message")
                // Mark as read is now done in the DetailChatRoomFragment
                return
            }

            val id = LinphoneUtils.getChatRoomId(room.localAddress, room.peerAddress)
            val mute = corePreferences.chatRoomMuted(id)
            if (mute) {
                Log.i("[Notifications Manager] Chat room $id has been muted")
                return
            }

            if (corePreferences.chatRoomShortcuts) {
                if (ShortcutsHelper.isShortcutToChatRoomAlreadyCreated(context, room)) {
                    Log.i("[Notifications Manager] Chat room shortcut already exists")
                } else {
                    Log.i("[Notifications Manager] Ensure chat room shortcut exists for bubble notification")
                    ShortcutsHelper.createShortcutsToChatRooms(context)
                }
            }

            val notifiable = createChatNotifiable(room, messages)
            if (notifiable.messages.isNotEmpty()) {
                displayChatNotifiable(room, notifiable)
            } else {
                Log.w("[Notifications Manager] No message to display in received aggregated messages")
            }
        }

        override fun onChatRoomRead(core: Core, chatRoom: ChatRoom) {
            val address = chatRoom.peerAddress.asStringUriOnly()
            val notifiable = chatNotificationsMap[address]
            if (notifiable != null) {
                if (chatBubbleNotifications.contains(notifiable.notificationId)) {
                    Log.i("[Notifications Manager] Chat room [$chatRoom] has been marked as read, not removing notification because of a chat bubble")
                } else {
                    Log.i("[Notifications Manager] Chat room [$chatRoom] has been marked as read, removing notification if any")
                    dismissChatNotification(chatRoom)
                }
            } else {
                val notificationId = chatRoom.creationTime.toInt()
                if (chatBubbleNotifications.contains(notificationId)) {
                    Log.i("[Notifications Manager] Chat room [$chatRoom] has been marked as read but no notifiable found, not removing notification because of a chat bubble")
                } else {
                    Log.i("[Notifications Manager] Chat room [$chatRoom] has been marked as read but no notifiable found, removing notification if any")
                    dismissChatNotification(chatRoom)
                }
            }
        }

        override fun onLastCallEnded(core: Core) {
            Log.i("[Notifications Manager] Last call ended, make sure foreground service is stopped and notification removed")
            stopCallForeground()
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
                    cancel(id, CHAT_TAG)
                }
            } else if (state == ChatMessage.State.NotDelivered) {
                Log.e("[Notifications Manager] Reply wasn't delivered")
                cancel(id, CHAT_TAG)
            }
        }
    }

    init {
        Compatibility.createNotificationChannels(context, notificationManager)

        val manager = context.getSystemService(NotificationManager::class.java) as NotificationManager
        for (notification in manager.activeNotifications) {
            if (notification.tag.isNullOrEmpty()) { // We use null tag for call notifications otherwise it will create duplicates when used with Service.startForeground()...
                Log.w("[Notifications Manager] Found existing call? notification [${notification.id}], cancelling it")
                manager.cancel(notification.tag, notification.id)
            } else if (notification.tag == CHAT_TAG) {
                Log.i("[Notifications Manager] Found existing chat notification [${notification.id}]")
                previousChatNotifications.add(notification.id)
            }
        }
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

        if (currentForegroundServiceNotificationId > 0 && !corePreferences.keepServiceAlive) {
            Log.i("[Notifications Manager] Clearing foreground Service")
            stopForegroundNotification()
        }

        if (callNotificationsMap.size > 0) {
            Log.i("[Notifications Manager] Clearing call notifications")
            for (notifiable in callNotificationsMap.values) {
                notificationManager.cancel(notifiable.notificationId)
            }
        }

        coreContext.core.removeListener(listener)
    }

    private fun notify(id: Int, notification: Notification, tag: String? = null) {
        if (!PermissionHelper.get().hasPostNotificationsPermission()) {
            Log.w("[Notifications Manager] Can't notify [$id] with tag [$tag], POST_NOTIFICATIONS permission isn't granted!")
            return
        }

        Log.i("[Notifications Manager] Notifying [$id] with tag [$tag]")
        notificationManager.notify(tag, id, notification)
    }

    fun cancel(id: Int, tag: String? = null) {
        Log.i("[Notifications Manager] Canceling [$id] with tag [$tag]")
        notificationManager.cancel(tag, id)
    }

    fun resetChatNotificationCounterForSipUri(sipUri: String) {
        val notifiable: Notifiable? = chatNotificationsMap[sipUri]
        notifiable?.messages?.clear()
    }

    /* Service related */

    fun startForeground() {
        val serviceChannel = context.getString(R.string.notification_channel_service_id)
        if (Compatibility.getChannelImportance(notificationManager, serviceChannel) == NotificationManagerCompat.IMPORTANCE_NONE) {
            Log.w("[Notifications Manager] Service channel is disabled!")
            return
        }

        val coreService = service
        if (coreService != null) {
            startForeground(coreService, useAutoStartDescription = false)
        } else {
            Log.w("[Notifications Manager] Can't start service as foreground without a service, starting it now")
            val intent = Intent()
            intent.setClass(coreContext.context, CoreService::class.java)
            try {
                Compatibility.startForegroundService(coreContext.context, intent)
            } catch (ise: IllegalStateException) {
                Log.e("[Notifications Manager] Failed to start Service: $ise")
            } catch (se: SecurityException) {
                Log.e("[Notifications Manager] Failed to start Service: $se")
            }
        }
    }

    fun startCallForeground(coreService: CoreService) {
        service = coreService
        when {
            currentForegroundServiceNotificationId != 0 -> {
                if (currentForegroundServiceNotificationId != SERVICE_NOTIF_ID) {
                    Log.e("[Notifications Manager] There is already a foreground service notification [$currentForegroundServiceNotificationId]")
                } else {
                    Log.i("[Notifications Manager] There is already a foreground service notification, no need to use the call notification to keep Service alive")
                }
            }
            coreContext.core.callsNb > 0 -> {
                // When this method will be called, we won't have any notification yet
                val call = coreContext.core.currentCall ?: coreContext.core.calls[0]
                when (call.state) {
                    Call.State.IncomingReceived, Call.State.IncomingEarlyMedia -> {
                        Log.i("[Notifications Manager] Creating incoming call notification to be used as foreground service")
                        displayIncomingCallNotification(call, true)
                    }
                    else -> {
                        Log.i("[Notifications Manager] Creating call notification to be used as foreground service")
                        displayCallNotification(call, true)
                    }
                }
            }
        }
    }

    fun startForeground(coreService: CoreService, useAutoStartDescription: Boolean = true) {
        service = coreService

        if (serviceNotification == null) {
            createServiceNotification(useAutoStartDescription)
            if (serviceNotification == null) {
                Log.e("[Notifications Manager] Failed to create service notification, aborting foreground service!")
                return
            }
        }

        currentForegroundServiceNotificationId = SERVICE_NOTIF_ID
        Log.i("[Notifications Manager] Starting service as foreground [$currentForegroundServiceNotificationId]")
        Compatibility.startForegroundService(coreService, currentForegroundServiceNotificationId, serviceNotification)
    }

    private fun startForeground(notificationId: Int, callNotification: Notification) {
        if (currentForegroundServiceNotificationId == 0 && service != null) {
            Log.i("[Notifications Manager] Starting service as foreground using call notification [$notificationId]")
            currentForegroundServiceNotificationId = notificationId
            service?.startForeground(currentForegroundServiceNotificationId, callNotification)
        } else {
            Log.w("[Notifications Manager] Can't start foreground service using notification id [$notificationId] (current foreground service notification id is [$currentForegroundServiceNotificationId]) and service [$service]")
        }
    }

    fun stopForegroundNotification() {
        if (service != null) {
            if (currentForegroundServiceNotificationId != 0) {
                Log.i("[Notifications Manager] Stopping service as foreground [$currentForegroundServiceNotificationId]")
                currentForegroundServiceNotificationId = 0
            }
            service?.stopForeground(true)
        }
    }

    fun stopForegroundNotificationIfPossible() {
        if (service != null && currentForegroundServiceNotificationId == SERVICE_NOTIF_ID && !corePreferences.keepServiceAlive) {
            Log.i("[Notifications Manager] Stopping auto-started service notification [$currentForegroundServiceNotificationId]")
            stopForegroundNotification()
        }
    }

    fun stopCallForeground() {
        if (service != null && currentForegroundServiceNotificationId != SERVICE_NOTIF_ID) {
            Log.i("[Notifications Manager] Stopping call notification [$currentForegroundServiceNotificationId] used as foreground service")
            stopForegroundNotification()
        }
    }

    fun serviceCreated(createdService: CoreService) {
        Log.i("[Notifications Manager] Service has been created, keeping it around")
        service = createdService
    }

    fun serviceDestroyed() {
        Log.i("[Notifications Manager] Service has been destroyed")
        stopForegroundNotification()
        service = null
    }

    private fun createServiceNotification(useAutoStartDescription: Boolean = false) {
        val serviceChannel = context.getString(R.string.notification_channel_service_id)
        if (Compatibility.getChannelImportance(notificationManager, serviceChannel) == NotificationManagerCompat.IMPORTANCE_NONE) {
            Log.w("[Notifications Manager] Service channel is disabled!")
            return
        }

        val pendingIntent = NavDeepLinkBuilder(context)
            .setComponentName(MainActivity::class.java)
            .setGraph(R.navigation.main_nav_graph)
            .setDestination(R.id.dialerFragment)
            .createPendingIntent()

        val builder = NotificationCompat.Builder(context, serviceChannel)
            .setContentTitle(context.getString(R.string.service_name))
            .setContentText(if (useAutoStartDescription) context.getString(R.string.service_auto_start_description) else context.getString(R.string.service_description))
            .setSmallIcon(R.drawable.topbar_service_notification)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setWhen(System.currentTimeMillis())
            .setShowWhen(true)
            .setOngoing(true)
            .setColor(ContextCompat.getColor(context, R.color.primary_color))

        if (!corePreferences.preventInterfaceFromShowingUp) {
            builder.setContentIntent(pendingIntent)
        }

        serviceNotification = builder.build()
    }

    /* Call related */

    private fun getNotificationIdForCall(call: Call): Int {
        return call.callLog.startDate.toInt()
    }

    private fun getNotifiableForCall(call: Call): Notifiable {
        val address = call.remoteAddress.asStringUriOnly()
        var notifiable: Notifiable? = callNotificationsMap[address]
        if (notifiable == null) {
            notifiable = Notifiable(getNotificationIdForCall(call))
            notifiable.remoteAddress = call.remoteAddress.asStringUriOnly()

            callNotificationsMap[address] = notifiable
        }
        return notifiable
    }

    fun getPerson(friend: Friend?, displayName: String, picture: Bitmap?): Person {
        return if (friend != null) {
            friend.getPerson()
        } else {
            val builder = Person.Builder().setName(displayName)
            val userIcon =
                if (picture != null) {
                    IconCompat.createWithAdaptiveBitmap(picture)
                } else {
                    coreContext.contactsManager.contactAvatar
                }
            if (userIcon != null) builder.setIcon(userIcon)
            builder.build()
        }
    }

    fun displayIncomingCallNotification(call: Call, useAsForeground: Boolean) {
        if (coreContext.declineCallDueToGsmActiveCall()) {
            Log.w("[Notifications Manager] Call will be declined, do not show incoming call notification")
            return
        }

        val notifiable = getNotifiableForCall(call)
        if (notifiable.notificationId == currentForegroundServiceNotificationId) {
            Log.i("[Notifications Manager] There is already a Service foreground notification for this incoming call, skipping")
            return
        }

        try {
            val showLockScreenNotification = android.provider.Settings.Secure.getInt(
                context.contentResolver,
                "lock_screen_show_notifications",
                0
            )
            Log.i("[Notifications Manager] Are notifications allowed on lock screen? ${showLockScreenNotification != 0} ($showLockScreenNotification)")
        } catch (e: Exception) {
            Log.e("[Notifications Manager] Failed to get android.provider.Settings.Secure.getInt(lock_screen_show_notifications): $e")
        }

        val incomingCallNotificationIntent = Intent(context, CallActivity::class.java)
        incomingCallNotificationIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION or Intent.FLAG_FROM_BACKGROUND)
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            incomingCallNotificationIntent,
            PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = Compatibility.createIncomingCallNotification(context, call, notifiable, pendingIntent, this)
        Log.i("[Notifications Manager] Notifying incoming call notification [${notifiable.notificationId}]")
        notify(notifiable.notificationId, notification)

        if (useAsForeground) {
            Log.i("[Notifications Manager] Notifying incoming call notification for foreground service [${notifiable.notificationId}]")
            startForeground(notifiable.notificationId, notification)
        }
    }

    private fun displayMissedCallNotification(remoteAddress: Address) {
        val missedCallCount: Int = coreContext.core.missedCallsCount
        val body: String
        if (missedCallCount > 1) {
            body = context.getString(R.string.missed_calls_notification_body)
                .format(missedCallCount)
            Log.i("[Notifications Manager] Updating missed calls notification count to $missedCallCount")
        } else {
            val friend: Friend? = coreContext.contactsManager.findContactByAddress(remoteAddress)
            body = context.getString(R.string.missed_call_notification_body)
                .format(friend?.name ?: LinphoneUtils.getDisplayName(remoteAddress))
            Log.i("[Notifications Manager] Creating missed call notification")
        }

        val pendingIntent = NavDeepLinkBuilder(context)
            .setComponentName(MainActivity::class.java)
            .setGraph(R.navigation.main_nav_graph)
            .setDestination(R.id.masterCallLogsFragment)
            .createPendingIntent()

        val builder = NotificationCompat.Builder(
            context, context.getString(R.string.notification_channel_missed_call_id)
        )
            .setContentTitle(context.getString(R.string.missed_call_notification_title))
            .setContentText(body)
            .setSmallIcon(R.drawable.topbar_missed_call_notification)
            .setAutoCancel(true)
            // .setCategory(NotificationCompat.CATEGORY_EVENT) No one really matches "missed call"
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setWhen(System.currentTimeMillis())
            .setShowWhen(true)
            .setNumber(missedCallCount)
            .setColor(ContextCompat.getColor(context, R.color.notification_led_color))

        if (!corePreferences.preventInterfaceFromShowingUp) {
            builder.setContentIntent(pendingIntent)
        }

        val notification = builder.build()
        notify(MISSED_CALLS_NOTIF_ID, notification, MISSED_CALL_TAG)
    }

    fun dismissMissedCallNotification() {
        cancel(MISSED_CALLS_NOTIF_ID, MISSED_CALL_TAG)
    }

    fun displayCallNotification(call: Call, useAsForeground: Boolean = false) {
        val notifiable = getNotifiableForCall(call)

        val serviceChannel = context.getString(R.string.notification_channel_service_id)
        val channelToUse = when (val serviceChannelImportance = Compatibility.getChannelImportance(notificationManager, serviceChannel)) {
            NotificationManagerCompat.IMPORTANCE_NONE -> {
                Log.w("[Notifications Manager] Service channel is disabled, using incoming call channel instead!")
                context.getString(R.string.notification_channel_incoming_call_id)
            }
            NotificationManagerCompat.IMPORTANCE_LOW -> {
                // Expected, nothing to do
                serviceChannel
            }
            else -> {
                // If user disables & enabled back service notifications channel, importance won't be low anymore but default!
                Log.w("[Notifications Manager] Service channel importance is $serviceChannelImportance and not LOW (${NotificationManagerCompat.IMPORTANCE_LOW}) as expected!")
                serviceChannel
            }
        }

        val callNotificationIntent = Intent(context, CallActivity::class.java)
        callNotificationIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            callNotificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = Compatibility.createCallNotification(context, call, notifiable, pendingIntent, channelToUse, this)
        Log.i("[Notifications Manager] Notifying call notification [${notifiable.notificationId}]")
        notify(notifiable.notificationId, notification)

        if (useAsForeground || (service != null && currentForegroundServiceNotificationId == 0)) {
            Log.i("[Notifications Manager] Notifying call notification for foreground service [${notifiable.notificationId}]")
            startForeground(notifiable.notificationId, notification)
        }
    }

    private fun dismissCallNotification(call: Call) {
        val address = call.remoteAddress.asStringUriOnly()
        val notifiable: Notifiable? = callNotificationsMap[address]
        if (notifiable != null) {
            cancel(notifiable.notificationId)
            callNotificationsMap.remove(address)
        } else {
            Log.w("[Notifications Manager] No notification found for call ${call.callLog.callId}")
        }
    }

    /* Chat related */

    private fun getNotificationIdForChat(chatRoom: ChatRoom): Int {
        return chatRoom.creationTime.toInt()
    }

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

        // PendingIntents attached to bubbles must be mutable
        val target = Intent(context, ChatBubbleActivity::class.java)
        target.putExtra("RemoteSipUri", peerAddress)
        target.putExtra("LocalSipUri", localAddress)
        val bubbleIntent = PendingIntent.getActivity(
            context,
            notifiable.notificationId,
            target,
            Compatibility.getUpdateCurrentPendingIntentFlag()
        )

        val id = LinphoneUtils.getChatRoomId(room.localAddress, room.peerAddress)
        val notification = createMessageNotification(notifiable, pendingIntent, bubbleIntent, id)
        notify(notifiable.notificationId, notification, CHAT_TAG)
    }

    private fun createChatNotifiable(room: ChatRoom, message: ChatMessage): Notifiable {
        val notifiable = getNotifiableForRoom(room)
        if (notifiable.messages.isNotEmpty() || room.unreadMessagesCount == 1) {
            val friend = coreContext.contactsManager.findContactByAddress(message.fromAddress)
            val notifiableMessage = getNotifiableMessage(message, friend)
            notifiable.messages.add(notifiableMessage)
        } else {
            for (chatMessage in room.unreadHistory) {
                val friend = coreContext.contactsManager.findContactByAddress(chatMessage.fromAddress)
                val notifiableMessage = getNotifiableMessage(chatMessage, friend)
                notifiable.messages.add(notifiableMessage)
            }
        }

        if (room.hasCapability(ChatRoomCapabilities.OneToOne.toInt())) {
            notifiable.isGroup = false
        } else {
            notifiable.isGroup = true
            notifiable.groupTitle = room.subject
        }

        return notifiable
    }

    private fun createChatNotifiable(room: ChatRoom, messages: Array<out ChatMessage>): Notifiable {
        val notifiable = getNotifiableForRoom(room)

        for (message in messages) {
            if (message.isRead || message.isOutgoing) continue
            val friend = coreContext.contactsManager.findContactByAddress(message.fromAddress)
            val notifiableMessage = getNotifiableMessage(message, friend)
            notifiable.messages.add(notifiableMessage)
        }

        if (room.hasCapability(ChatRoomCapabilities.OneToOne.toInt())) {
            notifiable.isGroup = false
        } else {
            notifiable.isGroup = true
            notifiable.groupTitle = room.subject
        }

        return notifiable
    }

    private fun getNotifiableForRoom(room: ChatRoom): Notifiable {
        val address = room.peerAddress.asStringUriOnly()
        var notifiable: Notifiable? = chatNotificationsMap[address]
        if (notifiable == null) {
            notifiable = Notifiable(getNotificationIdForChat(room))
            notifiable.myself = LinphoneUtils.getDisplayName(room.localAddress)
            notifiable.localIdentity = room.localAddress.asStringUriOnly()
            notifiable.remoteAddress = room.peerAddress.asStringUriOnly()

            chatNotificationsMap[address] = notifiable
        }
        return notifiable
    }

    private fun getNotifiableMessage(message: ChatMessage, friend: Friend?): NotifiableMessage {
        val roundPicture = ImageUtils.getRoundBitmapFromUri(context, friend?.getThumbnailUri())
        val displayName = friend?.name ?: LinphoneUtils.getDisplayName(message.fromAddress)
        var text = ""

        val isConferenceInvite = message.contents.firstOrNull()?.isIcalendar ?: false
        text = if (isConferenceInvite) {
            AppUtils.getString(R.string.conference_invitation_received_notification)
        } else {
            message.contents.find { content -> content.isText }?.utf8Text ?: ""
        }

        if (text.isEmpty()) {
            for (content in message.contents) {
                text += content.name
            }
        }

        val notifiableMessage = NotifiableMessage(
            text,
            friend,
            displayName,
            message.time,
            senderAvatar = roundPicture,
            isOutgoing = message.isOutgoing
        )

        for (content in message.contents) {
            if (content.isFile) {
                val path = content.filePath
                if (path != null) {
                    val contentUri: Uri = FileUtils.getPublicFilePath(context, path)
                    val filePath: String = contentUri.toString()
                    val extension = FileUtils.getExtensionFromFileName(filePath)
                    if (extension.isNotEmpty()) {
                        val mime =
                            MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
                        notifiableMessage.filePath = contentUri
                        notifiableMessage.fileMime = mime
                        Log.i("[Notifications Manager] Added file $contentUri with MIME $mime to notification")
                    } else {
                        Log.e("[Notifications Manager] Couldn't find extension for incoming message with file $path")
                    }
                }
            }
        }

        return notifiableMessage
    }

    private fun displayReplyMessageNotification(message: ChatMessage, notifiable: Notifiable) {
        Log.i("[Notifications Manager] Updating message notification with reply for notification ${notifiable.notificationId}")

        val text = message.contents.find { content -> content.isText }?.utf8Text ?: ""
        val reply = NotifiableMessage(
            text,
            null,
            notifiable.myself ?: LinphoneUtils.getDisplayName(message.fromAddress),
            System.currentTimeMillis(),
            isOutgoing = true
        )
        notifiable.messages.add(reply)

        displayChatNotifiable(message.chatRoom, notifiable)
    }

    fun dismissChatNotification(room: ChatRoom): Boolean {
        val address = room.peerAddress.asStringUriOnly()
        val notifiable: Notifiable? = chatNotificationsMap[address]
        if (notifiable != null) {
            Log.i("[Notifications Manager] Dismissing notification for chat room $room with id ${notifiable.notificationId}")
            notifiable.messages.clear()
            cancel(notifiable.notificationId, CHAT_TAG)
            return true
        } else {
            val previousNotificationId = previousChatNotifications.find { id -> id == room.creationTime.toInt() }
            if (previousNotificationId != null) {
                if (chatBubbleNotifications.contains(previousNotificationId)) {
                    Log.i("[Notifications Manager] Found previous notification with same ID [$previousNotificationId] but not cancelling it as it's ID is in chat bubbles list")
                } else {
                    Log.i("[Notifications Manager] Found previous notification with same ID [$previousNotificationId], canceling it")
                    cancel(previousNotificationId, CHAT_TAG)
                }
                return true
            }
        }
        return false
    }

    fun changeDismissNotificationUponReadForChatRoom(chatRoom: ChatRoom, dismiss: Boolean) {
        val notificationId = chatRoom.creationTime.toInt()
        if (dismiss) {
            Log.i("[Notifications Manager] Allow notification with id [$notificationId] to be dismissed when chat room will be marked as read, used for chat bubble")
            chatBubbleNotifications.add(notificationId)
        } else {
            Log.i("[Notifications Manager] Prevent notification with id [$notificationId] from being dismissed when chat room will be marked as read, used for chat bubble")
            chatBubbleNotifications.remove(notificationId)
        }
    }

    /* Notifications */

    private fun createMessageNotification(
        notifiable: Notifiable,
        pendingIntent: PendingIntent,
        bubbleIntent: PendingIntent,
        id: String
    ): Notification {
        val me = Person.Builder().setName(notifiable.myself).build()
        val style = NotificationCompat.MessagingStyle(me)
        val largeIcon: Bitmap? = notifiable.messages.lastOrNull()?.senderAvatar

        var lastPerson: Person? = null
        for (message in notifiable.messages) {
            val friend = message.friend
            val person = getPerson(friend, message.sender, message.senderAvatar)

            // We don't want to see our own avatar
            if (!message.isOutgoing) {
                lastPerson = person
            }

            val msg = if (!corePreferences.hideChatMessageContentInNotification) {
                NotificationCompat.MessagingStyle.Message(message.message, message.time, person)
            } else {
                NotificationCompat.MessagingStyle.Message(AppUtils.getString(R.string.chat_message_notification_hidden_content), message.time, person)
            }

            if (message.filePath != null && !corePreferences.hideChatMessageContentInNotification) {
                msg.setData(message.fileMime, message.filePath)
            }

            style.addMessage(msg)
        }

        if (notifiable.isGroup) {
            style.conversationTitle = notifiable.groupTitle
        }
        style.isGroupConversation = notifiable.isGroup

        val icon = lastPerson?.icon ?: coreContext.contactsManager.contactAvatar
        val bubble = NotificationCompat.BubbleMetadata.Builder(bubbleIntent, icon)
            .setDesiredHeightResId(R.dimen.chat_message_bubble_desired_height)
            .build()

        val notificationBuilder = NotificationCompat.Builder(context, context.getString(R.string.notification_channel_chat_id))
            .addPerson(lastPerson)
            .setSmallIcon(R.drawable.topbar_chat_notification)
            .setAutoCancel(true)
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
            .setShortcutId(id)
            .setLocusId(LocusIdCompat(id))

        if (!corePreferences.preventInterfaceFromShowingUp) {
            notificationBuilder.setContentIntent(pendingIntent)
        }

        if (corePreferences.markAsReadUponChatMessageNotificationDismissal) {
            Log.i("[Notifications Manager] Chat room will be marked as read when notification will be dismissed")
            notificationBuilder
                .setDeleteIntent(getMarkMessageAsReadPendingIntent(notifiable))
        }

        if (!Compatibility.canChatMessageChannelBubble(context)) {
            Log.w("[Notifications Manager] This conversation wasn't granted bubble permission yet")
        }
        // We still need to set the bubbleMetadata, otherwise user won't ever be able to enable bubbles!
        notificationBuilder.bubbleMetadata = bubble
        return notificationBuilder.build()
    }

    /* Notifications actions */

    fun getCallAnswerPendingIntent(notifiable: Notifiable): PendingIntent {
        val answerIntent = Intent(context, NotificationBroadcastReceiver::class.java)
        answerIntent.action = INTENT_ANSWER_CALL_NOTIF_ACTION
        answerIntent.putExtra(INTENT_NOTIF_ID, notifiable.notificationId)
        answerIntent.putExtra(INTENT_REMOTE_ADDRESS, notifiable.remoteAddress)

        return PendingIntent.getBroadcast(
            context,
            notifiable.notificationId,
            answerIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun getCallAnswerAction(notifiable: Notifiable): NotificationCompat.Action {
        return NotificationCompat.Action.Builder(
            R.drawable.call_audio_start,
            context.getString(R.string.incoming_call_notification_answer_action_label),
            getCallAnswerPendingIntent(notifiable)
        ).build()
    }

    fun getCallDeclinePendingIntent(notifiable: Notifiable): PendingIntent {
        val hangupIntent = Intent(context, NotificationBroadcastReceiver::class.java)
        hangupIntent.action = INTENT_HANGUP_CALL_NOTIF_ACTION
        hangupIntent.putExtra(INTENT_NOTIF_ID, notifiable.notificationId)
        hangupIntent.putExtra(INTENT_REMOTE_ADDRESS, notifiable.remoteAddress)

        return PendingIntent.getBroadcast(
            context,
            notifiable.notificationId,
            hangupIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun getCallDeclineAction(notifiable: Notifiable): NotificationCompat.Action {
        return NotificationCompat.Action.Builder(
            R.drawable.call_hangup,
            context.getString(R.string.incoming_call_notification_hangup_action_label),
            getCallDeclinePendingIntent(notifiable)
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
        replyIntent.putExtra(INTENT_REMOTE_ADDRESS, notifiable.remoteAddress)

        // PendingIntents attached to actions with remote inputs must be mutable
        val replyPendingIntent = PendingIntent.getBroadcast(
            context,
            notifiable.notificationId,
            replyIntent,
            Compatibility.getUpdateCurrentPendingIntentFlag()
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

    private fun getMarkMessageAsReadPendingIntent(notifiable: Notifiable): PendingIntent {
        val markAsReadIntent = Intent(context, NotificationBroadcastReceiver::class.java)
        markAsReadIntent.action = INTENT_MARK_AS_READ_ACTION
        markAsReadIntent.putExtra(INTENT_NOTIF_ID, notifiable.notificationId)
        markAsReadIntent.putExtra(INTENT_LOCAL_IDENTITY, notifiable.localIdentity)
        markAsReadIntent.putExtra(INTENT_REMOTE_ADDRESS, notifiable.remoteAddress)

        return PendingIntent.getBroadcast(
            context,
            notifiable.notificationId,
            markAsReadIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun getMarkMessageAsReadAction(notifiable: Notifiable): NotificationCompat.Action {
        val markAsReadPendingIntent = getMarkMessageAsReadPendingIntent(notifiable)
        return NotificationCompat.Action.Builder(
            R.drawable.chat_send_over,
            context.getString(R.string.received_chat_notification_mark_as_read_label),
            markAsReadPendingIntent
        )
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ)
            .build()
    }
}
