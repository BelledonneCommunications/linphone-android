package org.linphone.ui.main.history.model

import androidx.annotation.IntegerRes
import androidx.annotation.WorkerThread
import androidx.lifecycle.MutableLiveData
import org.linphone.R
import org.linphone.core.Call
import org.linphone.core.Call.Dir
import org.linphone.core.CallLog
import org.linphone.utils.AppUtils
import org.linphone.utils.LinphoneUtils
import org.linphone.utils.TimestampUtils

class CallLogHistoryModel @WorkerThread constructor(val callLog: CallLog) {
    val id = callLog.callId ?: callLog.refKey

    val isOutgoing = MutableLiveData<Boolean>()

    val isSuccessful = MutableLiveData<Boolean>()

    val dateTime = MutableLiveData<String>()

    val duration = MutableLiveData<String>()

    @IntegerRes
    val iconResId = MutableLiveData<Int>()

    init {
        isOutgoing.postValue(callLog.dir == Dir.Outgoing)

        val startDate = callLog.startDate
        val date = if (TimestampUtils.isToday(startDate)) {
            AppUtils.getString(R.string.today)
        } else if (TimestampUtils.isYesterday(startDate)) {
            AppUtils.getString(R.string.yesterday)
        } else {
            TimestampUtils.toString(startDate, onlyDate = true, shortDate = false, hideYear = true)
        }
        val time = TimestampUtils.timeToString(startDate)
        dateTime.postValue("$date | $time")

        duration.postValue(
            TimestampUtils.durationToString(callLog.duration)
        )

        isSuccessful.postValue(callLog.status == Call.Status.Success)
        iconResId.postValue(LinphoneUtils.getCallIconResId(callLog.status, callLog.dir))
    }
}
