package org.linphone.models

import com.google.gson.annotations.SerializedName

data class UserInfo(

    @SerializedName("id")
    val id: String = "",

    @SerializedName("tenantId")
    val tenantId: String = "",

    @SerializedName("tenantName")
    val tenantName: String = "",

    @SerializedName("tenantTier")
    val tenantTier: String = "",

    @SerializedName("displayName")
    val displayName: String = "",

    @SerializedName("email")
    val email: String = "",

    @SerializedName("isEnabled")
    val isEnabled: Boolean = true,

    @SerializedName("profileImageUrl")
    val profileImageUrl: String = "",

    @SerializedName("permissions")
    val permissions: List<String> = emptyList(),

    @SerializedName("presenceId")
    val presenceId: String = "",

    @SerializedName("primaryGroupId")
    val primaryGroupId: String = "",

    @SerializedName("pbxCountryCode")
    val pbxCountryCode: String = "",

    @SerializedName("tenantTimeZoneId")
    val tenantTimeZoneId: String = "",

    @SerializedName("deviceCount")
    val deviceCount: Number = 0,

    @SerializedName("clientProfileSettings")
    val clientProfileSettings: ClientProfileSettings = ClientProfileSettings()
) {

    fun hasClientPermission(): Boolean {
        return permissions.contains("customer.user.uc.mobile")
    }
}
