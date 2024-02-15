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
import org.linphone.core.tools.Log
import org.linphone.ui.main.chat.model.FileModel
import org.linphone.utils.Event
import org.linphone.utils.LinphoneUtils

class ConversationMediaListViewModel @UiThread constructor() : AbstractConversationViewModel() {
    companion object {
        private const val TAG = "[Conversation Media List ViewModel]"
    }

    val mediaList = MutableLiveData<ArrayList<FileModel>>()

    val fullScreenMode = MutableLiveData<Boolean>()

    val currentlyDisplayedFileName = MutableLiveData<String>()

    val operationInProgress = MutableLiveData<Boolean>()

    val openMediaEvent: MutableLiveData<Event<FileModel>> by lazy {
        MutableLiveData<Event<FileModel>>()
    }

    override fun beforeNotifyingChatRoomFound(sameOne: Boolean) {
        loadMediaList()
    }

    @WorkerThread
    private fun loadMediaList() {
        operationInProgress.postValue(true)

        val list = arrayListOf<FileModel>()
        Log.i(
            "$TAG Loading media contents for conversation [${LinphoneUtils.getChatRoomId(
                chatRoom
            )}]"
        )
        val media = chatRoom.mediaContents
        Log.i("$TAG [${media.size}] media have been fetched")
        for (mediaContent in media) {
            val path = mediaContent.filePath.orEmpty()
            val name = mediaContent.name.orEmpty()
            val size = mediaContent.size.toLong()
            if (path.isNotEmpty() && name.isNotEmpty()) {
                val model = FileModel(path, name, size) {
                    openMediaEvent.postValue(Event(it))
                }
                list.add(model)
            }
        }
        Log.i("$TAG [${media.size}] media have been processed")

        mediaList.postValue(list)
        operationInProgress.postValue(false)
    }
}