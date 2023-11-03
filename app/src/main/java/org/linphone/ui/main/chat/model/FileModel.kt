package org.linphone.ui.main.chat.model

import androidx.annotation.WorkerThread
import androidx.lifecycle.MutableLiveData

class FileModel @WorkerThread constructor(
    val file: String,
    private val onClicked: ((file: String) -> Unit)? = null
) {
    val path = MutableLiveData<String>()

    init {
        path.postValue(file)
    }

    fun onClick() {
        onClicked?.invoke(file)
    }
}
