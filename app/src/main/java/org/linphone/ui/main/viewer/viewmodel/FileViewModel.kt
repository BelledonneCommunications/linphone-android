package org.linphone.ui.main.viewer.viewmodel

import androidx.annotation.UiThread
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class FileViewModel @UiThread constructor() : ViewModel() {
    val path = MutableLiveData<String>()

    val fullScreenMode = MutableLiveData<Boolean>()

    init {
        fullScreenMode.value = true
    }

    fun loadFile(file: String) {
        path.postValue(file)
    }

    fun toggleFullScreen() {
        fullScreenMode.value = fullScreenMode.value != true
    }
}
