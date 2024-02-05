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
import androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback
import kotlinx.coroutines.launch
import org.linphone.R
import org.linphone.core.tools.Log
import org.linphone.databinding.FileViewerFragmentBinding
import org.linphone.ui.main.MainActivity
import org.linphone.ui.main.fragment.SlidingPaneChildFragment
import org.linphone.ui.main.viewer.adapter.PdfPagesListAdapter
import org.linphone.ui.main.viewer.viewmodel.FileViewModel
import org.linphone.utils.Event
import org.linphone.utils.FileUtils

@UiThread
class FileViewerFragment : SlidingPaneChildFragment() {
    companion object {
        private const val TAG = "[File Viewer Fragment]"

        private const val EXPORT_PDF = 10
    }

    private lateinit var binding: FileViewerFragmentBinding

    private lateinit var viewModel: FileViewModel

    private lateinit var adapter: PdfPagesListAdapter

    private val args: FileViewerFragmentArgs by navArgs()

    private val pageChangedListener = object : OnPageChangeCallback() {
        override fun onPageSelected(position: Int) {
            viewModel.pdfCurrentPage.value = (position + 1).toString()
        }
    }

    private var navBarDefaultColor: Int = -1

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
        postponeEnterTransition()
        super.onViewCreated(view, savedInstanceState)

        navBarDefaultColor = requireActivity().window.navigationBarColor

        viewModel = ViewModelProvider(this)[FileViewModel::class.java]

        binding.lifecycleOwner = viewLifecycleOwner
        binding.viewModel = viewModel

        val path = if (arguments?.containsKey("path") == true) {
            requireArguments().getString("path", args.path)
        } else {
            args.path
        }
        Log.i("$TAG Path argument is [$path]")
        viewModel.loadFile(path)

        binding.setBackClickListener {
            goBack()
        }

        viewModel.fileReadyEvent.observe(viewLifecycleOwner) {
            it.consume { done ->
                if (done) {
                    (view.parent as? ViewGroup)?.doOnPreDraw {
                        startPostponedEnterTransition()
                    }
                } else {
                    (view.parent as? ViewGroup)?.doOnPreDraw {
                        Log.e("$TAG Failed to open file, going back")
                        goBack()
                    }
                }
            }
        }

        binding.setShareClickListener {
            lifecycleScope.launch {
                val filePath = FileUtils.getProperFilePath(path)
                val copy = FileUtils.getFilePath(requireContext(), Uri.parse(filePath), false)
                if (!copy.isNullOrEmpty()) {
                    sharedViewModel.filesToShareFromIntent.value = arrayListOf(copy)
                    Log.i("$TAG Sharing file [$copy], going back to conversations list")
                    sharedViewModel.closeSlidingPaneEvent.value = Event(true)
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
        // Force this navigation bar color
        requireActivity().window.navigationBarColor = requireContext().getColor(R.color.gray_900)

        super.onResume()

        updateScreenSize()
        binding.pdfViewPager.registerOnPageChangeCallback(pageChangedListener)
    }

    override fun onPause() {
        binding.pdfViewPager.unregisterOnPageChangeCallback(pageChangedListener)

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

    override fun onDestroy() {
        // Reset default navigation bar color
        requireActivity().window.navigationBarColor = navBarDefaultColor

        super.onDestroy()
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
