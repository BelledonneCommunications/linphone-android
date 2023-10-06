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
package org.linphone.ui.main.chat.model

import androidx.annotation.WorkerThread
import androidx.lifecycle.MutableLiveData
import org.linphone.core.ChatMessage
import org.linphone.ui.main.contacts.model.ContactAvatarModel
import org.linphone.utils.LinphoneUtils

class ChatMessageModel @WorkerThread constructor(
    chatMessage: ChatMessage,
    val avatarModel: ContactAvatarModel
) {
    val id = chatMessage.messageId

    val isOutgoing = chatMessage.isOutgoing

    val state = MutableLiveData<ChatMessage.State>()

    val text = MutableLiveData<String>()

    init {
        state.postValue(chatMessage.state)
        text.postValue(LinphoneUtils.getTextDescribingMessage(chatMessage))
    }
}
