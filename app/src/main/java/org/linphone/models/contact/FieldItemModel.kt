package org.linphone.models.contact

import com.google.gson.annotations.SerializedName

data class FieldItemModel(
    @SerializedName("id")
    val id: String = "",

    @SerializedName("value")
    val value: String = ""
)
