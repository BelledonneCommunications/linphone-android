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
package com.naminfo.ui.fileviewer.viewmodel

import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.lifecycle.MutableLiveData
import org.linphone.core.tools.Log
import com.naminfo.ui.main.chat.model.FileModel
import com.naminfo.ui.main.chat.viewmodel.AbstractConversationViewModel
import com.naminfo.utils.FileUtils
import com.naminfo.utils.LinphoneUtils

class MediaListViewModel
    @UiThread
    constructor() : AbstractConversationViewModel() {
    companion object {
        private const val TAG = "[Media List ViewModel]"
    }

    val mediaList = MutableLiveData<List<FileModel>>()

    val currentlyDisplayedFileName = MutableLiveData<String>()

    val currentlyDisplayedFileDateTime = MutableLiveData<String>()

    val isCurrentlyDisplayedFileFromEphemeralMessage = MutableLiveData<Boolean>()

    private lateinit var temporaryModel: FileModel

    override fun beforeNotifyingChatRoomFound(sameOne: Boolean) {
        loadMediaList()
    }

    override fun onCleared() {
        super.onCleared()

        mediaList.value.orEmpty().forEach(FileModel::destroy)
        if (::temporaryModel.isInitialized) {
            temporaryModel.destroy()
        }
    }

    @UiThread
    fun initTempModel(path: String, timestamp: Long, isEncrypted: Boolean, originalPath: String, isFromEphemeralMessage: Boolean) {
        val name = FileUtils.getNameFromFilePath(path)
        val model = FileModel(path, name, 0, timestamp, isEncrypted, originalPath, isFromEphemeralMessage)
        temporaryModel = model
        Log.i("$TAG Temporary model for file [$name] created, use it while other media for conversation are being loaded")
        mediaList.postValue(arrayListOf(model))
    }

    @WorkerThread
    private fun loadMediaList() {
        val list = arrayListOf<FileModel>()
        val chatRoomId = LinphoneUtils.getConversationId(chatRoom)
        Log.i("$TAG Loading media contents for conversation [$chatRoomId]")

        val media = chatRoom.mediaContents
        Log.i("$TAG [${media.size}] media have been fetched")

        var tempFileModelFound = false
        var tempFilePath = ""
        if (::temporaryModel.isInitialized) {
            tempFilePath = temporaryModel.path
        }

        for (mediaContent in media) {
            // Do not display voice recordings here, even if they are media file
            if (mediaContent.isVoiceRecording) continue

            val isEncrypted = mediaContent.isFileEncrypted
            val originalPath = mediaContent.filePath.orEmpty()
            val path = if (isEncrypted) {
                Log.d(
                    "$TAG [VFS] Content is encrypted, requesting plain file path for file [${mediaContent.filePath}]"
                )
                val exportedPath = mediaContent.exportPlainFile()
                Log.i("$TAG Media original path is [$originalPath], newly exported plain file path is [$exportedPath]")
                exportedPath
            } else {
                originalPath
            }

            val name = mediaContent.name.orEmpty()
            val size = mediaContent.size.toLong()
            val timestamp = mediaContent.creationTimestamp
            if (path.isNotEmpty() && name.isNotEmpty()) {
                val messageId = mediaContent.relatedChatMessageId
                val ephemeral = if (messageId != null) {
                    val chatMessage = chatRoom.findMessage(messageId)
                    if (chatMessage == null) {
                        Log.w("$TAG Failed to find message using ID [$messageId] related to this content, can't get real info about being related to ephemeral message")
                    }
                    chatMessage?.isEphemeral ?: chatRoom.isEphemeralEnabled
                } else {
                    Log.e("$TAG No chat message ID related to this content, can't get real info about being related to ephemeral message")
                    chatRoom.isEphemeralEnabled
                }

                val model = FileModel(path, name, size, timestamp, isEncrypted, originalPath, ephemeral)
                list.add(model)
            } else {
                Log.w("$TAG Skipping content because either name [$name] or path [$path] is empty")
            }

            if (tempFilePath.isNotEmpty() && !tempFileModelFound) {
                if (path == tempFilePath || (isEncrypted && originalPath == temporaryModel.originalPath)) {
                    tempFileModelFound = true
                }
            }
        }
        Log.i("$TAG [${list.size}] media have been processed")

        if (tempFileModelFound || tempFilePath.isEmpty()) {
            mediaList.postValue(list)
        } else {
            Log.w("$TAG Temporary file [$tempFilePath] not found in processed media, keeping only temporary model")
        }
    }
}
