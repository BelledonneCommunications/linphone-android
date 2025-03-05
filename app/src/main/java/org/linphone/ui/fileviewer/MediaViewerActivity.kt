package org.linphone.ui.fileviewer

import android.content.Intent
import android.os.Bundle
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.R
import org.linphone.core.tools.Log
import org.linphone.databinding.FileMediaViewerActivityBinding
import org.linphone.ui.GenericActivity
import org.linphone.ui.fileviewer.adapter.MediaListAdapter
import org.linphone.ui.fileviewer.viewmodel.MediaListViewModel
import org.linphone.ui.main.chat.model.FileModel
import org.linphone.ui.main.viewmodel.SharedMainViewModel
import org.linphone.utils.AppUtils
import org.linphone.utils.FileUtils
import androidx.core.net.toUri

@UiThread
class MediaViewerActivity : GenericActivity() {
    companion object {
        private const val TAG = "[Media Viewer Activity]"
    }

    private lateinit var binding: FileMediaViewerActivityBinding

    private lateinit var adapter: MediaListAdapter

    private lateinit var viewModel: MediaListViewModel

    private lateinit var viewPager: ViewPager2

    private lateinit var sharedViewModel: SharedMainViewModel

    private val pageListener = object : ViewPager2.OnPageChangeCallback() {
        override fun onPageSelected(position: Int) {
            val list = viewModel.mediaList.value.orEmpty()
            if (position >= 0 && position < list.size) {
                val model = list[position]
                viewModel.currentlyDisplayedFileName.value = model.fileName
                viewModel.currentlyDisplayedFileDateTime.value = model.dateTime

                val isFromEphemeral = model.isFromEphemeralMessage
                viewModel.isCurrentlyDisplayedFileFromEphemeralMessage.value = isFromEphemeral
                if (!corePreferences.enableSecureMode) {
                    // Force preventing screenshots for ephemeral messages contents, but allow it for others
                    enableWindowSecureMode(isFromEphemeral)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        binding = DataBindingUtil.setContentView(this, R.layout.file_media_viewer_activity)
        binding.lifecycleOwner = this
        setUpToastsArea(binding.toastsArea)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(insets.left, insets.top, insets.right, insets.bottom)
            WindowInsetsCompat.CONSUMED
        }

        sharedViewModel = run {
            ViewModelProvider(this)[SharedMainViewModel::class.java]
        }
        binding.sharedViewModel = sharedViewModel
        sharedViewModel.mediaViewerFullScreenMode.value = true

        viewModel = ViewModelProvider(this)[MediaListViewModel::class.java]
        binding.viewModel = viewModel

        adapter = MediaListAdapter(this, viewModel)

        viewPager = binding.mediaViewPager
        viewPager.adapter = adapter
        viewPager.registerOnPageChangeCallback(pageListener)

        val args = intent.extras
        if (args == null) {
            finish()
            return
        }

        val path = args.getString("path", "")
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
        val isEncrypted = args.getBoolean("isEncrypted", false)
        val originalPath = args.getString("originalPath", "")
        Log.i("$TAG Path argument is [$path], timestamp [$timestamp], encrypted [$isEncrypted] and original path [$originalPath]")
        viewModel.initTempModel(path, timestamp, isEncrypted, originalPath, isFromEphemeralMessage)

        val conversationId = args.getString("conversationId").orEmpty()
        Log.i(
            "$TAG Looking up for conversation with conversation ID [$conversationId] trying to display file [$path]"
        )
        viewModel.findChatRoom(null, conversationId)

        viewModel.mediaList.observe(this) {
            updateMediaList(path, it)
        }

        binding.setBackClickListener {
            finish()
        }

        binding.setShareClickListener {
            shareFile()
        }

        binding.setExportClickListener {
            exportFile()
        }
    }

    override fun onDestroy() {
        if (::viewPager.isInitialized) {
            viewPager.unregisterOnPageChangeCallback(pageListener)
        }

        super.onDestroy()
    }

    private fun updateMediaList(path: String, list: List<FileModel>) {
        val count = list.size
        Log.i("$TAG Found [$count] media for conversation")

        var index = list.indexOfFirst { model ->
            model.path == path
        }

        if (index == -1) {
            Log.d(
                "$TAG Path [$path] not found in media list (expected if VFS is enabled), trying using file name"
            )
            val fileName = FileUtils.getNameFromFilePath(path)
            val underscore = fileName.indexOf("_")
            val originalFileName = if (underscore != -1 && underscore < 2) {
                fileName.subSequence(underscore, fileName.length)
            } else {
                fileName
            }
            index = list.indexOfFirst { model ->
                model.path.endsWith(originalFileName)
            }
            if (index == -1) {
                Log.w(
                    "$TAG Path [$path] not found in media list using either path or filename [$originalFileName]"
                )
            }
        }

        val position = if (index == -1) {
            Log.e(
                "$TAG File [$path] not found, using most recent one instead!"
            )
            val message = getString(R.string.conversation_media_not_found_toast)
            val icon = R.drawable.warning_circle
            showRedToast(message, icon)

            0
        } else {
            index
        }

        viewPager.setCurrentItem(position, false)
        viewPager.offscreenPageLimit = 1
    }

    private fun exportFile() {
        val list = viewModel.mediaList.value.orEmpty()
        val currentItem = binding.mediaViewPager.currentItem
        val model = if (currentItem >= 0 && currentItem < list.size) list[currentItem] else null
        if (model != null) {
            val filePath = model.path
            lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    Log.i(
                        "$TAG Export file [$filePath] to Android's MediaStore"
                    )
                    val mediaStorePath = FileUtils.addContentToMediaStore(filePath)
                    if (mediaStorePath.isNotEmpty()) {
                        Log.i(
                            "$TAG File [$filePath] has been successfully exported to MediaStore"
                        )
                        val message = AppUtils.getString(
                            R.string.file_successfully_exported_to_media_store_toast
                        )
                        showGreenToast(
                            message,
                            R.drawable.check
                        )
                    } else {
                        Log.e(
                            "$TAG Failed to export file [$filePath] to MediaStore!"
                        )
                        val message = AppUtils.getString(
                            R.string.export_file_to_media_store_error_toast
                        )
                        showRedToast(
                            message,
                            R.drawable.warning_circle
                        )
                    }
                }
            }
        } else {
            Log.e(
                "$TAG Failed to get FileModel at index [$currentItem], only [${list.size}] items in list"
            )
        }
    }

    private fun shareFile() {
        val list = viewModel.mediaList.value.orEmpty()
        val currentItem = binding.mediaViewPager.currentItem
        val model = if (currentItem >= 0 && currentItem < list.size) list[currentItem] else null
        if (model != null) {
            lifecycleScope.launch {
                val filePath = FileUtils.getProperFilePath(model.path)
                val copy = FileUtils.getFilePath(
                    baseContext,
                    filePath.toUri(),
                    overrideExisting = true,
                    copyToCache = true
                )
                if (!copy.isNullOrEmpty()) {
                    val publicUri = FileProvider.getUriForFile(
                        baseContext,
                        getString(R.string.file_provider),
                        File(copy)
                    )
                    Log.i(
                        "$TAG Public URI for file is [$publicUri], starting intent chooser"
                    )

                    val sendIntent: Intent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_STREAM, publicUri)
                        putExtra(Intent.EXTRA_SUBJECT, model.fileName)
                        type = model.mimeTypeString
                    }

                    val shareIntent = Intent.createChooser(sendIntent, null)
                    startActivity(shareIntent)
                } else {
                    Log.e(
                        "$TAG Failed to copy file [$filePath] to share!"
                    )
                }
            }
        } else {
            Log.e(
                "$TAG Failed to get FileModel at index [$currentItem], only [${list.size}] items in list"
            )
        }
    }
}
