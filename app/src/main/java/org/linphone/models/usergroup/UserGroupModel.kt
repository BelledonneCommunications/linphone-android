package org.linphone.models.usergroup

import com.google.gson.annotations.SerializedName
import org.linphone.models.contact.ContactItemModel

data class UserGroupModel(
    @SerializedName("id")
    var id: String = "",

    @SerializedName("name")
    var name: String = "",

    @SerializedName("users")
    var users: List<GroupUserSummaryModel> = emptyList(),

    @SerializedName("contacts")
    var contacts: List<ContactItemModel> = emptyList()
) {
    override fun toString(): String {
        return name
    }
}
