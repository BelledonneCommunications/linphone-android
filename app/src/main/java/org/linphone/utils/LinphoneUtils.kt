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
package org.linphone.utils

import android.graphics.Bitmap
import androidx.annotation.AnyThread
import androidx.annotation.DrawableRes
import androidx.annotation.IntegerRes
import androidx.annotation.WorkerThread
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.core.Account
import org.linphone.core.Address
import org.linphone.core.Call
import org.linphone.core.Call.Dir
import org.linphone.core.Call.Status
import org.linphone.core.CallLog
import org.linphone.core.ChatMessage
import org.linphone.core.ChatRoom
import org.linphone.core.ConferenceInfo
import org.linphone.core.Core
import org.linphone.core.Factory
import org.linphone.core.tools.Log
import org.linphone.ui.main.contacts.model.ContactAvatarModel

class LinphoneUtils {
    companion object {
        private const val TAG = "[Linphone Utils]"

        private const val RECORDING_DATE_PATTERN = "dd-MM-yyyy-HH-mm-ss"
        private const val CHAT_ROOM_ID_SEPARATOR = "#~#"

        @WorkerThread
        fun getDefaultAccount(): Account? {
            return coreContext.core.defaultAccount ?: coreContext.core.accountList.firstOrNull()
        }

        @WorkerThread
        fun getAddressAsCleanStringUriOnly(address: Address): String {
            val scheme = address.scheme ?: "sip"
            val username = address.username
            if (username.orEmpty().isEmpty()) {
                return "$scheme:${address.domain}"
            }
            return "$scheme:$username@${address.domain}"
        }

        @WorkerThread
        fun getDisplayName(address: Address?): String {
            if (address == null) return "[null]"
            if (address.displayName == null) {
                val account = coreContext.core.accountList.find { account ->
                    account.params.identityAddress?.asStringUriOnly() == address.asStringUriOnly()
                }
                val localDisplayName = account?.params?.identityAddress?.displayName
                // Do not return an empty local display name
                if (!localDisplayName.isNullOrEmpty()) {
                    return localDisplayName
                }
            }
            // Do not return an empty display name
            return address.displayName ?: address.username ?: address.asString()
        }

        @AnyThread
        fun isCallIncoming(callState: Call.State): Boolean {
            return when (callState) {
                Call.State.IncomingReceived, Call.State.IncomingEarlyMedia -> true
                else -> false
            }
        }

        @AnyThread
        fun isCallOutgoing(callState: Call.State): Boolean {
            return when (callState) {
                Call.State.OutgoingInit, Call.State.OutgoingProgress, Call.State.OutgoingRinging, Call.State.OutgoingEarlyMedia -> true
                else -> false
            }
        }

        @AnyThread
        fun isCallPaused(callState: Call.State): Boolean {
            return when (callState) {
                Call.State.Pausing, Call.State.Paused, Call.State.PausedByRemote, Call.State.Resuming -> true
                else -> false
            }
        }

        @AnyThread
        fun isCallEnding(callState: Call.State): Boolean {
            return when (callState) {
                Call.State.End, Call.State.Error -> true
                else -> false
            }
        }

        @WorkerThread
        fun isEndToEndEncryptedChatAvailable(core: Core): Boolean {
            return core.isLimeX3DhEnabled &&
                core.defaultAccount?.params?.limeServerUrl != null &&
                core.defaultAccount?.params?.conferenceFactoryUri != null
        }

        @WorkerThread
        fun isGroupChatAvailable(core: Core): Boolean {
            return core.defaultAccount?.params?.conferenceFactoryUri != null
        }

        @WorkerThread
        fun isRemoteConferencingAvailable(core: Core): Boolean {
            return core.defaultAccount?.params?.audioVideoConferenceFactoryAddress != null
        }

        @WorkerThread
        fun arePushNotificationsAvailable(core: Core): Boolean {
            if (!core.isPushNotificationAvailable) {
                Log.w(
                    "$TAG Push notifications aren't available in the Core, disable account creation"
                )
                return false
            }

            val pushConfig = core.pushNotificationConfig
            if (pushConfig == null) {
                Log.w(
                    "$TAG Core's push notifications configuration is null, disable account creation"
                )
                return false
            }

            if (pushConfig.provider.isNullOrEmpty()) {
                Log.w(
                    "$TAG Core's push notifications configuration provider is null or empty, disable account creation"
                )
                return false
            }
            if (pushConfig.param.isNullOrEmpty()) {
                Log.w(
                    "$TAG Core's push notifications configuration param is null or empty, disable account creation"
                )
                return false
            }
            if (pushConfig.prid.isNullOrEmpty()) {
                Log.w(
                    "$TAG Core's push notifications configuration prid is null or empty, disable account creation"
                )
                return false
            }

            Log.i("$TAG Push notifications seems to be available")
            return true
        }

        @WorkerThread
        fun isCallLogMissed(callLog: CallLog): Boolean {
            if (callLog.dir == Dir.Outgoing) return false
            return callLog.status == Status.Missed ||
                callLog.status == Status.Aborted ||
                callLog.status == Status.EarlyAborted
        }

        @AnyThread
        @IntegerRes
        fun getCallIconResId(callStatus: Status, callDir: Dir): Int {
            return when (callStatus) {
                Status.Missed -> {
                    if (callDir == Dir.Outgoing) {
                        R.drawable.outgoing_call_missed
                    } else {
                        R.drawable.incoming_call_missed
                    }
                }

                Status.Success -> {
                    if (callDir == Dir.Outgoing) {
                        R.drawable.outgoing_call
                    } else {
                        R.drawable.incoming_call
                    }
                }

                else -> {
                    if (callDir == Dir.Outgoing) {
                        R.drawable.outgoing_call_rejected
                    } else {
                        R.drawable.incoming_call_rejected
                    }
                }
            }
        }

        @AnyThread
        @DrawableRes
        fun getChatIconResId(chatState: ChatMessage.State): Int {
            return when (chatState) {
                ChatMessage.State.Displayed, ChatMessage.State.FileTransferDone -> {
                    R.drawable.checks
                }
                ChatMessage.State.DeliveredToUser -> {
                    R.drawable.check
                }
                ChatMessage.State.Delivered -> {
                    R.drawable.envelope_simple
                }
                ChatMessage.State.NotDelivered, ChatMessage.State.FileTransferError -> {
                    R.drawable.warning_circle
                }
                ChatMessage.State.InProgress, ChatMessage.State.FileTransferInProgress -> {
                    R.drawable.animated_in_progress
                }
                else -> {
                    R.drawable.animated_in_progress
                }
            }
        }

        @WorkerThread
        fun getChatRoomId(room: ChatRoom): String {
            return getChatRoomId(room.localAddress, room.peerAddress)
        }

        @WorkerThread
        fun getChatRoomId(localAddress: Address, remoteAddress: Address): String {
            val localSipUri = localAddress.clone()
            localSipUri.clean()
            val remoteSipUri = remoteAddress.clone()
            remoteSipUri.clean()
            return getChatRoomId(localSipUri.asStringUriOnly(), remoteSipUri.asStringUriOnly())
        }

        @AnyThread
        fun getChatRoomId(localSipUri: String, remoteSipUri: String): String {
            return "$localSipUri$CHAT_ROOM_ID_SEPARATOR$remoteSipUri"
        }

        @AnyThread
        fun getLocalAndPeerSipUrisFromChatRoomId(id: String): Pair<String, String>? {
            val split = id.split(CHAT_ROOM_ID_SEPARATOR)
            if (split.size == 2) {
                val localAddress = split[0]
                val peerAddress = split[1]
                Log.i(
                    "$TAG Got local [$localAddress] and peer [$peerAddress] SIP URIs from conversation id [$id]"
                )
                return Pair(localAddress, peerAddress)
            } else {
                Log.e(
                    "$TAG Failed to parse conversation id [$id] with separator [$CHAT_ROOM_ID_SEPARATOR]"
                )
            }
            return null
        }

        @WorkerThread
        fun isChatRoomAGroup(chatRoom: ChatRoom): Boolean {
            val oneToOne = chatRoom.hasCapability(ChatRoom.Capabilities.OneToOne.toInt())
            val conference = chatRoom.hasCapability(ChatRoom.Capabilities.Conference.toInt())
            return !oneToOne && conference
        }

        @WorkerThread
        fun getRecordingFilePathForAddress(address: Address): String {
            val displayName = getDisplayName(address)
            val dateFormat: DateFormat = SimpleDateFormat(
                RECORDING_DATE_PATTERN,
                Locale.getDefault()
            )
            val fileName = "${displayName}_${dateFormat.format(Date())}.mkv"
            return FileUtils.getFileStoragePath(fileName).absolutePath
        }

        @WorkerThread
        fun callStateToString(state: Call.State): String {
            return when (state) {
                Call.State.IncomingEarlyMedia, Call.State.IncomingReceived -> {
                    AppUtils.getString(R.string.call_state_incoming_received)
                }
                Call.State.OutgoingInit, Call.State.OutgoingProgress -> {
                    AppUtils.getString(R.string.call_state_outgoing_progress)
                }
                Call.State.OutgoingRinging, Call.State.OutgoingEarlyMedia -> {
                    AppUtils.getString(R.string.call_state_outgoing_ringing)
                }
                Call.State.Connected, Call.State.StreamsRunning, Call.State.Updating, Call.State.UpdatedByRemote -> {
                    AppUtils.getString(R.string.call_state_connected)
                }
                Call.State.Pausing, Call.State.Paused, Call.State.PausedByRemote -> {
                    AppUtils.getString(R.string.call_state_paused)
                }
                Call.State.Resuming -> {
                    AppUtils.getString(R.string.call_state_resuming)
                }
                Call.State.End, Call.State.Released, Call.State.Error -> {
                    AppUtils.getString(R.string.call_state_ended)
                }
                else -> {
                    // TODO: handle other states?
                    ""
                }
            }
        }

        @WorkerThread
        fun getTextDescribingMessage(message: ChatMessage): String {
            // If message contains text, then use that
            var text = message.contents.find { content -> content.isText }?.utf8Text ?: ""

            if (text.isEmpty()) {
                val firstContent = message.contents.firstOrNull()
                if (firstContent?.isIcalendar == true) {
                    val conferenceInfo = Factory.instance().createConferenceInfoFromIcalendarContent(
                        firstContent
                    )
                    if (conferenceInfo != null) {
                        val subject = conferenceInfo.subject.orEmpty()
                        text = when (conferenceInfo.state) {
                            ConferenceInfo.State.Cancelled -> {
                                AppUtils.getFormattedString(
                                    R.string.message_meeting_invitation_cancelled_content_description,
                                    subject
                                )
                            }

                            ConferenceInfo.State.Updated -> {
                                AppUtils.getFormattedString(
                                    R.string.message_meeting_invitation_updated_content_description,
                                    subject
                                )
                            }

                            else -> {
                                AppUtils.getFormattedString(
                                    R.string.message_meeting_invitation_content_description,
                                    subject
                                )
                            }
                        }
                    } else {
                        Log.e(
                            "$TAG Failed to parse content with iCalendar content type as conference info!"
                        )
                        text = firstContent.name.orEmpty()
                    }
                } else if (firstContent?.isVoiceRecording == true) {
                    text = AppUtils.getString(R.string.message_voice_message_content_description)
                } else {
                    for (content in message.contents) {
                        if (text.isNotEmpty()) {
                            text += ", "
                        }
                        text += content.name
                    }
                }
            }

            return text
        }

        @WorkerThread
        fun chatRoomConfigureEphemeralMessagesLifetime(chatRoom: ChatRoom, lifetime: Long) {
            if (lifetime == 0L) {
                if (chatRoom.isEphemeralEnabled) {
                    Log.i("$TAG Disabling ephemeral messages")
                    chatRoom.isEphemeralEnabled = false
                }
            } else {
                if (!chatRoom.isEphemeralEnabled) {
                    Log.i("$TAG Enabling ephemeral messages")
                    chatRoom.isEphemeralEnabled = true
                }

                if (chatRoom.ephemeralLifetime != lifetime) {
                    Log.i("$TAG Updating lifetime to [$lifetime]")
                    chatRoom.ephemeralLifetime = lifetime
                }
            }
            Log.i(
                "$TAG Ephemeral messages are [${if (chatRoom.isEphemeralEnabled) "enabled" else "disabled"}], lifetime is [${chatRoom.ephemeralLifetime}]"
            )
        }

        @WorkerThread
        fun getAvatarModelForConferenceInfo(conferenceInfo: ConferenceInfo): ContactAvatarModel {
            val fakeFriend = coreContext.core.createFriend()
            fakeFriend.address = conferenceInfo.uri
            fakeFriend.name = conferenceInfo.subject

            val hash = conferenceInfo.uri?.asStringUriOnly().hashCode().toString()
            val file = FileUtils.getFileStorageCacheDir("$hash.jpg", overrideExisting = true)
            if (!file.exists()) {
                val list = arrayListOf<String>()
                for (participant in conferenceInfo.participantInfos) {
                    val friend = coreContext.contactsManager.findContactByAddress(
                        participant.address
                    )
                    if (friend != null) {
                        val picture = friend.photo
                        if (picture != null) {
                            list.add(picture)
                        }
                    }
                }
                if (list.isNotEmpty()) {
                    val bitmap = ImageUtils.generateBitmapFromList(list)
                    val outputStream: OutputStream = FileOutputStream(file)
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
                    outputStream.close()
                }
            }
            fakeFriend.photo = FileUtils.getProperFilePath(file.absolutePath)

            val avatarModel = ContactAvatarModel(fakeFriend)
            avatarModel.defaultToConferenceIcon.postValue(true)
            avatarModel.skipInitials.postValue(true)

            return avatarModel
        }
    }
}
