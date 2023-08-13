package org.linphone.ui.main.calls.model

import androidx.lifecycle.MutableLiveData
import org.linphone.core.Call.Dir
import org.linphone.core.CallLog
import org.linphone.utils.TimestampUtils

class CallLogHistoryModel(callLog: CallLog) {
    val isOutgoing = MutableLiveData<Boolean>()

    val dateTime = MutableLiveData<String>()

    val duration = MutableLiveData<String>()

    init {
        // Core thread
        isOutgoing.postValue(callLog.dir == Dir.Outgoing)

        val startDate = callLog.startDate
        val date = if (TimestampUtils.isToday(startDate)) {
            "Aujourd'hui"
        } else if (TimestampUtils.isYesterday(startDate)) {
            "Hier"
        } else {
            TimestampUtils.dateToString(callLog.startDate)
        }
        val time = TimestampUtils.timeToString(startDate)
        dateTime.postValue("$date | $time")

        duration.postValue(
            TimestampUtils.durationToString(callLog.duration)
        )
    }
}
