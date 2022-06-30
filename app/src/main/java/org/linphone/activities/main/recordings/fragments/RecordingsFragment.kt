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

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import org.linphone.R
import org.linphone.activities.main.fragments.MasterFragment
import org.linphone.activities.main.recordings.adapters.RecordingsListAdapter
import org.linphone.activities.main.recordings.data.RecordingData
import org.linphone.activities.main.recordings.viewmodels.RecordingsViewModel
import org.linphone.core.tools.Log
import org.linphone.databinding.RecordingsFragmentBinding
import org.linphone.utils.AppUtils
import org.linphone.utils.FileUtils
import org.linphone.utils.RecyclerViewHeaderDecoration

class RecordingsFragment : MasterFragment<RecordingsFragmentBinding, RecordingsListAdapter>() {
    private lateinit var viewModel: RecordingsViewModel

    private var videoX: Float = 0f
    private var videoY: Float = 0f

    override fun getLayoutId(): Int = R.layout.recordings_fragment

    override fun onDestroyView() {
        binding.recordingsList.adapter = null
        super.onDestroyView()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.lifecycleOwner = viewLifecycleOwner

        viewModel = ViewModelProvider(this)[RecordingsViewModel::class.java]
        binding.viewModel = viewModel

        _adapter = RecordingsListAdapter(listSelectionViewModel, viewLifecycleOwner)
        binding.recordingsList.setHasFixedSize(true)
        binding.recordingsList.adapter = adapter

        val layoutManager = LinearLayoutManager(requireContext())
        binding.recordingsList.layoutManager = layoutManager

        // Divider between items
        binding.recordingsList.addItemDecoration(AppUtils.getDividerDecoration(requireContext(), layoutManager))

        // Displays the first letter header
        val headerItemDecoration = RecyclerViewHeaderDecoration(requireContext(), adapter)
        binding.recordingsList.addItemDecoration(headerItemDecoration)

        viewModel.recordingsList.observe(
            viewLifecycleOwner
        ) { recordings ->
            adapter.submitList(recordings)
        }

        viewModel.exportRecordingEvent.observe(
            viewLifecycleOwner
        ) {
            it.consume { path ->
                val publicFilePath = FileUtils.getPublicFilePath(requireContext(), "file://$path")
                Log.i("[Recordings] Exporting file [$path] with public URI [$publicFilePath]")
                val intent = Intent(Intent.ACTION_SEND)
                intent.type = " video/x-matroska"
                intent.putExtra(Intent.EXTRA_STREAM, publicFilePath)
                intent.putExtra(Intent.EXTRA_TEXT, getString(R.string.recordings_export))

                try {
                    requireActivity().startActivity(Intent.createChooser(intent, getString(R.string.recordings_export)))
                } catch (anfe: ActivityNotFoundException) {
                    Log.e(anfe)
                }
            }
        }

        binding.setEditClickListener { listSelectionViewModel.isEditionEnabled.value = true }

        binding.setVideoTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    videoX = v.x - event.rawX
                    videoY = v.y - event.rawY
                }
                MotionEvent.ACTION_MOVE -> {
                    v.animate()
                        .x(event.rawX + videoX)
                        .y(event.rawY + videoY)
                        .setDuration(0)
                        .start()
                }
                else -> {
                    v.performClick()
                    false
                }
            }
            true
        }

        adapter.setVideoTextureView(binding.recordingVideoSurface)
    }

    override fun deleteItems(indexesOfItemToDelete: ArrayList<Int>) {
        val list = ArrayList<RecordingData>()
        for (index in indexesOfItemToDelete) {
            val recording = adapter.currentList[index]
            list.add(recording)
        }
        viewModel.deleteRecordings(list)
    }

    override fun onResume() {
        if (this::viewModel.isInitialized) {
            viewModel.updateRecordingsList()
        } else {
            Log.e("[Recordings] Fragment resuming but viewModel lateinit property isn't initialized!")
        }
        super.onResume()
    }
}
