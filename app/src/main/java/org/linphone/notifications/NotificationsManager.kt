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
import android.graphics.Bitmap
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import androidx.annotation.AnyThread
import androidx.annotation.MainThread
import androidx.annotation.WorkerThread
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.app.TaskStackBuilder
import androidx.core.content.LocusIdCompat
import androidx.navigation.NavDeepLinkBuilder
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.R
import org.linphone.compatibility.Compatibility
import org.linphone.contacts.AvatarGenerator
import org.linphone.contacts.ContactsManager.ContactsListener
import org.linphone.contacts.getAvatarBitmap
import org.linphone.contacts.getPerson
import org.linphone.core.Address
import org.linphone.core.Call
import org.linphone.core.ChatMessage
import org.linphone.core.ChatMessageListener
import org.linphone.core.ChatMessageListenerStub
import org.linphone.core.ChatMessageReaction
import org.linphone.core.ChatRoom
import org.linphone.core.ConferenceParams
import org.linphone.core.Core
import org.linphone.core.CoreInCallService
import org.linphone.core.CoreKeepAliveThirdPartyAccountsService
import org.linphone.core.CoreListenerStub
import org.linphone.core.Factory
import org.linphone.core.Friend
import org.linphone.core.MediaDirection
import org.linphone.core.tools.Log
import org.linphone.ui.call.CallActivity
import org.linphone.ui.main.MainActivity
import org.linphone.ui.main.MainActivity.Companion.ARGUMENTS_CHAT
import org.linphone.ui.main.MainActivity.Companion.ARGUMENTS_CONVERSATION_ID
import org.linphone.utils.AppUtils
import org.linphone.utils.FileUtils
import org.linphone.utils.LinphoneUtils
import org.linphone.utils.ShortcutUtils

class NotificationsManager
    @MainThread
    constructor(private val context: Context) {
    companion object {
        private const val TAG = "[Notifications Manager]"

        const val INTENT_HANGUP_CALL_NOTIF_ACTION = "org.linphone.HANGUP_CALL_ACTION"
        const val INTENT_ANSWER_CALL_NOTIF_ACTION = "org.linphone.ANSWER_CALL_ACTION"
        const val INTENT_REPLY_MESSAGE_NOTIF_ACTION = "org.linphone.REPLY_ACTION"
        const val INTENT_MARK_MESSAGE_AS_READ_NOTIF_ACTION = "org.linphone.MARK_AS_READ_ACTION"
        const val INTENT_NOTIF_ID = "NOTIFICATION_ID"

        const val KEY_TEXT_REPLY = "key_text_reply"
        const val INTENT_LOCAL_IDENTITY = "LOCAL_IDENTITY"
        const val INTENT_REMOTE_ADDRESS = "REMOTE_ADDRESS"

        const val CHAT_TAG = "Chat"
        private const val MISSED_CALL_TAG = "Missed call"
        const val CHAT_NOTIFICATIONS_GROUP = "CHAT_NOTIF_GROUP"

        private const val INCOMING_CALL_ID = 1
        private const val KEEP_ALIVE_FOR_THIRD_PARTY_ACCOUNTS_ID = 5
        private const val MISSED_CALL_ID = 10
    }

    private var currentInCallServiceNotificationId = -1
    private var currentKeepAliveThirdPartyAccountsForegroundServiceNotificationId = -1

    private var currentlyRingingCallRemoteAddress: Address? = null

    private val notificationManager: NotificationManagerCompat by lazy {
        NotificationManagerCompat.from(context)
    }

    private var inCallService: CoreInCallService? = null
    private var keepAliveService: CoreKeepAliveThirdPartyAccountsService? = null

    private val callNotificationsMap: HashMap<String, Notifiable> = HashMap()
    private val chatNotificationsMap: HashMap<String, Notifiable> = HashMap()
    private val previousChatNotifications: ArrayList<Int> = arrayListOf()

    private val notificationsMap = HashMap<Int, Notification>()

    private var currentlyDisplayedChatRoomId: String = ""
    private var currentlyDisplayedIncomingCallFragment: Boolean = false

    private lateinit var mediaPlayer: MediaPlayer

    private val contactsListener = object : ContactsListener {
        @WorkerThread
        override fun onContactsLoaded() { }

        @WorkerThread
        override fun onContactFoundInRemoteDirectory(friend: Friend) {
            val addresses = friend.addresses
            Log.i(
                "$TAG Found contact [${friend.name}] in remote directory with [${addresses.size}] addresses"
            )

            for ((remoteAddress, notifiable) in callNotificationsMap.entries) {
                val parsedAddress = Factory.instance().createAddress(remoteAddress)
                parsedAddress ?: continue
                val addressMatch = addresses.find {
                    it.weakEqual(parsedAddress)
                }
                if (addressMatch != null) {
                    Log.i(
                        "$TAG Found call [${addressMatch.asStringUriOnly()}] with contact in notifications, updating it"
                    )
                    updateCallNotification(notifiable, addressMatch, friend)
                }
            }

            for ((remoteAddress, notifiable) in chatNotificationsMap.entries) {
                var updated = false
                var peerAddress: Address? = null
                for (message in notifiable.messages) {
                    val parsedAddress = Factory.instance().createAddress(message.senderAddress)
                    parsedAddress ?: continue
                    val addressMatch = addresses.find {
                        it.weakEqual(parsedAddress)
                    }
                    if (addressMatch != null) {
                        peerAddress = addressMatch
                        updated = true
                        message.sender = friend.name ?: LinphoneUtils.getDisplayName(addressMatch)
                        message.friend = friend
                    }
                }
                if (updated && peerAddress != null) {
                    Log.i(
                        "$TAG Found conversation [${peerAddress.asStringUriOnly()}] with contact in notifications, updating it"
                    )
                    updateConversationNotification(notifiable, peerAddress)
                }
            }
        }
    }

    private val coreListener = object : CoreListenerStub() {
        @WorkerThread
        override fun onCallStateChanged(
            core: Core,
            call: Call,
            state: Call.State?,
            message: String
        ) {
            Log.i("$TAG Call state changed: [$state]")
            when (state) {
                Call.State.IncomingReceived, Call.State.IncomingEarlyMedia -> {
                    Log.i(
                        "$TAG Showing incoming call notification for [${call.remoteAddress.asStringUriOnly()}]"
                    )
                    showCallNotification(call, true)
                }
                Call.State.OutgoingInit -> {
                    Log.i(
                        "$TAG Showing outgoing call notification for [${call.remoteAddress.asStringUriOnly()}]"
                    )
                    showCallNotification(call, false)
                }
                Call.State.Connected,
                Call.State.StreamsRunning -> {
                    if (call.state == Call.State.Connected && call.dir == Call.Dir.Incoming) {
                        Log.i(
                            "$TAG Connected call was incoming (so it was answered), removing incoming call notification"
                        )
                        removeIncomingCallNotification()
                    }

                    if (call.state == Call.State.Connected || call.dir == Call.Dir.Incoming) {
                        Log.i(
                            "$TAG Showing connected call notification for [${call.remoteAddress.asStringUriOnly()}]"
                        )
                        showCallNotification(call, false)
                    }
                }
                Call.State.Updating -> {
                    val notifiable = getNotifiableForCall(call)
                    if (notifiable.notificationId == currentInCallServiceNotificationId) {
                        Log.i(
                            "$TAG Update foreground Service type in case video was enabled/disabled since last time"
                        )
                        startInCallForegroundService(call)
                    }
                }
                Call.State.End, Call.State.Error -> {
                    val remoteSipAddress = call.remoteAddress
                    if (call.dir == Call.Dir.Incoming && currentlyRingingCallRemoteAddress?.weakEqual(
                            remoteSipAddress
                        ) == true
                    ) {
                        Log.i(
                            "$TAG Incoming call has been declined, cancelling incoming call notification"
                        )
                        removeIncomingCallNotification()
                    }

                    Log.i(
                        "$TAG Removing terminated/declined call notification for [${remoteSipAddress.asStringUriOnly()}]"
                    )
                    dismissCallNotification(call)
                }
                Call.State.Released -> {
                    if (LinphoneUtils.isCallLogMissed(call.callLog)) {
                        showMissedCallNotification(call)
                    }
                }
                else -> {
                }
            }
        }

        @WorkerThread
        override fun onLastCallEnded(core: Core) {
            Log.i("$TAG Last call ended, stopping foreground service")
            stopInCallCallForegroundService()
        }

        @WorkerThread
        override fun onMessagesReceived(
            core: Core,
            chatRoom: ChatRoom,
            messages: Array<ChatMessage>
        ) {
            Log.i("$TAG Received ${messages.size} aggregated messages")
            if (corePreferences.disableChat) return

            val id = LinphoneUtils.getConversationId(chatRoom)
            if (currentlyDisplayedChatRoomId.isNotEmpty() && id == currentlyDisplayedChatRoomId) {
                Log.i(
                    "$TAG Do not notify received messages for currently displayed conversation [$id], but play sound if at least one message is incoming and not read"
                )

                var playSound = false
                for (message in messages) {
                    if (!message.isOutgoing && !message.isRead) {
                        playSound = true
                        break
                    }
                }
                if (playSound) {
                    playMessageReceivedSound()
                }
                return
            }

            if (chatRoom.muted) {
                Log.i("$TAG Conversation $id has been muted")
                return
            }

            if (ShortcutUtils.isShortcutToChatRoomAlreadyCreated(context, chatRoom)) {
                Log.i("$TAG Conversation shortcut already exists")
                showChatRoomNotification(chatRoom, messages)
            } else {
                Log.i(
                    "$TAG Ensure conversation shortcut exists for notification"
                )
                ShortcutUtils.createShortcutsToChatRooms(context)
                showChatRoomNotification(chatRoom, messages)
            }
        }

        @WorkerThread
        override fun onNewMessageReaction(
            core: Core,
            chatRoom: ChatRoom,
            message: ChatMessage,
            reaction: ChatMessageReaction
        ) {
            if (corePreferences.disableChat) return

            val address = reaction.fromAddress
            Log.i(
                "$TAG Reaction received [${reaction.body}] from [${address.asStringUriOnly()}] for message [$message]"
            )

            val id = LinphoneUtils.getConversationId(chatRoom)
            /*if (id == currentlyDisplayedChatRoomId) {
                Log.i(
                    "$TAG Do not notify received reaction for currently displayed conversation [$id]"
                )
                return
            }*/

            if (chatRoom.muted) {
                Log.i("$TAG Conversation $id has been muted")
                return
            }
            if (coreContext.isAddressMyself(address)) {
                Log.i("$TAG Reaction has been sent by ourselves, do not notify it")
                return
            }
            if (!coreContext.isAddressMyself(message.fromAddress)) {
                Log.i("$TAG Reaction isn't for a message we sent, do not notify it")
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
                "$TAG [${address.asStringUriOnly()}] removed it's previously sent reaction for message [$message]"
            )
            if (corePreferences.disableChat) return

            if (chatRoom.muted) {
                val id = LinphoneUtils.getConversationId(chatRoom)
                Log.i("$TAG Conversation $id has been muted")
                return
            }

            val chatRoomPeerAddress = chatRoom.peerAddress.asStringUriOnly()
            val notifiable: Notifiable? = chatNotificationsMap[chatRoomPeerAddress]
            if (notifiable == null) {
                Log.i("$TAG No notification for conversation [$chatRoomPeerAddress], nothing to do")
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
                        val pendingIntent = getChatRoomPendingIntent(
                            chatRoom,
                            notifiable.notificationId
                        )
                        val notification = createMessageNotification(
                            notifiable,
                            pendingIntent,
                            LinphoneUtils.getConversationId(chatRoom),
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

        @WorkerThread
        override fun onChatRoomRead(core: Core, chatRoom: ChatRoom) {
            Log.i(
                "$TAG Conversation [${LinphoneUtils.getConversationId(chatRoom)}] has been marked as read, removing notification if any"
            )
            dismissChatNotification(chatRoom)
        }
    }

    val chatMessageListener: ChatMessageListener = object : ChatMessageListenerStub() {
        @WorkerThread
        override fun onMsgStateChanged(message: ChatMessage, state: ChatMessage.State) {
            message.userData ?: return
            val id = message.userData as Int
            Log.i("$TAG Reply message state changed [$state] for id $id")

            if (state != ChatMessage.State.InProgress) {
                // No need to be called here twice
                message.removeListener(this)
            }

            if (state == ChatMessage.State.Delivered || state == ChatMessage.State.Displayed) {
                val address = message.chatRoom.peerAddress.asStringUriOnly()
                val notifiable = chatNotificationsMap[address]
                if (notifiable != null) {
                    if (notifiable.notificationId != id) {
                        Log.w("$TAG ID doesn't match: ${notifiable.notificationId} != $id")
                    }
                    displayReplyMessageNotification(message, notifiable)
                } else {
                    Log.e("$TAG Couldn't find notification for conversation $address")
                    cancelNotification(id, CHAT_TAG)
                }
            } else if (state == ChatMessage.State.NotDelivered) {
                Log.e("$TAG Reply wasn't delivered")
                cancelNotification(id, CHAT_TAG)
            }
        }
    }

    init {
        for (notification in notificationManager.activeNotifications) {
            if (notification.tag.isNullOrEmpty()) {
                Log.w(
                    "$TAG Found existing (call?) notification [${notification.id}] without tag, cancelling it"
                )
                notificationManager.cancel(notification.id)
            } else if (notification.tag == CHAT_TAG) {
                Log.i(
                    "[Notifications Manager] Found existing chat notification [${notification.id}]"
                )
                previousChatNotifications.add(notification.id)
            }
        }
    }

    @AnyThread
    fun setIncomingCallFragmentCurrentlyDisplayed(visible: Boolean) {
        currentlyDisplayedIncomingCallFragment = visible
    }

    @WorkerThread
    fun setCurrentlyDisplayedChatRoomId(id: String) {
        Log.i(
            "$TAG Currently displayed conversation is [$id], messages received in it won't be notified"
        )
        currentlyDisplayedChatRoomId = id
    }

    @WorkerThread
    fun resetCurrentlyDisplayedChatRoomId() {
        currentlyDisplayedChatRoomId = ""
        Log.i("$TAG Reset currently displayed conversation")
    }

    @MainThread
    fun onInCallServiceStarted(service: CoreInCallService) {
        Log.i("$TAG Service has been started")
        inCallService = service

        coreContext.postOnCoreThread { core ->
            if (core.callsNb == 0) {
                Log.w("$TAG No call anymore, stopping service")
                stopInCallCallForegroundService()
            } else if (currentInCallServiceNotificationId == -1) {
                Log.i(
                    "$TAG At least a call is still running and no foreground Service notification was found"
                )
                val call = core.currentCall ?: core.calls.first()
                startInCallForegroundService(call)
            }
        }
    }

    @MainThread
    fun onInCallServiceDestroyed() {
        Log.i("$TAG Service has been destroyed")
        inCallService = null
        currentInCallServiceNotificationId = -1
    }

    @MainThread
    fun onKeepAliveServiceStarted(service: CoreKeepAliveThirdPartyAccountsService) {
        Log.i("$TAG Keep app alive for third party accounts Service has been started")
        keepAliveService = service
        startKeepAliveServiceForeground()
    }

    @MainThread
    fun onKeepAliveServiceDestroyed() {
        Log.i("$TAG Keep app alive for third party accounts Service has been destroyed")
        stopKeepAliveServiceForeground()
        keepAliveService = null
    }

    @MainThread
    private fun createChannels(clearPreviousChannels: Boolean) {
        if (clearPreviousChannels) {
            Log.w("$TAG We were asked to remove all existing notification channels")
            for (channel in notificationManager.notificationChannels) {
                try {
                    Log.i("$TAG Deleting notification channel ID [${channel.id}]")
                    notificationManager.deleteNotificationChannel(channel.id)
                } catch (e: Exception) {
                    Log.e("$TAG Failed to delete notification channel ID [${channel.id}]: $e")
                }
            }
        }

        createThirdPartyAccountKeepAliveServiceChannel()
        createIncomingCallNotificationChannel()
        createMissedCallNotificationChannel()
        createActiveCallNotificationChannel()
        createMessageChannel()
    }

    @WorkerThread
    fun onCoreStarted(core: Core, clearChannels: Boolean) {
        Log.i("$TAG Core has been started")

        coreContext.postOnMainThread {
            createChannels(clearChannels)
        }

        core.addListener(coreListener)

        coreContext.contactsManager.addListener(contactsListener)

        val soundPath = corePreferences.messageReceivedInVisibleConversationNotificationSound
        mediaPlayer = MediaPlayer().apply {
            try {
                setAudioAttributes(
                    AudioAttributes.Builder().setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .build()
                )
                setDataSource(soundPath)
                prepare()
            } catch (e: Exception) {
                Log.e("$TAG Failed to prepare message received sound file [$soundPath]: $e")
            }
        }
    }

    @WorkerThread
    fun onCoreStopped(core: Core) {
        Log.i("$TAG Getting destroyed, clearing foreground Service & call notifications")
        core.removeListener(coreListener)
        coreContext.contactsManager.removeListener(contactsListener)
    }

    @WorkerThread
    fun removeIncomingCallNotification() {
        if (currentInCallServiceNotificationId == INCOMING_CALL_ID) {
            if (inCallService != null) {
                Log.i(
                    "$TAG Service found, stopping it as foreground before cancelling notification"
                )
                inCallService?.stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                Log.w("$TAG Incoming call foreground notification Service wasn't found, weird...")
            }
            currentInCallServiceNotificationId = -1
        } else {
            Log.i(
                "$TAG Incoming call notification wasn't used to keep running Service as foreground"
            )
        }

        cancelNotification(INCOMING_CALL_ID)
        currentlyRingingCallRemoteAddress = null
    }

    @WorkerThread
    private fun showCallNotification(call: Call, isIncoming: Boolean, friend: Friend? = null) {
        val notifiable = getNotifiableForCall(call)

        val callNotificationIntent = Intent(context, CallActivity::class.java)
        callNotificationIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (isIncoming) {
            callNotificationIntent.putExtra("IncomingCall", true)
        } else {
            callNotificationIntent.putExtra("ActiveCall", true)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            callNotificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = createCallNotification(
            call,
            notifiable,
            pendingIntent,
            isIncoming,
            friend
        )
        if (isIncoming) {
            currentlyRingingCallRemoteAddress = call.remoteAddress
            notify(INCOMING_CALL_ID, notification)
            if (currentInCallServiceNotificationId == -1) {
                startIncomingCallForegroundService(notification)
            }
        } else {
            notify(notifiable.notificationId, notification)
            if (currentInCallServiceNotificationId == -1) {
                startInCallForegroundService(call)
            }
        }
    }

    @WorkerThread
    private fun showMissedCallNotification(call: Call) {
        val missedCallCount: Int = coreContext.core.missedCallsCount
        val body: String
        if (missedCallCount > 1) {
            body = context.getString(R.string.notification_missed_calls)
                .format(missedCallCount.toString())
            Log.i("$TAG Updating missed calls notification count to $missedCallCount")
        } else {
            val remoteAddress = call.callLog.remoteAddress
            val conferenceInfo = call.callLog.conferenceInfo
            body = if (conferenceInfo != null) {
                context.getString(R.string.notification_missed_group_call)
                    .format(conferenceInfo.subject ?: LinphoneUtils.getDisplayName(remoteAddress))
            } else {
                val friend: Friend? = coreContext.contactsManager.findContactByAddress(remoteAddress)
                context.getString(R.string.notification_missed_call)
                    .format(friend?.name ?: LinphoneUtils.getDisplayName(remoteAddress))
            }
            Log.i("$TAG Creating missed call notification with title [$body]")
        }

        val pendingIntent = NavDeepLinkBuilder(context)
            .setComponentName(MainActivity::class.java)
            .setGraph(R.navigation.main_nav_graph)
            .setDestination(R.id.historyListFragment)
            .createPendingIntent()

        val builder = NotificationCompat.Builder(
            context,
            context.getString(R.string.notification_channel_missed_call_id)
        )
            .setContentTitle(context.getString(R.string.notification_missed_call_title))
            .setContentText(body)
            .setSmallIcon(R.drawable.phone_x)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_MISSED_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setWhen(System.currentTimeMillis())
            .setShowWhen(true)
            .setNumber(missedCallCount)
            .setContentIntent(pendingIntent)

        val notification = builder.build()
        notify(MISSED_CALL_ID, notification, MISSED_CALL_TAG)
    }

    @WorkerThread
    private fun startIncomingCallForegroundService(notification: Notification) {
        Log.i("$TAG Trying to start foreground Service using incoming call notification")
        val service = inCallService
        if (service != null) {
            Log.i(
                "$TAG Service found, starting it as foreground using notification ID [$INCOMING_CALL_ID] with type PHONE_CALL"
            )
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                Compatibility.startServiceForeground(
                    service,
                    INCOMING_CALL_ID,
                    notification,
                    Compatibility.FOREGROUND_SERVICE_TYPE_PHONE_CALL
                )
                currentInCallServiceNotificationId = INCOMING_CALL_ID
            } else {
                Log.e("$TAG POST_NOTIFICATIONS permission isn't granted, don't start foreground service!")
            }
        } else {
            Log.w("$TAG Core Foreground Service hasn't started yet...")
        }
    }

    @WorkerThread
    private fun startInCallForegroundService(call: Call) {
        if (LinphoneUtils.isCallIncoming(call.state)) {
            val notification = notificationsMap[INCOMING_CALL_ID]
            if (notification != null) {
                startIncomingCallForegroundService(notification)
            } else {
                Log.w(
                    "$TAG Failed to find notification for incoming call with ID [$INCOMING_CALL_ID]"
                )
            }
            return
        }

        Log.i("$TAG Trying to start/update foreground Service using call notification")
        val service = inCallService
        if (service == null) {
            Log.w("$TAG Core Foreground Service hasn't started yet...")
            return
        }

        val channelId = context.getString(R.string.notification_channel_call_id)
        val channel = notificationManager.getNotificationChannel(channelId)
        val importance = channel?.importance ?: NotificationManagerCompat.IMPORTANCE_NONE
        if (importance == NotificationManagerCompat.IMPORTANCE_NONE) {
            Log.e("$TAG Calls channel has been disabled, can't start foreground service!")
            stopInCallCallForegroundService()
            return
        }

        val notifiable = getNotifiableForCall(call)
        val notificationId = notifiable.notificationId
        val notification = if (notificationsMap.containsKey(notificationId)) {
            notificationsMap[notificationId]
        } else if (notificationsMap.containsKey(INCOMING_CALL_ID)) {
            notificationsMap[INCOMING_CALL_ID]
        } else {
            null
        }

        if (notification == null) {
            Log.w(
                "$TAG No existing notification (ID [$notificationId]) found for current call [${call.remoteAddress.asStringUriOnly()}], aborting"
            )
            stopInCallCallForegroundService()
            return
        }
        Log.i("$TAG Found notification [$notificationId] for current Call")

        var mask = Compatibility.FOREGROUND_SERVICE_TYPE_PHONE_CALL
        val callState = call.state
        if (!LinphoneUtils.isCallIncoming(callState) && !LinphoneUtils.isCallOutgoing(callState) && !LinphoneUtils.isCallEnding(
                callState
            )
        ) {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                mask = mask or Compatibility.FOREGROUND_SERVICE_TYPE_MICROPHONE
                Log.i(
                    "$TAG RECORD_AUDIO permission has been granted, adding FOREGROUND_SERVICE_TYPE_MICROPHONE to foreground Service types mask"
                )
            }
            val isSendingVideo = when (call.currentParams.videoDirection) {
                MediaDirection.SendRecv, MediaDirection.SendOnly -> true
                else -> false
            }
            if (call.currentParams.isVideoEnabled && isSendingVideo) {
                if (ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.CAMERA
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    mask = mask or Compatibility.FOREGROUND_SERVICE_TYPE_CAMERA
                    Log.i(
                        "$TAG CAMERA permission has been granted, adding FOREGROUND_SERVICE_TYPE_CAMERA to foreground Service types mask"
                    )
                }
            }
        }

        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            Log.i(
                "$TAG Service found, starting it as foreground using notification ID [${notifiable.notificationId}] with type(s) [$mask]"
            )
            Compatibility.startServiceForeground(
                service,
                notifiable.notificationId,
                notification,
                mask
            )
            currentInCallServiceNotificationId = notifiable.notificationId
        } else {
            Log.e("$TAG POST_NOTIFICATIONS permission isn't granted, don't start foreground service!")
        }
    }

    @WorkerThread
    private fun stopInCallCallForegroundService() {
        val service = inCallService
        if (service != null) {
            Log.i(
                "$TAG Stopping foreground Service (was using notification ID [$currentInCallServiceNotificationId])"
            )
            service.stopForeground(STOP_FOREGROUND_REMOVE)
            service.stopSelf()
        } else {
            Log.w("$TAG Can't stop foreground Service & notif, no Service was found")
        }
        currentInCallServiceNotificationId = -1
    }

    @WorkerThread
    private fun getNotifiableForConversation(chatRoom: ChatRoom, messages: Array<ChatMessage>): Notifiable {
        val address = chatRoom.peerAddress.asStringUriOnly()
        var notifiable: Notifiable? = chatNotificationsMap[address]
        if (notifiable == null) {
            notifiable = Notifiable(LinphoneUtils.getConversationId(chatRoom).hashCode())
            notifiable.myself = LinphoneUtils.getDisplayName(chatRoom.localAddress)
            notifiable.localIdentity = chatRoom.localAddress.asStringUriOnly()
            notifiable.remoteAddress = chatRoom.peerAddress.asStringUriOnly()

            if (chatRoom.hasCapability(ChatRoom.Capabilities.OneToOne.toInt())) {
                notifiable.isGroup = false
            } else {
                notifiable.isGroup = true
                notifiable.groupTitle = chatRoom.subject
            }

            for (message in chatRoom.unreadHistory) {
                if (message.isRead || message.isOutgoing) continue
                val notifiableMessage = getNotifiableForChatMessage(message)
                notifiable.messages.add(notifiableMessage)
            }
        } else {
            for (message in messages) {
                if (message.isRead || message.isOutgoing) continue
                val notifiableMessage = getNotifiableForChatMessage(message)
                notifiable.messages.add(notifiableMessage)
            }
        }

        chatNotificationsMap[address] = notifiable
        return notifiable
    }

    @WorkerThread
    private fun showChatRoomNotification(chatRoom: ChatRoom, messages: Array<ChatMessage>) {
        val notifiable = getNotifiableForConversation(chatRoom, messages)

        if (!chatRoom.hasCapability(ChatRoom.Capabilities.OneToOne.toInt())) {
            if (chatRoom.subject != notifiable.groupTitle) {
                Log.i(
                    "$TAG Updating notification subject from [${notifiable.groupTitle}] to [${chatRoom.subject}]"
                )
                notifiable.groupTitle = chatRoom.subject
            }
        }

        if (notifiable.messages.isNotEmpty()) {
            val me = coreContext.contactsManager.getMePerson(chatRoom.localAddress)
            val pendingIntent = getChatRoomPendingIntent(chatRoom, notifiable.notificationId)
            val notification = createMessageNotification(
                notifiable,
                pendingIntent,
                LinphoneUtils.getConversationId(chatRoom),
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
        address: Address,
        message: ChatMessage
    ) {
        val notifiable = getNotifiableForConversation(chatRoom, arrayOf(message))

        // Check if a previous reaction notifiable exists from the same person & for the same message
        val from = address.asStringUriOnly()
        val found = notifiable.messages.find {
            it.isReaction && it.reactionToMessageId == message.messageId && it.reactionFrom == from
        }
        if (found != null) {
            Log.i(
                "$TAG Found a previous notifiable for a reaction from the same person to the same message"
            )
            if (notifiable.messages.remove(found)) {
                Log.i("$TAG Previous reaction notifiable removed")
            } else {
                Log.w("$TAG Failed to remove previous reaction notifiable")
            }
        }

        val contact =
            coreContext.contactsManager.findContactByAddress(address)
        val displayName = contact?.name ?: LinphoneUtils.getDisplayName(address)

        val originalMessage = LinphoneUtils.getPlainTextDescribingMessage(message)
        val text = AppUtils.getString(R.string.notification_chat_message_reaction_received).format(
            reaction,
            originalMessage
        )

        val notifiableMessage = NotifiableMessage(
            text,
            contact,
            displayName,
            address.asStringUriOnly(),
            message.time * 1000, // Linphone timestamps are in seconds
            isOutgoing = false,
            isReaction = true,
            reactionToMessageId = message.messageId,
            reactionFrom = from
        )
        notifiable.messages.add(notifiableMessage)

        if (notifiable.messages.isNotEmpty()) {
            val me = coreContext.contactsManager.getMePerson(chatRoom.localAddress)
            val pendingIntent = getChatRoomPendingIntent(chatRoom, notifiable.notificationId)
            val notification = createMessageNotification(
                notifiable,
                pendingIntent,
                LinphoneUtils.getConversationId(chatRoom),
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
    private fun updateConversationNotification(notifiable: Notifiable, remoteAddress: Address) {
        val localAddress = Factory.instance().createAddress(notifiable.localIdentity.orEmpty())
        localAddress ?: return
        val params: ConferenceParams? = null
        val chatRoom: ChatRoom? = coreContext.core.searchChatRoom(
            params,
            localAddress,
            remoteAddress,
            arrayOfNulls<Address>(0)
        )
        chatRoom ?: return

        val me = coreContext.contactsManager.getMePerson(chatRoom.localAddress)
        val pendingIntent = getChatRoomPendingIntent(chatRoom, notifiable.notificationId)
        val notification = createMessageNotification(
            notifiable,
            pendingIntent,
            LinphoneUtils.getConversationId(chatRoom),
            me
        )
        Log.i(
            "$TAG Updating chat notification with ID [${notifiable.notificationId}]"
        )
        notify(notifiable.notificationId, notification, CHAT_TAG)
    }

    @WorkerThread
    private fun notify(id: Int, notification: Notification, tag: String? = null) {
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            Log.i(
                "$TAG Notifying using ID [$id] and ${if (tag == null) "without tag" else "with tag [$tag]"}"
            )
            try {
                notificationManager.notify(tag, id, notification)
                notificationsMap[id] = notification
            } catch (iae: IllegalArgumentException) {
                if (inCallService == null && tag == null) {
                    // We can't notify using CallStyle if there isn't a foreground service running
                    Log.w(
                        "$TAG Foreground Service hasn't started yet, can't display a CallStyle notification until then: $iae"
                    )
                } else {
                    Log.e("$TAG Illegal Argument Exception occurred: $iae")
                }
            } catch (e: Exception) {
                Log.e("$TAG Exception occurred: $e")
            }
        } else {
            Log.w("$TAG POST_NOTIFICATIONS permission wasn't granted")
        }
    }

    @WorkerThread
    fun cancelNotification(id: Int, tag: String? = null) {
        Log.i(
            "$TAG Canceling notification with ID [$id] and ${if (tag == null) "without tag" else "with tag [$tag]"}"
        )
        notificationManager.cancel(tag, id)
        notificationsMap.remove(id)
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
            notifiable.remoteAddress = address

            callNotificationsMap[address] = notifiable
        }
        return notifiable
    }

    @WorkerThread
    private fun getNotifiableForChatMessage(message: ChatMessage): NotifiableMessage {
        val contact =
            coreContext.contactsManager.findContactByAddress(message.fromAddress)
        val displayName = contact?.name ?: LinphoneUtils.getDisplayName(message.fromAddress)

        val text = LinphoneUtils.getPlainTextDescribingMessage(message)
        val address = message.fromAddress
        val notifiableMessage = NotifiableMessage(
            text,
            contact,
            displayName,
            address.asStringUriOnly(),
            message.time * 1000, // Linphone timestamps are in seconds
            isOutgoing = message.isOutgoing
        )

        for (content in message.contents) {
            if (content.isFile) {
                val path = content.filePath
                if (path != null) {
                    val contentUri = FileUtils.getPublicFilePath(context, path)
                    val filePath = contentUri.toString()
                    val extension = FileUtils.getExtensionFromFileName(filePath)
                    if (extension.isNotEmpty()) {
                        val mime = FileUtils.getMimeTypeFromExtension(extension)
                        notifiableMessage.filePath = contentUri
                        notifiableMessage.fileMime = mime
                        Log.i("$TAG Added file $contentUri with MIME $mime to notification")
                    } else {
                        Log.e("$TAG Couldn't find extension for incoming message with file $path")
                    }
                }
            }
        }

        return notifiableMessage
    }

    @WorkerThread
    private fun createCallNotification(
        call: Call,
        notifiable: Notifiable,
        pendingIntent: PendingIntent?,
        isIncoming: Boolean,
        friend: Friend? = null
    ): Notification {
        val declineIntent = getCallDeclinePendingIntent(notifiable)
        val answerIntent = getCallAnswerPendingIntent(notifiable)

        val remoteAddress = call.callLog.remoteAddress
        val conference = call.conference
        val conferenceInfo = LinphoneUtils.getConferenceInfoIfAny(call)
        val isConference = conference != null || conferenceInfo != null

        val caller = if (isConference) {
            val subject = conferenceInfo?.subject ?: conference?.subject ?: LinphoneUtils.getDisplayName(
                remoteAddress
            )
            Person.Builder()
                .setName(subject)
                .setIcon(
                    AvatarGenerator(context).setInitials(AppUtils.getInitials(subject)).buildIcon()
                )
                .setImportant(false)
                .build()
        } else {
            val contact = friend
                ?: coreContext.contactsManager.findContactByAddress(remoteAddress)
            val displayName = contact?.name ?: LinphoneUtils.getDisplayName(remoteAddress)

            getPerson(contact, displayName)
        }

        val isVideo = LinphoneUtils.isVideoEnabled(call)

        val smallIcon = if (isConference) {
            R.drawable.video_conference
        } else if (isVideo) {
            R.drawable.video_camera
        } else {
            R.drawable.phone
        }

        val style = if (isIncoming) {
            if (!Compatibility.hasFullScreenIntentPermission(context)) {
                Log.e(
                    "$TAG Android >= 14 & full screen intent permission wasn't granted, incoming call may not be visible!"
                )
            }
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

        val channelId = if (isIncoming) {
            context.getString(R.string.notification_channel_incoming_call_id)
        } else {
            context.getString(R.string.notification_channel_call_id)
        }
        val channel = notificationManager.getNotificationChannel(channelId)
        val importance = channel?.importance ?: NotificationManagerCompat.IMPORTANCE_NONE
        Log.i(
            "Creating notification for ${if (isIncoming) "[incoming] " else ""}[${if (isConference) "conference" else "call"}] with video [${if (isVideo) "enabled" else "disabled"}] on channel [$channel] with importance [$importance]"
        )

        val builder = NotificationCompat.Builder(
            context,
            channelId
        ).apply {
            try {
                style.setIsVideo(isVideo)
                setStyle(style)
            } catch (iae: IllegalArgumentException) {
                Log.e(
                    "$TAG Can't use notification call style: $iae"
                )
            }
            setColor(context.resources.getColor(R.color.gray_600, context.theme))
            setColorized(true)
            setOnlyAlertOnce(true)
            setSmallIcon(smallIcon)
            setCategory(NotificationCompat.CATEGORY_CALL)
            setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            if (isIncoming) {
                setPriority(NotificationCompat.PRIORITY_MAX)
            } else {
                setPriority(NotificationCompat.PRIORITY_HIGH)
            }
            setWhen(call.callLog.startDate * 1000) // Linphone timestamps are in seconds
            setAutoCancel(false)
            setOngoing(true)
            setContentIntent(pendingIntent)
            setFullScreenIntent(pendingIntent, true)
        }

        return builder.build()
    }

    @WorkerThread
    private fun updateCallNotification(
        notifiable: Notifiable,
        remoteAddress: Address,
        friend: Friend
    ) {
        val call = coreContext.core.getCallByRemoteAddress2(remoteAddress)
        if (call == null) {
            Log.w(
                "$TAG Failed to find call with remote SIP URI [${remoteAddress.asStringUriOnly()}]"
            )
            return
        }
        val isIncoming = LinphoneUtils.isCallIncoming(call.state)

        val notification = if (isIncoming) {
            notificationsMap[INCOMING_CALL_ID]
        } else {
            notificationsMap[notifiable.notificationId]
        }
        if (notification == null) {
            Log.w(
                "$TAG Failed to find notification with ID [${notifiable.notificationId}], creating a new one"
            )
            showCallNotification(call, isIncoming, friend)
            return
        }

        val pendingIntent = notification.fullScreenIntent
        val newNotification = createCallNotification(
            call,
            notifiable,
            pendingIntent,
            isIncoming,
            friend
        )
        if (isIncoming) {
            if (!currentlyDisplayedIncomingCallFragment) {
                Log.i("$TAG Updating incoming call notification with ID [$INCOMING_CALL_ID]")
                notify(INCOMING_CALL_ID, newNotification)
            } else {
                Log.i(
                    "$TAG Incoming call fragment is visible, do not re-send an incoming call notification"
                )
            }
        } else {
            Log.i("$TAG Updating call notification with ID [${notifiable.notificationId}]")
            notify(notifiable.notificationId, newNotification)
        }
    }

    @WorkerThread
    private fun createMessageNotification(
        notifiable: Notifiable,
        pendingIntent: PendingIntent,
        id: String,
        me: Person
    ): Notification {
        val style = NotificationCompat.MessagingStyle(me)
        val allPersons = arrayListOf<Person>()

        var lastPersonAvatar: Bitmap? = null
        var lastPerson: Person? = null
        for (message in notifiable.messages) {
            val friend = message.friend
            val person = getPerson(friend, message.sender)

            if (!message.isOutgoing) {
                // We don't want to see our own avatar
                lastPerson = person
                lastPersonAvatar = friend?.getAvatarBitmap()

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
        Log.i(
            "$TAG Conversation is ${if (style.isGroupConversation) "group" else "1-1"} with title [${style.conversationTitle}]"
        )

        val largeIcon = lastPersonAvatar
        val notificationBuilder = NotificationCompat.Builder(
            context,
            context.getString(R.string.notification_channel_chat_id)
        )
            .setSmallIcon(R.drawable.chat_teardrop_text)
            .setAutoCancel(true)
            .setLargeIcon(largeIcon)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setGroup(CHAT_NOTIFICATIONS_GROUP)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setNumber(notifiable.messages.size)
            .setWhen(System.currentTimeMillis())
            .setShowWhen(true)
            .setStyle(style)
            .setContentIntent(pendingIntent)
            .addAction(getMarkMessageAsReadAction(notifiable))
            .addAction(getReplyMessageAction(notifiable))
            .setShortcutId(id)
            .setLocusId(LocusIdCompat(id))

        for (person in allPersons) {
            notificationBuilder.addPerson(person)
        }

        if (corePreferences.markConversationAsReadWhenDismissingMessageNotification) {
            Log.i("$TAG Conversation will be marked as read upon notification dismissal")
            notificationBuilder
                .setDeleteIntent(getMarkMessageAsReadPendingIntent(notifiable))
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

    @WorkerThread
    fun dismissChatNotification(chatRoom: ChatRoom): Boolean {
        val address = chatRoom.peerAddress.asStringUriOnly()
        val notifiable: Notifiable? = chatNotificationsMap[address]
        if (notifiable != null) {
            Log.i(
                "$TAG Dismissing notification for conversation [${chatRoom.peerAddress.asStringUriOnly()}] with id ${notifiable.notificationId}"
            )
            notifiable.messages.clear()
            cancelNotification(notifiable.notificationId, CHAT_TAG)
            return true
        } else {
            val previousNotificationId = previousChatNotifications.find { id ->
                id == LinphoneUtils.getConversationId(chatRoom).hashCode()
            }
            if (previousNotificationId != null) {
                Log.i(
                    "$TAG Found previous notification with same ID [$previousNotificationId], canceling it"
                )
                cancelNotification(previousNotificationId, CHAT_TAG)
                return true
            }
        }
        return false
    }

    @AnyThread
    fun getCallDeclinePendingIntent(notifiable: Notifiable): PendingIntent {
        val hangupIntent = Intent(context, NotificationBroadcastReceiver::class.java)
        hangupIntent.action = INTENT_HANGUP_CALL_NOTIF_ACTION
        hangupIntent.putExtra(INTENT_NOTIF_ID, notifiable.notificationId)
        hangupIntent.putExtra(INTENT_REMOTE_ADDRESS, notifiable.remoteAddress)

        return PendingIntent.getBroadcast(
            context,
            3,
            hangupIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    @AnyThread
    fun getCallAnswerPendingIntent(notifiable: Notifiable): PendingIntent {
        val answerIntent = Intent(context, NotificationBroadcastReceiver::class.java)
        answerIntent.action = INTENT_ANSWER_CALL_NOTIF_ACTION
        answerIntent.putExtra(INTENT_NOTIF_ID, notifiable.notificationId)
        answerIntent.putExtra(INTENT_REMOTE_ADDRESS, notifiable.remoteAddress)

        return PendingIntent.getBroadcast(
            context,
            2,
            answerIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    @WorkerThread
    private fun displayReplyMessageNotification(message: ChatMessage, notifiable: Notifiable) {
        Log.i(
            "$TAG Updating message notification with reply for notification ${notifiable.notificationId}"
        )

        val text = message.contents.find { content -> content.isText }?.utf8Text ?: ""
        val senderAddress = message.fromAddress
        val reply = NotifiableMessage(
            text,
            null,
            notifiable.myself ?: LinphoneUtils.getDisplayName(senderAddress),
            senderAddress.asStringUriOnly(),
            System.currentTimeMillis(),
            isOutgoing = true
        )
        notifiable.messages.add(reply)

        val chatRoom = message.chatRoom
        val pendingIntent = getChatRoomPendingIntent(chatRoom, notifiable.notificationId)
        val me = coreContext.contactsManager.getMePerson(chatRoom.localAddress)
        val notification = createMessageNotification(
            notifiable,
            pendingIntent,
            LinphoneUtils.getConversationId(chatRoom),
            me
        )
        notify(notifiable.notificationId, notification, CHAT_TAG)
    }

    @AnyThread
    private fun getReplyMessageAction(notifiable: Notifiable): NotificationCompat.Action {
        val replyLabel =
            context.resources.getString(R.string.notification_reply_to_message)
        val remoteInput =
            RemoteInput.Builder(KEY_TEXT_REPLY).setLabel(replyLabel).build()

        val replyIntent = Intent(context, NotificationBroadcastReceiver::class.java)
        replyIntent.action = INTENT_REPLY_MESSAGE_NOTIF_ACTION
        replyIntent.putExtra(INTENT_NOTIF_ID, notifiable.notificationId)
        replyIntent.putExtra(INTENT_LOCAL_IDENTITY, notifiable.localIdentity)
        replyIntent.putExtra(INTENT_REMOTE_ADDRESS, notifiable.remoteAddress)

        // PendingIntents attached to actions with remote inputs must be mutable
        val replyPendingIntent = PendingIntent.getBroadcast(
            context,
            notifiable.notificationId,
            replyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        return NotificationCompat.Action.Builder(
            R.drawable.paper_plane_right,
            context.getString(R.string.notification_reply_to_message),
            replyPendingIntent
        )
            .addRemoteInput(remoteInput)
            .setAllowGeneratedReplies(true)
            .setShowsUserInterface(false)
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
            .build()
    }

    @AnyThread
    private fun getMarkMessageAsReadPendingIntent(notifiable: Notifiable): PendingIntent {
        val markAsReadIntent = Intent(context, NotificationBroadcastReceiver::class.java)
        markAsReadIntent.action = INTENT_MARK_MESSAGE_AS_READ_NOTIF_ACTION
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

    @AnyThread
    private fun getMarkMessageAsReadAction(notifiable: Notifiable): NotificationCompat.Action {
        val markAsReadPendingIntent = getMarkMessageAsReadPendingIntent(notifiable)
        return NotificationCompat.Action.Builder(
            R.drawable.envelope_simple_open,
            context.getString(R.string.notification_mark_message_as_read),
            markAsReadPendingIntent
        )
            .setShowsUserInterface(false)
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ)
            .build()
    }

    @WorkerThread
    private fun getPerson(friend: Friend?, displayName: String): Person {
        return friend?.getPerson()
            ?: Person.Builder()
                .setName(displayName)
                .setIcon(
                    AvatarGenerator(context).setInitials(AppUtils.getInitials(displayName)).buildIcon()
                )
                .setKey(displayName)
                .setImportant(false)
                .build()
    }

    @MainThread
    private fun startKeepAliveServiceForeground() {
        Log.i(
            "$TAG Trying to start keep alive for third party accounts foreground Service using call notification"
        )

        val channelId = context.getString(R.string.notification_channel_service_id)
        val channel = notificationManager.getNotificationChannel(channelId)
        val importance = channel?.importance ?: NotificationManagerCompat.IMPORTANCE_NONE
        if (importance == NotificationManagerCompat.IMPORTANCE_NONE) {
            Log.e(
                "$TAG Keep alive for third party accounts Service channel has been disabled, can't start foreground service!"
            )
            return
        }

        val service = keepAliveService
        if (service != null) {
            val pendingIntent = TaskStackBuilder.create(context).run {
                addNextIntentWithParentStack(
                    Intent(context, MainActivity::class.java).apply {
                        setAction(Intent.ACTION_MAIN) // Needed as well
                    }
                )
                getPendingIntent(
                    KEEP_ALIVE_FOR_THIRD_PARTY_ACCOUNTS_ID,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )!!
            }

            val builder = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.linphone_notification)
                .setAutoCancel(false)
                .setOngoing(true)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setShowWhen(false)
                .setContentIntent(pendingIntent)
            val notification = builder.build()

            Log.i(
                "$TAG Keep alive for third party accounts Service found, starting it as foreground using notification ID [$KEEP_ALIVE_FOR_THIRD_PARTY_ACCOUNTS_ID] with type [SPECIAL_USE]"
            )
            Compatibility.startServiceForeground(
                service,
                KEEP_ALIVE_FOR_THIRD_PARTY_ACCOUNTS_ID,
                notification,
                Compatibility.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
            currentKeepAliveThirdPartyAccountsForegroundServiceNotificationId = KEEP_ALIVE_FOR_THIRD_PARTY_ACCOUNTS_ID
        } else {
            Log.w("$TAG Keep alive for third party accounts Service hasn't started yet...")
        }
    }

    @MainThread
    private fun stopKeepAliveServiceForeground() {
        val service = keepAliveService
        if (service != null) {
            Log.i(
                "$TAG Stopping keep alive for third party accounts foreground Service (was using notification ID [$currentKeepAliveThirdPartyAccountsForegroundServiceNotificationId])"
            )
            service.stopForeground(STOP_FOREGROUND_REMOVE)
            service.stopSelf()
        } else {
            Log.w(
                "$TAG Can't stop keep alive for third party accounts foreground Service & notif, no Service was found"
            )
        }
        currentKeepAliveThirdPartyAccountsForegroundServiceNotificationId = -1
    }

    @MainThread
    private fun createIncomingCallNotificationChannel() {
        val id = context.getString(R.string.notification_channel_incoming_call_id)
        val name = context.getString(R.string.notification_channel_incoming_call_name)

        val ringtone = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .setLegacyStreamType(AudioManager.STREAM_RING)
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE).build()

        val channel = NotificationChannel(id, name, NotificationManager.IMPORTANCE_HIGH).apply {
            description = name
            setSound(ringtone, audioAttributes)
        }
        notificationManager.createNotificationChannel(channel)
    }

    @MainThread
    private fun createMissedCallNotificationChannel() {
        val id = context.getString(R.string.notification_channel_missed_call_id)
        val name = context.getString(R.string.notification_channel_missed_call_name)

        val channel = NotificationChannel(id, name, NotificationManager.IMPORTANCE_HIGH).apply {
            description = name
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            enableVibration(true)
        }
        notificationManager.createNotificationChannel(channel)
    }

    @MainThread
    private fun createActiveCallNotificationChannel() {
        val id = context.getString(R.string.notification_channel_call_id)
        val name = context.getString(R.string.notification_channel_call_name)

        val channel = NotificationChannel(id, name, NotificationManager.IMPORTANCE_LOW).apply {
            description = name
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        notificationManager.createNotificationChannel(channel)
    }

    @MainThread
    private fun createMessageChannel() {
        val id = context.getString(R.string.notification_channel_chat_id)
        val name = context.getString(R.string.notification_channel_chat_name)

        val channel = NotificationChannel(id, name, NotificationManager.IMPORTANCE_HIGH).apply {
            description = name
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            enableVibration(true)
        }
        notificationManager.createNotificationChannel(channel)
    }

    @MainThread
    private fun createThirdPartyAccountKeepAliveServiceChannel() {
        val id = context.getString(R.string.notification_channel_service_id)
        val name = context.getString(R.string.notification_channel_service_name)

        val channel = NotificationChannel(id, name, NotificationManager.IMPORTANCE_LOW).apply {
            description = context.getString(R.string.notification_channel_service_desc)
        }
        notificationManager.createNotificationChannel(channel)
    }

    @WorkerThread
    private fun getChatRoomPendingIntent(chatRoom: ChatRoom, notificationId: Int): PendingIntent {
        val args = Bundle()
        args.putBoolean(ARGUMENTS_CHAT, true)
        args.putString(ARGUMENTS_CONVERSATION_ID, LinphoneUtils.getConversationId(chatRoom))

        // Not using NavDeepLinkBuilder to prevent stacking a ConversationsListFragment above another one
        return TaskStackBuilder.create(context).run {
            addNextIntentWithParentStack(
                Intent(context, MainActivity::class.java).apply {
                    setAction(Intent.ACTION_MAIN) // Needed as well
                    putExtras(args) // Need to pass args here for Chat extra
                }
            )
            getPendingIntent(
                notificationId,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                args // Need to pass args here too for Remote & Local SIP URIs
            )!!
        }
    }

    @WorkerThread
    private fun playMessageReceivedSound() {
        if (::mediaPlayer.isInitialized) {
            try {
                mediaPlayer.start()
            } catch (e: Exception) {
                Log.e("$TAG Failed to play message received sound file: $e")
            }
        }
    }

    class Notifiable(val notificationId: Int) {
        var myself: String? = null

        var localIdentity: String? = null
        var remoteAddress: String? = null

        var isGroup: Boolean = false
        var groupTitle: String? = null
        val messages: ArrayList<NotifiableMessage> = arrayListOf()
    }

    class NotifiableMessage(
        val message: String,
        var friend: Friend?,
        var sender: String,
        val senderAddress: String,
        val time: Long,
        var filePath: Uri? = null,
        var fileMime: String? = null,
        val isOutgoing: Boolean = false,
        val isReaction: Boolean = false,
        val reactionToMessageId: String? = null,
        val reactionFrom: String? = null
    )
}
