/*
 * Copyright (c) 2010-2021 Belledonne Communications SARL.
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
package org.linphone.activities.main.files.viewmodels

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.core.tools.Log

class PdfFileViewModelFactory(private val filePath: String) :
    ViewModelProvider.NewInstanceFactory() {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
        return PdfFileViewModel(filePath) as T
    }
}

class PdfFileViewModel(filePath: String) : ViewModel() {
    val operationInProgress = MutableLiveData<Boolean>()

    val pages = MutableLiveData<ArrayList<PdfPageViewModel>>()

    private val pdfRenderer: PdfRenderer

    init {
        operationInProgress.value = false

        val input = ParcelFileDescriptor.open(File(filePath), ParcelFileDescriptor.MODE_READ_ONLY)
        pdfRenderer = PdfRenderer(input)
        Log.i("[PDF Viewer] ${pdfRenderer.pageCount} pages in file $filePath")

        loadPdf()
    }

    override fun onCleared() {
        for (page in pages.value.orEmpty()) {
            page.destroy()
        }
        pages.value?.clear()
        super.onCleared()
    }

    private fun loadPdf() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    operationInProgress.postValue(true)

                    for (index in 0 until pdfRenderer.pageCount) {
                        val page: PdfRenderer.Page = pdfRenderer.openPage(index)
                        val width = if (coreContext.screenWidth <= coreContext.screenHeight) coreContext.screenWidth else coreContext.screenHeight
                        val bm = Bitmap.createBitmap(
                            width.toInt(),
                            (width / page.width * page.height).toInt(),
                            Bitmap.Config.ARGB_8888
                        )
                        page.render(bm, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        page.close()

                        val pageViewModel = PdfPageViewModel(bm)
                        val list = arrayListOf<PdfPageViewModel>()
                        list.addAll(pages.value.orEmpty())
                        list.add(pageViewModel)
                        pages.postValue(list)
                    }

                    operationInProgress.postValue(false)
                } catch (e: Exception) {
                    Log.e("[PDF Viewer] Exception: $e")
                    operationInProgress.postValue(false)
                }
            }
        }
    }

    class PdfPageViewModel(val bitmap: Bitmap) {
        fun destroy() {
            bitmap.recycle()
        }
    }
}
