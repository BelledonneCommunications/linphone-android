package org.linphone.activities.main.history.fragments

import android.os.Bundle
import android.view.View
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.lifecycle.ViewModelProvider
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
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

    private lateinit var mediaPlayer: ExoPlayer

    private lateinit var adapter: RecordingInfoListAdapter

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

        adapter = RecordingInfoListAdapter(viewLifecycleOwner)
        binding.recordingsList.setHasFixedSize(true)
        binding.recordingsList.adapter = adapter

        val layoutManager = LinearLayoutManager(requireContext())
        binding.recordingsList.layoutManager = layoutManager

        viewModel.recordings.observe(viewLifecycleOwner) { recordings ->
            adapter.submitList(recordings.map { r -> RecordingInfoViewModel(r) })
        }

        adapter.recordingSelected.observe(viewLifecycleOwner) { it ->
            it.consume { item -> playRecording(item) }
        }

        mediaPlayer = ExoPlayer.Builder(requireContext()).build()

        val playerView = view.findViewById<PlayerView>(R.id.mediaPlayerView)
        playerView.player = mediaPlayer
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

    @OptIn(UnstableApi::class)
    private fun playRecording(item: RecordingInfoViewModel) {
        Log.d("Recording selected: $item")
        viewModel.currentRecording.value = item

        // Mark the item as playing
        val recordings = adapter.currentList
        for (r in recordings) {
            r.isPlaying.value = false
        }
        item.isPlaying.value = true

        mediaPlayer.stop()

        val sessionId = viewModel.call.value?.callId

        if (sessionId == null || item.info.id == null) {
            Log.e("Unable to play recording - info is incomplete.")
            viewModel.currentRecording.value = null
        } else {
            try {
                runBlocking {
                    launch {
                        val file = recordingsService.getRecordingAudio(sessionId, item.info.id)

                        val uri = file.toUri()
                        // val sourceUri = RawResourceDataSource.buildRawResourceUri(R.raw.example)
                        val mediaItem = MediaItem.fromUri(uri)
                        mediaPlayer.setMediaItem(mediaItem)
                        mediaPlayer.prepare()
                        mediaPlayer.play()
                    }
                }
            } catch (e: Exception) {
                Log.e("Failed to retrieve recording audio.")
                viewModel.currentRecording.value = null
            }
        }
    }
}
