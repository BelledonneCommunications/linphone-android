package org.linphone.ui.main.file_media_viewer.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.UiThread
import androidx.lifecycle.ViewModelProvider
import org.linphone.core.tools.Log
import org.linphone.databinding.FileMediaViewerChildFragmentBinding
import org.linphone.ui.main.file_media_viewer.viewmodel.MediaViewModel
import org.linphone.ui.main.fragment.GenericFragment
import org.linphone.ui.main.viewmodel.SharedMainViewModel

@UiThread
class MediaViewerFragment : GenericFragment() {
    companion object {
        private const val TAG = "[Media Viewer Fragment]"
    }

    private lateinit var binding: FileMediaViewerChildFragmentBinding

    private lateinit var viewModel: MediaViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FileMediaViewerChildFragmentBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        sharedViewModel = requireActivity().run {
            ViewModelProvider(this)[SharedMainViewModel::class.java]
        }

        viewModel = ViewModelProvider(this)[MediaViewModel::class.java]

        binding.lifecycleOwner = viewLifecycleOwner
        binding.viewModel = viewModel

        val path = if (arguments?.containsKey("path") == true) {
            requireArguments().getString("path")
        } else {
            ""
        }
        if (path.isNullOrEmpty()) {
            Log.e("$TAG Path argument not found!")
            return
        }

        Log.i("$TAG Path argument is [$path]")
        viewModel.loadFile(path)

        viewModel.isVideo.observe(viewLifecycleOwner) { isVideo ->
            if (isVideo) {
                Log.i("$TAG Creating video player for file [$path]")
                binding.videoPlayer.setVideoPath(path)
                binding.videoPlayer.setOnCompletionListener {
                    Log.i("$TAG End of file reached")
                    viewModel.isVideoPlaying.value = false
                }
            }
        }

        viewModel.toggleVideoPlayPauseEvent.observe(viewLifecycleOwner) {
            it.consume { play ->
                if (play) {
                    Log.i("$TAG Starting video player")
                    binding.videoPlayer.start()
                } else {
                    Log.i("$TAG Pausing video player")
                    binding.videoPlayer.pause()
                }
            }
        }

        viewModel.fullScreenMode.observe(viewLifecycleOwner) {
            if (it != sharedViewModel.mediaViewerFullScreenMode.value) {
                sharedViewModel.mediaViewerFullScreenMode.value = it
            }
        }
    }

    override fun onResume() {
        super.onResume()

        viewModel.fullScreenMode.value = sharedViewModel.mediaViewerFullScreenMode.value

        if (viewModel.isVideo.value == true) {
            Log.i("$TAG Resumed, starting video player")
            binding.videoPlayer.start()
            viewModel.isVideoPlaying.value = true
        }
    }

    override fun onPause() {
        if (binding.videoPlayer.isPlaying) {
            Log.i("$TAG Paused, stopping video player")
            binding.videoPlayer.pause()
            viewModel.isVideoPlaying.value = false
        }

        super.onPause()
    }

    override fun onDestroyView() {
        binding.videoPlayer.stopPlayback()

        super.onDestroyView()
    }
}
