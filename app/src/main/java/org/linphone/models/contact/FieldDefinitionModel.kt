package org.linphone.models.contact

import com.google.gson.annotations.SerializedName

data class FieldDefinitionModel(
    @SerializedName("id")
    var id: String = "",

    @SerializedName("name")
    var name: String = "",

    @SerializedName("validation")
    var validation: String = "",

    @SerializedName("definitionType")
    var definitionType: String = ""
)
