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

class ConversationMediaListViewModel
    @UiThread
    constructor() : AbstractConversationViewModel() {
    companion object {
        private const val TAG = "[Conversation Media List ViewModel]"

        private const val CONTENTS_PER_PAGE = 50
    }

    val mediaList = MutableLiveData<List<FileModel>>()

    val operationInProgress = MutableLiveData<Boolean>()

    val openMediaEvent: MutableLiveData<Event<FileModel>> by lazy {
        MutableLiveData<Event<FileModel>>()
    }

    private var totalMediaCount: Int = -1

    @WorkerThread
    override fun afterNotifyingChatRoomFound(sameOne: Boolean) {
        loadMediaList()
    }

    override fun onCleared() {
        super.onCleared()

        mediaList.value.orEmpty().forEach(FileModel::destroy)
    }

    @WorkerThread
    private fun loadMediaList() {
        operationInProgress.postValue(true)
        Log.i(
            "$TAG Loading media contents for conversation [${LinphoneUtils.getConversationId(
                chatRoom
            )}]"
        )

        totalMediaCount = chatRoom.mediaContentsSize
        Log.i("$TAG Media contents size is [$totalMediaCount]")

        val contentsToLoad = min(totalMediaCount, CONTENTS_PER_PAGE)
        val contents = chatRoom.getMediaContentsRange(0, contentsToLoad)
        Log.i("$TAG [${contents.size}] media have been fetched")

        mediaList.postValue(getFileModelsListFromContents(contents))
        operationInProgress.postValue(false)
    }

    @UiThread
    fun loadMoreData(totalItemsCount: Int) {
        coreContext.postOnCoreThread {
            Log.i("$TAG Loading more data, current total is $totalItemsCount, max size is $totalMediaCount")

            if (totalItemsCount < totalMediaCount) {
                var upperBound: Int = totalItemsCount + CONTENTS_PER_PAGE
                if (upperBound > totalMediaCount) {
                    upperBound = totalMediaCount
                }
                val contents = chatRoom.getMediaContentsRange(totalItemsCount, upperBound)
                Log.i("$TAG [${contents.size}] contents loaded, adding them to list")

                val list = arrayListOf<FileModel>()
                list.addAll(mediaList.value.orEmpty())
                list.addAll(getFileModelsListFromContents(contents))
                mediaList.postValue(list)
            }
        }
    }

    @WorkerThread
    private fun getFileModelsListFromContents(contents: Array<Content>): ArrayList<FileModel> {
        val list = arrayListOf<FileModel>()
        for (mediaContent in contents) {
            // Do not display voice recordings here, even if they are media file
            if (mediaContent.isVoiceRecording) continue

            val isEncrypted = mediaContent.isFileEncrypted
            val originalPath = mediaContent.filePath.orEmpty()
            val path = if (isEncrypted) {
                Log.d(
                    "$TAG [VFS] Content is encrypted, requesting plain file path for file [${mediaContent.filePath}]"
                )
                mediaContent.exportPlainFile()
            } else {
                originalPath
            }
            val name = mediaContent.name.orEmpty()
            val size = mediaContent.size.toLong()
            val timestamp = mediaContent.creationTimestamp
            if (path.isNotEmpty() && name.isNotEmpty()) {
                val model =
                    FileModel(path, name, size, timestamp, isEncrypted, originalPath, chatRoom.isEphemeralEnabled) {
                        openMediaEvent.postValue(Event(it))
                    }
                list.add(model)
            }
        }
        return list
    }
}
