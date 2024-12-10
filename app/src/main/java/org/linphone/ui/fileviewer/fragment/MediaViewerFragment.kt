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
package org.linphone.ui.fileviewer.fragment

import android.graphics.SurfaceTexture
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Surface
import android.view.TextureView.SurfaceTextureListener
import android.view.View
import android.view.ViewGroup
import androidx.annotation.UiThread
import androidx.lifecycle.ViewModelProvider
import org.linphone.core.tools.Log
import org.linphone.databinding.FileMediaViewerChildFragmentBinding
import org.linphone.ui.fileviewer.viewmodel.MediaViewModel
import org.linphone.ui.main.fragment.GenericMainFragment
import org.linphone.ui.main.viewmodel.SharedMainViewModel
import org.linphone.utils.FileUtils

@UiThread
class MediaViewerFragment : GenericMainFragment() {
    companion object {
        private const val TAG = "[Media Viewer Fragment]"
    }

    private lateinit var binding: FileMediaViewerChildFragmentBinding

    private lateinit var viewModel: MediaViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FileMediaViewerChildFragmentBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        sharedViewModel = requireActivity().run {
            ViewModelProvider(this)[SharedMainViewModel::class.java]
        }

        viewModel = ViewModelProvider(this)[MediaViewModel::class.java]
        viewModel.fullScreenMode.value = sharedViewModel.mediaViewerFullScreenMode.value == true

        binding.lifecycleOwner = viewLifecycleOwner
        binding.viewModel = viewModel
        observeToastEvents(viewModel)

        val path = if (arguments?.containsKey("path") == true) {
            requireArguments().getString("path")
        } else {
            ""
        }
        if (path.isNullOrEmpty()) {
            Log.e("$TAG Path argument not found!")
            return
        }

        val exists = FileUtils.doesFileExist(path)
        Log.i("$TAG Path argument is [$path], it ${if (exists) "exists" else "doesn't exist"}")
        viewModel.loadFile(path)

        binding.setToggleFullScreenModeClickListener {
            val fullScreenMode = viewModel.toggleFullScreen()
            sharedViewModel.mediaViewerFullScreenMode.value = fullScreenMode
        }

        viewModel.videoSizeChangedEvent.observe(viewLifecycleOwner) {
            it.consume { pair ->
                val width = pair.first
                val height = pair.second
                Log.i("$TAG Updating video texture ration to ${width}x$height")
                binding.videoPlayer.setAspectRatio(width, height)
            }
        }

        viewModel.changeFullScreenModeEvent.observe(viewLifecycleOwner) {
            it.consume { fullScreenMode ->
                sharedViewModel.mediaViewerFullScreenMode.value = fullScreenMode
            }
        }
    }

    override fun onResume() {
        super.onResume()

        val textureView = binding.videoPlayer
        if (textureView.isAvailable) {
            Log.i("$TAG Surface created, setting display in mediaPlayer")
            viewModel.mediaPlayer.setSurface((Surface(textureView.surfaceTexture)))
        } else {
            Log.i("$TAG Surface not available yet, setting listener")
            textureView.surfaceTextureListener = object : SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(
                    surfaceTexture: SurfaceTexture,
                    p1: Int,
                    p2: Int
                ) {
                    Log.i("$TAG Surface available, setting display in mediaPlayer")
                    viewModel.mediaPlayer.setSurface(Surface(surfaceTexture))
                }

                override fun onSurfaceTextureSizeChanged(
                    surfaceTexture: SurfaceTexture,
                    p1: Int,
                    p2: Int
                ) {
                }

                override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
                    return true
                }

                override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) {
                }
            }
        }

        viewModel.play()
    }

    override fun onPause() {
        if (viewModel.isMediaPlaying.value == true) {
            Log.i("$TAG Paused, stopping media player")
            viewModel.pause()
        }

        super.onPause()
    }
}
