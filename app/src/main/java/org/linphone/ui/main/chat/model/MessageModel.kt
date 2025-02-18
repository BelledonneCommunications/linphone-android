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

import android.graphics.Typeface
import android.os.CountDownTimer
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StyleSpan
import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.media.AudioFocusRequestCompat
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.regex.Pattern
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.R
import org.linphone.core.Address
import org.linphone.core.ChatMessage
import org.linphone.core.ChatMessageListenerStub
import org.linphone.core.ChatMessageReaction
import org.linphone.core.ChatRoom
import org.linphone.core.ConferenceInfo
import org.linphone.core.Content
import org.linphone.core.Factory
import org.linphone.core.Player
import org.linphone.core.PlayerListener
import org.linphone.core.tools.Log
import org.linphone.ui.main.contacts.model.ContactAvatarModel
import org.linphone.utils.AppUtils
import org.linphone.utils.AudioUtils
import org.linphone.utils.Event
import org.linphone.utils.FileUtils
import org.linphone.utils.LinphoneUtils
import org.linphone.utils.PatternClickableSpan
import org.linphone.utils.SpannableClickedListener
import org.linphone.utils.TimestampUtils

class MessageModel
    @WorkerThread
    constructor(
    val chatMessage: ChatMessage,
    val isFromGroup: Boolean,
    val isReply: Boolean,
    val replyTo: String,
    val replyText: String,
    val replyToMessageId: String?,
    val isForward: Boolean,
    isGroupedWithPreviousOne: Boolean,
    isGroupedWithNextOne: Boolean,
    private val currentFilter: String = "",
    private val onContentClicked: ((fileModel: FileModel) -> Unit)? = null,
    private val onJoinConferenceClicked: ((uri: String) -> Unit)? = null,
    private val onWebUrlClicked: ((url: String) -> Unit)? = null,
    private val onContactClicked: ((friendRefKey: String) -> Unit)? = null,
    private val onRedToastToShow: ((pair: Pair<Int, Int>) -> Unit)? = null,
    private val onVoiceRecordingPlaybackEnded: ((id: String) -> Unit)? = null,
    private val onFileToExportToNativeGallery: ((path: String) -> Unit)? = null
) {
    companion object {
        private const val TAG = "[Message Model]"

        private const val SIP_URI_REGEXP = "(<?sips?:)[a-zA-Z0-9+_.\\-]+(?:@([a-zA-Z0-9+_.\\-;=~]+))+(>)?"
        private const val HTTP_LINK_REGEXP = "https?://(www\\.)?[-a-zA-Z0-9@:%._+~#=]{1,256}\\.[a-zA-Z0-9()]{1,6}\\b([-a-zA-Z0-9()@:%_+.~#?&/=]*)"
        private const val MENTION_REGEXP = "@([A-Za-z0-9._-]+)"
    }

    val id = chatMessage.messageId

    var isRead = chatMessage.isRead

    val isOutgoing = chatMessage.isOutgoing

    val isInError = MutableLiveData<Boolean>()

    val timestamp = chatMessage.time

    val time = TimestampUtils.toString(timestamp)

    val chatRoomIsReadOnly = chatMessage.chatRoom.isReadOnly ||
        (
            !chatMessage.chatRoom.hasCapability(ChatRoom.Capabilities.Encrypted.toInt()) && LinphoneUtils.getAccountForAddress(
                chatMessage.chatRoom.localAddress
            )?.params?.instantMessagingEncryptionMandatory == true
            )

    val avatarModel = MutableLiveData<ContactAvatarModel>()

    val groupedWithNextMessage = MutableLiveData<Boolean>()

    val groupedWithPreviousMessage = MutableLiveData<Boolean>()

    val statusIcon = MutableLiveData<Int>()

    val isEphemeral = MutableLiveData<Boolean>()

    val ephemeralLifetime = MutableLiveData<String>()

    val text = MutableLiveData<Spannable>()

    val reactions = MutableLiveData<String>()

    val ourReactionIndex = MutableLiveData<Int>()

    val filesList = MutableLiveData<ArrayList<FileModel>>()

    val firstFileModel = MediatorLiveData<FileModel>()

    val isSelected = MutableLiveData<Boolean>()

    // Below are for conferences info
    val meetingFound = MutableLiveData<Boolean>()

    val meetingUpdated = MutableLiveData<Boolean>()

    val meetingCancelled = MutableLiveData<Boolean>()

    val meetingDay = MutableLiveData<String>()

    val meetingDayNumber = MutableLiveData<String>()

    val meetingSubject = MutableLiveData<String>()

    val meetingDate = MutableLiveData<String>()

    val meetingTime = MutableLiveData<String>()

    val meetingDescription = MutableLiveData<String>()

    val meetingParticipants = MutableLiveData<String>()

    private lateinit var meetingConferenceUri: Address
    // End of conference info related fields

    // Voice record related fields
    val isVoiceRecord = MutableLiveData<Boolean>()

    val isPlayingVoiceRecord = MutableLiveData<Boolean>()

    val voiceRecordPlayerPosition = MutableLiveData<Int>()

    val voiceRecordingDuration = MutableLiveData<Int>()

    val formattedVoiceRecordingDuration = MutableLiveData<String>()

    val dismissLongPressMenuEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    var isTextHighlighted = false

    private var voiceRecordAudioFocusRequest: AudioFocusRequestCompat? = null

    private lateinit var voiceRecordPath: String

    private lateinit var voiceRecordPlayer: Player

    private val playerListener = PlayerListener {
        Log.i("$TAG End of file reached")
        stopVoiceRecordPlayer()
        onVoiceRecordingPlaybackEnded?.invoke(id)
    }
    // End of voice record related fields

    private lateinit var countDownTimer: CountDownTimer

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var transferringFileModel: FileModel? = null

    private var allFilesDownloaded = true

    private val chatMessageListener = object : ChatMessageListenerStub() {
        @WorkerThread
        override fun onMsgStateChanged(message: ChatMessage, messageState: ChatMessage.State?) {
            if (messageState != ChatMessage.State.FileTransferDone && messageState != ChatMessage.State.FileTransferInProgress) {
                statusIcon.postValue(LinphoneUtils.getChatIconResId(chatMessage.state))

                if (messageState == ChatMessage.State.Displayed) {
                    isRead = chatMessage.isRead
                }
            } else if (messageState == ChatMessage.State.FileTransferDone) {
                Log.i("$TAG File transfer is done")
                transferringFileModel?.updateTransferProgress(-1)
                transferringFileModel = null
                if (!allFilesDownloaded) {
                    computeContentsList()
                }

                for (content in message.contents) {
                    if (content.isVoiceRecording) {
                        Log.i("$TAG File transfer done, updating voice record info")
                        computeVoiceRecordContent(content)
                        break
                    }
                }
            }
            isInError.postValue(messageState == ChatMessage.State.NotDelivered)
        }

        @WorkerThread
        override fun onFileTransferTerminated(message: ChatMessage, content: Content) {
            Log.i("$TAG File [${content.name}] from message [${message.messageId}] transfer terminated")

            // Never do auto media export for ephemeral messages!
            if (corePreferences.makePublicMediaFilesDownloaded && !message.isEphemeral) {
                val path = content.filePath
                if (path.isNullOrEmpty()) return

                val mime = "${content.type}/${content.subtype}"
                val mimeType = FileUtils.getMimeType(mime)
                when (mimeType) {
                    FileUtils.MimeType.Image, FileUtils.MimeType.Video, FileUtils.MimeType.Audio -> {
                        Log.i("$TAG Exporting file path [$path] to the native media gallery")
                        onFileToExportToNativeGallery?.invoke(path)
                    }
                    else -> {}
                }
            }
        }

        @WorkerThread
        override fun onNewMessageReaction(message: ChatMessage, reaction: ChatMessageReaction) {
            Log.i(
                "$TAG New reaction [${reaction.body}] from [${reaction.fromAddress.asStringUriOnly()}] for message with ID [$id]"
            )
            updateReactionsList()
        }

        @WorkerThread
        override fun onReactionRemoved(message: ChatMessage, address: Address) {
            Log.i("$TAG A reaction was removed for message with ID [$id]")
            updateReactionsList()
        }

        @WorkerThread
        override fun onFileTransferProgressIndication(
            message: ChatMessage,
            content: Content,
            offset: Int,
            total: Int
        ) {
            val percent = ((offset * 100.0) / total).toInt() // Conversion from int to double and back to int is required

            if (transferringFileModel == null) {
                Log.w("$TAG A file is being uploaded/downloaded but no transferringFileModel set!")
                val found = filesList.value.orEmpty().find {
                    it.fileName == content.name
                }
                if (found != null) {
                    transferringFileModel = found
                    Log.i("$TAG Found matching FileModel in files list using content name")
                } else {
                    Log.w(
                        "$TAG Failed to find a matching FileModel in files list with content name [${content.name}]"
                    )
                }
            }
            transferringFileModel?.updateTransferProgress(percent)
        }

        @WorkerThread
        override fun onEphemeralMessageTimerStarted(message: ChatMessage) {
            Log.d("$TAG Ephemeral timer started")
            updateEphemeralTimer()
        }
    }

    init {
        updateAvatarModel()

        isInError.postValue(chatMessage.state == ChatMessage.State.NotDelivered)

        groupedWithNextMessage.postValue(isGroupedWithNextOne)
        groupedWithPreviousMessage.postValue(isGroupedWithPreviousOne)
        isPlayingVoiceRecord.postValue(false)
        isEphemeral.postValue(chatMessage.isEphemeral)
        updateEphemeralTimer()

        chatMessage.addListener(chatMessageListener)
        statusIcon.postValue(LinphoneUtils.getChatIconResId(chatMessage.state))
        updateReactionsList()

        computeContentsList()

        coreContext.postOnMainThread {
            firstFileModel.addSource(filesList) {
                val first = it.firstOrNull()
                if (first != null) {
                    firstFileModel.value = first!!
                }
            }
        }
    }

    @WorkerThread
    fun destroy() {
        scope.cancel()

        filesList.value.orEmpty().forEach(FileModel::destroy)

        if (::voiceRecordPlayer.isInitialized) {
            stopVoiceRecordPlayer()
            voiceRecordPlayer.removeListener(playerListener)
        }

        chatMessage.removeListener(chatMessageListener)
    }

    @UiThread
    fun sendReaction(emoji: String) {
        coreContext.postOnCoreThread {
            if (chatMessage.ownReaction?.body == emoji) {
                Log.i("$TAG Removing our existing reaction [$emoji] to message with ID [$id]")
                val reaction = chatMessage.createReaction("")
                reaction.send()
            } else {
                Log.i("$TAG Sending reaction [$emoji] to message with ID [$id]")
                val reaction = chatMessage.createReaction(emoji)
                reaction.send()
            }
            dismissLongPressMenuEvent.postValue(Event(true))
        }
    }

    @UiThread
    fun resend() {
        coreContext.postOnCoreThread {
            Log.i("$TAG Re-sending message with ID [$id]")
            chatMessage.send()
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
            }
        }
    }

    @UiThread
    fun togglePlayPauseVoiceRecord() {
        coreContext.postOnCoreThread {
            if (isPlayingVoiceRecord.value == false) {
                startVoiceRecordPlayer()
            } else {
                pauseVoiceRecordPlayer()
            }
        }
    }

    @UiThread
    fun markAsRead() {
        coreContext.postOnCoreThread {
            Log.i("$TAG Marking chat message with ID [$id] as read")
            chatMessage.markAsRead()
        }
    }

    @WorkerThread
    fun updateAvatarModel() {
        val avatar = coreContext.contactsManager.getContactAvatarModelForAddress(
            chatMessage.fromAddress
        )
        avatarModel.postValue(avatar)
    }

    @WorkerThread
    private fun computeContentsList() {
        Log.d("$TAG Computing message contents list")
        text.postValue(Spannable.Factory.getInstance().newSpannable(""))
        filesList.postValue(arrayListOf())

        var displayableContentFound = false
        var filesContentCount = 0
        val filesPath = arrayListOf<FileModel>()

        val contents = chatMessage.contents
        allFilesDownloaded = true

        val notMediaContent = contents.find {
            it.isIcalendar || it.isVoiceRecording || (it.isText && !it.isFile) || it.isFileTransfer || (it.isFile && !(it.type == "video" || it.type == "image"))
        }
        val allContentsAreMedia = notMediaContent == null
        val exactly4Contents = contents.size == 4

        for (content in contents) {
            val isFileEncrypted = content.isFileEncrypted

            if (content.isIcalendar) {
                Log.d("$TAG Found iCal content")
                parseConferenceInvite(content)

                displayableContentFound = true
            } else if (content.isText && !content.isFile) {
                Log.d("$TAG Found plain text content")
                computeTextContent(content, currentFilter)

                displayableContentFound = true
            } else if (content.isVoiceRecording) {
                Log.d("$TAG Found voice recording content")
                isVoiceRecord.postValue(true)
                computeVoiceRecordContent(content)

                displayableContentFound = true
            } else {
                if (content.isFile) {
                    Log.d("$TAG Found file content with type [${content.type}/${content.subtype}]")
                    filesContentCount += 1

                    checkAndRepairFilePathIfNeeded(content)

                    val originalPath = content.filePath.orEmpty()
                    val path = if (isFileEncrypted) {
                        Log.d(
                            "$TAG [VFS] Content is encrypted, requesting plain file path for file [${content.filePath}]"
                        )
                        content.exportPlainFile()
                    } else {
                        originalPath
                    }
                    val name = content.name ?: ""
                    if (path.isNotEmpty()) {
                        Log.d(
                            "$TAG Found file ready to be displayed [$path] with MIME [${content.type}/${content.subtype}] for message [${chatMessage.messageId}]"
                        )

                        val wrapBefore = allContentsAreMedia && exactly4Contents && filesContentCount == 3
                        val fileSize = content.fileSize.toLong()
                        val timestamp = content.creationTimestamp
                        val fileModel = FileModel(
                            path,
                            name,
                            fileSize,
                            timestamp,
                            isFileEncrypted,
                            originalPath,
                            chatMessage.isEphemeral,
                            flexboxLayoutWrapBefore = wrapBefore
                        ) { model ->
                            onContentClicked?.invoke(model)
                        }
                        filesPath.add(fileModel)

                        displayableContentFound = true
                    } else {
                        Log.e("$TAG No path found for File Content!")
                    }
                } else if (content.isFileTransfer) {
                    Log.d(
                        "$TAG Found file content (not downloaded yet) with type [${content.type}/${content.subtype}] and name [${content.name}]"
                    )
                    allFilesDownloaded = false
                    filesContentCount += 1
                    val name = content.name ?: ""
                    val timestamp = content.creationTimestamp
                    if (name.isNotEmpty()) {
                        val fileModel = if (isOutgoing && chatMessage.isFileTransferInProgress) {
                            val path = content.filePath.orEmpty()
                            FileModel(
                                path,
                                name,
                                content.fileSize.toLong(),
                                timestamp,
                                isFileEncrypted,
                                path,
                                chatMessage.isEphemeral
                            ) { model ->
                                onContentClicked?.invoke(model)
                            }
                        } else {
                            FileModel(
                                name,
                                name,
                                content.fileSize.toLong(),
                                timestamp,
                                isFileEncrypted,
                                name,
                                chatMessage.isEphemeral,
                                isWaitingToBeDownloaded = true
                            ) { model ->
                                downloadContent(model, content)
                            }
                        }
                        filesPath.add(fileModel)

                        displayableContentFound = true
                    } else {
                        Log.e("$TAG No name found for FileTransfer Content!")
                    }
                } else {
                    Log.w("$TAG Content [${content.name}] is not a File")
                }
            }
        }

        filesList.postValue(filesPath)

        if (!displayableContentFound) { // Temporary workaround to prevent empty bubbles
            val describe = LinphoneUtils.getFormattedTextDescribingMessage(chatMessage)
            Log.w(
                "$TAG No displayable content found, generating text based description [$describe]"
            )
            text.postValue(describe)
        }
    }

    @WorkerThread
    private fun downloadContent(model: FileModel, content: Content) {
        Log.d("$TAG Starting downloading content for file [${model.fileName}]")

        if (content.filePath.orEmpty().isEmpty()) {
            val contentName = content.name
            if (contentName != null) {
                val isImage = FileUtils.isExtensionImage(contentName)
                val file = FileUtils.getFileStoragePath(contentName, isImage)
                content.filePath = file.path
                Log.i(
                    "$TAG File [$contentName] will be downloaded at [${content.filePath}]"
                )

                model.updateTransferProgress(0)
                transferringFileModel = model
                chatMessage.downloadContent(content)
            } else {
                Log.e("$TAG Content name is null, can't download it!")
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
                val count = allReactions.size.toString()
                reactionsList += " $count"
            }
        }
        Log.d("$TAG Reactions for message [$id] are [$reactionsList]")

        val ourOwnReaction = chatMessage.ownReaction
        if (ourOwnReaction != null) {
            val index = when (ourOwnReaction.body) {
                AppUtils.getString(R.string.emoji_thumbs_up) -> 0
                AppUtils.getString(R.string.emoji_love) -> 1
                AppUtils.getString(R.string.emoji_laughing) -> 2
                AppUtils.getString(R.string.emoji_surprised) -> 3
                AppUtils.getString(R.string.emoji_tear) -> 4
                else -> -1
            }
            ourReactionIndex.postValue(index)
        } else {
            ourReactionIndex.postValue(-1)
        }

        reactions.postValue(reactionsList)
    }

    @WorkerThread
    fun highlightText(highlight: String) {
        if (isTextHighlighted && highlight.isEmpty()) {
            isTextHighlighted = false
        }

        val textContent = chatMessage.contents.find {
            it.isText
        }
        if (textContent != null) {
            computeTextContent(textContent, highlight)
        }
    }

    @WorkerThread
    private fun computeTextContent(content: Content, highlight: String) {
        val textContent = content.utf8Text.orEmpty().trim()
        val spannableBuilder = SpannableStringBuilder(textContent)

        // Check for search
        if (highlight.isNotEmpty()) {
            val indexStart = textContent.indexOf(highlight, 0, ignoreCase = true)
            if (indexStart >= 0) {
                isTextHighlighted = true
                val indexEnd = indexStart + highlight.length
                spannableBuilder.setSpan(
                    StyleSpan(Typeface.BOLD),
                    indexStart,
                    indexEnd,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }

        // Check for mentions
        val chatRoom = chatMessage.chatRoom
        val matcher = Pattern.compile(MENTION_REGEXP).matcher(textContent)
        while (matcher.find()) {
            val start = matcher.start()
            val end = matcher.end()
            val source = textContent.subSequence(start + 1, end) // +1 to remove @
            Log.d("$TAG Found mention [$source]")

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
                val avatarModel = coreContext.contactsManager.getContactAvatarModelForAddress(
                    address
                )
                val friend = avatarModel.friend
                val displayName = friend.name ?: LinphoneUtils.getDisplayName(address)
                Log.d(
                    "$TAG Using display name [$displayName] instead of username [$source]"
                )

                spannableBuilder.replace(start, end, "@$displayName")
                val span = PatternClickableSpan.StyledClickableSpan(
                    object :
                        SpannableClickedListener {
                        override fun onSpanClicked(text: String) {
                            val friendRefKey = friend.refKey ?: ""
                            Log.i(
                                "$TAG Clicked on [$text] span, matching friend ref key is [$friendRefKey]"
                            )
                            if (friendRefKey.isNotEmpty()) {
                                onContactClicked?.invoke(friendRefKey)
                            }
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
                                val address = coreContext.core.interpretUrl(text, false)
                                if (address != null) {
                                    coreContext.startAudioCall(address)
                                } else {
                                    Log.w("$TAG Failed to parse [$text] as SIP URI")
                                }
                            }
                        }
                    }
                )
                .add(
                    Pattern.compile(
                        HTTP_LINK_REGEXP
                    ),
                    object : SpannableClickedListener {
                        override fun onSpanClicked(text: String) {
                            Log.i("$TAG Clicked on web URL: $text")
                            onWebUrlClicked?.invoke(text)
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

            meetingUpdated.postValue(conferenceInfo.state == ConferenceInfo.State.Updated)
            meetingCancelled.postValue(conferenceInfo.state == ConferenceInfo.State.Cancelled)

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

            val count = conferenceInfo.participantInfos.size
            meetingParticipants.postValue(
                AppUtils.getStringWithPlural(
                    R.plurals.conference_participants_list_title,
                    count,
                    "$count"
                )
            )

            meetingFound.postValue(true)
        }
    }

    @WorkerThread
    private fun initVoiceRecordPlayer() {
        if (!::voiceRecordPath.isInitialized) {
            Log.e("$TAG No voice record path was set!")
            return
        }

        Log.i("$TAG Creating player for voice record")

        val playbackSoundCard = AudioUtils.getAudioPlaybackDeviceIdForCallRecordingOrVoiceMessage()
        Log.i(
            "$TAG Using device $playbackSoundCard to make the voice message playback"
        )

        val localPlayer = coreContext.core.createLocalPlayer(playbackSoundCard, null, null)
        if (localPlayer != null) {
            voiceRecordPlayer = localPlayer
        } else {
            Log.e("$TAG Couldn't create local player!")
            return
        }
        voiceRecordPlayer.addListener(playerListener)
        Log.i("$TAG Voice record player created")

        val path = voiceRecordPath
        Log.i("$TAG Opening voice record file [$path]")
        if (voiceRecordPlayer.open(path) == 0) {
            val duration = voiceRecordPlayer.duration
            voiceRecordingDuration.postValue(duration)
            val formattedDuration =
                SimpleDateFormat("mm:ss", Locale.getDefault()).format(duration) // duration is in ms
            formattedVoiceRecordingDuration.postValue(formattedDuration)
        } else {
            Log.e("$TAG Player failed to open file at [$path]")
        }
    }

    @WorkerThread
    private fun startVoiceRecordPlayer() {
        if (voiceRecordAudioFocusRequest == null) {
            voiceRecordAudioFocusRequest = AudioUtils.acquireAudioFocusForVoiceRecordingOrPlayback(
                coreContext.context
            )
        }

        if (isPlayerClosed()) {
            Log.w("$TAG Player closed, let's open it first")
            initVoiceRecordPlayer()

            if (voiceRecordPlayer.state == Player.State.Closed) {
                Log.e("$TAG It seems the player fails to open the file, abort playback")
                onRedToastToShow?.invoke(
                    Pair(
                        R.string.conversation_failed_to_play_voice_recording_message,
                        R.drawable.warning_circle
                    )
                )
                return
            }
        }

        val lowMediaVolume = AudioUtils.isMediaVolumeLow(coreContext.context)
        if (lowMediaVolume) {
            Log.w("$TAG Media volume is low, notifying user as they may not hear voice message")
            onRedToastToShow?.invoke(
                Pair(R.string.media_playback_low_volume_warning_toast, R.drawable.speaker_slash)
            )
        }

        Log.i("$TAG Playing voice record")
        isPlayingVoiceRecord.postValue(true)
        voiceRecordPlayer.start()

        playerTickerFlow().onEach {
            coreContext.postOnCoreThread {
                voiceRecordPlayerPosition.postValue(voiceRecordPlayer.currentPosition)
            }
        }.launchIn(scope)
    }

    @WorkerThread
    private fun pauseVoiceRecordPlayer() {
        if (!isPlayerClosed()) {
            Log.i("$TAG Pausing voice record")
            voiceRecordPlayer.pause()
        }

        val request = voiceRecordAudioFocusRequest
        if (request != null) {
            AudioUtils.releaseAudioFocusForVoiceRecordingOrPlayback(
                coreContext.context,
                request
            )
            voiceRecordAudioFocusRequest = null
        }

        isPlayingVoiceRecord.postValue(false)
    }

    @WorkerThread
    private fun isPlayerClosed(): Boolean {
        return !::voiceRecordPlayer.isInitialized || voiceRecordPlayer.state == Player.State.Closed
    }

    @WorkerThread
    private fun stopVoiceRecordPlayer() {
        if (!isPlayerClosed()) {
            Log.i("$TAG Stopping voice record")
            voiceRecordPlayer.pause()
            voiceRecordPlayer.seek(0)
            voiceRecordPlayerPosition.postValue(0)
            voiceRecordPlayer.close()
        }

        voiceRecordPlayerPosition.postValue(0)
        isPlayingVoiceRecord.postValue(false)

        val request = voiceRecordAudioFocusRequest
        if (request != null) {
            AudioUtils.releaseAudioFocusForVoiceRecordingOrPlayback(
                coreContext.context,
                request
            )
            voiceRecordAudioFocusRequest = null
        }

        isPlayingVoiceRecord.postValue(false)
    }

    private fun playerTickerFlow() = flow {
        while (isPlayingVoiceRecord.value == true) {
            emit(Unit)
            delay(10)
        }
    }

    @WorkerThread
    private fun checkAndRepairFilePathIfNeeded(content: Content): String {
        val path = content.filePath ?: ""
        if (path.isEmpty()) return ""
        val name = content.name ?: ""
        if (name.isEmpty()) return ""

        val extension = FileUtils.getExtensionFromFileName(path)
        if (extension.contains("/")) {
            Log.w(
                "$TAG Weird extension [$extension] found for file [$path], trying with file name [$name]"
            )
            val fileExtension = FileUtils.getExtensionFromFileName(name)
            if (!fileExtension.contains("/")) {
                Log.w("$TAG File extension [$fileExtension] seems better, renaming file")
                val newPath = FileUtils.renameFile(path, name)
                if (newPath.isNotEmpty()) {
                    content.filePath = newPath
                    Log.w("$TAG File [$path] has been renamed [${content.filePath}]")
                    return newPath
                } else {
                    Log.e("$TAG Failed to rename file!")
                }
            }
        }

        return ""
    }

    @WorkerThread
    private fun updateEphemeralTimer() {
        if (chatMessage.isEphemeral) {
            if (chatMessage.ephemeralExpireTime == 0L) {
                // This means the message hasn't been read by all participants yet, so the countdown hasn't started
                // In this case we simply display the configured value for lifetime
                ephemeralLifetime.postValue(
                    TimestampUtils.formatLifetime(chatMessage.ephemeralLifetime)
                )
            } else {
                // Countdown has started, display remaining time
                val remaining = chatMessage.ephemeralExpireTime - (System.currentTimeMillis() / 1000)
                ephemeralLifetime.postValue(TimestampUtils.formatLifetime(remaining))
                if (!::countDownTimer.isInitialized) {
                    countDownTimer = object : CountDownTimer(remaining * 1000, 1000) {
                        override fun onFinish() {}

                        override fun onTick(millisUntilFinished: Long) {
                            ephemeralLifetime.postValue(
                                TimestampUtils.formatLifetime(millisUntilFinished / 1000)
                            )
                        }
                    }
                    countDownTimer.start()
                }
            }
        }
    }

    @WorkerThread
    private fun computeVoiceRecordContent(content: Content) {
        voiceRecordPath = content.filePath ?: ""

        val duration = content.fileDuration
        voiceRecordingDuration.postValue(duration)

        val formattedDuration = SimpleDateFormat(
            "mm:ss",
            Locale.getDefault()
        ).format(duration) // duration is in ms
        formattedVoiceRecordingDuration.postValue(formattedDuration)
        Log.i(
            "$TAG Found voice record with path [$voiceRecordPath] and duration [$formattedDuration]"
        )
    }
}
