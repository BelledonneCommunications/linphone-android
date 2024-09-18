package org.linphone.models

import com.google.gson.annotations.SerializedName

data class SubscribableUserInfo(var userInfo: UserInfo?) {
    var hasValue = userInfo != null
}

data class UserInfo(

    var id: String,
    var tenantId: String,
    var tenantName: String,
    var tenantTier: String,
    var displayName: String,
    var email: String,
    var isEnabled: Boolean,
    var profileImageUrl: String,

    @SerializedName("permissions")
    var permissions: List<String>,

    @SerializedName("presenceId")
    var presenceId: String,

    @SerializedName("primaryGroupId")
    var primaryGroupId: String,

    @SerializedName("pbxCountryCode")
    var pbxCountryCode: String,

    @SerializedName("tenantTimeZoneId")
    var tenantTimeZoneId: String,

    @SerializedName("clientProfileSettings")
    var clientProfileSettings: ClientProfileSettings
)
