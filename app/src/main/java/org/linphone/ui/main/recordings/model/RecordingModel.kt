/*
 * Copyright (c) 2010-2024 Belledonne Communications SARL.
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
package org.linphone.ui.main.recordings.model

import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.lifecycle.MutableLiveData
import androidx.media.AudioFocusRequestCompat
import java.text.SimpleDateFormat
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.ticker
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.core.Factory
import org.linphone.core.Player
import org.linphone.core.PlayerListener
import org.linphone.core.tools.Log
import org.linphone.utils.AudioUtils
import org.linphone.utils.FileUtils
import org.linphone.utils.LinphoneUtils
import org.linphone.utils.TimestampUtils

class RecordingModel @WorkerThread constructor(val filePath: String, val fileName: String) {
    companion object {
        private const val TAG = "[Recording Model]"
    }

    val displayName: String

    val month: String

    val dateTime: String

    val formattedDuration: String

    val duration: Int

    val isPlaying = MutableLiveData<Boolean>()

    val position = MutableLiveData<Int>()

    private var audioFocusRequest: AudioFocusRequestCompat? = null

    private lateinit var player: Player
    private val playerListener = PlayerListener {
        Log.i("$TAG End of file reached")
        pause()
        player.seek(0)
        position.postValue(0)
        player.close()
    }

    private val tickerChannel = ticker(1000, 1000)
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    init {
        isPlaying.postValue(false)

        val withoutHeader = fileName.substring(LinphoneUtils.RECORDING_FILE_NAME_HEADER.length)
        val indexOfSeparator = withoutHeader.indexOf(
            LinphoneUtils.RECORDING_FILE_NAME_URI_TIMESTAMP_SEPARATOR
        )
        val sipUri = withoutHeader.substring(0, indexOfSeparator)
        val timestamp = withoutHeader.substring(
            indexOfSeparator + LinphoneUtils.RECORDING_FILE_NAME_URI_TIMESTAMP_SEPARATOR.length,
            withoutHeader.length - LinphoneUtils.RECORDING_FILE_EXTENSION.length
        )
        Log.i("$TAG Extract SIP URI [$sipUri] and timestamp [$timestamp] from file [$fileName]")

        val parsedTimestamp = timestamp.toLong()
        month = TimestampUtils.month(parsedTimestamp, timestampInSecs = false)
        val date = TimestampUtils.toString(
            parsedTimestamp,
            timestampInSecs = false,
            onlyDate = true,
            shortDate = false
        )
        val time = TimestampUtils.timeToString(parsedTimestamp, timestampInSecs = false)
        dateTime = "$date - $time"

        val sipAddress = Factory.instance().createAddress(sipUri)
        displayName = if (sipAddress != null) {
            val contact = coreContext.contactsManager.findContactByAddress(sipAddress)
            contact?.name ?: LinphoneUtils.getDisplayName(sipAddress)
        } else {
            sipUri
        }

        val playbackSoundCard = AudioUtils.getAudioPlaybackDeviceIdForCallRecordingOrVoiceMessage()
        val audioPlayer = coreContext.core.createLocalPlayer(playbackSoundCard, null, null)
        if (audioPlayer != null) {
            player = audioPlayer
            player.open(filePath)
            player.addListener(playerListener)
            duration = player.duration
            formattedDuration = SimpleDateFormat("mm:ss", Locale.getDefault()).format(duration)
        } else {
            duration = 0
            formattedDuration = "??:??"
        }
        position.postValue(0)
    }

    @WorkerThread
    fun destroy() {
        scope.cancel()
        tickerChannel.cancel()

        if (::player.isInitialized) {
            if (player.state != Player.State.Closed) {
                player.close()
            }

            if (audioFocusRequest != null) {
                AudioUtils.releaseAudioFocusForVoiceRecordingOrPlayback(
                    coreContext.context,
                    audioFocusRequest!!
                )
            }

            player.removeListener(playerListener)
        }
    }

    @UiThread
    fun togglePlayPause() {
        coreContext.postOnCoreThread {
            if (isPlaying.value == true) {
                pause()
            } else {
                play()
            }
        }
    }

    @UiThread
    suspend fun delete() {
        Log.i("$TAG Deleting call recording [$filePath]")
        FileUtils.deleteFile(filePath)
    }

    @WorkerThread
    private fun play() {
        if (!::player.isInitialized) return

        Log.i("$TAG Starting player, acquiring audio focus")
        audioFocusRequest = AudioUtils.acquireAudioFocusForVoiceRecordingOrPlayback(
            coreContext.context
        )

        if (player.state == Player.State.Closed) {
            player.open(filePath)
            player.seek(0)
        }

        player.start()
        isPlaying.postValue(true)

        scope.launch {
            withContext(Dispatchers.IO) {
                for (tick in tickerChannel) {
                    withContext(Dispatchers.Main) {
                        if (player.state == Player.State.Playing) {
                            updatePosition()
                        }
                    }
                }
            }
        }
    }

    @UiThread
    private fun updatePosition() {
        val progress = if (player.state == Player.State.Closed) 0 else player.currentPosition
        position.value = progress
    }

    @WorkerThread
    private fun pause() {
        if (!::player.isInitialized) return

        Log.i("$TAG Stopping player, releasing audio focus")
        if (audioFocusRequest != null) {
            AudioUtils.releaseAudioFocusForVoiceRecordingOrPlayback(
                coreContext.context,
                audioFocusRequest!!
            )
        }

        coreContext.postOnCoreThread {
            player.pause()
        }
        isPlaying.postValue(false)
    }
}
