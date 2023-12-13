package org.linphone.ui.main.chat.model

import androidx.annotation.AnyThread
import androidx.annotation.UiThread
import androidx.lifecycle.MutableLiveData
import org.linphone.core.tools.Log
import org.linphone.utils.FileUtils

class FileModel @AnyThread constructor(
    val file: String,
    val fileName: String,
    fileSize: Long,
    val isWaitingToBeDownloaded: Boolean = false,
    private val onClicked: ((model: FileModel) -> Unit)? = null
) {
    companion object {
        private const val TAG = "[File Model]"
    }

    val formattedFileSize = MutableLiveData<String>()

    val path = MutableLiveData<String>()

    val downloadProgress = MutableLiveData<Int>()

    val mimeType: FileUtils.MimeType

    val isImage: Boolean

    val isVideoPreview: Boolean

    val isPdf: Boolean

    init {
        path.postValue(file)
        downloadProgress.postValue(-1)
        formattedFileSize.postValue(FileUtils.bytesToDisplayableSize(fileSize))

        if (!isWaitingToBeDownloaded) {
            val extension = FileUtils.getExtensionFromFileName(file)
            isPdf = extension == "pdf"

            val mime = FileUtils.getMimeTypeFromExtension(extension)
            mimeType = FileUtils.getMimeType(mime)
            isImage = mimeType == FileUtils.MimeType.Image
            isVideoPreview = mimeType == FileUtils.MimeType.Video
            Log.d(
                "$TAG File has already been downloaded, extension is [$extension], MIME is [$mime]"
            )
        } else {
            mimeType = FileUtils.MimeType.Unknown
            isPdf = false
            isImage = false
            isVideoPreview = false
        }
    }

    @UiThread
    fun onClick() {
        onClicked?.invoke(this)
    }

    @AnyThread
    suspend fun deleteFile() {
        Log.i("$TAG Deleting file [$file]")
        FileUtils.deleteFile(file)
    }
}
