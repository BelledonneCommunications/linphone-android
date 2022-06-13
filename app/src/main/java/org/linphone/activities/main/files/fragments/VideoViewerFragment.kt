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
package org.linphone.activities.main.files.fragments

import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.MediaController
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import org.linphone.R
import org.linphone.activities.main.MainActivity
import org.linphone.activities.main.files.viewmodels.VideoFileViewModel
import org.linphone.activities.main.files.viewmodels.VideoFileViewModelFactory
import org.linphone.compatibility.Compatibility
import org.linphone.core.tools.Log
import org.linphone.databinding.FileVideoViewerFragmentBinding

class VideoViewerFragment : GenericViewerFragment<FileVideoViewerFragmentBinding>() {
    private lateinit var viewModel: VideoFileViewModel

    private lateinit var mediaController: MediaController

    override fun getLayoutId(): Int = R.layout.file_video_viewer_fragment

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.lifecycleOwner = viewLifecycleOwner

        val content = sharedViewModel.contentToOpen.value
        if (content == null) {
            Log.e("[Video Viewer] Content is null, aborting!")
            findNavController().navigateUp()
            return
        }

        viewModel = ViewModelProvider(
            this,
            VideoFileViewModelFactory(content)
        )[VideoFileViewModel::class.java]
        binding.viewModel = viewModel

        mediaController = object : MediaController(requireContext()) {
            // This is to prevent the first back key press to only hide to media controls
            override fun dispatchKeyEvent(event: KeyEvent?): Boolean {
                if (event?.keyCode == KeyEvent.KEYCODE_BACK) {
                    goBack()
                    return true
                }
                return super.dispatchKeyEvent(event)
            }
        }
        initMediaController()

        viewModel.fullScreenMode.observe(
            viewLifecycleOwner
        ) { hide ->
            Compatibility.hideAndroidSystemUI(hide, requireActivity().window)
            (requireActivity() as MainActivity).hideStatusFragment(hide)
        }
    }

    override fun onResume() {
        super.onResume()

        binding.videoView.start()
    }

    override fun onPause() {
        if (mediaController.isShowing) {
            mediaController.hide()
        }

        if (binding.videoView.isPlaying) {
            binding.videoView.pause()
        }

        viewModel.fullScreenMode.value = false
        super.onPause()
    }

    override fun onDestroyView() {
        binding.videoView.stopPlayback()

        super.onDestroyView()
    }

    private fun initMediaController() {
        val videoView = binding.videoView

        videoView.setOnPreparedListener { mediaPlayer ->
            mediaPlayer.setOnVideoSizeChangedListener { _, _, _ ->
                videoView.setMediaController(mediaController)
                // The following will make the video controls above the video
                // mediaController.setAnchorView(videoView)

                // This will make the controls visible right away for 3 seconds
                // If 0 as timeout, they will stay visible mediaController.hide() is called
                mediaController.show()
            }
        }

        videoView.setOnErrorListener { _, what, extra ->
            Log.e("[Video Viewer] Error: $what ($extra)")
            false
        }

        videoView.setVideoPath(viewModel.filePath)
    }
}
