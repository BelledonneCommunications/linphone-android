package org.linphone.models.realtime

import com.google.gson.annotations.SerializedName
import org.linphone.models.realtime.interfaces.IUserForwardState

class UserForwardStateOn(
    @SerializedName("enabled")
    var enabled: Boolean = false,

    @SerializedName("personalRoutingGroupEnabled")
    var personalRoutingGroupEnabled: Boolean = false,

    @SerializedName("destination")
    var destination: String = "",

    @SerializedName("requireKeyPress")
    var requireKeyPress: Boolean = false,

    @SerializedName("directCallsOnly")
    var directCallsOnly: Boolean = false,

    @SerializedName("keepCallId")
    var keepCallId: Boolean = false
) : IUserForwardState {
    internal val type: String? = this::class.simpleName

    override fun clone(): IUserForwardState {
        return UserForwardStateOn(
            this.enabled,
            this.personalRoutingGroupEnabled,
            this.destination,
            this.requireKeyPress,
            this.directCallsOnly,
            this.keepCallId
        )
    }

    override fun equals(other: Any?): Boolean {
        return other is UserForwardStateOn &&
            other.type == type &&
            other.enabled == enabled &&
            other.personalRoutingGroupEnabled == personalRoutingGroupEnabled &&
            other.destination == destination &&
            other.requireKeyPress == requireKeyPress &&
            other.directCallsOnly == directCallsOnly &&
            other.keepCallId == keepCallId
    }

    override fun hashCode(): Int {
        var result = enabled.hashCode()
        result = 31 * result + personalRoutingGroupEnabled.hashCode()
        result = 31 * result + destination.hashCode()
        result = 31 * result + requireKeyPress.hashCode()
        result = 31 * result + directCallsOnly.hashCode()
        result = 31 * result + keepCallId.hashCode()
        result = 31 * result + (type?.hashCode() ?: 0)
        return result
    }
}
