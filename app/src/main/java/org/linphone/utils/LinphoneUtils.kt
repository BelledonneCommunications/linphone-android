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

import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.annotation.AnyThread
import androidx.annotation.IntegerRes
import androidx.annotation.WorkerThread
import androidx.emoji2.text.EmojiCompat
import java.util.Locale
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.core.Address
import org.linphone.core.Call
import org.linphone.core.Call.Dir
import org.linphone.core.Call.Status
import org.linphone.core.ChatRoom
import org.linphone.core.tools.Log

class LinphoneUtils {
    companion object {
        private const val TAG = "[App Utils]"

        @AnyThread
        fun getFirstLetter(displayName: String): String {
            return getInitials(displayName, 1)
        }

        @AnyThread
        fun getInitials(displayName: String, limit: Int = 2): String {
            if (displayName.isEmpty()) return ""

            val split = displayName.uppercase(Locale.getDefault()).split(" ")
            var initials = ""
            var characters = 0
            val emoji = coreContext.emojiCompat

            for (i in split.indices) {
                if (split[i].isNotEmpty()) {
                    try {
                        if (emoji.loadState == EmojiCompat.LOAD_STATE_SUCCEEDED && emoji.hasEmojiGlyph(
                                split[i]
                            )
                        ) {
                            val glyph = emoji.process(split[i])
                            if (characters > 0) { // Limit initial to 1 emoji only
                                Log.d("$TAG We limit initials to one emoji only")
                                initials = ""
                            }
                            initials += glyph
                            break // Limit initial to 1 emoji only
                        } else {
                            initials += split[i][0]
                        }
                    } catch (ise: IllegalStateException) {
                        Log.e("$TAG Can't call hasEmojiGlyph: $ise")
                        initials += split[i][0]
                    }

                    characters += 1
                    if (characters >= limit) break
                }
            }
            return initials
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
        fun isCallOutgoing(callState: Call.State): Boolean {
            return when (callState) {
                Call.State.OutgoingInit, Call.State.OutgoingProgress, Call.State.OutgoingRinging, Call.State.OutgoingEarlyMedia -> true
                else -> false
            }
        }

        @AnyThread
        @IntegerRes
        fun getIconResId(callStatus: Status, callDir: Dir): Int {
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

        @WorkerThread
        private fun getChatRoomId(localAddress: Address, remoteAddress: Address): String {
            val localSipUri = localAddress.clone()
            localSipUri.clean()
            val remoteSipUri = remoteAddress.clone()
            remoteSipUri.clean()
            return "${localSipUri.asStringUriOnly()}~${remoteSipUri.asStringUriOnly()}"
        }

        @WorkerThread
        fun getChatRoomId(chatRoom: ChatRoom): String {
            return getChatRoomId(chatRoom.localAddress, chatRoom.peerAddress)
        }

        @AnyThread
        fun getDeviceName(context: Context): String {
            var name = Settings.Global.getString(
                context.contentResolver,
                Settings.Global.DEVICE_NAME
            )
            if (name == null) {
                val adapter = BluetoothAdapter.getDefaultAdapter()
                name = adapter?.name
            }
            if (name == null) {
                name = Settings.Secure.getString(
                    context.contentResolver,
                    "bluetooth_name"
                )
            }
            if (name == null) {
                name = Build.MANUFACTURER + " " + Build.MODEL
            }
            return name
        }
    }
}
