package org.linphone.activities.main.history.viewmodels

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import org.linphone.models.callhistory.CallHistoryItemViewModel

class RecordingPlaybackViewModel : ViewModel() {
    // TODO: Implement the ViewModel

    val call = MutableLiveData<CallHistoryItemViewModel>()
}
