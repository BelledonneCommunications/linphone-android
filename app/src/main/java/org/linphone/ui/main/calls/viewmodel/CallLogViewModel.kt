package org.linphone.ui.main.calls.viewmodel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.ui.main.calls.model.CallLogModel
import org.linphone.utils.Event

class CallLogViewModel : ViewModel() {
    val callLogModel = MutableLiveData<CallLogModel>()

    val showBackButton = MutableLiveData<Boolean>()

    val callLogFoundEvent = MutableLiveData<Event<Boolean>>()

    fun findCallLogByCallId(callId: String) {
        // UI thread
        coreContext.postOnCoreThread { core ->
            val callLog = core.findCallLogFromCallId(callId)
            if (callLog != null) {
                callLogModel.postValue(CallLogModel(callLog))
                callLogFoundEvent.postValue(Event(true))
            }
        }
    }

    fun startAudioCall() {
        // TODO
    }

    fun startVideoCall() {
        // TODO
    }

    fun sendMessage() {
        // TODO
    }
}
