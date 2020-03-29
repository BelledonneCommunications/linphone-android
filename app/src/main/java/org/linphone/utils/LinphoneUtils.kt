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
package org.linphone.utils

import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.core.*
import org.linphone.core.tools.Log

/**
 * Various utility methods for Linphone SDK
 */
class LinphoneUtils {
    companion object {
        fun getDisplayName(address: Address): String {
            return address.displayName ?: address.username
        }

        fun isLimeAvailable(): Boolean {
            val core = coreContext.core
            val defaultProxy = core.defaultProxyConfig
            return core.limeX3DhAvailable() && core.limeX3DhEnabled() && core.limeX3DhServerUrl != null && defaultProxy != null
        }

        fun isGroupChatAvailable(): Boolean {
            val core = coreContext.core
            val defaultProxy = core.defaultProxyConfig
            return defaultProxy != null && defaultProxy.conferenceFactoryUri != null
        }

        fun createOneToOneChatRoom(participant: Address, isSecured: Boolean = false): ChatRoom? {
            val core: Core = coreContext.core
            val defaultProxyConfig = core.defaultProxyConfig

            if (defaultProxyConfig != null) {
                val room = core.findOneToOneChatRoom(defaultProxyConfig.identityAddress, participant, isSecured)
                if (room != null) {
                    return room
                } else {
                    return if (defaultProxyConfig.conferenceFactoryUri != null && isSecured /*|| !LinphonePreferences.instance().useBasicChatRoomFor1To1()*/) {
                        val params = core.createDefaultChatRoomParams()
                        params.enableEncryption(isSecured)
                        params.enableGroup(false)
                        // We don't want a basic chat room, so if isSecured is false we have to set this manually
                        params.backend = ChatRoomBackend.FlexisipChat

                        val participants = arrayOfNulls<Address>(1)
                        participants[0] = participant
                        core.createChatRoom(params, AppUtils.getString(R.string.chat_room_dummy_subject), participants)
                    } else {
                        core.getChatRoom(participant)
                    }
                }
            } else {
                if (isSecured) {
                    Log.e("[Linphone Utils] Can't create a secured chat room without proxy config")
                    return null
                }
                return core.getChatRoom(participant)
            }
        }

        fun deleteFilesAttachedToEventLog(eventLog: EventLog) {
            if (eventLog.type == EventLog.Type.ConferenceChatMessage) {
                val message = eventLog.chatMessage
                if (message != null) deleteFilesAttachedToChatMessage(message)
            }
        }

        fun deleteFilesAttachedToChatMessage(chatMessage: ChatMessage) {
            for (content in chatMessage.contents) {
                val filePath = content.filePath
                if (filePath != null) {
                    Log.i("[Linphone Utils] Deleting file $filePath")
                    FileUtils.deleteFile(filePath)
                }
            }
        }

        fun getRecordingFilePathForAddress(address: Address): String {
            val displayName = getDisplayName(address)
            val dateFormat: DateFormat = SimpleDateFormat("dd-MM-yyyy-HH-mm-ss", Locale.getDefault())
            val fileName = "${displayName}_${dateFormat.format(Date())}.mkv"
            return FileUtils.getFileStoragePath(fileName).absolutePath
        }
    }
}
