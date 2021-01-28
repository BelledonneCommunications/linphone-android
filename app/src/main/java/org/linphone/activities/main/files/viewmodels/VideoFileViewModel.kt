/*
 * Copyright (c) 2010-2021 Belledonne Communications SARL.
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
package org.linphone.activities.main.files.viewmodels

import android.widget.MediaController
import android.widget.VideoView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import org.linphone.core.tools.Log

class VideoFileViewModelFactory(private val filePath: String) :
    ViewModelProvider.NewInstanceFactory() {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
        return VideoFileViewModel(filePath) as T
    }
}

class VideoFileViewModel(val filePath: String) : ViewModel() {

    fun initMediaController(mediaController: MediaController, videoView: VideoView) {
        videoView.setOnPreparedListener { mediaPlayer ->
            mediaPlayer.setOnVideoSizeChangedListener { _, _, _ ->
                videoView.setMediaController(mediaController)
                // The following will make the video controls above the video
                // mediaController.setAnchorView(videoView)
            }
        }

        videoView.setOnCompletionListener { mediaPlayer ->
            mediaPlayer.release()
        }

        videoView.setOnErrorListener { _, what, extra ->
            Log.e("[Video Viewer] Error: $what ($extra)")
            false
        }

        videoView.setVideoPath(filePath)
        videoView.start()
    }
}
