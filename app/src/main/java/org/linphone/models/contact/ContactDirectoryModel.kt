package org.linphone.models.contact

import com.google.gson.annotations.SerializedName

data class ContactDirectoryModel(
    @SerializedName("id")
    val id: String = "",

    @SerializedName("tenantId")
    val tenantId: String = "",

    @SerializedName("name")
    val name: String = "",

    @SerializedName("description")
    val description: String = "",

    @SerializedName("fields")
    val fields: List<FieldDefinitionModel> = emptyList(),

    @SerializedName("displayFields")
    val displayFields: List<String> = emptyList(),

    @SerializedName("tagFields")
    val tagFields: List<String> = emptyList(),

    @SerializedName("userRoleAssociations")
    val userRoleAssociations: Map<String, String>,

    @SerializedName("items")
    val items: Int = 0,

    @SerializedName("avatarDisplayFieldId")
    val avatarDisplayFieldId: String = ""
)
