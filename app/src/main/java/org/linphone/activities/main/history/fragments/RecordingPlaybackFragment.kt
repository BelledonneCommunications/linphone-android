package org.linphone.activities.main.history.fragments

import android.os.Bundle
import android.view.View
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.linphone.R
import org.linphone.activities.GenericFragment
import org.linphone.activities.main.history.adapters.RecordingInfoListAdapter
import org.linphone.activities.main.history.viewmodels.RecordingInfoViewModel
import org.linphone.activities.main.history.viewmodels.RecordingPlaybackViewModel
import org.linphone.databinding.FragmentRecordingPlaybackBinding
import org.linphone.services.RecordingsService
import org.linphone.utils.Log

class RecordingPlaybackFragment : GenericFragment<FragmentRecordingPlaybackBinding>() {

    private lateinit var viewModel: RecordingPlaybackViewModel

    private lateinit var recordingsService: RecordingsService

    override fun getLayoutId(): Int = R.layout.fragment_recording_playback

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recordingsService = RecordingsService.getInstance(requireContext())

        binding.lifecycleOwner = viewLifecycleOwner

        binding.sharedMainViewModel = sharedViewModel

        viewModel = ViewModelProvider(this)[RecordingPlaybackViewModel::class.java]
        binding.viewModel = viewModel

        val callLogViewModel = sharedViewModel.selectedHistoryItem.value
        if (callLogViewModel != null) {
            viewModel?.call?.value = callLogViewModel

            if (callLogViewModel.callId != null) {
                populateRecordingList(callLogViewModel.callId!!)
            }
        }

        val adapter = RecordingInfoListAdapter(viewLifecycleOwner)
        binding.recordingsList.setHasFixedSize(true)
        binding.recordingsList.adapter = adapter

        val layoutManager = LinearLayoutManager(requireContext())
        binding.recordingsList.layoutManager = layoutManager

        viewModel.recordings.observe(viewLifecycleOwner) { recordings ->
            adapter.submitList(recordings.map { r -> RecordingInfoViewModel(r) })
        }

        adapter.recordingSelected.observe(viewLifecycleOwner) { it ->
            it.consume { item ->
                Log.d("Recording selected: $item")
                viewModel.currentRecording.value = item
            }
        }
    }

    private fun populateRecordingList(sessionId: String) {
        Log.d("Fetching recordings for $sessionId...")

        runBlocking {
            launch {
                val recs = recordingsService.getRecordingInfoList(sessionId)
                Log.d("Got ${recs.size} recordings.")

                viewModel.recordings.value = recs
            }
        }
    }
}
