package org.linphone.ui.main.viewer.viewmodel

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import android.widget.ImageView
import androidx.annotation.UiThread
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.compatibility.Compatibility
import org.linphone.core.tools.Log
import org.linphone.utils.AppUtils
import org.linphone.utils.Event
import org.linphone.utils.FileUtils

class FileViewModel @UiThread constructor() : ViewModel() {
    companion object {
        private const val TAG = "[File ViewModel]"
    }

    val path = MutableLiveData<String>()

    val fileName = MutableLiveData<String>()

    val fullScreenMode = MutableLiveData<Boolean>()

    val isPdf = MutableLiveData<Boolean>()

    val isVideo = MutableLiveData<Boolean>()

    val isVideoPlaying = MutableLiveData<Boolean>()

    val pdfRendererReadyEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val toggleVideoPlayPauseEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    // Below are required for PDF viewer
    private lateinit var pdfRenderer: PdfRenderer

    private lateinit var filePath: String

    var screenWidth: Int = 0
    var screenHeight: Int = 0
    // End of PDF viewer required variables

    override fun onCleared() {
        if (::pdfRenderer.isInitialized) {
            pdfRenderer.close()
        }
        super.onCleared()
    }

    @UiThread
    fun loadFile(file: String) {
        filePath = file
        val name = FileUtils.getNameFromFilePath(file)
        fileName.value = name

        val extension = FileUtils.getExtensionFromFileName(name)
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
        when (FileUtils.getMimeType(mime)) {
            FileUtils.MimeType.Pdf -> {
                Log.i("$TAG File [$file] seems to be a PDF")
                isPdf.value = true
                fullScreenMode.value = false

                viewModelScope.launch {
                    withContext(Dispatchers.IO) {
                        val input = ParcelFileDescriptor.open(
                            File(file),
                            ParcelFileDescriptor.MODE_READ_ONLY
                        )
                        pdfRenderer = PdfRenderer(input)
                        Log.i("$TAG ${pdfRenderer.pageCount} pages in file $file")
                        pdfRendererReadyEvent.postValue(Event(true))
                    }
                }
            }
            FileUtils.MimeType.Video -> {
                Log.i("$TAG File [$file] seems to be a video")
                isVideo.value = true
                isVideoPlaying.value = false
                fullScreenMode.value = true
            }
            else -> {
                path.value = file
                fullScreenMode.value = true
            }
        }
    }

    @UiThread
    fun toggleFullScreen() {
        fullScreenMode.value = fullScreenMode.value != true
    }

    @UiThread
    fun getPagesCount(): Int {
        if (::pdfRenderer.isInitialized) {
            return pdfRenderer.pageCount
        }
        return 0
    }

    @UiThread
    fun loadPdfPageInto(index: Int, view: ImageView) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val page: PdfRenderer.Page = pdfRenderer.openPage(index)
                    val width = if (screenWidth <= screenHeight) screenWidth else screenHeight
                    val bm = Bitmap.createBitmap(
                        width,
                        (width / page.width * page.height),
                        Bitmap.Config.ARGB_8888
                    )
                    page.render(bm, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()

                    withContext(Dispatchers.Main) {
                        view.setImageBitmap(bm)
                    }
                } catch (e: Exception) {
                    Log.e("$TAG Exception: $e")
                }
            }
        }
    }

    @UiThread
    fun playPauseVideo() {
        val playVideo = isVideoPlaying.value == false
        isVideoPlaying.value = playVideo
        toggleVideoPlayPauseEvent.value = Event(playVideo)
    }

    @UiThread
    fun exportToMediaStore() {
        if (::filePath.isInitialized) {
            viewModelScope.launch {
                withContext(Dispatchers.IO) {
                    Log.i("$TAG Export file [$filePath] to Android's MediaStore")
                    if (addContentToMediaStore(filePath)) {
                        Log.i("$TAG File [$filePath] has been successfully exported to MediaStore")
                        // TODO: show toast
                    } else {
                        Log.e("$TAG Failed to export file [$filePath] to MediaStore!")
                        // TODO: show toast
                    }
                }
            }
        } else {
            Log.e("$TAG Filepath wasn't initialized!")
        }
    }

    @UiThread
    private suspend fun addContentToMediaStore(
        path: String
    ): Boolean {
        if (path.isEmpty()) {
            Log.e("$TAG No file path to export to MediaStore!")
            return false
        }

        val isImage = FileUtils.isExtensionImage(path)
        val isVideo = FileUtils.isExtensionVideo(path)
        val isAudio = FileUtils.isExtensionAudio(path)

        val directory = when {
            isImage -> Environment.DIRECTORY_PICTURES
            isVideo -> Environment.DIRECTORY_MOVIES
            isAudio -> Environment.DIRECTORY_MUSIC
            else -> Environment.DIRECTORY_DOWNLOADS
        }

        val appName = AppUtils.getString(R.string.app_name)
        val relativePath = "$directory/$appName"
        val fileName = FileUtils.getNameFromFilePath(path)
        val extension = FileUtils.getExtensionFromFileName(fileName)
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)

        val context = coreContext.context
        val mediaStoreFilePath = when {
            isImage -> {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, mime)
                    put(MediaStore.Images.Media.RELATIVE_PATH, relativePath)
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val collection = Compatibility.getMediaCollectionUri(isImage = true)
                addContentValuesToCollection(
                    context,
                    path,
                    collection,
                    values,
                    MediaStore.Images.Media.IS_PENDING
                )
            }
            isVideo -> {
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.TITLE, fileName)
                    put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Video.Media.MIME_TYPE, mime)
                    put(MediaStore.Video.Media.RELATIVE_PATH, relativePath)
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
                val collection = Compatibility.getMediaCollectionUri(isVideo = true)
                addContentValuesToCollection(
                    context,
                    path,
                    collection,
                    values,
                    MediaStore.Video.Media.IS_PENDING
                )
            }
            isAudio -> {
                val values = ContentValues().apply {
                    put(MediaStore.Audio.Media.TITLE, fileName)
                    put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Audio.Media.MIME_TYPE, mime)
                    put(MediaStore.Audio.Media.RELATIVE_PATH, relativePath)
                    put(MediaStore.Audio.Media.IS_PENDING, 1)
                }
                val collection = Compatibility.getMediaCollectionUri(isAudio = true)
                addContentValuesToCollection(
                    context,
                    path,
                    collection,
                    values,
                    MediaStore.Audio.Media.IS_PENDING
                )
            }
            else -> ""
        }

        if (mediaStoreFilePath.isNotEmpty()) {
            Log.i("$TAG Exported file path to MediaStore is: $mediaStoreFilePath")
            return true
        }

        return false
    }

    @UiThread
    private suspend fun addContentValuesToCollection(
        context: Context,
        filePath: String,
        collection: Uri,
        values: ContentValues,
        pendingKey: String
    ): String {
        try {
            val fileUri = context.contentResolver.insert(collection, values)
            if (fileUri == null) {
                Log.e("$TAG Failed to get a URI to where store the file, aborting")
                return ""
            }

            context.contentResolver.openOutputStream(fileUri).use { out ->
                if (FileUtils.copyFileTo(filePath, out)) {
                    values.clear()
                    values.put(pendingKey, 0)
                    context.contentResolver.update(fileUri, values, null, null)

                    return fileUri.toString()
                }
            }
        } catch (e: Exception) {
            Log.e("$TAG Exception: $e")
        }
        return ""
    }
}
