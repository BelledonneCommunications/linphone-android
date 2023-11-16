package org.linphone.ui.main.history.model

import androidx.annotation.IntegerRes
import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.lifecycle.MutableLiveData
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.core.Call.Dir
import org.linphone.core.CallLog
import org.linphone.core.tools.Log
import org.linphone.ui.main.contacts.model.ContactAvatarModel
import org.linphone.utils.AppUtils
import org.linphone.utils.LinphoneUtils
import org.linphone.utils.TimestampUtils

class CallLogModel @WorkerThread constructor(private val callLog: CallLog) {
    companion object {
        private const val TAG = "[CallLog Model]"
    }

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
        val date = if (TimestampUtils.isToday(timestamp)) {
            AppUtils.getString(R.string.today)
        } else if (TimestampUtils.isYesterday(timestamp)) {
            AppUtils.getString(R.string.yesterday)
        } else {
            TimestampUtils.toString(timestamp, onlyDate = true, shortDate = true, hideYear = true)
        }
        val time = TimestampUtils.timeToString(timestamp)
        dateTime.postValue("$date | $time")

        if (callLog.wasConference()) {

            val conferenceInfo = coreContext.core.findConferenceInformationFromUri(address)
            if (conferenceInfo != null) {
                avatarModel = coreContext.contactsManager.getContactAvatarModelForConferenceInfo(
                    conferenceInfo
                )
            } else {
                val fakeFriend = coreContext.core.createFriend()
                fakeFriend.address = address
                fakeFriend.name = LinphoneUtils.getDisplayName(address)
                avatarModel = ContactAvatarModel(fakeFriend)
                avatarModel.forceConferenceIcon.postValue(true)
                Log.w(
                    "$TAG Call log was conference but failed to find matching conference info from it's URI!"
                )
            }

            friendRefKey = null
            friendExists = false
        } else {
            avatarModel = coreContext.contactsManager.getContactAvatarModelForAddress(address)
            val friend = avatarModel.friend
            friendRefKey = friend.refKey
            friendExists = !friendRefKey.isNullOrEmpty()
        }
        displayedAddress = avatarModel.friend.address?.asStringUriOnly() ?: address.asStringUriOnly()

        iconResId.postValue(LinphoneUtils.getCallIconResId(callLog.status, callLog.dir))
    }

    @UiThread
    fun delete() {
        coreContext.postOnCoreThread { core ->
            core.removeCallLog(callLog)
        }
    }
}
