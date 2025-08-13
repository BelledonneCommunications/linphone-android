package org.linphone.activities.main.history.viewmodels

import org.linphone.models.callhistory.CallRecordingInfo

class RecordingInfoViewModel(val info: CallRecordingInfo) {

    val label: String

    init {
        val origin = if (info.callerName.isNullOrBlank()) info.callerNumber else info.callerName

        val destination = if (info.calleeName.isNullOrBlank()) info.calleeNumber else info.calleeName
        // info.calleeName?.ifEmpty { info.calleeNumber }

        label = "$origin ➔ $destination"
    }
}
