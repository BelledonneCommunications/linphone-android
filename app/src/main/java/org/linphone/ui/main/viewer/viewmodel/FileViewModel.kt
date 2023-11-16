package org.linphone.ui.main.viewer.viewmodel

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.widget.ImageView
import androidx.annotation.UiThread
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.linphone.core.tools.Log
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

    val pdfRendererReadyEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    // Below are required for PDF viewer
    private lateinit var pdfRenderer: PdfRenderer

    var screenWidth: Int = 0
    var screenHeight: Int = 0
    // End of PDF viewer required variables

    init {
        fullScreenMode.value = true
    }

    @UiThread
    fun loadFile(file: String) {
        val name = FileUtils.getNameFromFilePath(file)
        fileName.value = name

        val extension = FileUtils.getExtensionFromFileName(name)
        if (extension == "pdf") {
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
        } else {
            path.value = file
        }
    }

    override fun onCleared() {
        if (::pdfRenderer.isInitialized) {
            pdfRenderer.close()
        }
        super.onCleared()
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
}
