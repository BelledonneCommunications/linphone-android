package org.linphone.models.callhistory

import com.google.gson.annotations.SerializedName

data class CallHistoryCache(
    @SerializedName("userId")
    val userId: String,

    @SerializedName("version")
    val version: Int,

    @SerializedName("data")
    val data: String
)
