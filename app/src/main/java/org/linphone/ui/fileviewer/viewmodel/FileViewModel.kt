/*
 * Copyright (c) 2010-2023 Belledonne Communications SARL.
 *
 * This file is part of linphone-android
 * (see https://www.linphone.org).
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.linphone.ui.fileviewer.viewmodel

import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.widget.ImageView
import androidx.annotation.UiThread
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.lang.IllegalStateException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.linphone.R
import org.linphone.core.tools.Log
import org.linphone.ui.GenericViewModel
import org.linphone.utils.Event
import org.linphone.utils.FileUtils
import org.linphone.utils.TimestampUtils
import androidx.core.net.toUri
import androidx.core.graphics.createBitmap

class FileViewModel
    @UiThread
    constructor() : GenericViewModel() {
    companion object {
        private const val TAG = "[File ViewModel]"
    }

    val fileName = MutableLiveData<String>()

    val mimeType = MutableLiveData<String>()

    val fullScreenMode = MutableLiveData<Boolean>()

    val isPdf = MutableLiveData<Boolean>()

    val pdfCurrentPage = MutableLiveData<String>()

    val pdfPages = MutableLiveData<String>()

    val isText = MutableLiveData<Boolean>()

    val text = MutableLiveData<String>()

    val fileReadyEvent = MutableLiveData<Event<Boolean>>()

    val dateTime = MutableLiveData<String>()

    val isFromEphemeralMessage = MutableLiveData<Boolean>()

    val exportPlainTextFileEvent: MutableLiveData<Event<String>> by lazy {
        MutableLiveData<Event<String>>()
    }

    val pdfRendererReadyEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val exportPdfEvent: MutableLiveData<Event<String>> by lazy {
        MutableLiveData<Event<String>>()
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
    fun loadFile(file: String, timestamp: Long, content: String? = null) {
        fullScreenMode.value = true

        val name = FileUtils.getNameFromFilePath(file)
        fileName.value = name

        dateTime.value = TimestampUtils.toString(
            timestamp,
            shortDate = false,
            hideYear = false
        )

        if (!content.isNullOrEmpty()) {
            isText.value = true
            text.postValue(content!!)
            mimeType.postValue("text/plain")
            Log.i("$TAG Using pre-loaded content as PlainText")
            fileReadyEvent.postValue(Event(true))
            return
        }

        filePath = file
        val extension = FileUtils.getExtensionFromFileName(file)
        val mime = FileUtils.getMimeTypeFromExtension(extension)
        mimeType.postValue(mime)
        val mimeType = FileUtils.getMimeType(mime)
        when (mimeType) {
            FileUtils.MimeType.Pdf -> {
                Log.d("$TAG File [$file] seems to be a PDF")
                loadPdf()
            }
            FileUtils.MimeType.PlainText -> {
                Log.d("$TAG File [$file] seems to be plain text")
                loadPlainText()
            }
            FileUtils.MimeType.Unknown -> {
                Log.w("$TAG Unknown MIME type for file at [$file], opening it as plain text")
                loadPlainText()
            }
            else -> {
                Log.e("$TAG Unexpected MIME type [$mimeType] for file at [$file] with extension [$extension]")
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

                    Log.d(
                        "$TAG Page size is ${page.width}/${page.height}, screen size is $screenWidth/$screenHeight"
                    )
                    val bm = createBitmap(page.width, page.height)
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
    fun getFilePath(): String {
        if (::filePath.isInitialized) {
            return filePath
        }

        Log.i("$TAG File path wasn't initialized, storing memory content as file")
        val name = fileName.value.orEmpty()
        val file = FileUtils.getFileStorageCacheDir(
            fileName = name,
            overrideExisting = true
        )
        savePlainTextFileToUri(file)
        filePath = file.absolutePath
        return filePath
    }

    @UiThread
    fun exportToMediaStore() {
        if (isPdf.value == true) {
            Log.i("$TAG Exporting PDF as document")
            exportPdfEvent.postValue(Event(fileName.value.orEmpty()))
        } else {
            Log.i("$TAG Exporting plain text content as document")
            exportPlainTextFileEvent.postValue(Event(fileName.value.orEmpty()))
        }
    }

    @UiThread
    fun copyFileToUri(dest: Uri) {
        val source = FileUtils.getProperFilePath(getFilePath()).toUri()
        Log.i("$TAG Copying file URI [$source] to [$dest]")
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val result = FileUtils.copyFile(source, dest)
                if (result) {
                    Log.i(
                        "$TAG File [$filePath] has been successfully exported to documents"
                    )
                    showGreenToast(R.string.file_successfully_exported_to_documents_toast, R.drawable.check)
                } else {
                    Log.e("$TAG Failed to export file [$filePath] to documents!")
                    showRedToast(R.string.export_file_to_documents_error_toast, R.drawable.warning_circle)
                }
            }
        }
    }

    @UiThread
    private fun savePlainTextFileToUri(dest: File) {
        Log.i("$TAG Saving text to file  [${dest.absolutePath}]")
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val result = FileUtils.dumpStringToFile(text.value.orEmpty(), dest)
                if (result) {
                    Log.i(
                        "$TAG Text has been successfully exported to documents"
                    )
                    showGreenToast(
                                R.string.file_successfully_exported_to_documents_toast,
                                R.drawable.check
                            )
                } else {
                    Log.e("$TAG Failed to save text to documents!")
                    showRedToast(R.string.export_file_to_documents_error_toast, R.drawable.warning_circle)
                }
            }
        }
    }

    @UiThread
    private fun loadPdf() {
        isPdf.value = true

        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val input = ParcelFileDescriptor.open(
                    File(filePath),
                    ParcelFileDescriptor.MODE_READ_ONLY
                )
                try {
                    pdfRenderer = PdfRenderer(input)
                    val count = pdfRenderer.pageCount
                    Log.i("$TAG $count pages in file $filePath")
                    pdfPages.postValue(count.toString())
                    pdfCurrentPage.postValue("1")
                    pdfRendererReadyEvent.postValue(Event(true))
                    fileReadyEvent.postValue(Event(true))
                } catch (se: SecurityException) {
                    // TODO FIXME: add support for password protected PDFs
                    Log.e("$TAG Can't open PDF, probably protected by a password: $se")
                    pdfCurrentPage.postValue("0")
                    pdfPages.postValue("0")
                    showRedToast(R.string.conversation_pdf_password_protected_file_cant_be_opened_error_toast, R.drawable.warning_circle)
                } catch (e: Exception) {
                    Log.e("$TAG Can't open PDF, it may be corrupted: $e")
                    pdfCurrentPage.postValue("0")
                    pdfPages.postValue("0")
                    showRedToast(R.string.conversation_pdf_file_error_toast, R.drawable.warning_circle)
                }
            }
        }
    }

    @UiThread
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
                    showRedToast(R.string.conversation_file_cant_be_opened_error_toast, R.drawable.warning_circle)
                }
            }
        }
    }
}
