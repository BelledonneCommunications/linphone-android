package org.linphone.models.callhistory

import com.google.gson.annotations.SerializedName

data class ReportRequest(
    @SerializedName("status")
    val status: Int,

    @SerializedName("requestId")
    val requestId: String
)
