import android.content.Context
import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName
import org.linphone.R
import org.linphone.models.realtime.CallRoutingState
import org.linphone.models.realtime.ForwardState
import org.linphone.models.realtime.RoutingState

@Keep
class PresenceEventData(
    @SerializedName("availability")
    val availability: String,

    @SerializedName("callRoutingState")
    val callRoutingState: CallRoutingState,

    @SerializedName("dndState")
    val dndState: RoutingState,

    @SerializedName("forwardingState")
    val forwardingState: ForwardState,

    @SerializedName("hideFromSelection")
    val hideFromSelection: Boolean,

    @SerializedName("iconState")
    val iconState: String,

    @SerializedName("message")
    val message: String?,

    @SerializedName("stateId")
    val stateId: String,

    @SerializedName("stateName")
    val stateName: String,

    @SerializedName("updatePersonalRoutingGroup")
    val updatePersonalRoutingGroup: Boolean
) {
    fun getMessageText(context: Context): String {
        return when (iconState) {
            "available_acd" -> context.getString(R.string.presence_state_waiting)
            "busy_acdcall" -> context.getString(R.string.presence_state_queue_call)
            "busy_call" -> context.getString(R.string.presence_state_on_a_call)
            "busy_nonacdcall" -> context.getString(R.string.presence_state_on_a_call)
            "unavailable_acd" -> context.getString(R.string.presence_state_not_taking_calls)
            "wrapup_acd" -> context.getString(R.string.presence_state_wrap)
            else -> message.toString()
        }
    }
}
