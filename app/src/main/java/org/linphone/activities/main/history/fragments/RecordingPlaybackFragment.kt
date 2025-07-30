package org.linphone.activities.main.history.fragments

import android.os.Bundle
import android.view.View
import androidx.lifecycle.ViewModelProvider
import org.linphone.R
import org.linphone.activities.GenericFragment
import org.linphone.activities.main.history.viewmodels.RecordingPlaybackViewModel
import org.linphone.databinding.FragmentRecordingPlaybackBinding

class RecordingPlaybackFragment : GenericFragment<FragmentRecordingPlaybackBinding>() {

    private lateinit var viewModel: RecordingPlaybackViewModel

    override fun getLayoutId(): Int = R.layout.fragment_recording_playback

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.lifecycleOwner = viewLifecycleOwner

        binding.sharedMainViewModel = sharedViewModel

        viewModel = ViewModelProvider(this)[RecordingPlaybackViewModel::class.java]
        binding.viewModel = viewModel

        val callLogViewModel = sharedViewModel.selectedHistoryItem.value
        if (callLogViewModel != null) {
            viewModel?.call?.value = callLogViewModel
        }
    }
}
