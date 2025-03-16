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
package org.linphone.ui.main.recordings.viewmodel

import android.view.TextureView
import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.media.AudioFocusRequestCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.ticker
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.core.Player
import org.linphone.core.PlayerListener
import org.linphone.core.tools.Log
import org.linphone.ui.GenericViewModel
import org.linphone.ui.main.recordings.model.RecordingModel
import org.linphone.utils.AudioUtils

class RecordingMediaPlayerViewModel
    @UiThread
    constructor() : GenericViewModel() {
    companion object {
        private const val TAG = "[Recording Media Player ViewModel]"
    }

    lateinit var recordingModel: RecordingModel

    private lateinit var player: Player

    val isVideo = MutableLiveData<Boolean>()

    val isPlaying = MutableLiveData<Boolean>()

    val position = MutableLiveData<Int>()

    private var audioFocusRequest: AudioFocusRequestCompat? = null

    private val playerListener = PlayerListener {
        Log.i("$TAG End of file reached")
        stop()
    }

    private val tickerChannel = ticker(1000, 1000)
    private var updatePositionJob: Job? = null

    init {
        isVideo.value = false
        isPlaying.value = false
        position.value = 0
    }

    override fun onCleared() {
        if (::recordingModel.isInitialized) {
            stop()
            if (::player.isInitialized) {
                player.removeListener(playerListener)
            }
        }

        super.onCleared()
    }

    @UiThread
    fun loadRecording(model: RecordingModel) {
        recordingModel = model

        coreContext.postOnCoreThread { core ->
            initPlayer()
        }
    }

    @UiThread
    fun setVideoRenderingSurface(textureView: TextureView) {
        coreContext.postOnCoreThread {
            Log.i("$TAG Setting window ID in player")
            player.setWindowId(textureView.surfaceTexture)
        }
    }

    @WorkerThread
    private fun initPlayer() {
        if (!::recordingModel.isInitialized) return

        Log.i("$TAG Creating player")
        val playbackSoundCard = AudioUtils.getAudioPlaybackDeviceIdForCallRecordingOrVoiceMessage()
        val recordingPlayer = coreContext.core.createLocalPlayer(
            playbackSoundCard,
            null,
            null
        )
        if (recordingPlayer != null) {
            player = recordingPlayer
        } else {
            Log.e("$TAG Failed to create a Player!")
            return
        }

        player.addListener(playerListener)

        val lowMediaVolume = AudioUtils.isMediaVolumeLow(coreContext.context)
        if (lowMediaVolume) {
            Log.w("$TAG Media volume is low, notifying user as they may not hear voice message")
            showRedToast(R.string.media_playback_low_volume_warning_toast, R.drawable.speaker_slash)
        }

        if (player.state == Player.State.Closed) {
            player.open(recordingModel.filePath)
            player.seek(0)
        }
    }

    @UiThread
    fun togglePlayPause() {
        coreContext.postOnCoreThread {
            if (isPlaying.value == true) {
                pausePlayback()
            } else {
                startPlayback()
            }
        }
    }

    @UiThread
    fun play() {
        coreContext.postOnCoreThread {
            startPlayback()
        }
    }

    @UiThread
    fun pause() {
        coreContext.postOnCoreThread {
            pausePlayback()
        }
    }

    @WorkerThread
    private fun startPlayback() {
        if (!::player.isInitialized) return
        if (!::recordingModel.isInitialized) return

        Log.i("$TAG Starting player")
        if (player.state == Player.State.Closed) {
            player.open(recordingModel.filePath)
            player.seek(0)
        }

        Log.i("$TAG Acquiring audio focus")
        audioFocusRequest = AudioUtils.acquireAudioFocusForVoiceRecordingOrPlayback(
            coreContext.context
        )

        player.start()
        isPlaying.postValue(true)

        // We have to wait for the player to be started to have that information!
        val isVideoAvailable = player.isVideoAvailable
        Log.i(
            "$TAG Recording says video is ${if (isVideoAvailable) "available" else "not available"}"
        )
        isVideo.postValue(isVideoAvailable)

        updatePositionJob = viewModelScope.launch {
            withContext(Dispatchers.IO) {
                for (tick in tickerChannel) {
                    coreContext.postOnCoreThread {
                        if (player.state == Player.State.Playing) {
                            position.postValue(player.currentPosition)
                        }
                    }
                }
            }
        }
    }

    @WorkerThread
    private fun pausePlayback() {
        if (!::player.isInitialized) return

        Log.i("$TAG Pausing player, releasing audio focus")
        if (audioFocusRequest != null) {
            AudioUtils.releaseAudioFocusForVoiceRecordingOrPlayback(
                coreContext.context,
                audioFocusRequest!!
            )
        }

        player.pause()
        isPlaying.postValue(false)
        updatePositionJob?.cancel()
        updatePositionJob = null
    }

    @WorkerThread
    private fun stop() {
        if (!::player.isInitialized) return

        Log.i("$TAG Stopping player")
        pausePlayback()
        position.postValue(0)
        player.seek(0)
        player.close()
    }
}
