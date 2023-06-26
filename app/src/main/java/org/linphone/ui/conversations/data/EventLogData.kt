/*
 * Copyright (c) 2010-2021 Belledonne Communications SARL.
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
package org.linphone.ui.conversations.data

import org.linphone.core.EventLog

class EventLogData(val eventLog: EventLog) {
    val type: EventLog.Type = eventLog.type

    val isEvent = type != EventLog.Type.ConferenceChatMessage

    val data = if (isEvent) {
        EventData(eventLog)
    } else {
        ChatMessageData(eventLog.chatMessage!!)
    }

    val notifyId = eventLog.notifyId

    fun destroy() {
        when (data) {
            is EventData -> data.destroy()
            is ChatMessageData -> data.destroy()
        }
    }

    fun contactLookup() {
        when (data) {
            is EventData -> data.contactLookup()
            is ChatMessageData -> data.contactLookup()
        }
    }
}
