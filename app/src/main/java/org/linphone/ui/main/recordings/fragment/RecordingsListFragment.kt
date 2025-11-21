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
package org.linphone.ui.main.recordings.fragment

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.UiThread
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.linphone.R
import org.linphone.core.tools.Log
import org.linphone.databinding.RecordingsListFragmentBinding
import org.linphone.ui.GenericActivity
import org.linphone.ui.main.fragment.GenericMainFragment
import org.linphone.ui.main.recordings.adapter.RecordingsListAdapter
import org.linphone.ui.main.recordings.viewmodel.RecordingsListViewModel
import org.linphone.utils.AppUtils
import org.linphone.utils.FileUtils
import org.linphone.utils.RecyclerViewHeaderDecoration
import org.linphone.utils.hideKeyboard
import org.linphone.utils.showKeyboard

@UiThread
class RecordingsListFragment : GenericMainFragment() {
    companion object {
        private const val TAG = "[Recordings List Fragment]"
    }

    private lateinit var binding: RecordingsListFragmentBinding

    private lateinit var listViewModel: RecordingsListViewModel

    private lateinit var adapter: RecordingsListAdapter

    private var bottomSheetDialog: BottomSheetDialogFragment? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        adapter = RecordingsListAdapter()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = RecordingsListFragmentBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        listViewModel = ViewModelProvider(this)[RecordingsListViewModel::class.java]

        binding.lifecycleOwner = viewLifecycleOwner
        binding.viewModel = listViewModel
        observeToastEvents(listViewModel)

        binding.setBackClickListener {
            goBack()
        }

        binding.recordingsList.setHasFixedSize(true)
        binding.recordingsList.layoutManager = LinearLayoutManager(requireContext())
        val headerItemDecoration = RecyclerViewHeaderDecoration(requireContext(), adapter)
        binding.recordingsList.addItemDecoration(headerItemDecoration)

        listViewModel.recordings.observe(viewLifecycleOwner) {
            val count = it.size
            adapter.submitList(it)

            // Wait for adapter to have items before setting it in the RecyclerView,
            // otherwise scroll position isn't retained
            if (binding.recordingsList.adapter != adapter) {
                binding.recordingsList.adapter = adapter
            }

            Log.i("$TAG Recordings list ready with [$count] items")
            listViewModel.fetchInProgress.value = false
        }

        listViewModel.searchFilter.observe(viewLifecycleOwner) { filter ->
            listViewModel.applyFilter(filter.trim())
        }

        listViewModel.focusSearchBarEvent.observe(viewLifecycleOwner) {
            it.consume { show ->
                if (show) {
                    // To automatically open keyboard
                    binding.search.showKeyboard()
                } else {
                    binding.search.hideKeyboard()
                }
            }
        }

        adapter.recordingClickedEvent.observe(viewLifecycleOwner) {
            it.consume { model ->
                val action = RecordingsListFragmentDirections.actionRecordingsListFragmentToRecordingMediaPlayerFragment()
                if (findNavController().currentDestination?.id == R.id.recordingsListFragment) {
                    Log.i("$TAG Navigating to recording player for file [${model.filePath}]")
                    sharedViewModel.playingRecording = model

                    findNavController().navigate(action)
                }
            }
        }

        adapter.recordingLongClickedEvent.observe(viewLifecycleOwner) {
            it.consume { model ->
                val modalBottomSheet = RecordingsMenuDialogFragment(
                    { // onDismiss
                        adapter.resetSelection()
                    },
                    { // onShare
                        Log.i("$TAG Sharing call recording [${model.filePath}]")
                        shareFile(model.filePath, model.fileName)
                        adapter.resetSelection()
                    },
                    { // onExport
                        Log.i("$TAG Saving call recording [${model.filePath}]")
                        exportFile(model.filePath)
                        adapter.resetSelection()
                    },
                    { // onDelete
                        Log.i("$TAG Deleting call recording [${model.filePath}]")
                        lifecycleScope.launch {
                            model.delete()
                        }
                        listViewModel.applyFilter(listViewModel.searchFilter.value.orEmpty())
                    }
                )
                modalBottomSheet.show(parentFragmentManager, RecordingsMenuDialogFragment.TAG)
                bottomSheetDialog = modalBottomSheet
            }
        }
    }

    override fun onPause() {
        super.onPause()

        bottomSheetDialog?.dismiss()
        bottomSheetDialog = null
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
            try {
                startActivity(shareIntent)
            } catch (anfe: ActivityNotFoundException) {
                Log.e("$TAG Failed to start intent chooser: $anfe")
            }
        }
    }
}
