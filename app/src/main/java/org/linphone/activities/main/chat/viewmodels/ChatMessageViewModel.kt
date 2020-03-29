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
package org.linphone.activities.main.chat.viewmodels

import android.os.CountDownTimer
import android.text.Spanned
import androidx.lifecycle.MutableLiveData
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.R
import org.linphone.compatibility.Compatibility
import org.linphone.contact.GenericContactViewModel
import org.linphone.core.ChatMessage
import org.linphone.core.ChatMessageListenerStub
import org.linphone.core.Content
import org.linphone.core.tools.Log
import org.linphone.mediastream.Version
import org.linphone.utils.AppUtils
import org.linphone.utils.PermissionHelper
import org.linphone.utils.TimestampUtils

class ChatMessageViewModel(
    val chatMessage: ChatMessage,
    private val contentListener: OnContentClickedListener? = null
) : GenericContactViewModel(chatMessage.fromAddress) {
    val sendInProgress = MutableLiveData<Boolean>()

    val transferInProgress = MutableLiveData<Boolean>()

    val showImdn = MutableLiveData<Boolean>()

    val imdnIcon = MutableLiveData<Int>()

    val backgroundRes = MutableLiveData<Int>()

    val hideAvatar = MutableLiveData<Boolean>()

    val hideTime = MutableLiveData<Boolean>()

    val contents = MutableLiveData<ArrayList<ChatMessageContentViewModel>>()

    val time = MutableLiveData<String>()

    val ephemeralLifetime = MutableLiveData<String>()

    val text: Spanned? by lazy {
        if (chatMessage.textContent != null) AppUtils.getTextWithHttpLinks(chatMessage.textContent) else null
    }

    private var countDownTimer: CountDownTimer? = null

    private val listener = object : ChatMessageListenerStub() {
        override fun onMsgStateChanged(message: ChatMessage, state: ChatMessage.State) {
            time.value = TimestampUtils.toString(chatMessage.time)
            updateChatMessageState(state)

            // TODO FIXME : find a way to refresh outgoing message downloaded
            if (state == ChatMessage.State.FileTransferDone && !message.isOutgoing) {
                Log.i("[Chat Message] File transfer done")
                // No need to refresh content lists on outgoing messages after file transfer is done
                // It will even cause the app to crash if updateContentsList is not call right after
                updateContentsList()

                if (!message.isEphemeral && corePreferences.makePublicDownloadedImages) {
                    if (Version.sdkAboveOrEqual(Version.API29_ANDROID_10) || PermissionHelper.get().hasWriteExternalStorage()) {
                        for (content in message.contents) {
                            if (content.isFile && content.filePath != null && content.userData == null) {
                                addContentToMediaStore(content)
                            }
                        }
                    } else {
                        Log.e("[Chat Message] Can't make file public, app doesn't have WRITE_EXTERNAL_STORAGE permission")
                    }
                }
            }
        }

        override fun onEphemeralMessageTimerStarted(message: ChatMessage) {
            updateEphemeralTimer()
        }
    }

    init {
        chatMessage.addListener(listener)

        backgroundRes.value = if (chatMessage.isOutgoing) R.drawable.chat_bubble_outgoing_full else R.drawable.chat_bubble_incoming_full
        hideAvatar.value = false
        time.value = TimestampUtils.toString(chatMessage.time)
        updateEphemeralTimer()

        updateChatMessageState(chatMessage.state)
        updateContentsList()
    }

    override fun onCleared() {
        chatMessage.removeListener(listener)

        super.onCleared()
    }

    fun updateBubbleBackground(hasPrevious: Boolean, hasNext: Boolean) {
        if (hasPrevious) {
            hideTime.value = true
        }

        if (chatMessage.isOutgoing) {
            if (hasNext && hasPrevious) {
                backgroundRes.value = R.drawable.chat_bubble_outgoing_split_2
            } else if (hasNext) {
                backgroundRes.value = R.drawable.chat_bubble_outgoing_split_1
            } else if (hasPrevious) {
                backgroundRes.value = R.drawable.chat_bubble_outgoing_split_3
            } else {
                backgroundRes.value = R.drawable.chat_bubble_outgoing_full
            }
        } else {
            if (hasNext && hasPrevious) {
                hideAvatar.value = true
                backgroundRes.value = R.drawable.chat_bubble_incoming_split_2
            } else if (hasNext) {
                backgroundRes.value = R.drawable.chat_bubble_incoming_split_1
            } else if (hasPrevious) {
                hideAvatar.value = true
                backgroundRes.value = R.drawable.chat_bubble_incoming_split_3
            } else {
                backgroundRes.value = R.drawable.chat_bubble_incoming_full
            }
        }
    }

    private fun updateChatMessageState(state: ChatMessage.State) {
        transferInProgress.value = state == ChatMessage.State.FileTransferInProgress

        sendInProgress.value = state == ChatMessage.State.InProgress || state == ChatMessage.State.FileTransferInProgress

        showImdn.value = when (state) {
            ChatMessage.State.DeliveredToUser, ChatMessage.State.Displayed, ChatMessage.State.NotDelivered -> true
            else -> false
        }

        imdnIcon.value = when (state) {
            ChatMessage.State.DeliveredToUser -> R.drawable.imdn_received
            ChatMessage.State.Displayed -> R.drawable.imdn_read
            else -> R.drawable.imdn_error
        }
    }

    private fun updateContentsList() {
        val list = arrayListOf<ChatMessageContentViewModel>()
        for (content in chatMessage.contents) {
            if (content.isFileTransfer || content.isFile) {
                list.add(ChatMessageContentViewModel(content, chatMessage, contentListener))
            }
        }
        contents.value = list
    }

    private fun updateEphemeralTimer() {
        if (chatMessage.isEphemeral) {
            if (chatMessage.ephemeralExpireTime == 0L) {
                // This means the message hasn't been read by all participants yet, so the countdown hasn't started
                // In this case we simply display the configured value for lifetime
                ephemeralLifetime.value = formatLifetime(chatMessage.ephemeralLifetime)
            } else {
                // Countdown has started, display remaining time
                val remaining = chatMessage.ephemeralExpireTime - (System.currentTimeMillis() / 1000)
                ephemeralLifetime.value = formatLifetime(remaining)
                if (countDownTimer == null) {
                    countDownTimer = object : CountDownTimer(remaining * 1000, 1000) {
                        override fun onFinish() {}

                        override fun onTick(millisUntilFinished: Long) {
                            ephemeralLifetime.postValue(formatLifetime(millisUntilFinished / 1000))
                        }
                    }
                    countDownTimer?.start()
                }
            }
        }
    }

    private fun formatLifetime(seconds: Long): String {
        val days = seconds / 86400
        return when {
            days >= 1L -> AppUtils.getStringWithPlural(R.plurals.days, days.toInt())
            else -> String.format("%02d:%02d:%02d", seconds / 3600, (seconds % 3600) / 60, (seconds % 60))
        }
    }

    private fun addContentToMediaStore(content: Content) {
        when (content.type) {
            "image" -> {
                if (Compatibility.addImageToMediaStore(coreContext.context, content)) {
                    Log.i("[Chat Message] Adding image ${content.name} terminated")
                } else {
                    Log.e("[Chat Message] Something went wrong while copying file...")
                }
            }
            "video" -> {
                if (Compatibility.addVideoToMediaStore(coreContext.context, content)) {
                    Log.i("[Chat Message] Adding video ${content.name} terminated")
                } else {
                    Log.e("[Chat Message] Something went wrong while copying file...")
                }
            }
            "audio" -> {
                if (Compatibility.addAudioToMediaStore(coreContext.context, content)) {
                    Log.i("[Chat Message] Adding audio ${content.name} terminated")
                } else {
                    Log.e("[Chat Message] Something went wrong while copying file...")
                }
            }
            else -> {
                Log.w("[Chat Message] File ${content.name} isn't either an image, an audio file or a video, can't add it to the Media Store")
            }
        }
    }
}
