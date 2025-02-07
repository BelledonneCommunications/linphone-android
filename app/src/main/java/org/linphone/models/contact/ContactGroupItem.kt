package org.linphone.models.contact

import com.google.gson.annotations.SerializedName

data class ContactGroupItem(
    @SerializedName("directory")
    val directory: String = "",

    @SerializedName("contactId")
    val contactId: String = ""
)
