package org.linphone.models.realtime

import com.google.gson.annotations.SerializedName

class UserCallSummary(
    @SerializedName("callId")
    val callId: String,

    @SerializedName("direction")
    val direction: String,

    @SerializedName("type")
    val type: String,

    @SerializedName("groupName")
    val groupName: String,

    @SerializedName("routePathName")
    val routePathName: String,

    @SerializedName("routePathId")
    val routePathId: String
)
