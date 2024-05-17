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

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.UiThread
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.launch
import org.linphone.core.tools.Log
import org.linphone.databinding.RecordingsListFragmentBinding
import org.linphone.ui.main.fragment.GenericMainFragment
import org.linphone.ui.main.recordings.adapter.RecordingsListAdapter
import org.linphone.ui.main.recordings.viewmodel.RecordingsListViewModel
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

        if (binding.recordingsList.adapter != adapter) {
            binding.recordingsList.adapter = adapter
        }

        listViewModel.recordings.observe(viewLifecycleOwner) {
            val count = it.size
            adapter.submitList(it)
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

        adapter.recordingLongClickedEvent.observe(viewLifecycleOwner) {
            it.consume { model ->
                val modalBottomSheet = RecordingsMenuDialogFragment(
                    { // onDismiss
                        adapter.resetSelection()
                    },
                    { // onShare
                        adapter.resetSelection()
                    },
                    { // onExport
                        adapter.resetSelection()
                    },
                    { // onDelete
                        Log.i("$TAG Deleting meeting [${model.filePath}]")
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
}
