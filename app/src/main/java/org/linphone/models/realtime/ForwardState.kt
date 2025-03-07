package org.linphone.models.realtime

import com.google.gson.annotations.SerializedName

class ForwardState(
    type: String,
    timeout: Int?,
    enabled: Boolean,
    personalRoutingGroupEnabled: Boolean,

    @SerializedName("requireKeyPress")
    val requireKeyPress: Boolean,

    @SerializedName("destination")
    val destination: String,

    @SerializedName("directCallsOnly")
    val directCallsOnly: Boolean,

    @SerializedName("keepCallId")
    val keepCallId: Boolean
) : RoutingState(type, timeout, enabled, personalRoutingGroupEnabled)
