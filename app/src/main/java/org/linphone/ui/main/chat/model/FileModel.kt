package org.linphone.ui.main.chat.model

import android.media.MediaMetadataRetriever
import android.media.MediaMetadataRetriever.METADATA_KEY_DURATION
import android.net.Uri
import androidx.annotation.AnyThread
import androidx.annotation.UiThread
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.core.tools.Log
import org.linphone.utils.FileUtils
import org.linphone.utils.TimestampUtils

class FileModel @AnyThread constructor(
    val file: String,
    val fileName: String,
    fileSize: Long,
    private val isEncrypted: Boolean,
    val isWaitingToBeDownloaded: Boolean = false,
    private val onClicked: ((model: FileModel) -> Unit)? = null
) {
    companion object {
        private const val TAG = "[File Model]"
    }

    val formattedFileSize = MutableLiveData<String>()

    val downloadProgress = MutableLiveData<Int>()

    val mimeType: FileUtils.MimeType

    val isMedia: Boolean

    val isImage: Boolean

    val isVideoPreview: Boolean

    val videoDuration = MutableLiveData<String>()

    val isPdf: Boolean

    val isAudio: Boolean

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        downloadProgress.postValue(-1)
        formattedFileSize.postValue(FileUtils.bytesToDisplayableSize(fileSize))

        if (!isWaitingToBeDownloaded) {
            val extension = FileUtils.getExtensionFromFileName(file)
            isPdf = extension == "pdf"

            val mime = FileUtils.getMimeTypeFromExtension(extension)
            mimeType = FileUtils.getMimeType(mime)
            isImage = mimeType == FileUtils.MimeType.Image
            isVideoPreview = mimeType == FileUtils.MimeType.Video
            if (isVideoPreview) {
                getDuration()
            }
            isAudio = mimeType == FileUtils.MimeType.Audio
            Log.d(
                "$TAG File has already been downloaded, extension is [$extension], MIME is [$mime]"
            )
        } else {
            mimeType = FileUtils.MimeType.Unknown
            isPdf = false
            isImage = false
            isVideoPreview = false
            isAudio = false
        }

        isMedia = isVideoPreview || isImage
    }

    @AnyThread
    fun destroy() {
        if (isEncrypted) {
            Log.i("$TAG [VFS] Deleting plain file in cache: $file")
            scope.launch {
                FileUtils.deleteFile(file)
            }
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

    private fun getDuration() {
        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(coreContext.context, Uri.parse(file))
            val durationInMs = retriever.extractMetadata(METADATA_KEY_DURATION)?.toInt() ?: 0
            val seconds = durationInMs / 1000
            val duration = TimestampUtils.durationToString(seconds)
            Log.d("$TAG Duration for file [$file] is $duration")
            videoDuration.postValue(duration)
            retriever.release()
        } catch (e: Exception) {
            Log.e("$TAG Failed to get duration for file [$file]: $e")
        }
    }
}
