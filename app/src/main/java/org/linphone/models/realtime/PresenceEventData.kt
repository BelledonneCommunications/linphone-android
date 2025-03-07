import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName
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
)
