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
package org.linphone.ui.main.file_media_viewer.fragment

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.FileProvider
import androidx.core.view.doOnPreDraw
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.navArgs
import androidx.viewpager2.widget.ViewPager2
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.linphone.R
import org.linphone.core.tools.Log
import org.linphone.databinding.FileMediaViewerFragmentBinding
import org.linphone.ui.GenericActivity
import org.linphone.ui.main.chat.viewmodel.ConversationMediaListViewModel
import org.linphone.ui.main.file_media_viewer.adapter.MediaListAdapter
import org.linphone.ui.main.fragment.GenericFragment
import org.linphone.utils.AppUtils
import org.linphone.utils.FileUtils

class MediaListViewerFragment : GenericFragment() {
    companion object {
        private const val TAG = "[Media List Viewer]"
    }

    private lateinit var binding: FileMediaViewerFragmentBinding

    private lateinit var adapter: MediaListAdapter

    private lateinit var viewModel: ConversationMediaListViewModel

    private lateinit var viewPager: ViewPager2

    private val args: MediaListViewerFragmentArgs by navArgs()

    private var navBarDefaultColor: Int = -1

    private val pageListener = object : ViewPager2.OnPageChangeCallback() {
        override fun onPageSelected(position: Int) {
            val list = viewModel.mediaList.value.orEmpty()
            if (position >= 0 && position < list.size) {
                val model = list[position]
                viewModel.currentlyDisplayedFileName.value = "${model.fileName}\n${model.dateTime}"
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FileMediaViewerFragmentBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        postponeEnterTransition()
        super.onViewCreated(view, savedInstanceState)

        navBarDefaultColor = requireActivity().window.navigationBarColor

        binding.lifecycleOwner = viewLifecycleOwner

        viewModel = ViewModelProvider(this)[ConversationMediaListViewModel::class.java]
        binding.viewModel = viewModel

        // Consider full screen mode the default
        sharedViewModel.mediaViewerFullScreenMode.value = true

        val localSipUri = args.localSipUri
        val remoteSipUri = args.remoteSipUri
        val path = args.path
        Log.i(
            "$TAG Looking up for conversation with local SIP URI [$localSipUri] and remote SIP URI [$remoteSipUri] trying to display file [$path]"
        )
        val chatRoom = sharedViewModel.displayedChatRoom
        viewModel.findChatRoom(chatRoom, localSipUri, remoteSipUri)

        viewModel.mediaList.observe(viewLifecycleOwner) {
            val count = it.size
            Log.i(
                "$TAG Found [$count] media for conversation with local SIP URI [$localSipUri] and remote SIP URI [$remoteSipUri]"
            )
            adapter = MediaListAdapter(this, viewModel)
            viewPager = binding.mediaViewPager
            viewPager.adapter = adapter

            viewPager.registerOnPageChangeCallback(pageListener)

            var index = it.indexOfFirst { model ->
                model.file == path
            }

            if (index == -1) {
                Log.i(
                    "$TAG Path [$path] not found in media list (expected if VFS is enabled), trying using file name"
                )
                val fileName = File(path).name
                val underscore = fileName.indexOf("_")
                val originalFileName = if (underscore != -1 && underscore < 2) {
                    fileName.subSequence(underscore, fileName.length)
                } else {
                    fileName
                }
                index = it.indexOfFirst { model ->
                    model.file.endsWith(originalFileName)
                }
                if (index == -1) {
                    Log.w(
                        "$TAG Path [$path] not found either using filename [$originalFileName] match"
                    )
                }
            }

            val position = if (index == -1) {
                Log.e("$TAG File [$path] not found, using latest one available instead!")
                // TODO: show error toast
                count - 1
            } else {
                index
            }
            viewPager.setCurrentItem(position, false)
            viewPager.offscreenPageLimit = 1

            (view.parent as? ViewGroup)?.doOnPreDraw {
                startPostponedEnterTransition()
            }
        }

        binding.setBackClickListener {
            goBack()
        }

        binding.setShareClickListener {
            shareFile()
        }

        binding.setExportClickListener {
            exportFile()
        }

        sharedViewModel.mediaViewerFullScreenMode.observe(viewLifecycleOwner) {
            if (it != viewModel.fullScreenMode.value) {
                viewModel.fullScreenMode.value = it
            }
        }
    }

    override fun onResume() {
        // Force this navigation bar color
        requireActivity().window.navigationBarColor = requireContext().getColor(R.color.gray_900)

        super.onResume()
    }

    override fun onDestroy() {
        // Reset default navigation bar color
        requireActivity().window.navigationBarColor = navBarDefaultColor

        if (::viewPager.isInitialized) {
            viewPager.unregisterOnPageChangeCallback(pageListener)
        }

        super.onDestroy()
    }

    private fun exportFile() {
        val list = viewModel.mediaList.value.orEmpty()
        val currentItem = binding.mediaViewPager.currentItem
        val model = if (currentItem >= 0 && currentItem < list.size) list[currentItem] else null
        if (model != null) {
            val filePath = model.file
            lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    Log.i("$TAG Export file [$filePath] to Android's MediaStore")
                    val mediaStorePath = FileUtils.addContentToMediaStore(filePath)
                    if (mediaStorePath.isNotEmpty()) {
                        Log.i(
                            "$TAG File [$filePath] has been successfully exported to MediaStore"
                        )
                        val message = AppUtils.getString(
                            R.string.toast_file_successfully_exported_to_media_store
                        )
                        (requireActivity() as GenericActivity).showGreenToast(
                            message,
                            R.drawable.check
                        )
                    } else {
                        Log.e("$TAG Failed to export file [$filePath] to MediaStore!")
                        val message = AppUtils.getString(
                            R.string.toast_export_file_to_media_store_error
                        )
                        (requireActivity() as GenericActivity).showRedToast(
                            message,
                            R.drawable.warning_circle
                        )
                    }
                }
            }
        } else {
            Log.e(
                "$TAG Failed to get FileModel at index [$currentItem], only [${list.size}] items in list"
            )
        }
    }

    private fun shareFile() {
        val list = viewModel.mediaList.value.orEmpty()
        val currentItem = binding.mediaViewPager.currentItem
        val model = if (currentItem >= 0 && currentItem < list.size) list[currentItem] else null
        if (model != null) {
            lifecycleScope.launch {
                val filePath = FileUtils.getProperFilePath(model.file)
                val copy = FileUtils.getFilePath(
                    requireContext(),
                    Uri.parse(filePath),
                    overrideExisting = true,
                    copyToCache = true
                )
                if (!copy.isNullOrEmpty()) {
                    val publicUri = FileProvider.getUriForFile(
                        requireContext(),
                        requireContext().getString(R.string.file_provider),
                        File(copy)
                    )
                    Log.i("$TAG Public URI for file is [$publicUri], starting intent chooser")

                    val sendIntent: Intent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_STREAM, publicUri)
                        putExtra(Intent.EXTRA_SUBJECT, model.fileName)
                        type = model.mimeTypeString
                    }

                    val shareIntent = Intent.createChooser(sendIntent, null)
                    startActivity(shareIntent)
                } else {
                    Log.e("$TAG Failed to copy file [$filePath] to share!")
                }
            }
        } else {
            Log.e(
                "$TAG Failed to get FileModel at index [$currentItem], only [${list.size}] items in list"
            )
        }
    }
}
