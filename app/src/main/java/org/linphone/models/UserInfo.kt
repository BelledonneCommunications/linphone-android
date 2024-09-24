package org.linphone.models

import com.google.gson.annotations.SerializedName

data class UserInfo(

    @SerializedName("id")
    val id: String,

    @SerializedName("ditenantIdsplayName")
    val tenantId: String,

    @SerializedName("tenantName")
    val tenantName: String,

    @SerializedName("tenantTier")
    val tenantTier: String,

    @SerializedName("displayName")
    val displayName: String,

    @SerializedName("email")
    val email: String,

    @SerializedName("isEnabled")
    val isEnabled: Boolean,

    @SerializedName("profileImageUrl")
    val profileImageUrl: String,

    @SerializedName("permissions")
    val permissions: List<String>,

    @SerializedName("presenceId")
    val presenceId: String,

    @SerializedName("primaryGroupId")
    val primaryGroupId: String,

    @SerializedName("pbxCountryCode")
    val pbxCountryCode: String,

    @SerializedName("tenantTimeZoneId")
    val tenantTimeZoneId: String,

    @SerializedName("clientProfileSettings")
    val clientProfileSettings: ClientProfileSettings
)
