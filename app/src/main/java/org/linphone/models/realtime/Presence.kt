package org.linphone.models.realtime

import android.graphics.Color
import com.google.gson.annotations.SerializedName
import java.time.OffsetDateTime
import kotlin.time.Duration
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.models.realtime.interfaces.IUserDndState
import org.linphone.models.realtime.interfaces.IUserForwardState
import org.linphone.models.realtime.interfaces.IUserRoutingState

class Presence(
    @SerializedName("affectedUserId")
    val affectedUserId: String,

    @SerializedName("stateId")
    val stateId: String,

    @SerializedName("stateName")
    val stateName: String,

    @SerializedName("availability")
    val availability: String,

    @SerializedName("message")
    val message: String,

    @SerializedName("lastUpdate")
    val lastUpdate: OffsetDateTime,

    @SerializedName("messageExpiryTime")
    val messageExpiryTime: Duration,

    @SerializedName("callSummary")
    val callSummary: UserCallSummary,

    @SerializedName("iconState")
    val iconState: String,

    @SerializedName("dndState")
    val dndState: IUserDndState,

    @SerializedName("forwardState")
    val forwardState: IUserForwardState,

    @SerializedName("callRoutingState")
    val callRoutingState: IUserRoutingState,

    @SerializedName("primaryDevice")
    val primaryDevice: String
) {

    fun getStatusColour(): Int {
        return when (iconState) {
            "busy_acdcall", "busy_nonacdcall", "busy_call", "dnd" -> Color.argb(255, 216, 33, 40) // Red
            "wrapup_acd", "unavailable_acd", "unavailable" -> Color.argb(255, 255, 194, 10) // Yellow
            "available_acd", "available" -> Color.argb(255, 0, 138, 23) // Green
            else -> Color.argb(255, 86, 86, 86) // Grey
        }
    }

    fun getStatusText(): String {
        return when (iconState) {
            "busy_acdcall" -> coreContext.context.getString(R.string.statustext_busy_acdcall)
            "busy_nonacdcall" -> coreContext.context.getString(R.string.statustext_busy_nonacdcall)
            "wrapup_acd" -> coreContext.context.getString(R.string.statustext_wrapup_acd)
            "available_acd" -> coreContext.context.getString(R.string.statustext_available_acd)
            "unavailable_acd" -> coreContext.context.getString(R.string.statustext_unavailable_acd)
            "busy_call" -> coreContext.context.getString(R.string.statustext_busy_call)
            else -> ""
        }
    }
}
