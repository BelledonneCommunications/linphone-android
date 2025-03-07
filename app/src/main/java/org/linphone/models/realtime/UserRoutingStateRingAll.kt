package org.linphone.models.realtime

import com.google.gson.annotations.SerializedName
import org.linphone.models.realtime.interfaces.IUserRoutingState

class UserRoutingStateRingAll(
    @SerializedName("timeout")
    override val timeout: Int
) : IUserRoutingState {
    internal val type: String? = this::class.simpleName

    override fun clone(): IUserRoutingState {
        return UserRoutingStateRingAll(timeout)
    }

    override fun equals(other: Any?): Boolean {
        return other is UserRoutingStateFindMeFollowMe &&
            other.type == type &&
            other.timeout == timeout
    }

    override fun hashCode(): Int {
        var result = timeout.hashCode()
        result = 31 * result + (type?.hashCode() ?: 0)
        return result
    }
}
