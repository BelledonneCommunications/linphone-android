package org.linphone.ui.main.chat.model

import androidx.annotation.UiThread
import androidx.lifecycle.MutableLiveData
import org.linphone.utils.Event

class MessageDeleteDialogModel(val canBeRetracted: Boolean) {
    val dismissEvent = MutableLiveData<Event<Boolean>>()

    val cancelEvent = MutableLiveData<Event<Boolean>>()

    val deleteLocallyEvent = MutableLiveData<Event<Boolean>>()

    val deleteForEveryoneEvent = MutableLiveData<Event<Boolean>>()

    @UiThread
    fun dismiss() {
        dismissEvent.value = Event(true)
    }

    @UiThread
    fun cancel() {
        cancelEvent.value = Event(true)
    }

    @UiThread
    fun deleteLocally() {
        deleteLocallyEvent.value = Event(true)
    }

    @UiThread
    fun deleteForEveryone() {
        deleteForEveryoneEvent.value = Event(true)
    }
}
