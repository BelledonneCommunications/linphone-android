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
package org.linphone.ui.call.viewmodel

import android.Manifest
import android.app.KeyguardManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.annotation.AnyThread
import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.core.app.ActivityCompat
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.R
import org.linphone.contacts.ContactsManager.ContactsListener
import org.linphone.core.Address
import org.linphone.core.AudioDevice
import org.linphone.core.Call
import org.linphone.core.CallListenerStub
import org.linphone.core.CallStats
import org.linphone.core.ChatMessage
import org.linphone.core.ChatRoom
import org.linphone.core.ChatRoomListenerStub
import org.linphone.core.Conference
import org.linphone.core.ConferenceParams
import org.linphone.core.Core
import org.linphone.core.CoreListenerStub
import org.linphone.core.Friend
import org.linphone.core.MediaDirection
import org.linphone.core.MediaEncryption
import org.linphone.core.SecurityLevel
import org.linphone.core.tools.Log
import org.linphone.ui.GenericViewModel
import org.linphone.ui.call.conference.viewmodel.ConferenceViewModel
import org.linphone.ui.call.model.AudioDeviceModel
import org.linphone.ui.call.model.CallMediaEncryptionModel
import org.linphone.ui.call.model.CallStatsModel
import org.linphone.ui.main.contacts.model.ContactAvatarModel
import org.linphone.ui.main.history.model.NumpadModel
import org.linphone.utils.AppUtils
import org.linphone.utils.AudioUtils
import org.linphone.utils.Event
import org.linphone.utils.LinphoneUtils

class CurrentCallViewModel
    @UiThread
    constructor() : GenericViewModel() {
    companion object {
        private const val TAG = "[Current Call ViewModel]"
    }

    val contact = MutableLiveData<ContactAvatarModel>()

    val displayedName = MutableLiveData<String>()

    val displayedAddress = MutableLiveData<String>()

    val isVideoEnabled = MutableLiveData<Boolean>()

    val isSendingVideo = MutableLiveData<Boolean>()

    val isReceivingVideo = MutableLiveData<Boolean>()

    val showSwitchCamera = MutableLiveData<Boolean>()

    val videoUpdateInProgress = MutableLiveData<Boolean>()

    val isIncomingEarlyMedia = MutableLiveData<Boolean>()

    val isOutgoing = MutableLiveData<Boolean>()

    val isOutgoingRinging = MutableLiveData<Boolean>()

    val isOutgoingEarlyMedia = MutableLiveData<Boolean>()

    val isRecordingEnabled = MutableLiveData<Boolean>()

    val isRecording = MutableLiveData<Boolean>()

    val canBePaused = MutableLiveData<Boolean>()

    val isPaused = MutableLiveData<Boolean>()

    val isPausedByRemote = MutableLiveData<Boolean>()

    val isMicrophoneMuted = MutableLiveData<Boolean>()

    val isSpeakerEnabled = MutableLiveData<Boolean>()

    val isHeadsetEnabled = MutableLiveData<Boolean>()

    val isHearingAidEnabled = MutableLiveData<Boolean>()

    val isBluetoothEnabled = MutableLiveData<Boolean>()

    val fullScreenMode = MutableLiveData<Boolean>()

    val pipMode = MutableLiveData<Boolean>()

    val halfOpenedFolded = MutableLiveData<Boolean>()

    val isZrtp = MutableLiveData<Boolean>()

    val isZrtpSasValidationRequired = MutableLiveData<Boolean>()

    val waitingForEncryptionInfo = MutableLiveData<Boolean>()

    val isMediaEncrypted = MutableLiveData<Boolean>()

    val hideVideo = MutableLiveData<Boolean>()

    val callStatsModel = CallStatsModel()

    val callMediaEncryptionModel = CallMediaEncryptionModel {
        showZrtpSasDialogIfPossible()
    }

    val incomingCallTitle: MutableLiveData<String> by lazy {
        MutableLiveData<String>()
    }

    val qualityValue = MutableLiveData<Float>()

    val qualityIcon = MutableLiveData<Int>()

    var terminatedByUser = false

    val isRemoteRecordingEvent: MutableLiveData<Event<Pair<Boolean, String>>> by lazy {
        MutableLiveData<Event<Pair<Boolean, String>>>()
    }

    val goToEndedCallEvent: MutableLiveData<Event<String>> by lazy {
        MutableLiveData<Event<String>>()
    }

    val finishActivityEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val requestRecordAudioPermission: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val requestCameraPermission: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val proximitySensorEnabled = MutableLiveData<Boolean>()

    // To synchronize chronometers in UI
    val callDuration = MutableLiveData<Int>()

    val showAudioDevicesListEvent: MutableLiveData<Event<ArrayList<AudioDeviceModel>>> by lazy {
        MutableLiveData<Event<ArrayList<AudioDeviceModel>>>()
    }

    // ZRTP related

    val showZrtpSasDialogEvent: MutableLiveData<Event<Pair<String, List<String>>>> by lazy {
        MutableLiveData<Event<Pair<String, List<String>>>>()
    }

    val showZrtpSasCacheMismatchDialogEvent: MutableLiveData<Event<Pair<String, List<String>>>> by lazy {
        MutableLiveData<Event<Pair<String, List<String>>>>()
    }

    val zrtpAuthTokenVerifiedEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    var isZrtpDialogVisible: Boolean = false
    var isZrtpAlertDialogVisible: Boolean = false

    // Chat

    var currentCallConversation: ChatRoom? = null

    val unreadMessagesCount = MutableLiveData<Int>()

    val operationInProgress = MutableLiveData<Boolean>()

    val goToConversationEvent: MutableLiveData<Event<String>> by lazy {
        MutableLiveData<Event<String>>()
    }

    val chatRoomCreationErrorEvent: MutableLiveData<Event<Int>> by lazy {
        MutableLiveData<Event<Int>>()
    }

    // Conference

    val conferenceModel = ConferenceViewModel()

    val goToConferenceEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val goToCallEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    // Extras actions

    val toggleExtraActionsBottomSheetEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val showNumpadBottomSheetEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val transferInProgressEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val transferFailedEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val numpadModel: NumpadModel

    val appendDigitToSearchBarEvent: MutableLiveData<Event<String>> by lazy {
        MutableLiveData<Event<String>>()
    }

    val removedCharacterAtCurrentPositionEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    // Sliding answer/decline button

    val isScreenLocked = MutableLiveData<Boolean>()

    val slidingButtonAboveAnswer = MutableLiveData<Boolean>()

    val slidingButtonAboveDecline = MutableLiveData<Boolean>()

    val answerAlpha = MutableLiveData<Float>()

    val declineAlpha = MutableLiveData<Float>()

    lateinit var currentCall: Call

    private val contactsListener = object : ContactsListener {
        @WorkerThread
        override fun onContactsLoaded() {
        }

        @WorkerThread
        override fun onContactFoundInRemoteDirectory(friend: Friend) {
            val address = contact.value?.address
            address ?: return

            val addressMatch = friend.addresses.find {
                it.weakEqual(address)
            }
            if (addressMatch != null) {
                Log.i("$TAG Updating current call contact model")
                displayedName.postValue(friend.name)
                val model = ContactAvatarModel(friend, address)
                contact.postValue(model)
            }
        }
    }

    private val callListener = object : CallListenerStub() {
        @WorkerThread
        override fun onEncryptionChanged(call: Call, on: Boolean, authenticationToken: String?) {
            Log.i("$TAG Call encryption changed, updating...")
            updateEncryption()
        }

        @WorkerThread
        override fun onAuthenticationTokenVerified(call: Call, verified: Boolean) {
            Log.w(
                "$TAG Notified that authentication token is [${if (verified) "verified" else "not verified!"}]"
            )
            isZrtpSasValidationRequired.postValue(!verified)
            zrtpAuthTokenVerifiedEvent.postValue(Event(verified))
            if (verified) {
                isMediaEncrypted.postValue(true)
            }

            updateAvatarModelSecurityLevel(verified)
        }

        @WorkerThread
        override fun onRemoteRecording(call: Call, recording: Boolean) {
            Log.i("$TAG Remote recording changed: $recording")
            isRemoteRecordingEvent.postValue(Event(Pair(recording, displayedName.value.orEmpty())))
        }

        @WorkerThread
        override fun onStatsUpdated(call: Call, stats: CallStats) {
            callStatsModel.update(call, stats)
        }

        @WorkerThread
        override fun onStateChanged(call: Call, state: Call.State, message: String) {
            Log.i("$TAG Call [${call.remoteAddress.asStringUriOnly()}] state changed [$state]")
            if (LinphoneUtils.isCallOutgoing(call.state)) {
                isVideoEnabled.postValue(call.params.isVideoEnabled)
                updateVideoDirection(call.params.videoDirection)
            } else if (LinphoneUtils.isCallEnding(call.state)) {
                // If current call is being terminated but there is at least one other call, switch
                val core = call.core
                val callsCount = core.callsNb
                Log.i(
                    "$TAG Call is being ended, check for another current call (currently [$callsCount] calls)"
                )
                if (callsCount > 0) {
                    val newCurrentCall = core.currentCall ?: core.calls.firstOrNull()
                    if (newCurrentCall != null) {
                        Log.i(
                            "$TAG From now on current call will be [${newCurrentCall.remoteAddress.asStringUriOnly()}]"
                        )
                        configureCall(newCurrentCall)
                        updateEncryption()
                    } else {
                        Log.e("$TAG Failed to get a valid call to display")
                        endCall(call)
                    }
                } else {
                    endCall(call)
                }
            } else {
                val videoEnabled = LinphoneUtils.isVideoEnabled(call)
                if (videoEnabled && isVideoEnabled.value == false) {
                    if (isBluetoothEnabled.value == true || isHeadsetEnabled.value == true) {
                        Log.i(
                            "$TAG Audio is routed to bluetooth or headset, do not change it to speaker because video was enabled"
                        )
                    } else if (corePreferences.routeAudioToSpeakerWhenVideoIsEnabled) {
                        Log.i("$TAG Video is now enabled, routing audio to speaker")
                        AudioUtils.routeAudioToSpeaker(call)
                    }
                }
                isVideoEnabled.postValue(videoEnabled)
                updateVideoDirection(call.currentParams.videoDirection)

                if (call.state == Call.State.Connected) {
                    updateCallDuration()
                    if (call.conference != null) {
                        Log.i(
                            "$TAG Call is in Connected state and conference isn't null, going to conference fragment"
                        )
                        conferenceModel.configureFromCall(call)
                        goToConferenceEvent.postValue(Event(true))
                    } else {
                        conferenceModel.destroy()
                    }
                } else if (call.state == Call.State.StreamsRunning) {
                    videoUpdateInProgress.postValue(false)
                    updateCallDuration()
                    if (corePreferences.automaticallyStartCallRecording) {
                        val recording = call.params.isRecording
                        isRecording.postValue(recording)
                        if (recording) {
                            showRecordingToast()
                        }
                    }

                    // MediaEncryption None & SRTP won't be notified through onEncryptionChanged callback,
                    // we have to do it manually to leave the "wait for encryption" state
                    when (call.currentParams.mediaEncryption) {
                        MediaEncryption.SRTP, MediaEncryption.None -> {
                            updateEncryption()
                        }
                        else -> {}
                    }
                }
            }

            isPaused.postValue(isCallPaused())
            isPausedByRemote.postValue(call.state == Call.State.PausedByRemote)
            canBePaused.postValue(canCallBePaused())
        }

        @WorkerThread
        override fun onAudioDeviceChanged(call: Call, audioDevice: AudioDevice) {
            Log.i("$TAG Audio device changed [${audioDevice.id}]")
            updateOutputAudioDevice(audioDevice)
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

    private val coreListener = object : CoreListenerStub() {
        @WorkerThread
        override fun onCallStateChanged(
            core: Core,
            call: Call,
            state: Call.State,
            message: String
        ) {
            isOutgoingRinging.postValue(call.state == Call.State.OutgoingRinging)
            isIncomingEarlyMedia.postValue(call.state == Call.State.IncomingEarlyMedia)
            isOutgoingEarlyMedia.postValue(call.state == Call.State.OutgoingEarlyMedia)

            if (::currentCall.isInitialized) {
                if (call != currentCall) {
                    if (call == core.currentCall && state != Call.State.Pausing) {
                        Log.w(
                            "$TAG Current call has changed, now is [${call.remoteAddress.asStringUriOnly()}] with state [$state]"
                        )
                        currentCall.removeListener(callListener)
                        configureCall(call)
                        updateEncryption()
                    } else if (LinphoneUtils.isCallIncoming(call.state)) {
                        Log.w(
                            "$TAG A call is being received [${call.remoteAddress.asStringUriOnly()}], using it as current call unless declined"
                        )
                        currentCall.removeListener(callListener)
                        configureCall(call)
                    }
                }
            } else {
                Log.w(
                    "$TAG There was no current call (shouldn't be possible), using [${call.remoteAddress.asStringUriOnly()}] anyway"
                )
                configureCall(call)
            }

            if (LinphoneUtils.isCallEnding(call.state)) {
                waitingForEncryptionInfo.postValue(false)
            }

            updateProximitySensor()
        }

        @WorkerThread
        override fun onTransferStateChanged(core: Core, transfered: Call, state: Call.State) {
            Log.i(
                "$TAG Transferred call [${transfered.remoteAddress.asStringUriOnly()}] state changed [$state]"
            )

            if (state == Call.State.OutgoingProgress) {
                transferInProgressEvent.postValue(Event(true))
            } else if (LinphoneUtils.isCallEnding(state)) {
                transferFailedEvent.postValue(Event(true))
            }
        }

        @WorkerThread
        override fun onMessagesReceived(
            core: Core,
            chatRoom: ChatRoom,
            messages: Array<out ChatMessage>
        ) {
            if (::currentCall.isInitialized) {
                if (currentCallConversation == null) {
                    currentCallConversation = lookupCurrentCallConversation(currentCall)
                }
                if (currentCallConversation != null && currentCallConversation == chatRoom) {
                    val unreadCount = chatRoom.unreadMessagesCount
                    Log.i(
                        "$TAG Received [${messages.size}] message(s) for current call conversation, currently [$unreadCount] unread messages"
                    )
                    unreadMessagesCount.postValue(unreadCount)
                }
            }
        }

        @WorkerThread
        override fun onChatRoomRead(core: Core, chatRoom: ChatRoom) {
            if (currentCallConversation != null && currentCallConversation == chatRoom) {
                Log.i("$TAG Current call conversation was marked as read")
                unreadMessagesCount.postValue(0)
            }
        }

        @WorkerThread
        override fun onAudioDevicesListUpdated(core: Core) {
            Log.i("$TAG Audio devices list has been updated")
        }
    }

    init {
        fullScreenMode.value = false
        operationInProgress.value = false
        proximitySensorEnabled.value = false
        videoUpdateInProgress.value = false

        refreshKeyguardLockedStatus()
        answerAlpha.value = 1f
        declineAlpha.value = 1f

        coreContext.postOnCoreThread { core ->
            coreContext.contactsManager.addListener(contactsListener)

            core.addListener(coreListener)

            isRecordingEnabled.postValue(!corePreferences.disableCallRecordings)
            hideVideo.postValue(!core.isVideoEnabled)
            showSwitchCamera.postValue(coreContext.showSwitchCameraButton())

            val call = core.currentCall ?: core.calls.firstOrNull()

            if (call != null) {
                Log.i("$TAG Found call [${call.remoteAddress.asStringUriOnly()}]")
                configureCall(call)
            } else {
                Log.e("$TAG Failed to find call!")
            }
        }

        numpadModel = NumpadModel(
            true,
            { digit -> // onDigitClicked
                appendDigitToSearchBarEvent.value = Event(digit)
                coreContext.postOnCoreThread {
                    if (::currentCall.isInitialized) {
                        Log.i("$TAG Sending DTMF [${digit.first()}]")
                        currentCall.sendDtmf(digit.first())
                    }
                }
            },
            { // onVoicemailClicked
            },
            { // OnBackspaceClicked
                removedCharacterAtCurrentPositionEvent.value = Event(true)
            },
            { // OnCallClicked
            },
            { // OnBlindTransferClicked
            },
            { // OnClearInput
            }
        )

        updateCallQualityIcon()
    }

    @UiThread
    override fun onCleared() {
        super.onCleared()

        coreContext.postOnCoreThread { core ->
            core.removeListener(coreListener)
            coreContext.contactsManager.removeListener(contactsListener)
            conferenceModel.destroy()
            contact.value?.destroy()

            if (::currentCall.isInitialized) {
                currentCall.removeListener(callListener)
            }
        }
    }

    @UiThread
    fun refreshKeyguardLockedStatus() {
        val keyguardManager = coreContext.context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        val secure = keyguardManager.isKeyguardLocked
        isScreenLocked.value = secure
        Log.i("$TAG Device is [${if (secure) "locked" else "unlocked"}]")
    }

    @UiThread
    fun answer() {
        coreContext.postOnCoreThread { core ->
            val call = core.calls.find {
                LinphoneUtils.isCallIncoming(it.state)
            }
            if (call != null) {
                Log.i("$TAG Answering call [${call.remoteAddress.asStringUriOnly()}]")
                coreContext.answerCall(call)
            } else {
                Log.e("$TAG No call found in incoming state, can't answer any!")
            }
        }
    }

    @UiThread
    fun hangUp() {
        coreContext.postOnCoreThread {
            if (::currentCall.isInitialized) {
                Log.i("$TAG Terminating call [${currentCall.remoteAddress.asStringUriOnly()}]")
                terminatedByUser = true
                coreContext.terminateCall(currentCall)
            }
        }
    }

    @UiThread
    fun skipZrtpSas() {
        coreContext.postOnCoreThread {
            if (::currentCall.isInitialized) {
                Log.w("$TAG User skipped SAS validation in ZRTP call")
                currentCall.skipZrtpAuthentication()
            }
        }
    }

    @UiThread
    fun updateZrtpSas(authTokenClicked: String) {
        coreContext.postOnCoreThread {
            if (::currentCall.isInitialized) {
                if (authTokenClicked.isEmpty()) {
                    Log.e(
                        "$TAG Doing a fake ZRTP SAS check with empty token because user clicked on 'Not Found' button!"
                    )
                } else {
                    Log.i(
                        "$TAG Checking if ZRTP SAS auth token [$authTokenClicked] is the right one"
                    )
                }
                currentCall.checkAuthenticationTokenSelected(authTokenClicked)
            }
        }
    }

    @UiThread
    fun toggleMuteMicrophone() {
        if (ActivityCompat.checkSelfPermission(
                coreContext.context,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w("$TAG RECORD_AUDIO permission isn't granted, requesting it")
            requestRecordAudioPermission.postValue(Event(true))
            return
        }

        coreContext.postOnCoreThread {
            if (::currentCall.isInitialized) {
                val micMuted = if (currentCall.conference != null) {
                    currentCall.conference?.microphoneMuted == true
                } else {
                    currentCall.microphoneMuted
                }
                if (currentCall.conference != null) {
                    currentCall.conference?.microphoneMuted = !micMuted
                } else {
                    currentCall.microphoneMuted = !micMuted
                }
                if (micMuted) {
                    Log.w("$TAG Muting microphone")
                } else {
                    Log.i("$TAG Un-muting microphone")
                }
                isMicrophoneMuted.postValue(!micMuted)
            }
        }
    }

    @UiThread
    fun refreshMicrophoneState() {
        coreContext.postOnCoreThread {
            if (::currentCall.isInitialized) {
                val micMuted = if (currentCall.conference != null) {
                    currentCall.conference?.microphoneMuted == true
                } else {
                    currentCall.microphoneMuted
                }
                if (micMuted != isMicrophoneMuted.value) {
                    if (micMuted) {
                        Log.w("$TAG Microphone is muted, updating button state accordingly")
                    } else {
                        Log.i("$TAG Microphone is not muted, updating button state accordingly")
                    }
                    isMicrophoneMuted.postValue(micMuted)
                }
            }
        }
    }

    @UiThread
    fun changeAudioOutputDevice() {
        val routeAudioToSpeaker = isSpeakerEnabled.value != true
        if (!::currentCall.isInitialized) {
            Log.w("$TAG Current call not initialized yet, do not attempt to change output audio device")
            return
        }

        coreContext.postOnCoreThread { core ->
            var earpieceFound = false
            var speakerFound = false
            val audioDevices = core.audioDevices
            val currentDevice = currentCall.outputAudioDevice
            Log.i("$TAG Currently used output audio device is [${currentDevice?.deviceName} (${currentDevice?.type}])")

            val list = arrayListOf<AudioDeviceModel>()
            for (device in audioDevices) {
                // Only list output audio devices
                if (!device.hasCapability(AudioDevice.Capabilities.CapabilityPlay)) continue

                val name = when (device.type) {
                    AudioDevice.Type.Earpiece -> {
                        earpieceFound = true
                        AppUtils.getString(R.string.call_audio_device_type_earpiece)
                    }
                    AudioDevice.Type.Speaker -> {
                        speakerFound = true
                        AppUtils.getString(R.string.call_audio_device_type_speaker)
                    }
                    AudioDevice.Type.Headset -> {
                        AppUtils.getString(R.string.call_audio_device_type_headset)
                    }
                    AudioDevice.Type.Headphones -> {
                        AppUtils.getString(R.string.call_audio_device_type_headphones)
                    }
                    AudioDevice.Type.Bluetooth -> {
                        AppUtils.getFormattedString(
                            R.string.call_audio_device_type_bluetooth,
                            device.deviceName
                        )
                    }
                    AudioDevice.Type.HearingAid -> {
                        AppUtils.getFormattedString(
                            R.string.call_audio_device_type_hearing_aid,
                            device.deviceName
                        )
                    }
                    else -> device.deviceName
                }
                val isCurrentlyInUse = device.type == currentDevice?.type && device.deviceName == currentDevice.deviceName
                val model = AudioDeviceModel(device, name, device.type, isCurrentlyInUse, true) {
                    // onSelected
                    coreContext.postOnCoreThread {
                        Log.i("$TAG Selected audio device with ID [${device.id}]")
                        if (::currentCall.isInitialized) {
                            when (device.type) {
                                AudioDevice.Type.Headset, AudioDevice.Type.Headphones -> AudioUtils.routeAudioToHeadset(
                                    currentCall
                                )
                                AudioDevice.Type.Bluetooth -> AudioUtils.routeAudioToBluetooth(
                                    currentCall
                                )
                                AudioDevice.Type.HearingAid -> AudioUtils.routeAudioToHearingAid(
                                    currentCall
                                )
                                AudioDevice.Type.Speaker -> AudioUtils.routeAudioToSpeaker(
                                    currentCall
                                )
                                else -> AudioUtils.routeAudioToEarpiece(currentCall)
                            }
                        }
                    }
                }
                list.add(model)
                Log.i("$TAG Found audio device [${device.id}]")
            }

            if (list.size > 2 || (list.size > 1 && (!earpieceFound || !speakerFound))) {
                Log.i("$TAG Found more than two devices (or more than 1 but no earpiece or speaker), showing list to let user choose")
                showAudioDevicesListEvent.postValue(Event(list))
            } else {
                Log.i(
                    "$TAG Found less than two devices, simply switching between earpiece & speaker"
                )
                if (routeAudioToSpeaker) {
                    AudioUtils.routeAudioToSpeaker(currentCall)
                } else {
                    AudioUtils.routeAudioToEarpiece(currentCall)
                }
            }
        }
    }

    @UiThread
    fun toggleVideo() {
        if (ActivityCompat.checkSelfPermission(
                coreContext.context,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w("$TAG CAMERA permission isn't granted, requesting it")
            requestCameraPermission.postValue(Event(true))
            return
        }

        coreContext.postOnCoreThread { core ->
            if (::currentCall.isInitialized) {
                val params = core.createCallParams(currentCall)
                if (currentCall.conference != null) {
                    if (params?.isVideoEnabled == false) {
                        Log.i("$TAG Conference found and video disabled in params, enabling it")
                        params.isVideoEnabled = true
                        params.videoDirection = MediaDirection.SendRecv
                        conferenceModel.setNewLayout(ConferenceViewModel.ACTIVE_SPEAKER_LAYOUT)
                    } else {
                        if (params?.videoDirection == MediaDirection.SendRecv || params?.videoDirection == MediaDirection.SendOnly) {
                            Log.i(
                                "$TAG Conference found with video already enabled, changing video media direction to receive only"
                            )
                            params.videoDirection = MediaDirection.RecvOnly
                        } else {
                            Log.i(
                                "$TAG Conference found with video already enabled, changing video media direction to send & receive"
                            )
                            params?.videoDirection = MediaDirection.SendRecv
                        }
                    }

                    val sendingVideo = params?.videoDirection == MediaDirection.SendRecv || params?.videoDirection == MediaDirection.SendOnly
                    conferenceModel.localVideoStreamToggled(sendingVideo)
                } else if (params != null) {
                    params.isVideoEnabled = true
                    params.videoDirection = when (currentCall.currentParams.videoDirection) {
                        MediaDirection.SendRecv, MediaDirection.SendOnly -> MediaDirection.RecvOnly
                        else -> MediaDirection.SendRecv
                    }
                    Log.i(
                        "$TAG Updating call with video enabled and media direction set to ${params.videoDirection}"
                    )
                }
                currentCall.update(params)
                videoUpdateInProgress.postValue(true)
            }
        }
    }

    @UiThread
    fun switchCamera() {
        coreContext.postOnCoreThread {
            Log.i("$TAG Switching camera")
            coreContext.switchCamera()
        }
    }

    @UiThread
    fun toggleRecording() {
        coreContext.postOnCoreThread {
            if (::currentCall.isInitialized) {
                if (currentCall.params.isRecording) {
                    Log.i("$TAG Stopping call recording")
                    currentCall.stopRecording()
                } else {
                    Log.i("$TAG Starting call recording")
                    currentCall.startRecording()
                }

                val recording = currentCall.params.isRecording
                isRecording.postValue(recording)
                if (recording) {
                    showRecordingToast()
                }
            }
        }
    }

    @UiThread
    fun togglePause() {
        coreContext.postOnCoreThread {
            if (::currentCall.isInitialized) {
                if (currentCall.conference != null) {
                    conferenceModel.togglePause()
                } else {
                    when (isCallPaused()) {
                        true -> {
                            Log.i(
                                "$TAG Resuming call [${currentCall.remoteAddress.asStringUriOnly()}]"
                            )
                            currentCall.resume()
                        }

                        false -> {
                            Log.i(
                                "$TAG Pausing call [${currentCall.remoteAddress.asStringUriOnly()}]"
                            )
                            currentCall.pause()
                        }
                    }
                }
            }
        }
    }

    @UiThread
    fun toggleFullScreen() {
        if (fullScreenMode.value == true) {
            // Always allow to switch off full screen mode
            fullScreenMode.value = false
            return
        }

        if (isVideoEnabled.value == false) {
            // Do not allow turning full screen on for audio only calls
            return
        }
        fullScreenMode.value = true
    }

    @UiThread
    fun toggleExpandActionsMenu() {
        toggleExtraActionsBottomSheetEvent.value = Event(true)
    }

    @UiThread
    fun showNumpad() {
        showNumpadBottomSheetEvent.value = Event(true)
    }

    @UiThread
    fun createConversation() {
        if (::currentCall.isInitialized) {
            coreContext.postOnCoreThread {
                val existingConversation = currentCallConversation ?: lookupCurrentCallConversation(currentCall)
                if (existingConversation != null) {
                    Log.i(
                        "$TAG Found existing conversation [${LinphoneUtils.getConversationId(existingConversation)}], going to it"
                    )
                    goToConversationEvent.postValue(Event(LinphoneUtils.getConversationId(existingConversation)))
                } else {
                    Log.i("$TAG No existing conversation was found, let's create it")
                    createCurrentCallConversation(currentCall)
                }
            }
        }
    }

    @WorkerThread
    fun attendedTransferCallTo(to: Call) {
        if (::currentCall.isInitialized) {
            val toCallState = to.state
            if (LinphoneUtils.isCallEnding(toCallState, considerReleasedAsEnding = true)) {
                Log.e("$TAG Do not attempt attended transfer to call in state [$toCallState]")
                return
            }
            val currentCallState = currentCall.state
            if (LinphoneUtils.isCallEnding(currentCallState, considerReleasedAsEnding = true)) {
                Log.e("$TAG Do not attempt attended transfer of call in state [$currentCallState]")
                return
            }

            Log.i(
                "$TAG Doing an attended transfer between currently displayed call [${currentCall.remoteAddress.asStringUriOnly()}] and paused call [${to.remoteAddress.asStringUriOnly()}]"
            )
            if (to.transferToAnother(currentCall) == 0) {
                Log.i("$TAG Attended transfer is successful")
            } else {
                Log.e("$TAG Failed to make attended transfer!")
            }
        }
    }

    @WorkerThread
    fun blindTransferCallTo(to: Address) {
        if (::currentCall.isInitialized) {
            val callState = currentCall.state
            if (LinphoneUtils.isCallEnding(callState, considerReleasedAsEnding = true)) {
                Log.e("$TAG Do not attempt blind transfer of call in state [$callState]")
                return
            }

            Log.i(
                "$TAG Call [${currentCall.remoteAddress.asStringUriOnly()}] is being blindly transferred to [${to.asStringUriOnly()}]"
            )
            if (currentCall.transferTo(to) == 0) {
                Log.i("$TAG Blind call transfer is successful")
            } else {
                Log.e("$TAG Failed to make blind call transfer!")
                transferFailedEvent.postValue(Event(true))
            }
        }
    }

    @UiThread
    fun showZrtpSasDialogIfPossible() {
        coreContext.postOnCoreThread {
            if (currentCall.currentParams.mediaEncryption == MediaEncryption.ZRTP) {
                val isDeviceTrusted = currentCall.authenticationTokenVerified
                val cacheMismatch = currentCall.zrtpCacheMismatchFlag
                Log.i(
                    "$TAG Current call media encryption is ZRTP, auth token is [${if (isDeviceTrusted) "trusted" else "not trusted yet"}]"
                )
                val tokenToRead = currentCall.localAuthenticationToken
                val tokensToDisplay = currentCall.remoteAuthenticationTokens.toList()
                if (!tokenToRead.isNullOrEmpty() && tokensToDisplay.size == 4) {
                    val event = Event(Pair(tokenToRead, tokensToDisplay))
                    if (cacheMismatch) {
                        showZrtpSasCacheMismatchDialogEvent.postValue(event)
                    } else {
                        showZrtpSasDialogEvent.postValue(event)
                    }
                } else {
                    Log.w(
                        "$TAG Either local auth token is null/empty or remote tokens list doesn't contains 4 elements!"
                    )
                }
            }
        }
    }

    @WorkerThread
    private fun updateEncryption() {
        when (val mediaEncryption = currentCall.currentParams.mediaEncryption) {
            MediaEncryption.ZRTP -> {
                val isDeviceTrusted = currentCall.authenticationTokenVerified
                val cacheMismatch = currentCall.zrtpCacheMismatchFlag
                Log.i(
                    "$TAG Current call media encryption is ZRTP, auth token is [${if (isDeviceTrusted) "trusted" else "not trusted yet"}], cache mismatch is [$cacheMismatch]"
                )

                updateAvatarModelSecurityLevel(isDeviceTrusted && !cacheMismatch)

                isMediaEncrypted.postValue(true)
                isZrtp.postValue(true)

                isZrtpSasValidationRequired.postValue(cacheMismatch || !isDeviceTrusted)
                if (cacheMismatch || !isDeviceTrusted) {
                    Log.i("$TAG Showing ZRTP SAS confirmation dialog")
                    val tokenToRead = currentCall.localAuthenticationToken
                    val tokensToDisplay = currentCall.remoteAuthenticationTokens.toList()
                    if (!tokenToRead.isNullOrEmpty() && tokensToDisplay.size == 4) {
                        val event = Event(Pair(tokenToRead, tokensToDisplay))
                        if (cacheMismatch) {
                            showZrtpSasCacheMismatchDialogEvent.postValue(event)
                        } else {
                            showZrtpSasDialogEvent.postValue(event)
                        }
                    } else {
                        Log.w(
                            "$TAG Either local auth token is null/empty or remote tokens list doesn't contains 4 elements!"
                        )
                    }
                }
            }
            MediaEncryption.SRTP, MediaEncryption.DTLS -> {
                Log.i("$TAG Current call media encryption is [$mediaEncryption]")
                isMediaEncrypted.postValue(true)
                isZrtp.postValue(false)
            }
            else -> {
                Log.w("$TAG Current call doesn't have any media encryption!")
                isMediaEncrypted.postValue(false)
                isZrtp.postValue(false)
            }
        }
        waitingForEncryptionInfo.postValue(false)
        callMediaEncryptionModel.update(currentCall)
    }

    @WorkerThread
    private fun configureCall(call: Call) {
        Log.i(
            "$TAG Configuring call with remote address [${call.remoteAddress.asStringUriOnly()}] as current"
        )
        contact.value?.destroy()

        terminatedByUser = false
        currentCall = call
        callStatsModel.update(call, call.audioStats)
        callMediaEncryptionModel.update(call)
        call.addListener(callListener)

        if (call.currentParams.mediaEncryption == MediaEncryption.None) {
            waitingForEncryptionInfo.postValue(true)
            isMediaEncrypted.postValue(false)
        } else {
            updateEncryption()
        }

        val conferenceInfo = LinphoneUtils.getConferenceInfoIfAny(call)
        if (call.conference != null || conferenceInfo != null) {
            val subject = call.conference?.subject ?: conferenceInfo?.subject
            Log.i("$TAG Conference [$subject] found, going to conference fragment")
            conferenceModel.configureFromCall(call)
            goToConferenceEvent.postValue(Event(true))
        } else {
            conferenceModel.destroy()
            goToCallEvent.postValue(Event(true))
        }

        if (call.dir == Call.Dir.Incoming) {
            val isVideo = call.remoteParams?.isVideoEnabled == true && call.remoteParams?.videoDirection != MediaDirection.Inactive
            if (call.core.accountList.size > 1) {
                val localAddress = call.callLog.toAddress
                Log.i("$TAG Local address for incoming call is [${localAddress.asStringUriOnly()}]")
                val localAccount = coreContext.core.accountList.find {
                    it.params.identityAddress?.weakEqual(localAddress) == true
                }
                val displayName = if (localAccount != null) {
                    LinphoneUtils.getDisplayName(localAccount.params.identityAddress)
                } else {
                    Log.w("$TAG Matching local account was not found, using TO address display name or username")
                    LinphoneUtils.getDisplayName(localAddress)
                }
                Log.i("$TAG Showing account being called as [$displayName]")

                if (isVideo) {
                    incomingCallTitle.postValue(
                        AppUtils.getFormattedString(
                            R.string.call_video_incoming_for_account,
                            displayName
                        )
                    )
                } else {
                    incomingCallTitle.postValue(
                        AppUtils.getFormattedString(
                            R.string.call_audio_incoming_for_account,
                            displayName
                        )
                    )
                }
            } else {
                if (isVideo) {
                    incomingCallTitle.postValue(AppUtils.getString(R.string.call_video_incoming))
                } else {
                    incomingCallTitle.postValue(AppUtils.getString(R.string.call_audio_incoming))
                }
            }
        }

        if (LinphoneUtils.isCallOutgoing(call.state)) {
            isVideoEnabled.postValue(call.params.isVideoEnabled)
            updateVideoDirection(call.params.videoDirection)
        } else if (LinphoneUtils.isCallIncoming(call.state)) {
            isVideoEnabled.postValue(
                call.remoteParams?.isVideoEnabled == true && call.remoteParams?.videoDirection != MediaDirection.Inactive
            )
        } else {
            isVideoEnabled.postValue(call.currentParams.isVideoEnabled)
            updateVideoDirection(call.currentParams.videoDirection)
        }

        if (ActivityCompat.checkSelfPermission(
                coreContext.context,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(
                "$TAG RECORD_AUDIO permission wasn't granted yet, considering microphone as muted!"
            )
            isMicrophoneMuted.postValue(true)
        } else {
            val micMuted = call.conference?.microphoneMuted ?: call.microphoneMuted
            if (micMuted) {
                Log.w("$TAG Microphone is currently muted")
            }
            isMicrophoneMuted.postValue(micMuted)
        }

        val audioDevice = call.outputAudioDevice
        updateOutputAudioDevice(audioDevice)

        isOutgoing.postValue(call.dir == Call.Dir.Outgoing)
        val state = call.state
        isOutgoingRinging.postValue(state == Call.State.OutgoingRinging)
        isIncomingEarlyMedia.postValue(state == Call.State.IncomingEarlyMedia)
        isOutgoingEarlyMedia.postValue(state == Call.State.OutgoingEarlyMedia)

        isPaused.postValue(isCallPaused())
        isPausedByRemote.postValue(state == Call.State.PausedByRemote)
        canBePaused.postValue(canCallBePaused())

        val address = call.callLog.remoteAddress
        val uri = if (corePreferences.onlyDisplaySipUriUsername) {
            address.username ?: ""
        } else {
            LinphoneUtils.getAddressAsCleanStringUriOnly(address)
        }
        displayedAddress.postValue(uri)

        val model = if (conferenceInfo != null) {
            coreContext.contactsManager.getContactAvatarModelForConferenceInfo(conferenceInfo)
        } else {
            // Do not use contact avatar model from ContactsManager
            val friend = coreContext.contactsManager.findContactByAddress(address)
            if (friend != null) {
                ContactAvatarModel(friend, address)
            } else {
                val fakeFriend = coreContext.core.createFriend()
                fakeFriend.name = LinphoneUtils.getDisplayName(address)
                fakeFriend.address = address
                ContactAvatarModel(fakeFriend, address)
            }
        }

        contact.postValue(model)
        displayedName.postValue(model.friend.name)

        val recording = call.params.isRecording
        isRecording.postValue(recording)
        if (recording) {
            showRecordingToast()
        }

        val isRemoteRecording = call.remoteParams?.isRecording == true
        if (isRemoteRecording) {
            Log.w("$TAG Remote end [${displayedName.value.orEmpty()}] is recording the call")
            isRemoteRecordingEvent.postValue(Event(Pair(true, displayedName.value.orEmpty())))
        }

        callDuration.postValue(call.duration)

        val chatRoom = lookupCurrentCallConversation(call)
        currentCallConversation = chatRoom
        if (chatRoom != null) {
            val unreadCount = chatRoom.unreadMessagesCount
            Log.i(
                "$TAG Found existing 1-1 conversation for current call, currently [$unreadCount] unread messages in existing conversation"
            )
            unreadMessagesCount.postValue(unreadCount)
        } else {
            Log.i("$TAG Failed to find an existing 1-1 conversation for current call")
        }
    }

    @WorkerThread
    fun updateCallDuration() {
        if (::currentCall.isInitialized) {
            callDuration.postValue(currentCall.duration)
        }
    }

    @WorkerThread
    private fun updateOutputAudioDevice(audioDevice: AudioDevice?) {
        Log.i("$TAG Output audio device updated to [${audioDevice?.deviceName} (${audioDevice?.type})]")
        isSpeakerEnabled.postValue(audioDevice?.type == AudioDevice.Type.Speaker)
        isHeadsetEnabled.postValue(
            audioDevice?.type == AudioDevice.Type.Headphones || audioDevice?.type == AudioDevice.Type.Headset
        )
        isHearingAidEnabled.postValue(audioDevice?.type == AudioDevice.Type.HearingAid)
        isBluetoothEnabled.postValue(audioDevice?.type == AudioDevice.Type.Bluetooth)

        updateProximitySensor()
    }

    @WorkerThread
    private fun isCallPaused(): Boolean {
        if (::currentCall.isInitialized) {
            return when (currentCall.state) {
                Call.State.Paused, Call.State.Pausing -> true
                else -> false
            }
        }
        return false
    }

    @WorkerThread
    private fun canCallBePaused(): Boolean {
        return ::currentCall.isInitialized && !currentCall.mediaInProgress() && when (currentCall.state) {
            Call.State.StreamsRunning, Call.State.Pausing, Call.State.Paused -> true
            else -> false
        }
    }

    @WorkerThread
    private fun updateVideoDirection(direction: MediaDirection) {
        val state = currentCall.state
        if (state != Call.State.StreamsRunning) {
            return
        }

        val isSending = direction == MediaDirection.SendRecv || direction == MediaDirection.SendOnly
        val isReceiving = direction == MediaDirection.SendRecv || direction == MediaDirection.RecvOnly

        val wasSending = isSendingVideo.value == true
        val wasReceiving = isReceivingVideo.value == true

        if (isReceiving != wasReceiving || isSending != wasSending) {
            Log.i(
                "$TAG Video is enabled in ${if (isSending && isReceiving) "both ways" else if (isSending) "upload" else "download"}"
            )
            isSendingVideo.postValue(isSending)
            isReceivingVideo.postValue(isReceiving)
        }

        if (currentCall.conference == null) { // Let conference view model handle full screen while in conference
            if (isReceiving && !wasReceiving) { // Do not change full screen mode base on our video being sent when it wasn't
                if (fullScreenMode.value != true) {
                    Log.i(
                        "$TAG Video is being received or sent (and it wasn't before), switching to full-screen mode"
                    )
                    fullScreenMode.postValue(true)
                }
            } else if (!isSending && !isReceiving && fullScreenMode.value == true) {
                Log.w("$TAG Video is no longer sent nor received, leaving full screen mode")
                fullScreenMode.postValue(false)
            }
        }

        updateProximitySensor()
    }

    @AnyThread
    private fun updateCallQualityIcon() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                delay(1000)
                coreContext.postOnCoreThread {
                    if (::currentCall.isInitialized) {
                        val quality = currentCall.currentQuality
                        val icon = when {
                            quality >= 4 -> R.drawable.cell_signal_full
                            quality >= 3 -> R.drawable.cell_signal_high
                            quality >= 2 -> R.drawable.cell_signal_medium
                            quality >= 1 -> R.drawable.cell_signal_low
                            else -> R.drawable.cell_signal_none
                        }
                        qualityValue.postValue(quality)
                        qualityIcon.postValue(icon)
                    }

                    updateCallQualityIcon()
                }
            }
        }
    }

    @WorkerThread
    private fun updateAvatarModelSecurityLevel(trusted: Boolean) {
        val securityLevel = if (trusted) SecurityLevel.EndToEndEncryptedAndVerified else SecurityLevel.EndToEndEncrypted
        val avatarModel = contact.value
        if (avatarModel != null && currentCall.conference == null) { // Don't do it for conferences
            avatarModel.trust.postValue(securityLevel)
            contact.postValue(avatarModel!!)
        } else {
            Log.e("$TAG No avatar model found!")
        }

        // Also update avatar contact model if any for the rest of the app
        val address = currentCall.remoteAddress
        val storedModel = coreContext.contactsManager.getContactAvatarModelForAddress(
            address
        )
        storedModel.updateSecurityLevel(address)
    }

    @WorkerThread
    private fun lookupCurrentCallConversation(call: Call): ChatRoom? {
        val localAddress = call.callLog.localAddress
        val remoteAddress = call.remoteAddress

        val existingConversation = if (call.conference != null) {
            Log.i("$TAG We're in [${remoteAddress.asStringUriOnly()}] conference, using it as chat room if possible")
            call.conference?.chatRoom
        } else {
            val params = getChatRoomParams(call)
            val participants = arrayOf(remoteAddress)
            Log.i("$TAG Looking for conversation with local address [${localAddress.asStringUriOnly()}] and participant [${remoteAddress.asStringUriOnly()}]")
            call.core.searchChatRoom(
                params,
                localAddress,
                null,
                participants
            )
        }
        if (existingConversation != null) {
            Log.i("$TAG Found existing conversation [${existingConversation.peerAddress.asStringUriOnly()}] found for current call with local address [${localAddress.asStringUriOnly()}] and remote address [${remoteAddress.asStringUriOnly()}]")
        } else {
            Log.w("$TAG No existing conversation found for current call with local address [${localAddress.asStringUriOnly()}] and remote address [${remoteAddress.asStringUriOnly()}]")
        }
        return existingConversation
    }

    @WorkerThread
    private fun createCurrentCallConversation(call: Call) {
        val remoteAddress = call.remoteAddress
        val participants = arrayOf(remoteAddress)
        val core = call.core
        operationInProgress.postValue(true)

        val params = getChatRoomParams(call) ?: return // TODO: show error to user
        val chatRoom = core.createChatRoom(params, participants)
        if (chatRoom != null) {
            if (params.chatParams?.backend == ChatRoom.Backend.FlexisipChat) {
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
                "$TAG Failed to create 1-1 conversation with [${remoteAddress.asStringUriOnly()}]!"
            )
            operationInProgress.postValue(false)
            chatRoomCreationErrorEvent.postValue(
                Event(R.string.conversation_failed_to_create_toast)
            )
        }
    }

    @WorkerThread
    private fun getChatRoomParams(call: Call): ConferenceParams? {
        val localAddress = call.callLog.localAddress
        val remoteAddress = call.remoteAddress
        val core = call.core
        val account = LinphoneUtils.getAccountForAddress(localAddress) ?: LinphoneUtils.getDefaultAccount() ?: return null

        val params = coreContext.core.createConferenceParams(call.conference)
        params.isChatEnabled = true
        params.isGroupEnabled = false
        params.subject = AppUtils.getString(R.string.conversation_one_to_one_hidden_subject)
        params.account = account

        val chatParams = params.chatParams ?: return null
        chatParams.ephemeralLifetime = 0 // Make sure ephemeral is disabled by default

        val sameDomain = remoteAddress.domain == corePreferences.defaultDomain && remoteAddress.domain == account.params.domain
        if (account.params.instantMessagingEncryptionMandatory && sameDomain) {
            Log.i(
                "$TAG Account is in secure mode & domain matches, requesting E2E encryption"
            )
            chatParams.backend = ChatRoom.Backend.FlexisipChat
            params.securityLevel = Conference.SecurityLevel.EndToEnd
        } else if (!account.params.instantMessagingEncryptionMandatory) {
            if (LinphoneUtils.isEndToEndEncryptedChatAvailable(core)) {
                Log.i(
                    "$TAG Account is in interop mode but LIME is available, requesting E2E encryption"
                )
                chatParams.backend = ChatRoom.Backend.FlexisipChat
                params.securityLevel = Conference.SecurityLevel.EndToEnd
            } else {
                Log.i(
                    "$TAG Account is in interop mode but LIME isn't available, disabling E2E encryption"
                )
                chatParams.backend = ChatRoom.Backend.Basic
                params.securityLevel = Conference.SecurityLevel.None
            }
        } else {
            Log.e(
                "$TAG Account is in secure mode, can't chat with SIP address of different domain [${remoteAddress.asStringUriOnly()}]"
            )
            return null
        }
        return params
    }

    @WorkerThread
    private fun endCall(call: Call) {
        val reason = call.reason
        val status = call.callLog.status
        Log.i("$TAG Call is ending with status [$status] because of reason [$reason]")

        when (status) {
            Call.Status.AcceptedElsewhere, Call.Status.DeclinedElsewhere -> {
                Log.i("$TAG Call was accepted/declined on another device, do not show ended call fragment")
                finishActivityEvent.postValue(Event(true))
            }
            else -> {
                if (pipMode.value == true) {
                    Log.i("$TAG Activity currently displayed in PiP, do not go to ended call fragment")
                    finishActivityEvent.postValue(Event(true))
                    return
                }

                Log.i("$TAG Go to ended call fragment")
                updateCallDuration()

                // Show that call was ended for a few seconds, then leave
                val text = if (call.state == Call.State.Error) {
                    LinphoneUtils.getCallErrorInfoToast(call)
                } else {
                    ""
                }
                goToEndedCallEvent.postValue(Event(text))
            }
        }
    }

    @AnyThread
    private fun showRecordingToast() {
        showGreenToast(R.string.call_is_being_recorded, R.drawable.record_fill)
    }

    @WorkerThread
    private fun updateProximitySensor() {
        if (::currentCall.isInitialized) {
            val callState = currentCall.state
            if (LinphoneUtils.isCallIncoming(callState)) {
                proximitySensorEnabled.postValue(false)
            } else if (LinphoneUtils.isCallOutgoing(callState)) {
                val videoEnabled = currentCall.params.isVideoEnabled
                proximitySensorEnabled.postValue(!videoEnabled)
            } else {
                if (isSendingVideo.value == true || isReceivingVideo.value == true) {
                    proximitySensorEnabled.postValue(false)
                } else {
                    val outputAudioDevice = currentCall.outputAudioDevice ?: coreContext.core.outputAudioDevice
                    if (outputAudioDevice != null && outputAudioDevice.type == AudioDevice.Type.Earpiece) {
                        proximitySensorEnabled.postValue(true)
                    } else {
                        proximitySensorEnabled.postValue(false)
                    }
                }
            }
        } else {
            proximitySensorEnabled.postValue(false)
        }
    }
}
