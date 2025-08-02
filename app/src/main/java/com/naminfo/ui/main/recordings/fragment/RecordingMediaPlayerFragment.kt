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
package com.naminfo.ui.main.recordings.fragment

import android.content.Intent
import android.graphics.SurfaceTexture
import android.os.Bundle
import android.view.LayoutInflater
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.naminfo.R
import org.linphone.core.tools.Log
import com.naminfo.databinding.RecordingPlayerFragmentBinding
import com.naminfo.ui.GenericActivity
import com.naminfo.ui.main.fragment.GenericMainFragment
import com.naminfo.ui.main.recordings.viewmodel.RecordingMediaPlayerViewModel
import com.naminfo.utils.AppUtils
import com.naminfo.utils.FileUtils

class RecordingMediaPlayerFragment : GenericMainFragment() {
    companion object {
        private const val TAG = "[Recording Media Player Fragment]"
    }

    private lateinit var binding: RecordingPlayerFragmentBinding

    private lateinit var viewModel: RecordingMediaPlayerViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = RecordingPlayerFragmentBinding.inflate(inflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.lifecycleOwner = viewLifecycleOwner

        viewModel = ViewModelProvider(this)[RecordingMediaPlayerViewModel::class.java]
        binding.viewModel = viewModel
        observeToastEvents(viewModel)

        binding.setBackClickListener {
            goBack()
        }

        binding.setShareClickListener {
            Log.i("$TAG Sharing call recording [${viewModel.recordingModel.filePath}]")
            shareFile(viewModel.recordingModel.filePath, viewModel.recordingModel.fileName)
        }

        binding.setExportClickListener {
            Log.i("$TAG Saving call recording [${viewModel.recordingModel.filePath}]")
            exportFile(viewModel.recordingModel.filePath)
        }

        val model = sharedViewModel.playingRecording
        if (model != null) {
            Log.i("$TAG Loading recording [${model.fileName}] from shared view model")
            viewModel.loadRecording(model)
        } else {
            goBack()
        }
    }

    override fun onResume() {
        super.onResume()

        val textureView = binding.videoPlayer
        if (textureView.isAvailable) {
            Log.i("$TAG Surface created, setting display in player")
            viewModel.setVideoRenderingSurface(textureView)
        } else {
            Log.i("$TAG Surface not available yet, setting listener")
            textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(
                    surfaceTexture: SurfaceTexture,
                    p1: Int,
                    p2: Int
                ) {
                    Log.i("$TAG Surface available, setting display in player")
                    viewModel.setVideoRenderingSurface(textureView)
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
        if (viewModel.isPlaying.value == true) {
            Log.i("$TAG Paused, stopping player")
            viewModel.pause()
        }

        super.onPause()
    }

    private fun exportFile(filePath: String) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                Log.i(
                    "$TAG Export file [$filePath] to Android's MediaStore"
                )
                val mediaStorePath = FileUtils.addContentToMediaStore(filePath)
                if (mediaStorePath.isNotEmpty()) {
                    Log.i(
                        "$TAG File [$filePath] has been successfully exported to MediaStore"
                    )
                    val message = AppUtils.getString(
                        R.string.file_successfully_exported_to_media_store_toast
                    )
                    (requireActivity() as GenericActivity).showGreenToast(
                        message,
                        R.drawable.check
                    )
                } else {
                    Log.e(
                        "$TAG Failed to export file [$filePath] to MediaStore!"
                    )
                    val message = AppUtils.getString(
                        R.string.export_file_to_media_store_error_toast
                    )
                    (requireActivity() as GenericActivity).showRedToast(
                        message,
                        R.drawable.warning_circle
                    )
                }
            }
        }
    }

    private fun shareFile(filePath: String, fileName: String) {
        lifecycleScope.launch {
            val publicUri = FileProvider.getUriForFile(
                requireContext(),
                getString(R.string.file_provider),
                File(filePath)
            )
            Log.i(
                "$TAG Public URI for file is [$publicUri], starting intent chooser"
            )

            val sendIntent: Intent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_STREAM, publicUri)
                putExtra(Intent.EXTRA_SUBJECT, fileName)
                type = FileUtils.getMimeTypeFromExtension(
                    FileUtils.getExtensionFromFileName(fileName)
                )
            }

            val shareIntent = Intent.createChooser(sendIntent, null)
            startActivity(shareIntent)
        }
    }
}
