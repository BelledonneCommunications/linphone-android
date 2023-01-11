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

import android.text.Spannable
import android.text.SpannableString
import android.text.Spanned
import android.text.style.UnderlineSpan
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.lifecycle.MutableLiveData
import androidx.media.AudioFocusRequestCompat
import java.io.BufferedReader
import java.io.FileReader
import java.lang.StringBuilder
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.core.*
import org.linphone.core.tools.Log
import org.linphone.utils.AppUtils
import org.linphone.utils.AudioRouteUtils
import org.linphone.utils.FileUtils
import org.linphone.utils.TimestampUtils

class ChatMessageContentData(
    private val chatMessage: ChatMessage,
    private val contentIndex: Int,
) {
    var listener: OnContentClickedListener? = null

    val isOutgoing = chatMessage.isOutgoing

    val isImage = MutableLiveData<Boolean>()
    val isVideo = MutableLiveData<Boolean>()
    val isAudio = MutableLiveData<Boolean>()
    val isPdf = MutableLiveData<Boolean>()
    val isGenericFile = MutableLiveData<Boolean>()
    val isVoiceRecording = MutableLiveData<Boolean>()
    val isConferenceSchedule = MutableLiveData<Boolean>()
    val isConferenceUpdated = MutableLiveData<Boolean>()
    val isConferenceCancelled = MutableLiveData<Boolean>()

    val fileName = MutableLiveData<String>()
    val filePath = MutableLiveData<String>()

    val downloadable = MutableLiveData<Boolean>()
    val fileTransferProgress = MutableLiveData<Boolean>()
    val fileTransferProgressInt = MutableLiveData<Int>()
    val downloadLabel = MutableLiveData<Spannable>()

    val voiceRecordDuration = MutableLiveData<Int>()
    val formattedDuration = MutableLiveData<String>()
    val voiceRecordPlayingPosition = MutableLiveData<Int>()
    val isVoiceRecordPlaying = MutableLiveData<Boolean>()

    val conferenceSubject = MutableLiveData<String>()
    val conferenceDescription = MutableLiveData<String>()
    val conferenceParticipantCount = MutableLiveData<String>()
    val conferenceDate = MutableLiveData<String>()
    val conferenceTime = MutableLiveData<String>()
    val conferenceDuration = MutableLiveData<String>()
    var conferenceAddress = MutableLiveData<String>()
    val showDuration = MutableLiveData<Boolean>()

    val isAlone: Boolean
        get() {
            var count = 0
            for (content in chatMessage.contents) {
                if (content.isFileTransfer || content.isFile) {
                    count += 1
                }
            }
            return count == 1
        }

    private var isFileEncrypted: Boolean = false

    private var voiceRecordAudioFocusRequest: AudioFocusRequestCompat? = null

    private lateinit var voiceRecordingPlayer: Player
    private val playerListener = PlayerListener {
        Log.i("[Voice Recording] End of file reached")
        stopVoiceRecording()
    }

    private fun getContent(): Content {
        return chatMessage.contents[contentIndex]
    }

    private val chatMessageListener: ChatMessageListenerStub = object : ChatMessageListenerStub() {
        override fun onFileTransferProgressIndication(
            message: ChatMessage,
            c: Content,
            offset: Int,
            total: Int
        ) {
            if (c.filePath == getContent().filePath) {
                if (fileTransferProgress.value == false) {
                    fileTransferProgress.value = true
                }
                val percent = ((offset * 100.0) / total).toInt() // Conversion from int to double and back to int is required
                Log.d("[Content] Transfer progress is: $offset / $total -> $percent%")
                fileTransferProgressInt.value = percent
            }
        }

        override fun onMsgStateChanged(message: ChatMessage, state: ChatMessage.State) {
            if (state == ChatMessage.State.FileTransferDone || state == ChatMessage.State.FileTransferError) {
                fileTransferProgress.value = false
                updateContent()

                if (state == ChatMessage.State.FileTransferDone) {
                    Log.i("[Chat Message] File transfer done")
                    if (!message.isOutgoing && !message.isEphemeral) {
                        Log.i("[Chat Message] Adding content to media store")
                        coreContext.addContentToMediaStore(getContent())
                    }
                }
            }
        }
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        isVoiceRecordPlaying.value = false
        voiceRecordDuration.value = 0
        voiceRecordPlayingPosition.value = 0
        fileTransferProgress.value = false
        fileTransferProgressInt.value = 0

        updateContent()
        chatMessage.addListener(chatMessageListener)
    }

    fun destroy() {
        scope.cancel()

        deletePlainFilePath()
        chatMessage.removeListener(chatMessageListener)

        if (this::voiceRecordingPlayer.isInitialized) {
            Log.i("[Voice Recording] Destroying voice record")
            stopVoiceRecording()
            voiceRecordingPlayer.removeListener(playerListener)
        }
    }

    fun download() {
        if (chatMessage.isFileTransferInProgress) {
            Log.w("[Content] Another FileTransfer content for this message is currently being downloaded, can't start another one for now")
            listener?.onError(R.string.chat_message_download_already_in_progress)
            return
        }

        val content = getContent()
        val filePath = content.filePath
        if (content.isFileTransfer) {
            if (filePath == null || filePath.isEmpty()) {
                val contentName = content.name
                if (contentName != null) {
                    val file = FileUtils.getFileStoragePath(contentName)
                    content.filePath = file.path
                    Log.i("[Content] Started downloading $contentName into ${content.filePath}")
                } else {
                    Log.e("[Content] Content name is null, can't download it!")
                    return
                }
            } else {
                Log.w("[Content] File path already set [$filePath] using it (auto download that failed probably)")
            }

            if (!chatMessage.downloadContent(content)) {
                Log.e("[Content] Failed to start content download!")
            }
        } else {
            Log.e("[Content] Content is not a FileTransfer, can't download it!")
        }
    }

    fun openFile() {
        listener?.onContentClicked(getContent())
    }

    private fun deletePlainFilePath() {
        val path = filePath.value.orEmpty()
        if (path.isNotEmpty() && isFileEncrypted) {
            Log.i("[Content] Deleting file used for preview: $path")
            FileUtils.deleteFile(path)
            filePath.value = ""
        }
    }

    private fun updateContent() {
        Log.i("[Content] Updating content")
        deletePlainFilePath()

        val content = getContent()
        isFileEncrypted = content.isFileEncrypted
        Log.i("[Content] Is ${if (content.isFile) "file" else "file transfer"} content encrypted ? $isFileEncrypted")

        filePath.value = ""
        fileName.value = if (content.name.isNullOrEmpty() && !content.filePath.isNullOrEmpty()) {
            FileUtils.getNameFromFilePath(content.filePath!!)
        } else {
            content.name
        }

        // Display download size and underline text
        val fileSize = AppUtils.bytesToDisplayableSize(content.fileSize.toLong())
        val spannable = SpannableString("${AppUtils.getString(R.string.chat_message_download_file)} ($fileSize)")
        spannable.setSpan(UnderlineSpan(), 0, spannable.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        downloadLabel.value = spannable

        isImage.value = false
        isVideo.value = false
        isAudio.value = false
        isPdf.value = false
        isVoiceRecording.value = false
        isConferenceSchedule.value = false
        isConferenceUpdated.value = false
        isConferenceCancelled.value = false

        if (content.isFile || (content.isFileTransfer && chatMessage.isOutgoing)) {
            val path = if (isFileEncrypted) {
                Log.i("[Content] Content is encrypted, requesting plain file path")
                content.exportPlainFile()
            } else {
                content.filePath ?: ""
            }
            downloadable.value = content.filePath.orEmpty().isEmpty()

            val isVoiceRecord = content.isVoiceRecording
            isVoiceRecording.value = isVoiceRecord

            val isConferenceIcs = content.isIcalendar
            isConferenceSchedule.value = isConferenceIcs

            if (path.isNotEmpty()) {
                filePath.value = path
                val extension = FileUtils.getExtensionFromFileName(path)
                val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
                isImage.value = FileUtils.isMimeImage(mime)
                isVideo.value = FileUtils.isMimeVideo(mime) && !isVoiceRecord
                isAudio.value = FileUtils.isMimeAudio(mime) && !isVoiceRecord
                isPdf.value = FileUtils.isMimePdf(mime)
                val type = when {
                    isImage.value == true -> "image"
                    isVideo.value == true -> "video"
                    isAudio.value == true -> "audio"
                    isPdf.value == true -> "pdf"
                    isVoiceRecord -> "voice recording"
                    isConferenceIcs -> "conference invitation"
                    else -> "unknown"
                }
                Log.i("[Content] Extension for file [$path] is [$extension], deduced type from MIME is [$type]")

                if (isVoiceRecord) {
                    val duration = content.fileDuration // duration is in ms
                    voiceRecordDuration.value = duration
                    formattedDuration.value = SimpleDateFormat("mm:ss", Locale.getDefault()).format(duration)
                    Log.i("[Content] Voice recording duration is ${voiceRecordDuration.value} ($duration)")
                } else if (isConferenceIcs) {
                    parseConferenceInvite(content)
                }
            } else if (isConferenceIcs) {
                Log.i("[Content] Found content with icalendar file")
                parseConferenceInvite(content)
            } else {
                Log.w("[Content] Found ${if (content.isFile) "file" else "file transfer"} content with empty path...")
                isImage.value = false
                isVideo.value = false
                isAudio.value = false
                isPdf.value = false
                isVoiceRecording.value = false
                isConferenceSchedule.value = false
            }
        } else if (content.isFileTransfer) {
            downloadable.value = true
            val extension = FileUtils.getExtensionFromFileName(fileName.value!!)
            val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            isImage.value = FileUtils.isMimeImage(mime)
            isVideo.value = FileUtils.isMimeVideo(mime)
            isAudio.value = FileUtils.isMimeAudio(mime)
            isPdf.value = FileUtils.isMimePdf(mime)
            isVoiceRecording.value = false
            isConferenceSchedule.value = false
        } else if (content.isIcalendar) {
            Log.i("[Content] Found content with icalendar body")
            isConferenceSchedule.value = true
            parseConferenceInvite(content)
        } else {
            Log.w("[Content] Found content that's neither a file or a file transfer")
        }

        isGenericFile.value = !isPdf.value!! && !isAudio.value!! && !isVideo.value!! && !isImage.value!! && !isVoiceRecording.value!! && !isConferenceSchedule.value!!
    }

    private fun parseConferenceInvite(content: Content) {
        val conferenceInfo = Factory.instance().createConferenceInfoFromIcalendarContent(content)
        val conferenceUri = conferenceInfo?.uri?.asStringUriOnly()
        if (conferenceInfo != null && conferenceUri != null) {
            conferenceAddress.value = conferenceUri!!
            Log.i("[Content] Created conference info from ICS with address ${conferenceAddress.value}")
            conferenceSubject.value = conferenceInfo.subject
            conferenceDescription.value = conferenceInfo.description

            val state = conferenceInfo.state
            isConferenceUpdated.value = state == ConferenceInfo.State.Updated
            isConferenceCancelled.value = state == ConferenceInfo.State.Cancelled

            conferenceDate.value = TimestampUtils.dateToString(conferenceInfo.dateTime)
            conferenceTime.value = TimestampUtils.timeToString(conferenceInfo.dateTime)

            val minutes = conferenceInfo.duration
            val hours = TimeUnit.MINUTES.toHours(minutes.toLong())
            val remainMinutes = minutes - TimeUnit.HOURS.toMinutes(hours).toInt()
            conferenceDuration.value = TimestampUtils.durationToString(hours.toInt(), remainMinutes)
            showDuration.value = minutes > 0

            // Check if organizer is part of participants list
            var participantsCount = conferenceInfo.participants.size
            val organizer = conferenceInfo.organizer
            var organizerFound = false
            if (organizer != null) {
                for (participant in conferenceInfo.participants) {
                    if (participant.weakEqual(organizer)) {
                        organizerFound = true
                        break
                    }
                }
            }
            if (!organizerFound) participantsCount += 1 // +1 for organizer
            conferenceParticipantCount.value = String.format(AppUtils.getString(R.string.conference_invite_participants_count), participantsCount)
        } else if (conferenceInfo == null) {
            if (content.filePath != null) {
                try {
                    val br = BufferedReader(FileReader(content.filePath))
                    var line: String?
                    val textBuilder = StringBuilder()
                    while (br.readLine().also { line = it } != null) {
                        textBuilder.append(line)
                        textBuilder.append('\n')
                    }
                    br.close()
                    Log.e("[Content] Failed to create conference info from ICS file [${content.filePath}]: $textBuilder")
                } catch (e: Exception) {
                    Log.e("[Content] Failed to read content of ICS file [${content.filePath}]: $e")
                }
            } else {
                Log.e("[Content] Failed to create conference info from ICS: ${content.utf8Text}")
            }
        } else if (conferenceInfo.uri == null) {
            Log.e("[Content] Failed to find the conference URI in conference info [$conferenceInfo]")
        }
    }

    fun callConferenceAddress() {
        val address = conferenceAddress.value
        if (address == null) {
            Log.e("[Content] Can't call null conference address!")
            return
        }
        listener?.onCallConference(address, conferenceSubject.value)
    }

    /** Voice recording specifics */

    fun playVoiceRecording() {
        Log.i("[Voice Recording] Playing voice record")
        if (isPlayerClosed()) {
            Log.w("[Voice Recording] Player closed, let's open it first")
            initVoiceRecordPlayer()
        }

        if (AppUtils.isMediaVolumeLow(coreContext.context)) {
            Toast.makeText(coreContext.context, R.string.chat_message_voice_recording_playback_low_volume, Toast.LENGTH_LONG).show()
        }

        if (voiceRecordAudioFocusRequest == null) {
            voiceRecordAudioFocusRequest = AppUtils.acquireAudioFocusForVoiceRecordingOrPlayback(
                coreContext.context
            )
        }
        voiceRecordingPlayer.start()
        isVoiceRecordPlaying.value = true
        tickerFlow().onEach {
            withContext(Dispatchers.Main) {
                voiceRecordPlayingPosition.value = voiceRecordingPlayer.currentPosition
            }
        }.launchIn(scope)
    }

    fun pauseVoiceRecording() {
        Log.i("[Voice Recording] Pausing voice record")
        if (!isPlayerClosed()) {
            voiceRecordingPlayer.pause()
        }

        val request = voiceRecordAudioFocusRequest
        if (request != null) {
            AppUtils.releaseAudioFocusForVoiceRecordingOrPlayback(coreContext.context, request)
            voiceRecordAudioFocusRequest = null
        }

        isVoiceRecordPlaying.value = false
    }

    private fun tickerFlow() = flow {
        while (isVoiceRecordPlaying.value == true) {
            emit(Unit)
            delay(100)
        }
    }

    private fun initVoiceRecordPlayer() {
        Log.i("[Voice Recording] Creating player for voice record")
        val playbackSoundCard = AudioRouteUtils.getAudioPlaybackDeviceIdForCallRecordingOrVoiceMessage()
        Log.i("[Voice Recording] Using device $playbackSoundCard to make the voice message playback")

        val localPlayer = coreContext.core.createLocalPlayer(playbackSoundCard, null, null)
        if (localPlayer != null) {
            voiceRecordingPlayer = localPlayer
        } else {
            Log.e("[Voice Recording] Couldn't create local player!")
            return
        }
        voiceRecordingPlayer.addListener(playerListener)

        val path = filePath.value
        voiceRecordingPlayer.open(path.orEmpty())
        voiceRecordDuration.value = voiceRecordingPlayer.duration
        formattedDuration.value = SimpleDateFormat("mm:ss", Locale.getDefault()).format(voiceRecordingPlayer.duration) // is already in milliseconds
        Log.i("[Voice Recording] Duration is ${voiceRecordDuration.value} (${voiceRecordingPlayer.duration})")
    }

    private fun stopVoiceRecording() {
        if (!isPlayerClosed()) {
            Log.i("[Voice Recording] Stopping voice record")
            pauseVoiceRecording()
            voiceRecordingPlayer.seek(0)
            voiceRecordPlayingPosition.value = 0
            voiceRecordingPlayer.close()
        }
    }

    private fun isPlayerClosed(): Boolean {
        return !this::voiceRecordingPlayer.isInitialized || voiceRecordingPlayer.state == Player.State.Closed
    }
}

interface OnContentClickedListener {
    fun onContentClicked(content: Content)

    fun onSipAddressClicked(sipUri: String)

    fun onWebUrlClicked(url: String)

    fun onCallConference(address: String, subject: String?)

    fun onError(messageId: Int)
}
