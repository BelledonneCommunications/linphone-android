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
import android.widget.ImageView
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import java.io.File
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.core.Content
import org.linphone.core.tools.Log

class PdfFileViewModelFactory(private val content: Content) :
    ViewModelProvider.NewInstanceFactory() {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return PdfFileViewModel(content) as T
    }
}

class PdfFileViewModel(content: Content) : FileViewerViewModel(content) {
    val operationInProgress = MutableLiveData<Boolean>()

    private val pdfRenderer: PdfRenderer

    init {
        operationInProgress.value = false

        val input = ParcelFileDescriptor.open(File(filePath), ParcelFileDescriptor.MODE_READ_ONLY)
        pdfRenderer = PdfRenderer(input)
        Log.i("[PDF Viewer] ${pdfRenderer.pageCount} pages in file $filePath")
    }

    override fun onCleared() {
        pdfRenderer.close()
        super.onCleared()
    }

    fun getPagesCount(): Int {
        return pdfRenderer.pageCount
    }

    fun loadPdfPageInto(index: Int, view: ImageView) {
        try {
            operationInProgress.value = true

            val page: PdfRenderer.Page = pdfRenderer.openPage(index)
            val width = if (coreContext.screenWidth <= coreContext.screenHeight) coreContext.screenWidth else coreContext.screenHeight
            val bm = Bitmap.createBitmap(
                width.toInt(),
                (width / page.width * page.height).toInt(),
                Bitmap.Config.ARGB_8888
            )
            page.render(bm, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()

            view.setImageBitmap(bm)

            operationInProgress.value = false
        } catch (e: Exception) {
            Log.e("[PDF Viewer] Exception: $e")
            operationInProgress.value = false
        }
    }
}
