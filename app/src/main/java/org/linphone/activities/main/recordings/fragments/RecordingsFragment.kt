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
package org.linphone.activities.main.recordings.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import org.linphone.R
import org.linphone.activities.main.fragments.MasterFragment
import org.linphone.activities.main.recordings.adapters.RecordingsListAdapter
import org.linphone.activities.main.recordings.viewmodels.RecordingViewModel
import org.linphone.activities.main.recordings.viewmodels.RecordingsViewModel
import org.linphone.databinding.RecordingsFragmentBinding
import org.linphone.utils.RecyclerViewHeaderDecoration

class RecordingsFragment : MasterFragment() {
    private lateinit var binding: RecordingsFragmentBinding
    private lateinit var viewModel: RecordingsViewModel
    private lateinit var adapter: RecordingsListAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = RecordingsFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        binding.lifecycleOwner = this

        viewModel = ViewModelProvider(this).get(RecordingsViewModel::class.java)
        binding.viewModel = viewModel

        adapter =
            RecordingsListAdapter(
                listSelectionViewModel
            )
        binding.recordingsList.adapter = adapter

        val layoutManager = LinearLayoutManager(activity)
        binding.recordingsList.layoutManager = layoutManager

        // Divider between items
        val dividerItemDecoration = DividerItemDecoration(context, layoutManager.orientation)
        dividerItemDecoration.setDrawable(resources.getDrawable(R.drawable.divider, null))
        binding.recordingsList.addItemDecoration(dividerItemDecoration)

        // Displays the first letter header
        val headerItemDecoration = RecyclerViewHeaderDecoration(adapter)
        binding.recordingsList.addItemDecoration(headerItemDecoration)

        viewModel.recordingsList.observe(viewLifecycleOwner, Observer { recordings ->
            adapter.submitList(recordings)
        })

        binding.setBackClickListener { findNavController().popBackStack() }

        binding.setEditClickListener { listSelectionViewModel.isEditionEnabled.value = true }
    }

    override fun getItemCount(): Int {
        return adapter.itemCount
    }

    override fun deleteItems(indexesOfItemToDelete: ArrayList<Int>) {
        val list = ArrayList<RecordingViewModel>()
        for (index in indexesOfItemToDelete) {
            val recording = adapter.getItemAt(index)
            list.add(recording)
        }
        viewModel.deleteRecordings(list)
    }
}
