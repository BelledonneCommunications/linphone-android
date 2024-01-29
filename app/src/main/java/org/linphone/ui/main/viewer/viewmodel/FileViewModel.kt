package org.linphone.ui.main.viewer.viewmodel

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.widget.ImageView
import androidx.annotation.UiThread
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.lang.IllegalStateException
import java.lang.StringBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.linphone.R
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

    val pdfCurrentPage = MutableLiveData<String>()

    val pdfPages = MutableLiveData<String>()

    val isImage = MutableLiveData<Boolean>()

    val isAudio = MutableLiveData<Boolean>()

    val isVideo = MutableLiveData<Boolean>()

    val isVideoPlaying = MutableLiveData<Boolean>()

    val isText = MutableLiveData<Boolean>()

    val text = MutableLiveData<String>()

    val fileReadyEvent = MutableLiveData<Event<Boolean>>()

    val pdfRendererReadyEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val exportPdfEvent: MutableLiveData<Event<String>> by lazy {
        MutableLiveData<Event<String>>()
    }

    val toggleVideoPlayPauseEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val showGreenToastEvent: MutableLiveData<Event<Pair<String, Int>>> by lazy {
        MutableLiveData<Event<Pair<String, Int>>>()
    }

    val showRedToastEvent: MutableLiveData<Event<Pair<String, Int>>> by lazy {
        MutableLiveData<Event<Pair<String, Int>>>()
    }

    // Below are required for PDF viewer
    private lateinit var pdfRenderer: PdfRenderer

    private lateinit var filePath: String

    var screenWidth: Int = 0
    var screenHeight: Int = 0
    private var currentPdfPage: PdfRenderer.Page? = null
    // End of PDF viewer required variables

    override fun onCleared() {
        if (::pdfRenderer.isInitialized) {
            try {
                pdfRenderer.close()
            } catch (ise: IllegalStateException) {
                Log.e("$TAG Failed to close PDF renderer:  $ise")
            }
        }
        super.onCleared()
    }

    @UiThread
    fun loadFile(file: String) {
        fullScreenMode.value = true

        filePath = file
        val name = FileUtils.getNameFromFilePath(file)
        fileName.value = name

        val extension = FileUtils.getExtensionFromFileName(name)
        val mime = FileUtils.getMimeTypeFromExtension(extension)
        when (FileUtils.getMimeType(mime)) {
            FileUtils.MimeType.Pdf -> {
                Log.i("$TAG File [$file] seems to be a PDF")
                loadPdf()
            }
            FileUtils.MimeType.Image -> {
                Log.i("$TAG File [$file] seems to be an image")
                isImage.value = true
                path.value = file
                fileReadyEvent.value = Event(true)
            }
            FileUtils.MimeType.Video -> {
                Log.i("$TAG File [$file] seems to be a video")
                isVideo.value = true
                isVideoPlaying.value = false
                fileReadyEvent.value = Event(true)
            }
            FileUtils.MimeType.Audio -> {
                Log.i("$TAG File [$file] seems to be an audio")
                // TODO: handle audio files
                isAudio.value = true
                fileReadyEvent.value = Event(true)
            }
            FileUtils.MimeType.PlainText -> {
                Log.i("$TAG File [$file] seems to be plain text")
                loadPlainText()
            }
            else -> {
                fileReadyEvent.value = Event(false)
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
                    try {
                        currentPdfPage?.close()
                        currentPdfPage = null
                    } catch (_: IllegalStateException) {}

                    val page: PdfRenderer.Page = pdfRenderer.openPage(index)
                    currentPdfPage = page

                    Log.i(
                        "$TAG Page size is ${page.width}/${page.height}, screen size is $screenWidth/$screenHeight"
                    )
                    val bm = Bitmap.createBitmap(
                        page.width,
                        page.height,
                        Bitmap.Config.ARGB_8888
                    )
                    page.render(bm, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()

                    withContext(Dispatchers.Main) {
                        view.setImageBitmap(bm)
                    }
                } catch (e: Exception) {
                    Log.e("$TAG Exception: $e")
                    try {
                        currentPdfPage?.close()
                        currentPdfPage = null
                    } catch (_: IllegalStateException) {}
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
            if (isPdf.value == true) {
                Log.i("$TAG Exporting PDF as document")
                exportPdfEvent.postValue(Event(fileName.value.orEmpty()))
            } else {
                viewModelScope.launch {
                    withContext(Dispatchers.IO) {
                        Log.i("$TAG Export file [$filePath] to Android's MediaStore")
                        val mediaStorePath = FileUtils.addContentToMediaStore(filePath)
                        if (mediaStorePath.isNotEmpty()) {
                            Log.i(
                                "$TAG File [$filePath] has been successfully exported to MediaStore"
                            )
                            val message = AppUtils.getString(
                                R.string.toast_file_successfully_exported_to_media_store
                            )
                            showGreenToastEvent.postValue(Event(Pair(message, R.drawable.check)))
                        } else {
                            Log.e("$TAG Failed to export file [$filePath] to MediaStore!")
                            val message = AppUtils.getString(
                                R.string.toast_export_file_to_media_store_error
                            )
                            showRedToastEvent.postValue(Event(Pair(message, R.drawable.x)))
                        }
                    }
                }
            }
        } else {
            Log.e("$TAG Filepath wasn't initialized!")
        }
    }

    @UiThread
    fun copyPdfToUri(dest: Uri) {
        val source = Uri.parse(FileUtils.getProperFilePath(filePath))
        Log.i("$TAG Copying file URI [$source] to [$dest]")
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val result = FileUtils.copyFile(source, dest)
                if (result) {
                    Log.i(
                        "$TAG File [$filePath] has been successfully exported to documents"
                    )
                    val message = AppUtils.getString(
                        R.string.toast_file_successfully_exported_to_documents
                    )
                    showGreenToastEvent.postValue(Event(Pair(message, R.drawable.check)))
                } else {
                    Log.e("$TAG Failed to export file [$filePath] to documents!")
                    val message = AppUtils.getString(
                        R.string.toast_export_file_to_documents_error
                    )
                    showRedToastEvent.postValue(Event(Pair(message, R.drawable.x)))
                }
            }
        }
    }

    private fun loadPdf() {
        isPdf.value = true

        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val input = ParcelFileDescriptor.open(
                    File(filePath),
                    ParcelFileDescriptor.MODE_READ_ONLY
                )
                pdfRenderer = PdfRenderer(input)
                val count = pdfRenderer.pageCount
                Log.i("$TAG $count pages in file $filePath")
                pdfPages.postValue(count.toString())
                pdfCurrentPage.postValue("1")
                pdfRendererReadyEvent.postValue(Event(true))
                fileReadyEvent.postValue(Event(true))
            }
        }
    }

    private fun loadPlainText() {
        isText.value = true

        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val br = BufferedReader(FileReader(filePath))
                    var line: String?
                    val textBuilder = StringBuilder()
                    while (br.readLine().also { line = it } != null) {
                        textBuilder.append(line)
                        textBuilder.append('\n')
                    }
                    br.close()
                    text.postValue(textBuilder.toString())
                    Log.i("$TAG Finished reading file [$filePath]")
                    fileReadyEvent.postValue(Event(true))
                    // TODO FIXME : improve performances !
                } catch (e: Exception) {
                    Log.e("$TAG Exception trying to read file [$filePath] as text: $e")
                }
            }
        }
    }
}
