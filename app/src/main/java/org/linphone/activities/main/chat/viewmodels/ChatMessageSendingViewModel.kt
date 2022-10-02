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

import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.media.AudioFocusRequestCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.R
import org.linphone.activities.main.chat.data.ChatMessageAttachmentData
import org.linphone.activities.main.chat.data.ChatMessageData
import org.linphone.compatibility.Compatibility
import org.linphone.core.*
import org.linphone.core.tools.Log
import org.linphone.utils.AppUtils
import org.linphone.utils.Event
import org.linphone.utils.FileUtils
import org.linphone.utils.PermissionHelper

class ChatMessageSendingViewModelFactory(private val chatRoom: ChatRoom) :
    ViewModelProvider.NewInstanceFactory() {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return ChatMessageSendingViewModel(chatRoom) as T
    }
}

class ChatMessageSendingViewModel(private val chatRoom: ChatRoom) : ViewModel() {
    var temporaryFileUploadPath: File? = null

    val attachments = MutableLiveData<ArrayList<ChatMessageAttachmentData>>()

    val attachFileEnabled = MutableLiveData<Boolean>()

    val sendMessageEnabled = MutableLiveData<Boolean>()

    val isReadOnly = MutableLiveData<Boolean>()

    var textToSend = MutableLiveData<String>()

    val isPendingAnswer = MutableLiveData<Boolean>()

    var pendingChatMessageToReplyTo = MutableLiveData<ChatMessageData>()

    val requestRecordAudioPermissionEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val messageSentEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val voiceRecordingProgressBarMax = 10000

    val isPendingVoiceRecord = MutableLiveData<Boolean>()

    val isVoiceRecording = MutableLiveData<Boolean>()

    val voiceRecordingDuration = MutableLiveData<Int>()

    val formattedDuration = MutableLiveData<String>()

    val isPlayingVoiceRecording = MutableLiveData<Boolean>()

    val voiceRecordPlayingPosition = MutableLiveData<Int>()

    val imeFlags: Int = if (chatRoom.hasCapability(ChatRoomCapabilities.Encrypted.toInt())) {
        // IME_FLAG_NO_PERSONALIZED_LEARNING is only available on Android 8 and newer
        Compatibility.getImeFlagsForSecureChatRoom()
    } else {
        EditorInfo.IME_FLAG_NO_EXTRACT_UI
    }

    private val recorder: Recorder

    private var voiceRecordAudioFocusRequest: AudioFocusRequestCompat? = null

    private lateinit var voiceRecordingPlayer: Player
    private val playerListener = PlayerListener {
        Log.i("[Chat Message Sending] End of file reached")
        stopVoiceRecordPlayer()
    }

    private val chatRoomListener: ChatRoomListenerStub = object : ChatRoomListenerStub() {
        override fun onStateChanged(chatRoom: ChatRoom, state: ChatRoom.State) {
            updateChatRoomReadOnlyState()
        }

        override fun onConferenceJoined(chatRoom: ChatRoom, eventLog: EventLog) {
            updateChatRoomReadOnlyState()
        }

        override fun onConferenceLeft(chatRoom: ChatRoom, eventLog: EventLog) {
            updateChatRoomReadOnlyState()
        }
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        chatRoom.addListener(chatRoomListener)

        attachments.value = arrayListOf()

        attachFileEnabled.value = true
        sendMessageEnabled.value = false
        updateChatRoomReadOnlyState()

        val recorderParams = coreContext.core.createRecorderParams()
        if (corePreferences.voiceMessagesFormatMkv) {
            recorderParams.fileFormat = RecorderFileFormat.Mkv
        } else {
            recorderParams.fileFormat = RecorderFileFormat.Wav
        }
        recorder = coreContext.core.createRecorder(recorderParams)
    }

    override fun onCleared() {
        pendingChatMessageToReplyTo.value?.destroy()

        if (recorder.state != RecorderState.Closed) {
            recorder.close()
        }

        if (this::voiceRecordingPlayer.isInitialized) {
            stopVoiceRecordPlayer()
            voiceRecordingPlayer.removeListener(playerListener)
        }

        chatRoom.removeListener(chatRoomListener)
        scope.cancel()
        super.onCleared()
    }

    fun onTextToSendChanged(value: String) {
        sendMessageEnabled.value = value.trim().isNotEmpty() || attachments.value?.isNotEmpty() == true || isPendingVoiceRecord.value == true
        if (value.isNotEmpty()) {
            if (attachFileEnabled.value == true && !corePreferences.allowMultipleFilesAndTextInSameMessage) {
                attachFileEnabled.value = false
            }
            chatRoom.compose()
        } else {
            if (!corePreferences.allowMultipleFilesAndTextInSameMessage) {
                attachFileEnabled.value = attachments.value?.isEmpty() ?: true
            }
        }
    }

    fun addAttachment(path: String) {
        val list = arrayListOf<ChatMessageAttachmentData>()
        list.addAll(attachments.value.orEmpty())
        list.add(
            ChatMessageAttachmentData(path) {
                removeAttachment(it)
            }
        )
        attachments.value = list

        sendMessageEnabled.value = textToSend.value.orEmpty().trim().isNotEmpty() || list.isNotEmpty() || isPendingVoiceRecord.value == true
        if (!corePreferences.allowMultipleFilesAndTextInSameMessage) {
            attachFileEnabled.value = false
        }
    }

    private fun removeAttachment(attachment: ChatMessageAttachmentData) {
        val list = arrayListOf<ChatMessageAttachmentData>()
        list.addAll(attachments.value.orEmpty())
        list.remove(attachment)
        attachments.value = list

        sendMessageEnabled.value = textToSend.value.orEmpty().trim().isNotEmpty() || list.isNotEmpty() || isPendingVoiceRecord.value == true
        if (!corePreferences.allowMultipleFilesAndTextInSameMessage) {
            attachFileEnabled.value = list.isEmpty()
        }
    }

    fun sendMessage() {
        if (!isPlayerClosed()) {
            stopVoiceRecordPlayer()
        }

        val pendingMessageToReplyTo = pendingChatMessageToReplyTo.value
        val message: ChatMessage = if (isPendingAnswer.value == true && pendingMessageToReplyTo != null)
            chatRoom.createReplyMessage(pendingMessageToReplyTo.chatMessage)
        else
            chatRoom.createEmptyMessage()
        val isBasicChatRoom: Boolean = chatRoom.hasCapability(ChatRoomCapabilities.Basic.toInt())

        var voiceRecord = false
        if (isPendingVoiceRecord.value == true && recorder.file != null) {
            val content = recorder.createContent()
            if (content != null) {
                Log.i("[Chat Message Sending] Voice recording content created, file name is ${content.name} and duration is ${content.fileDuration}")
                message.addContent(content)
                voiceRecord = true
            } else {
                Log.e("[Chat Message Sending] Voice recording content couldn't be created!")
            }

            isPendingVoiceRecord.value = false
            isVoiceRecording.value = false
        }

        val toSend = textToSend.value.orEmpty().trim()
        if (toSend.isNotEmpty()) {
            if (voiceRecord && isBasicChatRoom) {
                val textMessage: ChatMessage = chatRoom.createMessageFromUtf8(toSend)
                textMessage.send()
            } else {
                message.addUtf8TextContent(toSend)
            }
        }

        var fileContent = false
        for (attachment in attachments.value.orEmpty()) {
            val content = Factory.instance().createContent()

            if (attachment.isImage) {
                content.type = "image"
            } else {
                content.type = "file"
            }
            content.subtype = FileUtils.getExtensionFromFileName(attachment.fileName)
            content.name = attachment.fileName
            content.filePath = attachment.path // Let the file body handler take care of the upload

            // Do not send file in the same message as the text in a BasicChatRoom
            // and don't send multiple files in the same message if setting says so
            if (isBasicChatRoom or (corePreferences.preventMoreThanOneFilePerMessage and (fileContent or voiceRecord))) {
                val fileMessage: ChatMessage = chatRoom.createFileTransferMessage(content)
                fileMessage.send()
            } else {
                message.addFileContent(content)
                fileContent = true
            }
        }

        if (message.contents.isNotEmpty()) {
            message.send()
        }

        cancelReply()
        attachments.value = arrayListOf()
        textToSend.value = ""

        messageSentEvent.value = Event(true)
    }

    fun transferMessage(chatMessage: ChatMessage) {
        val message = chatRoom.createForwardMessage(chatMessage)
        message.send()
    }

    fun cancelReply() {
        pendingChatMessageToReplyTo.value?.destroy()
        isPendingAnswer.value = false
    }

    private fun tickerFlowRecording() = flow {
        while (recorder.state == RecorderState.Running) {
            emit(Unit)
            delay(100)
        }
    }

    private fun tickerFlowPlaying() = flow {
        while (voiceRecordingPlayer.state == Player.State.Playing) {
            emit(Unit)
            delay(100)
        }
    }

    fun toggleVoiceRecording() {
        if (corePreferences.holdToRecordVoiceMessage) {
            // Disables click listener just in case, touch listener will be used instead
            return
        }

        if (isVoiceRecording.value == true) {
            stopVoiceRecording()
        } else {
            startVoiceRecording()
        }
    }

    fun startVoiceRecording() {
        if (!PermissionHelper.get().hasRecordAudioPermission()) {
            requestRecordAudioPermissionEvent.value = Event(true)
            return
        }

        if (voiceRecordAudioFocusRequest == null) {
            voiceRecordAudioFocusRequest = AppUtils.acquireAudioFocusForVoiceRecordingOrPlayback(
                coreContext.context
            )
        }

        when (recorder.state) {
            RecorderState.Running -> Log.w("[Chat Message Sending] Recorder is already recording")
            RecorderState.Paused -> {
                Log.w("[Chat Message Sending] Recorder isn't closed, resuming recording")
                recorder.start()
            }
            RecorderState.Closed -> {
                val extension = when (recorder.params.fileFormat) {
                    RecorderFileFormat.Mkv -> "mkv"
                    else -> "wav"
                }
                val tempFileName = "voice-recording-${System.currentTimeMillis()}.$extension"
                val file = FileUtils.getFileStoragePath(tempFileName)
                Log.w("[Chat Message Sending] Recorder is closed, starting recording in ${file.absoluteFile}")
                recorder.open(file.absolutePath)
                recorder.start()
            }
            else -> {}
        }

        val duration = recorder.duration
        voiceRecordingDuration.value = duration
        formattedDuration.value = SimpleDateFormat("mm:ss", Locale.getDefault()).format(duration) // duration is in ms

        isPendingVoiceRecord.value = true
        isVoiceRecording.value = true
        sendMessageEnabled.value = true

        tickerFlowRecording().onEach {
            val duration = recorder.duration
            voiceRecordingDuration.postValue(recorder.duration % voiceRecordingProgressBarMax)
            formattedDuration.postValue(SimpleDateFormat("mm:ss", Locale.getDefault()).format(duration)) // duration is in ms

            if (duration >= corePreferences.voiceRecordingMaxDuration) {
                withContext(Dispatchers.Main) {
                    Log.w("[Chat Message Sending] Max duration for voice recording exceeded (${corePreferences.voiceRecordingMaxDuration}ms), stopping.")
                    stopVoiceRecording()
                }
            }
        }.launchIn(scope)
    }

    fun cancelVoiceRecording() {
        if (recorder.state != RecorderState.Closed) {
            Log.i("[Chat Message Sending] Closing voice recorder")
            recorder.close()

            val path = recorder.file
            if (path != null) {
                Log.i("[Chat Message Sending] Deleting voice recording file: $path")
                FileUtils.deleteFile(path)
            }
        }

        val request = voiceRecordAudioFocusRequest
        if (request != null) {
            AppUtils.releaseAudioFocusForVoiceRecordingOrPlayback(coreContext.context, request)
            voiceRecordAudioFocusRequest = null
        }

        isPendingVoiceRecord.value = false
        isVoiceRecording.value = false
        sendMessageEnabled.value = textToSend.value.orEmpty().trim().isNotEmpty() == true || attachments.value?.isNotEmpty() == true

        if (!isPlayerClosed()) {
            stopVoiceRecordPlayer()
        }
    }

    fun stopVoiceRecording() {
        if (recorder.state == RecorderState.Running) {
            Log.i("[Chat Message Sending] Pausing / closing voice recorder")
            recorder.pause()
            recorder.close()
            voiceRecordingDuration.value = recorder.duration
        }

        val request = voiceRecordAudioFocusRequest
        if (request != null) {
            AppUtils.releaseAudioFocusForVoiceRecordingOrPlayback(coreContext.context, request)
            voiceRecordAudioFocusRequest = null
        }

        isVoiceRecording.value = false
        if (corePreferences.sendVoiceRecordingRightAway) {
            Log.i("[Chat Message Sending] Sending voice recording right away")
            sendMessage()
        }
    }

    fun playRecordedMessage() {
        if (isPlayerClosed()) {
            Log.w("[Chat Message Sending] Player closed, let's open it first")
            initVoiceRecordPlayer()
        }

        if (AppUtils.isMediaVolumeLow(coreContext.context)) {
            Toast.makeText(coreContext.context, R.string.chat_message_voice_recording_playback_low_volume, Toast.LENGTH_LONG).show()
        }

        if (voiceRecordAudioFocusRequest == null) {
            voiceRecordAudioFocusRequest = AppUtils.acquireAudioFocusForVoiceRecordingOrPlayback(
                coreContext.context
            )
        }

        voiceRecordingPlayer.start()
        isPlayingVoiceRecording.value = true

        tickerFlowPlaying().onEach {
            voiceRecordPlayingPosition.postValue(voiceRecordingPlayer.currentPosition)
        }.launchIn(scope)
    }

    fun pauseRecordedMessage() {
        Log.i("[Chat Message Sending] Pausing voice record")
        if (!isPlayerClosed()) {
            voiceRecordingPlayer.pause()
        }

        val request = voiceRecordAudioFocusRequest
        if (request != null) {
            AppUtils.releaseAudioFocusForVoiceRecordingOrPlayback(coreContext.context, request)
            voiceRecordAudioFocusRequest = null
        }

        isPlayingVoiceRecording.value = false
    }

    private fun initVoiceRecordPlayer() {
        Log.i("[Chat Message Sending] Creating player for voice record")
        // In case no headphones/headset is connected, use speaker sound card to play recordings, otherwise use earpiece
        // If none are available, default one will be used
        var headphonesCard: String? = null
        var speakerCard: String? = null
        var earpieceCard: String? = null
        for (device in coreContext.core.audioDevices) {
            if (device.hasCapability(AudioDevice.Capabilities.CapabilityPlay)) {
                when (device.type) {
                    AudioDevice.Type.Speaker -> {
                        speakerCard = device.id
                    }
                    AudioDevice.Type.Earpiece -> {
                        earpieceCard = device.id
                    }
                    AudioDevice.Type.Headphones, AudioDevice.Type.Headset -> {
                        headphonesCard = device.id
                    }
                    else -> {}
                }
            }
        }
        Log.i("[Chat Message Sending] Found headset/headphones sound card [$headphonesCard], speaker sound card [$speakerCard] and earpiece sound card [$earpieceCard]")

        val localPlayer = coreContext.core.createLocalPlayer(headphonesCard ?: speakerCard ?: earpieceCard, null, null)
        if (localPlayer != null) {
            voiceRecordingPlayer = localPlayer
        } else {
            Log.e("[Chat Message Sending] Couldn't create local player!")
            return
        }
        voiceRecordingPlayer.addListener(playerListener)

        val path = recorder.file
        if (path != null) {
            voiceRecordingPlayer.open(path)
            // Update recording duration using player value to ensure proper progress bar animation
            voiceRecordingDuration.value = voiceRecordingPlayer.duration
        }
    }

    private fun stopVoiceRecordPlayer() {
        if (!isPlayerClosed()) {
            Log.i("[Chat Message Sending] Stopping voice record")
            voiceRecordingPlayer.pause()
            voiceRecordingPlayer.seek(0)
            voiceRecordPlayingPosition.value = 0
            voiceRecordingPlayer.close()
        }

        val request = voiceRecordAudioFocusRequest
        if (request != null) {
            AppUtils.releaseAudioFocusForVoiceRecordingOrPlayback(coreContext.context, request)
            voiceRecordAudioFocusRequest = null
        }

        isPlayingVoiceRecording.value = false
    }

    private fun isPlayerClosed(): Boolean {
        return !this::voiceRecordingPlayer.isInitialized || voiceRecordingPlayer.state == Player.State.Closed
    }

    private fun updateChatRoomReadOnlyState() {
        isReadOnly.value = chatRoom.isReadOnly || (chatRoom.hasCapability(ChatRoomCapabilities.Conference.toInt()) && chatRoom.participants.isEmpty())
    }
}
