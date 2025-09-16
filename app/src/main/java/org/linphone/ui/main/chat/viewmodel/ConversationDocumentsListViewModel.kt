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
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.core.Content
import org.linphone.core.tools.Log
import org.linphone.ui.main.chat.model.FileModel
import org.linphone.utils.Event
import org.linphone.utils.LinphoneUtils
import kotlin.math.min

class ConversationDocumentsListViewModel
    @UiThread
    constructor() : AbstractConversationViewModel() {
    companion object {
        private const val TAG = "[Conversation Documents List ViewModel]"

        private const val CONTENTS_PER_PAGE = 20
    }

    val documentsList = MutableLiveData<List<FileModel>>()

    val operationInProgress = MutableLiveData<Boolean>()

    val openDocumentEvent: MutableLiveData<Event<FileModel>> by lazy {
        MutableLiveData<Event<FileModel>>()
    }

    private var totalDocumentsCount: Int = -1

    @WorkerThread
    override fun afterNotifyingChatRoomFound(sameOne: Boolean) {
        loadDocumentsList()
    }

    override fun onCleared() {
        super.onCleared()

        documentsList.value.orEmpty().forEach(FileModel::destroy)
    }

    @WorkerThread
    private fun loadDocumentsList() {
        operationInProgress.postValue(true)
        Log.i(
            "$TAG Loading document contents for conversation [${LinphoneUtils.getConversationId(
                chatRoom
            )}]"
        )

        totalDocumentsCount = chatRoom.documentContentsSize
        Log.i("$TAG Document contents size is [$totalDocumentsCount]")

        val contentsToLoad = min(totalDocumentsCount, CONTENTS_PER_PAGE)
        val contents = chatRoom.getDocumentContentsRange(0, contentsToLoad)
        Log.i("$TAG [${contents.size}] documents have been fetched")

        documentsList.postValue(getFileModelsListFromContents(contents))
        operationInProgress.postValue(false)
    }

    @UiThread
    fun loadMoreData(totalItemsCount: Int) {
        coreContext.postOnCoreThread {
            Log.i("$TAG Loading more data, current total is $totalItemsCount, max size is $totalDocumentsCount")

            if (totalItemsCount < totalDocumentsCount) {
                var upperBound: Int = totalItemsCount + CONTENTS_PER_PAGE
                if (upperBound > totalDocumentsCount) {
                    upperBound = totalDocumentsCount
                }
                val contents = chatRoom.getDocumentContentsRange(totalItemsCount, upperBound)
                Log.i("$TAG [${contents.size}] contents loaded, adding them to list")

                val list = arrayListOf<FileModel>()
                list.addAll(documentsList.value.orEmpty())
                list.addAll(getFileModelsListFromContents(contents))
                documentsList.postValue(list)
            }
        }
    }

    @WorkerThread
    private fun getFileModelsListFromContents(contents: Array<Content>): ArrayList<FileModel> {
        val list = arrayListOf<FileModel>()
        for (documentContent in contents) {
            val isEncrypted = documentContent.isFileEncrypted
            val originalPath = documentContent.filePath.orEmpty()
            val path = if (isEncrypted) {
                Log.d(
                    "$TAG [VFS] Content is encrypted, requesting plain file path for file [${documentContent.filePath}]"
                )
                documentContent.exportPlainFile()
            } else {
                originalPath
            }
            val name = documentContent.name.orEmpty()
            val size = documentContent.size.toLong()
            val timestamp = documentContent.creationTimestamp
            if (path.isNotEmpty() && name.isNotEmpty()) {
                val messageId = documentContent.relatedChatMessageId
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

                val model =
                    FileModel(path, name, size, timestamp, isEncrypted, originalPath, ephemeral) {
                        openDocumentEvent.postValue(Event(it))
                    }
                list.add(model)
            }
        }
        return list
    }
}
