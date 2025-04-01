package org.linphone.models.callhistory

import com.google.gson.annotations.SerializedName

data class UserCallHistorySummary(
    @SerializedName("userId")
    val userId: String,

    @SerializedName("missedCallTimestamp")
    val missedCallTimestamp: String
)
