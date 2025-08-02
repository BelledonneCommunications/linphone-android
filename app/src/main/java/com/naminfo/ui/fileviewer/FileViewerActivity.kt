package com.naminfo.ui.fileviewer

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.util.DisplayMetrics
import androidx.activity.enableEdgeToEdge
import androidx.annotation.UiThread
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import java.io.File
import kotlinx.coroutines.launch
import com.naminfo.R
import org.linphone.core.tools.Log
import com.naminfo.databinding.FileViewerActivityBinding
import com.naminfo.ui.GenericActivity
import com.naminfo.ui.fileviewer.adapter.PdfPagesListAdapter
import com.naminfo.ui.fileviewer.viewmodel.FileViewModel
import com.naminfo.utils.FileUtils
import androidx.core.net.toUri

@UiThread
class FileViewerActivity : GenericActivity() {
    companion object {
        private const val TAG = "[File Viewer Activity]"

        private const val EXPORT_FILE_AS_DOCUMENT = 10
    }

    private lateinit var binding: FileViewerActivityBinding

    private lateinit var viewModel: FileViewModel

    private lateinit var adapter: PdfPagesListAdapter

    private val pageChangedListener = object : ViewPager2.OnPageChangeCallback() {
        override fun onPageSelected(position: Int) {
            viewModel.pdfCurrentPage.value = (position + 1).toString()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        binding = DataBindingUtil.setContentView(this, R.layout.file_viewer_activity)
        binding.lifecycleOwner = this
        setUpToastsArea(binding.toastsArea)

        viewModel = ViewModelProvider(this)[FileViewModel::class.java]
        binding.viewModel = viewModel

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(insets.left, insets.top, insets.right, insets.bottom)
            WindowInsetsCompat.CONSUMED
        }

        val args = intent.extras
        if (args == null) {
            finish()
            return
        }

        val path = args.getString("path")
        if (path.isNullOrEmpty()) {
            finish()
            return
        }

        val isFromEphemeralMessage = args.getBoolean("isFromEphemeralMessage", false)
        if (isFromEphemeralMessage) {
            Log.i("$TAG Displayed content is from an ephemeral chat message, force secure mode to prevent screenshots")
            // Force preventing screenshots for ephemeral messages contents
            enableWindowSecureMode(true)
        }

        val timestamp = args.getLong("timestamp", -1)
        val preLoadedContent = args.getString("content")
        Log.i(
            "$TAG Path argument is [$path], pre loaded text content is ${if (preLoadedContent.isNullOrEmpty()) "not available" else "available, using it"}"
        )
        viewModel.isFromEphemeralMessage.value = isFromEphemeralMessage
        viewModel.loadFile(path, timestamp, preLoadedContent)

        binding.setBackClickListener {
            finish()
        }

        viewModel.showRedToastEvent.observe(this) {
            it.consume { pair ->
                val message = getString(pair.first)
                val icon = pair.second
                showRedToast(message, icon)
            }
        }

        viewModel.fileReadyEvent.observe(this) {
            it.consume { done ->
                if (!done) {
                    finish()
                    Log.e("$TAG Failed to open file, going back")
                }
            }
        }

        binding.setShareClickListener {
            shareFile()
        }

        viewModel.pdfRendererReadyEvent.observe(this) {
            it.consume {
                Log.i("$TAG PDF renderer is ready, attaching adapter to ViewPager")
                if (viewModel.screenWidth == 0 || viewModel.screenHeight == 0) {
                    updateScreenSize()
                }

                adapter = PdfPagesListAdapter(viewModel)
                binding.pdfViewPager.adapter = adapter
            }
        }

        viewModel.exportPlainTextFileEvent.observe(this) {
            it.consume { name ->
                exportFile(name, "text/plain")
            }
        }

        viewModel.exportPdfEvent.observe(this) {
            it.consume { name ->
                exportFile(name, "application/pdf")
            }
        }
    }

    override fun onResume() {
        super.onResume()

        updateScreenSize()
        binding.pdfViewPager.registerOnPageChangeCallback(pageChangedListener)
    }

    override fun onPause() {
        binding.pdfViewPager.unregisterOnPageChangeCallback(pageChangedListener)
        super.onPause()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == EXPORT_FILE_AS_DOCUMENT && resultCode == RESULT_OK) {
            data?.data?.also { documentUri ->
                Log.i("$TAG Exported file should be stored in URI [$documentUri]")
                viewModel.copyFileToUri(documentUri)
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun updateScreenSize() {
        val displayMetrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(displayMetrics)
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
                baseContext,
                filePath.toUri(),
                overrideExisting = false,
                copyToCache = true
            )
            if (!copy.isNullOrEmpty()) {
                val publicUri = FileProvider.getUriForFile(
                    baseContext,
                    getString(R.string.file_provider),
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

    private fun exportFile(name: String, mimeType: String) {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = mimeType
            putExtra(Intent.EXTRA_TITLE, name)
        }
        try {
            startActivityForResult(intent, EXPORT_FILE_AS_DOCUMENT)
        } catch (exception: ActivityNotFoundException) {
            Log.e("$TAG No activity found to handle intent ACTION_CREATE_DOCUMENT: $exception")
        }
    }
}
