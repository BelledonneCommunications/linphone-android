package org.linphone.ui.main.calls.model

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

    val address = if (callLog.dir == Dir.Outgoing) callLog.toAddress else callLog.fromAddress

    val displayedAddress = address.asStringUriOnly()

    val avatarModel: ContactAvatarModel

    val isOutgoing = MutableLiveData<Boolean>()

    @IntegerRes
    val iconResId = MutableLiveData<Int>()

    val dateTime = MutableLiveData<String>()

    val friendRefKey: String?

    var friendExists: Boolean = false

    init {
        isOutgoing.postValue(callLog.dir == Dir.Outgoing)

        val timestamp = callLog.startDate
        val displayedDate = if (TimestampUtils.isToday(timestamp)) {
            TimestampUtils.timeToString(timestamp)
        } else if (TimestampUtils.isYesterday(timestamp)) {
            "Hier"
        } else {
            TimestampUtils.dateToString(timestamp)
        }
        dateTime.postValue(displayedDate)

        val friend = coreContext.core.findFriend(address)
        if (friend != null) {
            friendRefKey = friend.refKey
            avatarModel = ContactAvatarModel(friend)
            friendExists = true
        } else {
            val fakeFriend = coreContext.core.createFriend()
            fakeFriend.address = address
            friendRefKey = null
            avatarModel = ContactAvatarModel(fakeFriend)
            friendExists = false
        }

        iconResId.postValue(LinphoneUtils.getIconResId(callLog.status, callLog.dir))
    }

    @UiThread
    fun delete() {
        coreContext.postOnCoreThread { core ->
            core.removeCallLog(callLog)
        }
    }
}
