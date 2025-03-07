package org.linphone.models.realtime

import com.google.gson.annotations.SerializedName
import org.linphone.models.realtime.interfaces.IUserRoutingState

class UserRoutingStateFindMeFollowMe(
    @SerializedName("timeout")
    override val timeout: Int,

    @SerializedName("devices")
    val devices: Collection<IRoutingDevice>

) : IUserRoutingState {
    internal val type: String? = this::class.simpleName

    override fun clone(): IUserRoutingState {
        return UserRoutingStateFindMeFollowMe(
            timeout,
            devices
        )
    }

    override fun equals(other: Any?): Boolean {
        val otherRoutingState = other as? UserRoutingStateFindMeFollowMe

        val result = otherRoutingState != null &&
            other.type == type &&
            other.timeout == timeout

        if (result) {
            if (otherRoutingState != null) {
                val mismatches =
                    otherRoutingState.devices.map { it.deviceId }.let { outerIt ->
                        devices.map { it.deviceId }.subtract(
                            outerIt.toSet()
                        )
                    }

                if (mismatches.any()) return false

                for (otherDevice in otherRoutingState.devices) {
                    val device = devices.find { x -> x.deviceId == otherDevice.deviceId }
                    if (device == null || device != otherDevice) return false
                }
            }
        }

        return result
    }

    override fun hashCode(): Int {
        var result = timeout.hashCode()
        result = 31 * result + devices.hashCode()
        result = 31 * result + (type?.hashCode() ?: 0)
        return result
    }
}
