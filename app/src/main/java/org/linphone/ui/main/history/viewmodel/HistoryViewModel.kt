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
package org.linphone.ui.main.history.viewmodel

import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.lifecycle.MutableLiveData
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.R
import org.linphone.core.Address
import org.linphone.core.CallLog
import org.linphone.core.ChatRoom
import org.linphone.core.ChatRoomListenerStub
import org.linphone.core.Conference
import org.linphone.core.Core
import org.linphone.core.CoreListenerStub
import org.linphone.core.tools.Log
import org.linphone.ui.GenericViewModel
import org.linphone.ui.main.history.model.CallLogHistoryModel
import org.linphone.ui.main.history.model.CallLogModel
import org.linphone.utils.AppUtils
import org.linphone.utils.Event
import org.linphone.utils.LinphoneUtils

class HistoryViewModel
    @UiThread
    constructor() : GenericViewModel() {
    companion object {
        private const val TAG = "[History ViewModel]"
    }

    val showBackButton = MutableLiveData<Boolean>()

    val callLogModel = MutableLiveData<CallLogModel>()

    val historyCallLogs = MutableLiveData<ArrayList<CallLogHistoryModel>>()

    val chatDisabled = MutableLiveData<Boolean>()

    val videoCallDisabled = MutableLiveData<Boolean>()

    val operationInProgress = MutableLiveData<Boolean>()

    val isConferenceCallLog = MutableLiveData<Boolean>()

    val isChatRoomAvailable = MutableLiveData<Boolean>()

    val hideSipAddresses = MutableLiveData<Boolean>()

    val callLogFoundEvent = MutableLiveData<Event<Boolean>>()

    val chatRoomCreationErrorEvent: MutableLiveData<Event<Int>> by lazy {
        MutableLiveData<Event<Int>>()
    }

    val goToMeetingConversationEvent: MutableLiveData<Event<String>> by lazy {
        MutableLiveData<Event<String>>()
    }

    val goToConversationEvent: MutableLiveData<Event<String>> by lazy {
        MutableLiveData<Event<String>>()
    }

    val conferenceToJoinEvent: MutableLiveData<Event<String>> by lazy {
        MutableLiveData<Event<String>>()
    }

    val historyDeletedEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    private lateinit var address: Address

    private var meetingChatRoom: ChatRoom? = null

    private val coreListener = object : CoreListenerStub() {
        @WorkerThread
        override fun onCallLogUpdated(core: Core, callLog: CallLog) {
            val peerAddress = callLog.remoteAddress
            val localAddress = callLog.localAddress
            val defaultAccount = LinphoneUtils.getDefaultAccount()
            if (::address.isInitialized && address.weakEqual(peerAddress) && defaultAccount?.params?.identityAddress?.weakEqual(
                    localAddress
                ) == true
            ) {
                Log.i("$TAG A call log was created, updating list")
                computeCallLogs()
            }
        }
    }

    private val chatRoomListener = object : ChatRoomListenerStub() {
        @WorkerThread
        override fun onStateChanged(chatRoom: ChatRoom, newState: ChatRoom.State?) {
            val state = chatRoom.state
            if (state == ChatRoom.State.Instantiated) return

            val id = LinphoneUtils.getConversationId(chatRoom)
            Log.i("$TAG Conversation [$id] (${chatRoom.subject}) state changed: [$state]")

            if (state == ChatRoom.State.Created) {
                Log.i("$TAG Conversation [$id] successfully created")
                chatRoom.removeListener(this)
                operationInProgress.postValue(false)
                goToConversationEvent.postValue(Event(LinphoneUtils.getConversationId(chatRoom)))
            } else if (state == ChatRoom.State.CreationFailed) {
                Log.e("$TAG Conversation [$id] creation has failed!")
                chatRoom.removeListener(this)
                operationInProgress.postValue(false)
                chatRoomCreationErrorEvent.postValue(
                    Event(R.string.conversation_failed_to_create_toast)
                )
            }
        }
    }

    init {
        coreContext.postOnCoreThread { core ->
            core.addListener(coreListener)
            chatDisabled.postValue(corePreferences.disableChat)
            videoCallDisabled.postValue(!core.isVideoEnabled)
            hideSipAddresses.postValue(corePreferences.hideSipAddresses)
        }
    }

    @UiThread
    override fun onCleared() {
        super.onCleared()

        coreContext.postOnCoreThread { core ->
            core.removeListener(coreListener)
        }
    }

    @UiThread
    fun findCallLogByCallId(callId: String) {
        coreContext.postOnCoreThread { core ->
            val callLog = core.findCallLogFromCallId(callId)
            if (callLog != null) {
                address = callLog.remoteAddress

                val model = CallLogModel(callLog)
                callLogModel.postValue(model)

                val conference = callLog.wasConference()
                isConferenceCallLog.postValue(conference)
                meetingChatRoom = callLog.chatRoom
                isChatRoomAvailable.postValue(meetingChatRoom != null)
                if (conference) {
                    Log.i(
                        "$TAG Conference call log, chat room is ${ if (meetingChatRoom != null) "available" else "not available"}"
                    )
                }

                computeCallLogs()

                callLogFoundEvent.postValue(Event(true))
            } else {
                callLogFoundEvent.postValue(Event(false))
            }
        }
    }

    @UiThread
    fun deleteHistory() {
        coreContext.postOnCoreThread { core ->
            for (model in historyCallLogs.value.orEmpty()) {
                core.removeCallLog(model.callLog)
            }
            historyDeletedEvent.postValue(Event(true))
        }
    }

    @UiThread
    fun startAudioCall() {
        coreContext.postOnCoreThread { core ->
            coreContext.startAudioCall(address)
        }
    }

    @UiThread
    fun startVideoCall() {
        coreContext.postOnCoreThread { core ->
            coreContext.startVideoCall(address)
        }
    }

    @UiThread
    fun goToMeetingConversation() {
        coreContext.postOnCoreThread {
            val chatRoom = meetingChatRoom
            if (chatRoom != null) {
                goToMeetingConversationEvent.postValue(Event(LinphoneUtils.getConversationId(chatRoom)))
            } else {
                Log.e("$TAG Failed to find chat room for current call log!")
            }
        }
    }

    @UiThread
    fun goToConversation() {
        coreContext.postOnCoreThread { core ->
            val account = core.defaultAccount
            val localSipUri = account?.params?.identityAddress?.asStringUriOnly()
            if (!localSipUri.isNullOrEmpty()) {
                val remote = address
                val remoteSipUri = remote.asStringUriOnly()
                Log.i(
                    "$TAG Looking for existing conversation between [$localSipUri] and [$remoteSipUri]"
                )

                val params = coreContext.core.createConferenceParams(null)
                params.isChatEnabled = true
                params.isGroupEnabled = false
                params.subject = AppUtils.getString(R.string.conversation_one_to_one_hidden_subject)
                params.account = account

                val chatParams = params.chatParams ?: return@postOnCoreThread
                chatParams.ephemeralLifetime = 0 // Make sure ephemeral is disabled by default

                val sameDomain = remote.domain == corePreferences.defaultDomain && remote.domain == account.params.domain
                if (account.params.instantMessagingEncryptionMandatory && sameDomain) {
                    Log.i(
                        "$TAG Account is in secure mode & domain matches, creating an E2E encrypted conversation"
                    )
                    chatParams.backend = ChatRoom.Backend.FlexisipChat
                    params.securityLevel = Conference.SecurityLevel.EndToEnd
                } else if (!account.params.instantMessagingEncryptionMandatory) {
                    if (LinphoneUtils.isEndToEndEncryptedChatAvailable(core)) {
                        Log.i(
                            "$TAG Account is in interop mode but LIME is available, creating an E2E encrypted conversation"
                        )
                        chatParams.backend = ChatRoom.Backend.FlexisipChat
                        params.securityLevel = Conference.SecurityLevel.EndToEnd
                    } else {
                        Log.i(
                            "$TAG Account is in interop mode but LIME isn't available, creating a SIP simple conversation"
                        )
                        chatParams.backend = ChatRoom.Backend.Basic
                        params.securityLevel = Conference.SecurityLevel.None
                    }
                } else {
                    Log.e(
                        "$TAG Account is in secure mode, can't chat with SIP address of different domain [${remote.asStringUriOnly()}]"
                    )
                    // TODO: show error
                    return@postOnCoreThread
                }

                val participants = arrayOf(remote)
                val localAddress = account.params.identityAddress
                val existingChatRoom = core.searchChatRoom(params, localAddress, null, participants)
                if (existingChatRoom != null) {
                    Log.i(
                        "$TAG Found existing conversation [${LinphoneUtils.getConversationId(
                            existingChatRoom
                        )}], going to it"
                    )
                    goToConversationEvent.postValue(Event(LinphoneUtils.getConversationId(existingChatRoom)))
                } else {
                    Log.i(
                        "$TAG No existing conversation between [$localSipUri] and [$remoteSipUri] was found, let's create it"
                    )
                    operationInProgress.postValue(true)
                    val chatRoom = core.createChatRoom(params, participants)
                    if (chatRoom != null) {
                        if (chatParams.backend == ChatRoom.Backend.FlexisipChat) {
                            if (chatRoom.state == ChatRoom.State.Created) {
                                val id = LinphoneUtils.getConversationId(chatRoom)
                                Log.i("$TAG 1-1 conversation [$id] has been created")
                                operationInProgress.postValue(false)
                                goToConversationEvent.postValue(Event(LinphoneUtils.getConversationId(chatRoom)))
                            } else {
                                Log.i("$TAG Conversation isn't in Created state yet, wait for it")
                                chatRoom.addListener(chatRoomListener)
                            }
                        } else {
                            val id = LinphoneUtils.getConversationId(chatRoom)
                            Log.i("$TAG Conversation successfully created [$id]")
                            operationInProgress.postValue(false)
                            goToConversationEvent.postValue(Event(LinphoneUtils.getConversationId(chatRoom)))
                        }
                    } else {
                        Log.e(
                            "$TAG Failed to create 1-1 conversation with [${remote.asStringUriOnly()}]!"
                        )
                        operationInProgress.postValue(false)
                        chatRoomCreationErrorEvent.postValue(
                            Event(R.string.conversation_failed_to_create_toast)
                        )
                    }
                }
            }
        }
    }

    @UiThread
    fun goToMeetingWaitingRoom() {
        coreContext.postOnCoreThread {
            if (::address.isInitialized) {
                conferenceToJoinEvent.postValue(Event(address.asStringUriOnly()))
            }
        }
    }

    @WorkerThread
    private fun computeCallLogs() {
        val history = arrayListOf<CallLogHistoryModel>()
        val account = LinphoneUtils.getDefaultAccount()
        account ?: return

        val list = account.getCallLogsForAddress(address)
        for (log in list) {
            val historyModel = CallLogHistoryModel(log)
            history.add(historyModel)
        }

        // Required when "unique account displays all call logs from Core" workaround
        // TODO FIXME: remove workaround later
        if (list.isEmpty()) {
            for (log in coreContext.core.callLogs) {
                if (log.remoteAddress.weakEqual(address)) {
                    val historyModel = CallLogHistoryModel(log)
                    history.add(historyModel)
                }
            }
        }

        Log.i("$TAG Found [${history.size}] call logs")
        historyCallLogs.postValue(history)
    }
}
