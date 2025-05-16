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
package org.linphone.ui.main.chat.model

import android.text.Spannable
import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.lifecycle.MutableLiveData
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.core.Address
import org.linphone.core.ChatMessage
import org.linphone.core.ChatMessageListenerStub
import org.linphone.core.ChatRoom
import org.linphone.core.ChatRoom.Capabilities
import org.linphone.core.ChatRoomListenerStub
import org.linphone.core.EventLog
import org.linphone.core.Friend
import org.linphone.core.tools.Log
import org.linphone.ui.main.contacts.model.ContactAvatarModel
import org.linphone.utils.AppUtils
import org.linphone.utils.FileUtils
import org.linphone.utils.LinphoneUtils
import org.linphone.utils.ShortcutUtils
import org.linphone.utils.TimestampUtils

class ConversationModel
    @WorkerThread
    constructor(val chatRoom: ChatRoom) {
    companion object {
        private const val TAG = "[Conversation Model]"
    }

    val id = LinphoneUtils.getConversationId(chatRoom)

    val isGroup = !chatRoom.hasCapability(Capabilities.OneToOne.toInt()) && chatRoom.hasCapability(
        Capabilities.Conference.toInt()
    )

    val isEncrypted = chatRoom.hasCapability(Capabilities.Encrypted.toInt())

    val isEncryptionAvailable = LinphoneUtils.isEndToEndEncryptedChatAvailable(chatRoom.core)

    val isReadOnly = MutableLiveData<Boolean>()

    val subject = MutableLiveData<String>()

    val lastUpdateTime = MutableLiveData<Long>()

    val isComposing = MutableLiveData<Boolean>()

    val composingLabel = MutableLiveData<String>()

    val isMuted = MutableLiveData<Boolean>()

    val isEphemeral = MutableLiveData<Boolean>()

    val lastMessageTextSender = MutableLiveData<String>()

    val lastMessageText = MutableLiveData<Spannable>()

    val lastMessageDeliveryIcon = MutableLiveData<Int>()

    val lastMessageContentIcon = MutableLiveData<Int>()

    val isLastMessageOutgoing = MutableLiveData<Boolean>()

    val dateTime = MutableLiveData<String>()

    val unreadMessageCount = MutableLiveData<Int>()

    val avatarModel = MutableLiveData<ContactAvatarModel>()

    val isBeingDeleted = MutableLiveData<Boolean>()

    private var lastMessage: ChatMessage? = null

    private val chatRoomListener = object : ChatRoomListenerStub() {
        @WorkerThread
        override fun onStateChanged(chatRoom: ChatRoom, newState: ChatRoom.State?) {
            Log.i("$TAG Conversation state changed [${chatRoom.state}]")
            if (chatRoom.state == ChatRoom.State.Created) {
                subject.postValue(chatRoom.subject)
                computeParticipants()
            } else if (chatRoom.state == ChatRoom.State.Deleted) {
                Log.i("$TAG Conversation [$id] has been deleted")
                isBeingDeleted.postValue(false)
            }
        }

        override fun onConferenceJoined(chatRoom: ChatRoom, eventLog: EventLog) {
            // This is required as a Created chat room may not have the participants list yet
            Log.i("$TAG Conversation has been joined")
            subject.postValue(chatRoom.subject)
            computeParticipants()
        }

        @WorkerThread
        override fun onConferenceLeft(chatRoom: ChatRoom, eventLog: EventLog) {
            Log.w("TAG Conversation has been left")
            isReadOnly.postValue(chatRoom.isReadOnly)
        }

        @WorkerThread
        override fun onIsComposingReceived(
            chatRoom: ChatRoom,
            remoteAddress: Address,
            isComposing: Boolean
        ) {
            computeComposingLabel()
        }

        override fun onNewEvent(chatRoom: ChatRoom, eventLog: EventLog) {
            updateLastUpdatedTime()
        }

        override fun onNewEvents(chatRoom: ChatRoom, eventLogs: Array<out EventLog>) {
            updateLastMessage()
            updateLastUpdatedTime()
            unreadMessageCount.postValue(chatRoom.unreadMessagesCount)
        }

        @WorkerThread
        override fun onChatMessageSending(chatRoom: ChatRoom, eventLog: EventLog) {
            updateLastMessage()
        }

        @WorkerThread
        override fun onChatRoomRead(chatRoom: ChatRoom) {
            unreadMessageCount.postValue(chatRoom.unreadMessagesCount)
        }

        @WorkerThread
        override fun onSubjectChanged(chatRoom: ChatRoom, eventLog: EventLog) {
            Log.i("$TAG Conversation subject changed [${chatRoom.subject}]")
            subject.postValue(chatRoom.subject)
        }

        @WorkerThread
        override fun onEphemeralEvent(chatRoom: ChatRoom, eventLog: EventLog) {
            isEphemeral.postValue(chatRoom.isEphemeralEnabled)
        }

        @WorkerThread
        override fun onEphemeralMessageDeleted(chatRoom: ChatRoom, eventLog: EventLog) {
            Log.i("$TAG An ephemeral message lifetime has expired, updating last displayed message")
            updateLastMessage()
        }
    }

    private val chatMessageListener = object : ChatMessageListenerStub() {
        @WorkerThread
        override fun onMsgStateChanged(message: ChatMessage, state: ChatMessage.State?) {
            updateLastMessageStatus(message)
        }
    }

    init {
        chatRoom.addListener(chatRoomListener)

        computeComposingLabel()
        subject.postValue(chatRoom.subject)
        computeParticipants()

        isMuted.postValue(chatRoom.muted)
        isEphemeral.postValue(chatRoom.isEphemeralEnabled)
        isReadOnly.postValue(chatRoom.isReadOnly)
        Log.d(
            "$TAG Ephemeral messages are [${if (chatRoom.isEphemeralEnabled) "enabled" else "disabled"}], lifetime is [${chatRoom.ephemeralLifetime}]"
        )
        lastMessageContentIcon.postValue(0)

        updateLastMessage()
        updateLastUpdatedTime()

        unreadMessageCount.postValue(chatRoom.unreadMessagesCount)
    }

    @WorkerThread
    fun destroy() {
        lastMessage?.removeListener(chatMessageListener)
        lastMessage = null

        chatRoom.removeListener(chatRoomListener)
    }

    @UiThread
    fun markAsRead() {
        coreContext.postOnCoreThread {
            chatRoom.markAsRead()
            unreadMessageCount.postValue(chatRoom.unreadMessagesCount)
            Log.i("$TAG Conversation [$id] has been marked as read")
        }
    }

    @UiThread
    fun updateUnreadCount() {
        coreContext.postOnCoreThread {
            unreadMessageCount.postValue(chatRoom.unreadMessagesCount)
        }
    }

    @UiThread
    fun updateMuteState() {
        coreContext.postOnCoreThread {
            isMuted.postValue(chatRoom.muted)
        }
    }

    @UiThread
    fun toggleMute() {
        coreContext.postOnCoreThread {
            chatRoom.muted = !chatRoom.muted
            val muted = chatRoom.muted
            if (muted) {
                Log.i("$TAG Conversation [$id] is now muted")
            } else {
                Log.i("$TAG Conversation [$id] is no longer muted")
            }
            isMuted.postValue(muted)
        }
    }

    @UiThread
    fun call() {
        coreContext.postOnCoreThread {
            val address = chatRoom.participants.firstOrNull()?.address ?: chatRoom.peerAddress
            Log.i("$TAG Calling [${address.asStringUriOnly()}]")
            coreContext.startAudioCall(address)
        }
    }

    @UiThread
    fun delete() {
        coreContext.postOnCoreThread { core ->
            Log.i("$TAG Deleting conversation [$id]")
            isBeingDeleted.postValue(true)
            val basic = chatRoom.hasCapability(Capabilities.Basic.toInt())
            ShortcutUtils.removeShortcutToChatRoom(chatRoom)
            core.deleteChatRoom(chatRoom)

            if (basic) {
                Log.i("$TAG Conversation [$id] has been deleted")
                isBeingDeleted.postValue(false)
            }
        }
    }

    @UiThread
    fun leaveGroup() {
        coreContext.postOnCoreThread {
            chatRoom.leave()
            Log.i("$TAG Group conversation [$id] has been leaved")
            isReadOnly.postValue(true)
        }
    }

    @WorkerThread
    private fun updateLastMessageStatus(message: ChatMessage) {
        val isOutgoing = message.isOutgoing

        if (isGroup && !isOutgoing) {
            val fromAddress = message.fromAddress
            val sender = coreContext.contactsManager.findContactByAddress(fromAddress)
            val name = sender?.name ?: LinphoneUtils.getDisplayName(fromAddress)
            val senderName = AppUtils.getFormattedString(
                R.string.conversations_last_message_format,
                name
            )
            lastMessageTextSender.postValue(senderName)
        } else {
            lastMessageTextSender.postValue("")
        }
        lastMessageText.postValue(LinphoneUtils.getFormattedTextDescribingMessage(message))

        isLastMessageOutgoing.postValue(isOutgoing)
        if (isOutgoing) {
            lastMessageDeliveryIcon.postValue(LinphoneUtils.getChatIconResId(message.state))
        }

        if (message.isForward) {
            lastMessageContentIcon.postValue(R.drawable.forward)
        } else {
            val firstContent = message.contents.firstOrNull()
            val icon = if (firstContent?.isIcalendar == true) {
                R.drawable.calendar
            } else if (firstContent?.isVoiceRecording == true) {
                R.drawable.waveform
            } else if (firstContent?.isFile == true) {
                val mime = "${firstContent.type}/${firstContent.subtype}"
                val mimeType = FileUtils.getMimeType(mime)
                val drawable = when (mimeType) {
                    FileUtils.MimeType.Image -> R.drawable.file_image
                    FileUtils.MimeType.Video -> R.drawable.file_video
                    FileUtils.MimeType.Audio -> R.drawable.file_audio
                    FileUtils.MimeType.Pdf -> R.drawable.file_pdf
                    FileUtils.MimeType.PlainText -> R.drawable.file_text
                    else -> R.drawable.file
                }
                drawable
            } else if (firstContent?.isFileTransfer == true) {
                R.drawable.download_simple
            } else {
                0
            }
            lastMessageContentIcon.postValue(icon)
        }
    }

    @WorkerThread
    fun updateLastMessage() {
        lastMessage?.removeListener(chatMessageListener)
        lastMessage = null

        val message = chatRoom.lastMessageInHistory
        if (message != null) {
            updateLastMessageStatus(message)

            if (message.isOutgoing && message.state != ChatMessage.State.Displayed) {
                message.addListener(chatMessageListener)
                lastMessage = message
            } else if (message.contents.find { it.isFileTransfer == true } != null) {
                message.addListener(chatMessageListener)
                lastMessage = message
            }
        } else {
            Log.w("$TAG No last message to display for conversation [$id]")
        }
    }

    @WorkerThread
    private fun updateLastUpdatedTime() {
        val timestamp = chatRoom.lastUpdateTime
        val humanReadableTimestamp = when {
            TimestampUtils.isToday(timestamp) -> {
                TimestampUtils.timeToString(chatRoom.lastUpdateTime)
            }
            TimestampUtils.isYesterday(timestamp) -> {
                AppUtils.getString(R.string.yesterday)
            }
            else -> {
                TimestampUtils.toString(chatRoom.lastUpdateTime, onlyDate = true)
            }
        }
        dateTime.postValue(humanReadableTimestamp)
        lastUpdateTime.postValue(timestamp)
    }

    @WorkerThread
    fun computeParticipants() {
        val friends = arrayListOf<Friend>()
        val address = if (chatRoom.hasCapability(Capabilities.Basic.toInt())) {
            Log.d("$TAG Conversation [$id] is 'Basic'")
            chatRoom.peerAddress
        } else {
            val firstParticipant = chatRoom.participants.firstOrNull()
            if (isGroup) {
                Log.d(
                    "$TAG Group conversation [$id] has [${chatRoom.nbParticipants}] participant(s)"
                )
                for (participant in chatRoom.participants) {
                    val friend = coreContext.contactsManager.findContactByAddress(
                        participant.address
                    )
                    if (friend != null && !friends.contains(friend)) {
                        friends.add(friend)
                    }
                }
            } else {
                Log.d(
                    "$TAG Conversation [$id] is with participant [${firstParticipant?.address?.asStringUriOnly()}]"
                )
            }
            firstParticipant?.address ?: chatRoom.peerAddress
        }

        if (isGroup) {
            val fakeFriend = coreContext.core.createFriend()
            fakeFriend.name = chatRoom.subject
            val model = ContactAvatarModel(fakeFriend)
            model.defaultToConversationIcon.postValue(true)
            model.updateSecurityLevelUsingConversation(chatRoom)
            avatarModel.postValue(model)
        } else {
            avatarModel.postValue(
                coreContext.contactsManager.getContactAvatarModelForAddress(address)
            )
        }
    }

    @WorkerThread
    private fun computeComposingLabel() {
        val composing = chatRoom.isRemoteComposing
        isComposing.postValue(composing)
        if (!composing) {
            composingLabel.postValue("")
            return
        }

        val composingFriends = arrayListOf<String>()
        var label = ""
        for (address in chatRoom.composingAddresses) {
            val avatar = coreContext.contactsManager.getContactAvatarModelForAddress(address)
            val name = avatar.name.value ?: LinphoneUtils.getDisplayName(address)
            composingFriends.add(name)
            label += "$name, "
        }
        if (composingFriends.isNotEmpty()) {
            label = label.dropLast(2)

            val format = AppUtils.getStringWithPlural(
                R.plurals.conversation_composing_label,
                composingFriends.size,
                label
            )
            composingLabel.postValue(format)
        } else {
            composingLabel.postValue("")
        }
    }
}
