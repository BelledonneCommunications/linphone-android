package org.linphone.models.realtime

import com.google.gson.annotations.SerializedName

class CallRoutingState(
    type: String,
    timeout: Int?,
    enabled: Boolean,
    personalRoutingGroupEnabled: Boolean,

    @SerializedName("devices")
    val devices: List<DeviceRoutingConfig>?
) : RoutingState(type, timeout, enabled, personalRoutingGroupEnabled)
