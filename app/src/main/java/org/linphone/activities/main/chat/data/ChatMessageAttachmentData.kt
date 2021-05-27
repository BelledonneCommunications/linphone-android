/*
 * Copyright (c) 2010-2020 Belledonne Communications SARL.
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
package org.linphone.activities.main.chat.data

import android.graphics.Bitmap
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.*
import org.linphone.utils.FileUtils
import org.linphone.utils.ImageUtils

class ChatMessageAttachmentData(
    val path: String,
    private val deleteCallback: (attachment: ChatMessageAttachmentData) -> Unit
) {
    val fileName: String = FileUtils.getNameFromFilePath(path)
    val isImage: Boolean = FileUtils.isExtensionImage(path)
    val isVideo: Boolean = FileUtils.isExtensionVideo(path)
    val isAudio: Boolean = FileUtils.isExtensionAudio(path)
    val isPdf: Boolean = FileUtils.isExtensionPdf(path)
    val videoPreview = MutableLiveData<Bitmap>()

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    init {
        if (isVideo) {
            scope.launch {
                withContext(Dispatchers.IO) {
                    videoPreview.postValue(ImageUtils.getVideoPreview(path))
                }
            }
        }
    }

    fun destroy() {
        scope.cancel()
    }

    fun delete() {
        deleteCallback(this)
    }
}
