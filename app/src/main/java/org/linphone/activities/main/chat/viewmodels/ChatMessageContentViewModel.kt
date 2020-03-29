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
import org.linphone.core.ChatMessage
import org.linphone.core.Content
import org.linphone.core.tools.Log
import org.linphone.utils.FileUtils

class ChatMessageContentViewModel(
    val content: Content,
    private val chatMessage: ChatMessage,
    private val listener: OnContentClickedListener?
) : ViewModel() {
    val isImage = MutableLiveData<Boolean>()

    val downloadable = MutableLiveData<Boolean>()

    val downloadEnabled = MutableLiveData<Boolean>()

    val isAlone: Boolean
        get() {
            var count = 0
            for (content in chatMessage.contents) {
                if (content.isFileTransfer || content.isFile) {
                    count += 1
                }
            }
            return count == 1
        }

    init {
        if (content.isFile || (content.isFileTransfer && chatMessage.isOutgoing)) {
            downloadable.value = content.filePath.isEmpty()

            if (content.filePath.isNotEmpty()) {
                Log.i("[Content] Found displayable content: ${content.filePath}")
                isImage.value = FileUtils.isExtensionImage(content.filePath)
            } else {
                Log.w("[Content] Found content with empty path...")
                isImage.value = false
            }
        } else {
            Log.i("[Content] Found downloadable content: ${content.name}")
            downloadable.value = true
            isImage.value = false
        }

        downloadEnabled.value = downloadable.value
    }

    fun download() {
        if (content.isFileTransfer && (content.filePath == null || content.filePath.isEmpty())) {
            val file = FileUtils.getFileStoragePath(content.name)
            content.filePath = file.path
            downloadEnabled.value = false

            Log.i("[Content] Started downloading ${content.name} into ${content.filePath}")
            chatMessage.downloadContent(content)
        }
    }

    fun openFile() {
        listener?.onContentClicked(content.filePath)
    }
}

interface OnContentClickedListener {
    fun onContentClicked(path: String)
}
