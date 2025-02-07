package org.linphone.models.contact

import com.google.gson.annotations.SerializedName

data class ContactItemModel(
    @SerializedName("id")
    val id: String = "",

    @SerializedName("directoryId")
    val directoryId: String = "",

    @SerializedName("fields")
    val fields: List<FieldItemModel> = emptyList()
) {
    companion object {
        const val FULL_NAME = "fullName"
        const val ORGANIZATION = "companyName"
        const val PHONE1 = "phone1"
        const val PHONE2 = "phone2"
        const val PHONE3 = "phone3"
        const val PHONE4 = "phone4"
        const val AVATAR_URL = "avatar"
    }

    var isInFavourites: Boolean = false // Calculated
}
