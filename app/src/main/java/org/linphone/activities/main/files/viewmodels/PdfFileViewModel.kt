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

class PdfFileViewModelFactory(private val filePath: String) :
    ViewModelProvider.NewInstanceFactory() {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
        return PdfFileViewModel(filePath) as T
    }
}

class PdfFileViewModel(filePath: String) : ViewModel() {
    val operationInProgress = MutableLiveData<Boolean>()

    val bitmap = MutableLiveData<Bitmap>()

    private val pdfRenderer: PdfRenderer

    init {
        operationInProgress.value = false

        val input = ParcelFileDescriptor.open(File(filePath), ParcelFileDescriptor.MODE_READ_ONLY)
        pdfRenderer = PdfRenderer(input)

        loadPdf()
    }

    private fun loadPdf() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    operationInProgress.postValue(true)

                    val page: PdfRenderer.Page = pdfRenderer.openPage(0)
                    val screenWidth = coreContext.screenWidth
                    val bm = Bitmap.createBitmap(
                        screenWidth.toInt(), (screenWidth / page.width * page.height).toInt(), Bitmap.Config.ARGB_8888
                    )
                    page.render(bm, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()

                    bitmap.postValue(bm)
                    operationInProgress.postValue(false)
                } catch (e: Exception) {
                    operationInProgress.postValue(false)
                }
            }
        }
    }
}
