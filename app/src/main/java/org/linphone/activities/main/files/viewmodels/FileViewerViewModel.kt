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
import org.linphone.core.Content
import org.linphone.core.tools.Log
import org.linphone.utils.FileUtils

open class FileViewerViewModel(val content: Content) : ViewModel() {
    val filePath: String
    private val deleteAfterUse: Boolean = content.isFileEncrypted

    val fullScreenMode = MutableLiveData<Boolean>()

    init {
        filePath = if (deleteAfterUse) {
            Log.i("[File Viewer] Content is encrypted, requesting plain file path")
            content.exportPlainFile()
        } else {
            content.filePath.orEmpty()
        }
    }

    override fun onCleared() {
        if (deleteAfterUse) {
            Log.i("[File Viewer] Deleting temporary plain file: $filePath")
            FileUtils.deleteFile(filePath)
        }

        super.onCleared()
    }

    fun toggleFullScreen() {
        fullScreenMode.value = fullScreenMode.value != true
    }
}
