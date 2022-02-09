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
package org.linphone.activities.main.chat.viewmodels

import android.animation.ValueAnimator
import android.view.animation.LinearInterpolator
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.R
import org.linphone.contact.Contact
import org.linphone.contact.ContactDataInterface
import org.linphone.contact.ContactsUpdatedListenerStub
import org.linphone.core.*
import org.linphone.core.tools.Log
import org.linphone.utils.AppUtils
import org.linphone.utils.LinphoneUtils
import org.linphone.utils.TimestampUtils

class ChatRoomViewModelFactory(private val chatRoom: ChatRoom) :
    ViewModelProvider.NewInstanceFactory() {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return ChatRoomViewModel(chatRoom) as T
    }
}

class ChatRoomViewModel(val chatRoom: ChatRoom) : ViewModel(), ContactDataInterface {
    override val contact: MutableLiveData<Contact> = MutableLiveData<Contact>()
    override val displayName: MutableLiveData<String> = MutableLiveData<String>()
    override val securityLevel: MutableLiveData<ChatRoomSecurityLevel> = MutableLiveData<ChatRoomSecurityLevel>()
    override val showGroupChatAvatar: Boolean = chatRoom.hasCapability(ChatRoomCapabilities.Conference.toInt()) &&
        !chatRoom.hasCapability(ChatRoomCapabilities.OneToOne.toInt())

    val subject = MutableLiveData<String>()

    val participants = MutableLiveData<String>()

    val unreadMessagesCount = MutableLiveData<Int>()

    val lastUpdate = MutableLiveData<String>()

    val lastMessageText = MutableLiveData<String>()

    val callInProgress = MutableLiveData<Boolean>()

    val remoteIsComposing = MutableLiveData<Boolean>()

    val composingList = MutableLiveData<String>()

    val securityLevelIcon = MutableLiveData<Int>()

    val securityLevelContentDescription = MutableLiveData<Int>()

    val peerSipUri = MutableLiveData<String>()

    val ephemeralEnabled = MutableLiveData<Boolean>()

    val oneToOneChatRoom: Boolean = chatRoom.hasCapability(ChatRoomCapabilities.OneToOne.toInt())

    val encryptedChatRoom: Boolean = chatRoom.hasCapability(ChatRoomCapabilities.Encrypted.toInt())

    val ephemeralChatRoom: Boolean = chatRoom.hasCapability(ChatRoomCapabilities.Ephemeral.toInt())

    val meAdmin: MutableLiveData<Boolean> by lazy {
        MutableLiveData<Boolean>()
    }

    val isUserScrollingUp = MutableLiveData<Boolean>()

    var oneParticipantOneDevice: Boolean = false

    var onlyParticipantOnlyDeviceAddress: Address? = null

    val chatUnreadCountTranslateY = MutableLiveData<Float>()

    private var addressToCall: Address? = null

    private val bounceAnimator: ValueAnimator by lazy {
        ValueAnimator.ofFloat(AppUtils.getDimension(R.dimen.tabs_fragment_unread_count_bounce_offset), 0f).apply {
            addUpdateListener {
                val value = it.animatedValue as Float
                chatUnreadCountTranslateY.value = value
            }
            interpolator = LinearInterpolator()
            duration = 250
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
        }
    }

    private val contactsUpdatedListener = object : ContactsUpdatedListenerStub() {
        override fun onContactsUpdated() {
            Log.i("[Chat Room] Contacts have changed")
            contactLookup()
            updateLastMessageToDisplay()
        }
    }

    private val coreListener: CoreListenerStub = object : CoreListenerStub() {
        override fun onCallStateChanged(
            core: Core,
            call: Call,
            state: Call.State,
            message: String
        ) {
            callInProgress.value = core.callsNb > 0
        }

        override fun onChatRoomRead(core: Core, room: ChatRoom) {
            if (room == chatRoom) {
                unreadMessagesCount.value = 0
            }
        }
    }

    private val chatRoomListener: ChatRoomListenerStub = object : ChatRoomListenerStub() {
        override fun onStateChanged(chatRoom: ChatRoom, state: ChatRoom.State) {
            Log.i("[Chat Room] $chatRoom state changed: $state")
            if (state == ChatRoom.State.Created) {
                contactLookup()
                updateSecurityIcon()
                subject.value = chatRoom.subject
            }
        }

        override fun onSubjectChanged(chatRoom: ChatRoom, eventLog: EventLog) {
            subject.value = chatRoom.subject
        }

        override fun onChatMessageReceived(chatRoom: ChatRoom, eventLog: EventLog) {
            unreadMessagesCount.value = chatRoom.unreadMessagesCount
            lastMessageText.value = formatLastMessage(eventLog.chatMessage)
        }

        override fun onChatMessageSent(chatRoom: ChatRoom, eventLog: EventLog) {
            lastMessageText.value = formatLastMessage(eventLog.chatMessage)
        }

        override fun onParticipantAdded(chatRoom: ChatRoom, eventLog: EventLog) {
            contactLookup()
            updateSecurityIcon()
            updateParticipants()
        }

        override fun onParticipantRemoved(chatRoom: ChatRoom, eventLog: EventLog) {
            contactLookup()
            updateSecurityIcon()
            updateParticipants()
        }

        override fun onIsComposingReceived(
            chatRoom: ChatRoom,
            remoteAddr: Address,
            isComposing: Boolean
        ) {
            updateRemotesComposing()
        }

        override fun onConferenceJoined(chatRoom: ChatRoom, eventLog: EventLog) {
            contactLookup()
            updateSecurityIcon()
            subject.value = chatRoom.subject
        }

        override fun onSecurityEvent(chatRoom: ChatRoom, eventLog: EventLog) {
            updateSecurityIcon()
        }

        override fun onParticipantDeviceAdded(chatRoom: ChatRoom, eventLog: EventLog) {
            updateSecurityIcon()
            updateParticipants()
        }

        override fun onParticipantDeviceRemoved(chatRoom: ChatRoom, eventLog: EventLog) {
            updateSecurityIcon()
            updateParticipants()
        }

        override fun onEphemeralMessageDeleted(chatRoom: ChatRoom, eventLog: EventLog) {
            Log.i("[Chat Room] Ephemeral message deleted, updated last message displayed")
            updateLastMessageToDisplay()
        }

        override fun onEphemeralEvent(chatRoom: ChatRoom, eventLog: EventLog) {
            ephemeralEnabled.value = chatRoom.isEphemeralEnabled
        }

        override fun onParticipantAdminStatusChanged(chatRoom: ChatRoom, eventLog: EventLog) {
            meAdmin.value = chatRoom.me?.isAdmin ?: false
        }
    }

    init {
        chatRoom.core.addListener(coreListener)
        chatRoom.addListener(chatRoomListener)
        coreContext.contactsManager.addListener(contactsUpdatedListener)

        unreadMessagesCount.value = chatRoom.unreadMessagesCount
        lastUpdate.value = TimestampUtils.toString(chatRoom.lastUpdateTime, true)

        subject.value = chatRoom.subject
        updateSecurityIcon()
        meAdmin.value = chatRoom.me?.isAdmin ?: false
        ephemeralEnabled.value = chatRoom.isEphemeralEnabled

        contactLookup()
        updateParticipants()
        updateLastMessageToDisplay()

        callInProgress.value = chatRoom.core.callsNb > 0
        updateRemotesComposing()

        if (corePreferences.enableAnimations) bounceAnimator.start()
    }

    override fun onCleared() {
        destroy()
        super.onCleared()
    }

    fun destroy() {
        coreContext.contactsManager.removeListener(contactsUpdatedListener)
        chatRoom.removeListener(chatRoomListener)
        chatRoom.core.removeListener(coreListener)
        if (corePreferences.enableAnimations) bounceAnimator.end()
    }

    fun hideMenu(): Boolean {
        return chatRoom.hasCapability(ChatRoomCapabilities.Basic.toInt()) || (oneToOneChatRoom && !encryptedChatRoom)
    }

    fun contactLookup() {
        displayName.value = when {
            chatRoom.hasCapability(ChatRoomCapabilities.Basic.toInt()) -> LinphoneUtils.getDisplayName(
                chatRoom.peerAddress
            )
            chatRoom.hasCapability(ChatRoomCapabilities.OneToOne.toInt()) -> LinphoneUtils.getDisplayName(
                chatRoom.participants.firstOrNull()?.address ?: chatRoom.peerAddress
            )
            chatRoom.hasCapability(ChatRoomCapabilities.Conference.toInt()) -> chatRoom.subject.orEmpty()
            else -> chatRoom.peerAddress.asStringUriOnly()
        }

        if (chatRoom.hasCapability(ChatRoomCapabilities.OneToOne.toInt())) {
            searchMatchingContact()
        } else {
            getParticipantsNames()
        }
    }

    fun startCall() {
        val address = addressToCall
        if (address != null) {
            coreContext.startCall(address)
        }
    }

    fun updateLastMessageToDisplay() {
        lastMessageText.value = formatLastMessage(chatRoom.lastMessageInHistory)
    }

    private fun formatLastMessage(msg: ChatMessage?): String {
        if (msg == null) return ""

        val sender: String =
            coreContext.contactsManager.findContactByAddress(msg.fromAddress)?.fullName
                ?: LinphoneUtils.getDisplayName(msg.fromAddress)
        var body = ""
        for (content in msg.contents) {
            if (content.isFile || content.isFileTransfer) body += content.name + " "
            else if (content.isText) body += content.utf8Text + " "
        }

        return "$sender: $body"
    }

    private fun searchMatchingContact() {
        val remoteAddress = if (chatRoom.hasCapability(ChatRoomCapabilities.Basic.toInt())) {
            chatRoom.peerAddress
        } else {
            if (chatRoom.participants.isNotEmpty()) {
                chatRoom.participants[0].address
            } else {
                Log.e("[Chat Room] $chatRoom doesn't have any participant in state ${chatRoom.state}!")
                return
            }
        }
        contact.value = coreContext.contactsManager.findContactByAddress(remoteAddress)
    }

    private fun getParticipantsNames() {
        if (oneToOneChatRoom) return

        var participantsList = ""
        var index = 0
        for (participant in chatRoom.participants) {
            val contact: Contact? =
                coreContext.contactsManager.findContactByAddress(participant.address)
            participantsList += contact?.fullName ?: LinphoneUtils.getDisplayName(participant.address)
            index++
            if (index != chatRoom.nbParticipants) participantsList += ", "
        }
        participants.value = participantsList
    }

    private fun updateSecurityIcon() {
        securityLevel.value = chatRoom.securityLevel

        securityLevelIcon.value = when (chatRoom.securityLevel) {
            ChatRoomSecurityLevel.Safe -> R.drawable.security_2_indicator
            ChatRoomSecurityLevel.Encrypted -> R.drawable.security_1_indicator
            else -> R.drawable.security_alert_indicator
        }
        securityLevelContentDescription.value = when (chatRoom.securityLevel) {
            ChatRoomSecurityLevel.Safe -> R.string.content_description_security_level_safe
            ChatRoomSecurityLevel.Encrypted -> R.string.content_description_security_level_encrypted
            else -> R.string.content_description_security_level_unsafe
        }
    }

    private fun updateRemotesComposing() {
        remoteIsComposing.value = chatRoom.isRemoteComposing

        var composing = ""
        for (address in chatRoom.composingAddresses) {
            val contact: Contact? = coreContext.contactsManager.findContactByAddress(address)
            composing += if (composing.isNotEmpty()) ", " else ""
            composing += contact?.fullName ?: LinphoneUtils.getDisplayName(address)
        }
        composingList.value = AppUtils.getStringWithPlural(R.plurals.chat_room_remote_composing, chatRoom.composingAddresses.size, composing)
    }

    private fun updateParticipants() {
        peerSipUri.value = if (oneToOneChatRoom && !chatRoom.hasCapability(ChatRoomCapabilities.Basic.toInt()))
            chatRoom.participants.firstOrNull()?.address?.asStringUriOnly()
                ?: chatRoom.peerAddress.asStringUriOnly()
        else chatRoom.peerAddress.asStringUriOnly()

        oneParticipantOneDevice = chatRoom.hasCapability(ChatRoomCapabilities.OneToOne.toInt()) &&
            chatRoom.me?.devices?.size == 1 &&
            chatRoom.participants.firstOrNull()?.devices?.size == 1

        addressToCall = if (chatRoom.hasCapability(ChatRoomCapabilities.Basic.toInt()))
            chatRoom.peerAddress
        else
            chatRoom.participants.firstOrNull()?.address

        onlyParticipantOnlyDeviceAddress = chatRoom.participants.firstOrNull()?.devices?.firstOrNull()?.address
    }
}
