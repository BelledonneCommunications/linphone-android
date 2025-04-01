package org.linphone.models.callhistory

import com.google.gson.annotations.SerializedName

data class CallInteractionTag(
    @SerializedName("id")
    val id: String?,

    @SerializedName("type")
    val type: String?,

    @SerializedName("name")
    val name: String?,

    @SerializedName("value")
    val value: String?,

    @SerializedName("required")
    val required: Boolean?,

    @SerializedName("userId")
    val userId: String?

// FixMe: This causes issues with Date serialization in DateTypeAdapter
//    @SerializedName("timestamp")
//    val timestamp: Date?
)
