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

import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.core.ChatMessage
import org.linphone.core.ChatRoom
import org.linphone.core.ChatRoomListenerStub
import org.linphone.core.EventLog
import org.linphone.core.Factory
import org.linphone.core.tools.Log
import org.linphone.ui.main.chat.model.ChatMessageModel
import org.linphone.ui.main.chat.model.FileModel
import org.linphone.ui.main.chat.model.ParticipantModel
import org.linphone.utils.Event
import org.linphone.utils.FileUtils
import org.linphone.utils.LinphoneUtils

class SendMessageInConversationViewModel @UiThread constructor() : ViewModel() {
    companion object {
        private const val TAG = "[Send Message In Conversation ViewModel]"
    }

    val textToSend = MutableLiveData<String>()

    val isEmojiPickerOpen = MutableLiveData<Boolean>()

    val isParticipantsListOpen = MutableLiveData<Boolean>()

    val participants = MutableLiveData<ArrayList<ParticipantModel>>()

    val isFileAttachmentsListOpen = MutableLiveData<Boolean>()

    val attachments = MutableLiveData<ArrayList<FileModel>>()

    val isReplying = MutableLiveData<Boolean>()

    val isReplyingTo = MutableLiveData<String>()

    val isReplyingToMessage = MutableLiveData<String>()

    val voiceRecordingInProgress = MutableLiveData<Boolean>()

    val requestKeyboardHidingEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val emojiToAddEvent: MutableLiveData<Event<String>> by lazy {
        MutableLiveData<Event<String>>()
    }

    val participantUsernameToAddEvent: MutableLiveData<Event<String>> by lazy {
        MutableLiveData<Event<String>>()
    }

    lateinit var chatRoom: ChatRoom

    private var chatMessageToReplyTo: ChatMessage? = null

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
        isEmojiPickerOpen.value = false
    }

    override fun onCleared() {
        super.onCleared()

        viewModelScope.launch {
            for (file in attachments.value.orEmpty()) {
                file.deleteFile()
            }
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
    }

    @UiThread
    fun insertEmoji(emoji: String) {
        emojiToAddEvent.value = Event(emoji)
    }

    @UiThread
    fun replyToMessage(model: ChatMessageModel) {
        coreContext.postOnCoreThread {
            val message = model.chatMessage
            Log.i("$TAG Pending reply to chat message [${message.messageId}]")
            chatMessageToReplyTo = message
            isReplyingTo.postValue(model.avatarModel.friend.name)
            isReplyingToMessage.postValue(LinphoneUtils.getTextDescribingMessage(message))
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
                content.filePath = attachment.file // Let the file body handler take care of the upload

                message.addFileContent(content)
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

            // Warning: do not delete files
            val attachmentsList = arrayListOf<FileModel>()
            attachments.postValue(attachmentsList)

            chatMessageToReplyTo = null
        }
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

        isFileAttachmentsListOpen.value = false
    }

    @UiThread
    fun addAttachment(file: String) {
        val list = arrayListOf<FileModel>()
        list.addAll(attachments.value.orEmpty())
        val model = FileModel(file) { file ->
            removeAttachment(file)
        }
        list.add(model)
        attachments.value = list

        if (list.isNotEmpty()) {
            isFileAttachmentsListOpen.value = true
        }
    }

    @UiThread
    fun removeAttachment(file: String, delete: Boolean = true) {
        val list = arrayListOf<FileModel>()
        list.addAll(attachments.value.orEmpty())
        val found = list.find {
            it.file == file
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

        if (list.isEmpty()) {
            isFileAttachmentsListOpen.value = false
        }
    }

    @UiThread
    fun startVoiceMessageRecording() {
        voiceRecordingInProgress.value = true
    }

    @UiThread
    fun stopVoiceMessageRecording() {
    }

    @UiThread
    fun cancelVoiceMessageRecording() {
        voiceRecordingInProgress.value = false
    }

    @UiThread
    fun playVoiceMessageRecording() {
    }

    @UiThread
    fun pauseVoiceMessageRecording() {
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
}
