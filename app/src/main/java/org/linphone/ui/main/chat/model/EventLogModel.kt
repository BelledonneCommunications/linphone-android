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
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.core.EventLog
import org.linphone.core.tools.Log
import org.linphone.ui.main.contacts.model.ContactAvatarModel
import org.linphone.utils.LinphoneUtils

class EventLogModel @WorkerThread constructor(
    val eventLog: EventLog,
    val avatarModel: ContactAvatarModel,
    isFromGroup: Boolean = false,
    isGroupedWithPreviousOne: Boolean = false,
    isGroupedWithNextOne: Boolean = false,
    onContentClicked: ((file: String) -> Unit)? = null,
    onJoinConferenceClicked: ((uri: String) -> Unit)? = null,
    onWebUrlClicked: ((url: String) -> Unit)? = null,
    onContactClicked: ((friendRefKey: String) -> Unit)? = null,
    onRedToastToShow: ((pair: Pair<String, Int>) -> Unit)? = null
) {
    companion object {
        private const val TAG = "[Event Log Model]"
    }

    val type: EventLog.Type = eventLog.type

    val isEvent = type != EventLog.Type.ConferenceChatMessage

    val model: Any = if (isEvent) {
        EventModel(eventLog)
    } else {
        val chatMessage = eventLog.chatMessage!!
        var replyTo = ""
        var isReply = chatMessage.isReply
        val replyText = if (chatMessage.isReply) {
            val replyMessage = chatMessage.replyMessage
            if (replyMessage != null) {
                val from = replyMessage.fromAddress
                replyTo = coreContext.contactsManager.findContactByAddress(from)?.name ?: LinphoneUtils.getDisplayName(
                    from
                )

                LinphoneUtils.getTextDescribingMessage(replyMessage)
            } else {
                Log.e(
                    "$TAG Failed to find the reply message from ID [${chatMessage.replyMessageId}]"
                )
                isReply = false
                ""
            }
        } else {
            ""
        }

        MessageModel(
            chatMessage,
            avatarModel,
            isFromGroup,
            isReply,
            replyTo,
            replyText,
            chatMessage.replyMessageId,
            chatMessage.isForward,
            isGroupedWithPreviousOne,
            isGroupedWithNextOne,
            onContentClicked,
            onJoinConferenceClicked,
            onWebUrlClicked,
            onContactClicked,
            onRedToastToShow
        )
    }

    val notifyId = eventLog.notifyId

    @WorkerThread
    fun destroy() {
        (model as? MessageModel)?.destroy()
    }
}
