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
package org.linphone.activities.main.recordings.data

import android.graphics.SurfaceTexture
import android.view.TextureView
import androidx.lifecycle.MutableLiveData
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Pattern
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ticker
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.core.AudioDevice
import org.linphone.core.Player
import org.linphone.core.PlayerListener
import org.linphone.core.tools.Log
import org.linphone.utils.LinphoneUtils

class RecordingData(val path: String, private val recordingListener: RecordingListener) : Comparable<RecordingData> {
    companion object {
        val RECORD_PATTERN: Pattern =
            Pattern.compile(".*/(.*)_(\\d{2}-\\d{2}-\\d{4}-\\d{2}-\\d{2}-\\d{2})\\..*")
    }

    lateinit var name: String
    lateinit var date: Date

    val duration = MutableLiveData<Int>()
    val formattedDuration = MutableLiveData<String>()
    val formattedDate = MutableLiveData<String>()
    val position = MutableLiveData<Int>()
    val formattedPosition = MutableLiveData<String>()
    val isPlaying = MutableLiveData<Boolean>()

    private val tickerChannel = ticker(1000, 1000)

    private lateinit var player: Player
    private val listener = PlayerListener {
        Log.i("[Recording] End of file reached")
        stop()
        recordingListener.onPlayingEnded()
    }

    private val textureViewListener = object : TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureSizeChanged(
            surface: SurfaceTexture,
            width: Int,
            height: Int
        ) { }

        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) { }

        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
            player.setWindowId(null)
            return true
        }

        override fun onSurfaceTextureAvailable(
            surface: SurfaceTexture,
            width: Int,
            height: Int
        ) {
            Log.i("[Recording VM] Surface texture should be available now")
            player.setWindowId(surface)
        }
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    init {
        val m = RECORD_PATTERN.matcher(path)
        if (m.matches() && m.groupCount() >= 2) {
            name = m.group(1)
            date = LinphoneUtils.getRecordingDateFromFileName(m.group(2))
        }
        isPlaying.value = false

        position.value = 0
        formattedPosition.value = SimpleDateFormat("mm:ss", Locale.getDefault()).format(0)

        initPlayer()
    }

    override fun compareTo(other: RecordingData): Int {
        return -date.compareTo(other.date)
    }

    fun destroy() {
        scope.cancel()
        tickerChannel.cancel()

        player.setWindowId(null)
        if (!isClosed()) player.close()

        player.removeListener(listener)
    }

    fun play() {
        if (isClosed()) {
            player.open(path)
            player.seek(0)
        }

        player.start()
        isPlaying.value = true
        recordingListener.onPlayingStarted(isVideoAvailable())

        scope.launch {
            withContext(Dispatchers.IO) {
                for (tick in tickerChannel) {
                    if (player.state == Player.State.Playing) {
                        updatePosition()
                    }
                }
            }
        }
    }

    fun isVideoAvailable(): Boolean {
        return player.isVideoAvailable
    }

    fun pause() {
        player.pause()
        isPlaying.value = false
    }

    fun onProgressChanged(progress: Any) {
        if (progress is Int) {
            if (player.state == Player.State.Playing) {
                pause()
            }
            player.seek(progress)
            updatePosition()
        }
    }

    fun setTextureView(textureView: TextureView) {
        Log.i("[Recording VM] Is TextureView available? ${textureView.isAvailable}")
        if (textureView.isAvailable) {
            player.setWindowId(textureView.surfaceTexture)
        } else {
            textureView.surfaceTextureListener = textureViewListener
        }
    }

    fun export() {
        recordingListener.onExportClicked(path)
    }

    private fun initPlayer() {
        // In case no headphones/headset is connected, use speaker sound card to play recordings, otherwise use earpiece
        // If none are available, default one will be used
        var headphonesCard: String? = null
        var speakerCard: String? = null
        var earpieceCard: String? = null
        for (device in coreContext.core.audioDevices) {
            if (device.hasCapability(AudioDevice.Capabilities.CapabilityPlay)) {
                when (device.type) {
                    AudioDevice.Type.Speaker -> {
                        speakerCard = device.id
                    }
                    AudioDevice.Type.Earpiece -> {
                        earpieceCard = device.id
                    }
                    AudioDevice.Type.Headphones, AudioDevice.Type.Headset -> {
                        headphonesCard = device.id
                    }
                    else -> {}
                }
            }
        }
        Log.i("[Recording VM] Found headset/headphones sound card [$headphonesCard], speaker sound card [$speakerCard] and earpiece sound card [$earpieceCard]")

        val localPlayer = coreContext.core.createLocalPlayer(headphonesCard ?: speakerCard ?: earpieceCard, null, null)
        if (localPlayer != null) player = localPlayer
        else Log.e("[Recording VM] Couldn't create local player!")
        player.addListener(listener)

        player.open(path)
        duration.value = player.duration
        formattedDuration.value = SimpleDateFormat("mm:ss", Locale.getDefault()).format(player.duration) // is already in milliseconds
        formattedDate.value = DateFormat.getTimeInstance(DateFormat.SHORT).format(date)
    }

    private fun updatePosition() {
        val progress = if (isClosed()) 0 else player.currentPosition
        position.postValue(progress)
        formattedPosition.postValue(SimpleDateFormat("mm:ss", Locale.getDefault()).format(progress)) // is already in milliseconds
    }

    private fun stop() {
        pause()
        player.seek(0)
        updatePosition()
        player.close()
    }

    private fun isClosed(): Boolean {
        return player.state == Player.State.Closed
    }

    interface RecordingListener {
        fun onPlayingStarted(videoAvailable: Boolean)
        fun onPlayingEnded()
        fun onExportClicked(path: String)
    }
}
