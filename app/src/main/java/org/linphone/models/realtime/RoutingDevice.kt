package org.linphone.models.realtime

import com.google.gson.annotations.SerializedName

class RoutingDevice(
    @SerializedName("enabled")
    override val enabled: Boolean,

    @SerializedName("deviceId")
    override val deviceId: String,

    @SerializedName("deviceType")
    override val deviceType: String,

    @SerializedName("name")
    override val name: String,

    @SerializedName("delay")
    override val delay: Int,

    @SerializedName("timeout")
    override val timeout: Int
) : IRoutingDevice {
    internal val type: String? = this::class.simpleName
    override fun clone(): IRoutingDevice {
        return RoutingDevice(
            enabled,
            deviceId,
            deviceType,
            name,
            delay,
            timeout
        )
    }

    override fun equals(other: Any?): Boolean {
        return other is RoutingDevice &&
            other.type == type &&
            other.enabled == enabled &&
            other.deviceId == deviceId &&
            other.deviceType == deviceType &&
            other.name == name &&
            other.delay == delay &&
            other.timeout == timeout
    }

    override fun hashCode(): Int {
        var result = enabled.hashCode()
        result = 31 * result + deviceId.hashCode()
        result = 31 * result + deviceType.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + delay.hashCode()
        result = 31 * result + timeout.hashCode()
        result = 31 * result + (type?.hashCode() ?: 0)
        return result
    }
}
