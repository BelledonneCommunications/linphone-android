package org.linphone.ui.main.viewer.fragment

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.UiThread
import androidx.core.view.doOnPreDraw
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import kotlinx.coroutines.launch
import org.linphone.core.tools.Log
import org.linphone.databinding.FileViewerFragmentBinding
import org.linphone.ui.main.MainActivity
import org.linphone.ui.main.fragment.GenericFragment
import org.linphone.ui.main.viewer.adapter.PdfPagesListAdapter
import org.linphone.ui.main.viewer.viewmodel.FileViewModel
import org.linphone.utils.FileUtils

@UiThread
class FileViewerFragment : GenericFragment() {
    companion object {
        private const val TAG = "[File Viewer Fragment]"

        private const val EXPORT_PDF = 10
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

        binding.setShareClickListener {
            lifecycleScope.launch {
                val filePath = FileUtils.getProperFilePath(path)
                val copy = FileUtils.getFilePath(requireContext(), Uri.parse(filePath), false)
                if (!copy.isNullOrEmpty()) {
                    sharedViewModel.filesToShareFromIntent.value = arrayListOf(copy)
                    Log.i("$TAG Sharing file [$copy], going back to conversations list")
                    val action =
                        FileViewerFragmentDirections.actionFileViewerFragmentToConversationsListFragment()
                    findNavController().navigate(action)
                } else {
                    Log.e("$TAG Failed to copy file [$filePath] to share!")
                }
            }
        }

        viewModel.pdfRendererReadyEvent.observe(viewLifecycleOwner) {
            it.consume {
                Log.i("$TAG PDF renderer is ready, attaching adapter to ViewPager")
                if (viewModel.screenWidth == 0 || viewModel.screenHeight == 0) {
                    updateScreenSize()
                }

                adapter = PdfPagesListAdapter(viewModel)
                binding.pdfViewPager.adapter = adapter
                binding.dotsIndicator.attachTo(binding.pdfViewPager)
            }
        }

        viewModel.exportPdfEvent.observe(viewLifecycleOwner) {
            it.consume { name ->
                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_TITLE, name)
                }
                startActivityForResult(intent, EXPORT_PDF)
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

        viewModel.showGreenToastEvent.observe(viewLifecycleOwner) {
            it.consume { pair ->
                val message = pair.first
                val icon = pair.second
                (requireActivity() as MainActivity).showGreenToast(message, icon)
            }
        }

        viewModel.showRedToastEvent.observe(viewLifecycleOwner) {
            it.consume { pair ->
                val message = pair.first
                val icon = pair.second
                (requireActivity() as MainActivity).showRedToast(message, icon)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateScreenSize()
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

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == EXPORT_PDF && resultCode == Activity.RESULT_OK) {
            data?.data?.also { documentUri ->
                Log.i("$TAG Exported PDF should be stored in URI [$documentUri]")
                viewModel.copyPdfToUri(documentUri)
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun updateScreenSize() {
        val displayMetrics = DisplayMetrics()
        requireActivity().windowManager.defaultDisplay.getMetrics(displayMetrics)
        viewModel.screenHeight = displayMetrics.heightPixels
        viewModel.screenWidth = displayMetrics.widthPixels
        Log.i(
            "$TAG Setting screen size ${viewModel.screenWidth}/${viewModel.screenHeight} for PDF renderer"
        )
    }
}
