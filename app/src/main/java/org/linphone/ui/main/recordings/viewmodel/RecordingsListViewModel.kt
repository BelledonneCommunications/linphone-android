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
import org.linphone.utils.Event
import org.linphone.utils.FileUtils

class RecordingsListViewModel @UiThread constructor() : GenericViewModel() {
    companion object {
        private const val TAG = "[Recordings List ViewModel]"
    }

    val recordings = MutableLiveData<ArrayList<RecordingModel>>()

    val searchBarVisible = MutableLiveData<Boolean>()

    val searchFilter = MutableLiveData<String>()

    val fetchInProgress = MutableLiveData<Boolean>()

    val focusSearchBarEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    private var audioFocusRequest: AudioFocusRequestCompat? = null

    private var currentlyPlayedRecording: RecordingModel? = null

    private var player: Player? = null
    private val playerListener = PlayerListener {
        Log.i("$TAG End of file reached")
        stop(currentlyPlayedRecording)
    }

    private val tickerChannel = ticker(1000, 1000)
    private var updatePositionJob: Job? = null

    init {
        searchBarVisible.value = false
        fetchInProgress.value = true

        coreContext.postOnCoreThread {
            computeList("")
        }
    }

    override fun onCleared() {
        if (currentlyPlayedRecording != null) {
            stop(currentlyPlayedRecording)
            player?.removeListener(playerListener)
            player = null
        }
        recordings.value.orEmpty().forEach(RecordingModel::destroy)

        super.onCleared()
    }

    @UiThread
    fun openSearchBar() {
        searchBarVisible.value = true
        focusSearchBarEvent.value = Event(true)
    }

    @UiThread
    fun closeSearchBar() {
        clearFilter()
        searchBarVisible.value = false
        focusSearchBarEvent.value = Event(false)
    }

    @UiThread
    fun clearFilter() {
        searchFilter.value = ""
    }

    @UiThread
    fun applyFilter(filter: String) {
        coreContext.postOnCoreThread {
            computeList(filter)
        }
    }

    @WorkerThread
    fun onRecordingStartedPlaying(model: RecordingModel) {
        val lowMediaVolume = AudioUtils.isMediaVolumeLow(coreContext.context)
        if (lowMediaVolume) {
            Log.w("$TAG Media volume is low, notifying user as they may not hear voice message")
            showRedToastEvent.postValue(
                Event(Pair(R.string.toast_low_media_volume, R.drawable.speaker_slash))
            )
        }

        if (player == null) {
            initAudioPlayer()
        }
        if (currentlyPlayedRecording != null && model != currentlyPlayedRecording) {
            Log.i("$TAG Recording model has changed, stopping player before starting it")
            stop(model)
        }

        currentlyPlayedRecording = model
        play(model)
    }

    @WorkerThread
    fun onRecordingPaused(model: RecordingModel) {
        pause(model)
    }

    @WorkerThread
    private fun initAudioPlayer() {
        Log.i("$TAG Creating player")
        val playbackSoundCard = AudioUtils.getAudioPlaybackDeviceIdForCallRecordingOrVoiceMessage()
        player = coreContext.core.createLocalPlayer(playbackSoundCard, null, null)
        player?.addListener(playerListener)
    }

    @WorkerThread
    private fun play(model: RecordingModel?) {
        model ?: return

        Log.i("$TAG Starting player")
        if (player?.state == Player.State.Closed) {
            player?.open(model.filePath)
            player?.seek(0)
        }

        Log.i("$TAG Acquiring audio focus")
        audioFocusRequest = AudioUtils.acquireAudioFocusForVoiceRecordingOrPlayback(
            coreContext.context
        )

        player?.start()
        model.isPlaying.postValue(true)

        updatePositionJob = viewModelScope.launch {
            withContext(Dispatchers.IO) {
                for (tick in tickerChannel) {
                    coreContext.postOnCoreThread {
                        if (player?.state == Player.State.Playing) {
                            model.position.postValue(player?.currentPosition)
                        }
                    }
                }
            }
        }
    }

    @WorkerThread
    private fun pause(model: RecordingModel?) {
        model ?: return

        Log.i("$TAG Pausing player, releasing audio focus")
        if (audioFocusRequest != null) {
            AudioUtils.releaseAudioFocusForVoiceRecordingOrPlayback(
                coreContext.context,
                audioFocusRequest!!
            )
        }

        player?.pause()
        model.isPlaying.postValue(false)
        updatePositionJob?.cancel()
        updatePositionJob = null
    }

    @WorkerThread
    private fun stop(model: RecordingModel?) {
        model ?: return

        Log.i("$TAG Stopping player")
        pause(model)
        model.position.postValue(0)
        player?.seek(0)
        player?.close()

        currentlyPlayedRecording = null
    }

    @WorkerThread
    private fun computeList(filter: String) {
        recordings.value.orEmpty().forEach(RecordingModel::destroy)
        val list = arrayListOf<RecordingModel>()

        // TODO FIXME: also load recordings from previous Linphone versions
        for (file in FileUtils.getFileStorageDir(isRecording = true).listFiles().orEmpty()) {
            val path = file.path
            val name = file.name
            Log.i("$TAG Found file $path")
            list.add(
                RecordingModel(
                    path,
                    name,
                    { model ->
                        onRecordingStartedPlaying(model)
                    },
                    { model ->
                        onRecordingPaused(model)
                    }
                )
            )
        }

        list.sortBy {
            it.filePath // TODO FIXME
        }
        recordings.postValue(list)
    }
}
