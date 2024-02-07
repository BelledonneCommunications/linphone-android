package org.linphone.ui.main.file_media_viewer.viewmodel

import androidx.annotation.UiThread
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import org.linphone.core.tools.Log
import org.linphone.utils.Event
import org.linphone.utils.FileUtils

class MediaViewModel @UiThread constructor() : ViewModel() {
    companion object {
        private const val TAG = "[Media ViewModel]"
    }

    val path = MutableLiveData<String>()

    val fileName = MutableLiveData<String>()

    val fullScreenMode = MutableLiveData<Boolean>()

    val isImage = MutableLiveData<Boolean>()

    val isVideo = MutableLiveData<Boolean>()

    val isVideoPlaying = MutableLiveData<Boolean>()

    val toggleVideoPlayPauseEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    private lateinit var filePath: String

    @UiThread
    fun loadFile(file: String) {
        filePath = file
        val name = FileUtils.getNameFromFilePath(file)
        fileName.value = name

        val extension = FileUtils.getExtensionFromFileName(name)
        val mime = FileUtils.getMimeTypeFromExtension(extension)
        when (FileUtils.getMimeType(mime)) {
            FileUtils.MimeType.Image -> {
                Log.i("$TAG File [$file] seems to be an image")
                isImage.value = true
                path.value = file
            }
            FileUtils.MimeType.Video -> {
                Log.i("$TAG File [$file] seems to be a video")
                isVideo.value = true
                isVideoPlaying.value = false
            }
            else -> { }
        }
    }

    @UiThread
    fun toggleFullScreen() {
        fullScreenMode.value = fullScreenMode.value != true
    }

    @UiThread
    fun playPauseVideo() {
        val playVideo = isVideoPlaying.value == false
        isVideoPlaying.value = playVideo
        toggleVideoPlayPauseEvent.value = Event(playVideo)
    }
}
