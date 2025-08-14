package org.linphone.models.callhistory

import java.util.Locale
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.core.Address
import org.linphone.core.Call
import org.linphone.core.CallLog
import org.linphone.core.ChatRoom
import org.linphone.core.ConferenceInfo
import org.linphone.core.ErrorInfo
import org.linphone.services.PhoneFormatterService
import org.linphone.utils.DateUtils
import org.linphone.utils.LinphoneUtils
import org.threeten.bp.LocalDateTime
import org.threeten.bp.ZoneId
import org.threeten.bp.format.DateTimeFormatter
import org.threeten.bp.format.FormatStyle

class CallHistoryItemViewModel(
    val call: CallHistoryItem,
    localDateTime: LocalDateTime
) : CallLog {
    val date: String = DateUtils.formatFriendlyDate(call.startTime, localDateTime)

    private val formatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(
        Locale.getDefault()
    )
    private val zonedDateTime = call.startTime.withZoneSameInstant(ZoneId.systemDefault())
    val time: String = zonedDateTime.format(formatter)

    var contactName: String = ""
    private var contactLabel: String = buildContactMatchLabel()
    var number: String = getOtherPartyNumber()
    var name: String = getOtherPartyName()
    var formattedNumber: String = retrieveFormattedNumber(number)
    var fields: List<String> = buildFields()
    var icon: String = buildIcon()
    var isSelected: Boolean = false

    var canCallBack = number.isNotEmpty()

//    val canCall: Observable<Boolean> = Observable.defer {
//        if (call.pbxType == PbxType.Teams) {
//            Observable.just(!call.isConference)
//        } else {
//            Observable.just(coreContext.core.callsNb == 0)
//        }
//    }

    private fun retrieveFormattedNumber(number: String): String {
        return if (call.isConference && call.pbxType == PbxType.Teams) {
            coreContext.context.getString(R.string.microsoft_teams)
        } else {
            if (number.isNotEmpty()) {
                try {
                    PhoneFormatterService.getInstance(coreContext.context).formatPhoneNumber(number)
                } catch (e: Exception) {
                    number
                }
            } else {
                coreContext.context.getString(R.string.unknown_formatted_number)
            }
        }
    }

    private fun buildFields(): List<String> {
        var name = call.contactName
        if (name.isNullOrBlank()) {
            name = if (CallDirections.fromValue(call.callDirection) == CallDirections.Incoming) {
                call.callingUserName
            } else {
                call.calledUserName
            }
        }

        val route = call.routePathName ?: call.groupName ?: call.huntgroupName ?: ""

        val fields = mutableListOf<String>()

        if (name != null) {
            fields.add(name)
            contactName = name
        }

        if (formattedNumber.isNotEmpty() && formattedNumber != name) fields.add(formattedNumber)
        if (!route.contains(number)) fields.add("via $route")

        return fields
    }

    private fun buildIcon(): String {
        return when {
            call.isConference -> CallHistoryIcons.Conference.iconValue
            CallDirections.fromValue(call.callDirection) == CallDirections.Incoming -> {
                when {
                    call.missedCall -> CallHistoryIcons.CallMissed.iconValue
                    !call.answered -> CallHistoryIcons.CallNotAnswered.iconValue
                    else -> CallHistoryIcons.CallInbound.iconValue
                }
            }
            else -> CallHistoryIcons.CallOutbound.iconValue
        }
    }

    private fun buildContactMatchLabel(): String {
        if (call.contactMatchType == null) return ""
        return when (call.contactMatchType) {
            "ClioContactMatch" -> "Open Clio record"
            "Dynamics365ContactMatch" -> "Open Dynamics record"
            "FreshdeskContactMatch" -> "Open Freshdesk contact"
            "SalesforceLightningContactMatch" -> "Open Salesforce record"
            "ZendeskContactMatch" -> "Open Zendesk record"
            "ZohoContactMatch" -> "Open Zoho record"
            else -> ""
        }
    }

    /* CallLog Interface */

    private var userData: Any? = null

    override fun getCallId(): String? {
        return call.documentId
    }

    override fun getChatRoom(): ChatRoom? {
        TODO("Not yet implemented")
    }

    override fun getConferenceInfo(): ConferenceInfo? {
        return null
    }

    override fun getDir(): Call.Dir {
        return when (CallDirections.fromValue(call.callDirection)) {
            CallDirections.Incoming -> Call.Dir.Incoming
            CallDirections.Outgoing -> Call.Dir.Outgoing
            else -> Call.Dir.Incoming // TODO: this may not be correct
        }
    }

    override fun getDuration(): Int {
        return 0 // Note: We don't currently have this
    }

    override fun getErrorInfo(): ErrorInfo? {
        return null
    }

    private fun getOtherPartyNumber(): String {
        return if (CallDirections.fromValue(call.callDirection) == CallDirections.Outgoing) {
            call.calledUserNumber ?: ""
        } else {
            call.callingUserNumber ?: ""
        }
    }

    private fun getOtherPartyName(): String {
        return if (CallDirections.fromValue(call.callDirection) == CallDirections.Outgoing) {
            call.calledUserName ?: ""
        } else {
            call.callingUserName ?: ""
        }
    }

    override fun getFromAddress(): Address {
        return if (CallDirections.fromValue(call.callDirection) == CallDirections.Outgoing) {
            getAddress(call.callingUserNumber)!!
        } else {
            getAddress(call.calledUserNumber)!!
        }
    }

    override fun getLocalAddress(): Address {
        return if (CallDirections.fromValue(call.callDirection) == CallDirections.Outgoing) {
            getAddress(call.callingUserNumber)!!
        } else {
            getAddress(call.calledUserNumber)!!
        }
    }

    override fun getQuality(): Float {
        return 0.0F
    }

    override fun getRefKey(): String? {
        return callId
    }

    override fun setRefKey(refkey: String?) {
        throw UnsupportedOperationException("RefKey is readonly")
    }

    override fun getRemoteAddress(): Address {
        return if (CallDirections.fromValue(call.callDirection) == CallDirections.Outgoing) {
            getAddress(call.calledUserNumber)!!
        } else {
            getAddress(call.callingUserNumber)!!
        }
    }

    override fun setRemoteAddress(address: Address) {
        throw UnsupportedOperationException("RemoteAddress is readonly")
    }

    override fun getStartDate(): Long {
        return call.startTime.toInstant().toEpochMilli()
    }

    override fun getStatus(): Call.Status {
        if (call.missedCall) return Call.Status.Missed
        return Call.Status.Success

        /* Which of these can we model?
            case 0: return Success;
            case 1: return Aborted;
            case 2: return Missed;
            case 3: return Declined;
            case 4: return EarlyAborted;
            case 5: return AcceptedElsewhere;
            case 6: return DeclinedElsewhere;
         */
    }

    override fun getToAddress(): Address {
        return if (CallDirections.fromValue(call.callDirection) == CallDirections.Outgoing) {
            getAddress(call.calledUserNumber)!!
        } else {
            getAddress(call.callingUserNumber)!!
        }
    }

    override fun isVideoEnabled(): Boolean {
        return false
    }

    override fun toStr(): String {
        return contactLabel
    }

    override fun wasConference(): Boolean {
        return false
    }

    override fun setUserData(data: Any?) {
        userData = data
    }

    override fun getUserData(): Any {
        return call
    }

    override fun getNativePointer(): Long {
        return -1 // FixMe - not sure how linphone get native pointers
    }

    private fun getAddress(number: String?): Address? {
        try {
            if (number.isNullOrBlank()) throw IllegalArgumentException()

            return coreContext.core.interpretUrl(
                number,
                LinphoneUtils.applyInternationalPrefix()
            )
        } catch (e: Exception) {
            return coreContext.core.interpretUrl(
                "07921910119",
                LinphoneUtils.applyInternationalPrefix()
            )
        }
    }
}
