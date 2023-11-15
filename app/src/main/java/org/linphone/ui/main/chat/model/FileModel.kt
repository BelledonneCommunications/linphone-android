package org.linphone.ui.main.chat.model

import android.webkit.MimeTypeMap
import androidx.annotation.AnyThread
import androidx.annotation.UiThread
import androidx.lifecycle.MutableLiveData
import org.linphone.core.tools.Log
import org.linphone.utils.FileUtils

class FileModel @AnyThread constructor(
    val file: String,
    fileSize: Long,
    val isWaitingToBeDownloaded: Boolean = false,
    private val onClicked: ((file: String) -> Unit)? = null
) {
    companion object {
        private const val TAG = "[File Model]"
    }

    val fileName: String = FileUtils.getNameFromFilePath(file)

    val formattedFileSize = MutableLiveData<String>()

    val path = MutableLiveData<String>()

    val mimeType: FileUtils.MimeType

    val isImage: Boolean

    val isVideoPreview: Boolean

    val isPdf: Boolean

    init {
        path.postValue(file)
        formattedFileSize.postValue(FileUtils.bytesToDisplayableSize(fileSize))

        if (!isWaitingToBeDownloaded) {
            val extension = FileUtils.getExtensionFromFileName(file)
            isPdf = extension == "pdf"

            val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            mimeType = FileUtils.getMimeType(mime)
            isImage = mimeType == FileUtils.MimeType.Image
            isVideoPreview = mimeType == FileUtils.MimeType.Video
        } else {
            mimeType = FileUtils.MimeType.Unknown
            isPdf = false
            isImage = false
            isVideoPreview = false
        }
    }

    @UiThread
    fun onClick() {
        onClicked?.invoke(file)
    }

    @AnyThread
    suspend fun deleteFile() {
        Log.i("$TAG Deleting file [$file]")
        FileUtils.deleteFile(file)
    }
}
