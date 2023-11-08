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

import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.lifecycle.MutableLiveData
import java.util.regex.Pattern
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.core.Address
import org.linphone.core.ChatMessage
import org.linphone.core.ChatMessageListenerStub
import org.linphone.core.ChatMessageReaction
import org.linphone.core.Content
import org.linphone.core.Factory
import org.linphone.core.tools.Log
import org.linphone.ui.main.contacts.model.ContactAvatarModel
import org.linphone.utils.AppUtils
import org.linphone.utils.Event
import org.linphone.utils.LinphoneUtils
import org.linphone.utils.PatternClickableSpan
import org.linphone.utils.SpannableClickedListener
import org.linphone.utils.TimestampUtils

class ChatMessageModel @WorkerThread constructor(
    val chatMessage: ChatMessage,
    val avatarModel: ContactAvatarModel,
    val isFromGroup: Boolean,
    val isReply: Boolean,
    val replyTo: String,
    val replyText: String,
    val replyToMessageId: String?,
    val isGroupedWithPreviousOne: Boolean,
    val isGroupedWithNextOne: Boolean,
    private val onContentClicked: ((file: String) -> Unit)? = null,
    private val onJoinConferenceClicked: ((uri: String) -> Unit)? = null
) {
    companion object {
        private const val TAG = "[Chat Message Model]"

        private const val SIP_URI_REGEXP = "(?:<?sips?:)[a-zA-Z0-9+_.\\-]+(?:@([a-zA-Z0-9+_.\\-;=~]+))+(>)?"
        private const val MENTION_REGEXP = "@(?:[A-Za-z0-9._-]+)"
    }

    val id = chatMessage.messageId

    val isOutgoing = chatMessage.isOutgoing

    val statusIcon = MutableLiveData<Int>()

    val text = MutableLiveData<Spannable>()

    val timestamp = chatMessage.time

    val time = TimestampUtils.toString(timestamp)

    val chatRoomIsReadOnly = chatMessage.chatRoom.isReadOnly

    val reactions = MutableLiveData<String>()

    val imagesList = MutableLiveData<ArrayList<FileModel>>()

    val firstImage = MutableLiveData<FileModel>()

    // Below are for conferences info
    val meetingFound = MutableLiveData<Boolean>()

    val meetingDay = MutableLiveData<String>()

    val meetingDayNumber = MutableLiveData<String>()

    val meetingSubject = MutableLiveData<String>()

    val meetingDate = MutableLiveData<String>()

    val meetingTime = MutableLiveData<String>()

    val meetingDescription = MutableLiveData<String>()

    val meetingParticipants = MutableLiveData<String>()

    private lateinit var meetingConferenceUri: Address
    // End of conference info related fields

    val dismissLongPressMenuEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    private val chatMessageListener = object : ChatMessageListenerStub() {
        @WorkerThread
        override fun onMsgStateChanged(message: ChatMessage, messageState: ChatMessage.State?) {
            statusIcon.postValue(LinphoneUtils.getChatIconResId(chatMessage.state))
        }

        @WorkerThread
        override fun onNewMessageReaction(message: ChatMessage, reaction: ChatMessageReaction) {
            Log.i(
                "$TAG New reaction [${reaction.body}] from [${reaction.fromAddress.asStringUriOnly()}] for chat message with ID [$id]"
            )
            updateReactionsList()
        }

        @WorkerThread
        override fun onReactionRemoved(message: ChatMessage, address: Address) {
            Log.i("$TAG A reaction was removed for chat message with ID [$id]")
            updateReactionsList()
        }
    }

    init {
        chatMessage.addListener(chatMessageListener)
        statusIcon.postValue(LinphoneUtils.getChatIconResId(chatMessage.state))
        updateReactionsList()

        var displayableContentFound = false
        var filesContentCount = 0
        val imagesPath = arrayListOf<FileModel>()

        val contents = chatMessage.contents
        for (content in contents) {
            if (content.isIcalendar) {
                parseConferenceInvite(content)
                displayableContentFound = true
            } else if (content.isText) {
                computeTextContent(content)
                displayableContentFound = true
            } else {
                filesContentCount += 1
                if (content.isFile) {
                    val path = content.filePath ?: ""
                    if (path.isNotEmpty()) {
                        Log.i(
                            "$TAG Found file ready to be displayed [$path] with MIME [${content.type}/${content.subtype}] for message [${chatMessage.messageId}]"
                        )
                        when (content.type) {
                            "image", "video" -> {
                                val fileModel = FileModel(path) { file ->
                                    onContentClicked?.invoke(file)
                                }
                                imagesPath.add(fileModel)

                                if (filesContentCount == 1) {
                                    firstImage.postValue(fileModel)
                                }

                                displayableContentFound = true
                            }
                            "audio" -> {
                            }
                            else -> {
                            }
                        }
                    } else {
                        Log.i("$TAG Content path is empty : have to download it first")
                        // TODO: download it
                    }
                } else {
                    Log.i("$TAG Content is not a File")
                }
            }
        }

        imagesList.postValue(imagesPath)

        if (!displayableContentFound) { // Temporary workaround to prevent empty bubbles
            val describe = LinphoneUtils.getTextDescribingMessage(chatMessage)
            val spannable = Spannable.Factory.getInstance().newSpannable(describe)
            text.postValue(spannable)
        }
    }

    @WorkerThread
    fun destroy() {
        chatMessage.removeListener(chatMessageListener)
    }

    @UiThread
    fun sendReaction(emoji: String) {
        coreContext.postOnCoreThread {
            Log.i("$TAG Sending reaction [$emoji] to chat message with ID [$id]")
            val reaction = chatMessage.createReaction(emoji)
            reaction.send()
            dismissLongPressMenuEvent.postValue(Event(true))
        }
    }

    @UiThread
    fun joinConference() {
        coreContext.postOnCoreThread {
            if (::meetingConferenceUri.isInitialized) {
                val uri = meetingConferenceUri.asStringUriOnly()
                coreContext.postOnMainThread {
                    onJoinConferenceClicked?.invoke(uri)
                }
                /*Log.i("$TAG Calling conference URI [${meetingConferenceUri.asStringUriOnly()}]")
                coreContext.startCall(meetingConferenceUri)*/
            }
        }
    }

    @WorkerThread
    private fun updateReactionsList() {
        var reactionsList = ""
        val allReactions = chatMessage.reactions

        var sameReactionTwiceOrMore = false
        if (allReactions.isNotEmpty()) {
            for (reaction in allReactions) {
                val body = reaction.body
                if (!reactionsList.contains(body)) {
                    reactionsList += body
                } else {
                    sameReactionTwiceOrMore = true
                }
            }

            if (sameReactionTwiceOrMore) {
                reactionsList += allReactions.size.toString()
            }
        }

        Log.i("$TAG Reactions for message [$id] are [$reactionsList]")
        reactions.postValue(reactionsList)
    }

    @WorkerThread
    private fun computeTextContent(content: Content) {
        val textContent = content.utf8Text.orEmpty().trim()
        Log.i("$TAG Found text content [$textContent] for message [${chatMessage.messageId}]")
        val spannableBuilder = SpannableStringBuilder(textContent)

        // Check for mentions
        val chatRoom = chatMessage.chatRoom
        val matcher = Pattern.compile(MENTION_REGEXP).matcher(textContent)
        while (matcher.find()) {
            val start = matcher.start()
            val end = matcher.end()
            val source = textContent.subSequence(start + 1, end) // +1 to remove @
            Log.i("$TAG Found mention [$source]")

            // Find address matching username
            val address = if (chatRoom.localAddress.username == source) {
                coreContext.core.accountList.find {
                    it.params.identityAddress?.username == source
                }?.params?.identityAddress
            } else if (chatRoom.peerAddress.username == source) {
                chatRoom.peerAddress
            } else {
                chatRoom.participants.find {
                    it.address.username == source
                }?.address
            }
            // Find display name for address
            if (address != null) {
                val displayName = coreContext.contactsManager.findDisplayName(address)
                Log.i(
                    "$TAG Using display name [$displayName] instead of username [$source]"
                )
                spannableBuilder.replace(start, end, "@$displayName")
                val span = PatternClickableSpan.StyledClickableSpan(
                    object :
                        SpannableClickedListener {
                        override fun onSpanClicked(text: String) {
                            Log.i("$TAG Clicked on [$text] span")
                            // TODO
                        }
                    }
                )
                spannableBuilder.setSpan(
                    span,
                    start,
                    start + displayName.length + 1,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }

        // Add clickable span for SIP URIs
        text.postValue(
            PatternClickableSpan()
                .add(
                    Pattern.compile(
                        SIP_URI_REGEXP
                    ),
                    object : SpannableClickedListener {
                        @UiThread
                        override fun onSpanClicked(text: String) {
                            coreContext.postOnCoreThread {
                                Log.i("$TAG Clicked on SIP URI: $text")
                                val address = coreContext.core.interpretUrl(text)
                                if (address != null) {
                                    coreContext.startCall(address)
                                } else {
                                    Log.w("$TAG Failed to parse [$text] as SIP URI")
                                }
                            }
                        }
                    }
                )
                .build(spannableBuilder)
        )
    }

    @WorkerThread
    private fun parseConferenceInvite(content: Content) {
        val conferenceInfo = Factory.instance().createConferenceInfoFromIcalendarContent(content)
        val conferenceAddress = conferenceInfo?.uri
        val conferenceUri = conferenceAddress?.asStringUriOnly()
        if (conferenceInfo != null && conferenceAddress != null) {
            Log.i(
                "$TAG Found conference info with URI [$conferenceUri] and subject [${conferenceInfo.subject}]"
            )
            meetingConferenceUri = conferenceAddress

            meetingSubject.postValue(conferenceInfo.subject)
            meetingDescription.postValue(conferenceInfo.description)

            val timestamp = conferenceInfo.dateTime
            val duration = conferenceInfo.duration
            val date = TimestampUtils.toString(
                timestamp,
                onlyDate = true,
                shortDate = false,
                hideYear = false
            )
            val startTime = TimestampUtils.timeToString(timestamp)
            val end = timestamp + (duration * 60)
            val endTime = TimestampUtils.timeToString(end)
            meetingDate.postValue(date)
            meetingTime.postValue("$startTime - $endTime")

            meetingDay.postValue(TimestampUtils.dayOfWeek(timestamp))
            meetingDayNumber.postValue(TimestampUtils.dayOfMonth(timestamp))

            var count = 0
            for (info in conferenceInfo.participantInfos) {
                count += 1
            }
            meetingParticipants.postValue(
                AppUtils.getFormattedString(R.string.conference_participants_list_title, "$count")
            )

            meetingFound.postValue(true)
        }
    }
}
