package org.linphone.ui.main.history.viewmodel

import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.R
import org.linphone.core.Address
import org.linphone.core.Call
import org.linphone.core.ChatRoom
import org.linphone.core.ChatRoomListenerStub
import org.linphone.core.ChatRoomParams
import org.linphone.core.tools.Log
import org.linphone.ui.main.history.model.CallLogHistoryModel
import org.linphone.ui.main.history.model.CallLogModel
import org.linphone.ui.main.model.isInSecureMode
import org.linphone.utils.AppUtils
import org.linphone.utils.Event
import org.linphone.utils.LinphoneUtils

class ContactHistoryViewModel @UiThread constructor() : ViewModel() {
    companion object {
        private const val TAG = "[Contact History ViewModel]"
    }

    val showBackButton = MutableLiveData<Boolean>()

    val callLogModel = MutableLiveData<CallLogModel>()

    val historyCallLogs = MutableLiveData<ArrayList<CallLogHistoryModel>>()

    val chatDisabled = MutableLiveData<Boolean>()

    val videoCallDisabled = MutableLiveData<Boolean>()

    val operationInProgress = MutableLiveData<Boolean>()

    val chatRoomCreationErrorEvent: MutableLiveData<Event<String>> by lazy {
        MutableLiveData<Event<String>>()
    }

    val goToConversationEvent: MutableLiveData<Event<Pair<String, String>>> by lazy {
        MutableLiveData<Event<Pair<String, String>>>()
    }

    val historyDeletedEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    private lateinit var address: Address

    private val chatRoomListener = object : ChatRoomListenerStub() {
        @WorkerThread
        override fun onStateChanged(chatRoom: ChatRoom, newState: ChatRoom.State?) {
            val state = chatRoom.state
            val id = LinphoneUtils.getChatRoomId(chatRoom)
            Log.i("$TAG Chat room [$id] (${chatRoom.subject}) state changed: [$state]")

            if (state == ChatRoom.State.Created) {
                Log.i("$TAG Chat room [$id] successfully created")
                chatRoom.removeListener(this)
                operationInProgress.postValue(false)
                goToConversationEvent.postValue(
                    Event(
                        Pair(
                            chatRoom.localAddress.asStringUriOnly(),
                            chatRoom.peerAddress.asStringUriOnly()
                        )
                    )
                )
            } else if (state == ChatRoom.State.CreationFailed) {
                Log.e("$TAG Chat room [$id] creation has failed!")
                chatRoom.removeListener(this)
                operationInProgress.postValue(false)
                chatRoomCreationErrorEvent.postValue(Event("Error!")) // TODO FIXME: use translated string
            }
        }
    }

    init {
        coreContext.postOnCoreThread { core ->
            chatDisabled.postValue(corePreferences.disableChat)
            videoCallDisabled.postValue(!core.isVideoEnabled)
        }
    }

    @UiThread
    fun findCallLogByCallId(callId: String) {
        coreContext.postOnCoreThread { core ->
            val callLog = core.findCallLogFromCallId(callId)
            if (callLog != null) {
                val model = CallLogModel(callLog)
                address = model.address

                // Check if it is a conference
                val conferenceInfo = coreContext.core.findConferenceInformationFromUri(address)
                if (conferenceInfo != null) {
                    model.avatarModel.name.postValue(conferenceInfo.subject)
                    model.avatarModel.forceConferenceIcon.postValue(true)
                }

                callLogModel.postValue(model)

                val peerAddress = if (callLog.dir == Call.Dir.Outgoing) callLog.toAddress else callLog.fromAddress
                val history = arrayListOf<CallLogHistoryModel>()
                val account = LinphoneUtils.getDefaultAccount()
                val list = if (account == null) {
                    val localAddress = if (callLog.dir == Call.Dir.Outgoing) callLog.fromAddress else callLog.toAddress
                    core.getCallHistory(peerAddress, localAddress)
                } else {
                    account.getCallLogsForAddress(peerAddress)
                }
                for (log in list) {
                    val historyModel = CallLogHistoryModel(log)
                    history.add(historyModel)
                }
                historyCallLogs.postValue(history)
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
            val params = core.createCallParams(null)
            params?.isVideoEnabled = false
            coreContext.startCall(address, params)
        }
    }

    @UiThread
    fun startVideoCall() {
        coreContext.postOnCoreThread { core ->
            val params = core.createCallParams(null)
            params?.isVideoEnabled = true
            coreContext.startCall(address, params)
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
                    "$TAG Looking for existing chat room between [$localSipUri] and [$remoteSipUri]"
                )

                val params: ChatRoomParams = coreContext.core.createDefaultChatRoomParams()
                params.isGroupEnabled = false
                params.subject = AppUtils.getString(R.string.conversation_one_to_one_hidden_subject)
                params.ephemeralLifetime = 0 // Make sure ephemeral is disabled by default

                val sameDomain = remote.domain == corePreferences.defaultDomain && remote.domain == account.params.domain
                if (account.isInSecureMode() && sameDomain) {
                    Log.i(
                        "$TAG Account is in secure mode & domain matches, creating a E2E chat room"
                    )
                    params.backend = ChatRoom.Backend.FlexisipChat
                    params.isEncryptionEnabled = true
                } else if (!account.isInSecureMode()) {
                    if (LinphoneUtils.isEndToEndEncryptedChatAvailable(core)) {
                        Log.i(
                            "$TAG Account is in interop mode but LIME is available, creating a E2E chat room"
                        )
                        params.backend = ChatRoom.Backend.FlexisipChat
                        params.isEncryptionEnabled = true
                    } else {
                        Log.i(
                            "$TAG Account is in interop mode but LIME isn't available, creating a SIP simple chat room"
                        )
                        params.backend = ChatRoom.Backend.Basic
                        params.isEncryptionEnabled = false
                    }
                } else {
                    Log.e(
                        "$TAG Account is in secure mode, can't chat with SIP address of different domain [${remote.asStringUriOnly()}]"
                    )
                    return@postOnCoreThread
                }

                val participants = arrayOf(remote)
                val localAddress = account.params.identityAddress
                val existingChatRoom = core.searchChatRoom(params, localAddress, null, participants)
                if (existingChatRoom != null) {
                    Log.i(
                        "$TAG Found existing chat room [${LinphoneUtils.getChatRoomId(
                            existingChatRoom
                        )}], going to it"
                    )
                    goToConversationEvent.postValue(
                        Event(Pair(localSipUri, existingChatRoom.peerAddress.asStringUriOnly()))
                    )
                } else {
                    Log.i(
                        "$TAG No existing chat room between [$localSipUri] and [$remoteSipUri] was found, let's create it"
                    )
                    operationInProgress.postValue(true)
                    val chatRoom = core.createChatRoom(params, localAddress, participants)
                    if (chatRoom != null) {
                        if (params.backend == ChatRoom.Backend.FlexisipChat) {
                            if (chatRoom.state == ChatRoom.State.Created) {
                                val id = LinphoneUtils.getChatRoomId(chatRoom)
                                Log.i("$TAG 1-1 chat room [$id] has been created")
                                operationInProgress.postValue(false)
                                goToConversationEvent.postValue(
                                    Event(
                                        Pair(
                                            chatRoom.localAddress.asStringUriOnly(),
                                            chatRoom.peerAddress.asStringUriOnly()
                                        )
                                    )
                                )
                            } else {
                                Log.i("$TAG Chat room isn't in Created state yet, wait for it")
                                chatRoom.addListener(chatRoomListener)
                            }
                        } else {
                            val id = LinphoneUtils.getChatRoomId(chatRoom)
                            Log.i("$TAG Chat room successfully created [$id]")
                            operationInProgress.postValue(false)
                            goToConversationEvent.postValue(
                                Event(
                                    Pair(
                                        chatRoom.localAddress.asStringUriOnly(),
                                        chatRoom.peerAddress.asStringUriOnly()
                                    )
                                )
                            )
                        }
                    } else {
                        Log.e(
                            "$TAG Failed to create 1-1 chat room with [${remote.asStringUriOnly()}]!"
                        )
                        operationInProgress.postValue(false)
                        chatRoomCreationErrorEvent.postValue(Event("Error!")) // TODO FIXME: use translated string
                    }
                }
            }
        }
    }
}
