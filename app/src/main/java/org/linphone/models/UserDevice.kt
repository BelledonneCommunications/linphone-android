package org.linphone.models

import com.google.gson.annotations.SerializedName

data class UserDevice(
    @SerializedName("deviceId")
    var deviceId: String,

    @SerializedName("pbxId")
    var pbxId: String,

    @SerializedName("sipUsername")
    var sipUsername: String,

    @SerializedName("sipPassword")
    var sipUserPassword: String,

    @SerializedName("sipRealm")
    var sipRealm: String,

    @SerializedName("sipOutboundProxy")
    var sipOutboundProxy: String,

    @SerializedName("sipOutboundProxyPort")
    var sipOutboundProxyPort: Int,

    @SerializedName("sipTransport")
    var sipTransport: String,

    @SerializedName("sipRegisterTimeout")
    var sipRegisterTimeout: Int,

    @SerializedName("primaryDns")
    var primaryDns: String,

    @SerializedName("secondaryDns")
    var secondaryDns: String,

    @SerializedName("callWaitingEnabled")
    var callWaitingEnabled: Boolean? = null
) {
    fun hasCredentials(): Boolean {
        return sipUsername.isNotBlank() &&
            sipUserPassword.isNotBlank() &&
            (sipRealm.isNotBlank() || sipOutboundProxy.isNotBlank())
    }
}
