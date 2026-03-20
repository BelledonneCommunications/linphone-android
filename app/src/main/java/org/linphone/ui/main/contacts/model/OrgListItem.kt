package org.linphone.ui.main.contacts.model

sealed class OrgListItem {
    data class Category(val model: OrgCategoryModel) : OrgListItem()

    data class Contact(val model: ContactAvatarModel) : OrgListItem()
}
