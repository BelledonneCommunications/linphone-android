package org.linphone.models.realtime

import com.google.gson.annotations.SerializedName

open class RoutingState(
    @SerializedName("_type")
    val type: String,

    @SerializedName("timeout")
    val timeout: Int?,

    @SerializedName("enabled")
    val enabled: Boolean,

    @SerializedName("personalRoutingGroupEnabled")
    val personalRoutingGroupEnabled: Boolean
)
