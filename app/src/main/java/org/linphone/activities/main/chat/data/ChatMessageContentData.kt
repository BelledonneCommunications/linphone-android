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

import android.graphics.Bitmap
import android.text.Spannable
import android.text.SpannableString
import android.text.Spanned
import android.text.style.UnderlineSpan
import android.widget.Toast
import androidx.lifecycle.MutableLiveData
import androidx.media.AudioFocusRequestCompat
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.core.*
import org.linphone.core.tools.Log
import org.linphone.utils.AppUtils
import org.linphone.utils.FileUtils
import org.linphone.utils.ImageUtils

class ChatMessageContentData(
    private val chatMessage: ChatMessage,
    private val contentIndex: Int,
) {
    var listener: OnContentClickedListener? = null

    val isOutgoing = chatMessage.isOutgoing

    val isImage = MutableLiveData<Boolean>()
    val isVideo = MutableLiveData<Boolean>()
    val isAudio = MutableLiveData<Boolean>()
    val videoPreview = MutableLiveData<Bitmap>()
    val isPdf = MutableLiveData<Boolean>()
    val isGenericFile = MutableLiveData<Boolean>()
    val isVoiceRecording = MutableLiveData<Boolean>()

    val fileName = MutableLiveData<String>()
    val filePath = MutableLiveData<String>()

    val downloadable = MutableLiveData<Boolean>()
    val downloadEnabled = MutableLiveData<Boolean>()
    val downloadProgressInt = MutableLiveData<Int>()
    val downloadProgressString = MutableLiveData<String>()
    val downloadLabel = MutableLiveData<Spannable>()

    val voiceRecordDuration = MutableLiveData<Int>()
    val formattedDuration = MutableLiveData<String>()
    val voiceRecordPlayingPosition = MutableLiveData<Int>()
    val isVoiceRecordPlaying = MutableLiveData<Boolean>()

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
                val percent = offset * 100 / total
                Log.d("[Content] Download progress is: $offset / $total ($percent%)")

                downloadProgressInt.value = percent
                downloadProgressString.value = "$percent%"
            }
        }

        override fun onMsgStateChanged(message: ChatMessage, state: ChatMessage.State) {
            downloadEnabled.value = state != ChatMessage.State.FileTransferInProgress

            if (state == ChatMessage.State.FileTransferDone || state == ChatMessage.State.FileTransferError) {
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
        val content = getContent()
        val filePath = content.filePath
        if (content.isFileTransfer && (filePath == null || filePath.isEmpty())) {
            val contentName = content.name
            if (contentName != null) {
                val file = FileUtils.getFileStoragePath(contentName)
                content.filePath = file.path
                downloadEnabled.value = false

                Log.i("[Content] Started downloading $contentName into ${content.filePath}")
                chatMessage.downloadContent(content)
            }
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

        if (content.isFile || (content.isFileTransfer && chatMessage.isOutgoing)) {
            val path = if (isFileEncrypted) {
                Log.i("[Content] Content is encrypted, requesting plain file path")
                content.plainFilePath
            } else {
                content.filePath ?: ""
            }
            downloadable.value = content.filePath.orEmpty().isEmpty()

            if (path.isNotEmpty()) {
                Log.i("[Content] Found displayable content: $path")
                val isVoiceRecord = content.isVoiceRecording
                filePath.value = path
                isImage.value = FileUtils.isExtensionImage(path)
                isVideo.value = FileUtils.isExtensionVideo(path) && !isVoiceRecord
                isAudio.value = FileUtils.isExtensionAudio(path) && !isVoiceRecord
                isPdf.value = FileUtils.isExtensionPdf(path)
                isVoiceRecording.value = isVoiceRecord

                if (isVoiceRecord) {
                    val duration = content.fileDuration // duration is in ms
                    voiceRecordDuration.value = duration
                    formattedDuration.value = SimpleDateFormat("mm:ss", Locale.getDefault()).format(duration)
                    Log.i("[Voice Recording] Duration is ${voiceRecordDuration.value} ($duration)")
                }

                if (isVideo.value == true) {
                    scope.launch {
                        videoPreview.postValue(ImageUtils.getVideoPreview(path))
                    }
                }
            } else {
                Log.w("[Content] Found ${if (content.isFile) "file" else "file transfer"} content with empty path...")
                isImage.value = false
                isVideo.value = false
                isAudio.value = false
                isPdf.value = false
                isVoiceRecording.value = false
            }
        } else {
            downloadable.value = true
            isImage.value = FileUtils.isExtensionImage(fileName.value!!)
            isVideo.value = FileUtils.isExtensionVideo(fileName.value!!)
            isAudio.value = FileUtils.isExtensionAudio(fileName.value!!)
            isPdf.value = FileUtils.isExtensionPdf(fileName.value!!)
            isVoiceRecording.value = false
        }

        isGenericFile.value = !isPdf.value!! && !isAudio.value!! && !isVideo.value!! && !isImage.value!! && !isVoiceRecording.value!!
        downloadEnabled.value = !chatMessage.isFileTransferInProgress
        downloadProgressInt.value = 0
        downloadProgressString.value = "0%"
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
            voiceRecordPlayingPosition.postValue(voiceRecordingPlayer.currentPosition)
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
        // Use speaker sound card to play recordings, otherwise use earpiece
        // If none are available, default one will be used
        var speakerCard: String? = null
        var earpieceCard: String? = null
        for (device in coreContext.core.audioDevices) {
            if (device.hasCapability(AudioDevice.Capabilities.CapabilityPlay)) {
                if (device.type == AudioDevice.Type.Speaker) {
                    speakerCard = device.id
                } else if (device.type == AudioDevice.Type.Earpiece) {
                    earpieceCard = device.id
                }
            }
        }
        Log.i("[Voice Recording] Found speaker sound card [$speakerCard] and earpiece sound card [$earpieceCard]")

        val localPlayer = coreContext.core.createLocalPlayer(speakerCard ?: earpieceCard, null, null)
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
}
