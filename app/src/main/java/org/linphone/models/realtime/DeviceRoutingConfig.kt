package org.linphone.models.realtime

class DeviceRoutingConfig(
    val type: String,
    val enabled: Boolean,
    val deviceId: String,
    val name: String,
    val delay: Int,
    val timeout: Int
)
