package org.linphone.ui.main.history.model

import androidx.annotation.IntegerRes
import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.lifecycle.MutableLiveData
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.core.Call.Dir
import org.linphone.core.CallLog
import org.linphone.ui.main.contacts.model.ContactAvatarModel
import org.linphone.utils.LinphoneUtils
import org.linphone.utils.TimestampUtils

class CallLogModel @WorkerThread constructor(private val callLog: CallLog) {
    val id = callLog.callId ?: callLog.refKey

    val timestamp = callLog.startDate

    val address = if (callLog.dir == Dir.Outgoing) callLog.toAddress else callLog.fromAddress

    val sipUri = address.asStringUriOnly()

    val displayedAddress: String

    val avatarModel: ContactAvatarModel

    @IntegerRes
    val iconResId = MutableLiveData<Int>()

    val dateTime = MutableLiveData<String>()

    val friendRefKey: String?

    var friendExists: Boolean = false

    init {
        val clone = address.clone()
        clone.clean()
        displayedAddress = clone.asStringUriOnly()

        val timestamp = timestamp
        val displayedDate = if (TimestampUtils.isToday(timestamp)) {
            TimestampUtils.timeToString(timestamp)
        } else if (TimestampUtils.isYesterday(timestamp)) {
            "Hier"
        } else {
            TimestampUtils.dateToString(timestamp)
        }
        dateTime.postValue(displayedDate)

        val friend = coreContext.contactsManager.findContactByAddress(address)
        avatarModel = coreContext.contactsManager.getContactAvatarModelForAddress(address)
        if (friend != null) {
            friendRefKey = friend.refKey
            friendExists = true
        } else {
            val fakeFriend = coreContext.core.createFriend()
            fakeFriend.address = address

            // Check if it is a conference
            val conferenceInfo = coreContext.core.findConferenceInformationFromUri(address)
            if (conferenceInfo != null) {
                avatarModel.name.postValue(conferenceInfo.subject)
                avatarModel.showConferenceIcon.postValue(true)
            }

            friendRefKey = null
            friendExists = false
        }

        iconResId.postValue(LinphoneUtils.getCallIconResId(callLog.status, callLog.dir))
    }

    @UiThread
    fun delete() {
        coreContext.postOnCoreThread { core ->
            core.removeCallLog(callLog)
        }
    }
}
