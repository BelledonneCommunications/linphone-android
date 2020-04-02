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

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import java.io.File
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.core.*
import org.linphone.utils.FileUtils

class ChatMessageSendingViewModelFactory(private val chatRoom: ChatRoom) :
    ViewModelProvider.NewInstanceFactory() {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
        return ChatMessageSendingViewModel(chatRoom) as T
    }
}

class ChatMessageSendingViewModel(private val chatRoom: ChatRoom) : ViewModel() {
    var temporaryFileUploadPath: File? = null

    val attachments = MutableLiveData<ArrayList<ChatMessageAttachmentViewModel>>()

    val attachFileEnabled = MutableLiveData<Boolean>()

    val sendMessageEnabled = MutableLiveData<Boolean>()

    val isReadOnly = MutableLiveData<Boolean>()

    var textToSend = MutableLiveData<String>()

    init {
        attachments.value = arrayListOf()

        attachFileEnabled.value = true
        sendMessageEnabled.value = false
        isReadOnly.value = chatRoom.hasBeenLeft()
    }

    fun onTextToSendChanged(value: String) {
        sendMessageEnabled.value = value.isNotEmpty() || attachments.value?.isNotEmpty() ?: false
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
        val list = arrayListOf<ChatMessageAttachmentViewModel>()
        list.addAll(attachments.value.orEmpty())
        list.add(ChatMessageAttachmentViewModel(path, FileUtils.isExtensionImage(path)) {
            removeAttachment(it)
        })
        attachments.value = list

        sendMessageEnabled.value = textToSend.value.orEmpty().isNotEmpty() || list.isNotEmpty()
        if (!corePreferences.allowMultipleFilesAndTextInSameMessage) {
            attachFileEnabled.value = false
        }
    }

    private fun removeAttachment(attachment: ChatMessageAttachmentViewModel) {
        val list = arrayListOf<ChatMessageAttachmentViewModel>()
        list.addAll(attachments.value.orEmpty())
        list.remove(attachment)
        attachments.value = list

        sendMessageEnabled.value = textToSend.value.orEmpty().isNotEmpty() || list.isNotEmpty()
        if (!corePreferences.allowMultipleFilesAndTextInSameMessage) {
            attachFileEnabled.value = list.isEmpty()
        }
    }

    fun sendMessage() {
        val isBasicChatRoom: Boolean = chatRoom.hasCapability(ChatRoomCapabilities.Basic.toInt())
        val message: ChatMessage = chatRoom.createEmptyMessage()

        if (textToSend.value.orEmpty().isNotEmpty()) {
            message.addTextContent(textToSend.value)
        }

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

            if (isBasicChatRoom) {
                val fileMessage: ChatMessage = chatRoom.createFileTransferMessage(content)
                fileMessage.send()
            } else {
                message.addFileContent(content)
            }
        }

        if (message.contents.isNotEmpty()) {
            message.send()
        }

        attachments.value = arrayListOf()
    }

    fun transferMessage(chatMessage: ChatMessage) {
        val message = chatRoom.createForwardMessage(chatMessage)
        message?.send()
    }
}
