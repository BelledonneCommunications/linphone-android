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
package org.linphone.ui.fileviewer.viewmodel

import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.annotation.UiThread
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.ticker
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.linphone.core.tools.Log
import org.linphone.ui.GenericViewModel
import org.linphone.utils.Event
import org.linphone.utils.FileUtils
import org.linphone.utils.TimestampUtils
import org.linphone.R

class MediaViewModel
    @UiThread
    constructor() : GenericViewModel() {
    companion object {
        private const val TAG = "[Media ViewModel]"
    }

    val path = MutableLiveData<String>()

    val fileName = MutableLiveData<String>()

    val fullScreenMode = MutableLiveData<Boolean>()

    val isImage = MutableLiveData<Boolean>()

    val isVideo = MutableLiveData<Boolean>()

    val isAudio = MutableLiveData<Boolean>()

    val isMediaPlaying = MutableLiveData<Boolean>()

    val duration = MutableLiveData<Int>()

    val formattedDuration = MutableLiveData<String>()

    val position = MutableLiveData<Int>()

    val videoSizeChangedEvent: MutableLiveData<Event<Pair<Int, Int>>> by lazy {
        MutableLiveData<Event<Pair<Int, Int>>>()
    }

    val changeFullScreenModeEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    lateinit var mediaPlayer: MediaPlayer

    private lateinit var filePath: String

    private val tickerChannel = ticker(1000, 1000)

    private var updatePositionJob: Job? = null

    override fun onCleared() {
        if (::mediaPlayer.isInitialized) {
            mediaPlayer.release()
        }
        stopUpdatePlaybackPosition()

        super.onCleared()
    }

    @UiThread
    fun loadFile(file: String) {
        filePath = file
        val name = FileUtils.getNameFromFilePath(file)
        fileName.value = name

        val extension = FileUtils.getExtensionFromFileName(file)
        val mime = FileUtils.getMimeTypeFromExtension(extension)
        when (FileUtils.getMimeType(mime)) {
            FileUtils.MimeType.Image -> {
                Log.d("$TAG File [$file] seems to be an image")
                isImage.value = true
                path.value = file
            }
            FileUtils.MimeType.Video -> {
                Log.d("$TAG File [$file] seems to be a video")
                initMediaPlayer()
                isVideo.value = true
            }
            FileUtils.MimeType.Audio -> {
                Log.d("$TAG File [$file] seems to be an audio file")
                initMediaPlayer()
                isAudio.value = true
            }
            else -> {
                Log.e("$TAG Unexpected MIME type [$mime] for file at [$file]")
            }
        }
    }

    @UiThread
    fun toggleFullScreen(): Boolean {
        val newValue = fullScreenMode.value != true
        fullScreenMode.value = newValue
        return newValue
    }

    @UiThread
    fun togglePlayPause() {
        if (::mediaPlayer.isInitialized) {
            if (mediaPlayer.isPlaying) {
                mediaPlayer.pause()

                isMediaPlaying.value = false
                stopUpdatePlaybackPosition()
            } else {
                mediaPlayer.start()

                isMediaPlaying.value = true
                startUpdatePlaybackPosition()
            }
        }
    }

    @UiThread
    fun play() {
        if (::mediaPlayer.isInitialized) {
            mediaPlayer.start()
            startUpdatePlaybackPosition()
            isMediaPlaying.value = true
        }
    }

    @UiThread
    fun pause() {
        if (::mediaPlayer.isInitialized) {
            isMediaPlaying.value = false
            stopUpdatePlaybackPosition()
            mediaPlayer.pause()
        }
    }

    @UiThread
    private fun initMediaPlayer() {
        isMediaPlaying.value = false

        try {
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder().setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(
                            AudioAttributes.USAGE_MEDIA
                        ).build()
                )
                setDataSource(filePath)
                setOnCompletionListener {
                    Log.i("$TAG Media player reached the end of file")
                    isMediaPlaying.postValue(false)
                    position.postValue(0)
                    stopUpdatePlaybackPosition()

                    // Leave full screen when playback is done
                    fullScreenMode.postValue(false)
                    changeFullScreenModeEvent.postValue(Event(false))
                }
                setOnVideoSizeChangedListener { mediaPlayer, width, height ->
                    videoSizeChangedEvent.postValue(Event(Pair(width, height)))
                }
                try {
                    prepare()
                } catch (e: Exception) {
                    fullScreenMode.postValue(false)
                    changeFullScreenModeEvent.postValue(Event(false))
                    Log.e("$TAG Failed to prepare video file: $e")
                }
            }
        } catch (e: Exception) {
            Log.e("$TAG Failed to initialize media player for file [$filePath]: $e")
            showRedToast(R.string.media_player_generic_error_toast, R.drawable.warning_circle)
            return
        }

        val durationInMillis = mediaPlayer.duration
        position.value = 0
        duration.value = durationInMillis
        formattedDuration.value = TimestampUtils.durationToString(durationInMillis / 1000)
        Log.i("$TAG Media player for file [$filePath] created, let's start it")
    }

    @UiThread
    fun startUpdatePlaybackPosition() {
        updatePositionJob = viewModelScope.launch {
            withContext(Dispatchers.IO) {
                for (tick in tickerChannel) {
                    if (::mediaPlayer.isInitialized && mediaPlayer.isPlaying) {
                        position.postValue(mediaPlayer.currentPosition)
                    }
                }
            }
        }
    }

    @UiThread
    fun stopUpdatePlaybackPosition() {
        updatePositionJob?.cancel()
        updatePositionJob = null
    }
}
