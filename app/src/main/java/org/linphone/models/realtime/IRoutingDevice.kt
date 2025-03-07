package org.linphone.models.realtime

interface IRoutingDevice {
    val enabled: Boolean
    val deviceId: String
    val deviceType: String
    val name: String
    val delay: Int
    val timeout: Int

    fun clone(): IRoutingDevice
    override fun equals(other: Any?): Boolean
    override fun hashCode(): Int
}
