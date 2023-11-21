package org.linphone.ui.main.viewer.fragment

import android.os.Bundle
import android.util.DisplayMetrics
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.UiThread
import androidx.core.view.doOnPreDraw
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import org.linphone.core.tools.Log
import org.linphone.databinding.FileViewerFragmentBinding
import org.linphone.ui.main.fragment.GenericFragment
import org.linphone.ui.main.viewer.adapter.PdfPagesListAdapter
import org.linphone.ui.main.viewer.viewmodel.FileViewModel

@UiThread
class FileViewerFragment : GenericFragment() {
    companion object {
        private const val TAG = "[File Viewer Fragment]"
    }

    private lateinit var binding: FileViewerFragmentBinding

    private lateinit var viewModel: FileViewModel

    private lateinit var adapter: PdfPagesListAdapter

    private val args: FileViewerFragmentArgs by navArgs()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FileViewerFragmentBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun goBack(): Boolean {
        return findNavController().popBackStack()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(this)[FileViewModel::class.java]

        binding.lifecycleOwner = viewLifecycleOwner
        binding.viewModel = viewModel

        val path = args.path
        Log.i("$TAG Path argument is [$path]")
        viewModel.loadFile(path)

        binding.setBackClickListener {
            goBack()
        }

        viewModel.pdfRendererReadyEvent.observe(viewLifecycleOwner) {
            it.consume {
                Log.i("$TAG PDF renderer is ready, attaching adapter to ViewPager")
                val displayMetrics = DisplayMetrics()
                requireActivity().windowManager.defaultDisplay.getMetrics(displayMetrics)
                viewModel.screenHeight = displayMetrics.heightPixels
                viewModel.screenWidth = displayMetrics.widthPixels

                adapter = PdfPagesListAdapter(viewModel)
                binding.pdfViewPager.adapter = adapter
                binding.dotsIndicator.attachTo(binding.pdfViewPager)
            }
        }

        viewModel.isVideo.observe(viewLifecycleOwner) { isVideo ->
            if (isVideo) {
                binding.videoPlayer.setVideoPath(path)
                binding.videoPlayer.setOnCompletionListener {
                    Log.i("$TAG End of file reached")
                    viewModel.isVideoPlaying.value = false
                }
                (view.parent as? ViewGroup)?.doOnPreDraw {
                    binding.videoPlayer.start()
                    viewModel.isVideoPlaying.value = true
                }
            }
        }

        viewModel.toggleVideoPlayPauseEvent.observe(viewLifecycleOwner) {
            it.consume { play ->
                if (play) {
                    binding.videoPlayer.start()
                } else {
                    binding.videoPlayer.pause()
                }
            }
        }
    }

    override fun onPause() {
        if (binding.videoPlayer.isPlaying) {
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
