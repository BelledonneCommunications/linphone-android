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
import android.view.View
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.linphone.R
import org.linphone.activities.GenericFragment
import org.linphone.activities.SnackBarActivity
import org.linphone.compatibility.Compatibility
import org.linphone.core.Content
import org.linphone.core.tools.Log
import org.linphone.databinding.FileViewerTopBarFragmentBinding
import org.linphone.utils.FileUtils
import org.linphone.utils.PermissionHelper

class TopBarFragment : GenericFragment<FileViewerTopBarFragmentBinding>() {
    private var content: Content? = null
    private var plainFilePath: String = ""

    override fun getLayoutId(): Int = R.layout.file_viewer_top_bar_fragment

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.lifecycleOwner = viewLifecycleOwner
        useMaterialSharedAxisXForwardAnimation = false

        binding.setExportClickListener {
            val contentToExport = content
            if (contentToExport != null) {
                exportContent(contentToExport)
            } else {
                Log.e("[File Viewer] No Content set!")
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("FilePath", plainFilePath)
        super.onSaveInstanceState(outState)
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        plainFilePath = savedInstanceState?.getString("FilePath") ?: plainFilePath
    }

    override fun onDestroyView() {
        if (plainFilePath.isNotEmpty() && plainFilePath != content?.filePath.orEmpty()) {
            Log.i("[File Viewer] Destroying plain file path: $plainFilePath")
            FileUtils.deleteFile(plainFilePath)
        }
        super.onDestroyView()
    }

    fun setContent(c: Content) {
        Log.i("[File Viewer] Content file path is: ${c.filePath}")
        content = c
        binding.fileName.text = c.name
    }

    private fun exportContent(content: Content) {
        lifecycleScope.launch {
            var mediaStoreFilePath = ""
            if (PermissionHelper.get().hasWriteExternalStoragePermission()) {
                Log.i("[File Viewer] Exporting image through Media Store API")
                when (content.type) {
                    "image" -> {
                        val export = lifecycleScope.async {
                            Compatibility.addImageToMediaStore(requireContext(), content)
                        }
                        if (export.await()) {
                            Log.i("[File Viewer] Adding image ${content.name} to Media Store terminated: ${content.userData}")
                            mediaStoreFilePath = content.userData.toString()
                        } else {
                            Log.e("[File Viewer] Something went wrong while copying file to Media Store...")
                        }
                    }
                    "video" -> {
                        val export = lifecycleScope.async {
                            Compatibility.addVideoToMediaStore(requireContext(), content)
                        }
                        if (export.await()) {
                            Log.i("[File Viewer] Adding video ${content.name} to Media Store terminated: ${content.userData}")
                            mediaStoreFilePath = content.userData.toString()
                        } else {
                            Log.e("[File Viewer] Something went wrong while copying file to Media Store...")
                        }
                    }
                    "audio" -> {
                        val export = lifecycleScope.async {
                            Compatibility.addAudioToMediaStore(requireContext(), content)
                        }
                        if (export.await()) {
                            Log.i("[File Viewer] Adding audio ${content.name} to Media Store terminated: ${content.userData}")
                            mediaStoreFilePath = content.userData.toString()
                        } else {
                            Log.e("[File Viewer] Something went wrong while copying file to Media Store...")
                        }
                    }
                    else -> {
                        Log.w("[File Viewer] File ${content.name} isn't either an image, an audio file or a video, can't add it to the Media Store")
                    }
                }
            } else {
                Log.w("[File Viewer] Can't export image through Media Store API (requires Android 10 or WRITE_EXTERNAL permission, using fallback method...")
            }

            withContext(Dispatchers.Main) {
                if (mediaStoreFilePath.isEmpty()) {
                    Log.w("[File Viewer] Media store file path is empty, media store export failed?")

                    val filePath = content.exportPlainFile().orEmpty()
                    plainFilePath = filePath.ifEmpty { content.filePath.orEmpty() }
                    Log.i("[File Viewer] Plain file path is: $plainFilePath")
                    if (plainFilePath.isNotEmpty()) {
                        if (!FileUtils.openFileInThirdPartyApp(requireActivity(), plainFilePath)) {
                            (requireActivity() as SnackBarActivity).showSnackBar(R.string.chat_message_no_app_found_to_handle_file_mime_type)
                            if (plainFilePath != content.filePath.orEmpty()) {
                                Log.i("[File Viewer] No app to open plain file path: $plainFilePath, destroying it")
                                FileUtils.deleteFile(plainFilePath)
                            }
                            plainFilePath = ""
                        }
                    }
                } else {
                    plainFilePath = ""
                    Log.i("[File Viewer] Media store file path is: $mediaStoreFilePath")
                    FileUtils.openMediaStoreFile(requireActivity(), mediaStoreFilePath)
                }
            }
        }
    }
}
