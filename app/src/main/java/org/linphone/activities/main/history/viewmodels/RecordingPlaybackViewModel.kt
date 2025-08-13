package org.linphone.activities.main.history.viewmodels

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import org.linphone.models.callhistory.CallHistoryItemViewModel
import org.linphone.models.callhistory.CallRecordingInfo

class RecordingPlaybackViewModel : ViewModel() {
    // TODO: Implement the ViewModel

    val call = MutableLiveData<CallHistoryItemViewModel>()

    val recordings = MutableLiveData<List<CallRecordingInfo>>(emptyList())

    val currentRecording = MutableLiveData<RecordingInfoViewModel>()
}
