package org.linphone.ui.main.file_media_viewer.fragment

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.UiThread
import androidx.core.content.FileProvider
import androidx.core.view.doOnPreDraw
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback
import java.io.File
import kotlinx.coroutines.launch
import org.linphone.R
import org.linphone.core.tools.Log
import org.linphone.databinding.FileViewerFragmentBinding
import org.linphone.ui.main.MainActivity
import org.linphone.ui.main.file_media_viewer.adapter.PdfPagesListAdapter
import org.linphone.ui.main.file_media_viewer.viewmodel.FileViewModel
import org.linphone.ui.main.fragment.GenericFragment
import org.linphone.utils.FileUtils

@UiThread
class FileViewerFragment : GenericFragment() {
    companion object {
        private const val TAG = "[File Viewer Fragment]"

        private const val EXPORT_FILE_AS_DOCUMENT = 10
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

        val path = args.path
        val preLoadedContent = args.content
        Log.i(
            "$TAG Path argument is [$path], pre loaded text content is ${if (preLoadedContent.isNullOrEmpty()) "not available" else "available, using it"}"
        )
        viewModel.loadFile(path, preLoadedContent)

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
            shareFile()
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

        viewModel.exportPlainTextFileEvent.observe(viewLifecycleOwner) {
            it.consume { name ->
                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TITLE, name)
                }
                startActivityForResult(intent, EXPORT_FILE_AS_DOCUMENT)
            }
        }

        viewModel.exportPdfEvent.observe(viewLifecycleOwner) {
            it.consume { name ->
                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_TITLE, name)
                }
                startActivityForResult(intent, EXPORT_FILE_AS_DOCUMENT)
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
        super.onPause()
    }

    override fun onDestroy() {
        // Reset default navigation bar color
        requireActivity().window.navigationBarColor = navBarDefaultColor

        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == EXPORT_FILE_AS_DOCUMENT && resultCode == Activity.RESULT_OK) {
            data?.data?.also { documentUri ->
                Log.i("$TAG Exported file should be stored in URI [$documentUri]")
                viewModel.copyFileToUri(documentUri)
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

    private fun shareFile() {
        lifecycleScope.launch {
            val filePath = FileUtils.getProperFilePath(viewModel.getFilePath())
            val copy = FileUtils.getFilePath(
                requireContext(),
                Uri.parse(filePath),
                overrideExisting = true,
                copyToCache = true
            )
            if (!copy.isNullOrEmpty()) {
                val publicUri = FileProvider.getUriForFile(
                    requireContext(),
                    requireContext().getString(R.string.file_provider),
                    File(copy)
                )
                Log.i("$TAG Public URI for file is [$publicUri], starting intent chooser")

                val sendIntent: Intent = Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_STREAM, publicUri)
                    putExtra(Intent.EXTRA_SUBJECT, viewModel.fileName.value.orEmpty())
                    type = viewModel.mimeType.value.orEmpty()
                }

                val shareIntent = Intent.createChooser(sendIntent, null)
                startActivity(shareIntent)
            } else {
                Log.e("$TAG Failed to copy file [$filePath] to share!")
            }
        }
    }
}
