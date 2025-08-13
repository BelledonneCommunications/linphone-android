package org.linphone.models.callhistory

import com.google.gson.annotations.SerializedName

data class CallRecordingInfo(
    @SerializedName("sessionId")
    val sessionId: String?,

    @SerializedName("id")
    val id: String?,

    @SerializedName("start")
    val start: Int?,

    @SerializedName("duration")
    val duration: Int?,

    @SerializedName("calleeName")
    val calleeName: String?,

    @SerializedName("calleeNumber")
    val calleeNumber: String?,

    @SerializedName("callerName")
    val callerName: String?,

    @SerializedName("callerNumber")
    val callerNumber: String?,

    @SerializedName("devNum")
    val devNum: String?

)
