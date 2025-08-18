package org.linphone.activities.main.history.viewmodels

import androidx.lifecycle.MutableLiveData
import org.linphone.models.callhistory.CallRecordingInfo
import org.linphone.utils.TimestampUtils

class RecordingInfoViewModel(val info: CallRecordingInfo) {

    val label: String

    val formattedDuration: String

    val isPlaying: MutableLiveData<Boolean> = MutableLiveData(false)

    init {
        val origin = if (info.callerName.isNullOrBlank()) info.callerNumber else info.callerName
        val destination = if (info.calleeName.isNullOrBlank()) info.calleeNumber else info.calleeName

        label = "$origin ➔ $destination"

        formattedDuration = TimestampUtils.durationToFriendlyString(info.duration ?: 0)
    }
}
