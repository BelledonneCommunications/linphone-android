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

import android.annotation.SuppressLint
import android.os.Bundle
import android.widget.MediaController
import androidx.lifecycle.ViewModelProvider
import org.linphone.R
import org.linphone.activities.main.files.viewmodels.AudioFileViewModel
import org.linphone.activities.main.files.viewmodels.AudioFileViewModelFactory
import org.linphone.activities.main.fragments.SecureFragment
import org.linphone.activities.main.viewmodels.SharedMainViewModel
import org.linphone.databinding.FileAudioViewerFragmentBinding

class AudioViewerFragment : SecureFragment<FileAudioViewerFragmentBinding>() {
    private lateinit var viewModel: AudioFileViewModel
    private lateinit var sharedViewModel: SharedMainViewModel

    private lateinit var mediaController: MediaController

    override fun getLayoutId(): Int = R.layout.file_audio_viewer_fragment

    @SuppressLint("ClickableViewAccessibility")
    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        binding.lifecycleOwner = this

        sharedViewModel = requireActivity().run {
            ViewModelProvider(this).get(SharedMainViewModel::class.java)
        }

        val content = sharedViewModel.contentToOpen.value
        content ?: return
        val filePath = content.filePath
        filePath ?: return

        (childFragmentManager.findFragmentById(R.id.top_bar_fragment) as? TopBarFragment)
            ?.setContent(content)

        viewModel = ViewModelProvider(
            this,
            AudioFileViewModelFactory(filePath)
        )[AudioFileViewModel::class.java]
        binding.viewModel = viewModel

        isSecure = arguments?.getBoolean("Secure") ?: false

        mediaController = MediaController(requireContext())
        mediaController.setMediaPlayer(viewModel)

        viewModel.mediaPlayer.setOnPreparedListener {
            mediaController.setAnchorView(binding.anchor)
            // This will make the controls visible right away for 3 seconds
            // If 0 as timeout, they will stay visible mediaController.hide() is called
            mediaController.show(0)
        }
    }

    override fun onPause() {
        viewModel.mediaPlayer.pause()

        super.onPause()
    }
}
