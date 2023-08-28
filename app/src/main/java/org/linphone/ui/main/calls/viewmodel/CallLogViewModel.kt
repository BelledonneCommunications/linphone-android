package org.linphone.ui.main.calls.viewmodel

import androidx.annotation.UiThread
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.core.Address
import org.linphone.core.Call
import org.linphone.ui.main.calls.model.CallLogHistoryModel
import org.linphone.ui.main.calls.model.CallLogModel
import org.linphone.utils.Event

class CallLogViewModel @UiThread constructor() : ViewModel() {
    val showBackButton = MutableLiveData<Boolean>()

    val callLogModel = MutableLiveData<CallLogModel>()

    val historyCallLogs = MutableLiveData<ArrayList<CallLogHistoryModel>>()

    val historyDeletedEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    private lateinit var address: Address

    @UiThread
    fun findCallLogByCallId(callId: String) {
        coreContext.postOnCoreThread { core ->
            val callLog = core.findCallLogFromCallId(callId)
            if (callLog != null) {
                val model = CallLogModel(callLog)
                address = model.address
                callLogModel.postValue(model)

                val localAddress = if (callLog.dir == Call.Dir.Outgoing) callLog.fromAddress else callLog.toAddress
                val peerAddress = if (callLog.dir == Call.Dir.Outgoing) callLog.toAddress else callLog.fromAddress
                val history = arrayListOf<CallLogHistoryModel>()
                for (log in core.getCallHistory(peerAddress, localAddress)) {
                    val historyModel = CallLogHistoryModel(log)
                    history.add(historyModel)
                }
                historyCallLogs.postValue(history)
            }
        }
    }

    @UiThread
    fun deleteHistory() {
        coreContext.postOnCoreThread { core ->
            for (model in historyCallLogs.value.orEmpty()) {
                core.removeCallLog(model.callLog)
            }
            historyDeletedEvent.postValue(Event(true))
        }
    }

    @UiThread
    fun startAudioCall() {
        coreContext.postOnCoreThread { core ->
            val params = core.createCallParams(null)
            params?.isVideoEnabled = false
            coreContext.startCall(address, params)
        }
    }

    @UiThread
    fun startVideoCall() {
        coreContext.postOnCoreThread { core ->
            val params = core.createCallParams(null)
            params?.isVideoEnabled = true
            coreContext.startCall(address, params)
        }
    }

    @UiThread
    fun sendMessage() {
        // TODO
    }
}
