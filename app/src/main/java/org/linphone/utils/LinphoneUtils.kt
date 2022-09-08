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

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.telephony.TelephonyManager.*
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.R
import org.linphone.core.*
import org.linphone.core.tools.Log

/**
 * Various utility methods for Linphone SDK
 */
class LinphoneUtils {
    companion object {
        private const val RECORDING_DATE_PATTERN = "dd-MM-yyyy-HH-mm-ss"

        fun getDisplayName(address: Address?): String {
            if (address == null) return "[null]"
            if (address.displayName == null) {
                val account = coreContext.core.accountList.find { account ->
                    account.params.identityAddress?.asStringUriOnly() == address.asStringUriOnly()
                }
                val localDisplayName = account?.params?.identityAddress?.displayName
                // Do not return an empty local display name
                if (localDisplayName != null && localDisplayName.isNotEmpty()) {
                    return localDisplayName
                }
            }
            // Do not return an empty display name
            return address.displayName ?: address.username ?: address.asString()
        }

        fun getDisplayableAddress(address: Address?): String {
            if (address == null) return "[null]"
            return if (corePreferences.replaceSipUriByUsername) {
                address.username ?: address.asStringUriOnly()
            } else {
                val copy = address.clone()
                copy.clean() // To remove gruu if any
                copy.asStringUriOnly()
            }
        }

        fun getCleanedAddress(address: Address): Address {
            // To remove the GRUU if any
            val cleanAddress = address.clone()
            cleanAddress.clean()
            return cleanAddress
        }

        fun getConferenceAddress(call: Call): Address? {
            val remoteContact = call.remoteContact
            val conferenceAddress = if (call.dir == Call.Dir.Incoming) {
                if (remoteContact != null)
                    coreContext.core.interpretUrl(remoteContact, false)
                else
                    null
            } else {
                call.remoteAddress
            }
            return conferenceAddress
        }

        fun getConferenceSubject(conference: Conference): String? {
            return if (conference.subject.isNullOrEmpty()) {
                val conferenceInfo = coreContext.core.findConferenceInformationFromUri(conference.conferenceAddress)
                if (conferenceInfo != null) {
                    conferenceInfo.subject
                } else {
                    if (conference.me.isFocus) {
                        AppUtils.getString(R.string.conference_local_title)
                    } else {
                        AppUtils.getString(R.string.conference_default_title)
                    }
                }
            } else {
                conference.subject
            }
        }

        fun isEndToEndEncryptedChatAvailable(): Boolean {
            val core = coreContext.core
            return core.isLimeX3DhEnabled &&
                core.defaultAccount?.params?.limeServerUrl != null &&
                core.defaultAccount?.params?.conferenceFactoryUri != null
        }

        fun isGroupChatAvailable(): Boolean {
            val core = coreContext.core
            return core.defaultAccount?.params?.conferenceFactoryUri != null
        }

        fun isRemoteConferencingAvailable(): Boolean {
            val core = coreContext.core
            return core.defaultAccount?.params?.audioVideoConferenceFactoryAddress != null
        }

        fun createOneToOneChatRoom(participant: Address, isSecured: Boolean = false): ChatRoom? {
            val core: Core = coreContext.core
            val defaultAccount = core.defaultAccount

            val params = core.createDefaultChatRoomParams()
            params.isGroupEnabled = false
            params.backend = ChatRoomBackend.Basic
            if (isSecured) {
                params.subject = AppUtils.getString(R.string.chat_room_dummy_subject)
                params.isEncryptionEnabled = true
                params.backend = ChatRoomBackend.FlexisipChat
            }

            val participants = arrayOf(participant)

            return core.searchChatRoom(params, defaultAccount?.params?.identityAddress, null, participants)
                ?: core.createChatRoom(params, defaultAccount?.params?.identityAddress, participants)
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
                if (filePath != null && filePath.isNotEmpty()) {
                    Log.i("[Linphone Utils] Deleting file $filePath")
                    FileUtils.deleteFile(filePath)
                }
            }
        }

        fun getRecordingFilePathForAddress(address: Address): String {
            val displayName = getDisplayName(address)
            val dateFormat: DateFormat = SimpleDateFormat(
                RECORDING_DATE_PATTERN,
                Locale.getDefault()
            )
            val fileName = "${displayName}_${dateFormat.format(Date())}.mkv"
            return FileUtils.getFileStoragePath(fileName).absolutePath
        }

        fun getRecordingFilePathForConference(subject: String?): String {
            val dateFormat: DateFormat = SimpleDateFormat(
                RECORDING_DATE_PATTERN,
                Locale.getDefault()
            )
            val fileName = if (subject.isNullOrEmpty())
                "conference_${dateFormat.format(Date())}.mkv"
            else
                "${subject}_${dateFormat.format(Date())}.mkv"
            return FileUtils.getFileStoragePath(fileName).absolutePath
        }

        fun getRecordingDateFromFileName(name: String): Date {
            return SimpleDateFormat(RECORDING_DATE_PATTERN, Locale.getDefault()).parse(name)
        }

        @SuppressLint("MissingPermission")
        fun checkIfNetworkHasLowBandwidth(context: Context): Boolean {
            val connMgr = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val networkInfo: NetworkInfo? = connMgr.activeNetworkInfo
            if (networkInfo != null && networkInfo.isConnected) {
                if (networkInfo.type == ConnectivityManager.TYPE_MOBILE) {
                    return when (networkInfo.subtype) {
                        NETWORK_TYPE_EDGE, NETWORK_TYPE_GPRS, NETWORK_TYPE_IDEN -> true
                        else -> false
                    }
                }
            }
            // In doubt return false
            return false
        }

        fun isCallLogMissed(callLog: CallLog): Boolean {
            return (
                callLog.dir == Call.Dir.Incoming &&
                    (
                        callLog.status == Call.Status.Missed ||
                            callLog.status == Call.Status.Aborted ||
                            callLog.status == Call.Status.EarlyAborted
                        )
                )
        }

        fun areChatRoomsTheSame(chatRoom1: ChatRoom, chatRoom2: ChatRoom): Boolean {
            return chatRoom1.localAddress.weakEqual(chatRoom2.localAddress) &&
                chatRoom1.peerAddress.weakEqual(chatRoom2.peerAddress)
        }

        fun getChatRoomId(localAddress: Address, remoteAddress: Address): String {
            val localSipUri = localAddress.clone()
            localSipUri.clean()
            val remoteSipUri = remoteAddress.clone()
            remoteSipUri.clean()
            return "${localSipUri.asStringUriOnly()}~${remoteSipUri.asStringUriOnly()}"
        }

        fun getAccountsNotHidden(): List<Account> {
            val list = arrayListOf<Account>()

            for (account in coreContext.core.accountList) {
                if (account.getCustomParam("hidden") != "1") {
                    list.add(account)
                }
            }

            return list
        }

        fun applyInternationalPrefix(): Boolean {
            val account = coreContext.core.defaultAccount
            if (account != null) {
                val params = account.params
                return params.useInternationalPrefixForCallsAndChats
            }

            return true // Legacy behavior
        }
    }
}
