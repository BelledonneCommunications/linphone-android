package org.linphone.models.usergroup

import com.google.gson.annotations.SerializedName

data class GroupUserSummaryModel(
    @SerializedName("id")
    var id: String = "",

    @SerializedName("name")
    var name: String = "",

    @SerializedName("email")
    var email: String = "",

    @SerializedName("presenceId")
    var presenceId: String = "",

    @SerializedName("profileImagePath")
    var profileImagePath: String = ""
) {
    var isInFavourites: Boolean = false // Calculated
}
