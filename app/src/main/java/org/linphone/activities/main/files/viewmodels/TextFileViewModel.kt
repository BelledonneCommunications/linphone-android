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

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import java.io.BufferedReader
import java.io.FileReader
import java.lang.StringBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.linphone.core.Content
import org.linphone.core.tools.Log

class TextFileViewModelFactory(private val content: Content) :
    ViewModelProvider.NewInstanceFactory() {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return TextFileViewModel(content) as T
    }
}

class TextFileViewModel(content: Content) : FileViewerViewModel(content) {
    val operationInProgress = MutableLiveData<Boolean>()

    val text = MutableLiveData<String>()

    init {
        operationInProgress.value = false

        openFile()
    }

    private fun openFile() {
        operationInProgress.value = true

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
                    operationInProgress.postValue(false)
                } catch (e: Exception) {
                    Log.e("[Text Viewer] Exception: $e")
                    operationInProgress.postValue(false)
                }
            }
        }
    }
}
