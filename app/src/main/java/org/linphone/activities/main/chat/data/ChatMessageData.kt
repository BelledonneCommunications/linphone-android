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
package org.linphone.activities.main.chat.data

import android.os.CountDownTimer
import android.text.Spannable
import android.util.Patterns
import androidx.lifecycle.MutableLiveData
import java.util.regex.Pattern
import org.linphone.R
import org.linphone.contact.GenericContactData
import org.linphone.core.ChatMessage
import org.linphone.core.ChatMessageListenerStub
import org.linphone.core.tools.Log
import org.linphone.utils.AppUtils
import org.linphone.utils.PatternClickableSpan
import org.linphone.utils.TimestampUtils

class ChatMessageData(val chatMessage: ChatMessage) : GenericContactData(chatMessage.fromAddress) {
    private var contentListener: OnContentClickedListener? = null

    val sendInProgress = MutableLiveData<Boolean>()

    val showImdn = MutableLiveData<Boolean>()

    val imdnIcon = MutableLiveData<Int>()

    val backgroundRes = MutableLiveData<Int>()

    val hideAvatar = MutableLiveData<Boolean>()

    val hideTime = MutableLiveData<Boolean>()

    val contents = MutableLiveData<ArrayList<ChatMessageContentData>>()

    val time = MutableLiveData<String>()

    val ephemeralLifetime = MutableLiveData<String>()

    val text = MutableLiveData<Spannable>()

    val replyData = MutableLiveData<ChatMessageData>()

    val isDisplayed = MutableLiveData<Boolean>()

    val isOutgoing = chatMessage.isOutgoing

    var hasPreviousMessage = false
    var hasNextMessage = false

    private var countDownTimer: CountDownTimer? = null

    private val listener = object : ChatMessageListenerStub() {
        override fun onMsgStateChanged(message: ChatMessage, state: ChatMessage.State) {
            time.value = TimestampUtils.toString(chatMessage.time)
            updateChatMessageState(state)
        }

        override fun onEphemeralMessageTimerStarted(message: ChatMessage) {
            updateEphemeralTimer()
        }
    }

    init {
        chatMessage.addListener(listener)

        backgroundRes.value = if (chatMessage.isOutgoing) R.drawable.chat_bubble_outgoing_full else R.drawable.chat_bubble_incoming_full
        hideAvatar.value = false

        if (chatMessage.isReply) {
            val reply = chatMessage.replyMessage
            if (reply != null) {
                Log.i("[Chat Message Data] Message is a reply of message id [${chatMessage.replyMessageId}] sent by [${chatMessage.replyMessageSenderAddress?.asStringUriOnly()}]")
                replyData.value = ChatMessageData(reply)
            }
        }

        time.value = TimestampUtils.toString(chatMessage.time)
        updateEphemeralTimer()

        updateChatMessageState(chatMessage.state)
        updateContentsList()
    }

    override fun destroy() {
        super.destroy()

        if (chatMessage.isReply) {
            replyData.value?.destroy()
        }

        contents.value.orEmpty().forEach(ChatMessageContentData::destroy)
        chatMessage.removeListener(listener)
        contentListener = null
    }

    fun updateBubbleBackground(hasPrevious: Boolean, hasNext: Boolean) {
        hasPreviousMessage = hasPrevious
        hasNextMessage = hasNext
        hideTime.value = false
        hideAvatar.value = false

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

    fun setContentClickListener(listener: OnContentClickedListener) {
        contentListener = listener

        for (data in contents.value.orEmpty()) {
            data.listener = listener
        }
    }

    private fun updateChatMessageState(state: ChatMessage.State) {
        sendInProgress.value = when (state) {
            ChatMessage.State.InProgress, ChatMessage.State.FileTransferInProgress, ChatMessage.State.FileTransferDone -> true
            else -> false
        }

        showImdn.value = when (state) {
            ChatMessage.State.DeliveredToUser, ChatMessage.State.Displayed,
            ChatMessage.State.NotDelivered, ChatMessage.State.FileTransferError -> true
            else -> false
        }

        imdnIcon.value = when (state) {
            ChatMessage.State.DeliveredToUser -> R.drawable.chat_delivered
            ChatMessage.State.Displayed -> R.drawable.chat_read
            ChatMessage.State.FileTransferError, ChatMessage.State.NotDelivered -> R.drawable.chat_error
            else -> R.drawable.chat_error
        }

        isDisplayed.value = state == ChatMessage.State.Displayed
    }

    private fun updateContentsList() {
        contents.value.orEmpty().forEach(ChatMessageContentData::destroy)
        val list = arrayListOf<ChatMessageContentData>()

        val contentsList = chatMessage.contents
        for (index in contentsList.indices) {
            val content = contentsList[index]
            if (content.isFileTransfer || content.isFile || content.isIcalendar) {
                val data = ChatMessageContentData(chatMessage, index)
                data.listener = contentListener
                list.add(data)
            } else if (content.isText) {
                val spannable = Spannable.Factory.getInstance().newSpannable(content.utf8Text?.trim())
                text.value = PatternClickableSpan()
                    .add(
                        Pattern.compile("(?:<?sips?:)?[a-zA-Z0-9+_.\\-]+(?:@([a-zA-Z0-9+_.\\-;=]+))+(>)?"),
                        object : PatternClickableSpan.SpannableClickedListener {
                            override fun onSpanClicked(text: String) {
                                Log.i("[Chat Message Data] Clicked on SIP URI: $text")
                                contentListener?.onSipAddressClicked(text)
                            }
                        }
                    )
                    .add(
                        Patterns.WEB_URL,
                        object : PatternClickableSpan.SpannableClickedListener {
                            override fun onSpanClicked(text: String) {
                                Log.i("[Chat Message Data] Clicked on web URL: $text")
                                contentListener?.onWebUrlClicked(text)
                            }
                        }
                    )
                    .add(
                        Patterns.PHONE,
                        object : PatternClickableSpan.SpannableClickedListener {
                            override fun onSpanClicked(text: String) {
                                Log.i("[Chat Message Data] Clicked on phone number: $text")
                                contentListener?.onSipAddressClicked(text)
                            }
                        }
                    ).build(spannable)
            } else {
                Log.e("[Chat Message Data] Unexpected content with type: ${content.type}/${content.subtype}")
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
}
