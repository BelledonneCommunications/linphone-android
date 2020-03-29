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
package org.linphone.activities.main.recordings.viewmodels

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Pattern
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.core.Player
import org.linphone.core.PlayerListener
import org.linphone.core.tools.Log
import org.linphone.utils.Event

class RecordingViewModel(val path: String) : ViewModel(), Comparable<RecordingViewModel> {
    companion object {
        val RECORD_PATTERN: Pattern =
            Pattern.compile(".*/(.*)_(\\d{2}-\\d{2}-\\d{4}-\\d{2}-\\d{2}-\\d{2})\\..*")
    }

    lateinit var name: String
    lateinit var date: Date

    val duration: Int
        get() {
            if (isClosed()) player.open(path)
            return player.duration
        }

    val formattedDuration: String
        get() = SimpleDateFormat("mm:ss", Locale.getDefault()).format(duration) // is already in milliseconds

    val formattedDate: String
        get() = SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)

    val playStartedEvent = MutableLiveData<Event<Boolean>>()

    val isPlaying = MutableLiveData<Boolean>()

    private var player: Player
    private val listener = PlayerListener {
        Log.i("[Recording] End of file reached")
        pause()
    }

    init {
        val m = RECORD_PATTERN.matcher(path)
        if (m.matches()) {
            name = m.group(1)
            date = SimpleDateFormat("dd-MM-yyyy-HH-mm-ss", Locale.getDefault()).parse(m.group(2))
        }
        isPlaying.value = false

        player = coreContext.core.createLocalPlayer(null, null, null)
        player.addListener(listener)
    }

    override fun onCleared() {
        if (!isClosed()) player.close()
        player.removeListener(listener)

        super.onCleared()
    }

    override fun compareTo(other: RecordingViewModel): Int {
        return -date.compareTo(other.date)
    }

    fun play() {
        if (isClosed()) player.open(path)
        seek(0)
        player.start()
        playStartedEvent.value = Event(true)
        isPlaying.value = true
    }

    fun pause() {
        player.pause()
        isPlaying.value = false
        playStartedEvent.value = Event(false)
    }

    private fun seek(position: Int) {
        if (!isClosed()) player.seek(position)
    }

    private fun isClosed(): Boolean {
        return player.state == Player.State.Closed
    }
}
