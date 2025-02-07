package org.linphone.models.search

import org.linphone.activities.main.contact.data.ContactAdditionalData
import org.linphone.models.contact.ContactItemModel
import org.linphone.models.usergroup.GroupUserSummaryModel

data class UserDataModel(
    val contact: ContactItemModel?,
    val user: GroupUserSummaryModel?,
    val additionalData: ArrayList<ContactAdditionalData> = arrayListOf(),
    var isInFavourites: Boolean = false
)
