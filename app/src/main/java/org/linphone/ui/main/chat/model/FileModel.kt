package org.linphone.ui.main.chat.model

import androidx.annotation.AnyThread
import androidx.annotation.UiThread
import androidx.lifecycle.MutableLiveData
import org.linphone.core.tools.Log
import org.linphone.utils.FileUtils

class FileModel @AnyThread constructor(
    val file: String,
    private val onClicked: ((file: String) -> Unit)? = null
) {
    companion object {
        private const val TAG = "[File Model]"
    }

    val path = MutableLiveData<String>()

    init {
        path.postValue(file)
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
