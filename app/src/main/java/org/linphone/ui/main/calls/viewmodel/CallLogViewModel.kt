package org.linphone.ui.main.calls.viewmodel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.core.Address
import org.linphone.core.Call
import org.linphone.ui.main.calls.model.CallLogHistoryModel
import org.linphone.ui.main.calls.model.CallLogModel
import org.linphone.utils.Event

class CallLogViewModel : ViewModel() {
    val showBackButton = MutableLiveData<Boolean>()

    val callLogModel = MutableLiveData<CallLogModel>()

    val historyCallLogs = MutableLiveData<ArrayList<CallLogHistoryModel>>()

    val callLogFoundEvent = MutableLiveData<Event<Boolean>>()

    private lateinit var address: Address

    fun findCallLogByCallId(callId: String) {
        // UI thread
        coreContext.postOnCoreThread { core ->
            val callLog = core.findCallLogFromCallId(callId)
            if (callLog != null) {
                val model = CallLogModel(callLog)
                callLogModel.postValue(model)

                val localAddress = if (callLog.dir == Call.Dir.Outgoing) callLog.fromAddress else callLog.toAddress
                val peerAddress = if (callLog.dir == Call.Dir.Outgoing) callLog.toAddress else callLog.fromAddress
                val history = arrayListOf<CallLogHistoryModel>()
                for (log in core.getCallHistory(peerAddress, localAddress)) {
                    val historyModel = CallLogHistoryModel(log)
                    history.add(historyModel)
                }
                historyCallLogs.postValue(history)

                address = model.address
                callLogFoundEvent.postValue(Event(true))
            }
        }
    }

    fun startAudioCall() {
        coreContext.postOnCoreThread { core ->
            val params = core.createCallParams(null)
            params?.isVideoEnabled = false
            coreContext.startCall(address, params)
        }
    }

    fun startVideoCall() {
        coreContext.postOnCoreThread { core ->
            val params = core.createCallParams(null)
            params?.isVideoEnabled = true
            coreContext.startCall(address, params)
        }
    }

    fun sendMessage() {
        // TODO
    }
}
