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
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.R
import org.linphone.contact.ContactDataInterface
import org.linphone.contact.ContactsUpdatedListenerStub
import org.linphone.core.*
import org.linphone.core.tools.Log
import org.linphone.utils.AppUtils
import org.linphone.utils.LinphoneUtils

class ChatRoomViewModelFactory(private val chatRoom: ChatRoom) :
    ViewModelProvider.NewInstanceFactory() {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return ChatRoomViewModel(chatRoom) as T
    }
}

class ChatRoomViewModel(val chatRoom: ChatRoom) : ViewModel(), ContactDataInterface {
    override val contact: MutableLiveData<Friend> = MutableLiveData<Friend>()
    override val displayName: MutableLiveData<String> = MutableLiveData<String>()
    override val securityLevel: MutableLiveData<ChatRoomSecurityLevel> = MutableLiveData<ChatRoomSecurityLevel>()
    override val showGroupChatAvatar: Boolean
        get() = conferenceChatRoom && !oneToOneChatRoom
    override val coroutineScope: CoroutineScope = viewModelScope

    val subject = MutableLiveData<String>()

    val participants = MutableLiveData<String>()

    val unreadMessagesCount = MutableLiveData<Int>()

    val remoteIsComposing = MutableLiveData<Boolean>()

    val composingList = MutableLiveData<String>()

    val securityLevelIcon = MutableLiveData<Int>()

    val securityLevelContentDescription = MutableLiveData<Int>()

    val peerSipUri = MutableLiveData<String>()

    val ephemeralEnabled = MutableLiveData<Boolean>()

    val basicChatRoom: Boolean by lazy {
        chatRoom.hasCapability(ChatRoomCapabilities.Basic.toInt())
    }

    val oneToOneChatRoom: Boolean by lazy {
        chatRoom.hasCapability(ChatRoomCapabilities.OneToOne.toInt())
    }

    private val conferenceChatRoom: Boolean by lazy {
        chatRoom.hasCapability(ChatRoomCapabilities.Conference.toInt())
    }

    val encryptedChatRoom: Boolean by lazy {
        chatRoom.hasCapability(ChatRoomCapabilities.Encrypted.toInt())
    }

    val ephemeralChatRoom: Boolean by lazy {
        chatRoom.hasCapability(ChatRoomCapabilities.Ephemeral.toInt())
    }

    val meAdmin: MutableLiveData<Boolean> by lazy {
        MutableLiveData<Boolean>()
    }

    val isUserScrollingUp = MutableLiveData<Boolean>()

    var oneParticipantOneDevice: Boolean = false

    var onlyParticipantOnlyDeviceAddress: Address? = null

    val chatUnreadCountTranslateY = MutableLiveData<Float>()

    val groupCallAvailable: Boolean
        get() = LinphoneUtils.isRemoteConferencingAvailable()

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
            Log.d("[Chat Room] Contacts have changed")
            contactLookup()
        }
    }

    private val coreListener: CoreListenerStub = object : CoreListenerStub() {
        override fun onChatRoomRead(core: Core, room: ChatRoom) {
            if (room == chatRoom) {
                updateUnreadMessageCount()
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

        override fun onChatMessagesReceived(chatRoom: ChatRoom, eventLogs: Array<out EventLog>) {
            updateUnreadMessageCount()
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

        updateUnreadMessageCount()

        subject.value = chatRoom.subject
        updateSecurityIcon()
        meAdmin.value = chatRoom.me?.isAdmin ?: false
        ephemeralEnabled.value = chatRoom.isEphemeralEnabled

        contactLookup()
        updateParticipants()

        updateRemotesComposing()
    }

    override fun onCleared() {
        coreContext.contactsManager.removeListener(contactsUpdatedListener)
        chatRoom.removeListener(chatRoomListener)
        chatRoom.core.removeListener(coreListener)
        if (corePreferences.enableAnimations) bounceAnimator.end()
        super.onCleared()
    }

    fun contactLookup() {
        displayName.value = when {
            basicChatRoom -> LinphoneUtils.getDisplayName(
                chatRoom.peerAddress
            )
            oneToOneChatRoom -> LinphoneUtils.getDisplayName(
                chatRoom.participants.firstOrNull()?.address ?: chatRoom.peerAddress
            )
            conferenceChatRoom -> chatRoom.subject.orEmpty()
            else -> chatRoom.peerAddress.asStringUriOnly()
        }

        if (oneToOneChatRoom) {
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

    fun startGroupCall() {
        val conferenceScheduler = coreContext.core.createConferenceScheduler()
        val conferenceInfo = Factory.instance().createConferenceInfo()

        val localAddress = chatRoom.localAddress.clone()
        localAddress.clean() // Remove GRUU
        val addresses = Array(chatRoom.participants.size) {
            index ->
            chatRoom.participants[index].address
        }
        val localAccount = coreContext.core.accountList.find {
            account ->
            account.params.identityAddress?.weakEqual(localAddress) ?: false
        }

        conferenceInfo.organizer = localAddress
        conferenceInfo.subject = subject.value
        conferenceInfo.setParticipants(addresses)
        conferenceScheduler.account = localAccount
        // Will trigger the conference creation/update automatically
        conferenceScheduler.info = conferenceInfo
    }

    fun areNotificationsMuted(): Boolean {
        val id = LinphoneUtils.getChatRoomId(chatRoom.localAddress, chatRoom.peerAddress)
        return corePreferences.chatRoomMuted(id)
    }

    fun muteNotifications(mute: Boolean) {
        val id = LinphoneUtils.getChatRoomId(chatRoom.localAddress, chatRoom.peerAddress)
        corePreferences.muteChatRoom(id, mute)
    }

    fun getRemoteAddress(): Address? {
        return if (basicChatRoom) {
            chatRoom.peerAddress
        } else {
            if (chatRoom.participants.isNotEmpty()) {
                chatRoom.participants[0].address
            } else {
                Log.e("[Chat Room] ${chatRoom.peerAddress} doesn't have any participant (state ${chatRoom.state})!")
                null
            }
        }
    }

    private fun searchMatchingContact() {
        val remoteAddress = getRemoteAddress()
        if (remoteAddress != null) {
            contact.value = coreContext.contactsManager.findContactByAddress(remoteAddress)
        }
    }

    private fun getParticipantsNames() {
        if (oneToOneChatRoom) return

        var participantsList = ""
        var index = 0
        for (participant in chatRoom.participants) {
            val contact = coreContext.contactsManager.findContactByAddress(participant.address)
            participantsList += contact?.name ?: LinphoneUtils.getDisplayName(participant.address)
            index++
            if (index != chatRoom.nbParticipants) participantsList += ", "
        }
        participants.value = participantsList
    }

    private fun updateSecurityIcon() {
        val level = chatRoom.securityLevel
        securityLevel.value = level

        securityLevelIcon.value = when (level) {
            ChatRoomSecurityLevel.Safe -> R.drawable.security_2_indicator
            ChatRoomSecurityLevel.Encrypted -> R.drawable.security_1_indicator
            else -> R.drawable.security_alert_indicator
        }
        securityLevelContentDescription.value = when (level) {
            ChatRoomSecurityLevel.Safe -> R.string.content_description_security_level_safe
            ChatRoomSecurityLevel.Encrypted -> R.string.content_description_security_level_encrypted
            else -> R.string.content_description_security_level_unsafe
        }
    }

    private fun updateRemotesComposing() {
        val isComposing = chatRoom.isRemoteComposing
        remoteIsComposing.value = isComposing
        if (!isComposing) return

        var composing = ""
        for (address in chatRoom.composingAddresses) {
            val contact = coreContext.contactsManager.findContactByAddress(address)
            composing += if (composing.isNotEmpty()) ", " else ""
            composing += contact?.name ?: LinphoneUtils.getDisplayName(address)
        }
        composingList.value = AppUtils.getStringWithPlural(R.plurals.chat_room_remote_composing, chatRoom.composingAddresses.size, composing)
    }

    private fun updateParticipants() {
        val participants = chatRoom.participants
        peerSipUri.value = if (oneToOneChatRoom && !basicChatRoom) {
            participants.firstOrNull()?.address?.asStringUriOnly()
                ?: chatRoom.peerAddress.asStringUriOnly()
        } else {
            chatRoom.peerAddress.asStringUriOnly()
        }

        oneParticipantOneDevice = oneToOneChatRoom &&
            chatRoom.me?.devices?.size == 1 &&
            participants.firstOrNull()?.devices?.size == 1

        addressToCall = if (basicChatRoom)
            chatRoom.peerAddress
        else
            participants.firstOrNull()?.address

        onlyParticipantOnlyDeviceAddress = participants.firstOrNull()?.devices?.firstOrNull()?.address
    }

    private fun updateUnreadMessageCount() {
        val count = chatRoom.unreadMessagesCount
        unreadMessagesCount.value = count
        if (count > 0 && corePreferences.enableAnimations) bounceAnimator.start()
        else if (count == 0 && bounceAnimator.isStarted) bounceAnimator.end()
    }
}
