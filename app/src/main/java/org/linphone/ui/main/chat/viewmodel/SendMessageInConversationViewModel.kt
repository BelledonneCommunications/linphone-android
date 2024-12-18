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
package org.linphone.ui.main.chat.viewmodel

import android.Manifest
import android.content.pm.PackageManager
import android.text.Spannable
import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.core.app.ActivityCompat
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.media.AudioFocusRequestCompat
import java.text.SimpleDateFormat
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.R
import org.linphone.core.ChatMessage
import org.linphone.core.ChatRoom
import org.linphone.core.ChatRoomListenerStub
import org.linphone.core.EventLog
import org.linphone.core.Factory
import org.linphone.core.MediaFileFormat
import org.linphone.core.Player
import org.linphone.core.PlayerListener
import org.linphone.core.Recorder
import org.linphone.core.tools.Log
import org.linphone.ui.GenericViewModel
import org.linphone.ui.main.chat.model.FileModel
import org.linphone.ui.main.chat.model.MessageModel
import org.linphone.ui.main.chat.model.ParticipantModel
import org.linphone.utils.AudioUtils
import org.linphone.utils.Event
import org.linphone.utils.FileUtils
import org.linphone.utils.LinphoneUtils

class SendMessageInConversationViewModel
    @UiThread
    constructor() : GenericViewModel() {
    companion object {
        private const val TAG = "[Send Message In Conversation ViewModel]"

        const val MAX_FILES_TO_ATTACH = 12
    }

    val textToSend = MutableLiveData<String>()

    val isFileTransferServerAvailable = MutableLiveData<Boolean>()

    val isEmojiPickerOpen = MutableLiveData<Boolean>()

    val areFilePickersOpen = MutableLiveData<Boolean>()

    val isParticipantsListOpen = MutableLiveData<Boolean>()

    val participants = MutableLiveData<ArrayList<ParticipantModel>>()

    val isFileAttachmentsListOpen = MutableLiveData<Boolean>()

    val maxNumberOfAttachmentsReached = MutableLiveData<Boolean>()

    val attachments = MutableLiveData<ArrayList<FileModel>>()

    val isReplying = MutableLiveData<Boolean>()

    val isReplyingTo = MutableLiveData<String>()

    val isReplyingToMessage = MutableLiveData<Spannable>()

    val isKeyboardOpen = MutableLiveData<Boolean>()

    val isCallConversation = MutableLiveData<Boolean>()

    val isVoiceRecording = MutableLiveData<Boolean>()

    val isVoiceRecordingInProgress = MutableLiveData<Boolean>()

    val voiceRecordingDuration = MutableLiveData<Int>()

    val formattedVoiceRecordingDuration = MutableLiveData<String>()

    val isPlayingVoiceRecord = MutableLiveData<Boolean>()

    val voiceRecordPlayerPosition = MutableLiveData<Int>()

    private lateinit var voiceRecordPlayer: Player

    private val playerListener = PlayerListener {
        Log.i("$TAG End of file reached")
        stopVoiceRecordPlayer()
    }

    val requestKeyboardHidingEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val emojiToAddEvent: MutableLiveData<Event<String>> by lazy {
        MutableLiveData<Event<String>>()
    }

    val participantUsernameToAddEvent: MutableLiveData<Event<String>> by lazy {
        MutableLiveData<Event<String>>()
    }

    val askRecordAudioPermissionEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    lateinit var chatRoom: ChatRoom

    private var chatMessageToReplyTo: ChatMessage? = null

    private lateinit var voiceMessageRecorder: Recorder

    private var voiceRecordAudioFocusRequest: AudioFocusRequestCompat? = null

    private val chatRoomListener = object : ChatRoomListenerStub() {
        @WorkerThread
        override fun onParticipantAdded(chatRoom: ChatRoom, eventLog: EventLog) {
            computeParticipantsList()
        }

        @WorkerThread
        override fun onParticipantRemoved(chatRoom: ChatRoom, eventLog: EventLog) {
            computeParticipantsList()
        }
    }

    init {
        coreContext.postOnCoreThread { core ->
            isFileTransferServerAvailable.postValue(!core.fileTransferServer.isNullOrEmpty())
        }

        isEmojiPickerOpen.value = false
        areFilePickersOpen.value = false
        isPlayingVoiceRecord.value = false
        isCallConversation.value = false
        maxNumberOfAttachmentsReached.value = false
    }

    override fun onCleared() {
        super.onCleared()

        viewModelScope.launch {
            for (file in attachments.value.orEmpty()) {
                file.deleteFile()
                file.destroy()
            }
        }

        if (::voiceMessageRecorder.isInitialized) {
            if (voiceMessageRecorder.state != Recorder.State.Closed) {
                voiceMessageRecorder.close()
            }
        }

        if (::voiceRecordPlayer.isInitialized) {
            stopVoiceRecordPlayer()
            voiceRecordPlayer.removeListener(playerListener)
        }

        coreContext.postOnCoreThread {
            if (::chatRoom.isInitialized) {
                chatRoom.removeListener(chatRoomListener)
            }
        }
    }

    @UiThread
    fun configureChatRoom(room: ChatRoom) {
        chatRoom = room
        coreContext.postOnCoreThread {
            chatRoom.addListener(chatRoomListener)
            computeParticipantsList()
        }
    }

    @UiThread
    fun toggleEmojiPickerVisibility() {
        isEmojiPickerOpen.value = isEmojiPickerOpen.value == false
        if (isEmojiPickerOpen.value == true) {
            requestKeyboardHidingEvent.value = Event(true)
        }
        closeFilePickerBottomSheet()
        closeParticipantsList()
    }

    @UiThread
    fun insertEmoji(emoji: String) {
        emojiToAddEvent.value = Event(emoji)
    }

    @UiThread
    fun toggleFilePickersVisibility() {
        areFilePickersOpen.value = areFilePickersOpen.value == false
        isEmojiPickerOpen.value = false
        closeParticipantsList()
    }

    @UiThread
    fun closeFilePickerBottomSheet() {
        areFilePickersOpen.value = false
    }

    @UiThread
    fun replyToMessage(model: MessageModel) {
        coreContext.postOnCoreThread {
            val message = model.chatMessage
            Log.i("$TAG Pending reply to message [${message.messageId}]")
            chatMessageToReplyTo = message
            isReplyingTo.postValue(model.avatarModel.value?.friend?.name)
            isReplyingToMessage.postValue(LinphoneUtils.getFormattedTextDescribingMessage(message))
            isReplying.postValue(true)
        }
    }

    @UiThread
    fun cancelReply() {
        Log.i("$TAG Cancelling reply")
        isReplying.value = false
        chatMessageToReplyTo = null
    }

    @UiThread
    fun sendMessage() {
        coreContext.postOnCoreThread {
            val messageToReplyTo = chatMessageToReplyTo
            val message = if (messageToReplyTo != null) {
                Log.i("$TAG Sending message as reply to [${messageToReplyTo.messageId}]")
                chatRoom.createReplyMessage(messageToReplyTo)
            } else {
                chatRoom.createEmptyMessage()
            }

            val toSend = textToSend.value.orEmpty().trim()
            if (toSend.isNotEmpty()) {
                message.addUtf8TextContent(toSend)
            }

            if (isVoiceRecording.value == true && voiceMessageRecorder.file != null) {
                stopVoiceRecorder()
                val content = voiceMessageRecorder.createContent()
                if (content != null) {
                    Log.i(
                        "$TAG Voice recording content created, file name is ${content.name} and duration is ${content.fileDuration}"
                    )
                    message.addContent(content)
                } else {
                    Log.e("$TAG Voice recording content couldn't be created!")
                }
            } else {
                for (attachment in attachments.value.orEmpty()) {
                    val content = Factory.instance().createContent()

                    content.type = when (attachment.mimeType) {
                        FileUtils.MimeType.Image -> "image"
                        FileUtils.MimeType.Audio -> "audio"
                        FileUtils.MimeType.Video -> "video"
                        FileUtils.MimeType.Pdf -> "application"
                        FileUtils.MimeType.PlainText -> "text"
                        else -> "file"
                    }
                    content.subtype = if (attachment.mimeType == FileUtils.MimeType.PlainText) {
                        "plain"
                    } else {
                        FileUtils.getExtensionFromFileName(attachment.fileName)
                    }
                    content.name = attachment.fileName
                    // Let the file body handler take care of the upload
                    content.filePath = attachment.path

                    message.addFileContent(content)
                }
            }

            if (message.contents.isNotEmpty()) {
                Log.i("$TAG Sending message")
                message.send()
            }

            Log.i("$TAG Message sent, re-setting defaults")
            textToSend.postValue("")
            isReplying.postValue(false)
            isFileAttachmentsListOpen.postValue(false)
            isParticipantsListOpen.postValue(false)
            isEmojiPickerOpen.postValue(false)

            if (::voiceMessageRecorder.isInitialized) {
                stopVoiceRecorder()
            }
            isVoiceRecording.postValue(false)

            // Warning: do not delete files
            val attachmentsList = arrayListOf<FileModel>()
            attachments.postValue(attachmentsList)

            chatMessageToReplyTo = null
        }
    }

    @UiThread
    fun openParticipantsList() {
        isParticipantsListOpen.value = true
        isEmojiPickerOpen.value = false
        closeFilePickerBottomSheet()
    }

    @UiThread
    fun closeParticipantsList() {
        isParticipantsListOpen.value = false
    }

    @UiThread
    fun closeFileAttachmentsList() {
        viewModelScope.launch {
            for (file in attachments.value.orEmpty()) {
                file.deleteFile()
            }
        }
        val list = arrayListOf<FileModel>()
        attachments.value = list
        maxNumberOfAttachmentsReached.value = false

        isFileAttachmentsListOpen.value = false
    }

    @UiThread
    fun addAttachment(file: String) {
        if (attachments.value.orEmpty().size >= MAX_FILES_TO_ATTACH) {
            Log.w(
                "$TAG Max number of attachments [$MAX_FILES_TO_ATTACH] reached, file [$file] won't be attached"
            )
            showRedToastEvent.postValue(
                Event(
                    Pair(
                        R.string.conversation_maximum_number_of_attachments_reached,
                        R.drawable.warning_circle
                    )
                )
            )
            viewModelScope.launch {
                Log.i("$TAG Deleting temporary file [$file]")
                FileUtils.deleteFile(file)
            }
            return
        }

        val list = arrayListOf<FileModel>()
        list.addAll(attachments.value.orEmpty())

        val fileName = FileUtils.getNameFromFilePath(file)
        val timestamp = System.currentTimeMillis() / 1000
        val model = FileModel(file, fileName, 0, timestamp, false, file) { model ->
            removeAttachment(model.path)
        }

        list.add(model)
        attachments.value = list
        maxNumberOfAttachmentsReached.value = list.size >= MAX_FILES_TO_ATTACH

        if (list.isNotEmpty()) {
            isFileAttachmentsListOpen.value = true
            Log.i("$TAG [${list.size}] attachment(s) added")
        } else {
            Log.w("$TAG No attachment to display!")
        }
    }

    @UiThread
    fun removeAttachment(file: String, delete: Boolean = true) {
        val list = arrayListOf<FileModel>()
        list.addAll(attachments.value.orEmpty())
        val found = list.find {
            it.path == file
        }
        if (found != null) {
            if (delete) {
                viewModelScope.launch {
                    found.deleteFile()
                }
            }
            list.remove(found)
        } else {
            Log.w("$TAG Failed to find file attachment matching [$file]")
        }
        attachments.value = list
        maxNumberOfAttachmentsReached.value = list.size >= MAX_FILES_TO_ATTACH

        if (list.isEmpty) {
            isFileAttachmentsListOpen.value = false
        }
    }

    @UiThread
    fun forwardMessage(toForward: MessageModel) {
        coreContext.postOnCoreThread {
            if (::chatRoom.isInitialized) {
                val messageToForward = toForward.chatMessage
                val forwardedMessage = chatRoom.createForwardMessage(messageToForward)
                Log.i("$TAG Sending forwarded message")
                forwardedMessage.send()

                showGreenToastEvent.postValue(
                    Event(Pair(R.string.conversation_message_forwarded_toast, R.drawable.forward))
                )
            }
        }
    }

    @UiThread
    fun startVoiceMessageRecording() {
        if (ActivityCompat.checkSelfPermission(
                coreContext.context,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(
                "$TAG Can't start voice message recording, RECORD_AUDIO permission wasn't granted yet"
            )
            askRecordAudioPermissionEvent.postValue(Event(true))
            return
        }

        isEmojiPickerOpen.value = false
        closeFilePickerBottomSheet()
        closeParticipantsList()

        coreContext.postOnCoreThread {
            requestKeyboardHidingEvent.postValue(Event(true))
            isVoiceRecording.postValue(true)
            initVoiceRecorder()

            isVoiceRecordingInProgress.postValue(true)
            startVoiceRecorder()
        }
    }

    @UiThread
    fun stopVoiceMessageRecording() {
        coreContext.postOnCoreThread {
            stopVoiceRecorder()
        }
    }

    @UiThread
    fun cancelVoiceMessageRecording() {
        coreContext.postOnCoreThread {
            stopVoiceRecorder()

            val path = voiceMessageRecorder.file
            if (path != null) {
                viewModelScope.launch {
                    Log.i("$TAG Deleting voice recording file: $path")
                    FileUtils.deleteFile(path)
                }
            }

            isVoiceRecording.postValue(false)
        }
    }

    @UiThread
    fun togglePlayPauseVoiceRecord() {
        coreContext.postOnCoreThread {
            if (isPlayingVoiceRecord.value == false) {
                startVoiceRecordPlayer()
            } else {
                pauseVoiceRecordPlayer()
            }
        }
    }

    @WorkerThread
    private fun computeParticipantsList() {
        val participantsList = arrayListOf<ParticipantModel>()

        for (participant in chatRoom.participants) {
            val model = ParticipantModel(participant.address, onClicked = { clicked ->
                Log.i("$TAG Clicked on participant [${clicked.sipUri}]")
                coreContext.postOnCoreThread {
                    val username = clicked.address.username
                    if (!username.isNullOrEmpty()) {
                        participantUsernameToAddEvent.postValue(Event(username))
                    }
                }
            })
            participantsList.add(model)
        }

        participants.postValue(participantsList)
    }

    @WorkerThread
    private fun initVoiceRecorder() {
        val core = coreContext.core
        Log.i("$TAG Creating voice message recorder")
        val recorderParams = core.createRecorderParams()
        recorderParams.fileFormat = MediaFileFormat.Mkv

        val recordingAudioDevice = AudioUtils.getAudioRecordingDeviceIdForVoiceMessage()
        recorderParams.audioDevice = recordingAudioDevice
        Log.i(
            "$TAG Using device ${recorderParams.audioDevice?.id} to make the voice message recording"
        )

        voiceMessageRecorder = core.createRecorder(recorderParams)
        Log.i("$TAG Voice message recorder created")
    }

    @WorkerThread
    private fun startVoiceRecorder() {
        if (voiceRecordAudioFocusRequest == null) {
            Log.i("$TAG Requesting audio focus for voice message recording")
            voiceRecordAudioFocusRequest = AudioUtils.acquireAudioFocusForVoiceRecordingOrPlayback(
                coreContext.context
            )
        }

        when (voiceMessageRecorder.state) {
            Recorder.State.Running -> Log.w("$TAG Recorder is already recording")
            Recorder.State.Paused -> {
                Log.w("$TAG Recorder is paused, resuming recording")
                voiceMessageRecorder.start()
            }
            Recorder.State.Closed -> {
                val extension = when (voiceMessageRecorder.params.fileFormat) {
                    MediaFileFormat.Smff -> "smff"
                    MediaFileFormat.Mkv -> "mka"
                    else -> "wav"
                }
                val tempFileName = "voice-recording-${System.currentTimeMillis()}.$extension"
                val file = FileUtils.getFileStoragePath(tempFileName)
                Log.w(
                    "$TAG Recorder is closed, starting recording in ${file.absoluteFile}"
                )
                voiceMessageRecorder.open(file.absolutePath)
                voiceMessageRecorder.start()
            }
            else -> {}
        }

        val duration = voiceMessageRecorder.duration
        val formattedDuration = SimpleDateFormat("mm:ss", Locale.getDefault()).format(duration) // duration is in ms
        formattedVoiceRecordingDuration.postValue(formattedDuration)

        val maxVoiceRecordDuration = corePreferences.voiceRecordingMaxDuration
        recorderTickerFlow().onEach {
            coreContext.postOnCoreThread {
                val duration = voiceMessageRecorder.duration
                val formattedDuration = SimpleDateFormat("mm:ss", Locale.getDefault()).format(
                    duration
                ) // duration is in ms
                formattedVoiceRecordingDuration.postValue(formattedDuration)

                if (duration >= maxVoiceRecordDuration) {
                    Log.w(
                        "$TAG Max duration for voice recording exceeded (${maxVoiceRecordDuration}ms), stopping."
                    )
                    stopVoiceRecorder()
                    showRedToastEvent.postValue(
                        Event(
                            Pair(
                                R.string.conversation_voice_recording_max_duration_reached_toast,
                                R.drawable.warning_circle
                            )
                        )
                    )
                }
            }
        }.launchIn(viewModelScope)
    }

    @WorkerThread
    private fun stopVoiceRecorder() {
        if (voiceMessageRecorder.state == Recorder.State.Running) {
            Log.i("$TAG Closing voice recorder")
            voiceMessageRecorder.pause()
            voiceMessageRecorder.close()
        }

        val request = voiceRecordAudioFocusRequest
        if (request != null) {
            Log.i("$TAG Releasing voice recording audio focus request")
            AudioUtils.releaseAudioFocusForVoiceRecordingOrPlayback(
                coreContext.context,
                request
            )
            voiceRecordAudioFocusRequest = null
        }

        isVoiceRecordingInProgress.postValue(false)
    }

    @WorkerThread
    private fun initVoiceRecordPlayer() {
        Log.i("$TAG Creating player for voice record")

        val playbackSoundCard = AudioUtils.getAudioPlaybackDeviceIdForCallRecordingOrVoiceMessage()
        Log.i(
            "$TAG Using device $playbackSoundCard to make the voice message playback"
        )

        val localPlayer = coreContext.core.createLocalPlayer(playbackSoundCard, null, null)
        if (localPlayer != null) {
            voiceRecordPlayer = localPlayer
        } else {
            Log.e("$TAG Couldn't create local player!")
            return
        }
        voiceRecordPlayer.addListener(playerListener)
        Log.i("$TAG Voice record player created")

        val path = voiceMessageRecorder.file
        if (path != null) {
            Log.i("$TAG Opening voice record file [$path]")
            voiceRecordPlayer.open(path)
            voiceRecordingDuration.postValue(voiceRecordPlayer.duration)
        }
    }

    @WorkerThread
    private fun startVoiceRecordPlayer() {
        if (isPlayerClosed()) {
            Log.w("$TAG Player closed, let's open it first")
            initVoiceRecordPlayer()
        }

        val context = coreContext.context
        val lowMediaVolume = AudioUtils.isMediaVolumeLow(context)
        if (lowMediaVolume) {
            Log.w("$TAG Media volume is low, notifying user as they may not hear voice message")
            showRedToastEvent.postValue(
                Event(
                    Pair(R.string.media_playback_low_volume_warning_toast, R.drawable.speaker_slash)
                )
            )
        }

        if (voiceRecordAudioFocusRequest == null) {
            voiceRecordAudioFocusRequest = AudioUtils.acquireAudioFocusForVoiceRecordingOrPlayback(
                context
            )
        }

        Log.i("$TAG Playing voice record")
        voiceRecordPlayer.start()
        isPlayingVoiceRecord.postValue(true)

        playerTickerFlow().onEach {
            coreContext.postOnCoreThread {
                voiceRecordPlayerPosition.postValue(voiceRecordPlayer.currentPosition)
            }
        }.launchIn(viewModelScope)
    }

    @WorkerThread
    private fun pauseVoiceRecordPlayer() {
        if (!isPlayerClosed()) {
            Log.i("$TAG Pausing voice record")
            voiceRecordPlayer.pause()
        }

        val request = voiceRecordAudioFocusRequest
        if (request != null) {
            AudioUtils.releaseAudioFocusForVoiceRecordingOrPlayback(
                coreContext.context,
                request
            )
            voiceRecordAudioFocusRequest = null
        }

        isPlayingVoiceRecord.postValue(false)
    }

    @WorkerThread
    private fun stopVoiceRecordPlayer() {
        if (!isPlayerClosed()) {
            Log.i("$TAG Stopping voice record")
            voiceRecordPlayer.pause()
            voiceRecordPlayer.seek(0)
            voiceRecordPlayerPosition.postValue(0)
            voiceRecordPlayer.close()
        }

        voiceRecordPlayerPosition.postValue(0)
        isPlayingVoiceRecord.postValue(false)

        val request = voiceRecordAudioFocusRequest
        if (request != null) {
            AudioUtils.releaseAudioFocusForVoiceRecordingOrPlayback(
                coreContext.context,
                request
            )
            voiceRecordAudioFocusRequest = null
        }

        isPlayingVoiceRecord.postValue(false)
    }

    @WorkerThread
    private fun isPlayerClosed(): Boolean {
        return !::voiceRecordPlayer.isInitialized || voiceRecordPlayer.state == Player.State.Closed
    }

    private fun recorderTickerFlow() = flow {
        while (isVoiceRecordingInProgress.value == true) {
            emit(Unit)
            delay(500)
        }
    }

    private fun playerTickerFlow() = flow {
        while (isPlayingVoiceRecord.value == true) {
            emit(Unit)
            delay(10)
        }
    }
}
